package com.tuxundl.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the mihomo core process, the same core the Android edition
 * embeds. On Windows the core ships as a sidecar executable in the
 * install directory (with `wintun.dll` beside it for TUN mode), and
 * this bridge starts it with a generated config, waits for its REST
 * API, and stops it cleanly.
 *
 * Modes:
 * - System proxy (default): the core runs unelevated and the user's
 *   system proxy is pointed at the core's mixed port while connected.
 * - TUN: the config gains a `tun:` block (see ConfigFactory) and the
 *   core must run elevated. Elevation of the sidecar is handled at
 *   packaging time; this bridge only notes the requirement in the log.
 */
object CoreBridge {

    /** App data root: config, profiles and the core log live here. */
    val workDir: File = File(System.getProperty("user.home"), ".tuxundl-windows")

    @Volatile
    var running: Boolean = false
        private set

    /** API client for the running core; null while stopped. */
    @Volatile
    var api: MihomoApi? = null
        private set

    private var process: Process? = null

    /** The profile the home page connects with, once subscriptions land. */
    fun profileFile(): File = File(workDir, "profiles/current.yaml")

    /**
     * Locate the bundled core executable. `tuxundl.coreDir` (set by the
     * packaged launcher) wins; otherwise look next to the working dir
     * for development runs.
     */
    fun coreExecutable(): File {
        val fileName = if (System.getProperty("os.name").contains("Windows", true)) {
            "mihomo.exe"
        } else {
            "mihomo"
        }
        val dir = System.getProperty("tuxundl.coreDir")
            ?.let { File(it) }
            ?: File(workDir, "core")
        return File(dir, fileName)
    }

    /**
     * Write the effective config and start the core. Returns false with
     * no side effects beyond the log when the executable is missing or
     * the API never comes up.
     */
    suspend fun start(
        profileYaml: String,
        tunMode: Boolean,
        mixedPort: Int = 7890,
        controllerPort: Int = 9090,
    ): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext true

        val exe = coreExecutable()
        if (!exe.exists()) {
            // First run of the packaged app: unpack the core that
            // ships inside the app resources (see the fetchCore task
            // in build.gradle.kts).
            extractBundled(exe.name, exe)
            extractBundled("wintun.dll", File(exe.parentFile, "wintun.dll"))
        }
        if (!exe.exists()) {
            log("core executable not found at ${exe.absolutePath}")
            return@withContext false
        }

        workDir.mkdirs()
        val configFile = File(workDir, "config.yaml")
        configFile.writeText(
            ConfigFactory.build(profileYaml, mixedPort, controllerPort, tunMode),
        )

        val started = runCatching {
            ProcessBuilder(exe.absolutePath, "-d", workDir.absolutePath, "-f", configFile.absolutePath)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File(workDir, "core.log")))
                .start()
        }.getOrElse {
            log("failed to launch core: ${it.message}")
            return@withContext false
        }
        process = started

        val client = MihomoApi("http://127.0.0.1:$controllerPort")
        if (!client.waitReady()) {
            log("core did not answer its API in time, killing it")
            started.destroy()
            process = null
            return@withContext false
        }

        api = client
        running = true

        // In TUN mode the core routes everything itself; only the
        // system-proxy mode touches the user's proxy settings.
        if (!tunMode) {
            SystemProxy.enable(mixedPort)
        }

        log("core started, version ${client.version()}")
        true
    }

    /** Stop the core and always restore the user's proxy state. */
    fun stop() {
        SystemProxy.disable()
        process?.let {
            it.destroy()
            runCatching { it.waitFor() }
        }
        process = null
        api = null
        running = false
    }

    /** Copy a bundled core file out of the app resources, once. */
    private fun extractBundled(fileName: String, target: File) {
        if (target.exists()) return
        val stream = CoreBridge::class.java
            .getResourceAsStream("/core/$fileName") ?: return
        target.parentFile?.mkdirs()
        stream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setExecutable(true)
    }

    private fun log(message: String) {
        runCatching {
            workDir.mkdirs()
            File(workDir, "app.log").appendText("$message\n")
        }
    }
}
