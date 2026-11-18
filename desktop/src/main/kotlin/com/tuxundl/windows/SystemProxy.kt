package com.tuxundl.windows

/**
 * The Windows per-user system proxy switch (HKCU Internet Settings),
 * driven through `reg.exe` so the app needs no native libraries.
 * Only ever touches the current user's hive, and [disable] restores
 * the off state on exit. Calls are no-ops off Windows.
 */
object SystemProxy {

    private const val KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"

    private val isWindows: Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    fun enable(port: Int): Boolean {
        if (!isWindows) return false
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

    fun disable(): Boolean {
        if (!isWindows) return false
        return reg(
            "add", KEY, "/v", "ProxyEnable", "/t", "REG_DWORD",
            "/d", "0", "/f",
        )
    }

    /**
     * True when the system proxy is switched on and points at this
     * app's own mixed port on loopback — the leftover state a crashed
     * or killed app leaves behind: the core is gone, but Windows
     * still sends browsers to the dead port. Read-only; only the
     * exact loopback address of this app counts, so another program's
     * proxy setup is never mistaken for ours.
     */
    fun pointsAtAppPort(port: Int): Boolean {
        if (!isWindows) return false
        val enabled = queryValue("ProxyEnable")
            ?.let { it == "0x1" || it == "1" } ?: false
        if (!enabled) return false
        val server = queryValue("ProxyServer")?.trim() ?: return false
        return server == "127.0.0.1:$port" ||
            server == "http://127.0.0.1:$port" ||
            server == "https://127.0.0.1:$port"
    }

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
}
