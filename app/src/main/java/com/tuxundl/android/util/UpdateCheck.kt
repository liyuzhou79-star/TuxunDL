package com.tuxundl.android.util

import android.content.Context
import android.os.Build
import com.tuxundl.android.common.util.packageName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(
    /** Version without the leading "v", e.g. "0.6.0". */
    val version: String,
    /**
     * Direct APK download URL for this device (matching package variant and
     * ABI), if the release has one; callers fall back to [pageUrl].
     */
    val downloadUrl: String?,
    /**
     * Size in bytes of the asset [downloadUrl] points at, from the
     * release's asset list; -1 when unknown (e.g. the Atom fallback
     * carries no asset data). The in-app updater (1.6.1) verifies the
     * downloaded file against it before offering the install.
     */
    val downloadSize: Long = -1L,
    /** File name of the asset [downloadUrl] points at; "" when unknown. */
    val downloadName: String = "",
    /** The release page URL, used as a fallback target. */
    val pageUrl: String?,
    /** Release notes body (markdown) for the full-screen update page. */
    val notes: String = "",
    /** ISO-8601 publish timestamp from GitHub; "" when absent. */
    val publishedAt: String = "",
)

/** The newest Windows-edition release, for the cross-platform hint. */
data class WindowsInfo(
    /** Version without the "win-v" prefix, e.g. "1.0.1". */
    val version: String,
    /** The Windows release page URL. */
    val pageUrl: String?,
    /** ISO-8601 publish timestamp from GitHub; "" when absent. */
    val publishedAt: String = "",
)

/** Why an update check failed, in user-presentable terms. */
sealed interface UpdateFailure {
    /** The request never completed (offline, blocked, timed out). */
    object Network : UpdateFailure

    /**
     * GitHub refused with 403/429 because the (unauthenticated) API
     * quota for this IP is exhausted — very common behind shared
     * carrier NAT egress addresses.
     */
    object RateLimited : UpdateFailure

    /** GitHub answered with a non-200 status that is not rate limiting. */
    data class Server(val code: Int) : UpdateFailure

    /** The answer was not the JSON the checker expects. */
    object Parse : UpdateFailure
}

/**
 * The three explicit outcomes of an update check. Before 1.1.0 every
 * failure collapsed into a single null, so "could not check at all"
 * (e.g. api.github.com unreachable on a domestic network with the proxy
 * off) was indistinguishable from — and displayed as — a generic error.
 */
sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val info: UpdateInfo,
        val windows: WindowsInfo? = null,
    ) : UpdateCheckResult

    data class UpToDate(
        val latestVersion: String,
        val windows: WindowsInfo? = null,
        /** ISO-8601 publish date of the newest release; "" when absent. */
        val publishedAt: String = "",
    ) : UpdateCheckResult

    data class Failed(val failure: UpdateFailure) : UpdateCheckResult
}

/**
 * Minimal GitHub Releases update check, parsed with the built-in org.json.
 *
 * Uses the release *list* endpoint rather than `/releases/latest`: the
 * latest endpoint only ever returns non-prerelease releases, so a release
 * accidentally published as a prerelease would make every check fail. The
 * list endpoint includes prereleases; the first entry is the newest.
 *
 * Resilience (1.2.1): a failed list fetch is retried once after a short
 * pause, and if it still fails the `/releases/latest` endpoint gets one
 * try as a fallback before the failure is reported. HTTP 403/429 with
 * rate-limit evidence is classified as [UpdateFailure.RateLimited]
 * instead of the generic server bucket it landed in before.
 *
 * Platform filtering (1.2.2): the Windows edition publishes from this
 * same repository with `win-v`-prefixed tags. Only tags matching the
 * Android shape (`v` + numeric dotted version) count as Android
 * releases; anything else in the list is skipped, and a non-Android
 * `/releases/latest` answer is rejected instead of being mistaken for
 * an Android update (or for "already up to date").
 *
 * Cross-platform hint (1.2.3): the same list scan also remembers the
 * newest Windows release, and the result carries it along so the
 * settings page can mention that a Windows edition exists and is on
 * the same release page. It never affects the Android verdict.
 *
 * Rate-limit relief (1.3.0): the unauthenticated api.github.com quota
 * (60 requests/hour per IP) is easily exhausted behind shared carrier
 * NAT addresses, which used to make checks fail outright. Two defences
 * now: the list request is conditional — the last ETag and response
 * body are cached in SharedPreferences and a 304 answer (which does
 * not consume quota) replays the cached body — and when a check still
 * ends in [UpdateFailure.RateLimited], the github.com Atom feed of
 * the same releases is tried as a fallback: it is served outside the
 * API quota and carries each release's tag and date, so the verdict
 * and the Windows hint still work (only the direct APK link is
 * missing; callers fall back to the release page).
 *
 * One shared state (1.6.0): callers no longer invoke this object
 * directly. [UpdateCheckCenter] owns the single check pipeline and
 * the single result every surface reads — before, the home card
 * latched whatever the startup check found while Settings ran its
 * own fresh check (and the Windows entry a third one), so the three
 * could and did disagree. The list window also widened from 10 to
 * 30 entries: the list is created-at ordered across all four
 * platforms' tags, and a desktop release burst could push every
 * `win-v` tag (or, worse, the newest Android tag) out of a 10-entry
 * window.
 */
