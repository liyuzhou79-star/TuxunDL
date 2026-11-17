package com.tuxundl.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.util.toBytesString
import com.tuxundl.android.util.HourTrafficBucket
import com.tuxundl.android.util.QualityRating
import com.tuxundl.android.util.SavedConnection
import com.tuxundl.android.util.summarizeQuality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tuxundl.android.design.R as DesignR

/**
 * Advanced traffic panel (full-screen dialog), opened by tapping the
 * home traffic monitor card or the traffic tile. Everything here counts
 * connected time only, over the last 24 hours:
 *
 * - 24h up/down totals (from the hourly buckets) and today's connected
 *   duration;
 * - an hourly bar chart over the last 24 hours;
 * - connection quality from MainActivity's periodic delay probes of the
 *   current group (average delay, jitter, failure rate, stall count)
 *   with a simple Excellent/Stable/Unstable/Poor verdict;
 * - "save this connection": snapshot the live session into the
 *   saved-connections list below, where entries can be deleted one by
 *   one.
 */
@Composable
fun TrafficAdvancedDialog(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
    onClose: () -> Unit,
) {
    val summary = remember(state.delaySamples) { summarizeQuality(state.delaySamples) }
    val last24Up = state.hourlyBuckets.sumOf { it.up }
    val last24Down = state.hourlyBuckets.sumOf { it.down }

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.traffic_advanced_title),
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

                // --- Last 24 hours -----------------------------------
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
                        Text(
                            text = stringResource(
                                R.string.traffic_last_24h_total,
                                last24Up.toBytesString(),
                                last24Down.toBytesString(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.traffic_conn_today) + ": " +
                                formatDuration(state.todayConnectedSeconds * 1000L),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.clashRunning) {
                            Text(
                                text = stringResource(
                                    R.string.connected_for,
                                    formatDuration(state.connectedMillis),
                                ) + "  ↑ ${state.sessionUpBytes.toBytesString()}" +
                                    "  ↓ ${state.sessionDownBytes.toBytesString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // --- Hourly chart ------------------------------------
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
                        Text(
                            text = stringResource(R.string.traffic_hourly_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        HourlyBars(buckets = state.hourlyBuckets)
                    }
                }

                // --- Connection quality ------------------------------
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
                        Text(
                            text = stringResource(R.string.traffic_quality_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val qualityColor = ratingColor(summary.rating)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = qualityColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(ratingTextRes(summary.rating)),
                                style = MaterialTheme.typography.titleMedium,
                                color = ratingColor(summary.rating),
                            )
                        }
                        QualityMetricRow(
                            label = stringResource(R.string.quality_avg_delay),
                            value = if (summary.successCount > 0) {
                                "${summary.avgDelayMs} ms"
                            } else {
                                "-"
                            },
                        )
                        QualityMetricRow(
                            label = stringResource(R.string.quality_jitter),
                            value = if (summary.successCount >= 2) {
                                "${summary.jitterMs} ms"
                            } else {
                                "-"
                            },
                        )
                        QualityMetricRow(
                            label = stringResource(R.string.quality_fail_rate),
                            value = if (summary.sampleCount > 0) {
                                "${(summary.failRate * 100).toInt()}%"
                            } else {
                                "-"
                            },
                        )
                        QualityMetricRow(
                            label = stringResource(R.string.quality_stalls),
                            value = state.stallCount.toString(),
                        )
                        QualityMetricRow(
                            label = stringResource(R.string.quality_samples),
                            value = summary.sampleCount.toString(),
                        )
                    }
                }

                Button(
                    onClick = { onRequest(MainDesign.Request.SaveCurrentConnection) },
                    enabled = state.clashRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.save_connection))
                }

                // --- Saved connections -------------------------------
                Text(
                    text = stringResource(R.string.saved_connections),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.savedConnections.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Mascot(size = 56.dp, animated = false)
                        Text(
                            text = stringResource(R.string.saved_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.savedConnections.forEach { connection ->
                        SavedConnectionRow(
                            connection = connection,
                            onDelete = {
                                onRequest(
                                    MainDesign.Request.DeleteSavedConnection(connection.id),
                                )
                            },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.traffic_advanced_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 24 hourly bars (oldest first); each grows to its share of the peak hour. */
@Composable
private fun HourlyBars(buckets: List<HourTrafficBucket>) {
    val style = animStyle()
    val maxTotal = buckets.maxOfOrNull { it.up + it.down } ?: 0L
    val hourFormat = remember { SimpleDateFormat("HH", Locale.US) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val total = bucket.up + bucket.down
                val target = if (maxTotal > 0L) {
                    (total.toFloat() / maxTotal.toFloat()).coerceIn(0.02f, 1f)
                } else {
                    0.02f
                }
                val fraction by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tuxunProgressSpec(style),
                    label = "hourBar",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((96f * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (total > 0L) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            buckets.forEachIndexed { index, bucket ->
                Box(modifier = Modifier.weight(1f)) {
                    if (index % 6 == 0) {
                        Text(
                            text = hourFormat.format(Date(bucket.hourStartMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** One saved-connection snapshot row with its delete button. */
@Composable
private fun SavedConnectionRow(
    connection: SavedConnection,
    onDelete: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
    val rating = connection.ratingValue()

    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val savedRatingColor = ratingColor(rating)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeFormat.format(Date(connection.savedAtMillis)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Canvas(modifier = Modifier.size(9.dp)) {
                        drawCircle(color = savedRatingColor)
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(ratingTextRes(rating)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ratingColor(rating),
                    )
                }
                if (connection.nodeName.isNotEmpty()) {
                    Text(
                        text = connection.nodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "↑ ${connection.upBytes.toBytesString()}" +
                        "  ↓ ${connection.downBytes.toBytesString()}" +
                        "  ·  " + formatDuration(connection.durationMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (connection.avgDelayMs > 0) {
                    Text(
                        text = stringResource(R.string.quality_avg_delay) +
                            " ${connection.avgDelayMs} ms" +
                            "  ·  " + stringResource(R.string.quality_jitter) +
                            " ${connection.jitterMs} ms" +
                            "  ·  " + stringResource(R.string.quality_fail_rate) +
                            " ${(connection.failRate * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_delete),
                    contentDescription = stringResource(DesignR.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Verdict colour: fixed green/orange for the extremes, theme colours between. */
@Composable
internal fun ratingColor(rating: QualityRating): Color = when (rating) {
    QualityRating.Excellent -> Color(0xFF43A047)
    QualityRating.Stable -> MaterialTheme.colorScheme.primary
    QualityRating.Unstable -> Color(0xFFEF6C00)
    QualityRating.Poor -> MaterialTheme.colorScheme.error
    QualityRating.NoData -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** String resource for a [QualityRating] verdict. */
internal fun ratingTextRes(rating: QualityRating): Int = when (rating) {
    QualityRating.Excellent -> R.string.quality_excellent
    QualityRating.Stable -> R.string.quality_stable
    QualityRating.Unstable -> R.string.quality_unstable
    QualityRating.Poor -> R.string.quality_poor
    QualityRating.NoData -> R.string.quality_no_data
}
