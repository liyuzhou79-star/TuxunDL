package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tuxundl.android.R
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.design.model.AnimationStyle
import com.tuxundl.android.core.util.trafficDownload
import com.tuxundl.android.core.util.trafficUpload
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.util.toBytesString
import com.tuxundl.android.design.util.toDateStr
import com.tuxundl.android.util.DailyTrafficDay
import com.tuxundl.android.util.DelaySample
import com.tuxundl.android.util.HourTrafficBucket
import com.tuxundl.android.util.SavedConnection
import com.tuxundl.android.util.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
     * first, capped at 120 samples (2 minutes) by MainActivity.
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

    /**
     * Home brick order. Defaults to the five core bricks; MainActivity
     * replaces this with the parsed stored order on every fetch. The
     * id set and default live in HomeBricks.kt (single definition).
     */
    var brickOrder by mutableStateOf(HOME_BRICK_DEFAULT_ORDER)

    /** True while the mascot easter-egg dialog is shown. */
    var showEasterEgg by mutableStateOf(false)

    /** True while the advanced traffic panel is shown. */
    var showTrafficAdvanced by mutableStateOf(false)

    /** Lifetime forwarded bytes (all days, app-open time only). */
    var lifetimeUp by mutableStateOf(0L)
    var lifetimeDown by mutableStateOf(0L)

    /** Lifetime connected seconds (app open and tunnel running). */
    var lifetimeConnectedSeconds by mutableStateOf(0L)

    /** Days since first launch, counting the first day as day 1. */
    var daysUsed by mutableStateOf(1)

    /** Hourly buckets covering the last 24 hours, oldest first (24 entries). */
    var hourlyBuckets by mutableStateOf<List<HourTrafficBucket>>(emptyList())

    /** Connected seconds recorded for today. */
    var todayConnectedSeconds by mutableStateOf(0L)

    /** Recent quality probes of the main group (memory only, last 40). */
    var delaySamples by mutableStateOf<List<DelaySample>>(emptyList())

    /** Zero-rate stalls (5+ seconds) seen this session. */
    var stallCount by mutableStateOf(0)

    /** This session's forwarded bytes since the tunnel started. */
    var sessionUpBytes by mutableStateOf(0L)
    var sessionDownBytes by mutableStateOf(0L)

    /** User-saved connection snapshots, newest first. */
    var savedConnections by mutableStateOf<List<SavedConnection>>(emptyList())

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
    // Page scroll is locked while a home item is being dragged, so the
    // column never fights the drag gesture for the pointer.
    val scrollState = rememberScrollState()
    var homeDragActive by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.application_name)) },
                actions = {
                    MascotButton(onTripleTap = { state.showEasterEgg = true })
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState, enabled = !homeDragActive)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateCard(state = state, onRequest = onRequest)

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerCircle(state = state, onRequest = onRequest)
            }

            ModeSelector(state = state, onRequest = onRequest)

            HomeCards(
                state = state,
                onRequest = onRequest,
                onDragActiveChange = { homeDragActive = it },
            )
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

    // Mascot easter egg (eight quick taps on the top-bar mascot).
    if (state.showEasterEgg) {
        EasterEggDialog(
            state = state,
            onClose = { state.showEasterEgg = false },
        )
    }

    // Advanced traffic panel (tap on the monitor card / traffic tile).
    if (state.showTrafficAdvanced) {
        TrafficAdvancedDialog(
            state = state,
            onRequest = onRequest,
            onClose = { state.showTrafficAdvanced = false },
        )
    }
}

/**
 * The mascot in the top bar. Tapping it eight times within three
 * seconds opens the easter egg. From the fourth tap on, the mascot
 * answers more strongly — a deeper bounce, wider antenna sway and a
 * brighter node-dot halo — so the user can feel the egg getting
 * closer; the eighth tap opens it.
 */