object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=30"
    private const val LATEST_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases/latest"

    /** Web (non-API) Atom feed of the same releases; no API quota applies. */
    private const val ATOM_URL =
        "https://github.com/liyuzhou79-star/TuxunDL/releases.atom"

    /** SharedPreferences file holding the list ETag + last good body. */
    private const val CACHE_PREFS = "update_check"
    private const val KEY_ETAG = "releases_etag"
    private const val KEY_BODY = "releases_body"

    /** Tag link inside one Atom entry: …/releases/tag/<tag>. */
    private val ATOM_TAG = Regex("""href="https://github\.com/liyuzhou79-star/TuxunDL/releases/tag/([^"]+)"""")
    private val ATOM_UPDATED = Regex("""<updated>([^<]+)</updated>""")

    /** Android release tags look like `v1.2.2`; Windows uses `win-v…`. */
    private val ANDROID_TAG = Regex("""^v\d+\.\d+\.\d+$""")

    /** Windows release tags look like `win-v1.0.1`. */
    private val WINDOWS_TAG = Regex("""^win-v(\d+\.\d+\.\d+)$""")

    private sealed interface FetchOutcome {
        data class Ok(
            val info: UpdateInfo,
            val windows: WindowsInfo? = null,
        ) : FetchOutcome

        data class Error(val failure: UpdateFailure) : FetchOutcome
    }

    /**
     * Check [currentVersion] against the newest GitHub release.
     *
     * [forceFresh] skips the ETag revalidation so the list body is
     * fetched in full; manual checks pass it so a user tapping
     * "check update" always gets a verdict computed from a body the
     * server sent just now, never a replay. A fresh 200 still
     * refreshes the shared ETag/body cache for everyone else.
     */
    suspend fun check(
        context: Context,
        currentVersion: String,
        forceFresh: Boolean = false,
    ): UpdateCheckResult {
        var outcome = fetchList(context, forceFresh)

        if (outcome is FetchOutcome.Error) {
            // One retry after a short pause covers transient failures.
            delay(2000)
            outcome = fetchList(context, forceFresh)
        }

        if (outcome is FetchOutcome.Error) {
            // Fallback endpoint: a different code path on GitHub's side,
            // worth one try before reporting the failure.
            outcome = fetchLatest()
        }

        if (outcome is FetchOutcome.Error &&
            outcome.failure == UpdateFailure.RateLimited
        ) {
            // The API quota for this IP is gone; the Atom feed lives
            // outside that quota and still names the newest releases.
            val atom = fetchAtom()
            if (atom is FetchOutcome.Ok) {
                outcome = atom
            }
        }

        return when (outcome) {
            is FetchOutcome.Error -> UpdateCheckResult.Failed(outcome.failure)
            is FetchOutcome.Ok -> {
                val info = outcome.info

                if (isNewer(info.version, currentVersion)) {
                    UpdateCheckResult.UpdateAvailable(info, outcome.windows)
                } else {
                    UpdateCheckResult.UpToDate(
                        info.version,
                        outcome.windows,
                        info.publishedAt,
                    )
                }
            }
        }
    }

    /**
     * The release list, with ETag revalidation: a cached ETag goes out
     * as If-None-Match, and a 304 answer (free of API quota) replays
     * the cached body. A 200 answer refreshes the cache — but only
     * when it parses, so a corrupt entry can never wedge every later
     * check onto the 304 path. Errors classify exactly like [fetch].
     * [forceFresh] skips the If-None-Match header (a 304 is then
     * impossible), but a 200 still refreshes the cache as usual.
     */
    private suspend fun fetchList(
        context: Context,
        forceFresh: Boolean = false,
    ): FetchOutcome =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val etag = prefs.getString(KEY_ETAG, null)
            val cachedBody = prefs.getString(KEY_BODY, null)

            val connection = try {
                URL(RELEASES_URL).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return@withContext FetchOutcome.Error(UpdateFailure.Network)
            }

            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "TuxunDL-Android")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (etag != null && !forceFresh) {
                    connection.setRequestProperty("If-None-Match", etag)
                }

                val responseCode = try {
                    connection.responseCode
                } catch (e: Exception) {
                    return@withContext FetchOutcome.Error(UpdateFailure.Network)
                }

                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED &&
                    cachedBody != null
                ) {
                    return@withContext parseListBody(cachedBody)
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext FetchOutcome.Error(
                        classifyHttpError(connection, responseCode),
                    )
                }

                val body = try {
                    connection.inputStream.bufferedReader().readText()
                } catch (e: Exception) {
                    return@withContext FetchOutcome.Error(UpdateFailure.Network)
                }

                val parsed = parseListBody(body)

                if (parsed is FetchOutcome.Ok) {
                    prefs.edit()
                        .putString(KEY_ETAG, connection.getHeaderField("ETag"))
                        .putString(KEY_BODY, body)
                        .apply()
                }

                parsed
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun fetchLatest(): FetchOutcome =
        fetch(LATEST_URL) { body -> parseLatestBody(body) }

    private suspend fun fetchAtom(): FetchOutcome =
        fetch(ATOM_URL) { body -> parseAtomBody(body) }

    /**
     * The Atom feed lists releases newest first; each entry links to
     * its tag page and carries an <updated> timestamp. Same platform
     * filter as the API list. The feed has no asset links, so the
     * download URL stays null and callers use the release page.
     */
    private fun parseAtomBody(body: String): FetchOutcome {
        var android: UpdateInfo? = null
        var windows: WindowsInfo? = null

        // Chunk 0 is the feed header (no tag link); entries follow.
        for (entry in body.split("<entry>")) {
            val tag = ATOM_TAG.find(entry)?.groupValues?.get(1) ?: continue
            val updated = ATOM_UPDATED.find(entry)?.groupValues?.get(1) ?: ""

            if (android == null && ANDROID_TAG.matches(tag)) {
                android = UpdateInfo(
                    version = tag.removePrefix("v"),
                    downloadUrl = null,
                    pageUrl = "https://github.com/liyuzhou79-star/TuxunDL" +
                        "/releases/tag/$tag",
                    notes = "",
                    publishedAt = updated,
                )
            }

            if (windows == null) {
                WINDOWS_TAG.matchEntire(tag)?.let { match ->
                    windows = WindowsInfo(
                        version = match.groupValues[1],
                        pageUrl = "https://github.com/liyuzhou79-star/TuxunDL" +
                            "/releases/tag/$tag",
                        publishedAt = updated,
                    )
                }
            }

            if (android != null && windows != null) break
        }

        return if (android != null) {
            FetchOutcome.Ok(android, windows)
        } else {
            FetchOutcome.Error(UpdateFailure.Parse)
        }
    }

    private fun parseListBody(body: String): FetchOutcome {
        val releases = try {
            JSONArray(body)
        } catch (e: Exception) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        // One pass over the newest-first list: the first Android tag
        // is the newest Android release, the first `win-v` tag is the
        // newest Windows release. Both are reported; only the Android
        // one decides the update verdict.
        var android: UpdateInfo? = null
        var windows: WindowsInfo? = null

        for (i in 0 until releases.length()) {
            val json = releases.getJSONObject(i)

            if (json.optBoolean("draft")) continue

            val tag = json.optString("tag_name")

            if (android == null && ANDROID_TAG.matches(tag)) {
                android = infoFrom(json, tag.removePrefix("v"))
            }

            if (windows == null) {
                WINDOWS_TAG.matchEntire(tag)?.let { match ->
                    windows = WindowsInfo(
                        version = match.groupValues[1],
                        pageUrl = json.optString("html_url")
                            .takeIf { it.isNotEmpty() },
                        publishedAt = json.optString("published_at"),
                    )
                }
            }

            if (android != null && windows != null) break
        }

        return if (android != null) {
            FetchOutcome.Ok(android, windows)
        } else {
            FetchOutcome.Error(UpdateFailure.Parse)
        }
    }

    private fun parseLatestBody(body: String): FetchOutcome {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        if (json.optBoolean("draft")) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        val tag = json.optString("tag_name")

        // The latest release overall may be a Windows release; that is
        // not an Android update and must not pose as one.
        if (!ANDROID_TAG.matches(tag)) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        val version = tag.removePrefix("v")

        return FetchOutcome.Ok(infoFrom(json, version))
    }

    private fun infoFrom(json: JSONObject, version: String): UpdateInfo {
        val asset = pickAsset(json.optJSONArray("assets"))

        return UpdateInfo(
            version = version,
            downloadUrl = asset?.url,
            downloadSize = asset?.size ?: -1L,
            downloadName = asset?.name ?: "",
            pageUrl = json.optString("html_url")
                .takeIf { it.isNotEmpty() },
            notes = json.optString("body"),
            publishedAt = json.optString("published_at"),
        )
    }

    /** One release asset, as picked for this device. */
    private data class PickedAsset(
        val name: String,
        val url: String,
        val size: Long,
    )

    private suspend fun fetch(
        url: String,
        parse: (String) -> FetchOutcome,
    ): FetchOutcome = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext FetchOutcome.Error(UpdateFailure.Network)
        }

        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "TuxunDL-Android")
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            val responseCode = try {
                connection.responseCode
            } catch (e: Exception) {
                return@withContext FetchOutcome.Error(UpdateFailure.Network)
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext FetchOutcome.Error(
                    classifyHttpError(connection, responseCode),
                )
            }

            val body = try {
                connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                return@withContext FetchOutcome.Error(UpdateFailure.Network)
            }

            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Tell rate limiting apart from other HTTP errors: GitHub answers
     * quota exhaustion with 403 (or 429), an `X-RateLimit-Remaining: 0`
     * header and a body mentioning "rate limit". Anything else keeps
     * its status code so the UI can show it.
     */
    private fun classifyHttpError(
        connection: HttpURLConnection,
        code: Int,
    ): UpdateFailure {
        if (code == 403 || code == 429) {
            val remaining = connection.getHeaderField("X-RateLimit-Remaining")
            val body = try {
                connection.errorStream?.bufferedReader()?.readText()
            } catch (e: Exception) {
                null
            }

            if (remaining?.trim() == "0" ||
                body?.contains("rate limit", ignoreCase = true) == true
            ) {
                return UpdateFailure.RateLimited
            }
        }

        return UpdateFailure.Server(code)
    }

    /**
     * Pick the APK asset matching this build: the legacy package
     * (applicationId ending in `.legacy`) takes the `legacy` + `armeabi-v7a`
     * asset; the standard package takes a non-legacy asset for the device's
     * ABI (arm64 when supported, otherwise armeabi-v7a). Null when the
     * release has no matching asset.
     */
    private fun pickAsset(assets: JSONArray?): PickedAsset? {
        if (assets == null) return null

        val candidates = mutableListOf<PickedAsset>()

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")

            if (name.isNotEmpty() && url.isNotEmpty()) {
                candidates += PickedAsset(
                    name = name,
                    url = url,
                    size = asset.optLong("size", -1L),
                )
            }
        }

        val isLegacyPackage = packageName.endsWith(".legacy")
        val deviceSupportsArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")

        return when {
            isLegacyPackage -> candidates.firstOrNull {
                it.name.contains("legacy") && it.name.contains("armeabi-v7a")
            }

            deviceSupportsArm64 -> candidates.firstOrNull {
                !it.name.contains("legacy") && it.name.contains("arm64")
            }

            else -> candidates.firstOrNull {
                !it.name.contains("legacy") && it.name.contains("armeabi-v7a")
            }
        }
    }

    /** Segment-wise numeric comparison: is [remote] strictly newer than [local]? */
    fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }

            if (r != l) return r > l
        }

        return false
    }
}

