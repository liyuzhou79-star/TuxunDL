package com.tuxundl.windows

/**
 * Bridge to the mihomo core, the same core the Android edition embeds.
 *
 * Design (1.0.0):
 * - The installer bundles the Windows build of mihomo as `mihomo.exe`
 *   next to the app, plus `wintun.dll` for TUN mode. The core version is
 *   pinned to the same revision the Android side builds from this repo.
 * - The app starts the core as a child process with its own config
 *   directory and talks to it over the core's loopback REST API, the
 *   same control surface the Android service uses: proxies, traffic,
 *   connections, delay tests and config reload all map one to one.
 * - System proxy mode needs no elevation: the app points the Windows
 *   user proxy settings at the core's mixed port and restores them on
 *   exit.
 * - TUN mode needs elevation for the core only: the config gains a
 *   `tun:` block (stack: system, dns-hijack) and the core process is
 *   started elevated while the UI keeps running unelevated.
 *
 * Nothing here is wired up yet; this object fixes the shape so the UI
 * and the packaging grow against a stable seam.
 */
object CoreBridge {

    /** True once the core process is running and answering its API. */
    var running: Boolean = false
        private set

    /** Start the core with the given profile config. TODO in 1.0.0 dev. */
    fun start(configYaml: String, tunMode: Boolean): Boolean {
        // TODO: write config, spawn mihomo.exe, wait for the API, set proxy.
        return false
    }

    /** Stop the core and restore the user's proxy settings. */
    fun stop() {
        // TODO: restore proxy settings, terminate the core process.
        running = false
    }
}
