package com.tuxundl.windows

/**
 * The per-user system proxy switch.
 *
 * Windows: the HKCU Internet Settings keys, driven through `reg.exe`
 * so the app needs no native libraries. Only ever touches the
 * current user's hive, and [disable] restores the off state on exit.
 *
 * Linux: the GNOME system proxy (`org.gnome.system.proxy`), driven
 * through `gsettings` — the settings GNOME apps and browsers on
 * GNOME follow. Desktops where that schema does not answer are
 * reported as unsupported by [isSupported], so callers can say so
 * honestly instead of pretending the proxy was set.
 */
object SystemProxy {

    private const val KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

    private val isWindows: Boolean = Platform.isWindows
    private val isLinux: Boolean = Platform.isLinux

    /** Set by a successful Linux [enable] in this process. */
    @Volatile
    private var linuxEnabledByUs = false

    /** Cached GNOME probe result; null until first checked. */
    @Volatile
    private var gnomeAvailable: Boolean? = null

    /**
     * Whether this machine's system proxy can be switched from here
     * at all: always true on Windows; on Linux only when the GNOME
     * proxy schema answers through gsettings.
     */
    fun isSupported(): Boolean = when {
        isWindows -> true
        isLinux -> gnomeProxyAvailable()
        else -> false
    }

    fun enable(port: Int): Boolean {
        if (isWindows) {
            val server = reg(
                "add", KEY, "/v", "ProxyServer", "/t", "REG_SZ",
                "/d", "127.0.0.1:$port", "/f",
            )
            val on = reg(
                "add", KEY, "/v", "ProxyEnable", "/t", "REG_DWORD",
                "/d", "1", "/f",
            )
            return server && on
        }
        if (isLinux) return enableGnome(port)
        return false
    }

    fun disable(): Boolean {
        if (isWindows) {
            return reg(
                "add", KEY, "/v", "ProxyEnable", "/t", "REG_DWORD",
                "/d", "0", "/f",
            )
        }
        if (isLinux) return disableGnome()
        return false
    }

    /**
     * True when the system proxy is switched on and points at this
     * app's own mixed port on loopback — the leftover state a crashed
     * or killed app leaves behind: the core is gone, but the desktop
     * still sends browsers to the dead port. Read-only; only the
     * exact loopback address of this app counts, so another program's
     * proxy setup is never mistaken for ours.
     */
    fun pointsAtAppPort(port: Int): Boolean {
        if (isWindows) {
            val enabled = queryValue("ProxyEnable")
                ?.let { it == "0x1" || it == "1" } ?: false
            if (!enabled) return false
            val server = queryValue("ProxyServer")?.trim() ?: return false
            return server == "127.0.0.1:$port" ||
                server == "http://127.0.0.1:$port" ||
                server == "https://127.0.0.1:$port"
        }
        if (isLinux) {
            if (!gnomeProxyAvailable()) return false
            val mode = gsettingsGet("org.gnome.system.proxy", "mode")
                ?.trim('\'') ?: return false
            if (mode != "manual") return false
            val host = gsettingsGet("org.gnome.system.proxy.http", "host")
                ?.trim('\'') ?: return false
            val proxyPort = gsettingsGet("org.gnome.system.proxy.http", "port")
                ?.toIntOrNull() ?: return false
            return host == "127.0.0.1" && proxyPort == port
        }
        return false
    }

    // ---- Windows (reg.exe) ----

    /**
     * The raw value of one Internet Settings entry, read with
     * `reg query`; null when the entry or the read is unavailable.
     * Output lines look like "    ProxyServer    REG_SZ    127.0.0.1:7890".
     */
    private fun queryValue(name: String): String? {
        if (!isWindows) return null
        return runCatching {
            val process = ProcessBuilder("reg", "query", KEY, "/v", name)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) return@runCatching null
            val line = output.lineSequence()
                .firstOrNull { it.trimStart().startsWith(name) }
                ?: return@runCatching null
            val parts = line.trim().split(Regex("\\s+"))
            // Entry name, type token, then the value itself.
            if (parts.size >= 3) parts.drop(2).joinToString(" ") else null
        }.getOrNull()
    }

    private fun reg(vararg args: String): Boolean = runCatching {
        val process = ProcessBuilder(listOf("reg") + args)
            .redirectErrorStream(true)
            .start()
        process.waitFor() == 0
    }.getOrDefault(false)

    // ---- Linux (GNOME gsettings) ----

    /** Probe once: does the GNOME proxy schema answer at all? */
    private fun gnomeProxyAvailable(): Boolean {
        gnomeAvailable?.let { return it }
        val ok = runCatching {
            val process = ProcessBuilder(
                "gsettings", "get", "org.gnome.system.proxy", "mode",
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        }.getOrDefault(false)
        gnomeAvailable = ok
        return ok
    }

    private fun enableGnome(port: Int): Boolean {
        if (!gnomeProxyAvailable()) return false
        var ok = true
        // Hosts and ports first; the mode flips to manual last, so
        // nothing ever sees a half-written manual configuration.
        ok = gsettingsSet("org.gnome.system.proxy.http", "host", "127.0.0.1") && ok
        ok = gsettingsSet("org.gnome.system.proxy.http", "port", port.toString()) && ok
        ok = gsettingsSet("org.gnome.system.proxy.https", "host", "127.0.0.1") && ok
        ok = gsettingsSet("org.gnome.system.proxy.https", "port", port.toString()) && ok
        ok = gsettingsSet("org.gnome.system.proxy.socks", "host", "127.0.0.1") && ok
        ok = gsettingsSet("org.gnome.system.proxy.socks", "port", port.toString()) && ok
        ok = gsettingsSet("org.gnome.system.proxy", "mode", "manual") && ok
        if (ok) linuxEnabledByUs = true
        return ok
    }

    private fun disableGnome(): Boolean {
        if (!gnomeProxyAvailable()) return false
        // Only switch off what is ours: enabled by this process, or
        // a manual config still pointing at this app's port (the
        // leftover of a crashed run). A proxy the user configured
        // for something else is never touched; when nothing is ours
        // the off state already holds, which counts as success.
        val ours = linuxEnabledByUs || pointsAtAppPort(SettingsStore.mixedPort())
        if (!ours) return true
        val ok = gsettingsSet("org.gnome.system.proxy", "mode", "none")
        if (ok) linuxEnabledByUs = false
        return ok
    }

    private fun gsettingsSet(schema: String, key: String, value: String): Boolean =
        runCatching {
            ProcessBuilder("gsettings", "set", schema, key, value)
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        }.getOrDefault(false)

    /** Raw `gsettings get` output (strings come back single-quoted). */
    private fun gsettingsGet(schema: String, key: String): String? = runCatching {
        val process = ProcessBuilder("gsettings", "get", schema, key)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) output else null
    }.getOrNull()
}
