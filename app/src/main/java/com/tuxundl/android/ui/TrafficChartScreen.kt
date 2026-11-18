package com.tuxundl.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.design.util.toBytesString
import com.tuxundl.android.util.ConnectionState
import com.tuxundl.android.util.HealthKind
import com.tuxundl.android.util.HealthSegment
import com.tuxundl.android.util.Haptics
import com.tuxundl.android.util.TrafficPoint
import com.tuxundl.android.util.analyzeHealth
import com.tuxundl.android.util.averageDelayIn
import com.tuxundl.android.util.connectionStateAt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import com.tuxundl.android.design.R as DesignR

/** The five chart windows (1.4.0), shortest first. */
internal val TRAFFIC_WINDOW_MS =
    listOf(120_000L, 600_000L, 1_800_000L, 3_600_000L, 7_200_000L)

internal val TRAFFIC_WINDOW_LABEL_RES = listOf(
    R.string.traffic_win_2m,
    R.string.traffic_win_10m,
    R.string.traffic_win_30m,
    R.string.traffic_win_1h,
    R.string.traffic_win_2h,
)

internal val TRAFFIC_WINDOW_CAPTION_RES = listOf(
    R.string.traffic_last_2_min,
    R.string.traffic_last_10_min,
    R.string.traffic_last_30_min,
    R.string.traffic_last_1_hour,
    R.string.traffic_last_2_hours,
)

/** Closest the fullscreen chart can zoom in. */
private const val MIN_VIEW_SPAN_MS = 10_000L

/** Chart colours for the health overlays (the poor yellow matches the delay tiers). */
private val PoorFill = Color(0xFFFACC15)
private val OutageRed = Color(0xFFEF5350)
private val OkGreen = Color(0xFF43A047)
private val PoorText = Color(0xFFF9A825)

/**
 * Fullscreen traffic overview + connection analysis (1.4.0).
 *
 * One chart over the tiered 2-hour history: five time windows on a
 * plain segmented row, pinch to zoom, two-finger drag to pan,
 * double-tap to reset, and a one-finger scrub that pins a crosshair
 * to any moment — the card under the chart then shows that moment's
 * up/down rates and what the connection was doing (from the delay
 * probes: OK / struggling / down / no data). Outages are drawn as
 * red lines, struggling stretches as yellow filled regions with a
 * yellow top edge. The summary strip totals the selected window:
 * average delay, total traffic, drop count.
 */
