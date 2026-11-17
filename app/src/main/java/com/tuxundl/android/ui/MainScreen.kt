package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.design.model.AnimationStyle
import com.tuxundl.android.core.util.trafficDownload
import com.tuxundl.android.core.util.trafficUpload
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.util.toBytesString
import com.tuxundl.android.design.util.toDateStr
import com.tuxundl.android.util.DailyTrafficDay
import com.tuxundl.android.util.UpdateInfo
import kotlinx.coroutines.delay
import kotlin.math.roundToLong
import com.tuxundl.android.design.R as DesignR

/**
 * State holder for the Compose home screen.
 *
 * All values are written by MainActivity (from withClash / withProfile,
 * exactly like the old MainDesign setters did) and read by [MainScreen].
 */
class MainUiState {
    var clashRunning by mutableStateOf(false)
    var mode by mutableStateOf(TunnelState.Mode.Rule)
    var profileName by mutableStateOf<String?>(null)
    var hasProviders by mutableStateOf(false)

    /** Packed traffic value from IClashManager.queryTrafficTotal(). */
    var traffic by mutableStateOf(0L)

    /** Non-null while the About dialog should be shown. */
    var aboutVersion by mutableStateOf<String?>(null)

    // Home card visibility toggles (Appearance settings; power circle always shows).
    var showTraffic by mutableStateOf(true)
    var showProxy by mutableStateOf(true)
    var showProfile by mutableStateOf(true)
    var showProviders by mutableStateOf(true)

    /** Main selector group and its current node, for the node tile. */
    var mainGroupName by mutableStateOf<String?>(null)
    var currentNodeName by mutableStateOf<String?>(null)
    var currentNodeDelay by mutableStateOf(-1)
    var testingNode by mutableStateOf(false)
    var testingAll by mutableStateOf(false)

    /** Connection duration: runningSince mirrors UiStore, connectedMillis is refreshed per second. */
    var runningSince by mutableStateOf(0L)
    var connectedMillis by mutableStateOf(0L)

    /** Non-null while the "new version available" card should be shown. */
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    /** Non-null while the full-screen update page should be shown. */
    var updatePageInfo by mutableStateOf<UpdateInfo?>(null)

    /** False until the first-run disclaimer is accepted (gate screen). */
    var disclaimerAccepted by mutableStateOf(false)

    /**
     * Per-second traffic rates (bytes/s) for the monitor chart, oldest
     * first, capped at 60 samples by MainActivity.
     */
    var trafficSamples by mutableStateOf<List<TrafficSample>>(emptyList())

    /** Whether the traffic monitor card is shown (Appearance toggle). */
    var showTrafficMonitor by mutableStateOf(true)

    /** Home card order (keys: traffic, proxy, profile, providers, monitor). */
    var cardOrder by mutableStateOf(
        listOf("monitor", "traffic", "proxy", "profile", "providers"),
    )

    /** Active profile's subscription usage; totals of 0 mean "no data". */
    var subUpload by mutableStateOf(0L)
    var subDownload by mutableStateOf(0L)
    var subTotal by mutableStateOf(0L)
    var subExpire by mutableStateOf(0L)

    /** Forwarded traffic today (only counted while the app is open). */
    var todayUp by mutableStateOf(0L)
    var todayDown by mutableStateOf(0L)

    /** This week's days with data (Monday..today), oldest first. */
    var currentWeekDays by mutableStateOf<List<DailyTrafficDay>>(emptyList())

    /** Last week's days with data (Monday..Sunday), oldest first. */
    var lastWeekDays by mutableStateOf<List<DailyTrafficDay>>(emptyList())

    val snackbarHostState = SnackbarHostState()
}

