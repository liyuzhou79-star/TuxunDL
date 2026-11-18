package com.tuxundl.windows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// Palette mirrors the Android app's indigo theme so both editions read
// as one product.
private val Indigo = Color(0xFF5B5BD6)
private val IndigoSoft = Color(0xFF8E8EF0)
private val BgDark = Color(0xFF14141C)
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

private val NAV_ITEMS =
    listOf("首页", "节点", "连接", "订阅", "规则", "日志", "设置")

fun main() {
    // Refresh stale subscription configs in the background; the UI
    // never waits on the network to open.
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        runCatching { SubscriptionStore.autoUpdateStale() }
    }
    application {
    var windowVisible by remember { mutableStateOf(true) }
    val trayIcon = painterResource("tray-icon.png")

    // The tray menu names the current exit node while connected.
    var trayNode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(CoreBridge.running) {
        trayNode = null
        while (CoreBridge.running) {
            CoreBridge.api?.currentNode()?.let { trayNode = it }
            delay(5000)
        }
    }

    // Closing the window hides it to the tray by default (the
    // "关闭时最小化到系统匣" setting, read fresh at close time); the
    // app lives in the tray and the core keeps running, like every
    // desktop proxy client. With the setting off, closing quits —
    // stopping the core first, which also restores the system proxy.
    // Quitting from the tray menu works either way.
    Tray(
        icon = trayIcon,
        tooltip = if (CoreBridge.running) "TuxunDL — 已连接" else "TuxunDL — 未连接",
        onAction = { windowVisible = true },
        menu = {
            Item(
                text = "打开主界面",
                onClick = { windowVisible = true },
            )
            Item(
                text = if (CoreBridge.running) "断开连接" else "连接（打开主界面）",
                onClick = {
                    if (CoreBridge.running) {
                        CoreBridge.stop()
                    } else {
                        windowVisible = true
                    }
                },
            )
            if (CoreBridge.running && trayNode != null) {
                Item(
                    text = "当前节点：$trayNode",
                    onClick = { windowVisible = true },
                )
            }
            Separator()
            Item(
                text = "退出",
                onClick = {
                    CoreBridge.stop()
                    exitApplication()
                },
            )
        },
    )

    if (windowVisible) {
        Window(
            onCloseRequest = {
                if (SettingsStore.closeToTray()) {
                    windowVisible = false
                } else {
                    CoreBridge.stop()
                    exitApplication()
                }
            },
            title = "TuxunDL",
            icon = trayIcon,
        ) {
            MaterialTheme {
                App()
            }
        }
    }
    }
}

