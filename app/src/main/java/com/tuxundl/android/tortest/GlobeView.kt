package com.tuxundl.android.tortest

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** A lit point on the globe: a located circuit hop (exit flagged). */
data class GlobePoint(val lat: Double, val lon: Double, val isExit: Boolean)

/**
 * Rotation state for the globe. Angles in radians; yaw spins around
 * the polar axis, pitch tilts. Velocities (rad/s) drive the fling
 * inertia; [lastInteractMs] gates the idle auto-rotate.
 */
private class GlobeRotation {
    var yaw by mutableFloatStateOf(-1.45f)
    var pitch by mutableFloatStateOf(0.30f)
    var velYaw = 0f
    var velPitch = 0f
    var dragging = false
    var lastInteractMs = 0L
}

private const val PITCH_LIMIT = 1.05f
private const val AUTO_ROTATE_RAD_PER_SEC = 0.10f
private const val IDLE_BEFORE_AUTO_ROTATE_MS = 3000L

/**
 * The centerpiece of the Tor page: a solid globe the user can spin.
 *
 * Orthographic projection of the bundled country rings (unit-sphere
 * coordinates precomputed in [WorldGlobeData]). Per frame the cost
 * is one rotation + projection per point of the rings that survive
 * centroid culling — flat colors only, no shading passes, no text.
 * Hop markers and dashed route arcs are projected onto the sphere
 * the same way (slerp with a slight altitude lift for the arcs);
 * anything on the back hemisphere is hidden.
 */
