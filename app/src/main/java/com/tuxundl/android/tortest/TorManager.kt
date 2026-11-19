package com.tuxundl.android.tortest

import IPtProxy.Controller
import IPtProxy.IPtProxy
import IPtProxy.OnTransportEvents
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * Tor connectivity spike (tor-spike branch only).
 *
 * Runs the bundled tor daemon (Guardian Project tor-android) as a child
 * process and the pluggable transports in-process via IPtProxy
 * (lyrebird obfs4/webtunnel/meek-lite, snowflake), exactly the way
 * Orbot wires the same two libraries together:
 *
 *   Controller.start(transport, null) opens a local SOCKS listener,
 *   torrc points ClientTransportPlugin at it, Bridge lines do the rest.
 *
 * Deliberately standalone: nothing here talks to the mihomo core.
 */
object TorManager {

    const val SOCKS_PORT = 19050
    const val CONTROL_PORT = 19051

    enum class Mode(val label: String) {
        DIRECT("直连（无网桥）"),
        SNOWFLAKE("Snowflake（内置桥，无需地址）"),
        OBFS4("obfs4（内置桥）"),
        MEEK("meek（内置桥）"),
        CUSTOM("自定义网桥（可粘贴多行，支持 obfs4 / webtunnel / snowflake 等）")
    }

    data class UiState(
        val running: Boolean = false,
        val bootstrap: Int = -1,
        val status: String = "未启动",
        val exitResult: String? = null,
        val testing: Boolean = false,
        val logs: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Built-in bridge lines, as published by the Tor Project and shipped
    // by Orbot (assets/builtin-bridges.json, fetched 2026-11-19).
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

    fun start(context: Context, mode: Mode, customText: String) {
        if (_state.value.running) return
        val appContext = context.applicationContext
        _state.update {
            it.copy(
                running = true,
                bootstrap = 0,
                status = "启动中…",
                exitResult = null
            )
        }
        thread(name = "tor-spike-start") {
            try {
                doStart(appContext, mode, customText)
            } catch (e: Exception) {
                appendLog("[错误] 启动失败: ${e.message}")
                _state.update { it.copy(running = false, status = "启动失败") }
                stopTransports()
            }
        }
    }

    private fun doStart(context: Context, mode: Mode, customText: String) {
        val baseDir = File(context.filesDir, "tor-test").apply { mkdirs() }
        val dataDir = File(baseDir, "data").apply { mkdirs() }
        val ptDir = File(baseDir, "pt-state").apply { mkdirs() }

        val controller = controller ?: Controller(
            ptDir.absolutePath, true, false, "INFO", transportEvents
        ).also { controller = it }

        // Work out which transports to start and which bridge lines to use.
        val transports = mutableListOf<String>()
        var bridgeLines: List<String> = emptyList()
        when (mode) {
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

        thread(name = "tor-spike-log") {
            val bootstrapRegex = Regex("Bootstrapped (\\d+)%")
            proc.inputStream.bufferedReader().forEachLine { line ->
                appendLog(line)
                bootstrapRegex.find(line)?.let { match ->
                    val percent = match.groupValues[1].toInt()
                    _state.update {
                        it.copy(
                            bootstrap = percent,
                            status = if (percent >= 100) "已连接（bootstrap 100%）" else "连接中… bootstrap $percent%"
                        )
                    }
                }
            }
            val exitCode = proc.waitFor()
            appendLog("[Tor] 进程退出，code=$exitCode")
            if (_state.value.running) {
                _state.update { it.copy(running = false, status = "Tor 进程已退出（code $exitCode）") }
                stopTransports()
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

    fun stop() {
        if (!_state.value.running && process == null) return
        _state.update { it.copy(status = "停止中…") }
        thread(name = "tor-spike-stop") {
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
            _state.update {
                it.copy(running = false, bootstrap = -1, status = "已停止", testing = false)
            }
        }
    }

    /** GET https://check.torproject.org/api/ip through the Tor SOCKS port. */
    fun testExit() {
        if (_state.value.testing) return
        _state.update { it.copy(testing = true, exitResult = null) }
        thread(name = "tor-spike-exit-test") {
            val result = try {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT))
                val connection =
                    URL("https://check.torproject.org/api/ip").openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                "IsTor = ${json.optBoolean("IsTor")}，出口 IP = ${json.optString("IP")}"
            } catch (e: Exception) {
                "失败：${e.javaClass.simpleName}: ${e.message}"
            }
            appendLog("[出口测试] $result")
            _state.update { it.copy(testing = false, exitResult = result) }
        }
    }
}
