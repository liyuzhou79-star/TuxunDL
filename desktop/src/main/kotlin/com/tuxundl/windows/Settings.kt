package com.tuxundl.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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

    /**
     * Launch at sign-in, per-user, no admin: the HKCU Run key on
     * Windows, an XDG autostart entry on Linux.
     */
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

    /**
     * Sort the nodes page by delay automatically whenever a speed
     * test finishes. Off by default; the manual "按延迟排序" chip
     * on the nodes page works either way.
     */
    fun autoSortAfterTest(): Boolean =
        read()["autoSortAfterTest"]?.jsonPrimitive?.booleanOrNull ?: false

    fun setAutoSortAfterTest(value: Boolean) {
        write(
            mapOf(
                "autoSortAfterTest" to
                    kotlinx.serialization.json.JsonPrimitive(value),
            ),
        )
    }

    /** Hours between automatic subscription refreshes (default 24). */
    fun autoUpdateIntervalHours(): Int =
        read()["autoUpdateIntervalHours"]?.jsonPrimitive?.intOrNull ?: 24

    fun setAutoUpdateIntervalHours(value: Int) {
        write(
            mapOf(
                "autoUpdateIntervalHours" to
                    kotlinx.serialization.json.JsonPrimitive(value),
            ),
        )
    }

    /** Core log verbosity, written into the generated config. */
    fun logLevel(): String =
        read()["logLevel"]?.jsonPrimitive?.contentOrNull ?: "info"

    fun setLogLevel(value: String) {
        write(mapOf("logLevel" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    /**
     * Core routing mode: "rule" (profile rules decide), "global"
     * (everything via the selected node) or "direct" (nothing via a
     * node). Written into the generated config like the log level,
     * so a change takes effect on the next connect.
     */
    fun coreMode(): String =
        read()["coreMode"]?.jsonPrimitive?.contentOrNull ?: "rule"

    fun setCoreMode(value: String) {
        write(mapOf("coreMode" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    /** Theme accent color as an ARGB hex string (see ThemeState). */
    fun themeColorHex(): String? =
        read()["themeColor"]?.jsonPrimitive?.contentOrNull

    fun setThemeColorHex(value: String) {
        write(mapOf("themeColor" to kotlinx.serialization.json.JsonPrimitive(value)))
    }

    /** Favorite node names, starred on the nodes page. Stored by name. */
    fun favoriteNodes(): Set<String> =
        read()["favoriteNodes"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet() ?: emptySet()

    fun setFavoriteNodes(value: Set<String>) {
        write(
            mapOf(
                "favoriteNodes" to kotlinx.serialization.json.JsonArray(
                    value.map { kotlinx.serialization.json.JsonPrimitive(it) },
                ),
            ),
        )
    }

    /** All settings as one JSON object, for backup export. */
    fun snapshot(): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.JsonObject(read())

    /** Replace all settings at once, for backup restore. */
    fun restore(snapshot: kotlinx.serialization.json.JsonObject) {
        CoreBridge.workDir.mkdirs()
        file.writeText(snapshot.toString())
        // The system autostart entry is a side effect of its
        // setting; re-apply it so a restored value actually takes hold.
        applyAutoStart(autoStart())
    }

    private fun applyAutoStart(value: Boolean) {
        if (Platform.isLinux) {
            applyAutoStartLinux(value)
            return
        }
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

    /**
     * Linux autostart is an XDG desktop entry in ~/.config/autostart,
     * honoured by GNOME, KDE and the other major desktops. For the
     * AppImage build the entry must point at the AppImage file
     * itself — the launcher path inside a mounted AppImage is
     * temporary — which the AppImage runtime exposes as $APPIMAGE.
     */
    private fun applyAutoStartLinux(value: Boolean) {
        val dir = File(System.getProperty("user.home"), ".config/autostart")
        val entry = File(dir, "tuxundl.desktop")
        runCatching {
            if (value) {
                val exe = System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() }
                    ?: ProcessHandle.current().info().command().orElse("")
                if (exe.isBlank()) return
                dir.mkdirs()
                entry.writeText(
                    "[Desktop Entry]\n" +
                        "Type=Application\n" +
                        "Name=TuxunDL\n" +
                        "Exec=\"$exe\"\n" +
                        "X-GNOME-Autostart-enabled=true\n",
                )
            } else {
                entry.delete()
            }
        }
    }
}