@Composable
fun GlobeCanvas(
    rings: List<GlobeRing>?,
    points: List<GlobePoint>,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { GlobeRotation() }

    // Exit breathing + arc dash flow: two cheap infinite animations,
    // they only change marker/arc drawing parameters.
    val pulse = rememberInfiniteTransition(label = "globePulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "exitPulseAlpha"
    )
    val pulseScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "exitPulseScale"
    )
    val flow = rememberInfiniteTransition(label = "globeDashFlow")
    val dashPhase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 34f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    // Frame loop: fling inertia with exponential decay, then a slow
    // idle auto-rotate once the user has left the globe alone.
    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        if (rotation.lastInteractMs == 0L) {
            rotation.lastInteractMs = System.currentTimeMillis()
        }
        while (true) {
            withFrameNanos { frameMs ->
                val nowMs = frameMs / 1_000_000L
                if (lastFrameMs != 0L && !rotation.dragging) {
                    val dt = ((nowMs - lastFrameMs) / 1000f).coerceIn(0f, 0.05f)
                    if (rotation.velYaw != 0f || rotation.velPitch != 0f) {
                        rotation.yaw += rotation.velYaw * dt
                        rotation.pitch =
                            (rotation.pitch + rotation.velPitch * dt)
                                .coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
                        val decay = exp(-2.4f * dt)
                        rotation.velYaw *= decay
                        rotation.velPitch *= decay
                        if (kotlin.math.abs(rotation.velYaw) < 0.005f) {
                            rotation.velYaw = 0f
                        }
                        if (kotlin.math.abs(rotation.velPitch) < 0.005f) {
                            rotation.velPitch = 0f
                        }
                        rotation.lastInteractMs = System.currentTimeMillis()
                    } else if (
                        System.currentTimeMillis() - rotation.lastInteractMs >
                        IDLE_BEFORE_AUTO_ROTATE_MS
                    ) {
                        rotation.yaw += AUTO_ROTATE_RAD_PER_SEC * dt
                    }
                }
                lastFrameMs = nowMs
            }
        }
    }

    val landPath = remember { Path() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            var lastEventMs = 0L
            detectDragGestures(
                onDragStart = {
                    rotation.dragging = true
                    rotation.velYaw = 0f
                    rotation.velPitch = 0f
                    lastEventMs = 0L
                    rotation.lastInteractMs = System.currentTimeMillis()
                },
                onDragEnd = { rotation.dragging = false },
                onDragCancel = { rotation.dragging = false },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val sensitivity = 0.0075f
                    rotation.yaw += dragAmount.x * sensitivity
                    rotation.pitch =
                        (rotation.pitch + dragAmount.y * sensitivity)
                            .coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
                    val nowMs = change.uptimeMillis
                    if (lastEventMs != 0L) {
                        val dtSec = ((nowMs - lastEventMs) / 1000f).coerceIn(0.008f, 0.1f)
                        rotation.velYaw = 0.65f * rotation.velYaw +
                            0.35f * (dragAmount.x * sensitivity / dtSec)
                        rotation.velPitch = 0.65f * rotation.velPitch +
                            0.35f * (dragAmount.y * sensitivity / dtSec)
                    }
                    lastEventMs = nowMs
                    rotation.lastInteractMs = System.currentTimeMillis()
                }
            )
        }
    ) {
        val radius = size.minDimension / 2f
        val cx = center.x
        val cy = center.y

        // Ocean: one flat radial gradient — reads as a lit sphere.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF5E35B1), Color(0xFF3A1470), Color(0xFF1D0545)),
                center = Offset(cx - radius * 0.38f, cy - radius * 0.38f),
                radius = radius * 1.55f
            ),
            radius = radius,
            center = center
        )

        val cosY = cos(rotation.yaw)
        val sinY = sin(rotation.yaw)
        val cosP = cos(rotation.pitch)
        val sinP = sin(rotation.pitch)

        // Rotate + project a unit-sphere point into screen space;
        // returns (screenX, screenY, rotatedZ) via the scratch array.
        val scratch = FloatArray(3)
        fun project(x: Float, y: Float, z: Float) {
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY
            val y2 = y * cosP - z1 * sinP
            val z2 = y * sinP + z1 * cosP
            scratch[0] = cx + radius * x1
            scratch[1] = cy - radius * y2
            scratch[2] = z2
        }

        if (rings != null) {
            landPath.reset()
            for (ring in rings) {
                // Cheap whole-ring cull: rotate the centroid (z only);
                // if even the ring's nearest limb point is behind
                // the sphere, skip the ring entirely.
                val rz1 = -ring.cx * sinY + ring.cz * cosY
                val rz2 = ring.cy * sinP + rz1 * cosP
                if (rz2 <= ring.cullZ) continue

                val coords = ring.coords
                val count = coords.size / 3
                var drawing = false
                var prevZ = -1f
                var prevSx = 0f
                var prevSy = 0f
                for (p in 0 until count) {
                    project(coords[p * 3], coords[p * 3 + 1], coords[p * 3 + 2])
                    val sx = scratch[0]
                    val sy = scratch[1]
                    val z2 = scratch[2]
                    val front = z2 > 0f
                    if (p > 0) {
                        val prevFront = prevZ > 0f
                        if (front && prevFront) {
                            landPath.lineTo(sx, sy)
                        } else if (front && !prevFront) {
                            // Entering the front hemisphere: start at
                            // the limb crossing.
                            val t = prevZ / (prevZ - z2)
                            val ix = prevSx + (sx - prevSx) * t
                            val iy = prevSy + (sy - prevSy) * t
                            if (drawing) {
                                landPath.lineTo(ix, iy)
                            } else {
                                landPath.moveTo(ix, iy)
                                drawing = true
                            }
                            landPath.lineTo(sx, sy)
                        } else if (!front && prevFront) {
                            val t = prevZ / (prevZ - z2)
                            val ix = prevSx + (sx - prevSx) * t
                            val iy = prevSy + (sy - prevSy) * t
                            landPath.lineTo(ix, iy)
                            drawing = false
                        }
                    } else if (front) {
                        landPath.moveTo(sx, sy)
                        drawing = true
                    }
                    prevZ = z2
                    prevSx = sx
                    prevSy = sy
                }
                if (drawing) landPath.close()
            }
            val landFill = if (connected) {
                Color(0xFF9575CD).copy(alpha = 0.95f)
            } else {
                Color(0xFF9575CD).copy(alpha = 0.55f)
            }
            drawPath(landPath, color = landFill)
            drawPath(
                landPath,
                color = Color.White.copy(alpha = if (connected) 0.38f else 0.20f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Route arcs + hop markers, projected onto the sphere.
        if (points.isNotEmpty()) {
            val vectors = points.map { point ->
                val lon = Math.toRadians(point.lon)
                val lat = Math.toRadians(point.lat)
                val cosLat = cos(lat)
                floatArrayOf(
                    (cosLat * sin(lon)).toFloat(),
                    sin(lat).toFloat(),
                    (cosLat * cos(lon)).toFloat()
                )
            }
            if (vectors.size >= 2) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), dashPhase)
                val arcPath = Path()
                for (i in 0 until vectors.size - 1) {
                    val a = vectors[i]
                    val b = vectors[i + 1]
                    val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2])
                        .coerceIn(-1f, 1f)
                    val omega = kotlin.math.acos(dot)
                    if (omega < 0.01f) continue
                    val sinOmega = sin(omega)
                    val steps = 24
                    var drawing = false
                    for (s in 0..steps) {
                        val t = s / steps.toFloat()
                        val ka = sin((1f - t) * omega) / sinOmega
                        val kb = sin(t * omega) / sinOmega
                        var vx = a[0] * ka + b[0] * kb
                        var vy = a[1] * ka + b[1] * kb
                        var vz = a[2] * ka + b[2] * kb
                        val lift = 1f + 0.16f * sin(t * Math.PI.toFloat()) *
                            (omega / Math.PI.toFloat()).coerceAtMost(1f)
                        vx *= lift
                        vy *= lift
                        vz *= lift
                        project(vx, vy, vz)
                        val z2 = scratch[2]
                        // Points lifted above the surface stay
                        // visible a touch past the limb.
                        if (z2 > -0.02f) {
                            if (drawing) {
                                arcPath.lineTo(scratch[0], scratch[1])
                            } else {
                                arcPath.moveTo(scratch[0], scratch[1])
                                drawing = true
                            }
                        } else {
                            drawing = false
                        }
                    }
                }
                drawPath(
                    arcPath,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = dash)
                )
            }
            for (index in vectors.indices) {
                val v = vectors[index]
                project(v[0], v[1], v[2])
                if (scratch[2] <= 0.02f) continue
                val isExit = points[index].isExit
                val c = Offset(scratch[0], scratch[1])
                val glowRadius = if (isExit) {
                    24.dp.toPx() * pulseScale
                } else {
                    15.dp.toPx()
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color(0xFFB388FF).copy(
                                alpha = if (isExit) pulseAlpha else 0.7f
                            ),
                            Color.Transparent
                        ),
                        center = c,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = c
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isExit) 4.5.dp.toPx() else 3.2.dp.toPx(),
                    center = c
                )
            }
        }

        // Rim.
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = radius - 1.dp.toPx(),
            center = center,
            style = Stroke(1.2.dp.toPx())
        )
    }
}
