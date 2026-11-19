package com.tuxundl.android.tortest

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.tuxundl.android.ui.TuxunCard
import com.tuxundl.android.ui.TuxunSegmentedRow
import com.tuxundl.android.ui.theme.TuxunDLTheme
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tor page (tor-spike branch only). A separate page, apart from
 * the proxy main UI, wearing the main app's own design language:
 * the theme is the app's TuxunDLTheme with a purple seed run
 * through its Material 3 derivation, cards are TuxunCard, the
 * type scale is the app's Typography — purple stays as this
 * page's identity colour, but every component is family.
 *
 * Layout: Duck bar on top, the solid globe in the middle (country
 * outlines only, no text on it; connected hops light up green
 * with glowing flow lines), a lock + plain-language reassurance
 * card under it, and the connect control as a small button at
 * the bottom. Logs live behind 详细信息 with two views — the raw
 * log and a plain-language reading of it.
 */
private val TorPurpleSeed = Color(0xFF7C4DFF)

class TorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TuxunDLTheme(darkTheme = true, customSeed = TorPurpleSeed) {
                TorPage()
            }
        }
    }
}

private fun transportShortLabel(mode: TorManager.Mode): String = when (mode) {
    TorManager.Mode.AUTO -> "智能"
    TorManager.Mode.DIRECT -> "直连"
    TorManager.Mode.SNOWFLAKE -> "Snowflake"
    TorManager.Mode.OBFS4 -> "obfs4"
    TorManager.Mode.MEEK -> "meek"
    TorManager.Mode.CUSTOM -> "自定义网桥"
}

private fun transportLongLabel(mode: TorManager.Mode): String = when (mode) {
    TorManager.Mode.AUTO -> "智能（自动尝试）"
    TorManager.Mode.DIRECT -> "直连（无网桥）"
    TorManager.Mode.SNOWFLAKE -> "Snowflake 网桥"
    TorManager.Mode.OBFS4 -> "obfs4 网桥"
    TorManager.Mode.MEEK -> "meek 网桥"
    TorManager.Mode.CUSTOM -> "自定义网桥"
}

/** Duck bar input: full URL → as-is; bare host → https://; else a DuckDuckGo search. */
private fun resolveDuckInput(raw: String): String {
    val input = raw.trim()
    if (input.startsWith("http://") || input.startsWith("https://")) return input
    val looksLikeHost = !input.contains(' ') && input.contains('.') &&
        input.substringBefore('.').isNotBlank() && input.substringAfterLast('.').isNotBlank()
    if (looksLikeHost) {
        return if (input.substringBefore('/').endsWith(".onion")) {
            "http://$input"
        } else {
            "https://$input"
        }
    }
    return "https://duckduckgo.com/?q=" + URLEncoder.encode(input, "UTF-8")
}

/** Point every WebView in this app at Tor's HTTP tunnel. */
private fun applyTorProxy(context: Context, onDone: (ok: Boolean, error: String?) -> Unit) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
        onDone(false, "此设备的 WebView 不支持代理设置")
        return
    }
    val config = ProxyConfig.Builder()
        .addProxyRule("127.0.0.1:${TorManager.HTTP_TUNNEL_PORT}")
        .build()
    try {
        ProxyController.getInstance().setProxyOverride(
            config,
            ContextCompat.getMainExecutor(context)
        ) { onDone(true, null) }
    } catch (e: Exception) {
        onDone(false, e.message ?: e.javaClass.simpleName)
    }
}

private fun clearTorProxy(context: Context) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
    try {
        ProxyController.getInstance()
            .clearProxyOverride(ContextCompat.getMainExecutor(context)) { }
    } catch (_: Exception) {
        // Clearing is best-effort; Tor stopping closes the port anyway.
    }
}

/** Short label for a saved bridge line: transport + endpoint only. */
private fun bridgeShortLabel(line: String): String {
    val parts = line.split(" ")
    val head = listOfNotNull(parts.getOrNull(0), parts.getOrNull(1))
        .joinToString(" ")
    return head.take(44)
}

