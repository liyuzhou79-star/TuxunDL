package com.tuxundl.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

/**
 * TUN mode needs the core to create a virtual network adapter, which
 * Windows only allows for administrators. When the user picks TUN and
 * the app is not elevated, the home page offers a restart as
 * administrator: the system shows its standard UAC prompt and the
 * elevated instance replaces this one. Nothing is bypassed or hidden.
 *
 * Linux reaches the same end differently: the app itself never
 * elevates — TUN mode starts only the core as root through pkexec
 * (see CoreBridge), and the helpers for that live here too.
 */
object Elevation {

    fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", true)

    fun isLinux(): Boolean = Platform.isLinux

    /** True when this process already runs as root (Linux). */
    fun isRoot(): Boolean {
        if (!isLinux()) return false
        if (System.getProperty("user.name") == "root") return true
        return runCatching {
            val proc = ProcessBuilder("id", "-u")
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out == "0"
        }.getOrDefault(false)
    }

    /**
     * On Linux the app itself never elevates: TUN mode launches only
     * the core through pkexec, the desktop's standard graphical
     * privilege prompt (part of polkit). False when pkexec is not
     * installed, in which case TUN cannot be offered honestly.
     */
    fun pkexecAvailable(): Boolean {
        if (!isLinux()) return false
        return runCatching {
            val proc = ProcessBuilder("pkexec", "--version")
                .redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0
        }.getOrDefault(false)
    }

    /** True when this process already runs with administrator rights. */
    suspend fun isElevated(): Boolean = withContext(Dispatchers.IO) {
        if (!isWindows()) return@withContext false
        runCatching {
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "([Security.Principal.WindowsPrincipal]" +
                    "[Security.Principal.WindowsIdentity]::GetCurrent())" +
                    ".IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)",
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.equals("True", ignoreCase = true)
        }.getOrDefault(false)
    }

    /** Restart this app elevated (UAC prompt) and exit this instance. */
    fun relaunchElevated() {
        val command = ProcessHandle.current().info().command().orElse(null)
            ?: return
        val escaped = command.replace("'", "''")
        runCatching {
            ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Start-Process -FilePath '$escaped' -Verb RunAs",
            ).start()
        }
        CoreBridge.stop()
        exitProcess(0)
    }
}