@Composable
internal fun App() {
    var selectedNav by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Sidebar(
            selected = selectedNav,
            onSelect = { selectedNav = it },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedNav) {
                0 -> HomePage()
                1 -> NodesPage()
                2 -> ConnectionsPage()
                3 -> SubscriptionsPage()
                4 -> RulesPage()
                5 -> LogsPage()
                6 -> SettingsPage()
                else -> Text(
                    text = "${NAV_ITEMS[selectedNav]}（开发中）",
                    color = TextDim,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun Sidebar(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(PanelDark)
            .padding(vertical = 20.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindowMascot(size = 34)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "TuxunDL",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(28.dp))

        NAV_ITEMS.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Indigo else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else TextDim,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "Windows 版 v${WindowsUpdateCheck.CURRENT_VERSION}",
            color = TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HomePage() {
    // Connection state lives in CoreBridge (Compose state), so this
    // page and the tray menu never disagree about it.
    val connected = CoreBridge.running
    var busy by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var currentNode by remember { mutableStateOf<String?>(null) }
    var rateText by remember { mutableStateOf("↓ 0 B/s   ↑ 0 B/s") }
    var sessionText by remember { mutableStateOf("本次会话 0 B") }
    var durationText by remember { mutableStateOf("") }
    // Leftover system proxy from an unclean exit (crash, killed
    // process): Windows still points browsers at our port while the
    // core is down. Detected read-only; the banner offers the fix.
    var staleProxy by remember { mutableStateOf(false) }
    // (down, up) bytes-per-second samples for the home traffic chart,
    // one every two seconds, the last two minutes kept.
    var samples by remember { mutableStateOf<List<Pair<Long, Long>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // While connected, poll the core every two seconds so the home
    // cards show the live exit node and traffic rates.
    LaunchedEffect(connected) {
        if (!connected) {
            currentNode = null
            rateText = "↓ 0 B/s   ↑ 0 B/s"
            sessionText = "本次会话 0 B"
            durationText = ""
            return@LaunchedEffect
        }
        val startedAt = System.currentTimeMillis()
        var prevDown = 0L
        var prevUp = 0L
        var first = true
        while (true) {
            val api = CoreBridge.api ?: break
            api.totals()?.let { (down, up) ->
                if (first) {
                    prevDown = down
                    prevUp = up
                    first = false
                }
                val downRate = (down - prevDown).coerceAtLeast(0) / 2
                val upRate = (up - prevUp).coerceAtLeast(0) / 2
                prevDown = down
                prevUp = up
                rateText = "↓ ${fmtBytes(downRate)}/s   ↑ ${fmtBytes(upRate)}/s"
                sessionText = "本次会话 ${fmtBytes(down + up)}"
                samples = (samples + (downRate to upRate)).takeLast(60)
            }
            api.currentNode()?.let { currentNode = it }
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000
            durationText = " · %02d:%02d:%02d".format(
                elapsed / 3600, elapsed / 60 % 60, elapsed % 60,
            )
            delay(2000)
        }
    }

    // Check for a leftover system proxy whenever the connection
    // state settles to stopped (page open included).
    LaunchedEffect(connected) {
        staleProxy = !connected &&
            SystemProxy.pointsAtAppPort(SettingsStore.mixedPort())
    }

    // Auto-connect once when the home page first appears, if the
    // user turned it on and a profile is ready. This is the same
    // start path as the power button below, elevation included.
    LaunchedEffect(Unit) {
        if (CoreBridge.running || !SettingsStore.autoConnect()) {
            return@LaunchedEffect
        }
        val profile = CoreBridge.profileFile()
        if (!profile.exists()) return@LaunchedEffect
        if (SettingsStore.tunMode() &&
            Elevation.isWindows() &&
            !Elevation.isElevated()
        ) {
            statusNote = "TUN 需要管理员权限，正在以管理员身份重启…"
            Elevation.relaunchElevated()
            return@LaunchedEffect
        }
        busy = true
        val ok = CoreBridge.start(
            profileYaml = profile.readText(),
            tunMode = SettingsStore.tunMode(),
            mixedPort = SettingsStore.mixedPort(),
        )
        if (!ok) statusNote = "自动连接失败，细节在日志页"
        busy = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (staleProxy) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF3A2A1E))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "系统代理还开着并指向本程序的端口，但核心没有在运行——" +
                        "多半是上次没正常退出留下的，浏览器会因此上不了网。",
                    color = Color(0xFFF0D9B5),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "清理",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Indigo)
                        .clickable {
                            SystemProxy.disable()
                            staleProxy = false
                            statusNote = "已清理残留的系统代理"
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(12.dp))

        WindowMascot(size = 84)

        Spacer(Modifier.height(20.dp))

        PowerButton(
            connected = connected,
            onClick = {
                if (busy) return@PowerButton
                scope.launch {
                    busy = true
                    if (CoreBridge.running) {
                        CoreBridge.stop()
                        statusNote = null
                    } else {
                        val profile = CoreBridge.profileFile()
                        if (!profile.exists()) {
                            statusNote = "还没有订阅配置，先到订阅页添加"
                        } else if (SettingsStore.tunMode() &&
                            Elevation.isWindows() &&
                            !Elevation.isElevated()
                        ) {
                            // TUN needs administrator rights: restart
                            // elevated; Windows shows its UAC prompt.
                            statusNote = "TUN 需要管理员权限，正在以管理员身份重启…"
                            busy = false
                            Elevation.relaunchElevated()
                            return@launch
                        } else {
                            val ok = CoreBridge.start(
                                profileYaml = profile.readText(),
                                tunMode = SettingsStore.tunMode(),
                                mixedPort = SettingsStore.mixedPort(),
                            )
                            statusNote = if (ok) {
                                null
                            } else {
                                "核心启动失败，细节在日志页"
                            }
                        }
                    }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = when {
                busy -> "处理中…"
                statusNote != null -> statusNote!!
                connected -> "已连接$durationText"
                else -> "未连接"
            },
            color = if (connected) IndigoSoft else TextDim,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoCard(
                title = "当前节点",
                main = if (connected) (currentNode ?: "读取中…") else "尚未选择",
                sub = "到节点页选择",
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                title = "流量",
                main = rateText,
                sub = sessionText,
                modifier = Modifier.weight(1f),
            )
        }

        // The active subscription's data plan, when the provider
        // reports one: used / total as a bar, plus the expiry date.
        val activeSub = remember { SubscriptionStore.active() }
        if (activeSub != null && activeSub.trafficTotal > 0) {
            Spacer(Modifier.height(16.dp))
            SubscriptionTrafficCard(
                sub = activeSub,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (connected) {
            Spacer(Modifier.height(16.dp))
            TrafficChart(samples = samples, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The active subscription's data plan at a glance: how much of the
 * quota is used, as a bar, and when the plan expires. Only composed
 * when the provider reports usage (subscription-userinfo).
 */
@Composable
private fun SubscriptionTrafficCard(
    sub: Subscription,
    modifier: Modifier = Modifier,
) {
    val fraction = if (sub.trafficTotal > 0) {
        (sub.trafficUsed.toDouble() / sub.trafficTotal.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    } else {
        0f
    }
    val expireText = if (sub.expireAt > 0) {
        val date = java.time.Instant.ofEpochSecond(sub.expireAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        " · 到期 $date"
    } else {
        ""
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PanelDark),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row {
                Text(
                    text = "订阅流量 · ${sub.name}",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    color = IndigoSoft,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2A2A3A)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Indigo),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已用 ${fmtBytes(sub.trafficUsed)} / 共 " +
                    "${fmtBytes(sub.trafficTotal)}$expireText",
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * The last two minutes of traffic as two lines: download in indigo,
 * upload in soft indigo, scaled to the busiest sample in the window.
 */
@Composable
private fun TrafficChart(
    samples: List<Pair<Long, Long>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanelDark)
            .padding(16.dp),
    ) {
        Row {
            Text(
                text = "实时流量",
                color = TextDim,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = "↓ 下载", color = IndigoSoft, fontSize = 12.sp)
            Text(text = "   ↑ 上传", color = Color(0xFF6E6E8C), fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp),
        ) {
            if (samples.size < 2) return@Canvas
            val maxValue = samples.maxOf { maxOf(it.first, it.second) }
                .coerceAtLeast(1L)
                .toFloat()
            fun line(values: List<Long>, color: Color) {
                val path = androidx.compose.ui.graphics.Path()
                values.forEachIndexed { index, value ->
                    val x = size.width * index / (values.size - 1)
                    val y = size.height - size.height * (value / maxValue) * 0.92f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
            }
            line(samples.map { it.first }, IndigoSoft)
            line(samples.map { it.second }, Color(0xFF6E6E8C))
        }
    }
}

@Composable
private fun PowerButton(connected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(148.dp)
            .clip(CircleShape)
            .background(if (connected) Indigo else PanelDark)
            .border(
                width = 3.dp,
                color = if (connected) IndigoSoft else TextDim,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // The power symbol is drawn, not a font glyph: the ⏻ character
        // is missing from common fonts and rendered as a tofu box.
        Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = size.minDimension * 0.11f
            val radius = size.minDimension * 0.36f
            drawArc(
                color = Color.White,
                startAngle = -55f,
                sweepAngle = 290f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x, center.y - radius * 1.28f),
                end = Offset(center.x, center.y - radius * 0.1f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun fmtBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun InfoCard(
    title: String,
    main: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PanelDark),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = title, color = TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = main,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = sub, color = TextDim, fontSize = 13.sp)
        }
    }
}

/**
 * The Windows mascot: a four-pane window character (the Android robot
 * stays exclusive to the Android edition). Drawn in Compose for the
 * skeleton; the final art replaces this placeholder.
 */
@Composable
private fun WindowMascot(size: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Antenna with the glowing node dot, shared with the Android mascot.
        Box(
            modifier = Modifier
                .size(3.dp, 8.dp)
                .background(TextDim),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(IndigoSoft),
        )

        Spacer(Modifier.height(2.dp))

        Column(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Indigo)
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.85f)),
                        )
                    }
                }
            }
        }
    }
}