/**
 * The one shared update-check state every surface reads (1.6.0).
 *
 * Before 1.6.0 each surface ran its own check: the home card latched
 * the startup check's verdict for the whole session, Settings ran a
 * fresh check per tap, and the Windows-edition entry ran a third one
 * just to extract the Windows hint. Three independent network checks
 * at three moments can return three different answers — the home
 * screen said "new version" while Settings, checking later against
 * an exhausted API quota or a shifted release-list window, said
 * otherwise. Now there is exactly one pipeline ([UpdateChecker],
 * driven only from here) and exactly one result, held in [state]
 * and persisted across process death.
 */
data class UpdateCheckSnapshot(
    /** True while a check is running. */
    val checking: Boolean = false,
    /** The last completed check's outcome; null before the first one. */
    val result: UpdateCheckResult? = null,
    /** Wall-clock time the last check completed; 0 = never. */
    val checkedAtMillis: Long = 0L,
    /** Installed version [result] was computed for; "" = none. */
    val forVersion: String = "",
)

object UpdateCheckCenter {
    /**
     * Automatic checks (app start) run at most once per this
     * interval for a given installed version; inside the interval
     * the persisted verdict is replayed with no network at all.
     * Manual checks are never throttled.
     */
    private const val AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000

    /** Same prefs file [UpdateChecker] keeps its ETag/body cache in. */
    private const val PREFS = "update_check"
    private const val KEY_VERDICT = "state_verdict"
    private const val KEY_FOR_VERSION = "state_for_version"
    private const val KEY_CHECKED_AT = "state_checked_at"
    private const val KEY_LATEST_VERSION = "state_latest_version"
    private const val KEY_DOWNLOAD_URL = "state_download_url"
    private const val KEY_DOWNLOAD_SIZE = "state_download_size"
    private const val KEY_DOWNLOAD_NAME = "state_download_name"
    private const val KEY_PAGE_URL = "state_page_url"
    private const val KEY_NOTES = "state_notes"
    private const val KEY_PUBLISHED_AT = "state_published_at"
    private const val KEY_WIN_VERSION = "state_win_version"
    private const val KEY_WIN_PAGE_URL = "state_win_page_url"
    private const val KEY_WIN_PUBLISHED_AT = "state_win_published_at"

