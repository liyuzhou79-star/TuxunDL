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
 */
object Elevation {

    fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", true)

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
