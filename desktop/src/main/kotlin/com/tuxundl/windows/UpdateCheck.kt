package com.tuxundl.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Update check for the Windows edition. Reads this repository's
 * release list and only considers tags shaped `win-v<version>` —
 * Android releases (`v<version>`) are a different product and never
 * count here, mirroring the platform filter the Android checker
 * gained in 1.2.2.
 */
object WindowsUpdateCheck {

    const val CURRENT_VERSION = "1.1.4"

    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=30"

    private val tagPattern = Regex("^win-v(\\d+)\\.(\\d+)\\.(\\d+)$")

    data class Result(
        /** Newer version without the prefix, e.g. "1.1.0"; null if up to date. */
        val newerVersion: String?,
        /** Release page of the newer version, when one exists. */
        val pageUrl: String?,
    )

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val body = runCatching {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
            val request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) response.body() else null
        }.getOrNull() ?: return@withContext Result(null, null)

        val releases = runCatching {
            Json.parseToJsonElement(body).jsonArray
        }.getOrNull() ?: return@withContext Result(null, null)

        var best: Triple<Int, Int, Int>? = null
        var bestText: String? = null
        var bestUrl: String? = null
        for (release in releases) {
            val obj = release.jsonObject
            if (obj["draft"]?.jsonPrimitive?.content == "true") continue
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: continue
            val match = tagPattern.matchEntire(tag) ?: continue
            val version = Triple(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
            if (best == null || compare(version, best!!) > 0) {
                best = version
                bestText = "${version.first}.${version.second}.${version.third}"
                bestUrl = obj["html_url"]?.jsonPrimitive?.content
            }
        }

        val current = CURRENT_VERSION.split(".").map { it.toInt() }
        val currentTriple = Triple(current[0], current[1], current[2])
        if (best != null && compare(best!!, currentTriple) > 0) {
            Result(bestText, bestUrl)
        } else {
            Result(null, null)
        }
    }

    private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
        compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
}