    private const val VERDICT_AVAILABLE = "available"
    private const val VERDICT_UP_TO_DATE = "uptodate"

    // Own scope, like TrafficSampler: a check started by one activity
    // must be able to finish (and publish its result) even when that
    // activity goes away mid-flight.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var inFlight: Deferred<UpdateCheckResult>? = null
    private var inFlightManual = false
    private var inFlightFor = ""

    private val _state = MutableStateFlow(UpdateCheckSnapshot())

    /** The shared state; surfaces collect this and render from it. */
    val state: StateFlow<UpdateCheckSnapshot> = _state.asStateFlow()

    /**
     * The current snapshot, hydrating from the persisted verdict
     * when this process has not checked yet. Returns an empty
     * snapshot when nothing was ever stored for [currentVersion].
     */
    fun peek(context: Context, currentVersion: String): UpdateCheckSnapshot {
        val current = _state.value

        if (current.result != null && current.forVersion == currentVersion) {
            return current
        }

        // A result computed for another installed version (the app
        // was updated since) must never be presented as current.
        if (current.forVersion.isNotEmpty() &&
            current.forVersion != currentVersion
        ) {
            return UpdateCheckSnapshot()
        }

        val stored = readStored(context, currentVersion) ?: return current
        _state.value = stored
        return stored
    }

