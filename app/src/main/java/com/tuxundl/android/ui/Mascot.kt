package com.tuxundl.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.model.AnimationStyle
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The TuxunDL mascot: a minimal Android-style robot head drawn on a
 * Canvas — rounded head, two antennae, dot eyes. The right antenna ends
 * in a glowing "node" dot (the proxy node) instead of a plain ball;
 * that is the mascot's own little twist.
 *
 * [animated] lets the antennae sway gently and the eyes blink now and
 * then; the Reduced animation style (or [animated] = false) keeps it
 * perfectly still. [squish] (0..1) is driven by the easter egg's
 * "pat my head" button: the head flattens, the eyes turn into happy
 * arcs and blush marks fade in.
 */
@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    eyeColor: Color = Color.White,
    animated: Boolean = true,
    squish: Float = 0f,
) {
    val style = animStyle()
    val motion = animated && style != AnimationStyle.Reduced

    // Antenna sway: a slow -1..1 oscillation, frozen when motion is off.
    val swayTransition = rememberInfiniteTransition(label = "mascotSway")
    val sway by swayTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mascotSwayValue",
    )

    // Blinking: eyes stay open, snap shut for a beat every few seconds.
    val blink = remember { Animatable(1f) }
    LaunchedEffect(motion) {
        if (!motion) {
            blink.snapTo(1f)
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(2400L, 4400L))
            blink.animateTo(0.08f, tween(durationMillis = 60))
            blink.animateTo(1f, tween(durationMillis = 130))
        }
    }

    val swayValue = if (motion) sway else 0f
    val squishClamped = squish.coerceIn(0f, 1f)
    val happyEyes = squishClamped > 0.45f

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = 1f + 0.12f * squishClamped
                scaleY = 1f - 0.16f * squishClamped
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
    ) {
        val s = this.size.minDimension
        val headColor = color

        val headTop = s * 0.22f
        val headLeft = s * 0.08f
        val headWidth = s * 0.84f
        val headHeight = s * 0.76f
        val headCorner = s * 0.20f

        // Antennae (drawn first, the head overlaps their bases).
        val swayOffset = swayValue * s * 0.03f
        val leftBase = Offset(s * 0.32f, headTop + s * 0.02f)
        val rightBase = Offset(s * 0.68f, headTop + s * 0.02f)
        val leftTip = Offset(s * 0.19f + swayOffset, s * 0.07f)
        val rightTip = Offset(s * 0.81f + swayOffset, s * 0.07f)
        val antennaWidth = s * 0.045f

        drawLine(
            color = headColor,
            start = leftBase,
            end = leftTip,
            strokeWidth = antennaWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = headColor,
            start = rightBase,
            end = rightTip,
            strokeWidth = antennaWidth,
            cap = StrokeCap.Round,
        )
        // Left tip: a plain ball.
        drawCircle(color = headColor, radius = s * 0.045f, center = leftTip)
        // Right tip: the node dot — a glowing primary point.
        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = s * 0.115f,
            center = rightTip,
        )
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = s * 0.078f,
            center = rightTip,
        )
        drawCircle(color = color, radius = s * 0.052f, center = rightTip)
        drawCircle(color = Color.White, radius = s * 0.020f, center = rightTip)

        // Head.
        drawRoundRect(
            color = headColor,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headCorner, headCorner),
        )

        // Eyes.
        val eyeY = headTop + headHeight * 0.42f
        val leftEye = Offset(s * 0.36f, eyeY)
        val rightEye = Offset(s * 0.64f, eyeY)
        val eyeRadius = s * 0.055f

        if (happyEyes) {
            // Happy closed eyes: two ∩ arcs.
            val arcSize = Size(eyeRadius * 2.4f, eyeRadius * 2.0f)
            drawArc(
                color = eyeColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEye.x - arcSize.width / 2, leftEye.y - arcSize.height / 2),
                size = arcSize,
                style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
            )
            drawArc(
                color = eyeColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEye.x - arcSize.width / 2, rightEye.y - arcSize.height / 2),
                size = arcSize,
                style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
            )
        } else {
            val eyeScaleY = blink.value.coerceIn(0.05f, 1f)
            drawOval(
                color = eyeColor,
                topLeft = Offset(leftEye.x - eyeRadius, leftEye.y - eyeRadius * eyeScaleY),
                size = Size(eyeRadius * 2, eyeRadius * 2 * eyeScaleY),
            )
            drawOval(
                color = eyeColor,
                topLeft = Offset(rightEye.x - eyeRadius, rightEye.y - eyeRadius * eyeScaleY),
                size = Size(eyeRadius * 2, eyeRadius * 2 * eyeScaleY),
            )
        }

        // Blush marks while being patted.
        if (squishClamped > 0.05f) {
            val blushColor = Color(0xFFF48FB1).copy(alpha = 0.55f * squishClamped)
            val blushSize = Size(s * 0.13f, s * 0.07f)
            val blushY = headTop + headHeight * 0.62f
            drawOval(
                color = blushColor,
                topLeft = Offset(s * 0.16f, blushY),
                size = blushSize,
            )
            drawOval(
                color = blushColor,
                topLeft = Offset(s * 0.71f, blushY),
                size = blushSize,
            )
        }
    }
}