@Composable
private fun MascotButton(onTripleTap: () -> Unit) {
    val style = animStyle()
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    var taps by remember { mutableStateOf<List<Long>>(emptyList()) }
    val interaction = remember { MutableInteractionSource() }

    val tapCount = taps.size
    // 0 until tap 3, then climbs: tap 4 -> 0.3, 5 -> 0.5, 6 -> 0.7,
    // tap 7 -> 1 (the "almost there" full glow), tap 8 opens the egg.
    val excitement = when {
        tapCount >= 7 -> 1f
        tapCount >= 4 -> 0.3f + 0.2f * (tapCount - 4)
        else -> 0f
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                val now = SystemClock.elapsedRealtime()
                taps = (taps + now).filter { now - it <= 3000L }
                val count = taps.size

                if (style != AnimationStyle.Reduced) {
                    scope.launch {
                        // The dip deepens from the fourth tap on.
                        val dip = 0.82f -
                            0.03f * (count - 3).coerceIn(0, 4)
                        bounce.snapTo(dip)
                        bounce.animateTo(1f, tuxunSnapSpec<Float>(style))
                    }
                }

                if (count >= 8) {
                    taps = emptyList()
                    onTripleTap()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Mascot(
            size = 34.dp,
            modifier = Modifier.scale(bounce.value),
            excitement = excitement,
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

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

/** Blend a colour toward white by [fraction] (0 = itself, 1 = white). */
private fun Color.lightened(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

/** The four visual states of the power circle. */
private enum class PowerPhase {
    Stopped,
    Connecting,
    Connected,
    Stopping,
}

/**
 * The big round start/stop button at the center of the home screen.
 *
 * Four states, each with its own motion (the core exposes no
 * transitional tunnel state, so Connecting/Stopping are tracked at the
 * UI layer between the tap and the running flag flipping):
 *
 * - Stopped: hollow ring breathing slowly, grey hint text.
 * - Connecting: a fast rotating arc whose sweep breathes, the disc
 *   pulses gently, and the label walks "Connecting" -> "Handshaking";
 *   if the core never comes up within 4 s the button shakes once and
 *   falls back to Stopped (e.g. VPN permission denied).
 * - Connected: filled disc, live speed inside, slow rotating halo;
 *   when the total rate suddenly doubles its recent average the halo
 *   "heartbeats" (its stroke swells once). Right after connecting, the
 *   label briefly reads "Connected".
 * - Stopping: the rotating arc again, in grey.
 *
 * The Reduced animation style drops rotation, breathing, pulsing and
 * the shake; states then differ by colour and label only.
 */
@Composable
private fun PowerCircle(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val running = state.clashRunning
    val style = animStyle()
    val reduced = style == AnimationStyle.Reduced
    val speedEnterSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = true)
    val speedExitSpec: FiniteAnimationSpec<Float> = tuxunFadeSpec(style, entering = false)

    var phase by remember {
        mutableStateOf(if (running) PowerPhase.Connected else PowerPhase.Stopped)
    }
    var stageElapsed by remember { mutableStateOf(0L) }
    var connectedFlash by remember { mutableStateOf(false) }
    var shakeCount by remember { mutableStateOf(0) }

    // Follow the real running flag: it confirms or ends the transitional
    // phases the tap handler below starts.
    LaunchedEffect(running) {
        if (running) {
            if (phase == PowerPhase.Connecting) {
                connectedFlash = true
            }
            phase = PowerPhase.Connected
        } else if (phase == PowerPhase.Connected || phase == PowerPhase.Stopping) {
            phase = PowerPhase.Stopped
        }
    }

    // "Connected" flash right after the tunnel comes up.
    LaunchedEffect(connectedFlash) {
        if (connectedFlash) {
            delay(900)
            connectedFlash = false
        }
    }

    // While connecting: advance the stage label, and give up after 4 s
    // (the core never reported running) with a small shake.
    LaunchedEffect(phase) {
        if (phase == PowerPhase.Connecting) {
            val start = SystemClock.elapsedRealtime()
            stageElapsed = 0L
            while (phase == PowerPhase.Connecting) {
                stageElapsed = SystemClock.elapsedRealtime() - start
                if (stageElapsed > 4000L) {
                    shakeCount++
                    phase = PowerPhase.Stopped
                    break
                }
                delay(150)
            }
        }
    }

    var upSpeed by remember { mutableStateOf("0 B/s") }
    var downSpeed by remember { mutableStateOf("0 B/s") }

    // Halo stroke width; swells once per traffic "heartbeat".
    val ringWidth = remember { Animatable(6f) }
    val heartbeatScope = rememberCoroutineScope()
    val recentTotals = remember { mutableListOf<Long>() }
    var lastHeartbeatAt by remember { mutableStateOf(0L) }

    // Sample the packed totals once per second (MainActivity refreshes
    // them at the same cadence) and turn the delta into a live rate.
    LaunchedEffect(running) {
        if (!running) {
            upSpeed = "0 B/s"
            downSpeed = "0 B/s"
            recentTotals.clear()
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
                val upRate = ((up - prevUp).coerceAtLeast(0) / seconds).roundToLong()
                val downRate = ((down - prevDown).coerceAtLeast(0) / seconds).roundToLong()
                upSpeed = formatSpeed(upRate)
                downSpeed = formatSpeed(downRate)

                // Heartbeat: the total rate suddenly doubling its
                // recent average swells the halo once (3 s cooldown).
                val totalRate = upRate + downRate
                recentTotals.add(totalRate)
                if (recentTotals.size > 11) recentTotals.removeAt(0)
                if (!reduced && recentTotals.size >= 6) {
                    val previousAvg = recentTotals.dropLast(1).average()
                    if (previousAvg > 10240.0 &&
                        totalRate > 2.0 * previousAvg &&
                        now - lastHeartbeatAt > 3000L
                    ) {
                        lastHeartbeatAt = now
                        heartbeatScope.launch {
                            ringWidth.snapTo(6f)
                            ringWidth.animateTo(
                                targetValue = 11f,
                                animationSpec = tween(durationMillis = 120),
                            )
                            ringWidth.animateTo(
                                targetValue = 6f,
                                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                            )
                        }
                    }
                }
            }

            prevUp = up
            prevDown = down
            prevAt = now

            delay(1000)
        }
    }

    // Failure shake (offset keyframes), skipped in the Reduced style.
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeCount) {
        if (shakeCount > 0 && !reduced) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    0f at 0
                    -10f at 60
                    8f at 130
                    -5f at 200
                    0f at 320
                },
            )
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
    // Connecting/stopping: a quicker rotation and a breathing sweep.
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fastRotation",
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweepAngle",
    )
    // Stopped: the hollow ring breathes (alpha, slow 3.6 s cycle,
    // small amplitude — a calm sine-like swell, not a blink).
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )
    // Connecting: the disc itself pulses gently.
    val discPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "discPulse",
    )

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val track = MaterialTheme.colorScheme.outlineVariant
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    val connected = phase == PowerPhase.Connected
    val contentColor = when (phase) {
        PowerPhase.Connected -> onPrimary
        PowerPhase.Stopping -> grey
        else -> primary
    }

    // Smooth the state flips: the disc grows a touch and fills with
    // the theme colour instead of snapping. Connected is a vertical
    // gradient (light at the top, full primary at the bottom) over a
    // soft same-colour glow, so the disc feels lit rather than flat;
    // both gradient stops cross-fade with the shared colour spec, and
    // in the other phases both stops sit at surface colour (flat).
    val fillTop by animateColorAsState(
        targetValue = if (connected) {
            primary.lightened(0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tuxunColorSpec(style),
        label = "circleFillTop",
    )
    val fillBottom by animateColorAsState(
        targetValue = if (connected) {
            primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tuxunColorSpec(style),
        label = "circleFillBottom",
    )
    val glowColor by animateColorAsState(
        targetValue = if (connected) {
            primary.copy(alpha = 0.30f)
        } else {
            primary.copy(alpha = 0f)
        },
        animationSpec = tuxunColorSpec(style),
        label = "circleGlow",
    )
    val circleSizeSpec: AnimationSpec<Dp> = when (style) {
        AnimationStyle.Spring -> spring(dampingRatio = 0.8f, stiffness = 300f)
        AnimationStyle.Soft -> spring(dampingRatio = 0.92f, stiffness = 420f)
        AnimationStyle.Reduced -> tween(durationMillis = 120)
    }
    val circleSize by animateDpAsState(
        targetValue = if (connected) 204.dp else 196.dp,
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
        modifier = Modifier
            .size(224.dp)
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()

            when (phase) {
                PowerPhase.Stopped -> {
                    drawCircle(
                        color = track.copy(alpha = if (reduced) 0.55f else breathAlpha),
                        style = Stroke(width = strokeWidth),
                    )
                }
                PowerPhase.Connecting -> {
                    drawCircle(
                        color = track,
                        style = Stroke(width = strokeWidth),
                    )
                    drawArc(
                        color = primary,
                        startAngle = if (reduced) -90f else fastRotation,
                        sweepAngle = if (reduced) 270f else sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                PowerPhase.Connected -> {
                    drawCircle(
                        color = track,
                        style = Stroke(width = strokeWidth),
                    )
                    drawArc(
                        color = primary.copy(alpha = if (reduced) 1f else ringAlpha),
                        startAngle = if (reduced) -90f else ringRotation,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(
                            width = ringWidth.value.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
                PowerPhase.Stopping -> {
                    drawCircle(
                        color = track,
                        style = Stroke(width = strokeWidth),
                    )
                    drawArc(
                        color = grey,
                        startAngle = if (reduced) -90f else fastRotation,
                        sweepAngle = 120f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // Soft same-colour glow behind the disc (connected only; the
        // colour animates to fully transparent in the other phases).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .size(circleSize)
                .graphicsLayer {
                    if (phase == PowerPhase.Connecting && !reduced) {
                        scaleX = discPulse
                        scaleY = discPulse
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(fillTop, fillBottom)),
                )
                .then(
                    when (phase) {
                        PowerPhase.Connected -> Modifier
                        PowerPhase.Stopping -> Modifier.border(2.dp, grey, CircleShape)
                        else -> Modifier.border(2.dp, primary, CircleShape)
                    },
                )
                .tuxunPressScale(
                    powerInteraction,
                    pressedScale = 0.95f,
                    spec = tuxunPowerPressSpec(style),
                )
                .clickable(
                    interactionSource = powerInteraction,
                    indication = null,
                    enabled = phase == PowerPhase.Stopped || phase == PowerPhase.Connected,
                ) {
                    when (phase) {
                        PowerPhase.Stopped -> {
                            phase = PowerPhase.Connecting
                            onRequest(MainDesign.Request.ToggleStatus)
                        }
                        PowerPhase.Connected -> {
                            phase = PowerPhase.Stopping
                            onRequest(MainDesign.Request.ToggleStatus)
                        }
                        else -> Unit
                    }
                },
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
                    targetState = phase,
                    transitionSpec = {
                        fadeIn(speedEnterSpec) togetherWith
                            fadeOut(speedExitSpec)
                    },
                    label = "powerStatus",
                ) { currentPhase ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        when (currentPhase) {
                            PowerPhase.Stopped -> {
                                Text(
                                    text = stringResource(DesignR.string.stopped),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(DesignR.string.tap_to_start),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = animatedContentColor,
                                )
                            }
                            PowerPhase.Connecting -> {
                                val stageText = stringResource(
                                    if (stageElapsed < 1500L) {
                                        R.string.power_connecting
                                    } else {
                                        R.string.power_handshaking
                                    },
                                )
                                AnimatedContent(
                                    targetState = stageText,
                                    transitionSpec = {
                                        fadeIn(speedEnterSpec) togetherWith
                                            fadeOut(speedExitSpec)
                                    },
                                    label = "connectStage",
                                ) { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            PowerPhase.Connected -> {
                                Text(
                                    text = if (connectedFlash) {
                                        stringResource(R.string.power_connected)
                                    } else {
                                        stringResource(DesignR.string.running)
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = onPrimary,
                                )
                                // Live speed: soft cross-fade on each
                                // new sample instead of snapping digits.
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
                            }
                            PowerPhase.Stopping -> {
                                Text(
                                    text = stringResource(R.string.power_stopping),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = grey,
                                )
                            }
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

/**
 * Shared bookkeeping for home-screen drag reordering.
 *
 * All coordinates are kept relative to the blocks container, so
 * scrolling (which shifts every element and the container equally)
 * does not disturb hit testing or the slide offsets of swapped items.
 *
 * Drag model (reworked in 1.2.1 — the 1.2.0 version felt laggy and
 * jittery): at drag start every item's bounds are frozen into
 * [frozenBounds] and all hit testing runs against that snapshot, so
 * layout changes caused by swaps can never feed back into the drag.
 * The dragged item itself follows the finger's raw accumulated
 * travel ([dragTotal]) one-to-one through a plain graphicsLayer
 * translation — no spring, no Animatable on that path. When a swap
 * moves the dragged item's slot, [dragSlotTopLeft] tracks the new
 * slot so the rendered translation compensates and the item stays
 * glued to the finger. On release the item settles into its slot
 * with a short spring ([settling]).
 */
private class HomeDragState {
    /** Container origin in root coordinates. */
    var containerOrigin by mutableStateOf(Offset.Zero)

    /** Item key -> layout bounds relative to the container (live). */
    val bounds = mutableStateMapOf<String, Rect>()

    /** Bounds snapshot frozen at drag start; hit testing uses this. */
    var frozenBounds: Map<String, Rect> = emptyMap()

    var draggingKey by mutableStateOf<String?>(null)

    /** Accumulated raw finger travel since drag start. */
    var dragTotal by mutableStateOf(Offset.Zero)

    /** Frozen top-left of the slot the dragged item currently occupies. */
    var dragSlotTopLeft by mutableStateOf(Offset.Zero)

    /** Where inside the dragged item the pointer grabbed it. */
    var grabOffset by mutableStateOf(Offset.Zero)

    /** True while the dragged item settles into its final slot. */
    var settling by mutableStateOf(false)

    /** Brick that must not be targeted again until the pointer leaves it. */
    var blockedKey by mutableStateOf<String?>(null)

    /** The monitor<->bricks block swap fires at most once per drag. */
    var blockSwapped by mutableStateOf(false)

    /** Finger position in container space, from the frozen snapshot. */
    fun pointerFor(key: String): Offset? {
        val self = frozenBounds[key] ?: return null
        return self.topLeft + grabOffset + dragTotal
    }
}

/**
 * One draggable home item (a brick, or the monitor block).
 *
 * Normal mode: taps run [onClick], a long press enters edit mode
 * (combinedClickable keeps the two apart; interactive children — the
 * tile's test button, the monitor's week switch — still get their own
 * taps first). Edit mode: the item wiggles, an overlay swallows all
 * taps, and a long-press drag moves the item with the pointer; swaps
 * are decided by [onDragMove] from the shared [HomeDragState], and
 * items whose slot moved slide there with a placement spring (FLIP).
 *
 * The dragged item is the exception to FLIP: it renders at the raw
 * accumulated finger travel (see [HomeDragState]), and on release a
 * short settle spring parks it into its final slot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableHomeItem(
    key: String,
    drag: HomeDragState,
    editMode: Boolean,
    wiggleSeed: Int,
    onClick: (() -> Unit)?,
    onEnterEditMode: () -> Unit,
    onDragMove: (draggingKey: String) -> Unit,
    modifier: Modifier = Modifier,
    pressScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val style = animStyle()
    val isDragging = drag.draggingKey == key
    val bounds = drag.bounds[key]
    val dragScope = rememberCoroutineScope()

    // FLIP: when a swap moves this item's slot, start visually at the
    // old position and spring to the new one.
    val flipX = remember { Animatable(0f) }
    val flipY = remember { Animatable(0f) }
    var prevBounds by remember { mutableStateOf<Rect?>(null) }
    // Set when a settle finishes: the dragged item's bounds entry is
    // stale by then, and the jump to the real slot must not FLIP.
    var skipNextFlip by remember { mutableStateOf(false) }
    LaunchedEffect(bounds) {
        val prev = prevBounds
        if (bounds != null) {
            if (skipNextFlip) {
                skipNextFlip = false
            } else if (prev != null && prev.topLeft != bounds.topLeft &&
                drag.draggingKey != key
            ) {
                flipX.snapTo(prev.left - bounds.left)
                flipY.snapTo(prev.top - bounds.top)
                launch { flipX.animateTo(0f, tuxunPlacementSpec(style)) }
                launch { flipY.animateTo(0f, tuxunPlacementSpec(style)) }
            }
            prevBounds = bounds
        }
    }

    // Settle animation for the dragged item on release.
    val settleX = remember { Animatable(0f) }
    val settleY = remember { Animatable(0f) }

    fun settleDrag() {
        if (drag.draggingKey != key) return

        val frozenSelf = drag.frozenBounds[key]
        val start = if (frozenSelf != null) {
            drag.dragTotal - (drag.dragSlotTopLeft - frozenSelf.topLeft)
        } else {
            drag.dragTotal
        }

        drag.settling = true
        dragScope.launch {
            settleX.snapTo(start.x)
            settleY.snapTo(start.y)
            launch { settleX.animateTo(0f, tuxunSnapSpec<Float>(style)) }
            launch { settleY.animateTo(0f, tuxunSnapSpec<Float>(style)) }
        }.invokeOnCompletion {
            skipNextFlip = true
            drag.draggingKey = null
            drag.blockedKey = null
            drag.settling = false
            drag.frozenBounds = emptyMap()
        }
    }

    // Edit-mode wiggle; each item gets a slightly different period so
    // they do not move in lockstep. Reduced style: perfectly still.
    val wiggleTransition = rememberInfiniteTransition(label = "homeWiggle")
    val wiggle by wiggleTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 170 + (wiggleSeed % 3).coerceAtLeast(0) * 45,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wiggleValue",
    )

    val frozenSelf = drag.frozenBounds[key]
    val translation = when {
        // Settling: the short spring into the final slot.
        isDragging && drag.settling -> Offset(settleX.value, settleY.value)
        // Dragging: raw finger travel, compensated for slot swaps so
        // the item stays exactly under the finger (1:1, no spring).
        isDragging && frozenSelf != null ->
            drag.dragTotal - (drag.dragSlotTopLeft - frozenSelf.topLeft)
        else -> Offset(flipX.value, flipY.value)
    }

    val pressInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 2f else 0f)
            .onGloballyPositioned { coordinates ->
                // The dragged item's entry stays frozen for the whole
                // drag: its layout slot changes under it as swaps land,
                // and reporting those positions is what fed back into
                // the 1.2.0 drag as jitter.
                if (drag.draggingKey != key) {
                    drag.bounds[key] = Rect(
                        offset = coordinates.positionInRoot() - drag.containerOrigin,
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                    )
                }
            }
            .graphicsLayer {
                translationX = translation.x
                translationY = translation.y
                if (isDragging) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                    shadowElevation = 16.dp.toPx()
                }
                rotationZ = if (editMode && !isDragging &&
                    style != AnimationStyle.Reduced
                ) {
                    wiggle
                } else {
                    0f
                }
            }
            .then(
                if (pressScale) {
                    Modifier.tuxunPressScale(pressInteraction, pressedScale = 0.98f)
                } else {
                    Modifier
                },
            )
            .then(
                if (!editMode) {
                    Modifier.combinedClickable(
                        interactionSource = pressInteraction,
                        indication = null,
                        onClick = { onClick?.invoke() },
                        onLongClick = onEnterEditMode,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()

        if (editMode) {
            // Overlay: swallows taps (inner buttons stay inert in edit
            // mode) and carries the drag gesture.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .pointerInput(key, editMode) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                // One drag at a time (a settle also
                                // counts); freeze the grid snapshot.
                                if (drag.draggingKey == null) {
                                    val itemBounds = drag.bounds[key]
                                    if (itemBounds != null) {
                                        drag.frozenBounds = drag.bounds.toMap()
                                        drag.draggingKey = key
                                        drag.blockedKey = null
                                        drag.blockSwapped = false
                                        drag.settling = false
                                        drag.grabOffset = local
                                        drag.dragTotal = Offset.Zero
                                        drag.dragSlotTopLeft = itemBounds.topLeft
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (drag.draggingKey == key && !drag.settling) {
                                    drag.dragTotal += dragAmount
                                    onDragMove(key)
                                }
                                change.consume()
                            },
                            onDragEnd = { settleDrag() },
                            onDragCancel = { settleDrag() },
                        )
                    },
            )
        }
    }
}

/**
 * The reorderable home area under the mode selector.
 *
 * Layout: two blocks — the traffic monitor block (monitor card +
 * subscription card) and the bricks grid — whose relative order comes
 * from MainUiState.cardOrder (the monitor key versus the tile keys).
 * The bricks grid holds every brick in MainUiState.brickOrder: the
 * info tiles (node, traffic, proxy, profile, providers) and the four
 * shortcut bricks (logs, settings, help, about), two per row.
 *
 * Long-pressing any brick or the monitor block enters edit mode;
 * dragging swaps bricks within the grid (persisted as brickOrder) or
 * flips the two blocks (persisted as cardOrder). The Appearance page
 * keeps editing the same cardOrder; tile-key order there is honoured
 * until the first brick drag writes brickOrder.
 */
@Composable
private fun HomeCards(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
) {
    val style = animStyle()

    val tilesByKey = mutableMapOf<String, HomeTile>()

    if (state.showTraffic) {
        tilesByKey["traffic"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_swap_vert,
            title = R.string.traffic,
            lines = listOf(
                "↑ ${state.traffic.trafficUpload()}",
                "↓ ${state.traffic.trafficDownload()}",
            ),
            request = MainDesign.Request.OpenTrafficAdvanced,
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
    // The core reports a failed/untested node as delay 65535 — show the
    // timeout label instead of the raw sentinel number.
    val nodeName = state.currentNodeName
    val nodeTile = if (nodeName != null) {
        HomeTile(
            icon = DesignR.drawable.ic_baseline_vpn_lock,
            title = R.string.current_node,
            lines = listOf(
                nodeName,
                when {
                    state.currentNodeDelay in 1..65534 -> "${state.currentNodeDelay} ms"
                    state.currentNodeDelay >= 65535 ->
                        stringResource(R.string.proxy_timeout)
                    else -> "-"
                },
            ),
            request = MainDesign.Request.OpenProxy,
            testRequest = MainDesign.Request.TestCurrentGroup,
            testing = state.testingNode,
        )
    } else {
        null
    }

    var editMode by remember { mutableStateOf(false) }
    val drag = remember { HomeDragState() }

    // While a drag runs (including the settle), the page scroll locks.
    LaunchedEffect(drag.draggingKey) {
        onDragActiveChange(drag.draggingKey != null)
    }

    fun brickVisible(id: String): Boolean = when (id) {
        "node" -> nodeTile != null
        "traffic", "proxy", "profile", "providers" -> tilesByKey.containsKey(id)
        else -> true // logs / settings / help / about always show
    }

    val visibleBricks = state.brickOrder.filter(::brickVisible)

    // Block order: the monitor block sits where cardOrder puts the
    // "monitor" key relative to the first tile key.
    val firstTileIndex = state.cardOrder.indexOfFirst { it != "monitor" }
    val monitorIndex = state.cardOrder.indexOf("monitor")
    val monitorFirst = firstTileIndex == -1 || monitorIndex < firstTileIndex

    fun swapBricks(a: String, b: String) {
        val order = state.brickOrder.toMutableList()
        val ia = order.indexOf(a)
        val ib = order.indexOf(b)
        if (ia >= 0 && ib >= 0 && ia != ib) {
            order[ia] = b
            order[ib] = a
            onRequest(MainDesign.Request.SetBrickOrder(order.joinToString(",")))
        }
    }

    fun swapBlocks() {
        val order = state.cardOrder.toMutableList()
        order.remove("monitor")
        if (monitorFirst) {
            order.add("monitor")
        } else {
            order.add(0, "monitor")
        }
        onRequest(MainDesign.Request.SetCardOrder(order.joinToString(",")))
    }

    // Hit testing for the active drag. Everything reads the snapshot
    // frozen at drag start (frozenBounds) and the pointer is derived
    // from the raw accumulated finger travel — the layout moves under
    // the finger as swaps land, and reading live bounds back into the
    // drag was what made the 1.2.0 version jitter and lag.
    fun onDragMove(draggingKey: String) {
        val frozen = drag.frozenBounds
        val pointer = drag.pointerFor(draggingKey) ?: return

        // Hysteresis: a brick we just swapped with stays blocked until
        // the pointer leaves its (frozen) bounds.
        val blocked = drag.blockedKey
        if (blocked != null) {
            val blockedBounds = frozen[blocked]
            if (blockedBounds == null || !blockedBounds.contains(pointer)) {
                drag.blockedKey = null
            }
        }

        if (draggingKey == "monitor") {
            if (!drag.blockSwapped) {
                val bricksBounds = frozen["bricks"]
                if (bricksBounds != null && bricksBounds.contains(pointer)) {
                    val crossed = if (monitorFirst) {
                        pointer.y > bricksBounds.center.y
                    } else {
                        pointer.y < bricksBounds.center.y
                    }
                    if (crossed) {
                        drag.blockSwapped = true
                        swapBlocks()
                    }
                }
            }
            return
        }

        // Brick drag: swap with the brick whose centre region (middle
        // 50% of the frozen bounds) the pointer has entered. The swap
        // also moves the dragged item's recorded slot, so its rendered
        // position (raw travel compensated by the slot delta) stays
        // glued to the finger instead of jumping to the new slot.
        for (id in visibleBricks) {
            if (id == draggingKey || id == drag.blockedKey) continue
            val b = frozen[id] ?: continue
            val inner = Rect(
                left = b.left + b.width * 0.25f,
                top = b.top + b.height * 0.25f,
                right = b.right - b.width * 0.25f,
                bottom = b.bottom - b.height * 0.25f,
            )
            if (inner.contains(pointer)) {
                swapBricks(draggingKey, id)
                drag.blockedKey = id
                drag.dragSlotTopLeft = b.topLeft
                break
            }
        }
    }

    fun brickClick(id: String): (() -> Unit)? {
        val request = when (id) {
            "node" -> MainDesign.Request.OpenProxy
            "traffic" -> MainDesign.Request.OpenTrafficAdvanced
            "proxy" -> MainDesign.Request.OpenProxy
            "profile" -> MainDesign.Request.OpenProfiles
            "providers" -> MainDesign.Request.OpenProviders
            "logs" -> MainDesign.Request.OpenLogs
            "settings" -> MainDesign.Request.OpenSettings
            "help" -> MainDesign.Request.OpenHelp
            "about" -> MainDesign.Request.OpenAbout
            else -> null
        }
        return request?.let { req -> { onRequest(req) } }
    }

    Column(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                drag.containerOrigin = coordinates.positionInRoot()
            }
            .animateContentSize(tuxunSizeSpec(style)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editMode = false }) {
                    Text(text = stringResource(R.string.home_edit_done))
                }
            }
        }

        // Monitor hidden: the subscription card takes the monitor's
        // place, right above the blocks (not draggable in that spot).
        if (!state.showTrafficMonitor) {
            SubscriptionCard(state = state)
        }

        val blocks = if (monitorFirst) {
            listOf("monitor", "bricks")
        } else {
            listOf("bricks", "monitor")
        }

        blocks.forEach { block ->
            key(block) {
                when (block) {
                    "monitor" -> if (state.showTrafficMonitor) {
                        DraggableHomeItem(
                            key = "monitor",
                            drag = drag,
                            editMode = editMode,
                            wiggleSeed = 0,
                            onClick = {
                                onRequest(MainDesign.Request.OpenTrafficAdvanced)
                            },
                            onEnterEditMode = { editMode = true },
                            onDragMove = ::onDragMove,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TrafficMonitorCard(
                                    state = state,
                                    onRequest = onRequest,
                                )
                                SubscriptionCard(state = state)
                            }
                        }
                    }

                    else -> {
                        // The bricks grid (registers its own bounds so
                        // the monitor block can hit-test against it).
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    drag.bounds["bricks"] = Rect(
                                        offset = coordinates.positionInRoot() -
                                            drag.containerOrigin,
                                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                                    )
                                },
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            visibleBricks.chunked(2).forEach { rowBricks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    rowBricks.forEach { id ->
                                        key(id) {
                                            DraggableHomeItem(
                                                key = id,
                                                drag = drag,
                                                editMode = editMode,
                                                wiggleSeed = id.hashCode(),
                                                onClick = brickClick(id),
                                                onEnterEditMode = { editMode = true },
                                                onDragMove = ::onDragMove,
                                                modifier = Modifier.weight(1f),
                                                pressScale = true,
                                            ) {
                                                BrickContent(
                                                    id = id,
                                                    nodeTile = nodeTile,
                                                    tilesByKey = tilesByKey,
                                                    onRequest = onRequest,
                                                )
                                            }
                                        }
                                    }
                                    if (rowBricks.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Content of one brick in the home bricks grid. */
@Composable
private fun BrickContent(
    id: String,
    nodeTile: HomeTile?,
    tilesByKey: Map<String, HomeTile>,
    onRequest: (MainDesign.Request) -> Unit,
) {
    when (id) {
        "node" -> nodeTile?.let { InfoTile(tile = it, onRequest = onRequest) }
        "traffic", "proxy", "profile", "providers" ->
            tilesByKey[id]?.let { InfoTile(tile = it, onRequest = onRequest) }
        "logs" -> NavBrick(
            icon = DesignR.drawable.ic_baseline_assignment,
            title = DesignR.string.logs,
        )
        "settings" -> NavBrick(
            icon = DesignR.drawable.ic_baseline_settings,
            title = DesignR.string.settings,
        )
        "help" -> NavBrick(
            icon = DesignR.drawable.ic_baseline_help_center,
            title = DesignR.string.help,
        )
        "about" -> NavBrick(
            icon = DesignR.drawable.ic_baseline_info,
            title = DesignR.string.about,
        )
    }
}

/**
 * Traffic monitor: a live rate chart (last 120 one-second samples,
 * i.e. 2 minutes) with smooth curves and a gradient fill under the
 * download line, the forwarded totals and the connection duration.
 *
 * On first composition the lines sweep in from the left (the draw
 * progress animates 0 -> 1) so the chart draws itself head-to-tail.
 * The whole card opens the advanced traffic panel (handled by the
 * DraggableHomeItem wrapper's click); the button at the bottom is the
 * explicit entry to the same panel.
 */
@Composable
private fun TrafficMonitorCard(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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

            // Window caption (2 minutes of samples) and the peak rates
            // inside the window, so the chart's scale is readable.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.traffic_last_2_min),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val peakDown = state.trafficSamples.maxOfOrNull { it.downRate } ?: 0L
                val peakUp = state.trafficSamples.maxOfOrNull { it.upRate } ?: 0L
                Text(
                    text = stringResource(
                        R.string.traffic_peak,
                        formatSpeed(peakDown),
                        formatSpeed(peakUp),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            // (The "app-open time only" note lives in the advanced
            // panel now; the home card stays clean.)

            // Weekly view: this week (Monday..today) or last week
            // (Monday..Sunday), with a small bar per day. Only days
            // with stored data are shown; the file keeps 14 days, so
            // last week may be partial. Switching weeks cross-fades
            // the content and each bar grows with its own spring.
            Spacer(modifier = Modifier.height(2.dp))
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (weekDays.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.traffic_week_total,
                                weekDays.sumOf { it.up }.toBytesString(),
                                weekDays.sumOf { it.down }.toBytesString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
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

            // Explicit entry to the advanced traffic panel (the whole
            // card opens it too, via the wrapper's click).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onRequest(MainDesign.Request.OpenTrafficAdvanced) },
                ) {
                    Text(text = stringResource(R.string.traffic_advanced))
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "↑ ${day.up.toBytesString()}   ↓ ${day.down.toBytesString()}",
                style = MaterialTheme.typography.bodySmall,
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
        animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
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

        // Download carries the gradient fill; upload is a plain line.
        drawSeries(pointsOf(up = false), downColor, sweep, filled = true)
        drawSeries(pointsOf(up = true), upColor, sweep, filled = false)
    }
}

/** Draw one rate series, revealing only the first [sweep] fraction of it. */
private fun DrawScope.drawSeries(
    points: List<Offset>,
    color: Color,
    sweep: Float,
    filled: Boolean,
) {
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

    val linePath = smoothPath(visible)

    if (filled) {
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
    }
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

/**
 * A smooth curve through [points]: Catmull-Rom spline converted to
 * cubic Bezier segments, so the rate chart flows instead of zig-zagging
 * between one-second samples.
 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        val c1 = Offset(
            x = p1.x + (p2.x - p0.x) / 6f,
            y = p1.y + (p2.y - p0.y) / 6f,
        )
        val c2 = Offset(
            x = p2.x - (p3.x - p1.x) / 6f,
            y = p2.y - (p3.y - p1.y) / 6f,
        )
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }

    return path
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
private fun InfoTile(
    tile: HomeTile,
    onRequest: (MainDesign.Request) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The card itself is not clickable: taps and long presses are
    // handled by the DraggableHomeItem wrapper, so the delay-test
    // button rides as a sibling on top of the card instead of living
    // inside the clickable surface.
    Box(
        modifier = modifier
            .heightIn(min = 96.dp)
            .tuxunAppear(),
    ) {
        TuxunCard(modifier = Modifier.fillMaxWidth()) {
            TileContent(tile)
        }

        if (tile.testing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(20.dp),
            )
        } else if (tile.testRequest != null) {
            IconButton(
                onClick = { onRequest(tile.testRequest) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
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
}

@Composable
private fun TileContent(tile: HomeTile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
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

/** One shortcut brick (Logs / Settings / Help / About) in the bricks grid. */
@Composable
private fun NavBrick(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    TuxunCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
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
