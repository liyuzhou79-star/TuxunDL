package com.tuxundl.windows

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the mihomo core process, the same core the Android edition
 * embeds. The core ships as a sidecar executable in the install
 * directory (on Windows with `wintun.dll` beside it for TUN mode;
 * Linux needs no driver — the kernel provides /dev/net/tun), and
 * this bridge starts it with a generated config, waits for its REST
 * API, and stops it cleanly.
 *
 * Modes:
 * - System proxy (default): the core runs unelevated and the user's
 *   system proxy is pointed at the core's mixed port while connected.
 * - TUN: the config gains a `tun:` block (see ConfigFactory) and the
 *   core must run with administrator/root rights. On Windows the
 *   whole app restarts elevated (see Elevation); on Linux the app
 *   stays unprivileged and only the core is started as root through
 *   pkexec.
 */
object CoreBridge {

    /**
     * The platform-default data root, before any user override.
     * Windows keeps its historical ~/.tuxundl-windows folder; Linux
     * follows the XDG base directory convention
     * (~/.local/share/tuxundl, honouring $XDG_DATA_HOME); macOS
     * follows the platform convention
     * (~/Library/Application Support/tuxundl).
     */
    val defaultWorkDir: File = when {
        Platform.isLinux -> {
            val xdgBase = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: File(System.getProperty("user.home"), ".local/share").path
            File(xdgBase, "tuxundl")
        }
        Platform.isMac -> File(
            System.getProperty("user.home"),
            "Library/Application Support/tuxundl",
        )
        else -> File(System.getProperty("user.home"), ".tuxundl-windows")
    }

    private const val DATA_DIR_POINTER_NAME = "data-dir.txt"

    /**
     * File in the default data dir recording a user-chosen data dir
     * (one line: its absolute path). It has to live outside the data
     * it points at — the settings file sits inside the data dir, so
     * it cannot hold the pointer to itself.
     */
    private val dataDirPointer: File
        get() = File(defaultWorkDir, DATA_DIR_POINTER_NAME)

    /**
     * App data root: config, profiles and the core log live here.
     * This is the single resolution point for the data dir: it is
     * resolved once at startup from [defaultWorkDir] plus the
     * pointer file, and [changeDataDir] re-points it live. Every
     * consumer reads this property at call time (never caches it),
     * so flipping it moves all later file access to the new folder.
     */
    @Volatile
    var workDir: File = resolveDataDir()
        private set

    private fun resolveDataDir(): File {
        val pointed = runCatching {
            val pointer = dataDirPointer
            if (pointer.exists()) {
                pointer.readText().trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { File(it) }
            } else {
                null
            }
        }.getOrNull()
        return pointed ?: defaultWorkDir
    }

    /** True when the data dir is a user-chosen one, not the default. */
    val usesCustomDataDir: Boolean
        get() = canonical(workDir) != canonical(defaultWorkDir)

    private fun canonical(file: File): File =
        runCatching { file.canonicalFile }.getOrDefault(file)

    /**
     * Move the data dir to [target]: copy everything across, record
     * the choice in the pointer file and switch [workDir] over, so
     * later file access uses the new folder right away. Pointing
     * back at the default removes the pointer instead. A core that
     * is already running keeps the home folder it was started with;
     * it picks the new dir up on its next start. False when the
     * copy or the pointer write failed — [workDir] is untouched
     * then, so nothing is left half-switched.
     */
    fun changeDataDir(target: File): Boolean {
        val from = workDir
        val to = canonical(target)
        if (canonical(from) == to) return true
        return runCatching {
            to.mkdirs()
            check(to.isDirectory) { "target is not a directory" }
            if (from.exists()) {
                from.listFiles()?.forEach { child ->
                    // The pointer records where the data lives; it
                    // belongs to the default dir, not to the data.
                    if (child.name == DATA_DIR_POINTER_NAME) return@forEach
                    check(
                        child.copyRecursively(
                            File(to, child.name),
                            overwrite = true,
                        ),
                    ) { "copy failed for ${child.name}" }
                }
            }
            if (to == canonical(defaultWorkDir)) {
                dataDirPointer.delete()
            } else {
                defaultWorkDir.mkdirs()
                dataDirPointer.writeText(to.absolutePath)
            }
            workDir = to
            true
        }.getOrDefault(false)
    }

