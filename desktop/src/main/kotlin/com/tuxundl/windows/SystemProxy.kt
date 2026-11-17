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

    private fun reg(vararg args: String): Boolean = runCatching {
        val process = ProcessBuilder(listOf("reg") + args)
            .redirectErrorStream(true)
            .start()
        process.waitFor() == 0
    }.getOrDefault(false)
}
