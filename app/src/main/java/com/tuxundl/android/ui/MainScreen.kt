package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.util.DailyTrafficDay
import com.tuxundl.android.util.DelaySample
import com.tuxundl.android.util.ProxyScheduler
import com.tuxundl.android.util.Haptics
import com.tuxundl.android.util.HourTrafficBucket
import com.tuxundl.android.util.SavedConnection
import com.tuxundl.android.util.UpdateInfo
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
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

    /**
     * Latest per-second rates (bytes/s) from TrafficSampler — the
     * same recorded points that feed both charts, mirrored by
     * MainActivity. The power circle reads these; it never derives
     * its own rates from the packed totals (1.5.3).
     */
    var upRate by mutableStateOf(0L)
    var downRate by mutableStateOf(0L)

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

    /** True once the 1.3.0 onboarding flow is done (or grandfathered). */
    var onboardingCompleted by mutableStateOf(false)

    /**
     * Home entrance counter: MainActivity bumps it on every onResume,
     * and the home sections replay their staggered entrance whenever
     * it changes — so returning from any other page animates the home
     * screen back in. The first composition plays it too.
     */
    var entranceTick by mutableStateOf(0)

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

    /** Rolling last 7 days ending today with data, oldest first. */
    var recentDays by mutableStateOf<List<DailyTrafficDay>>(emptyList())

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

    /** True while the fullscreen traffic chart (1.4.0) is shown. */
    var showTrafficChart by mutableStateOf(false)

    /**
     * Selected chart window (index into TRAFFIC_WINDOW_MS: 2 min …
     * 2 hours), shared by the home monitor card and the fullscreen
     * chart. Session-only, not persisted.
     */
    var trafficWindowIndex by mutableStateOf(0)

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

    /**
     * Tiered traffic history for the fullscreen chart (1.4.0):
     * 1-second points for the last 10 minutes, 10-second buckets out
     * to 2 hours, oldest first. Empty while the tunnel is stopped.
     */
    var historyPoints by mutableStateOf<List<com.tuxundl.android.util.TrafficPoint>>(emptyList())

    /** Delay probes kept for the whole 2-hour window (15 s apart, last 480). */
    var delayHistory by mutableStateOf<List<DelaySample>>(emptyList())

    /** Instants this session where the core's counters reset (a drop / reconnect). */
    var disconnectTimes by mutableStateOf<List<Long>>(emptyList())

    /** Onboarding import outcome: 0 none, 1 added, 2 failed, 3 cancelled (1.4.0). */
    var onboardingImportOutcome by mutableStateOf(0)

    /** Bumped whenever an onboarding import finishes, so the step reacts once. */
    var onboardingImportNonce by mutableStateOf(0)

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

    // Drag pickup / drop buzzes go through the 1.2.5 haptics helper,
    // which honours the master switch and strength setting.
    val homeContext = LocalContext.current
    val homeUiStore = remember { UiStore(homeContext) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .homeEntrance(0, state.entranceTick),
                contentAlignment = Alignment.Center,
            ) {
                PowerCircle(state = state, onRequest = onRequest)
            }

            Box(modifier = Modifier.homeEntrance(1, state.entranceTick)) {
                ModeSelector(state = state, onRequest = onRequest)
            }

            Box(modifier = Modifier.homeEntrance(2, state.entranceTick)) {
                SchedulePanel()
            }

            HomeCards(
                state = state,
                onRequest = onRequest,
                onDragActiveChange = { homeDragActive = it },
                entranceTick = state.entranceTick,
                onDragPickup = {
                    Haptics.buzz(homeContext, homeUiStore, Haptics.Kind.Tap)
                },
                onDragDrop = {
                    Haptics.buzz(homeContext, homeUiStore, Haptics.Kind.Tap)
                },
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

    // Fullscreen traffic chart + connection analysis (1.4.0).
    if (state.showTrafficChart) {
        TrafficChartDialog(
            state = state,
            onClose = { state.showTrafficChart = false },
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

/**
 * Staggered entrance for one home section: it fades in while rising
 * a few dp, [index] steps behind the previous section. Replays
 * whenever [tick] changes — MainActivity bumps the tick on every
 * onResume, so coming back from any other page animates the home
 * screen in again — while plain recompositions never retrigger it.
 * The Reduced style keeps only the fade.
 */
@Composable
private fun Modifier.homeEntrance(index: Int, tick: Int): Modifier = composed {
    val style = LocalAnimStyle.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(tick) {
        progress.snapTo(0f)
        if (style != AnimationStyle.Reduced) {
            delay((index * 55).coerceAtMost(330).toLong())
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = when (style) {
                AnimationStyle.Spring ->
                    spring(dampingRatio = 0.72f, stiffness = 300f)
                AnimationStyle.Soft ->
                    tween(durationMillis = 240, easing = FastOutSlowInEasing)
                AnimationStyle.Reduced -> tween(durationMillis = 130)
            },
        )
    }

    val drift = if (style == AnimationStyle.Reduced) 0f else 18f

    this.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * drift.dp.toPx()
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
 * Look (1.3.0): Material You wavy ring + frosted glass + floating.
 * A scalloped ring in the theme colour wraps the disc — semi-
 * transparent and crystal-like, drawn as a soft wide pass under a
 * crisper stroke — and the disc itself is a pale frosted-glass dome
 * (radial gradient, a light bloom at the top-left, a soft inner
 * shadow at the bottom) hovering over the page on a large blurred
 * shadow. A small capsule under the status text mirrors the state.
 *
 * Four states, each with its own motion (the core exposes no
 * transitional tunnel state, so Connecting/Stopping are tracked at the
 * UI layer between the tap and the running flag flipping):
 *
 * - Stopped: the wavy ring turns grey and still, the float stops,
 *   grey hint text.
 * - Connecting: the ring spins fast, the disc pulses gently, and the
 *   label walks "Connecting" -> "Handshaking"; if the core never
 *   comes up within 4 s the button shakes once and falls back to
 *   Stopped (e.g. VPN permission denied).
 * - Connected: the ring rotates slowly and breathes, the whole
 *   button floats (a barely-there vertical drift), live speed inside;
 *   when the total rate suddenly doubles its recent average the ring
 *   "heartbeats" (its stroke swells once). Right after connecting,
 *   the label briefly reads "Connected".
 * - Stopping: the ring spins fast again, in grey.
 *
 * Long-pressing the disc (1.3.1) opens a quick menu — switch mode,
 * test the current group, or jump to the node page; a long press
 * never toggles the tunnel.
 *
 * The Reduced animation style drops rotation, breathing, floating,
 * pulsing and the shake; states then differ by colour and label only.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    // Long-press quick menu (1.3.1): switch mode, test the current
    // group or jump to the node page without leaving home. A long
    // press only opens the menu — it never toggles the tunnel.
    var menuOpen by remember { mutableStateOf(false) }
    val powerContext = LocalContext.current
    val powerUiStore = remember { UiStore(powerContext) }

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

    // Live rates come from TrafficSampler (mirrored into the state
    // by MainActivity): the same recorded points that feed the
    // charts. This loop only formats them and drives the heartbeat —
    // it never derives rates from the packed totals itself, so the
    // numbers under the circle cannot drift away from the charts
    // (until 1.5.2 a second, UI-side derivation did exactly that and
    // sat on 0 B/s whenever the activity ticker stalled).
    LaunchedEffect(running) {
        if (!running) {
            upSpeed = "0 B/s"
            downSpeed = "0 B/s"
            recentTotals.clear()
            return@LaunchedEffect
        }

        while (true) {
            val upRate = state.upRate
            val downRate = state.downRate
            val now = SystemClock.elapsedRealtime()
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
    // Connecting/stopping: a quicker rotation.
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fastRotation",
    )
    // Floating drift (connected only): a slow, barely-there bob.
    val floatShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatShift",
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
            primary.lightened(0.38f)
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
    // Floating shadow: deep and soft while connected (the disc
    // hovers), shallow while parked.
    val discShadow by animateDpAsState(
        targetValue = if (connected) 26.dp else 9.dp,
        animationSpec = circleSizeSpec,
        label = "discShadow",
    )
    // Status capsule colours (dot + wash) follow the phase.
    val chipBackground by animateColorAsState(
        targetValue = when (phase) {
            PowerPhase.Connected -> onPrimary.copy(alpha = 0.22f)
            PowerPhase.Stopping -> grey.copy(alpha = 0.14f)
            PowerPhase.Connecting -> primary.copy(alpha = 0.12f)
            PowerPhase.Stopped -> primary.copy(alpha = 0.10f)
        },
        animationSpec = tuxunColorSpec(style),
        label = "chipBackground",
    )
    val chipDot by animateColorAsState(
        targetValue = when (phase) {
            PowerPhase.Connected -> Color(0xFF8CE99A)
            PowerPhase.Connecting -> primary
            else -> grey
        },
        animationSpec = tuxunColorSpec(style),
        label = "chipDot",
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
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .graphicsLayer {
                // Floating: while connected the whole button hovers
                // with a slow vertical drift; parked in every other
                // phase (and always in the Reduced style).
                translationY = if (connected && !reduced) {
                    -floatShift.dp.toPx()
                } else {
                    0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The Material You wavy ring. Colour, spin and breath all
            // follow the phase; the heartbeat Animatable (ringWidth)
            // swells the band. Stopped is grey and perfectly still.
            val waveColor: Color
            val waveAlpha: Float
            val waveRotation: Float
            when (phase) {
                PowerPhase.Stopped -> {
                    waveColor = grey
                    waveAlpha = if (reduced) 0.45f else breathAlpha * 0.75f
                    waveRotation = 0f
                }
                PowerPhase.Connecting -> {
                    waveColor = primary
                    waveAlpha = 0.9f
                    waveRotation = if (reduced) 0f else fastRotation
                }
                PowerPhase.Connected -> {
                    waveColor = primary
                    waveAlpha = if (reduced) 0.9f else ringAlpha
                    waveRotation = if (reduced) 0f else ringRotation
                }
                PowerPhase.Stopping -> {
                    waveColor = grey
                    waveAlpha = 0.75f
                    waveRotation = if (reduced) 0f else fastRotation
                }
            }

            drawWavyRing(
                color = waveColor,
                alpha = waveAlpha,
                rotationDegrees = waveRotation,
                bandWidth = (ringWidth.value + 2f).dp.toPx(),
                trackColor = track,
            )
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
                .shadow(
                    elevation = discShadow,
                    shape = CircleShape,
                    ambientColor = primary.copy(alpha = 0.30f),
                    spotColor = primary.copy(alpha = 0.38f),
                )
                .clip(CircleShape)
                .background(
                    // Frosted-glass dome: a radial wash, brightest
                    // toward the top, settling into the full colour.
                    Brush.radialGradient(listOf(fillTop, fillBottom)),
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
                .combinedClickable(
                    interactionSource = powerInteraction,
                    indication = null,
                    enabled = phase == PowerPhase.Stopped || phase == PowerPhase.Connected,
                    onClick = {
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
                    onLongClick = {
                        // Quick menu only; the tap buzz goes through
                        // the 1.2.5 haptics helper, like drag pickup.
                        menuOpen = true
                        Haptics.buzz(powerContext, powerUiStore, Haptics.Kind.Tap)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Frosted-glass light play over the disc fill (clipped to
            // the circle by the parent's clip): a soft bloom toward
            // the top-left and a gentle inner shadow at the bottom,
            // so the dome reads as glass rather than flat paint.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (connected) 0.32f else 0.45f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.30f, size.height * 0.20f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.30f, size.height * 0.20f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 1.08f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, size.height * 1.08f),
                )
            }

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

                // Status capsule: a small pill with a state dot,
                // mirroring the phase under the status text.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(chipBackground)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(chipDot),
                    )
                    Text(
                        text = stringResource(
                            when (phase) {
                                PowerPhase.Connected -> R.string.power_chip_active
                                PowerPhase.Connecting -> R.string.power_chip_connecting
                                PowerPhase.Stopping -> R.string.power_chip_stopping
                                PowerPhase.Stopped -> R.string.power_chip_idle
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedContentColor,
                    )
                }
            }

        }

        // Long-press quick menu (1.3.1). Every action reuses an
        // existing home request, so behaviour matches the mode
        // selector row and the node tile exactly.
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            listOf(
                TunnelState.Mode.Rule to DesignR.string.rule_mode,
                TunnelState.Mode.Global to DesignR.string.global_mode,
                TunnelState.Mode.Direct to DesignR.string.direct_mode,
            ).forEach { (mode, labelRes) ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(labelRes)) },
                    onClick = {
                        menuOpen = false
                        onRequest(MainDesign.Request.SetMode(mode))
                    },
                    trailingIcon = {
                        if (state.mode == mode) {
                            Icon(
                                painter = painterResource(
                                    DesignR.drawable.ic_outline_check_circle,
                                ),
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.power_menu_test_group)) },
                onClick = {
                    menuOpen = false
                    onRequest(MainDesign.Request.TestCurrentGroup)
                },
                enabled = running,
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.power_menu_update_all)) },
                onClick = {
                    menuOpen = false
                    onRequest(MainDesign.Request.UpdateAllProfiles)
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.power_menu_open_nodes)) },
                onClick = {
                    menuOpen = false
                    onRequest(MainDesign.Request.OpenProxy)
                },
            )
        }
    }
}

