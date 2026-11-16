package com.tuxundl.android.ui

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.core.util.trafficDownload
import com.tuxundl.android.core.util.trafficUpload
import com.tuxundl.android.design.MainDesign
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

    val snackbarHostState = SnackbarHostState()
}

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
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PowerCircle(state = state, onRequest = onRequest)
            }

            ModeSelector(state = state, onRequest = onRequest)

            InfoTiles(state = state, onRequest = onRequest)

            BottomNavRow(onRequest = onRequest)
        }
    }

    val aboutVersion = state.aboutVersion
    if (aboutVersion != null) {
        AlertDialog(
            onDismissRequest = { state.aboutVersion = null },
            title = { Text(text = stringResource(R.string.application_name)) },
            text = { Text(text = aboutVersion) },
            confirmButton = {
                TextButton(onClick = { state.aboutVersion = null }) {
                    Text(text = stringResource(DesignR.string.ok))
                }
            },
        )
    }
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
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    if (running) primary else MaterialTheme.colorScheme.surface,
                )
                .then(
                    if (running) {
                        Modifier
                    } else {
                        Modifier.border(2.dp, primary, CircleShape)
                    },
                )
                .clickable { onRequest(MainDesign.Request.ToggleStatus) },
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
                    tint = contentColor,
                    modifier = Modifier.size(54.dp),
                )
                Text(
                    text = stringResource(
                        if (running) DesignR.string.running else DesignR.string.stopped,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (running) onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (running) {
                        "↑ $upSpeed   ↓ $downSpeed"
                    } else {
                        stringResource(DesignR.string.tap_to_start)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}

/** Rule / Global / Direct switch, right under the power circle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val modes = listOf(
        TunnelState.Mode.Rule to DesignR.string.rule_mode,
        TunnelState.Mode.Global to DesignR.string.global_mode,
        TunnelState.Mode.Direct to DesignR.string.direct_mode,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = state.mode == value,
                onClick = { onRequest(MainDesign.Request.SetMode(value)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {},
                label = { Text(text = stringResource(label)) },
            )
        }
    }
}

private class HomeTile(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    val lines: List<String>,
    /** Null = display-only tile (traffic totals), not clickable. */
    val request: MainDesign.Request?,
)

/** Two-column grid of small info tiles under the power circle. */
@Composable
private fun InfoTiles(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val tiles = mutableListOf<HomeTile>()

    if (state.showTraffic) {
        tiles += HomeTile(
            icon = DesignR.drawable.ic_baseline_swap_vert,
            title = R.string.traffic,
            lines = listOf(
                "↑ ${state.traffic.trafficUpload()}",
                "↓ ${state.traffic.trafficDownload()}",
            ),
            request = null,
        )
    }
    if (state.showProxy) {
        tiles += HomeTile(
            icon = DesignR.drawable.ic_baseline_apps,
            title = DesignR.string.proxy,
            lines = listOf(modeText(state.mode)),
            request = MainDesign.Request.OpenProxy,
        )
    }
    if (state.showProfile) {
        tiles += HomeTile(
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
        tiles += HomeTile(
            icon = DesignR.drawable.ic_baseline_extension,
            title = DesignR.string.providers,
            lines = emptyList(),
            request = MainDesign.Request.OpenProviders,
        )
    }

    if (tiles.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTiles.forEach { tile ->
                    InfoTile(
                        tile = tile,
                        onRequest = onRequest,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
    val cardModifier = modifier.heightIn(min = 108.dp)

    if (tile.request != null) {
        Card(
            onClick = { onRequest(tile.request) },
            modifier = cardModifier,
        ) {
            TileContent(tile)
        }
    } else {
        Card(modifier = cardModifier) {
            TileContent(tile)
        }
    }
}

@Composable
private fun TileContent(tile: HomeTile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
    Card(
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

private fun trafficUploadBytes(traffic: Long): Long =
    decodeTrafficHalf(traffic ushr 32)

private fun trafficDownloadBytes(traffic: Long): Long =
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
