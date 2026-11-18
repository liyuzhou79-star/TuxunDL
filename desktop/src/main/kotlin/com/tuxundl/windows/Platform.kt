package com.tuxundl.windows

/**
 * Which desktop edition this build is, decided once from the OS the
 * app runs on. The Windows and Linux editions share this whole
 * source tree (the package name is historical); every platform
 * difference — system proxy, autostart, TUN elevation, update
 * tags — branches on these flags and keeps the other platform's
 * behaviour untouched.
 */
object Platform {

    val isWindows: Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    val isLinux: Boolean =
        System.getProperty("os.name").contains("Linux", ignoreCase = true)

    /** "Windows" or "Linux", for the version labels in the UI. */
    val editionName: String
        get() = if (isLinux) "Linux" else "Windows"
}
