package com.tuxundl.android.tor

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.tuxundl.android.remote.Remote
import com.tuxundl.android.ui.TuxunCard
import com.tuxundl.android.ui.TuxunSegmentedRow
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.startClashService
import com.tuxundl.android.util.withProfile
import java.net.URLEncoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Tor page (1.7.3): a separate full-screen page, apart from
 * the proxy main UI, wearing the main app's own design language —
 * the theme is the app's TuxunDLTheme with a purple seed run
 * through its Material 3 derivation, cards are TuxunCard, the type
 * scale is the app's Typography; purple stays as this page's
 * identity colour, but every component is family.
 *
 * Connecting here is whole-device Tor mode: [TorEgress] starts
 * the engine, verifies the exit, and only then points the main
 * tunnel at Tor. Layout: Duck bar on top, the solid globe in the
 * middle (country outlines only, no text on it; connected hops
 * light up green with glowing flow lines), a lock + reassurance
 * card under it, and the connect control as a small button at the
 * bottom. Logs live behind 详细信息 in two views — the raw log and
 * a plain-language reading of it — with one-tap copy.
 */
private val TorPurpleSeed = Color(0xFF7C4DFF)

class TorActivity : ComponentActivity() {

    private var vpnWaiter: CompletableDeferred<Boolean>? = null

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val waiter = vpnWaiter
        vpnWaiter = null
        if (result.resultCode == RESULT_OK) {
            // Permission just granted: starting again goes through.
            startClashService()
            waiter?.complete(true)
        } else {
            waiter?.complete(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorEgress.sweepStaleArm()
        setContent {
            TuxunDLTheme(darkTheme = true, customSeed = TorPurpleSeed) {
                TorPage(ensureTunnel = { ensureTunnel() })
            }
        }
    }

    /**
     * The tunnel half of a Tor connection: an imported active
     * profile must exist; if the tunnel is not running yet, start
     * it, asking for the VPN permission when Android requires it.
     */
    private suspend fun ensureTunnel(): Boolean {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) return false
        if (Remote.broadcasts.clashRunning) return true
        val request = withContext(Dispatchers.Main) { startClashService() }
        if (request == null) return true
        val waiter = CompletableDeferred<Boolean>()
        vpnWaiter = waiter
        withContext(Dispatchers.Main) { vpnLauncher.launch(request) }
        return waiter.await()
    }
}

private fun transportShortLabel(mode: TorManager.Mode): String = mode.via

