package com.tuxundl.android.util

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
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult
    data class UpToDate(val latestVersion: String) : UpdateCheckResult
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
 */
object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=5"
    private const val LATEST_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases/latest"

    private sealed interface FetchOutcome {
        data class Ok(val info: UpdateInfo) : FetchOutcome
        data class Error(val failure: UpdateFailure) : FetchOutcome
    }

    /** Check [currentVersion] against the newest GitHub release. */
    suspend fun check(currentVersion: String): UpdateCheckResult {
        var outcome = fetchList()

        if (outcome is FetchOutcome.Error) {
            // One retry after a short pause covers transient failures.
            delay(2000)
            outcome = fetchList()
        }

        if (outcome is FetchOutcome.Error) {
            // Fallback endpoint: a different code path on GitHub's side,
            // worth one try before reporting the failure.
            outcome = fetchLatest()
        }

        return when (outcome) {
            is FetchOutcome.Error -> UpdateCheckResult.Failed(outcome.failure)
            is FetchOutcome.Ok -> {
                val info = outcome.info

                if (isNewer(info.version, currentVersion)) {
                    UpdateCheckResult.UpdateAvailable(info)
                } else {
                    UpdateCheckResult.UpToDate(info.version)
                }
            }
        }
    }

    private suspend fun fetchList(): FetchOutcome =
        fetch(RELEASES_URL) { body -> parseListBody(body) }

    private suspend fun fetchLatest(): FetchOutcome =
        fetch(LATEST_URL) { body -> parseLatestBody(body) }

    private fun parseListBody(body: String): FetchOutcome {
        val releases = try {
            JSONArray(body)
        } catch (e: Exception) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        for (i in 0 until releases.length()) {
            val json = releases.getJSONObject(i)

            if (json.optBoolean("draft")) continue

            val version = json.optString("tag_name").removePrefix("v")

            if (version.isEmpty()) continue

            return FetchOutcome.Ok(infoFrom(json, version))
        }

        return FetchOutcome.Error(UpdateFailure.Parse)
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

        val version = json.optString("tag_name").removePrefix("v")

        if (version.isEmpty()) {
            return FetchOutcome.Error(UpdateFailure.Parse)
        }

        return FetchOutcome.Ok(infoFrom(json, version))
    }

    private fun infoFrom(json: JSONObject, version: String): UpdateInfo {
        return UpdateInfo(
            version = version,
            downloadUrl = pickAssetUrl(json.optJSONArray("assets")),
            pageUrl = json.optString("html_url")
                .takeIf { it.isNotEmpty() },
            notes = json.optString("body"),
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