    /**
     * The program's own file: the running executable (on Linux the
     * AppImage file itself when running from one). Shown in the
     * onboarding and the settings page so users can tell the
     * program's install location apart from their data folder.
     */
    fun programFile(): File {
        if (Platform.isLinux) {
            System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() }
                ?.let { return File(it) }
        }
        val command = runCatching {
            ProcessHandle.current().info().command().orElse("")
        }.getOrDefault("")
        return if (command.isNotBlank()) {
            File(command)
        } else {
            File(System.getProperty("user.dir"))
        }
    }

    // Compose state (not a plain flag) so the tray menu and the home
    // page both observe connection changes immediately.
    var running: Boolean by mutableStateOf(false)
        private set

    /** API client for the running core; null while stopped. */
    @Volatile
    var api: MihomoApi? = null
        private set

    /**
     * Why the last [start] failed, in user-facing words, when the
     * cause is one the user can act on (a busy port). Null when the
     * last start succeeded or failed for a generic reason — the UI
     * falls back to its "details in the log" message then.
     */
    @Volatile
    var lastStartError: String? = null
        private set

    /** True when nothing is bound to the port yet. */
    private fun isPortFree(port: Int): Boolean = runCatching {
        java.net.ServerSocket(port).use { }
        true
    }.getOrDefault(false)

    private var process: Process? = null

    /**
     * When the running core was started through pkexec (Linux TUN),
     * the pkill pattern that matches exactly this instance's command
     * line; null for a normally started core. See [stop].
     */
    private var pkexecKillPattern: String? = null

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
        lastStartError = null

        val exe = coreExecutable()
        if (!exe.exists()) {
            // First run of the packaged app: unpack the core that
            // ships inside the app resources (see the fetchCore task
            // in build.gradle.kts).
            extractBundled(exe.name, exe)
            if (Platform.isWindows) {
                extractBundled("wintun.dll", File(exe.parentFile, "wintun.dll"))
            }
        }
        if (!exe.exists()) {
            log("core executable not found at ${exe.absolutePath}")
            return@withContext false
        }

        // Port pre-flight: if the mixed or controller port is already
        // taken (another proxy client, or a leftover core from a run
        // that did not exit cleanly), the core would die on its bind
        // and the only symptom here would be a generic API timeout.
        // Name the port and the fix instead.
        val busyPort = listOf(mixedPort, controllerPort)
            .firstOrNull { !isPortFree(it) }
        if (busyPort != null) {
            val message = if (busyPort == mixedPort) {
                "连接失败：混合端口 $busyPort 已被占用，多半是其他代理程序" +
                    "或上次残留的核心还在用它。到设置页把混合端口改成别的" +
                    "数字，或者关掉占用它的程序再试"
            } else {
                "连接失败：控制端口 $busyPort 已被占用，多半是其他代理程序" +
                    "或上次残留的核心还在用它。关掉占用它的程序再试"
            }
            log(message)
            lastStartError = message
            return@withContext false
        }

        // Mac 1.0.0 has no TUN support: the root launch was never
        // verified on a real Mac, so refuse plainly instead of
        // starting a core that cannot create its tunnel.
        if (tunMode && Platform.isMac) {
            val message = "Mac 版 1.0.0 暂不支持 TUN 模式，请用系统代理模式连接"
            log(message)
            lastStartError = message
            return@withContext false
        }

        workDir.mkdirs()
        val configFile = File(workDir, "config.yaml")
        configFile.writeText(
            ConfigFactory.build(
                profileYaml, mixedPort, controllerPort, tunMode,
                SettingsStore.logLevel(),
                SettingsStore.coreMode(),
            ),
        )

        // Linux TUN: the core itself needs root (it creates the tun
        // device), so it is launched through pkexec while the app
        // stays unprivileged. Without pkexec there is no honest way
        // to do this — fail loudly instead of pretending. The root
        // core gets its own home folder (tun-home) so the files it
        // creates as root never block later unprivileged runs.
        var command = listOf(
            exe.absolutePath, "-d", workDir.absolutePath,
            "-f", configFile.absolutePath,
        )
        pkexecKillPattern = null
        if (tunMode && Platform.isLinux && !Elevation.isRoot()) {
            if (!Elevation.pkexecAvailable()) {
                log(
                    "TUN mode on Linux starts the core as root through " +
                        "pkexec, but pkexec was not found. Install polkit " +
                        "(pkexec) or use system-proxy mode instead.",
                )
                return@withContext false
            }
            val tunHome = File(workDir, "tun-home")
            tunHome.mkdirs()
            command = listOf(
                "pkexec", exe.absolutePath, "-d", tunHome.absolutePath,
                "-f", configFile.absolutePath,
            )
            pkexecKillPattern = "mihomo -d ${tunHome.absolutePath}"
        }

        val started = runCatching {
            ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File(workDir, "core.log")))
                .start()
        }.getOrElse {
            log("failed to launch core: ${it.message}")
            pkexecKillPattern = null
            return@withContext false
        }
        process = started

        val client = MihomoApi("http://127.0.0.1:$controllerPort")
        if (!client.waitReady()) {
            log("core did not answer its API in time, killing it")
            started.destroy()
            killPkexecCore()
            process = null
            return@withContext false
        }

        api = client
        running = true

        // In TUN mode the core routes everything itself; only the
        // system-proxy mode touches the user's proxy settings. A
        // failed switch is logged honestly — on Linux it usually
        // means the desktop is not GNOME, where the user has to set
        // the proxy by hand (the log line says so).
        if (!tunMode && !SystemProxy.enable(mixedPort)) {
            log(
                when {
                    Platform.isLinux -> {
                        "the system proxy was NOT set automatically: on " +
                            "Linux that needs a GNOME desktop. Set your " +
                            "system proxy to 127.0.0.1:$mixedPort manually " +
                            "to use this connection."
                    }
                    Platform.isMac -> {
                        "the system proxy was NOT set automatically: on " +
                            "macOS that needs an admin account and a " +
                            "network service networksetup can see. Set " +
                            "your system proxy to 127.0.0.1:$mixedPort " +
                            "manually to use this connection."
                    }
                    else -> "the system proxy could not be set (registry write failed)"
                },
            )
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
        killPkexecCore()
        process = null
        api = null
        running = false
    }

    /**
     * Best-effort cleanup for a core started through pkexec (Linux
     * TUN): destroying the pkexec wrapper does not guarantee the
     * root-owned core died with it, so when the controller still
     * answers afterwards, ask pkexec to kill exactly this instance
     * (matched by its unique home folder). If even that fails, the
     * leftover core holds the ports and the next start fails loudly
     * instead of silently — see the release notes' honesty section.
     */
    private fun killPkexecCore() {
        val pattern = pkexecKillPattern ?: return
        pkexecKillPattern = null
        val alive = runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", 9090), 300)
                true
            }
        }.getOrDefault(false)
        if (!alive) return
        runCatching {
            ProcessBuilder("pkexec", "pkill", "-f", pattern)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
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