    /**
     * Run a check through the single pipeline and publish the
     * outcome to [state].
     *
     * Manual checks always hit the network and force a fresh list
     * body (no ETag replay, no throttle). Automatic checks replay
     * the persisted verdict when the last successful check for this
     * installed version is younger than [AUTO_INTERVAL_MS]. A check
     * already in flight for the same version is shared, never
     * duplicated; a manual check never waits on an automatic one
     * (it starts its own, forced-fresh run instead).
     */
    suspend fun check(
        context: Context,
        currentVersion: String,
        manual: Boolean,
    ): UpdateCheckResult {
        if (!manual) {
            val stored = readStored(context, currentVersion)

            if (stored?.result != null &&
                System.currentTimeMillis() - stored.checkedAtMillis <
                AUTO_INTERVAL_MS
            ) {
                _state.value = stored
                return stored.result
            }
        }

        val appContext = context.applicationContext

        val deferred = mutex.withLock {
            val existing = inFlight

            if (existing != null && existing.isActive &&
                inFlightFor == currentVersion && (inFlightManual || !manual)
            ) {
                existing
            } else {
                val started = scope.async {
                    runCheck(appContext, currentVersion, manual)
                }
                inFlight = started
                inFlightManual = manual
                inFlightFor = currentVersion
                started
            }
        }

        return deferred.await()
    }