private fun transportLongLabel(mode: TorManager.Mode): String = mode.label

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
private fun applyTorProxy(
    context: android.content.Context,
    onDone: (ok: Boolean, error: String?) -> Unit
) {
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

private fun clearTorProxy(context: android.content.Context) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TorPage(ensureTunnel: suspend () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by TorManager.state.collectAsState()
    val egress by TorEgress.state.collectAsState()

    val connected = state.running && state.bootstrap >= 100
    val connecting = state.running && state.bootstrap in 0..99
    val egressActive = egress.phase == TorEgress.Phase.ACTIVE
    val egressBusy = egress.phase == TorEgress.Phase.STARTING ||
        egress.phase == TorEgress.Phase.VERIFYING ||
        egress.phase == TorEgress.Phase.ARMING
    val busy = egressBusy || connecting

    var mode by remember { mutableStateOf(TorManager.Mode.AUTO) }
    var modeTouched by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    var duckNote by remember { mutableStateOf<String?>(null) }
    var savedBridges by remember { mutableStateOf(TorLocalStore.loadBridges(context)) }

    // First visit: ask the region before anything else. The
    // choice is remembered and can be re-opened from details.
    var region by remember { mutableStateOf(TorLocalStore.loadRegion(context)) }
    var showRegionPicker by remember { mutableStateOf(region == null) }

    val showCustom = mode == TorManager.Mode.CUSTOM

    // Region-aware default: severe-filtering regions start on
    // Snowflake (smart mode off there), everywhere else smart.
    // Runs when the picker closes as well as on first load.
    LaunchedEffect(region) {
        val picked = region
        if (picked != null && !modeTouched) {
            mode = if (picked.severeNetwork) {
                TorManager.Mode.SNOWFLAKE
            } else {
                TorManager.Mode.AUTO
            }
        }
    }

    // Bridges saved on this device are prefilled into the custom
    // field silently — no list, no note on the main page.
    LaunchedEffect(mode) {
        if (mode == TorManager.Mode.CUSTOM && customText.isBlank()) {
            val saved = TorLocalStore.loadBridges(context)
            if (saved.isNotEmpty()) customText = saved.joinToString("\n")
        }
    }

    // When a connection finishes on its own (Tor died, watchdog
    // reverted), drop the in-app browser's Tor proxy override too.
    LaunchedEffect(state.running) {
        if (!state.running && browserUrl != null) {
            clearTorProxy(context)
            browserUrl = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Duck bar ──────────────────────────────────────
            DuckBar(
                connected = connected,
                onSubmit = { input ->
                    if (!connected) return@DuckBar
                    val target = resolveDuckInput(input)
                    applyTorProxy(context) { ok, error ->
                        if (ok) {
                            duckNote = null
                            browserUrl = target
                        } else {
                            // Never fall back to a direct open.
                            duckNote = "内置浏览已取消：代理设置失败" +
                                "（${error ?: "未知"}），没有直连打开"
                        }
                    }
                }
            )
            duckNote?.let { note ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Transport picker ──────────────────────────────
            TransportChips(
                mode = mode,
                onModeChange = { mode = it; modeTouched = true }
            )

            if (showCustom) {
                Spacer(Modifier.height(10.dp))
                CustomBridgeInput(text = customText, onChange = { customText = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showGuide = !showGuide }) {
                        Text(
                            text = if (showGuide) "收起网桥说明" else "如何获取我自己的网桥？",
                            fontSize = 12.sp
                        )
                    }
                }
                if (showGuide) {
                    BridgeGuide()
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── The globe ─────────────────────────────────────
            val globePoints = remember(state.hops, state.exitLat, state.exitLon, connected) {
                if (!connected) {
                    emptyList()
                } else {
                    val pts = mutableListOf<GlobePoint>()
                    state.hops.forEach { hop ->
                        val la = hop.lat
                        val lo = hop.lon
                        if (la != null && lo != null) {
                            pts += GlobePoint(la, lo, isExit = false)
                        }
                    }
                    val eLa = state.exitLat
                    val eLo = state.exitLon
                    if (eLa != null && eLo != null) {
                        val last = pts.lastOrNull()
                        if (last == null ||
                            kotlin.math.abs(last.lat - eLa) > 0.01 ||
                            kotlin.math.abs(last.lon - eLo) > 0.01
                        ) {
                            pts += GlobePoint(eLa, eLo, isExit = true)
                        } else {
                            pts[pts.lastIndex] = last.copy(isExit = true)
                        }
                    } else if (pts.isNotEmpty()) {
                        // No exit coordinates: mark the last hop as
                        // the visible end of the route rather than
                        // drawing nothing.
                        pts[pts.lastIndex] = pts.last().copy(isExit = true)
                    }
                    pts
                }
            }
            val globeRings = remember(region) {
                WorldGlobeData.load(
                    context,
                    regionalVariant = region == TorRegion.MAINLAND
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlobeCanvas(
                    rings = globeRings,
                    points = globePoints,
                    connected = connected,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── Exit + lock reassurance ───────────────────────
            val exitText = when {
                connected && state.exitLocation != null ->
                    "出口在 ${state.exitLocation}"
                connected && state.exitIp != null ->
                    "出口 ${state.exitIp}"
                connecting -> "正在建立匿名线路…"
                else -> "还没有连接"
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = exitText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                if (connected && state.hops.isNotEmpty() && globePoints.isEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "线路已读到，但节点位置暂时定位失败",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
                TuxunCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LockBadge()
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val lockText = when {
                                    egressActive ->
                                        "全机流量正经 Tor 出口，层层加密"
                                    connected ->
                                        "Tor 已连通，正在切换全机出口"
                                    else ->
                                        "连接后，全机流量经三重跳转，层层加密"
                                }
                                Text(
                                    text = lockText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "当前传输：${state.connectedVia ?: transportLongLabel(mode)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (connected && state.hops.isNotEmpty()) {
                                    val path = state.hops
                                        .joinToString(" → ") { it.place }
                                    Text(
                                        text = "路径：$path",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (egressActive || connected) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Tor 只转发 TCP：语音通话、游戏等 UDP 流量会被拦下，不会直连泄露。",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Bottom: status + connect ──────────────────────
            val statusText = when (egress.phase) {
                TorEgress.Phase.ACTIVE -> "已连接 · 全机流量正经 Tor 出口"
                TorEgress.Phase.FAILED,
                TorEgress.Phase.INTERRUPTED -> egress.message ?: state.status
                TorEgress.Phase.STARTING,
                TorEgress.Phase.VERIFYING,
                TorEgress.Phase.ARMING -> egress.message ?: state.status
                TorEgress.Phase.OFF -> state.status
            }
            Text(
                text = statusText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            if (connecting || egressBusy) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.bootstrap.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.running || egressBusy) {
                    OutlinedButton(onClick = { TorEgress.disconnect() }) {
                        Text("断开")
                    }
                } else {
                    Button(
                        onClick = {
                            if (mode == TorManager.Mode.CUSTOM &&
                                customText.isNotBlank()
                            ) {
                                savedBridges = TorLocalStore
                                    .addBridges(context, customText)
                            }
                            TorEgress.connect(
                                context = context,
                                mode = mode,
                                customText = customText,
                                ensureTunnel = ensureTunnel
                            )
                        }
                    ) {
                        Text("连接")
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { TorManager.testExit() },
                    enabled = connected
                ) {
                    Text("测试出口")
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "收起详情" else "详细信息")
                }
            }

            if (showDetails) {
                Spacer(Modifier.height(10.dp))
                DetailsPanel(
                    state = state,
                    mode = mode,
                    region = region,
                    bridges = savedBridges,
                    onOpenRegionPicker = { showRegionPicker = true },
                    onDeleteBridge = { line ->
                        savedBridges = TorLocalStore.removeBridge(context, line)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                )
            }
        }

        // In-app browser sheet: Duck results inside the app,
        // always through Tor; closing it clears the proxy override.
        browserUrl?.let { url ->
            TorBrowser(
                url = url,
                onClose = {
                    clearTorProxy(context)
                    browserUrl = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // First-run gate: region choice before the page is usable.
        if (showRegionPicker) {
            RegionPicker(
                current = region,
                onPick = { picked ->
                    TorLocalStore.saveRegion(context, picked)
                    region = picked
                    showRegionPicker = false
                },
                onDismiss = if (region != null) {
                    { showRegionPicker = false }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A tiny drawn padlock in a rounded chip — identity, not decoration. */
@Composable
private fun LockBadge() {
    Canvas(modifier = Modifier.size(30.dp)) {
        val ink = Color(0xFFB39DDB)
        val bodyW = size.width * 0.62f
        val bodyH = size.height * 0.44f
        val bodyX = (size.width - bodyW) / 2f
        val bodyY = size.height * 0.46f
        // Shackle.
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.28f, size.height * 0.10f),
            size = Size(size.width * 0.44f, size.height * 0.42f),
            style = Stroke(width = size.width * 0.075f)
        )
        // Body.
        drawRoundRect(
            color = ink,
            topLeft = Offset(bodyX, bodyY),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f)
        )
        // Keyhole.
        drawCircle(
            color = Color(0xFF1B1030),
            radius = size.width * 0.075f,
            center = Offset(size.width / 2f, bodyY + bodyH * 0.42f)
        )
    }
}

@Composable
private fun DuckBar(
    connected: Boolean,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Duck",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onSubmit(text) }
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        text = if (connected) "搜索或输入网址（经 Tor 打开）" else "连接后可经 Tor 搜索",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportChips(
    mode: TorManager.Mode,
    onModeChange: (TorManager.Mode) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TorManager.Mode.values().forEach { candidate ->
            val selected = candidate == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(50)
                    )
                    .clickable { onModeChange(candidate) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    text = transportShortLabel(candidate),
                    fontSize = 13.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomBridgeInput(text: String, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 96.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        text = "粘贴网桥行，一行一个，例如：\nwebtunnel … / obfs4 … / snowflake …",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )
    }
}

/**
 * Where to get a personal bridge. Kept compact and factual; the
 * bridges themselves are per-person and live on this device only.
 */
@Composable
private fun BridgeGuide() {
    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = "网桥是每人单独领取的，领到后粘贴到上面的输入框再点连接即可。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "· 官网领取：bridges.torproject.org（选 WebTunnel）\n" +
                    "· 发邮件给 bridges@torproject.org 索取\n" +
                    "· Telegram 机器人 @GetBridgesBot",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "网桥只保存在本机，不会上传，也不会写进备份；换了新桥直接粘贴即可。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailsPanel(
    state: TorManager.UiState,
    mode: TorManager.Mode,
    region: TorRegion?,
    bridges: List<String>,
    onOpenRegionPicker: () -> Unit,
    onDeleteBridge: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var viewIndex by remember { mutableStateOf(0) }
    var copied by remember { mutableStateOf(false) }

    val plainLines = remember(state.logs) { TorLogTranslate.translate(state.logs) }
    val shownLogs = if (viewIndex == 0) state.logs else plainLines

    // Keep the newest log line visible.
    LaunchedEffect(shownLogs.size, viewIndex) {
        if (shownLogs.isNotEmpty()) {
            listState.scrollToItem(shownLogs.lastIndex)
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    TuxunCard(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            item {
                Text(
                    text = "详细信息",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "地区：${region?.label ?: "未选择"}" +
                        if (region?.severeNetwork == true) {
                            "（网络限制较严，智能连接已关闭）"
                        } else {
                            ""
                        },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onOpenRegionPicker,
                    modifier = Modifier.alpha(0.9f)
                ) {
                    Text("更改地区", fontSize = 12.sp)
                }
                Text(
                    text = "状态：${state.status}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "进程：${if (state.running) "运行中" else "未运行"} · " +
                        "SOCKS 127.0.0.1:${TorManager.SOCKS_PORT} · " +
                        "HTTP 127.0.0.1:${TorManager.HTTP_TUNNEL_PORT}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "传输：${transportLongLabel(mode)} · " +
                        "引导进度：${state.bootstrap}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.exitIp?.let { ip ->
                    Text(
                        text = "出口 IP：$ip" +
                            (state.exitLocation?.let { "（$it）" } ?: ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.hops.isNotEmpty()) {
                    Text(
                        text = "线路：" + state.hops.joinToString(" → ") { it.place },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TuxunSegmentedRow(
                        options = listOf("原始日志", "人话版"),
                        selectedIndex = viewIndex,
                        onSelect = { viewIndex = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(shownLogs.joinToString("\n"))
                            )
                            copied = true
                        }
                    ) {
                        Text(if (copied) "已复制" else "复制", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (shownLogs.isEmpty()) {
                item {
                    Text(
                        text = "暂无日志",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(shownLogs) { line ->
                    SelectionContainer {
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (bridges.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "保存在本机的网桥",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(bridges) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bridgeShortLabel(line),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onDeleteBridge(line) }) {
                            Text("删除", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * First-run gate: a full-screen card asking where the user is.
 * Countries with severe filtering get smart mode disabled and
 * start on Snowflake; everything else defaults to smart.
 */
@Composable
private fun RegionPicker(
    current: TorRegion?,
    onPick: (TorRegion) -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val entries = TorRegion.ordered
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "你在哪个地区？",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "不同地区的网络环境不一样，选对了连接更容易成功。这个选择只保存在本机。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries) { entry ->
                    val selected = entry == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onPick(entry) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (entry.severeNetwork) {
                                Text(
                                    text = "网络限制较严：智能连接关闭，默认用 Snowflake",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
            if (onDismiss != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TorBrowser(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var loading by remember(url) { mutableStateOf(true) }
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "经 Tor 打开",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (loading) {
                Text(
                    text = "加载中…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = onClose) { Text("关闭") }
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
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