@Composable
fun TrafficChartDialog(
    state: MainUiState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val uiStore = remember { UiStore(context) }
    val style = animStyle()

    val windowIndex = state.trafficWindowIndex
        .coerceIn(0, TRAFFIC_WINDOW_MS.lastIndex)
    val windowMs = TRAFFIC_WINDOW_MS[windowIndex]
    val points = state.historyPoints
    val probes = state.delayHistory
    val segments = remember(probes) {
        analyzeHealth(probes, System.currentTimeMillis())
    }

    // Viewport: in follow mode the right edge tracks the newest data;
    // any pinch/pan drops out of follow until "back to now" is tapped.
    var follow by remember { mutableStateOf(true) }
    var viewSpanMs by remember { mutableStateOf(windowMs) }
    var viewEndMs by remember { mutableStateOf(0L) }
    var selected by remember { mutableStateOf<TrafficPoint?>(null) }

    val dataEnd = points.lastOrNull()?.endMillis
        ?: System.currentTimeMillis()
    val effectiveEnd = if (follow) dataEnd else viewEndMs
    val viewStart = effectiveEnd - viewSpanMs

    LaunchedEffect(windowIndex) {
        follow = true
        viewSpanMs = windowMs
        selected = null
    }

    fun buzz() = Haptics.buzz(context, uiStore, Haptics.Kind.Tap)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .tuxunPageEnter()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.traffic_chart_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_close),
                            contentDescription = null,
                        )
                    }
                }

                // Plain segmented window switcher (no big buttons).
                TuxunSegmentedRow(
                    options = TRAFFIC_WINDOW_LABEL_RES.map { stringResource(it) },
                    selectedIndex = windowIndex,
                    onSelect = { index ->
                        if (index != windowIndex) {
                            state.trafficWindowIndex = index
                            buzz()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Legend: series colours + health marks.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "↓",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LegendDot(color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LegendBar(color = PoorFill)
                    Text(
                        text = stringResource(R.string.traffic_legend_poor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LegendBar(color = OutageRed)
                    Text(
                        text = stringResource(R.string.traffic_legend_outage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (points.isEmpty()) {
                    ChartEmptyState(
                        text = stringResource(R.string.traffic_chart_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else if (points.size < 2) {
                    ChartEmptyState(
                        text = stringResource(R.string.traffic_chart_collecting),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    InteractiveChart(
                        points = points,
                        segments = segments,
                        disconnectTimes = state.disconnectTimes,
                        viewStart = viewStart,
                        viewSpanMs = viewSpanMs,
                        selected = selected,
                        onSelect = { point ->
                            if (point?.startMillis != selected?.startMillis) {
                                selected = point
                            }
                        },
                        onTransform = { newEnd, newSpan ->
                            follow = false
                            viewEndMs = newEnd
                            viewSpanMs = newSpan
                        },
                        onReset = {
                            follow = true
                            viewSpanMs = windowMs
                            selected = null
                            buzz()
                        },
                        onScrubBuzz = { buzz() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    // Axis labels: start / middle / end of the view.
                    val axisFormat = remember(viewSpanMs) {
                        SimpleDateFormat(
                            if (viewSpanMs <= 600_000L) "HH:mm:ss" else "HH:mm",
                            Locale.US,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = axisFormat.format(Date(viewStart)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = axisFormat.format(Date(viewStart + viewSpanMs / 2)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = axisFormat.format(Date(effectiveEnd)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Selection info / hint, cross-fading between moments.
                TuxunCard(
                    shape = RoundedCornerShape(TuxunCardCornerLarge),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = selected?.startMillis,
                        transitionSpec = {
                            fadeIn(tuxunFadeSpec(style, entering = true)) togetherWith
                                fadeOut(tuxunFadeSpec(style, entering = false))
                        },
                        label = "chartSelection",
                    ) { selectedStart ->
                        val point = selected?.takeIf { it.startMillis == selectedStart }
                        if (point == null) {
                            Text(
                                text = stringResource(R.string.traffic_chart_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            SelectionInfo(
                                point = point,
                                probes = probes,
                                segments = segments,
                            )
                        }
                    }
                }

                // Window summary: average delay / total traffic / drops.
                val windowStart = dataEnd - windowMs
                val avgDelay = remember(probes, windowStart, dataEnd) {
                    averageDelayIn(probes, windowStart, dataEnd)
                }
                val outageCount = remember(segments, state.disconnectTimes, windowStart, dataEnd) {
                    val outages = segments.count {
                        it.kind == HealthKind.Outage && it.endMillis > windowStart
                    }
                    val resets = state.disconnectTimes.count { at ->
                        at in windowStart..dataEnd && segments.none { segment ->
                            segment.kind == HealthKind.Outage &&
                                at in segment.startMillis..segment.endMillis
                        }
                    }
                    outages + resets
                }
                val windowTotals = remember(points, windowStart, dataEnd) {
                    totalsInWindow(points, windowStart, dataEnd)
                }

                TuxunCard(
                    shape = RoundedCornerShape(TuxunCardCornerLarge),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        SummaryStat(
                            label = stringResource(R.string.quality_avg_delay),
                            value = avgDelay?.let { "$it ms" }
                                ?: stringResource(R.string.traffic_status_nodata),
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStat(
                            label = stringResource(R.string.traffic_summary_traffic),
                            value = "↓ ${windowTotals.second.toBytesString()}\n" +
                                "↑ ${windowTotals.first.toBytesString()}",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStat(
                            label = stringResource(R.string.traffic_summary_disconnects),
                            value = outageCount.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                AnimatedVisibility(visible = !follow) {
                    Button(
                        onClick = {
                            follow = true
                            buzz()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.traffic_chart_back_now))
                    }
                }
            }
        }
    }
}

/** Bytes forwarded inside [fromMillis, toMillis], prorating edge points. */
private fun totalsInWindow(
    points: List<TrafficPoint>,
    fromMillis: Long,
    toMillis: Long,
): Pair<Long, Long> {
    var up = 0L
    var down = 0L
    for (point in points) {
        val overlapStart = maxOf(point.startMillis, fromMillis)
        val overlapEnd = minOf(point.endMillis, toMillis)
        if (overlapEnd <= overlapStart || point.windowMillis <= 0L) continue
        val fraction = (overlapEnd - overlapStart).toDouble() / point.windowMillis
        up += (point.upBytes * fraction).roundToLong()
        down += (point.downBytes * fraction).roundToLong()
    }
    return up to down
}

@Composable
private fun ChartEmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mascot(size = 72.dp, animated = false)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun LegendBar(color: Color) {
    Box(
        modifier = Modifier
            .width(14.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The pinned moment: clock time, both rates, and the connection state then. */
@Composable
private fun SelectionInfo(
    point: TrafficPoint,
    probes: List<com.tuxundl.android.util.DelaySample>,
    segments: List<HealthSegment>,
) {
    val center = point.startMillis + point.windowMillis / 2
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val (stateAt, delay) = remember(point.startMillis, probes, segments) {
        connectionStateAt(center, probes, segments)
    }
    val statusColor = when (stateAt) {
        ConnectionState.Ok -> OkGreen
        ConnectionState.Poor -> PoorText
        ConnectionState.Outage -> OutageRed
        ConnectionState.NoData -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (stateAt) {
        ConnectionState.Ok -> stringResource(R.string.traffic_status_ok)
        ConnectionState.Poor -> stringResource(R.string.traffic_status_poor)
        ConnectionState.Outage -> stringResource(R.string.traffic_status_outage)
        ConnectionState.NoData -> stringResource(R.string.traffic_status_nodata)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(center)),
                style = MaterialTheme.typography.titleMedium,
            )
            if (point.windowMillis > 1_500L) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.traffic_chart_bucket_avg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            LegendDot(color = statusColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )
            if (delay != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.traffic_status_delay, delay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "↓ ${formatRate(point.downRate)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "↑ ${formatRate(point.upRate)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** Bytes/s formatted like the home chart's peak label. */
private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024 * 1024 ->
        String.format("%.2f GiB/s", bytesPerSecond / (1024.0 * 1024 * 1024))
    bytesPerSecond >= 1024L * 1024 ->
        String.format("%.2f MiB/s", bytesPerSecond / (1024.0 * 1024))
    bytesPerSecond >= 1024L ->
        String.format("%.1f KiB/s", bytesPerSecond / 1024.0)
    else -> "$bytesPerSecond B/s"
}

/**
 * The interactive chart itself: pinch zoom (10 s … the whole
 * window), two-finger pan, double-tap reset, one-finger scrub with
 * a crosshair. Health overlays come from [segments] (yellow poor
 * regions, red outage lines) plus the raw counter-reset instants.
 */
@Composable
private fun InteractiveChart(
    points: List<TrafficPoint>,
    segments: List<HealthSegment>,
    disconnectTimes: List<Long>,
    viewStart: Long,
    viewSpanMs: Long,
    selected: TrafficPoint?,
    onSelect: (TrafficPoint?) -> Unit,
    onTransform: (endMs: Long, spanMs: Long) -> Unit,
    onReset: () -> Unit,
    onScrubBuzz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val style = animStyle()

    val visible = remember(points, viewStart, viewSpanMs) {
        points.filter {
            it.endMillis > viewStart && it.startMillis < viewStart + viewSpanMs
        }
    }
    val targetMax = visible.maxOfOrNull { maxOf(it.upRate, it.downRate) }
        ?.toFloat() ?: 1f
    val animatedMax by animateFloatAsState(
        targetValue = targetMax.coerceAtLeast(1f),
        animationSpec = tween(
            durationMillis = if (style == com.tuxundl.android.design.model.AnimationStyle.Reduced) 120 else 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "chartViewMax",
    )

    // Entrance sweep: the series draws itself left to right once.
    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (style == com.tuxundl.android.design.model.AnimationStyle.Reduced) 150 else 900,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
    }

    // Latest values for the gesture coroutine (it outlives frames).
    val pointsRef = androidx.compose.runtime.rememberUpdatedState(points)
    val viewStartRef = androidx.compose.runtime.rememberUpdatedState(viewStart)
    val viewSpanRef = androidx.compose.runtime.rememberUpdatedState(viewSpanMs)
    val onSelectRef = androidx.compose.runtime.rememberUpdatedState(onSelect)
    val onTransformRef = androidx.compose.runtime.rememberUpdatedState(onTransform)
    val onResetRef = androidx.compose.runtime.rememberUpdatedState(onReset)
    val onScrubBuzzRef = androidx.compose.runtime.rememberUpdatedState(onScrubBuzz)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                var lastBuzzStart = Long.MIN_VALUE
                var lastBuzzAt = 0L

                fun timeAtX(x: Float): Long =
                    viewStartRef.value +
                        (x / size.width * viewSpanRef.value).roundToLong()

                fun selectAt(x: Float) {
                    val target = timeAtX(x)
                    val candidate = pointsRef.value.minByOrNull {
                        abs(it.startMillis + it.windowMillis / 2 - target)
                    } ?: return
                    onSelectRef.value(candidate)
                    val now = System.currentTimeMillis()
                    if (candidate.startMillis != lastBuzzStart && now - lastBuzzAt > 70L) {
                        lastBuzzStart = candidate.startMillis
                        lastBuzzAt = now
                        onScrubBuzzRef.value()
                    }
                }

                fun clampEnd(end: Long, span: Long): Long {
                    val pts = pointsRef.value
                    val dataEnd = pts.lastOrNull()?.endMillis ?: end
                    val dataStart = pts.firstOrNull()?.startMillis ?: (dataEnd - span)
                    val minEnd = dataStart + span
                    return if (minEnd >= dataEnd) dataEnd else end.coerceIn(minEnd, dataEnd)
                }

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var transformed = false
                    var lastCentroidX = firstDown.position.x
                    var lastDist = 0f

                    selectAt(firstDown.position.x)

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size >= 2) {
                            transformed = true
                            val p0 = pressed[0].position
                            val p1 = pressed[1].position
                            val centroidX = (p0.x + p1.x) / 2f
                            val dist = (p0 - p1).getDistance()

                            if (lastDist > 0f) {
                                val span = viewSpanRef.value
                                val end = viewStartRef.value + span
                                val anchor = timeAtX(centroidX)
                                val ratio = if (size.width > 0) {
                                    centroidX / size.width
                                } else {
                                    0.5f
                                }
                                val newSpan = (span / (dist / lastDist))
                                    .roundToLong()
                                    .coerceIn(MIN_VIEW_SPAN_MS, TRAFFIC_WINDOW_MS.last())
                                var newEnd = anchor + (newSpan * (1f - ratio)).roundToLong()
                                // Centroid drift pans the view.
                                val panDt = ((centroidX - lastCentroidX) / size.width * newSpan)
                                    .roundToLong()
                                newEnd -= panDt
                                onTransformRef.value(clampEnd(newEnd, newSpan), newSpan)
                            }
                            lastDist = dist
                            lastCentroidX = centroidX
                        } else if (pressed.size == 1) {
                            if (!transformed) {
                                selectAt(pressed[0].position.x)
                            }
                            lastDist = 0f
                            lastCentroidX = pressed[0].position.x
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onResetRef.value() })
            },
    ) {
        fun x(t: Long): Float =
            (t - viewStart).toFloat() / viewSpanMs * size.width

        // Poor stretches: yellow fill + a yellow line along the top.
        for (segment in segments) {
            if (segment.kind != HealthKind.Poor) continue
            if (segment.endMillis <= viewStart || segment.startMillis >= viewStart + viewSpanMs) {
                continue
            }
            val x0 = x(segment.startMillis).coerceIn(0f, size.width)
            val x1 = x(segment.endMillis).coerceIn(0f, size.width)
            if (x1 <= x0) continue
            drawRect(
                color = PoorFill.copy(alpha = 0.13f),
                topLeft = Offset(x0, 0f),
                size = androidx.compose.ui.geometry.Size(x1 - x0, size.height),
            )
            drawLine(
                color = PoorFill.copy(alpha = 0.6f),
                start = Offset(x0, 2.dp.toPx()),
                end = Offset(x1, 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }

        // Outages: a solid red line where the connection dropped.
        for (segment in segments) {
            if (segment.kind != HealthKind.Outage) continue
            if (segment.startMillis !in viewStart..(viewStart + viewSpanMs)) continue
            val sx = x(segment.startMillis)
            drawLine(
                color = OutageRed.copy(alpha = 0.9f),
                start = Offset(sx, 0f),
                end = Offset(sx, size.height),
                strokeWidth = 2.5.dp.toPx(),
            )
        }
        // Raw counter resets (tunnel reconnects) get a thinner line.
        for (at in disconnectTimes) {
            if (at !in viewStart..(viewStart + viewSpanMs)) continue
            val sx = x(at)
            drawLine(
                color = OutageRed.copy(alpha = 0.45f),
                start = Offset(sx, 0f),
                end = Offset(sx, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // Mid gridline + baseline.
        drawLine(
            color = baselineColor.copy(alpha = 0.5f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx(),
        )

        clipRect(right = size.width * sweep.value) {
            drawHistoryRuns(visible, viewStart, viewSpanMs, animatedMax, downColor, upColor)
        }

        // Crosshair on the pinned moment.
        val pinned = selected
        if (pinned != null) {
            val cx = x(pinned.startMillis + pinned.windowMillis / 2)
            if (cx in 0f..size.width) {
                drawLine(
                    color = crosshairColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(6f, 6f),
                    ),
                )
                val usable = size.height - 6.dp.toPx()
                val downY = size.height - (pinned.downRate / animatedMax) * usable
                val upY = size.height - (pinned.upRate / animatedMax) * usable
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx, downY))
                drawCircle(color = downColor, radius = 3.dp.toPx(), center = Offset(cx, downY))
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx, upY))
                drawCircle(color = upColor, radius = 3.dp.toPx(), center = Offset(cx, upY))
            }
        }
    }
}

/**
 * The home card's chart for windows longer than 2 minutes: the same
 * tiered history, drawn without gestures (the 2-minute window keeps
 * the original morphing chart). Window changes replay the sweep.
 */
@Composable
internal fun HistoryChart(
    points: List<TrafficPoint>,
    windowMs: Long,
    modifier: Modifier = Modifier,
) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val style = animStyle()

    val dataEnd = points.lastOrNull()?.endMillis ?: System.currentTimeMillis()
    val viewStart = dataEnd - windowMs
    val visible = points.filter {
        it.endMillis > viewStart && it.startMillis < dataEnd
    }
    val targetMax = visible.maxOfOrNull { maxOf(it.upRate, it.downRate) }
        ?.toFloat() ?: 1f
    val animatedMax by animateFloatAsState(
        targetValue = targetMax.coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "historyChartMax",
    )

    val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(windowMs) {
        sweep.snapTo(0f)
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (style == com.tuxundl.android.design.model.AnimationStyle.Reduced) 120 else 700,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
    }

    Canvas(modifier = modifier) {
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx(),
        )
        if (visible.size < 2) return@Canvas
        clipRect(right = size.width * sweep.value) {
            drawHistoryRuns(visible, viewStart, windowMs, animatedMax, downColor, upColor)
        }
    }
}

/**
 * Draw both series of a point list, breaking the lines wherever the
 * sampling has a real gap (app paused) instead of bridging it.
 */
private fun DrawScope.drawHistoryRuns(
    points: List<TrafficPoint>,
    viewStart: Long,
    viewSpanMs: Long,
    maxRate: Float,
    downColor: Color,
    upColor: Color,
) {
    val usableHeight = size.height - 6.dp.toPx()

    fun offsetOf(point: TrafficPoint, rate: Long): Offset = Offset(
        x = (point.startMillis + point.windowMillis / 2 - viewStart).toFloat() /
            viewSpanMs * size.width,
        y = size.height - (rate / maxRate) * usableHeight,
    )

    for (run in splitRuns(points)) {
        if (run.size == 1) {
            val point = run.first()
            drawCircle(
                color = downColor,
                radius = 2.dp.toPx(),
                center = offsetOf(point, point.downRate),
            )
            continue
        }

        val downOffsets = run.map { offsetOf(it, it.downRate) }
        val downPath = smoothPathOf(downOffsets)
        val fillPath = Path().apply {
            addPath(downPath)
            lineTo(downOffsets.last().x, size.height)
            lineTo(downOffsets.first().x, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(downColor.copy(alpha = 0.32f), downColor.copy(alpha = 0f)),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = downPath,
            color = downColor,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawPath(
            path = smoothPathOf(run.map { offsetOf(it, it.upRate) }),
            color = upColor,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** Split points wherever the gap between neighbours is implausibly large. */
private fun splitRuns(points: List<TrafficPoint>): List<List<TrafficPoint>> {
    if (points.isEmpty()) return emptyList()

    val runs = mutableListOf<MutableList<TrafficPoint>>()
    var current = mutableListOf(points.first())
    runs += current

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val next = points[i]
        val gap = next.startMillis - prev.endMillis
        val limit = maxOf(prev.windowMillis, next.windowMillis) * 2 + 500L
        if (gap > limit) {
            current = mutableListOf()
            runs += current
        }
        current += next
    }
    return runs
}

/** Catmull-Rom smoothing, the same curve family as the home chart. */
private fun smoothPathOf(points: List<Offset>): Path {
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