/**
 * Draw the Material You wavy ring: a full faint track, then the
 * scalloped band itself as two strokes — a wide, very soft pass that
 * reads as crystal glow, and a crisper pass on top. The scallops are
 * a sinusoidal radius modulation; [rotationDegrees] phase-shifts the
 * pattern so the whole ring appears to rotate.
 */
private fun DrawScope.drawWavyRing(
    color: Color,
    alpha: Float,
    rotationDegrees: Float,
    bandWidth: Float,
    trackColor: Color,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = size.minDimension / 2f - 7.dp.toPx()
    val amplitude = 4.5.dp.toPx()
    val waves = 14
    val phaseRad = Math.toRadians(rotationDegrees.toDouble())

    drawCircle(
        color = trackColor.copy(alpha = 0.5f),
        radius = baseRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )

    val path = Path()
    val steps = 180
    for (i in 0..steps) {
        val theta = i.toDouble() / steps * 2.0 * PI
        val radius = baseRadius +
            amplitude * sin(waves * (theta - phaseRad)).toFloat()
        val x = center.x + radius * cos(theta).toFloat()
        val y = center.y + radius * sin(theta).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    // Crystal glow pass, then the crisper band.
    drawPath(
        path = path,
        color = color.copy(alpha = alpha * 0.22f),
        style = Stroke(width = bandWidth * 2.1f, cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(width = bandWidth, cap = StrokeCap.Round),
    )
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

/** Which time field the schedule panel's shared time picker edits. */
private enum class ScheduleTimeField {
    TimerOn, TimerOff, NightStart, NightEnd,
}

private fun formatMinutesOfDay(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

/**
 * Expandable schedule panel right under the mode selector (1.5.4):
 * a slim chevron bar that springs open into the timed on/off rule
 * and the night auto-switch rule. All values live in [UiStore];
 * every change is written through immediately and re-arms
 * [ProxyScheduler], so what the panel shows is what will fire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulePanel() {
    val context = LocalContext.current
    val store = remember { UiStore(context) }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var timerEnabled by remember { mutableStateOf(store.scheduleEnabled) }
    var onMinutes by remember { mutableStateOf(store.scheduleOnMinutes) }
    var offMinutes by remember { mutableStateOf(store.scheduleOffMinutes) }
    var nightEnabled by remember { mutableStateOf(store.nightEnabled) }
    var nightStart by remember { mutableStateOf(store.nightStartMinutes) }
    var nightEnd by remember { mutableStateOf(store.nightEndMinutes) }
    var nightAction by remember { mutableStateOf(store.nightAction) }
    var exactAllowed by remember {
        mutableStateOf(ProxyScheduler.canScheduleExact(context))
    }
    var pickerField by remember { mutableStateOf<ScheduleTimeField?>(null) }

    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { exactAllowed = ProxyScheduler.canScheduleExact(context) }

    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "scheduleChevron",
    )

    val nightActionText = stringResource(
        if (nightAction == ProxyScheduler.NIGHT_ACTION_STOP) {
            R.string.schedule_night_action_stop
        } else {
            R.string.schedule_night_action_global
        },
    )
    val timerSummary = stringResource(
        R.string.schedule_summary_timer,
        formatMinutesOfDay(onMinutes),
        formatMinutesOfDay(offMinutes),
    )
    val nightSummary = stringResource(
        R.string.schedule_summary_night,
        formatMinutesOfDay(nightStart),
        formatMinutesOfDay(nightEnd),
        nightActionText,
    )
    val summary = listOfNotNull(
        if (timerEnabled) timerSummary else null,
        if (nightEnabled) nightSummary else null,
    ).joinToString(" · ")
        .ifEmpty { stringResource(R.string.schedule_summary_off) }

    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        // The slim bar: schedule icon, title, one-line summary of
        // what is armed, and the chevron that opens the panel.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TuxunCardCorner))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_schedule),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.schedule_panel_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Icon(
                painter = painterResource(DesignR.drawable.ic_baseline_expand_more),
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = chevronAngle },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            ) + fadeIn(tween(durationMillis = 180)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            ) + fadeOut(tween(durationMillis = 140)),
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                ScheduleSwitchRow(
                    title = stringResource(R.string.schedule_timer_title),
                    checked = timerEnabled,
                ) { checked ->
                    timerEnabled = checked
                    store.scheduleEnabled = checked
                    ProxyScheduler.settingsChanged(context)
                }
                if (timerEnabled) {
                    ScheduleTimeRow(
                        label = stringResource(R.string.schedule_on_time),
                        minutes = onMinutes,
                    ) { pickerField = ScheduleTimeField.TimerOn }
                    ScheduleTimeRow(
                        label = stringResource(R.string.schedule_off_time),
                        minutes = offMinutes,
                    ) { pickerField = ScheduleTimeField.TimerOff }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                ScheduleSwitchRow(
                    title = stringResource(R.string.schedule_night_title),
                    checked = nightEnabled,
                ) { checked ->
                    nightEnabled = checked
                    store.nightEnabled = checked
                    ProxyScheduler.settingsChanged(context)
                }
                if (nightEnabled) {
                    ScheduleTimeRow(
                        label = stringResource(R.string.schedule_night_start),
                        minutes = nightStart,
                    ) { pickerField = ScheduleTimeField.NightStart }
                    ScheduleTimeRow(
                        label = stringResource(R.string.schedule_night_end),
                        minutes = nightEnd,
                    ) { pickerField = ScheduleTimeField.NightEnd }
                    TuxunSegmentedRow(
                        options = listOf(
                            stringResource(R.string.schedule_night_action_global),
                            stringResource(R.string.schedule_night_action_stop),
                        ),
                        selectedIndex = nightAction,
                        onSelect = { index ->
                            nightAction = index
                            store.nightAction = index
                            ProxyScheduler.settingsChanged(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                    Text(
                        text = stringResource(R.string.schedule_night_end_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                if (!exactAllowed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    exactAlarmLauncher.launch(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:" + context.packageName),
                                        ),
                                    )
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_info),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.schedule_exact_alarm_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }

    val field = pickerField
    if (field != null) {
        val initial = when (field) {
            ScheduleTimeField.TimerOn -> onMinutes
            ScheduleTimeField.TimerOff -> offMinutes
            ScheduleTimeField.NightStart -> nightStart
            ScheduleTimeField.NightEnd -> nightEnd
        }

        ScheduleTimePickerDialog(
            initialMinutes = initial,
            onDismiss = { pickerField = null },
        ) { picked ->
            when (field) {
                ScheduleTimeField.TimerOn -> {
                    onMinutes = picked
                    store.scheduleOnMinutes = picked
                }
                ScheduleTimeField.TimerOff -> {
                    offMinutes = picked
                    store.scheduleOffMinutes = picked
                }
                ScheduleTimeField.NightStart -> {
                    nightStart = picked
                    store.nightStartMinutes = picked
                }
                ScheduleTimeField.NightEnd -> {
                    nightEnd = picked
                    store.nightEndMinutes = picked
                }
            }

            pickerField = null
            ProxyScheduler.settingsChanged(context)
        }
    }
}

@Composable
private fun ScheduleSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScheduleTimeRow(
    label: String,
    minutes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMinutesOfDay(minutes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.schedule_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
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
    onDragPickup: () -> Unit = {},
    onDragDrop: () -> Unit = {},
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
            onDragDrop()
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
                                        onDragPickup()
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
    entranceTick: Int,
    onDragPickup: () -> Unit,
    onDragDrop: () -> Unit,
) {
    val style = animStyle()

    val tilesByKey = mutableMapOf<String, HomeTile>()

    if (state.showTraffic) {
        tilesByKey["traffic"] = HomeTile(
            icon = DesignR.drawable.ic_baseline_swap_vert,
            title = R.string.traffic,
            // The totals lines are filled in by TrafficInfoTile when
            // the brick renders: the packed counter ticks about once
            // a second while connected, and reading it here would
            // recompose this whole cards section on every tick.
            lines = emptyList(),
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

    // Settings and Logs live in their own dock row at the very
    // bottom of the home screen (1.3.0), out of the main grid flow.
    // Their relative order still comes from brickOrder, and a brick
    // drag only ever swaps within the grid or within the dock.
    val dockIds = listOf("logs", "settings")
    val gridBricks = visibleBricks.filter { it !in dockIds }
    val dockBricks = visibleBricks.filter { it in dockIds }

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
        // Grid bricks and dock bricks never swap across the divide.
        val candidates = if (draggingKey in dockIds) dockBricks else gridBricks
        for (id in candidates) {
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
                            onDragPickup = onDragPickup,
                            onDragDrop = onDragDrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .homeEntrance(2, entranceTick),
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // The bricks grid (registers its own bounds
                            // so the monitor block can hit-test it).
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .homeEntrance(3, entranceTick)
                                    .onGloballyPositioned { coordinates ->
                                        drag.bounds["bricks"] = Rect(
                                            offset = coordinates.positionInRoot() -
                                                drag.containerOrigin,
                                            size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                gridBricks.chunked(2).forEach { rowBricks ->
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
                                                    onDragPickup = onDragPickup,
                                                    onDragDrop = onDragDrop,
                                                    modifier = Modifier.weight(1f),
                                                    pressScale = true,
                                                ) {
                                                    BrickContent(
                                                        id = id,
                                                        state = state,
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

                            // Dock row: Settings and Logs, sunk to the
                            // very bottom in their own row.
                            if (dockBricks.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .homeEntrance(4, entranceTick),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    dockBricks.forEach { id ->
                                        key(id) {
                                            DraggableHomeItem(
                                                key = id,
                                                drag = drag,
                                                editMode = editMode,
                                                wiggleSeed = id.hashCode(),
                                                onClick = brickClick(id),
                                                onEnterEditMode = { editMode = true },
                                                onDragMove = ::onDragMove,
                                                onDragPickup = onDragPickup,
                                                onDragDrop = onDragDrop,
                                                modifier = Modifier.weight(1f),
                                                pressScale = true,
                                            ) {
                                                BrickContent(
                                                    id = id,
                                                    state = state,
                                                    nodeTile = nodeTile,
                                                    tilesByKey = tilesByKey,
                                                    onRequest = onRequest,
                                                )
                                            }
                                        }
                                    }
                                    if (dockBricks.size == 1) {
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
    state: MainUiState,
    nodeTile: HomeTile?,
    tilesByKey: Map<String, HomeTile>,
    onRequest: (MainDesign.Request) -> Unit,
) {
    when (id) {
        "node" -> nodeTile?.let { InfoTile(tile = it, onRequest = onRequest) }
        "traffic" ->
            tilesByKey[id]?.let {
                TrafficInfoTile(state = state, tile = it, onRequest = onRequest)
            }
        "proxy", "profile", "providers" ->
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
 * The traffic totals brick. The packed traffic counter in
 * [MainUiState] ticks about once a second while connected; reading
 * it here — in this brick's own scope, instead of where the brick
 * list is built — confines the per-second recomposition to this
 * one brick instead of the whole home cards section.
 */
@Composable
private fun TrafficInfoTile(
    state: MainUiState,
    tile: HomeTile,
    onRequest: (MainDesign.Request) -> Unit,
) {
    InfoTile(
        tile = HomeTile(
            icon = tile.icon,
            title = tile.title,
            lines = listOf(
                "↑ ${state.traffic.trafficUpload()}",
                "↓ ${state.traffic.trafficDownload()}",
            ),
            request = tile.request,
            testRequest = tile.testRequest,
            testing = tile.testing,
        ),
        onRequest = onRequest,
    )
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

            // Window caption with the range switcher beside it
            // (1.4.0: five windows, 2 minutes … 2 hours) and the peak
            // rates inside the window, so the chart's scale is
            // readable. The fullscreen button opens the big chart
            // with the connection analysis.
            val windowIndex = state.trafficWindowIndex
                .coerceIn(0, TRAFFIC_WINDOW_MS.lastIndex)
            val windowMs = TRAFFIC_WINDOW_MS[windowIndex]
            var windowMenuOpen by remember { mutableStateOf(false) }
            val cardContext = LocalContext.current
            val cardUiStore = remember { UiStore(cardContext) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(TRAFFIC_WINDOW_CAPTION_RES[windowIndex]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(
                        onClick = { windowMenuOpen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_schedule),
                            contentDescription = stringResource(R.string.traffic_switch_window),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = windowMenuOpen,
                        onDismissRequest = { windowMenuOpen = false },
                    ) {
                        TRAFFIC_WINDOW_LABEL_RES.forEachIndexed { index, labelRes ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(labelRes),
                                        color = if (index == windowIndex) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    windowMenuOpen = false
                                    if (index != windowIndex) {
                                        state.trafficWindowIndex = index
                                        com.tuxundl.android.util.Haptics.buzz(
                                            cardContext,
                                            cardUiStore,
                                            com.tuxundl.android.util.Haptics.Kind.Tap,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                val (peakDown, peakUp) = if (windowIndex == 0) {
                    (state.trafficSamples.maxOfOrNull { it.downRate } ?: 0L) to
                        (state.trafficSamples.maxOfOrNull { it.upRate } ?: 0L)
                } else {
                    val end = state.historyPoints.lastOrNull()?.endMillis ?: 0L
                    val slice = state.historyPoints.filter { it.endMillis > end - windowMs }
                    (slice.maxOfOrNull { it.downRate } ?: 0L) to
                        (slice.maxOfOrNull { it.upRate } ?: 0L)
                }
                Text(
                    text = stringResource(
                        R.string.traffic_peak,
                        formatSpeed(peakDown),
                        formatSpeed(peakUp),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = {
                        state.showTrafficChart = true
                        com.tuxundl.android.util.Haptics.buzz(
                            cardContext,
                            cardUiStore,
                            com.tuxundl.android.util.Haptics.Kind.Tap,
                        )
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_fullscreen),
                        contentDescription = stringResource(R.string.traffic_fullscreen),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            // The 2-minute window keeps the original morphing chart;
            // longer windows draw the tiered history. Switching
            // cross-fades between the two.
            AnimatedContent(
                targetState = windowIndex,
                transitionSpec = {
                    fadeIn(todayEnterSpec) togetherWith fadeOut(todayExitSpec)
                },
                label = "trafficWindowChart",
            ) { index ->
                if (index == 0) {
                    TrafficChart(
                        samples = state.trafficSamples,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                    )
                } else {
                    HistoryChart(
                        points = state.historyPoints,
                        windowMs = TRAFFIC_WINDOW_MS[index],
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                    )
                }
            }

            // Live totals cross-fade as they tick up (1.3.0).
            AnimatedContent(
                targetState = state.traffic.trafficDownload() to
                    state.traffic.trafficUpload(),
                transitionSpec = {
                    fadeIn(todayEnterSpec) togetherWith fadeOut(todayExitSpec)
                },
                label = "totalsRow",
            ) { (downText, upText) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "↓ $downText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "↑ $upText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
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

            // Per-day view: this week (Monday..today), last week
            // (Monday..Sunday) or the rolling last 7 days ending
            // today, with a small bar per day. Only days with stored
            // data are shown; the file keeps 14 days, so last week
            // may be partial. Switching tabs cross-fades the content
            // and each bar grows with its own spring.
            Spacer(modifier = Modifier.height(2.dp))
            TuxunSegmentedRow(
                options = listOf(
                    stringResource(R.string.traffic_this_week),
                    stringResource(R.string.traffic_last_week),
                    stringResource(R.string.traffic_recent_days),
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
                val weekDays = when (tab) {
                    0 -> state.currentWeekDays
                    1 -> state.lastWeekDays
                    else -> state.recentDays
                }
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
                            text = stringResource(
                                if (tab == 2) {
                                    R.string.traffic_recent_empty
                                } else {
                                    R.string.traffic_week_empty
                                },
                            ),
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
    val style = animStyle()

    var sweepTarget by remember { mutableStateOf(0f) }
    val sweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
        label = "chartSweep",
    )
    LaunchedEffect(Unit) {
        sweepTarget = 1f
    }

    // Curve morphing (1.3.0): when a new sample lands, the drawn
    // series interpolates from the previous window over ~280 ms
    // instead of jumping, and the vertical scale eases toward the
    // new maximum rather than snapping.
    var prevSamples by remember { mutableStateOf<List<TrafficSample>>(emptyList()) }
    var displaySamples by remember { mutableStateOf(samples) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(samples) {
        val from = prevSamples
        prevSamples = samples
        if (style == AnimationStyle.Reduced || from.isEmpty() ||
            samples.isEmpty() || from == samples
        ) {
            displaySamples = samples
            morph.snapTo(1f)
        } else {
            morph.snapTo(0f)
            morph.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ) {
                displaySamples = interpolateTrafficSamples(from, samples, value)
            }
            displaySamples = samples
        }
    }

    val rawMax = displaySamples.maxOfOrNull { maxOf(it.upRate, it.downRate) }
        ?.toFloat()
        ?: 1f
    val animatedMax by animateFloatAsState(
        targetValue = rawMax.coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "chartMax",
    )

    Canvas(modifier = modifier) {
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx(),
        )

        if (displaySamples.size < 2) return@Canvas

        val maxRate = animatedMax
        val usableHeight = size.height - 6.dp.toPx()

        fun pointsOf(up: Boolean): List<Offset> =
            displaySamples.mapIndexed { index, sample ->
                val rate = if (up) sample.upRate else sample.downRate
                Offset(
                    x = size.width * index / (displaySamples.size - 1),
                    y = size.height - (rate / maxRate) * usableHeight,
                )
            }

        val downPoints = pointsOf(up = false)

        // Download carries the gradient fill; upload is a plain line.
        drawSeries(downPoints, downColor, sweep, filled = true)
        drawSeries(pointsOf(up = true), upColor, sweep, filled = false)

        // Peak marker: a dot and a faint dashed guide at the highest
        // download point inside the revealed part of the window (the
        // card header prints the peak values as text).
        val peakIndex = displaySamples.indices
            .maxByOrNull { displaySamples[it].downRate }
        if (peakIndex != null) {
            val peakPoint = downPoints[peakIndex]
            if (peakPoint.x <= size.width * sweep) {
                drawLine(
                    color = downColor.copy(alpha = 0.35f),
                    start = peakPoint,
                    end = Offset(peakPoint.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = peakPoint,
                )
                drawCircle(
                    color = downColor,
                    radius = 2.5.dp.toPx(),
                    center = peakPoint,
                )
            }
        }
    }
}

/** Interpolate two sample windows at [t], aligned from the end. */
private fun interpolateTrafficSamples(
    from: List<TrafficSample>,
    to: List<TrafficSample>,
    t: Float,
): List<TrafficSample> = to.mapIndexed { index, sample ->
    val fromSample = from.getOrNull(from.size - (to.size - index))
    if (fromSample == null) {
        sample
    } else {
        TrafficSample(
            upRate = fromSample.upRate +
                ((sample.upRate - fromSample.upRate) * t).roundToLong(),
            downRate = fromSample.downRate +
                ((sample.downRate - fromSample.downRate) * t).roundToLong(),
        )
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
    val context = LocalContext.current
    val uiStore = remember { UiStore(context) }

    // Traffic alert quick setting (1.5.0): mirrors the exact UiStore
    // fields the settings page edits, so the two never disagree.
    var alertEnabled by remember { mutableStateOf(uiStore.trafficAlertEnabled) }
    var alertThresholdGb by remember { mutableStateOf(uiStore.trafficAlertThresholdGb) }
    var showAlertDialog by remember { mutableStateOf(false) }

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
                )
                // Armed alert marker (1.5.0): a red dot rides next to
                // the title whenever the low-traffic alert is on. It
                // breathes gently once the remainder has actually
                // dropped to the threshold.
                if (alertEnabled) {
                    val remainingBytes = state.subTotal - used
                    val breached = remainingBytes <=
                        alertThresholdGb.toLong() * 1024L * 1024L * 1024L
                    val dotAlpha = if (breached) {
                        val pulse = rememberInfiniteTransition(
                            label = "alertDotPulse",
                        )
                        val animated by pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.25f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "alertDotAlpha",
                        )
                        animated
                    } else {
                        1f
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.error
                                    .copy(alpha = dotAlpha),
                            ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
            // Quick alert row (1.5.0): tap to arm/disarm and pick the
            // threshold without leaving the home screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAlertDialog = true }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.traffic_alert),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (alertEnabled) {
                        stringResource(
                            R.string.traffic_alert_card_value,
                            alertThresholdGb,
                        )
                    } else {
                        stringResource(R.string.traffic_alert_card_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(
                        DesignR.drawable.ic_baseline_expand_more,
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    }

    if (showAlertDialog) {
        SubscriptionAlertDialog(
            enabled = alertEnabled,
            thresholdGb = alertThresholdGb,
            onConfirm = { enabled, gb ->
                showAlertDialog = false
                alertEnabled = enabled
                alertThresholdGb = gb
                uiStore.trafficAlertEnabled = enabled
                uiStore.trafficAlertThresholdGb = gb
                Haptics.buzz(context, uiStore, Haptics.Kind.Tap)
            },
            onDismiss = { showAlertDialog = false },
        )
    }
}

/**
 * Traffic-alert picker for the home subscription card (1.5.0): the
 * same switch + GiB threshold as the settings page (one shared
 * UiStore setting), with common presets and a custom value.
 */
@Composable
private fun SubscriptionAlertDialog(
    enabled: Boolean,
    thresholdGb: Int,
    onConfirm: (Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(1, 3, 5, 10, 20)
    var enabledState by remember { mutableStateOf(enabled) }
    var customSelected by remember { mutableStateOf(thresholdGb !in presets) }
    var selectedGb by remember { mutableStateOf(thresholdGb) }
    var customText by remember {
        mutableStateOf(
            if (thresholdGb !in presets) thresholdGb.toString() else "",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.traffic_alert)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.traffic_alert),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TuxunSwitch(
                        checked = enabledState,
                        onCheckedChange = { enabledState = it },
                    )
                }

                if (enabledState) {
                    Spacer(modifier = Modifier.height(8.dp))
                    presets.forEach { gb ->
                        AlertThresholdRow(
                            label = stringResource(
                                R.string.traffic_alert_threshold_value,
                                gb,
                            ),
                            selected = !customSelected && selectedGb == gb,
                            onClick = {
                                customSelected = false
                                selectedGb = gb
                            },
                        )
                    }
                    AlertThresholdRow(
                        label = stringResource(R.string.traffic_alert_custom),
                        selected = customSelected,
                        onClick = { customSelected = true },
                    )
                    if (customSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { input ->
                                customText = input
                                    .filter { it.isDigit() }
                                    .take(3)
                            },
                            singleLine = true,
                            suffix = { Text(text = "GB") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val gb = if (customSelected) {
                        customText.toIntOrNull()?.coerceIn(1, 999)
                            ?: selectedGb
                    } else {
                        selectedGb
                    }
                    onConfirm(enabledState, gb)
                },
            ) {
                Text(text = stringResource(DesignR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(DesignR.string.cancel))
            }
        },
    )
}

/** One radio row of the subscription alert picker. */
@Composable
private fun AlertThresholdRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
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
    // Taller than before (1.3.0 proportions): the brick now matches
    // the info tiles' minimum height, with its content centred, so
    // mixed rows and the bottom dock read as one rhythm.
    TuxunCard(modifier = modifier.heightIn(min = 96.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
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
