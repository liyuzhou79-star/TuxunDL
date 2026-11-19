package com.tuxundl.android.tor

import IPtProxy.Controller
import IPtProxy.IPtProxy
import IPtProxy.OnTransportEvents
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The app's Tor engine (1.7.3).
 *
 * Runs the bundled tor daemon (Guardian Project tor-android) as a
 * child process and the pluggable transports in-process via IPtProxy
 * (lyrebird obfs4/webtunnel/meek-lite, snowflake), the same way
 * Orbot wires the same two libraries together:
 *
 *   Controller.start(transport, null) opens a local SOCKS listener,
 *   torrc points ClientTransportPlugin at it, Bridge lines do the rest.
 *
 * On top of the raw engine sit the pieces the rest of the app
 * relies on: an awaitable exit verification (IsTor through Tor's
 * own check service), a heartbeat file the service process watches
 * while the tunnel exits through Tor, and a circuit reader that
 * publishes the live three-hop path for the globe — decoupled from
 * the exit test, tolerant of single-relay failures, and geolocated
 * hop by hop as results arrive.
 */
object TorManager {

    const val SOCKS_PORT = 19050
    const val CONTROL_PORT = 19051
    const val HTTP_TUNNEL_PORT = 19052

    enum class Mode(val label: String, val via: String) {
        AUTO("智能（自动尝试）", "智能"),
        DIRECT("直连（无网桥）", "直连"),
        SNOWFLAKE("Snowflake（内置桥，无需地址）", "Snowflake"),
        OBFS4("obfs4（内置桥）", "obfs4"),
        MEEK("meek（内置桥）", "meek"),
        CUSTOM("自定义网桥（可粘贴多行，支持 obfs4 / webtunnel / snowflake 等）", "自定义网桥")
    }

    /** Attempts of the 智能 chain, in order, and the stall budget. */
    private val autoChain = listOf(Mode.SNOWFLAKE, Mode.OBFS4, Mode.MEEK)
    private const val STALL_TIMEOUT_MS = 45_000L

    /** One relay of the current circuit; lat/lon null when unlocated. */
    data class Hop(val place: String, val lat: Double?, val lon: Double?)

    private data class GeoInfo(
        val country: String,
        val display: String,
        val lat: Double?,
        val lon: Double?
    )

    /** A relay of the live circuit, before geolocation. */
    private data class RelayRef(val fingerprint: String, val nickname: String?)