/** One second of traffic: upload/download rates in bytes per second. */
data class TrafficSample(val upRate: Long, val downRate: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.application_name)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UpdateCard(state = state, onRequest = onRequest)

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerCircle(state = state, onRequest = onRequest)
            }

            ModeSelector(state = state, onRequest = onRequest)

            HomeCards(state = state, onRequest = onRequest)

            BottomNavRow(onRequest = onRequest)
        }
    }

    val aboutVersion = state.aboutVersion
    if (aboutVersion != null) {
        AlertDialog(
            onDismissRequest = { state.aboutVersion = null },
            title = { Text(text = stringResource(R.string.application_name)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.about_globe),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = aboutVersion)
                }
            },
            confirmButton = {
                TextButton(onClick = { state.aboutVersion = null }) {
                    Text(text = stringResource(DesignR.string.ok))
                }
            },
        )
    }

    // Full-screen update page: shown once per version per launch by the
    // startup check; closing it leaves the update card as the reminder.
    val updatePageInfo = state.updatePageInfo
    if (updatePageInfo != null) {
        UpdateFullScreen(
            info = updatePageInfo,
            onClose = { state.updatePageInfo = null },
        )
    }
}

/**
 * "New version available" card pinned to the top of the home screen.
 * Download opens the release APK (or page) in the browser; the close
 * button dismisses this version for good (handled by MainActivity).
 */
@Composable
private fun UpdateCard(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val info = state.updateInfo

    AnimatedVisibility(
        visible = info != null,
        enter = tuxunEnterTransition(),
        exit = tuxunExitTransition(),
    ) {
        if (info == null) return@AnimatedVisibility

        val context = LocalContext.current
        val targetUrl = info.downloadUrl ?: info.pageUrl

        TuxunCard(
            shape = RoundedCornerShape(TuxunCardCornerLarge),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.update_available, info.version),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (targetUrl != null) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)),
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.update_download))
                    }
                }
                IconButton(onClick = { onRequest(MainDesign.Request.DismissUpdate) }) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_close),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

/**
 * The big round start/stop button at the center of the home screen.
 *
 * Stopped: outlined circle with a power icon and a "tap to start" hint.
 * Running: filled circle, live up/down speed inside, and a slowly rotating,
 * breathing progress arc around it. Tapping toggles the service.
 */
