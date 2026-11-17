package com.tuxundl.android.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(
    /** Version without the leading "v", e.g. "0.6.0". */
    val version: String,
    /** Direct APK download URL (arm64 asset), if the release has one. */
    val downloadUrl: String?,
    /** The release page URL, used as a fallback target. */
    val pageUrl: String?,
)

/**
 * Minimal GitHub Releases update check: one GET on the `latest` endpoint,
 * parsed with the built-in org.json. Any failure (offline, rate limit,
 * malformed body) returns null and callers stay silent.
 */
object UpdateChecker {
    private const val LATEST_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases/latest"

    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection

            try {
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "TuxunDL")
                connection.setRequestProperty("Accept", "application/vnd.github+json")

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val version = json.optString("tag_name").removePrefix("v")

                if (version.isEmpty()) return@withContext null

                var downloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").contains("arm64")) {
                            downloadUrl = asset.optString("browser_download_url")
                                .takeIf { it.isNotEmpty() }
                            break
                        }
                    }
                }

                UpdateInfo(
                    version = version,
                    downloadUrl = downloadUrl,
                    pageUrl = json.optString("html_url").takeIf { it.isNotEmpty() },
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
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