    data class UiState(
        val running: Boolean = false,
        val bootstrap: Int = -1,
        val status: String = "未启动",
        val exitResult: String? = null,
        val exitIp: String? = null,
        val exitLocation: String? = null,
        val exitLat: Double? = null,
        val exitLon: Double? = null,
        /** Which transport actually connected (matters for 智能）. */
        val connectedVia: String? = null,
        val hops: List<Hop> = emptyList(),
        val testing: Boolean = false,
        val logs: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Invoked when the tor process dies after a successful connect
     * (nobody is supervising anymore at that point). The egress
     * layer uses it to cut the tunnel back to the profile's own
     * exit immediately.
     */
    @Volatile
    var onUnexpectedExit: (() -> Unit)? = null

    // Built-in bridge lines, as published by the Tor Project and
    // shipped by Orbot (app/src/main/assets/builtin-bridges.json in
    // guardianproject/orbot-android; fetched 2026-11-19 and
    // re-verified against Orbot master the same day — the published
    // set was unchanged: 2 snowflake, 5 unique obfs4 (the JSON lists
    // 7 with duplicates), 1 meek_lite). These are the Tor Project's
    // public built-ins; users' own bridge lines are never in code —
    // they live in TorLocalStore on the device only.
    // There is no published built-in WebTunnel line, so WebTunnel is
    // reachable through the custom-bridge input only.
    private val snowflakeBridges = listOf(
        "snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn",
        "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn"
    )

    private val obfs4Bridges = listOf(
        "obfs4 45.145.95.6:27015 C5B7CD6946FF10C5B3E89691A7D3F2C122D2117C cert=TD7PbUO0/0k6xYHMPW3vJxICfkMZNdkRrb63Zhl5j9dW3iRGiCx0A7mPhe5T2EDzQ35+Zw iat-mode=0",
        "obfs4 51.222.13.177:80 5EDAC3B810E12B01F6FD8050D2FD3E277B289A08 cert=2uplIpLQ0q9+0qMFrK5pkaYRDOe460LL9WHBvatgkuRr/SL31wBOEupaMMJ6koRE6Ld0ew iat-mode=0",
        "obfs4 212.83.43.95:443 BFE712113A72899AD685764B211FACD30FF52C31 cert=ayq0XzCwhpdysn5o0EyDUbmSOx3X/oTEbzDMvczHOdBJKlvIdHHLJGkZARtT4dcBFArPPg iat-mode=1",
        "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0",
        "obfs4 212.83.43.74:443 39562501228A4D5E27FCA4C0C81A01EE23AE3EE4 cert=PBwr+S8JTVZo6MPdHnkTwXJPILWADLqfMGoVvhZClMq/Urndyd42BwX9FJHZnBB3H0XCw iat-mode=1"
    )

    private val meekBridges = listOf(
        "meek_lite 192.0.2.20:80 url=https://1603026938.rsc.cdn77.org front=www.phpmyadmin.net utls=HelloRandomizedALPN"
    )

    private var controller: Controller? = null
    private var process: Process? = null
    private val startedTransports = mutableListOf<String>()

    private val transportEvents = object : OnTransportEvents {
        override fun connected(name: String?) {
            appendLog("[PT] $name 已连接")
        }

        override fun error(name: String?, error: Exception?) {
            appendLog("[PT] $name 错误: ${error?.message}")
        }

        override fun stopped(name: String?, error: Exception?) {
            appendLog("[PT] $name 已停止 ${error?.message ?: ""}".trim())
        }
    }

    private fun appendLog(line: String) {
        _state.update { current ->
            val logs = (current.logs + line).takeLast(400)
            current.copy(logs = logs)
        }
    }

    private fun bridgePiece(line: String, key: String): String? =
        line.split(" ").firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

    /** Mirror Orbot: snowflake needs its broker/front/ice config on the controller. */
    private fun configureSnowflake(controller: Controller, bridgeLine: String) {
        val fronts = mutableSetOf<String>()
        bridgePiece(bridgeLine, "front")?.let { fronts.add(it) }
        bridgePiece(bridgeLine, "fronts")?.split(",")?.let { fronts.addAll(it) }

        controller.snowflakeIceServers = bridgePiece(bridgeLine, "ice") ?: ""
        controller.snowflakeBrokerUrl = bridgePiece(bridgeLine, "url") ?: ""
        controller.snowflakeFrontDomains = fronts.joinToString(",")
        controller.snowflakeAmpCacheUrl = ""
        controller.snowflakeSqsUrl = ""
        controller.snowflakeSqsCreds = ""
    }

    private fun transportConstant(token: String): String? = when (token) {
        "obfs4" -> IPtProxy.Obfs4
        "snowflake" -> IPtProxy.Snowflake
        "meek_lite", "meek" -> IPtProxy.MeekLite
        "webtunnel" -> IPtProxy.Webtunnel
        "dnstt" -> IPtProxy.Dnstt
        else -> null
    }

    @Volatile
    private var autoExitChecked = false

    /**
     * Identifies the current connection session. start() and stop()
     * both bump it; supervisors and watchers from an older session
     * see the mismatch and quietly leave.
     */
    @Volatile
    private var sessionId = 0

    /** Set by the log reader when the current tor process dies. */
    @Volatile
    private var processExited = false

    // ---- heartbeat -------------------------------------------------
    // While tor runs, a fresh timestamp lands in filesDir/tor/
    // heartbeat every few seconds. The service process watches it
    // whenever the tunnel exits through Tor: if this process dies,
    // the heartbeat goes stale and the service disarms Tor egress
    // on its own (see ClashManager's watchdog).

    private val heartbeatActive = AtomicBoolean(false)

    private fun startHeartbeat(context: Context) {
        if (!heartbeatActive.compareAndSet(false, true)) return
        val file = File(context.filesDir, "tor/heartbeat")
        thread(name = "tor-heartbeat") {
            try {
                while (process != null || _state.value.running) {
                    try {
                        file.parentFile?.mkdirs()
                        file.writeText(System.currentTimeMillis().toString())
                    } catch (_: Exception) {
                        // Best effort: a missed beat only matters if
                        // every beat is missed.
                    }
                    Thread.sleep(4_000)
                }
            } finally {
                heartbeatActive.set(false)
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun start(context: Context, mode: Mode, customText: String) {
        if (_state.value.running) return
        val appContext = context.applicationContext
        val sid = ++sessionId
        autoExitChecked = false
        _state.update {
            it.copy(
                running = true,
                bootstrap = 0,
                status = "启动中…",
                exitResult = null,
                exitIp = null,
                exitLocation = null,
                exitLat = null,
                exitLon = null,
                connectedVia = null,
                hops = emptyList()
            )
        }
        startHeartbeat(appContext)
        thread(name = "tor-supervisor") {
            if (mode == Mode.AUTO) {
                supervise(sid, appContext, autoChain, customText, auto = true)
            } else {
                supervise(sid, appContext, listOf(mode), customText, auto = false)
            }
        }
    }

    /**
     * Runs one attempt (or the whole 智能 chain) and watches it:
     * bootstrap reaching 100% is success; the tor process dying or
     * [STALL_TIMEOUT_MS] without any bootstrap progress is a failed
     * attempt. In 智能 mode a failed attempt is torn down and the
     * next transport is tried; in manual mode a stall only produces
     * a plain-language diagnosis and the attempt keeps running.
     */
    private fun supervise(
        sid: Int,
        context: Context,
        attempts: List<Mode>,
        customText: String,
        auto: Boolean
    ) {
        for ((index, attemptMode) in attempts.withIndex()) {
            if (sid != sessionId || !_state.value.running) return
            autoExitChecked = false
            processExited = false
            _state.update {
                it.copy(
                    bootstrap = 0,
                    status = if (auto) {
                        "智能尝试 ${index + 1}/${attempts.size}：正在试 ${attemptMode.via}…"
                    } else {
                        it.status
                    }
                )
            }
            try {
                launchAttempt(context, attemptMode, customText)
            } catch (e: Exception) {
                appendLog("[错误] ${attemptMode.via} 启动失败: ${e.message}")
                if (!auto) {
                    _state.update { it.copy(running = false, status = "启动失败") }
                    stopTransports()
                    return
                }
                continue
            }
            var lastBootstrap = 0
            var lastProgressAt = System.currentTimeMillis()
            var stallDiagnosed = false
            var attemptDead = false
            while (sid == sessionId && _state.value.running) {
                Thread.sleep(500)
                val snapshot = _state.value
                if (snapshot.bootstrap >= 100) {
                    _state.update {
                        it.copy(
                            connectedVia = attemptMode.via,
                            status = if (auto) {
                                "已连接（智能模式 · 经 ${attemptMode.via} 连通）"
                            } else {
                                it.status
                            }
                        )
                    }
                    appendLog("[连接] 经 ${attemptMode.via} 连通")
                    // The circuit exists from this moment; read it
                    // for the globe right away, independently of
                    // the exit test.
                    refreshCircuit()
                    return
                }
                if (snapshot.bootstrap > lastBootstrap) {
                    lastBootstrap = snapshot.bootstrap
                    lastProgressAt = System.currentTimeMillis()
                    stallDiagnosed = false
                }
                if (processExited) {
                    attemptDead = true
                    break
                }
                val stalledFor = System.currentTimeMillis() - lastProgressAt
                if (stalledFor > STALL_TIMEOUT_MS && snapshot.bootstrap < 100) {
                    if (auto) {
                        appendLog(
                            "[诊断] ${attemptMode.via} 卡在 ${snapshot.bootstrap}% " +
                                "超过 ${STALL_TIMEOUT_MS / 1000} 秒没有进展，自动换下一个"
                        )
                        attemptDead = true
                        break
                    }
                    if (!stallDiagnosed) {
                        stallDiagnosed = true
                        appendLog(
                            "[诊断] ${attemptMode.via} 卡在 ${snapshot.bootstrap}% " +
                                "超过 ${STALL_TIMEOUT_MS / 1000} 秒没有进展"
                        )
                        _state.update { it.copy(status = stallHint(attemptMode, snapshot.bootstrap)) }
                    }
                }
            }
            // The attempt ended without connecting.
            if (sid != sessionId || !_state.value.running) return
            teardownAttempt()
            if (auto && index < attempts.lastIndex) {
                _state.update {
                    it.copy(
                        bootstrap = 0,
                        status = "${attemptMode.via} 没能连通，自动换 ${attempts[index + 1].via} 试试…"
                    )
                }
                Thread.sleep(800)
            } else if (auto) {
                _state.update {
                    it.copy(
                        running = false,
                        bootstrap = -1,
                        status = "智能模式：Snowflake、obfs4、meek 都没能连通。" +
                            "当前网络封锁较严时，可以试试「自定义网桥」贴一条 WebTunnel 桥，往往更有效"
                    )
                }
                stopTransports()
                return
            } else {
                // Manual mode: the tor process died on its own.
                if (attemptDead) {
                    _state.update {
                        it.copy(running = false, status = "Tor 进程已退出，连接中断")
                    }
                    stopTransports()
                }
                return
            }
        }
    }

    /** Plain-language explanation when a manual attempt stalls. */
    private fun stallHint(mode: Mode, bootstrap: Int): String = when (mode) {
        Mode.SNOWFLAKE ->
            "卡在 $bootstrap% 一阵了：Snowflake 在当前网络可能被拦住。" +
                "可以停下换 obfs4，或选「智能」让它自动轮着试"

        Mode.OBFS4 ->
            "卡在 $bootstrap% 一阵了：这批 obfs4 内置桥可能已被封锁。" +
                "可以换 meek，或选「智能」自动尝试；有 WebTunnel 桥地址时，自定义网桥往往更有效"

        Mode.MEEK ->
            "卡在 $bootstrap% 一阵了：meek 的通道可能暂时不可用。" +
                "可以换 Snowflake，或选「智能」自动尝试"

        Mode.DIRECT ->
            "卡在 $bootstrap% 一阵了：直连 Tor 在当前网络基本行不通，请改用网桥（推荐「智能」）"

        Mode.CUSTOM ->
            "卡在 $bootstrap% 一阵了：自定义网桥暂时没有响应，检查桥行是否完整，或换一条试试"

        Mode.AUTO -> "连接中… bootstrap $bootstrap%"
    }

    private fun launchAttempt(context: Context, mode: Mode, customText: String) {
        val baseDir = File(context.filesDir, "tor").apply { mkdirs() }
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val ptDir = File(baseDir, "pt-state").apply { mkdirs() }

        val controller = controller ?: Controller(
            ptDir.absolutePath, true, false, "INFO", transportEvents
        ).also { controller = it }

        // Work out which transports to start and which bridge lines to use.
        val transports = mutableListOf<String>()
        var bridgeLines: List<String> = emptyList()
        when (mode) {
            // The supervisor resolves 智能 into concrete attempts;
            // launchAttempt never receives it.
            Mode.AUTO -> Unit
            Mode.DIRECT -> Unit
            Mode.SNOWFLAKE -> {
                configureSnowflake(controller, snowflakeBridges.first())
                transports += IPtProxy.Snowflake
                bridgeLines = snowflakeBridges
            }

            Mode.OBFS4 -> {
                transports += IPtProxy.Obfs4
                bridgeLines = obfs4Bridges
            }

            Mode.MEEK -> {
                transports += IPtProxy.MeekLite
                bridgeLines = meekBridges
            }

            Mode.CUSTOM -> {
                val lines = customText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val usable = mutableListOf<String>()
                for (line in lines) {
                    val token = line.substringBefore(" ")
                    val constant = transportConstant(token)
                    if (constant == null) {
                        if (token.contains(".") || token.contains(":")) {
                            // Vanilla bridge (a plain relay address, no transport).
                            usable += line
                        } else {
                            appendLog("[PT] 不认识的传输方式「$token」，跳过该行")
                        }
                        continue
                    }
                    if (constant == IPtProxy.Snowflake) configureSnowflake(controller, line)
                    if (constant !in transports) transports += constant
                    usable += line
                }
                bridgeLines = usable
            }
        }

        for (transport in transports) {
            controller.start(transport, null)
            startedTransports += transport
            appendLog("[PT] 已启动 $transport，本地端口 ${controller.port(transport)}")
        }

        val torrc = buildString {
            appendLine("DataDirectory ${dataDir.absolutePath}")
            appendLine("SocksPort 127.0.0.1:$SOCKS_PORT")
            appendLine("HTTPTunnelPort 127.0.0.1:$HTTP_TUNNEL_PORT")
            appendLine("ControlPort 127.0.0.1:$CONTROL_PORT")
            appendLine("CookieAuthentication 0")
            appendLine("Log notice stdout")
            appendLine("AvoidDiskWrites 1")
            appendLine("DormantCanceledByStartup 1")
            if (bridgeLines.isNotEmpty()) {
                appendLine("UseBridges 1")
                for (transport in transports) {
                    val port = controller.port(transport)
                    if (port in 1..65535) {
                        appendLine("ClientTransportPlugin $transport socks5 127.0.0.1:$port")
                    } else {
                        appendLog("[PT] 警告: $transport 端口无效 ($port)")
                    }
                }
                for (line in bridgeLines) appendLine("Bridge $line")
            } else {
                appendLine("UseBridges 0")
            }
        }
        val torrcFile = File(baseDir, "torrc")
        torrcFile.writeText(torrc)
        appendLog("[Tor] torrc 已生成（模式: ${mode.name}）")

        val torBinary = File(context.applicationInfo.nativeLibraryDir, "libtor.so")
        if (!torBinary.exists()) {
            throw IllegalStateException("找不到 libtor.so (${torBinary.path})")
        }

        appendLog("[Tor] 启动 tor daemon…")
        val proc = ProcessBuilder(torBinary.absolutePath, "-f", torrcFile.absolutePath)
            .directory(baseDir)
            .redirectErrorStream(true)
            .start()
        process = proc
        _state.update { it.copy(status = "连接中…（等待 bootstrap）") }

        thread(name = "tor-log") {
            val bootstrapRegex = Regex("Bootstrapped (\\d+)%")
            proc.inputStream.bufferedReader().forEachLine { line ->
                appendLog(line)
                bootstrapRegex.find(line)?.let { match ->
                    val percent = match.groupValues[1].toInt()
                    _state.update {
                        it.copy(
                            bootstrap = percent,
                            status = if (percent >= 100) {
                                "已连接（bootstrap 100%）"
                            } else {
                                "连接中（${mode.via}）… bootstrap $percent%"
                            }
                        )
                    }
                    if (percent >= 100 && !autoExitChecked) {
                        autoExitChecked = true
                        testExit()
                    }
                }
            }
            val exitCode = proc.waitFor()
            appendLog("[Tor] 进程退出，code=$exitCode")
            // State transitions on process death belong to the
            // supervisor (it decides between retry, diagnosis and
            // giving up); the reader only raises the flag. Exception:
            // death AFTER a successful connect has no supervisor
            // watching anymore, so it is finalized right here — and
            // the egress layer is told at once, so a tunnel that
            // exits through Tor is cut back without waiting for
            // anyone to notice.
            if (process === proc) {
                processExited = true
                val snapshot = _state.value
                if (snapshot.running && snapshot.bootstrap >= 100) {
                    _state.update {
                        it.copy(
                            running = false,
                            status = "Tor 进程已退出，连接中断",
                            connectedVia = null
                        )
                    }
                    stopTransports()
                    try {
                        onUnexpectedExit?.invoke()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun stopTransports() {
        val controller = controller ?: return
        for (transport in startedTransports) {
            try {
                controller.stop(transport)
            } catch (e: Exception) {
                appendLog("[PT] 停止 $transport 失败: ${e.message}")
            }
        }
        startedTransports.clear()
    }

    /** Shut down the current attempt's tor process and transports. */
    private fun teardownAttempt() {
        // Ask tor to shut down over the control port first.
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", CONTROL_PORT), 2000)
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter()
                val reader = socket.getInputStream().bufferedReader()
                writer.write("AUTHENTICATE\r\n")
                writer.flush()
                reader.readLine()
                writer.write("SIGNAL SHUTDOWN\r\n")
                writer.flush()
                reader.readLine()
            }
            appendLog("[Tor] 已发送 SHUTDOWN 信号")
        } catch (e: Exception) {
            appendLog("[Tor] 控制口关停失败（${e.message}），直接结束进程")
        }
        try {
            val exited = process?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) ?: true
            if (!exited) process?.destroy()
        } catch (_: Exception) {
            process?.destroy()
        }
        process = null
        stopTransports()
    }

    fun stop() {
        if (!_state.value.running && process == null) return
        // Invalidate any running supervisor before tearing down, so
        // it cannot mistake the shutdown for a failed attempt.
        sessionId++
        _state.update { it.copy(status = "停止中…") }
        thread(name = "tor-stop") {
            teardownAttempt()
            _state.update {
                it.copy(
                    running = false,
                    bootstrap = -1,
                    status = "已停止",
                    testing = false,
                    exitIp = null,
                    exitLocation = null,
                    exitLat = null,
                    exitLon = null,
                    connectedVia = null,
                    hops = emptyList()
                )
            }
        }
    }

    /** GET https://check.torproject.org/api/ip through the Tor SOCKS port. */
    private fun queryExitIp(): Pair<Boolean, String?>? {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
        val connection =
            URL("https://check.torproject.org/api/ip").openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val isTor = json.optBoolean("IsTor")
        val ip = json.optString("IP").ifBlank { null }
        return isTor to ip
    }

    fun testExit() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, exitResult = null) }
        thread(name = "tor-exit-test") {
            var exitIp: String? = null
            var isTor = false
            val result = try {
                val answer = queryExitIp()
                isTor = answer?.first == true
                exitIp = answer?.second
                "IsTor = $isTor，出口 IP = ${exitIp ?: "未知"}"
            } catch (e: Exception) {
                "失败：${e.javaClass.simpleName}: ${e.message}"
            }
            appendLog("[出口测试] $result")
            _state.update { it.copy(testing = false, exitResult = result, exitIp = exitIp) }
            // Geolocate the exit (through Tor as well) for the map
            // visual; failure just leaves the location empty and the
            // page shows the bare IP — never a guessed place.
            val ip = exitIp
            if (ip != null) {
                val geo = lookupLocation(ip)
                if (geo != null) {
                    appendLog("[出口位置] ${geo.display}")
                    _state.update {
                        it.copy(
                            exitLocation = geo.display,
                            exitLat = geo.lat,
                            exitLon = geo.lon
                        )
                    }
                }
                if (isTor) refreshCircuit()
            }
        }
    }

    /**
     * Awaitable exit verification for the egress switch: the tunnel
     * is only ever pointed at Tor after this returns true, i.e.
     * after Tor's own check service confirms the SOCKS port really
     * exits through the Tor network. A few retries ride out the
     * first seconds after bootstrap, when circuits are still warm.
     */
    suspend fun verifyExit(): Boolean = withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            val ok = try {
                val answer = queryExitIp()
                val ip = answer?.second
                if (ip != null) {
                    _state.update { it.copy(exitIp = ip) }
                }
                answer?.first == true
            } catch (e: Exception) {
                appendLog("[出口验证] 第 ${attempt + 1} 次失败: ${e.message}")
                false
            }
            if (ok) {
                appendLog("[出口验证] 已确认出口在 Tor 网络上")
                return@withContext true
            }
            if (attempt < 2) delay(1_500)
        }
        appendLog("[出口验证] 未能确认出口是 Tor，取消切换")
        false
    }

    /**
     * Country/city + coordinates for an IP, asked through Tor
     * SOCKS. Several public sources are tried in order — a single
     * source refusing Tor exits must not blank the whole map.
     */
    private fun lookupLocation(ip: String): GeoInfo? {
        lookupViaIpApi(ip)?.let { return it }
        lookupViaIpApiCo(ip)?.let { return it }
        lookupViaIpInfo(ip)?.let { return it }
        appendLog("[位置] $ip 的地理定位几个来源都没查到")
        return null
    }

    private fun httpGetThroughTor(url: String): String {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun lookupViaIpApi(ip: String): GeoInfo? = try {
        val json = JSONObject(
            httpGetThroughTor("http://ip-api.com/json/$ip?fields=status,country,city,lat,lon")
        )
        if (json.optString("status") == "success") {
            val country = json.optString("country")
            val city = json.optString("city")
            val lat = json.optDouble("lat", Double.NaN)
            val lon = json.optDouble("lon", Double.NaN)
            if (country.isBlank()) {
                null
            } else {
                GeoInfo(
                    country = country,
                    display = if (city.isNotBlank() && city != country) {
                        "$country · $city"
                    } else {
                        country
                    },
                    lat = if (lat.isNaN()) null else lat,
                    lon = if (lon.isNaN()) null else lon
                )
            }
        } else {
            null
        }
    } catch (e: Exception) {
        appendLog("[位置] ip-api 查询失败: ${e.message}")
        null
    }

    private fun lookupViaIpApiCo(ip: String): GeoInfo? = try {
        val json = JSONObject(httpGetThroughTor("https://ipapi.co/$ip/json/"))
        val country = json.optString("country_name")
        val city = json.optString("city")
        val lat = json.optDouble("latitude", Double.NaN)
        val lon = json.optDouble("longitude", Double.NaN)
        if (country.isBlank() || json.optBoolean("error", false)) {
            null
        } else {
            GeoInfo(
                country = country,
                display = if (city.isNotBlank() && city != country) {
                    "$country · $city"
                } else {
                    country
                },
                lat = if (lat.isNaN()) null else lat,
                lon = if (lon.isNaN()) null else lon
            )
        }
    } catch (e: Exception) {
        appendLog("[位置] ipapi.co 查询失败: ${e.message}")
        null
    }

    private fun lookupViaIpInfo(ip: String): GeoInfo? = try {
        val json = JSONObject(httpGetThroughTor("https://ipinfo.io/$ip/json"))
        val countryCode = json.optString("country")
        val city = json.optString("city")
        val loc = json.optString("loc")
        val parts = loc.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lon = parts.getOrNull(1)?.toDoubleOrNull()
        if (countryCode.isBlank()) {
            null
        } else {
            GeoInfo(
                country = countryCode,
                display = if (city.isNotBlank()) "$city · $countryCode" else countryCode,
                lat = lat,
                lon = lon
            )
        }
    } catch (e: Exception) {
        appendLog("[位置] ipinfo 查询失败: ${e.message}")
        null
    }

    // ---- circuit → globe -------------------------------------------
    //
    // The 0.4.0 test build taught us how this chain breaks in the
    // field, so it is built defensively now:
    //   * it runs on its own trigger (bootstrap 100%), not as a
    //     side effect of the exit test;
    //   * the control-port reader understands the reply grammar
    //     (data blocks, continuations, error replies) instead of
    //     waiting for one exact success line — an error reply for
    //     one relay used to hang the whole read until the socket
    //     timed out and everything was dropped;
    //   * every relay is resolved independently (descriptor first,
    //     network-status entry as fallback; a relay that cannot be
    //     resolved keeps its nickname and the chain moves on);
    //   * hops are published as soon as their names are known and
    //     then filled in hop by hop as geolocation lands, in
    //     parallel — one slow lookup can no longer blank the route.

    private class ControlReply(val ok: Boolean, val data: List<String>)

    /**
     * Read one control-port reply. Terminal lines are "NNN " (a
     * space ends the reply, a dash continues it); "250+" opens a
     * data block closed by a lone "." line.
     */
    private fun readControlReply(reader: BufferedReader): ControlReply {
        val data = mutableListOf<String>()
        var inBlock = false
        while (true) {
            val line = reader.readLine()
                ?: throw IOException("控制口连接被关闭")
            when {
                line.startsWith("250+") -> inBlock = true
                inBlock && line == "." -> inBlock = false
                inBlock -> data += line
                line.length >= 4 && line[3] == ' ' &&
                    line.substring(0, 3).all { it.isDigit() } -> {
                    val code = line.substring(0, 3).toInt()
                    return ControlReply(code in 200..299, data)
                }

                line.length >= 4 && line[3] == '-' -> Unit // continuation line
                else -> data += line
            }
        }
    }

    /**
     * Read the live circuit and publish it for the globe: relay
     * names first (the path line under the globe), coordinates hop
     * by hop as geolocation answers. Safe to call repeatedly —
     * circuits rotate, and a refresh simply replaces the hops.
     */
    fun refreshCircuit() {
        thread(name = "tor-circuit") {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", CONTROL_PORT), 3_000)
                    socket.soTimeout = 6_000
                    val writer = socket.getOutputStream().bufferedWriter()
                    val reader = socket.getInputStream().bufferedReader()

                    fun send(command: String): ControlReply {
                        writer.write("$command\r\n")
                        writer.flush()
                        return readControlReply(reader)
                    }

                    if (!send("AUTHENTICATE").ok) {
                        appendLog("[线路] 控制口认证失败")
                        return@thread
                    }
                    val circuits = send("GETINFO circuit-status")
                    if (!circuits.ok) {
                        appendLog("[线路] 读不到线路状态")
                        return@thread
                    }
                    val relays = parseCircuitPath(circuits.data)
                    if (relays == null) {
                        appendLog("[线路] 还没有已建成的线路")
                        return@thread
                    }

                    // Names first: the path is visible even before
                    // any coordinate is known.
                    _state.update {
                        it.copy(
                            hops = relays.map { relay ->
                                Hop(relay.nickname ?: "中继节点", null, null)
                            }
                        )
                    }

                    // Resolve each relay's IP on this connection;
                    // a failure only costs that relay its address.
                    val ips = relays.map { relay ->
                        try {
                            relayIp(::send, relay)
                        } catch (e: Exception) {
                            appendLog("[线路] ${relay.nickname ?: relay.fingerprint.take(8)} 的地址没查到: ${e.message}")
                            null
                        }
                    }
                    try {
                        send("QUIT")
                    } catch (_: Exception) {
                    }

                    // Geolocate in parallel; publish per hop.
                    relays.forEachIndexed { index, relay ->
                        val ip = ips.getOrNull(index) ?: return@forEachIndexed
                        thread(name = "tor-circuit-geo") {
                            val geo = lookupLocation(ip)
                            if (geo != null) {
                                _state.update { current ->
                                    val hops = current.hops.toMutableList()
                                    if (index < hops.size) {
                                        hops[index] = hops[index].copy(
                                            place = geo.country,
                                            lat = geo.lat,
                                            lon = geo.lon
                                        )
                                    }
                                    current.copy(hops = hops)
                                }
                            }
                        }
                    }
                    appendLog(
                        "[线路] ${relays.joinToString(" → ") { it.nickname ?: "中继" }}"
                    )
                }
            } catch (e: Exception) {
                appendLog("[线路] 获取失败: ${e.message}")
            }
        }
    }

