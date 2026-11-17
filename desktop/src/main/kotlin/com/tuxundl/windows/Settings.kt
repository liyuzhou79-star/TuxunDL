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

    /** Launch at Windows sign-in (HKCU Run key, per-user, no admin). */
    fun autoStart(): Boolean =
        read()["autoStart"]?.jsonPrimitive?.booleanOrNull ?: false

    fun setAutoStart(value: Boolean) {
        write(mapOf("autoStart" to kotlinx.serialization.json.JsonPrimitive(value)))
        applyAutoStart(value)
    }

    /** Connect on launch when a subscription profile is ready. */
    fun autoConnect(): Boolean =
        read()["autoConnect"]?.jsonPrimitive?.booleanOrNull ?: false

    fun setAutoConnect(value: Boolean) {
        write(mapOf("autoConnect" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    /**
     * Closing the window hides it to the tray instead of quitting;
     * on by default. Read fresh at close time so toggling the setting
     * takes effect without a restart.
     */
    fun closeToTray(): Boolean =
        read()["closeToTray"]?.jsonPrimitive?.booleanOrNull ?: true

    fun setCloseToTray(value: Boolean) {
        write(mapOf("closeToTray" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    private fun applyAutoStart(value: Boolean) {
        if (!Elevation.isWindows()) return
        val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        runCatching {
            if (value) {
                val exe = ProcessHandle.current().info().command().orElse("")
                if (exe.isNotBlank()) {
                    ProcessBuilder(
                        "reg", "add", key, "/v", "TuxunDL", "/t", "REG_SZ",
                        "/d", "\"$exe\"", "/f",
                    ).start().waitFor()
                }
            } else {
                ProcessBuilder("reg", "delete", key, "/v", "TuxunDL", "/f")
                    .start().waitFor()
            }
        }
    }
}
