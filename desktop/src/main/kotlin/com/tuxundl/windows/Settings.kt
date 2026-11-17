package com.tuxundl.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * User settings for the Windows edition, stored as plain JSON in the
 * app data folder. Deliberately tiny: connection mode and ports. The
 * home page reads these when connecting.
 */
object SettingsStore {

    private val file: File
        get() = File(CoreBridge.workDir, "settings.json")

    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): Map<String, kotlinx.serialization.json.JsonElement> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
        }.getOrDefault(emptyMap())
    }

    private fun write(values: Map<String, kotlinx.serialization.json.JsonElement>) {
        CoreBridge.workDir.mkdirs()
        val merged = read() + values
        file.writeText(buildJsonObject {
            merged.forEach { (key, value) -> put(key, value) }
        }.toString())
    }

    /** TUN mode on/off; system proxy is the default mode. */
    fun tunMode(): Boolean =
        read()["tunMode"]?.jsonPrimitive?.booleanOrNull ?: false

    fun setTunMode(value: Boolean) {
        write(mapOf("tunMode" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    /** Local mixed port the core listens on. */
    fun mixedPort(): Int =
        read()["mixedPort"]?.jsonPrimitive?.intOrNull ?: 7890

    fun setMixedPort(value: Int) {
        write(mapOf("mixedPort" to kotlinx.serialization.json.JsonPrimitive(value)))
    }
}
