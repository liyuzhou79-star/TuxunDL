package com.tuxundl.windows

import java.io.File

/**
 * Which desktop edition this build is, decided once from the OS the
 * app runs on. The Windows, Linux and Mac editions share this whole
 * source tree (the package name is historical); every platform
 * difference — system proxy, autostart, TUN elevation, update
 * tags — branches on these flags and keeps the other platforms'
 * behaviour untouched.
 */
object Platform {

    val isWindows: Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    val isLinux: Boolean =
        System.getProperty("os.name").contains("Linux", ignoreCase = true)

    val isMac: Boolean =
        System.getProperty("os.name").contains("Mac", ignoreCase = true)

    /** "Windows", "Linux" or "Mac", for the version labels in the UI. */
    val editionName: String
        get() = when {
            isLinux -> "Linux"
            isMac -> "Mac"
            else -> "Windows"
        }

    /**
     * Open a folder in the OS file manager: Explorer through AWT
     * on Windows (AWT's Desktop is unreliable on Linux desktops),
     * xdg-open on Linux, open on macOS. False when neither route
     * worked, so the caller can fall back to just showing the path.
     */
    fun openInFileManager(dir: File): Boolean = runCatching {
        if (isLinux) {
            ProcessBuilder("xdg-open", dir.absolutePath).start()
        } else if (isMac) {
            ProcessBuilder("open", dir.absolutePath).start()
        } else {
            java.awt.Desktop.getDesktop().open(dir)
        }
    }.isSuccess
}
