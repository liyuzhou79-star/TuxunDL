package com.tuxundl.windows

import java.io.File

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

    /**
     * Open a folder in the OS file manager: Explorer through AWT
     * on Windows (AWT's Desktop is unreliable on Linux desktops),
     * xdg-open on Linux. False when neither route worked, so the
     * caller can fall back to just showing the path.
     */
    fun openInFileManager(dir: File): Boolean = runCatching {
        if (isLinux) {
            ProcessBuilder("xdg-open", dir.absolutePath).start()
        } else {
            java.awt.Desktop.getDesktop().open(dir)
        }
    }.isSuccess
}
