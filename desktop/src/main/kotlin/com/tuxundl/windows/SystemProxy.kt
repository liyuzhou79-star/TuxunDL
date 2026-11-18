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
 *
 * macOS: the per-service proxy of the current network service
 * (usually Wi-Fi), driven through `networksetup`. No sudo is
 * needed for an admin account — networksetup writes the user's own
 * SystemConfiguration preferences — but a non-admin account cannot
 * change it, and then [enable] fails honestly instead of pretending.
 * [isSupported] is false when no usable network service is found.
 */
object SystemProxy {

    private const val KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

    private val isWindows: Boolean = Platform.isWindows
    private val isLinux: Boolean = Platform.isLinux
    private val isMac: Boolean = Platform.isMac

    /** Set by a successful Linux [enable] in this process. */
    @Volatile
    private var linuxEnabledByUs = false

    /** Set by a successful macOS [enable] in this process. */
    @Volatile
    private var macEnabledByUs = false

    /** Cached GNOME probe result; null until first checked. */
    @Volatile
    private var gnomeAvailable: Boolean? = null

    /** Cached macOS network service name; null until first checked. */
    @Volatile
    private var macService: String? = null

    /** True once the macOS service lookup has run (hit or miss). */
    @Volatile
    private var macServiceLookedUp = false

    /**
     * Whether this machine's system proxy can be switched from here
     * at all: always true on Windows; on Linux only when the GNOME
     * proxy schema answers through gsettings; on macOS only when
     * networksetup finds a usable network service.
     */
    fun isSupported(): Boolean = when {
        isWindows -> true
        isLinux -> gnomeProxyAvailable()
        isMac -> macNetworkService() != null
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
        if (isMac) return enableMac(port)
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
        if (isMac) return disableMac()
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
        if (isMac) {
            val service = macNetworkService() ?: return false
            val web = networksetupGetProxy(service, "-getwebproxy") ?: return false
            return web.enabled && web.server == "127.0.0.1" && web.port == port
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

    // ---- macOS (networksetup) ----

    /** One proxy entry as `networksetup -get<kind>proxy` reports it. */
    private data class MacProxy(val enabled: Boolean, val server: String, val port: Int)

    /**
     * The network service whose proxy this app manages: "Wi-Fi"
     * when present, otherwise the first enabled service from
     * `networksetup -listallnetworkservices` (disabled services are
     * marked with a leading asterisk and skipped). Cached after the
     * first lookup; null when networksetup answers nothing usable.
     */
    private fun macNetworkService(): String? {
        if (macServiceLookedUp) return macService
        macServiceLookedUp = true
        macService = runCatching {
            val process = ProcessBuilder("networksetup", "-listallnetworkservices")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) return@runCatching null
            val services = output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("An asterisk") }
                .filter { !it.startsWith("*") }
                .toList()
            services.firstOrNull { it == "Wi-Fi" } ?: services.firstOrNull()
        }.getOrNull()
        return macService
    }

    private fun enableMac(port: Int): Boolean {
        val service = macNetworkService() ?: return false
        var ok = true
        // Hosts and ports first; the on-switches flip last, so
        // nothing ever sees a half-written proxy configuration.
        ok = networksetup("-setwebproxy", service, "127.0.0.1", port.toString()) && ok
        ok = networksetup("-setsecurewebproxy", service, "127.0.0.1", port.toString()) && ok
        ok = networksetup("-setsocksfirewallproxy", service, "127.0.0.1", port.toString()) && ok
        ok = networksetup("-setwebproxystate", service, "on") && ok
        ok = networksetup("-setsecurewebproxystate", service, "on") && ok
        ok = networksetup("-setsocksfirewallproxystate", service, "on") && ok
        if (ok) macEnabledByUs = true
        return ok
    }

    private fun disableMac(): Boolean {
        val service = macNetworkService() ?: return false
        // Only switch off what is ours: enabled by this process, or
        // a web proxy still pointing at this app's port (the
        // leftover of a crashed run). A proxy the user configured
        // for something else is never touched; when nothing is ours
        // the off state already holds, which counts as success.
        val ours = macEnabledByUs || pointsAtAppPort(SettingsStore.mixedPort())
        if (!ours) return true
        var ok = true
        ok = networksetup("-setwebproxystate", service, "off") && ok
        ok = networksetup("-setsecurewebproxystate", service, "off") && ok
        ok = networksetup("-setsocksfirewallproxystate", service, "off") && ok
        if (ok) macEnabledByUs = false
        return ok
    }

    /**
     * Parse one `networksetup -get<kind>proxy` answer, whose lines
     * look like "Enabled: Yes", "Server: 127.0.0.1", "Port: 7890".
     * Null when the query itself fails.
     */
    private fun networksetupGetProxy(service: String, command: String): MacProxy? =
        runCatching {
            val process = ProcessBuilder("networksetup", command, service)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) return@runCatching null
            var enabled = false
            var server = ""
            var port = -1
            for (line in output.lineSequence()) {
                val parts = line.split(":", limit = 2)
                if (parts.size != 2) continue
                when (parts[0].trim()) {
                    "Enabled" -> enabled = parts[1].trim() == "Yes"
                    "Server" -> server = parts[1].trim()
                    "Port" -> port = parts[1].trim().toIntOrNull() ?: -1
                }
            }
            MacProxy(enabled, server, port)
        }.getOrNull()

    private fun networksetup(vararg args: String): Boolean = runCatching {
        ProcessBuilder(listOf("networksetup") + args)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)
}