@Composable
private fun TorPage() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val state by TorManager.state.collectAsState()
    var mode by remember { mutableStateOf(TorManager.Mode.AUTO) }
    var customText by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var duckText by remember { mutableStateOf("") }
    var duckHint by remember { mutableStateOf<String?>(null) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var proxyBusy by remember { mutableStateOf(false) }

    // Region: asked once on first entry, remembered on device,
    // changeable later from 详细信息.
    var region by remember { mutableStateOf(TorLocalStore.loadRegion(context)) }
    var showRegionPicker by remember { mutableStateOf(region == null) }
    val severeNetwork = region?.severeNetwork == true

    // Custom bridges remembered on this device only.
    var savedBridges by remember { mutableStateOf(TorLocalStore.loadBridges(context)) }

    val connected = state.running && state.bootstrap >= 100
    val connecting = state.running && state.bootstrap in 0..99

    // In heavily filtered regions the 智能 chain is not offered:
    // fall back to Snowflake, keep every manual choice.
    LaunchedEffect(severeNetwork) {
        if (severeNetwork && mode == TorManager.Mode.AUTO) {
            mode = TorManager.Mode.SNOWFLAKE
        }
    }

    // Country outlines are parsed once off the main thread into
    // unit-sphere rings; the globe Canvas only rotates + projects.
    val appContext = context.applicationContext
    val regionalVariant = region == TorRegion.MAINLAND
    var globeRings by remember { mutableStateOf<List<GlobeRing>?>(null) }
    LaunchedEffect(regionalVariant) {
        globeRings = withContext(Dispatchers.IO) {
            WorldGlobeData.load(appContext, regionalVariant)
        }
    }

    // Lit points on the globe: located circuit hops in order (the
    // last one is the exit); if the circuit could not be read, the
    // exit alone. A hop whose position is unknown is not lit.
    val globePoints: List<GlobePoint> = remember(state.hops, state.exitLat, state.exitLon, connected) {
        if (!connected) {
            emptyList()
        } else {
            val located = state.hops.mapNotNull { hop ->
                val lat = hop.lat
                val lon = hop.lon
                if (lat != null && lon != null) {
                    GlobePoint(lat, lon, isExit = false)
                } else {
                    null
                }
            }
            when {
                located.size >= 2 -> located.mapIndexed { index, dot ->
                    if (index == located.lastIndex) dot.copy(isExit = true) else dot
                }

                state.exitLat != null && state.exitLon != null -> listOf(
                    GlobePoint(state.exitLat!!, state.exitLon!!, isExit = true)
                )

                else -> located
            }
        }
    }

    // If Tor dies while the browser is open, leave the browser too —
    // its proxy port is gone and nothing may fall back to direct.
    LaunchedEffect(state.running) {
        if (!state.running && browserUrl != null) {
            browserUrl = null
            clearTorProxy(context)
        }
    }

    fun submitDuck() {
        val query = duckText.trim()
        if (query.isEmpty() || proxyBusy) return
        if (!connected) {
            duckHint = if (connecting) {
                "正在连接 Tor，等连上之后再浏览"
            } else {
                "先连接 Tor，再用 Duck 栏搜索或打开网址"
            }
            return
        }
        duckHint = null
        val target = resolveDuckInput(query)
        proxyBusy = true
        applyTorProxy(context) { ok, error ->
            proxyBusy = false
            if (ok) {
                browserUrl = target
            } else {
                duckHint = "浏览器流量没能导入 Tor（$error），已取消打开，不会直连"
            }
        }
    }

    if (showRegionPicker) {
        RegionPicker(
            onPick = { picked ->
                region = picked
                TorLocalStore.saveRegion(context, picked)
                showRegionPicker = false
            }
        )
        return
    }

    if (browserUrl != null) {
        TorBrowser(
            initialUrl = browserUrl!!,
            onClose = {
                browserUrl = null
                clearTorProxy(context)
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            DuckBar(
                value = duckText,
                onValueChange = { duckText = it },
                onSubmit = { submitDuck() }
            )
            val hint = when {
                proxyBusy -> "正在把浏览器接到 Tor 上…"
                else -> duckHint
            }
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))
            TransportChips(
                modes = if (severeNetwork) {
                    TorManager.Mode.entries.filter { it != TorManager.Mode.AUTO }
                } else {
                    TorManager.Mode.entries
                },
                selected = mode,
                enabled = !state.running,
                onSelect = { mode = it }
            )
            if (mode == TorManager.Mode.AUTO && !state.running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "智能模式会依次试 Snowflake、obfs4、meek，卡住自动换下一个",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            if (mode == TorManager.Mode.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                CustomBridgeInput(
                    value = customText,
                    onValueChange = { customText = it },
                    enabled = !state.running
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "WebTunnel 目前没有公开发布的内置桥，粘贴桥行即可测试。点连接后，桥会记在本机，下次打开还在。",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                if (savedBridges.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SavedBridgeList(
                        bridges = savedBridges,
                        enabled = !state.running,
                        onFill = { customText = it },
                        onDelete = { savedBridges = TorLocalStore.removeBridge(context, it) }
                    )
                }
            }

            // ------- center: globe + exit location + lock reassurance -------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GlobeCanvas(
                    rings = globeRings,
                    points = globePoints,
                    connected = connected,
                    modifier = Modifier.size(264.dp)
                )
                Spacer(Modifier.height(16.dp))
                when {
                    connected && state.exitIp != null -> {
                        Text(
                            state.exitLocation ?: state.exitIp!!,
                            color = scheme.onBackground,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.exitResult ?: "出口 IP ${state.exitIp}",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    connected -> {
                        Text(
                            "已连接",
                            color = scheme.onBackground,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.testing) "正在确认出口位置…" else "点下方「测试出口」确认出口位置",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    connecting -> {
                        Text(
                            "连接中…",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    else -> {
                        Text(
                            "尚未连接",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                TuxunCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        LockBadge(Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            if (connected) {
                                Text(
                                    "流量已经过 3 重跳转，层层加密",
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "连接非常安全 · 当前传输：${state.connectedVia ?: transportLongLabel(mode)}",
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.hops.size >= 2) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        hopsLine(state.hops),
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Text(
                                    "连接后，流量会经过 3 个中继节点",
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "谁也看不到你去了哪里",
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // ------- bottom: status, thin progress, small buttons -------
            if (showDetails) {
                DetailsPanel(
                    logs = state.logs,
                    regionLabel = region?.label,
                    onChangeRegion = { showRegionPicker = true }
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                state.status,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            if (connecting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.bootstrap / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "收起" else "详细信息")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { TorManager.testExit() },
                    enabled = connected && !state.testing
                ) {
                    Text(if (state.testing) "测试中…" else "测试出口")
                }
                Spacer(Modifier.width(4.dp))
                if (state.running) {
                    OutlinedButton(onClick = { TorManager.stop() }) {
                        Text("断开")
                    }
                } else {
                    Button(
                        onClick = {
                            duckHint = null
                            if (mode == TorManager.Mode.CUSTOM) {
                                savedBridges = TorLocalStore.addBridges(context, customText)
                            }
                            TorManager.start(context, mode, customText)
                        }
                    ) {
                        Text("连接")
                    }
                }
            }
        }
    }
}

private fun hopsLine(hops: List<TorManager.Hop>): String = when {
    hops.size >= 3 ->
        "入口：${hops.first().place} → 中间：${hops[hops.size / 2].place} → 出口：${hops.last().place}"

    hops.size == 2 -> "入口：${hops.first().place} → 出口：${hops.last().place}"
    else -> hops.joinToString(" → ") { it.place }
}

/**
 * First-entry region choice: a full-screen list in the app's
 * card language. One tap picks, saves and continues.
 */
@Composable
private fun RegionPicker(onPick: (TorRegion) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            "你所在的地区",
            color = scheme.onBackground,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "不同地方的网络环境差别很大，选对地区，连接策略会跟着调。之后随时能在「详细信息」里改。",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TorRegion.ordered) { candidate ->
                TuxunCard(
                    onClick = { onPick(candidate) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        candidate.label,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DuckBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "D",
                    color = scheme.onPrimaryContainer,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "用 DuckDuckGo 搜索，或输入网址",
                            color = scheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )
            if (value.isNotEmpty()) {
                Text(
                    "✕",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onValueChange("") }
                        .padding(4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportChips(
    modes: List<TorManager.Mode>,
    selected: TorManager.Mode,
    enabled: Boolean,
    onSelect: (TorManager.Mode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { candidate ->
            val isSelected = candidate == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) scheme.primary else scheme.surfaceVariant
                    )
                    .then(
                        if (isSelected) {
                            Modifier
                        } else {
                            Modifier.border(
                                1.dp,
                                scheme.outlineVariant,
                                RoundedCornerShape(20.dp)
                            )
                        }
                    )
                    .clickable(enabled = enabled) { onSelect(candidate) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    transportShortLabel(candidate),
                    color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun CustomBridgeInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = scheme.onSurface,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(scheme.primary),
                minLines = 3,
                maxLines = 5,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "粘贴网桥行，每行一条\nwebtunnel [2001:db8::1]:443 指纹… url=https://…\nobfs4 1.2.3.4:443 指纹… cert=… iat-mode=0",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    inner()
                }
            )
        }
    }
}

/** Bridges remembered on this device: tap to fill, ✕ to forget. */
@Composable
private fun SavedBridgeList(
    bridges: List<String>,
    enabled: Boolean,
    onFill: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "已保存在本机的网桥（点按填入）",
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bridges.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant)
                    .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bridgeShortLabel(line),
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = enabled) { onFill(line) }
                        .padding(vertical = 6.dp)
                )
                TextButton(
                    onClick = { onDelete(line) },
                    enabled = enabled
                ) {
                    Text("删除", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Small filled lock, drawn — no emoji, no icon font needed. */
@Composable
private fun LockBadge(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val keyhole = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val shackleWidth = 1.8.dp.toPx()
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.26f, size.height * 0.04f),
            size = Size(size.width * 0.48f, size.height * 0.52f),
            style = Stroke(shackleWidth)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.10f, size.height * 0.42f),
            size = Size(size.width * 0.80f, size.height * 0.54f),
            cornerRadius = CornerRadius(2.5.dp.toPx())
        )
        drawCircle(
            color = keyhole,
            radius = size.width * 0.10f,
            center = Offset(size.width / 2f, size.height * 0.68f)
        )
    }
}

/**
 * 详细信息 panel: the log in two views — the raw lines (long-press
 * to select/copy) and a plain-language reading produced locally
 * by [TorLogTranslate] — plus the region entry.
 */
@Composable
private fun DetailsPanel(
    logs: List<String>,
    regionLabel: String?,
    onChangeRegion: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var viewIndex by remember { mutableStateOf(0) }
    val plainLogs = remember(logs) { TorLogTranslate.translate(logs) }
    val rawState = rememberLazyListState()
    val plainState = rememberLazyListState()
    LaunchedEffect(logs.size, viewIndex) {
        if (viewIndex == 0) {
            val total = rawState.layoutInfo.totalItemsCount
            if (total > 0) rawState.scrollToItem(total - 1)
        } else {
            val total = plainState.layoutInfo.totalItemsCount
            if (total > 0) plainState.scrollToItem(total - 1)
        }
    }
    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "详细信息",
                    color = scheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.weight(1f))
                if (regionLabel != null) {
                    Text(
                        "地区：$regionLabel",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = onChangeRegion) {
                        Text("更改", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TuxunSegmentedRow(
                options = listOf("原始日志", "人话版"),
                selectedIndex = viewIndex,
                onSelect = { viewIndex = it }
            )
            Spacer(Modifier.height(8.dp))
            if (viewIndex == 0) {
                SelectionContainer {
                    LazyColumn(
                        state = rawState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                    ) {
                        items(logs) { line ->
                            Text(
                                text = line,
                                color = scheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    "把常见记录翻成大白话，对不上规则的已省略；要看全量请切回原始日志。",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
                if (plainLogs.isEmpty()) {
                    Text(
                        "暂无可翻译的记录",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        state = plainState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                    ) {
                        items(plainLogs) { line ->
                            Text(
                                text = line,
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-app browser in the page's theme. Only reachable after the
 * WebView proxy override points at Tor's HTTP tunnel; if that
 * override could not be applied the caller never opens this.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TorBrowser(initialUrl: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }

    fun backOrClose() {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { backOrClose() }) {
                Text("←", fontSize = 19.sp)
            }
            Text(
                pageTitle.ifBlank { currentUrl },
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("✕", fontSize = 16.sp)
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            if (url != null) currentUrl = url
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) currentUrl = url
                            pageTitle = view?.title ?: ""
                        }
                    }
                    loadUrl(initialUrl)
                }
            },
            update = { view -> webView = view },
            onRelease = { view -> view.destroy() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
    // Hardware back mirrors the chrome back button.
    androidx.activity.compose.BackHandler { backOrClose() }
}