    private suspend fun runCheck(
        context: Context,
        currentVersion: String,
        manual: Boolean,
    ): UpdateCheckResult {
        _state.value = _state.value.copy(checking = true)

        val result = UpdateChecker.check(
            context,
            currentVersion,
            forceFresh = manual,
        )
        val now = System.currentTimeMillis()

        // Only definitive verdicts are persisted; a failure is a
        // momentary report, not a state worth resurrecting after
        // the process restarts. The in-memory snapshot still
        // carries it so every surface shows the same failure.
        if (result !is UpdateCheckResult.Failed) {
            writeStored(context, currentVersion, result, now)
        }

        val snapshot = UpdateCheckSnapshot(
            checking = false,
            result = result,
            checkedAtMillis = now,
            forVersion = currentVersion,
        )
        _state.value = snapshot
        return result
    }

    private fun readStored(
        context: Context,
        currentVersion: String,
    ): UpdateCheckSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (prefs.getString(KEY_FOR_VERSION, null) != currentVersion) {
            return null
        }

        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        if (checkedAt == 0L) return null

        val latest = prefs.getString(KEY_LATEST_VERSION, null)
            ?.takeIf { it.isNotEmpty() } ?: return null

        val windows = prefs.getString(KEY_WIN_VERSION, null)
            ?.takeIf { it.isNotEmpty() }
            ?.let { version ->
                WindowsInfo(
                    version = version,
                    pageUrl = prefs.getString(KEY_WIN_PAGE_URL, null),
                    publishedAt = prefs.getString(
                        KEY_WIN_PUBLISHED_AT, "",
                    ) ?: "",
                )
            }

        val publishedAt = prefs.getString(KEY_PUBLISHED_AT, "") ?: ""

        val result = when (prefs.getString(KEY_VERDICT, null)) {
            VERDICT_AVAILABLE -> UpdateCheckResult.UpdateAvailable(
                info = UpdateInfo(
                    version = latest,
                    downloadUrl = prefs.getString(KEY_DOWNLOAD_URL, null),
                    downloadSize = prefs.getLong(KEY_DOWNLOAD_SIZE, -1L),
                    downloadName = prefs.getString(KEY_DOWNLOAD_NAME, "") ?: "",
                    pageUrl = prefs.getString(KEY_PAGE_URL, null),
                    notes = prefs.getString(KEY_NOTES, "") ?: "",
                    publishedAt = publishedAt,
                ),
                windows = windows,
            )

            VERDICT_UP_TO_DATE -> UpdateCheckResult.UpToDate(
                latestVersion = latest,
                windows = windows,
                publishedAt = publishedAt,
            )

            else -> return null
        }

        return UpdateCheckSnapshot(
            checking = false,
            result = result,
            checkedAtMillis = checkedAt,
            forVersion = currentVersion,
        )
    }

    private fun writeStored(
        context: Context,
        currentVersion: String,
        result: UpdateCheckResult,
        checkedAt: Long,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_FOR_VERSION, currentVersion)
            .putLong(KEY_CHECKED_AT, checkedAt)

        when (result) {
            is UpdateCheckResult.UpdateAvailable -> {
                editor.putString(KEY_VERDICT, VERDICT_AVAILABLE)
                    .putString(KEY_LATEST_VERSION, result.info.version)
                    .putString(KEY_DOWNLOAD_URL, result.info.downloadUrl)
                    .putLong(KEY_DOWNLOAD_SIZE, result.info.downloadSize)
                    .putString(KEY_DOWNLOAD_NAME, result.info.downloadName)
                    .putString(KEY_PAGE_URL, result.info.pageUrl)
                    .putString(KEY_NOTES, result.info.notes)
                    .putString(KEY_PUBLISHED_AT, result.info.publishedAt)
                writeWindows(editor, result.windows)
            }

            is UpdateCheckResult.UpToDate -> {
                editor.putString(KEY_VERDICT, VERDICT_UP_TO_DATE)
                    .putString(KEY_LATEST_VERSION, result.latestVersion)
                    .putString(KEY_DOWNLOAD_URL, null)
                    .putLong(KEY_DOWNLOAD_SIZE, -1L)
                    .putString(KEY_DOWNLOAD_NAME, null)
                    .putString(KEY_PAGE_URL, null)
                    .putString(KEY_NOTES, null)
                    .putString(KEY_PUBLISHED_AT, result.publishedAt)
                writeWindows(editor, result.windows)
            }

            is UpdateCheckResult.Failed -> Unit
        }

        editor.apply()
    }

    private fun writeWindows(
        editor: android.content.SharedPreferences.Editor,
        windows: WindowsInfo?,
    ) {
        editor.putString(KEY_WIN_VERSION, windows?.version)
            .putString(KEY_WIN_PAGE_URL, windows?.pageUrl)
            .putString(KEY_WIN_PUBLISHED_AT, windows?.publishedAt)
    }
}
