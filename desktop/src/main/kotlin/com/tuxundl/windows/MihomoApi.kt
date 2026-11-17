package com.tuxundl.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Client for the mihomo core's loopback REST API, the same control
 * surface the Android service talks to: version, proxy groups, node
 * selection, delay tests and connection totals.
 */
class MihomoApi(private val base: String = "http://127.0.0.1:9090") {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun request(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(10))

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.send(
                request(path).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() == 200) response.body() else null
        }.getOrNull()
    }

    /** Core version string, or null when the core is not answering. */
    suspend fun version(): String? =
        get("/version")?.let { body ->
            runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["version"]?.jsonPrimitive?.content
            }.getOrNull()
        }

    /** Poll until the core answers, giving up after [attempts] tries. */
    suspend fun waitReady(attempts: Int = 50): Boolean {
        repeat(attempts) {
            if (version() != null) return true
            delay(200)
        }
        return false
    }

    /**
     * All proxy entries by name. Group entries carry an `all` list of
     * member names and a `now` field with the current selection.
     */
    suspend fun proxies(): Map<String, kotlinx.serialization.json.JsonObject> =
        get("/proxies")?.let { body ->
            runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["proxies"]?.jsonObject
                    ?.mapValues { it.value.jsonObject }
            }.getOrNull()
        } ?: emptyMap()

    /** Switch the selected node of a proxy group. */
    suspend fun selectProxy(group: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(group, "UTF-8").replace("+", "%20")
                val body = buildJsonObject { put("name", name) }.toString()
                val response = client.send(
                    request("/proxies/$encoded")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                response.statusCode() in listOf(200, 204)
            }.getOrDefault(false)
        }

    /** One delay test for a node, in milliseconds; null on failure. */
    suspend fun delay(name: String, timeoutMs: Int = 5000): Long? {
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val url = URLEncoder.encode("http://www.gstatic.com/generate_204", "UTF-8")
        return get("/proxies/$encoded/delay?timeout=$timeoutMs&url=$url")?.let { body ->
            runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["delay"]?.jsonPrimitive?.longOrNull
            }.getOrNull()
        }
    }

    /** Cumulative (download, upload) bytes across all connections. */
    suspend fun totals(): Pair<Long, Long>? =
        get("/connections")?.let { body ->
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val down = root["downloadTotal"]?.jsonPrimitive?.longOrNull ?: 0L
                val up = root["uploadTotal"]?.jsonPrimitive?.longOrNull ?: 0L
                down to up
            }.getOrNull()
        }
}
