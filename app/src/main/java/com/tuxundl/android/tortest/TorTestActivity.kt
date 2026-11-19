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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tor page (tor-spike branch only), design as locked by the user:
 * a separate page, fully purple (Firefox Private Browsing vibe),
 * exquisite but extremely minimal. Duck bar on top, a solid globe
 * the user can spin in the middle (phase 3: orthographic sphere,
 * country outlines only, no text on it; hop markers + dashed route
 * arcs projected onto it), a lock + plain-language reassurance
 * under it, and the connect control as a small button at the
 * bottom. Logs live behind 详细信息 and are never the main view.
 */
private val DeepTop = Color(0xFF3A1470)
private val DeepMid = Color(0xFF22084A)
private val DeepBottom = Color(0xFF150330)
private val Accent = Color(0xFFB388FF)
private val OnPurple = Color.White
private val OnPurpleDim = Color.White.copy(alpha = 0.62f)
private val OnPurpleFaint = Color.White.copy(alpha = 0.38f)

class TorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = DeepBottom,
                    surface = DeepMid
                )
            ) {
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

@Composable
private fun TorPage() {
    val context = LocalContext.current
    val state by TorManager.state.collectAsState()
    var mode by remember { mutableStateOf(TorManager.Mode.AUTO) }
    var customText by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var duckText by remember { mutableStateOf("") }
    var duckHint by remember { mutableStateOf<String?>(null) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var proxyBusy by remember { mutableStateOf(false) }

    val connected = state.running && state.bootstrap >= 100
    val connecting = state.running && state.bootstrap in 0..99

    // Country outlines are parsed once off the main thread into
    // unit-sphere rings; the globe Canvas only rotates + projects.
    val appContext = context.applicationContext
    var globeRings by remember { mutableStateOf<List<GlobeRing>?>(null) }
    LaunchedEffect(Unit) {
        globeRings = withContext(Dispatchers.IO) { WorldGlobeData.load(appContext) }
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
            .background(
                Brush.verticalGradient(listOf(DeepTop, DeepMid, DeepBottom))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
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
                    color = Accent,
                    fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))
            TransportChips(
                selected = mode,
                enabled = !state.running,
                onSelect = { mode = it }
            )
            if (mode == TorManager.Mode.AUTO && !state.running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "智能模式会依次试 Snowflake、obfs4、meek，卡住自动换下一个",
                    color = OnPurpleFaint,
                    fontSize = 11.5.sp,
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
                    "WebTunnel 目前没有公开发布的内置桥，粘贴桥行即可测试。",
                    color = OnPurpleFaint,
                    fontSize = 11.5.sp
                )
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
                            color = OnPurple,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.exitResult ?: "出口 IP ${state.exitIp}",
                            color = OnPurpleDim,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    connected -> {
                        Text(
                            "已连接",
                            color = OnPurple,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.testing) "正在确认出口位置…" else "点下方「测试出口」确认出口位置",
                            color = OnPurpleDim,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    connecting -> {
                        Text(
                            "连接中…",
                            color = OnPurpleDim,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    else -> {
                        Text(
                            "尚未连接",
                            color = OnPurpleFaint,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                if (connected) {
                    Row(verticalAlignment = Alignment.Top) {
                        LockBadge(Modifier.size(17.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "流量已经过 3 重跳转，层层加密",
                                color = OnPurple,
                                fontSize = 13.5.sp
                            )
                            Text(
                                "连接非常安全 · 当前传输：${state.connectedVia ?: transportLongLabel(mode)}",
                                color = OnPurpleDim,
                                fontSize = 12.5.sp
                            )
                            if (state.hops.size >= 2) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    hopsLine(state.hops),
                                    color = OnPurpleDim,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LockBadge(
                            Modifier.size(15.dp),
                            tint = OnPurpleFaint
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "连接后，流量会经过 3 个中继节点，谁也看不到你去了哪里",
                            color = OnPurpleFaint,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ------- bottom: status, thin progress, small buttons -------
            if (showDetails) {
                DetailsLog(logs = state.logs)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                state.status,
                color = OnPurpleDim,
                fontSize = 13.sp,
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
                        .clip(RoundedCornerShape(50)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(
                        if (showDetails) "收起" else "详细信息",
                        color = OnPurpleDim,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { TorManager.testExit() },
                    enabled = connected && !state.testing
                ) {
                    Text(
                        if (state.testing) "测试中…" else "测试出口",
                        color = if (connected) OnPurple else OnPurpleFaint,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (state.running) {
                            TorManager.stop()
                        } else {
                            duckHint = null
                            TorManager.start(context, mode, customText)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = DeepMid
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (state.running) "断开" else "连接",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
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

@Composable
private fun DuckBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "D",
                color = DeepMid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = OnPurple, fontSize = 14.5.sp),
            cursorBrush = SolidColor(OnPurple),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "用 DuckDuckGo 搜索，或输入网址",
                        color = OnPurpleFaint,
                        fontSize = 14.5.sp
                    )
                }
                inner()
            }
        )
        if (value.isNotEmpty()) {
            Text(
                "✕",
                color = OnPurpleDim,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
                    .padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportChips(
    selected: TorManager.Mode,
    enabled: Boolean,
    onSelect: (TorManager.Mode) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TorManager.Mode.entries.forEach { candidate ->
            val isSelected = candidate == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.10f)
                    )
                    .clickable(enabled = enabled) { onSelect(candidate) }
                    .padding(horizontal = 13.dp, vertical = 6.dp)
            ) {
                Text(
                    transportShortLabel(candidate),
                    color = if (isSelected) DeepMid else OnPurpleDim,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = OnPurple,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(OnPurple),
            minLines = 3,
            maxLines = 5,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "粘贴网桥行，每行一条\nwebtunnel [2001:db8::1]:443 指纹… url=https://…\nobfs4 1.2.3.4:443 指纹… cert=… iat-mode=0",
                        color = OnPurpleFaint,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                inner()
            }
        )
    }
}

/** Small filled lock, drawn — no emoji, no icon font needed. */
@Composable
private fun LockBadge(modifier: Modifier = Modifier, tint: Color = Accent) {
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
            color = DeepBottom,
            radius = size.width * 0.10f,
            center = Offset(size.width / 2f, size.height * 0.68f)
        )
    }
}

@Composable
private fun DetailsLog(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("详细信息", color = OnPurpleDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
        ) {
            items(logs) { line ->
                Text(
                    text = line,
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * In-app browser with purple chrome. Only reachable after the
 * WebView proxy override points at Tor's HTTP tunnel; if that
 * override could not be applied the caller never opens this.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TorBrowser(initialUrl: String, onClose: () -> Unit) {
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
            .background(Brush.verticalGradient(listOf(DeepTop, DeepBottom)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { backOrClose() }) {
                Text("←", color = OnPurple, fontSize = 19.sp)
            }
            Text(
                pageTitle.ifBlank { currentUrl },
                color = OnPurple,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) {
                Text("✕", color = OnPurple, fontSize = 16.sp)
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
