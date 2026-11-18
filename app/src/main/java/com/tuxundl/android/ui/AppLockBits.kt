package com.tuxundl.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Shared input widgets for the app lock (1.5.0): the 3×3 pattern pad
 * and the numeric keypad, used by both the unlock screen and the
 * setup flow in settings so the two feel identical.
 */

/**
 * Nine-dot pattern pad. Reports the visited dot indices when the
 * finger lifts; jumping over an unvisited middle dot adds it first,
 * like the system pattern lock. Bump [resetSignal] to clear the
 * drawing (e.g. after a failed attempt has been shown).
 */
@Composable
fun PatternPad(
    onPatternComplete: (List<Int>) -> Unit,
    onDotAdded: () -> Unit,
    error: Boolean,
    resetSignal: Int,
    modifier: Modifier = Modifier,
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var sizePx by remember { mutableStateOf(0) }

    LaunchedEffect(resetSignal) {
        selected.clear()
        finger = null
    }

    val lineColor = if (error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun centerOf(index: Int, size: Int): Offset {
        val cell = size / 3f
        return Offset(
            x = (index % 3 + 0.5f) * cell,
            y = (index / 3 + 0.5f) * cell,
        )
    }

    fun tryAdd(position: Offset) {
        if (sizePx == 0) return

        val cell = sizePx / 3f
        val hitRadius = cell * 0.42f
        var best = -1
        var bestDist = Float.MAX_VALUE

        for (index in 0 until 9) {
            if (index in selected) continue
            val dist = (centerOf(index, sizePx) - position).getDistance()
            if (dist < bestDist) {
                bestDist = dist
                best = index
            }
        }
        if (best < 0 || bestDist > hitRadius) return

        // Leaping over the middle dot of a straight two-step move
        // selects it first (rows, columns and diagonals alike).
        val last = selected.lastOrNull()
        if (last != null) {
            val dRow = abs(best / 3 - last / 3)
            val dCol = abs(best % 3 - last % 3)
            if ((dRow == 2 && dCol == 0) || (dRow == 0 && dCol == 2) ||
                (dRow == 2 && dCol == 2)
            ) {
                val middle = ((best / 3 + last / 3) / 2) * 3 +
                    (best % 3 + last % 3) / 2
                if (middle !in selected) {
                    selected += middle
                    onDotAdded()
                }
            }
        }

        selected += best
        onDotAdded()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .onSizeChanged { sizePx = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selected.clear()
                    finger = down.position
                    tryAdd(down.position)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull {
                            it.id == down.id
                        } ?: break
                        if (change.pressed) {
                            finger = change.position
                            tryAdd(change.position)
                        }
                    } while (event.changes.any { it.pressed })

                    finger = null
                    if (selected.isNotEmpty()) {
                        onPatternComplete(selected.toList())
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (sizePx == 0) return@Canvas

            // Trail through the visited dots, then out to the finger.
            if (selected.isNotEmpty()) {
                val points = selected.map { centerOf(it, sizePx) }
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.75f),
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                val tip = finger
                if (tip != null) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.75f),
                        start = points.last(),
                        end = tip,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            for (index in 0 until 9) {
                val center = centerOf(index, sizePx)
                val active = index in selected
                drawCircle(
                    color = if (active) lineColor else ringColor,
                    radius = if (active) 13.dp.toPx() else 11.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                    ),
                )
                drawCircle(
                    color = if (active) lineColor else dotColor,
                    radius = if (active) 6.dp.toPx() else 4.dp.toPx(),
                    center = center,
                )
            }
        }
    }
}

/** PIN progress dots: one slot per digit of a 4–6 digit code. */
@Composable
fun PinDots(
    count: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
    maxDots: Int = 6,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until maxDots) {
            val filled = index < count
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !filled -> MaterialTheme.colorScheme.outlineVariant
                            error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ),
            )
        }
    }
}

/** Round-key numeric pad: 1–9, then a blank, 0 and backspace. */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit ->
                    PinKey(label = digit.toString()) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(modifier = Modifier.size(62.dp))
            PinKey(label = "0") { onDigit(0) }
            PinKey(label = "⌫") { onBackspace() }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
