package com.tuxundl.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** One saved subscription: a name, its URL and the last fetch time. */
@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    /** Traffic used so far (upload + download), from the provider. */
    val trafficUsed: Long = 0,
    /** Total traffic quota; 0 when the provider does not report one. */
    val trafficTotal: Long = 0,
    /** Plan expiry as epoch seconds; 0 when not reported. */
    val expireAt: Long = 0,
)

/**
 * Subscription storage under the app data dir: the list lives in
 * `subscriptions.json`, each fetched config in `profiles/<id>.yaml`,
 * and the active one is copied to the profile file CoreBridge reads.
 * Configs are the same mihomo YAML the Android edition consumes.
 */
object SubscriptionStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun listFile(): File = File(CoreBridge.workDir, "subscriptions.json")

    private fun profilesDir(): File =
        File(CoreBridge.workDir, "profiles").apply { mkdirs() }

    fun load(): List<Subscription> = runCatching {
        val file = listFile()
        if (!file.exists()) {
            emptyList()
        } else {
            json.decodeFromString<List<Subscription>>(file.readText())
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Subscription>) {
        CoreBridge.workDir.mkdirs()
        listFile().writeText(json.encodeToString(list))
    }

    fun newSubscription(name: String, url: String): Subscription =
        Subscription(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            url = url,
        )

    fun profileFile(sub: Subscription): File =
        File(profilesDir(), "${sub.id}.yaml")

    /**
     * Download the subscription's config and store it. The body must
     * look like a mihomo config (it has to name its proxies), which
     * filters out HTML error pages and login walls.
     */
    suspend fun fetch(sub: Subscription): Result<Subscription> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = HttpRequest.newBuilder(URI.create(sub.url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "clash.meta")
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }

                val body = response.body()
                check(body.contains("proxies:")) { "返回的内容不像订阅配置" }

                profileFile(sub).writeText(body)
                val userInfo = response.headers()
                    .firstValue("subscription-userinfo")
                    .map { parseUserInfo(it) }
                    .orElse(null)
                if (userInfo != null) {
                    sub.copy(
                        updatedAt = System.currentTimeMillis(),
                        trafficUsed = userInfo.first,
                        trafficTotal = userInfo.second,
                        expireAt = userInfo.third,
                    )
                } else {
                    // No usage header this time: keep what we already
                    // knew instead of wiping the traffic info.
                    sub.copy(updatedAt = System.currentTimeMillis())
                }
            }
        }

    /**
     * Parse a `subscription-userinfo` response header
     * (`upload=..; download=..; total=..; expire=..`, expire in epoch
     * seconds) into used bytes, total bytes and expiry. Returns null
     * when the header carries no usable total.
     */
    private fun parseUserInfo(header: String): Triple<Long, Long, Long>? {
        val fields = header.split(';').mapNotNull { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size == 2) {
                kv[0].trim() to kv[1].trim().toLongOrNull()
            } else {
                null
            }
        }.toMap()
        val total = fields["total"] ?: return null
        val used = (fields["upload"] ?: 0L) + (fields["download"] ?: 0L)
        return Triple(used, total, fields["expire"] ?: 0L)
    }

    /** Make a fetched subscription the one the home page connects with. */
    fun activate(sub: Subscription) {
        profileFile(sub).copyTo(CoreBridge.profileFile(), overwrite = true)
    }

    /**
     * Refresh every subscription whose config is older than [maxAgeMs]
     * (a day by default). Runs at startup and when the subscriptions
     * page opens, so configs stay fresh without the user remembering
     * to update them. If the stale subscription is the active one, the
     * active profile is refreshed with it. Returns how many updated.
     */
    suspend fun autoUpdateStale(
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
    ): Int {
        val now = System.currentTimeMillis()
        val list = load()
        var updated = 0
        val newList = list.map { sub ->
            val file = profileFile(sub)
            val stale = file.exists() && now - sub.updatedAt > maxAgeMs
            if (!stale) {
                sub
            } else {
                val wasActive = CoreBridge.profileFile().exists() &&
                    CoreBridge.profileFile().readText() == file.readText()
                fetch(sub).getOrElse { return@map sub }.also { fresh ->
                    updated++
                    if (wasActive) activate(fresh)
                }
            }
        }
        if (updated > 0) save(newList)
        return updated
    }

    fun delete(sub: Subscription, list: List<Subscription>): List<Subscription> {
        profileFile(sub).delete()
        return list.filterNot { it.id == sub.id }
    }
}
