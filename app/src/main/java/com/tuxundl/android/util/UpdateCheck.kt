package com.tuxundl.android.util

import android.content.Context
import android.os.Build
import com.tuxundl.android.common.util.packageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 */
object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=10"
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

    /** Check [currentVersion] against the newest GitHub release. */
    suspend fun check(context: Context, currentVersion: String): UpdateCheckResult {
        var outcome = fetchList(context)

        if (outcome is FetchOutcome.Error) {
            // One retry after a short pause covers transient failures.
            delay(2000)
            outcome = fetchList(context)
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
     */
    private suspend fun fetchList(context: Context): FetchOutcome =
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
                if (etag != null) {
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
        return UpdateInfo(
            version = version,
            downloadUrl = pickAssetUrl(json.optJSONArray("assets")),
            pageUrl = json.optString("html_url")
                .takeIf { it.isNotEmpty() },
            notes = json.optString("body"),
            publishedAt = json.optString("published_at"),
        )
    }

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
    private fun pickAssetUrl(assets: JSONArray?): String? {
        if (assets == null) return null

        val candidates = mutableListOf<Pair<String, String>>()

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")

            if (name.isNotEmpty() && url.isNotEmpty()) {
                candidates += name to url
            }
        }

        val isLegacyPackage = packageName.endsWith(".legacy")
        val deviceSupportsArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")

        val match: Pair<String, String>? = when {
            isLegacyPackage -> candidates.firstOrNull {
                it.first.contains("legacy") && it.first.contains("armeabi-v7a")
            }

            deviceSupportsArm64 -> candidates.firstOrNull {
                !it.first.contains("legacy") && it.first.contains("arm64")
            }

            else -> candidates.firstOrNull {
                !it.first.contains("legacy") && it.first.contains("armeabi-v7a")
            }
        }

        return match?.second
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
