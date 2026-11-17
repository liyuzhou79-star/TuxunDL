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

private val NAV_ITEMS = listOf("首页", "节点", "订阅", "规则", "日志", "设置")

fun main() = application {
    var windowVisible by remember { mutableStateOf(true) }
    val trayIcon = painterResource("tray-icon.png")

    // Closing the window only hides it; the app lives in the tray and
    // the core keeps running, like every desktop proxy client. Quitting
    // happens from the tray menu and always stops the core first, which
    // also restores the user's system proxy.
    Tray(
        icon = trayIcon,
        tooltip = "TuxunDL",
        onAction = { windowVisible = true },
        menu = {
            Item(
                text = "打开主界面",
                onClick = { windowVisible = true },
            )
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
            onCloseRequest = { windowVisible = false },
            title = "TuxunDL",
            icon = trayIcon,
        ) {
            MaterialTheme {
                App()
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
                2 -> SubscriptionsPage()
                3 -> RulesPage()
                4 -> LogsPage()
                5 -> SettingsPage()
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
            text = "Windows 版 v1.0.0",
            color = TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HomePage() {
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var currentNode by remember { mutableStateOf<String?>(null) }
    var rateText by remember { mutableStateOf("↓ 0 B/s   ↑ 0 B/s") }
    var sessionText by remember { mutableStateOf("本次会话 0 B") }
    val scope = rememberCoroutineScope()

    // While connected, poll the core every two seconds so the home
    // cards show the live exit node and traffic rates.
    LaunchedEffect(connected) {
        if (!connected) {
            currentNode = null
            rateText = "↓ 0 B/s   ↑ 0 B/s"
            sessionText = "本次会话 0 B"
            return@LaunchedEffect
        }
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
            }
            api.currentNode()?.let { currentNode = it }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        WindowMascot(size = 84)

        Spacer(Modifier.height(20.dp))

        PowerButton(
            connected = connected,
            onClick = {
                if (busy) return@PowerButton
                scope.launch {
                    busy = true
                    if (connected) {
                        CoreBridge.stop()
                        connected = false
                        statusNote = null
                    } else {
                        val profile = CoreBridge.profileFile()
                        if (!profile.exists()) {
                            statusNote = "还没有订阅配置，先到订阅页添加"
                        } else {
                            val ok = CoreBridge.start(
                                profileYaml = profile.readText(),
                                tunMode = SettingsStore.tunMode(),
                                mixedPort = SettingsStore.mixedPort(),
                            )
                            connected = ok
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
                connected -> "已连接"
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