    /**
     * Pick the user circuit out of circuit-status: a BUILT line
     * whose path token starts with $, preferring circuits that are
     * not flagged internal (directory / hidden-service housekeeping).
     */
    private fun parseCircuitPath(lines: List<String>): List<RelayRef>? {
        val built = lines.filter { it.contains(" BUILT ") }
        val chosen = built.firstOrNull { !it.contains("IS_INTERNAL") }
            ?: built.firstOrNull()
            ?: return null
        val path = chosen.split(" ").firstOrNull { it.startsWith("$") }
            ?: return null
        val relays = path.split(",").map { entry ->
            RelayRef(
                fingerprint = entry.substringBefore("~").removePrefix("$"),
                nickname = entry.substringAfter("~", "").ifBlank { null }
            )
        }.filter { it.fingerprint.isNotBlank() }
        return relays.ifEmpty { null }
    }

    /**
     * One relay's IP: its descriptor ("router <nick> <ip> …") with
     * the network-status entry ("r <nick> … <ip> …") as fallback.
     * Bridge relays in particular may only answer one of the two.
     */
    private fun relayIp(
        send: (String) -> ControlReply,
        relay: RelayRef
    ): String? {
        val desc = try {
            send("GETINFO desc/id/${relay.fingerprint}")
        } catch (e: Exception) {
            null
        }
        if (desc != null && desc.ok) {
            val router = desc.data.firstOrNull { it.startsWith("router ") }
            val ip = router?.split(" ")?.getOrNull(2)
            if (ip != null) return ip
        }
        val ns = try {
            send("GETINFO ns/id/${relay.fingerprint}")
        } catch (e: Exception) {
            null
        }
        if (ns != null && ns.ok) {
            val entry = ns.data.firstOrNull { it.startsWith("r ") }
            val ip = entry?.split(" ")?.getOrNull(6)
            if (ip != null) return ip
        }
        return null
    }
}
