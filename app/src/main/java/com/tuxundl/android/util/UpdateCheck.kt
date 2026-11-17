package com.tuxundl.android.util

import android.os.Build
import com.tuxundl.android.common.util.packageName
import kotlinx.coroutines.Dispatchers
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
)

/**
 * Minimal GitHub Releases update check, parsed with the built-in org.json.
 * Any failure (offline, rate limit, malformed body) returns null and
 * callers stay silent.
 *
 * Uses the release *list* endpoint rather than `/releases/latest`: the
 * latest endpoint only ever returns non-prerelease releases, so a release
 * accidentally published as a prerelease would make every check fail. The
 * list endpoint includes prereleases; the first entry is the newest.
 */
object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=5"

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "TuxunDL-Android")
                connection.setRequestProperty("Accept", "application/vnd.github+json")

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val releases = JSONArray(
                    connection.inputStream.bufferedReader().readText()
                )

                for (i in 0 until releases.length()) {
                    val json = releases.getJSONObject(i)

                    if (json.optBoolean("draft")) continue

                    val version = json.optString("tag_name").removePrefix("v")

                    if (version.isEmpty()) continue

                    return@withContext UpdateInfo(
                        version = version,
                        downloadUrl = pickAssetUrl(json.optJSONArray("assets")),
                        pageUrl = json.optString("html_url")
                            .takeIf { it.isNotEmpty() },
                    )
                }

                null
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
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