@Composable
private fun PowerCircle(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val running = state.clashRunning
    val style = animStyle()
    val speedEnterSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = true)
    val speedExitSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = false)

    var upSpeed by remember { mutableStateOf("0 B/s") }
    var downSpeed by remember { mutableStateOf("0 B/s") }

    // Sample the packed totals once per second (MainActivity refreshes them
    // at the same cadence) and turn the delta into a live rate.
    LaunchedEffect(running) {
        if (!running) {
            upSpeed = "0 B/s"
            downSpeed = "0 B/s"
            return@LaunchedEffect
        }

        var prevUp = -1L
        var prevDown = -1L
        var prevAt = 0L

        while (true) {
            val up = trafficUploadBytes(state.traffic)
            val down = trafficDownloadBytes(state.traffic)
            val now = SystemClock.elapsedRealtime()

            if (prevAt != 0L && now > prevAt) {
                val seconds = (now - prevAt) / 1000.0
                upSpeed = formatSpeed(((up - prevUp).coerceAtLeast(0) / seconds).roundToLong())
                downSpeed = formatSpeed(((down - prevDown).coerceAtLeast(0) / seconds).roundToLong())
            }

            prevUp = up
            prevDown = down
            prevAt = now

            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "powerCircle")
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringRotation",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val track = MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (running) onPrimary else primary

    // Smooth the start/stop flip: the disc grows a touch, fills with the
    // theme colour and its content cross-fades instead of snapping. The
    // size spring is the circle's own tuning: bouncy in Spring style,
    // near-critically damped in Soft, a plain short tween in Reduced.
    val circleColor by animateColorAsState(
        targetValue = if (running) primary else MaterialTheme.colorScheme.surface,
        animationSpec = tuxunColorSpec(style),
        label = "circleColor",
    )
    val circleSizeSpec: AnimationSpec<Dp> = when (style) {
        AnimationStyle.Spring -> spring(dampingRatio = 0.8f, stiffness = 300f)
        AnimationStyle.Soft -> spring(dampingRatio = 0.92f, stiffness = 420f)
        AnimationStyle.Reduced -> tween(durationMillis = 120)
    }
    val circleSize by animateDpAsState(
        targetValue = if (running) 204.dp else 196.dp,
        animationSpec = circleSizeSpec,
        label = "circleSize",
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tuxunColorSpec(style),
        label = "circleContentColor",
    )
    val powerInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier.size(224.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()

            drawCircle(
                color = track,
                style = Stroke(width = strokeWidth),
            )
            if (running) {
                drawArc(
                    color = primary.copy(alpha = ringAlpha),
                    startAngle = ringRotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(circleColor)
                .then(
                    if (running) {
                        Modifier
                    } else {
                        Modifier.border(2.dp, primary, CircleShape)
                    },
                )
                .tuxunPressScale(powerInteraction, pressedScale = 0.96f)
                .clickable(
                    interactionSource = powerInteraction,
                    indication = null,
                ) { onRequest(MainDesign.Request.ToggleStatus) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_toggle_on),
                    contentDescription = null,
                    tint = animatedContentColor,
                    modifier = Modifier.size(54.dp),
                )
                AnimatedContent(
                    targetState = running,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "powerStatus",
                ) { isRunning ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (isRunning) DesignR.string.running else DesignR.string.stopped,
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isRunning) onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isRunning) {
                            // Live speed: soft cross-fade on each new
                            // sample instead of the digits snapping.
                            AnimatedContent(
                                targetState = "↑ $upSpeed   ↓ $downSpeed",
                                transitionSpec = {
                                    fadeIn(speedEnterSpec) togetherWith
                                        fadeOut(speedExitSpec)
                                },
                                label = "powerSpeed",
                            ) { speedText ->
                                Text(
                                    text = speedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = animatedContentColor,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(DesignR.string.tap_to_start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = animatedContentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rule / Global / Direct switch, right under the power circle. */
@Composable
private fun ModeSelector(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val modes = listOf(
        TunnelState.Mode.Rule,
        TunnelState.Mode.Global,
        TunnelState.Mode.Direct,
    )

    // Sliding-pill segmented row: the selected background glides.
    TuxunSegmentedRow(
        options = listOf(
            stringResource(DesignR.string.rule_mode),
            stringResource(DesignR.string.global_mode),
            stringResource(DesignR.string.direct_mode),
        ),
        selectedIndex = modes.indexOf(state.mode),
        onSelect = { onRequest(MainDesign.Request.SetMode(modes[it])) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private class HomeTile(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    val lines: List<String>,
    /** Null = display-only tile (traffic totals), not clickable. */
    val request: MainDesign.Request?,
    /** Optional small delay-test button in the tile's header row. */
    val testRequest: MainDesign.Request? = null,
    val testing: Boolean = false,
)

/** One block of the ordered home-card area: the monitor card or a row of tiles. */
private sealed interface HomeBlock {
    object Monitor : HomeBlock
    data class TileRow(val tiles: List<HomeTile>) : HomeBlock
}

/**
 * The orderable home cards under the mode selector.
 *
 * Blocks follow MainUiState.cardOrder (Appearance settings): the traffic
 * monitor renders as a full-width card in sequence, the other cards as
 * two-column tile rows. The current-node tile is pinned as the first tile
 * of the first tile row. The subscription card rides directly under the
 * monitor card; when the monitor is hidden it sits above this whole area
 * instead (rendered by [MainScreen] through [SubscriptionCard] placement
 * in the plan below).
 */
@Composable
private fun HomeCards(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val tilesByKey = mutableMapOf<String, HomeTile>()

    if (state.showTraffic) {
        tilesByKey["traffic"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_swap_vert,
            title = R.string.traffic,
            lines = listOf(
                "↑ ${state.traffic.trafficUpload()}",
                "↓ ${state.traffic.trafficDownload()}",
            ),
            request = null,
            testRequest = MainDesign.Request.TestAllGroups,
            testing = state.testingAll,
        )
    }
    if (state.showProxy) {
        tilesByKey["proxy"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_apps,
            title = DesignR.string.proxy,
            lines = listOf(modeText(state.mode)),
            request = MainDesign.Request.OpenProxy,
        )
    }
    if (state.showProfile) {
        tilesByKey["profile"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_view_list,
            title = DesignR.string.profile,
            lines = listOf(
                state.profileName?.let {
                    stringResource(DesignR.string.format_profile_activated, it)
                } ?: stringResource(DesignR.string.not_selected),
            ),
            request = MainDesign.Request.OpenProfiles,
        )
    }
    if (state.showProviders && state.clashRunning && state.hasProviders) {
        tilesByKey["providers"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_extension,
            title = DesignR.string.providers,
            lines = emptyList(),
            request = MainDesign.Request.OpenProviders,
        )
    }

    // Current node tile: always shown when a main selector group exists,
    // independent of the card toggles (it is the most-glanced info).
    val nodeName = state.currentNodeName
    val nodeTile = if (nodeName != null) {
        HomeTile(
            icon = DesignR.drawable.ic_baseline_vpn_lock,
            title = R.string.current_node,
            lines = listOf(
                nodeName,
                if (state.currentNodeDelay > 0) {
                    "${state.currentNodeDelay} ms"
                } else {
                    "-"
                },
            ),
            request = MainDesign.Request.OpenProxy,
            testRequest = MainDesign.Request.TestCurrentGroup,
            testing = state.testingNode,
        )
    } else {
        null
    }

    // Build the render plan in the stored order.
    val blocks = mutableListOf<HomeBlock>()
    val buffer = mutableListOf<HomeTile>()
    var nodePending = nodeTile != null

    fun flushTiles() {
        if (nodePending && nodeTile != null) {
            buffer.add(0, nodeTile)
            nodePending = false
        }
        buffer.chunked(2).forEach { blocks += HomeBlock.TileRow(it) }
        buffer.clear()
    }

    for (key in state.cardOrder) {
        if (key == "monitor") {
            if (state.showTrafficMonitor) {
                flushTiles()
                blocks += HomeBlock.Monitor
            }
        } else {
            tilesByKey[key]?.let { buffer += it }
        }
    }
    flushTiles()

    if (blocks.isEmpty() && state.subTotal <= 1L) return

    Column(
        modifier = Modifier.animateContentSize(tuxunSizeSpec(animStyle())),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Monitor hidden: the subscription card takes the monitor's
        // place, right above the tile rows.
        if (!state.showTrafficMonitor) {
            SubscriptionCard(state = state)
        }

        blocks.forEach { block ->
            when (block) {
                HomeBlock.Monitor -> {
                    TrafficMonitorCard(state = state)
                    SubscriptionCard(state = state)
                }
                is HomeBlock.TileRow -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        block.tiles.forEach { tile ->
                            InfoTile(
                                tile = tile,
                                onRequest = onRequest,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (block.tiles.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Traffic monitor: a live rate chart (last 60 one-second samples) with a
 * gradient fill, the forwarded totals and the connection duration.
 *
 * On first composition the lines sweep in from the left (the draw
 * progress animates 0 -> 1) so the chart draws itself head-to-tail.
 */
@Composable
private fun TrafficMonitorCard(state: MainUiState) {
    // This week / last week toggle; session-only, not persisted.
    var weekTab by remember { mutableStateOf(0) }
    val style = animStyle()
    val todayEnterSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = true)
    val todayExitSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = false)
    val weekEnterSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = true)
    val weekExitSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = false)

    TuxunCard(
        shape = RoundedCornerShape(TuxunCardCornerLarge),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_swap_vert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.traffic_monitor),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.clashRunning && state.connectedMillis > 0L) {
                    Text(
                        text = stringResource(
                            R.string.connected_for,
                            formatDuration(state.connectedMillis),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TrafficChart(
                samples = state.trafficSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "↓ ${state.traffic.trafficDownload()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "↑ ${state.traffic.trafficUpload()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // Today's forwarded total (app-open time only) plus the
            // previous days. When the proxy is stopped the chart is
            // empty, so say so instead of showing a dead card. The line
            // cross-fades as the totals tick up.
            val todayText = if (!state.clashRunning) {
                stringResource(R.string.traffic_not_running)
            } else {
                stringResource(
                    R.string.traffic_today,
                    state.todayUp.toBytesString(),
                    state.todayDown.toBytesString(),
                )
            }
            AnimatedContent(
                targetState = todayText,
                transitionSpec = {
                    fadeIn(todayEnterSpec) togetherWith fadeOut(todayExitSpec)
                },
                label = "todayLine",
            ) { text ->
                Text(
                    text = text,
                    style = if (state.clashRunning) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.traffic_daily_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Weekly view: this week (Monday..today) or last week
            // (Monday..Sunday), with a small bar per day. Only days
            // with stored data are shown; the file keeps 14 days, so
            // last week may be partial. Switching weeks cross-fades
            // the content and each bar grows with its own spring.
            Spacer(modifier = Modifier.height(4.dp))
            TuxunSegmentedRow(
                options = listOf(
                    stringResource(R.string.traffic_this_week),
                    stringResource(R.string.traffic_last_week),
                ),
                selectedIndex = weekTab,
                onSelect = { weekTab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedContent(
                targetState = weekTab,
                transitionSpec = {
                    fadeIn(weekEnterSpec) togetherWith fadeOut(weekExitSpec)
                },
                label = "weekTab",
            ) { tab ->
                val weekDays =
                    if (tab == 0) state.currentWeekDays else state.lastWeekDays
                val weekMax = weekDays.maxOfOrNull { it.up + it.down } ?: 0L

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (weekDays.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.traffic_week_total,
                                weekDays.sumOf { it.up }.toBytesString(),
                                weekDays.sumOf { it.down }.toBytesString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        weekDays.forEach { day ->
                            WeekDayRow(day = day, weekMax = weekMax)
                        }
                        if (tab == 1) {
                            Text(
                                text = stringResource(R.string.traffic_week_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.traffic_week_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One day of the weekly view; the bar grows to its share of the week max. */
@Composable
private fun WeekDayRow(day: DailyTrafficDay, weekMax: Long) {
    val total = day.up + day.down
    val targetFraction = if (weekMax > 0L) {
        (total.toFloat() / weekMax.toFloat()).coerceIn(0.06f, 1f)
    } else {
        0.06f
    }
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tuxunProgressSpec(animStyle()),
        label = "weekDayBar",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.date.substring(5),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "↑ ${day.up.toBytesString()}   ↓ ${day.down.toBytesString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun TrafficChart(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    var sweepTarget by remember { mutableStateOf(0f) }
    val sweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "chartSweep",
    )
    LaunchedEffect(Unit) {
        sweepTarget = 1f
    }

    Canvas(modifier = modifier) {
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx(),
        )

        if (samples.size < 2) return@Canvas

        val maxRate = samples.maxOf { maxOf(it.upRate, it.downRate) }
            .coerceAtLeast(1)
            .toFloat()
        val usableHeight = size.height - 6.dp.toPx()

        fun pointsOf(up: Boolean): List<Offset> = samples.mapIndexed { index, sample ->
            val rate = if (up) sample.upRate else sample.downRate
            Offset(
                x = size.width * index / (samples.size - 1),
                y = size.height - (rate / maxRate) * usableHeight,
            )
        }

        drawSeries(pointsOf(up = false), downColor, sweep)
        drawSeries(pointsOf(up = true), upColor, sweep)
    }
}

/** Draw one rate series, revealing only the first [sweep] fraction of it. */
private fun DrawScope.drawSeries(points: List<Offset>, color: Color, sweep: Float) {
    if (points.size < 2 || sweep <= 0f) return

    val segments = points.size - 1
    val cutoff = (sweep.coerceIn(0f, 1f) * segments)
    val fullSegments = cutoff.toInt()
    val fraction = cutoff - fullSegments

    val visible = mutableListOf<Offset>()
    visible += points[0]
    for (i in 1..fullSegments.coerceAtMost(segments)) {
        visible += points[i]
    }
    if (fraction > 0f && fullSegments < segments) {
        val from = points[fullSegments]
        val to = points[fullSegments + 1]
        visible += Offset(
            x = from.x + (to.x - from.x) * fraction,
            y = from.y + (to.y - from.y) * fraction,
        )
    }
    if (visible.size < 2) return

    val linePath = Path().apply {
        moveTo(visible.first().x, visible.first().y)
        for (i in 1 until visible.size) {
            lineTo(visible[i].x, visible[i].y)
        }
    }

    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(visible.last().x, size.height)
        lineTo(visible.first().x, size.height)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawPath(
        path = linePath,
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** Days until [expireMillis] (epoch millis), rounded up; -1 means already expired. */
private fun daysUntilExpiry(expireMillis: Long): Long {
    val diff = expireMillis - System.currentTimeMillis()
    if (diff <= 0L) return -1L
    return (diff + 86_400_000L - 1L) / 86_400_000L
}

/**
 * Active profile's subscription usage (from the subscription-userinfo the
 * profile manager already tracks): used / total with a progress bar and
 * the expiry date. Renders nothing when the profile carries no data.
 */
@Composable
private fun SubscriptionCard(state: MainUiState) {
    val style = animStyle()

    // The card fades + grows in when the profile gains subscription
    // data and shrinks away when it loses it, instead of popping.
    AnimatedVisibility(
        visible = state.subTotal > 1L,
        enter = tuxunEnterTransition(),
        exit = tuxunExitTransition(),
    ) {
        if (state.subTotal <= 1L) return@AnimatedVisibility

        val used = state.subUpload + state.subDownload

        TuxunCard(
            shape = RoundedCornerShape(TuxunCardCornerLarge),
            modifier = Modifier.fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_view_list),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.subscription),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.subExpire > 0L) {
                    Text(
                        text = stringResource(
                            R.string.subscription_expires,
                            state.subExpire.toDateStr(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${used.toBytesString()} / ${state.subTotal.toBytesString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Expiry reminder: only within 3 days of expiry (or already
            // expired). No expiry date on the profile: show nothing.
            // Display only — no notification, no subscription refresh.
            if (state.subExpire > 0L) {
                val daysLeft = daysUntilExpiry(state.subExpire)
                if (daysLeft < 0L) {
                    Text(
                        text = stringResource(R.string.subscription_expired),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (daysLeft <= 3L) {
                    Text(
                        text = stringResource(
                            R.string.subscription_expires_soon,
                            daysLeft.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            // Usage reminder: only when the subscription has a total
            // cap. 80%+: warn with remaining amount; 100%: used up.
            // Display only — no notification, no subscription refresh.
            // Shown alongside the expiry reminder above, not instead.
            val usageRatio = used.toFloat() / state.subTotal.toFloat()
            if (usageRatio >= 1f) {
                Text(
                    text = stringResource(R.string.subscription_usage_exhausted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (usageRatio >= 0.8f) {
                Text(
                    text = stringResource(
                        R.string.subscription_usage_warning,
                        (usageRatio * 100).toInt(),
                        (state.subTotal - used).toBytesString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val animatedUsageRatio by animateFloatAsState(
                targetValue = usageRatio.coerceIn(0f, 1f),
                animationSpec = tuxunProgressSpec(style),
                label = "subscriptionUsage",
            )
            LinearProgressIndicator(
                progress = animatedUsageRatio,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoTile(
    tile: HomeTile,
    onRequest: (MainDesign.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier.heightIn(min = 108.dp).tuxunAppear()

    if (tile.request != null) {
        TuxunCard(
            onClick = { onRequest(tile.request) },
            modifier = cardModifier,
        ) {
            TileContent(tile, onRequest)
        }
    } else {
        TuxunCard(modifier = cardModifier) {
            TileContent(tile, onRequest)
        }
    }
}

@Composable
private fun TileContent(
    tile: HomeTile,
    onRequest: (MainDesign.Request) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (tile.testing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (tile.testRequest != null) {
                IconButton(
                    onClick = { onRequest(tile.testRequest) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_flash_on),
                        contentDescription = stringResource(R.string.test_all),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(tile.title),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        tile.lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Logs / Settings / Help / About as one compact row of icon tiles. */
@Composable
private fun BottomNavRow(
    onRequest: (MainDesign.Request) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NavTile(
            icon = DesignR.drawable.ic_baseline_assignment,
            title = DesignR.string.logs,
            request = MainDesign.Request.OpenLogs,
            onRequest = onRequest,
            modifier = Modifier.weight(1f),
        )
        NavTile(
            icon = DesignR.drawable.ic_baseline_settings,
            title = DesignR.string.settings,
            request = MainDesign.Request.OpenSettings,
            onRequest = onRequest,
            modifier = Modifier.weight(1f),
        )
        NavTile(
            icon = DesignR.drawable.ic_baseline_help_center,
            title = DesignR.string.help,
            request = MainDesign.Request.OpenHelp,
            onRequest = onRequest,
            modifier = Modifier.weight(1f),
        )
        NavTile(
            icon = DesignR.drawable.ic_baseline_info,
            title = DesignR.string.about,
            request = MainDesign.Request.OpenAbout,
            onRequest = onRequest,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavTile(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    request: MainDesign.Request,
    onRequest: (MainDesign.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    TuxunCard(
        onClick = { onRequest(request) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun modeText(mode: TunnelState.Mode): String {
    return stringResource(
        when (mode) {
            TunnelState.Mode.Direct -> DesignR.string.direct_mode
            TunnelState.Mode.Global -> DesignR.string.global_mode
            TunnelState.Mode.Rule -> DesignR.string.rule_mode
            else -> DesignR.string.rule_mode
        },
    )
}

/**
 * Decode one half of the packed traffic value into raw bytes.
 *
 * Mirrors the private scaleTraffic() in core Traffic.kt: the packed value
 * stores bytes * 100, scaled by a power-of-1024 type tag in the top 2 bits.
 */
private fun decodeTrafficHalf(half: Long): Long {
    val type = (half ushr 30) and 0x3
    val data = half and 0x3FFFFFFF

    val scaled = when (type) {
        0L -> data
        1L -> data * 1024
        2L -> data * 1024 * 1024
        else -> data * 1024 * 1024 * 1024
    }

    return scaled / 100
}

internal fun trafficUploadBytes(traffic: Long): Long =
    decodeTrafficHalf(traffic ushr 32)

internal fun trafficDownloadBytes(traffic: Long): Long =
    decodeTrafficHalf(traffic and 0xFFFFFFFF)

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond >= 1024L * 1024 * 1024 ->
            String.format("%.2f GiB/s", bytesPerSecond / (1024.0 * 1024 * 1024))
        bytesPerSecond >= 1024L * 1024 ->
            String.format("%.2f MiB/s", bytesPerSecond / (1024.0 * 1024))
        bytesPerSecond >= 1024L ->
            String.format("%.1f KiB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }
}
