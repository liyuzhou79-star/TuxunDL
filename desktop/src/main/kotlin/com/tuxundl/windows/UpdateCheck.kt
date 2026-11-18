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
 * Update check for the desktop editions. Reads this repository's
 * release list and only considers tags shaped `<prefix><version>`
 * for the edition that is running — `win-v<version>` on Windows,
 * `linux-v<version>` on Linux. Android releases (`v<version>`) and
 * the other desktop edition's releases are different products and
 * never count here, mirroring the platform filter the Android
 * checker gained in 1.2.2. The two desktop editions version
 * independently (Windows 1.6.6, Linux 1.0.0 at the time of writing),
 * so the current version is picked per OS as well.
 *
 * Two data sources, tried in order:
 * 1. The GitHub REST API (api.github.com). Unauthenticated requests
 *    are rate-limited per IP (60/hour), and behind shared carrier
 *    NAT egress addresses that quota is often already exhausted, so
 *    this source failing is a normal event, not an edge case.
 * 2. The releases Atom feed on github.com proper, which is not
 *    subject to the API quota. Entry links carry the tag
 *    (`/releases/tag/win-v<x.y.z>`), which is all the check needs.
 *
 * Outcomes are explicit (Windows 1.6.1): before that version every
 * failure — offline, timeout, rate limit, unparsable answer —
 * collapsed into the same "no newer version" result, so the
 * settings page happily reported "already up to date" when the
 * check had never succeeded. Failures now surface as [Result.Failed]
 * and the UI says so.
 */
object DesktopUpdateCheck {

    /** Release tag prefix of the running edition: "win-v" / "linux-v". */
    val TAG_PREFIX: String = if (Platform.isLinux) "linux-v" else "win-v"

    private const val WINDOWS_VERSION = "1.6.7"
    private const val LINUX_VERSION = "1.0.0"

    /** Version of the running edition (kept in sync with build.gradle.kts). */
    val CURRENT_VERSION: String =
        if (Platform.isLinux) LINUX_VERSION else WINDOWS_VERSION

    private const val RELEASES_URL =
        "https://api.github.com/repos/liyuzhou79-star/TuxunDL/releases?per_page=30"

    private const val ATOM_URL =
        "https://github.com/liyuzhou79-star/TuxunDL/releases.atom"

    private const val TAG_PAGE_PREFIX =
        "https://github.com/liyuzhou79-star/TuxunDL/releases/tag/"

    private val tagPattern =
        Regex("^" + Regex.escape(TAG_PREFIX) + "(\\d+)\\.(\\d+)\\.(\\d+)$")

    /** Release tags as they appear in Atom entry links. */
    private val atomTagPattern = Regex(
        "/releases/tag/(" + Regex.escape(TAG_PREFIX) + "\\d+\\.\\d+\\.\\d+)",
    )

    /** The three honest outcomes of an update check. */
    sealed interface Result {
        /** A strictly newer release of this edition exists. */
        data class UpdateAvailable(
            /** Newer version without the prefix, e.g. "1.1.0". */
            val version: String,
            /** Release page of the newer version. */
            val pageUrl: String?,
        ) : Result

        /** The newest release of this edition is not newer than the running one. */
        object UpToDate : Result

        /** Every data source failed; the check did not succeed. */
        data class Failed(val reason: String) : Result
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        checkViaApi()
            ?: checkViaAtom()
            ?: Result.Failed("网络不通或接口限流")
    }

    /** Primary source: the REST release list. Null on any failure. */
    private fun checkViaApi(): Result? {
        val body = httpGet(RELEASES_URL, "application/vnd.github+json")
            ?: return null

        val releases = runCatching {
            Json.parseToJsonElement(body).jsonArray
        }.getOrNull() ?: return null

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

        // A parsed list without a single release of this edition is
        // not a trustworthy "up to date" — let the fallback source try.
        if (best == null) return null

        return verdict(best!!, bestText!!, bestUrl)
    }

    /**
     * Fallback source: the releases Atom feed. Carries no draft /
     * prerelease flags, but entries are newest-first public releases
     * and the tag in each entry link is enough to find the newest
     * version of this edition. Null on any failure.
     */
    private fun checkViaAtom(): Result? {
        val body = httpGet(ATOM_URL, "application/atom+xml") ?: return null

        var best: Triple<Int, Int, Int>? = null
        var bestTag: String? = null
        for (match in atomTagPattern.findAll(body)) {
            val tag = match.groupValues[1]
            val parts = tag.removePrefix(TAG_PREFIX).split(".")
            val version = Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            if (best == null || compare(version, best!!) > 0) {
                best = version
                bestTag = tag
            }
        }

        if (best == null || bestTag == null) return null

        val bestText = bestTag!!.removePrefix(TAG_PREFIX)
        return verdict(best!!, bestText, TAG_PAGE_PREFIX + bestTag)
    }

    /** Compare the newest found version against the running one. */
    private fun verdict(
        best: Triple<Int, Int, Int>,
        bestText: String,
        pageUrl: String?,
    ): Result {
        val current = CURRENT_VERSION.split(".").map { it.toInt() }
        val currentTriple = Triple(current[0], current[1], current[2])
        return if (compare(best, currentTriple) > 0) {
            Result.UpdateAvailable(bestText, pageUrl)
        } else {
            Result.UpToDate
        }
    }

    /** GET with generous timeouts; null unless the answer is 200. */
    private fun httpGet(url: String, accept: String): String? = runCatching {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", accept)
            .header("User-Agent", "TuxunDL-${Platform.editionName}")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) response.body() else null
    }.getOrNull()

    private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
        compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
}
