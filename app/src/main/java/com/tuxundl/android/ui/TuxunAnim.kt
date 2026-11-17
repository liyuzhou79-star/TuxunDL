package com.tuxundl.android.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.model.AnimationStyle
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole-app animation style, provided by TuxunDLTheme from UiStore.
 * Every screen reads it through the spec factories below, so the three
 * Appearance choices (Spring / Soft / Reduced) reshape all motion at
 * once, while each element still keeps its own tuned parameters.
 */
val LocalAnimStyle = compositionLocalOf { AnimationStyle.Spring }

/** The current animation style (composable read). */
@Composable
fun animStyle(): AnimationStyle = LocalAnimStyle.current

// ---------------------------------------------------------------------------
// Shared spec factories. Each element family has its own numbers; the
// style only scales how bouncy / fast / plain the family feels.
// ---------------------------------------------------------------------------

/** Expand / shrink / content-size spring (cards, group bodies). */
fun <T> tuxunSizeSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Spring -> spring(dampingRatio = 0.55f, stiffness = 230f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.9f, stiffness = 480f)
    AnimationStyle.Reduced -> tween(durationMillis = 120)
}

/** Snappy spring for small elements (chevrons, segmented indicator). */
fun <T> tuxunSnapSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Spring -> spring(dampingRatio = 0.42f, stiffness = 640f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.8f, stiffness = 720f)
    AnimationStyle.Reduced -> tween(durationMillis = 100)
}

/** Press-and-release scale spring (buttons, cards, the power circle). */
fun <T> tuxunPressSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Spring -> spring(dampingRatio = 0.48f, stiffness = 520f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.85f, stiffness = 620f)
    AnimationStyle.Reduced -> tween(durationMillis = 80)
}

/** Progress / bar fill spring (progress indicators, weekly bars). */
fun <T> tuxunProgressSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Spring -> spring(dampingRatio = 0.68f, stiffness = 300f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.9f, stiffness = 460f)
    AnimationStyle.Reduced -> tween(durationMillis = 120)
}

/** Lazy-list placement spring (animateItem). */
fun <T> tuxunPlacementSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    AnimationStyle.Spring -> spring(dampingRatio = 0.6f, stiffness = 320f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.88f, stiffness = 480f)
    AnimationStyle.Reduced -> tween(durationMillis = 120)
}

/** Colour cross-fade: a tween in every style, only the length changes. */
fun tuxunColorSpec(style: AnimationStyle): AnimationSpec<Color> = tween(
    durationMillis = when (style) {
        AnimationStyle.Spring -> 380
        AnimationStyle.Soft -> 260
        AnimationStyle.Reduced -> 140
    },
    easing = FastOutSlowInEasing,
)

/** Fade-in length per style. */
fun tuxunFadeInMs(style: AnimationStyle): Int = when (style) {
    AnimationStyle.Spring -> 260
    AnimationStyle.Soft -> 190
    AnimationStyle.Reduced -> 130
}

/** Fade-out length per style. */
fun tuxunFadeOutMs(style: AnimationStyle): Int = when (style) {
    AnimationStyle.Spring -> 180
    AnimationStyle.Soft -> 140
    AnimationStyle.Reduced -> 100
}

@Composable
fun tuxunEnterTransition(): EnterTransition =
    expandVertically(animationSpec = tuxunSizeSpec(animStyle())) +
        fadeIn(
            animationSpec = tween(
                durationMillis = tuxunFadeInMs(animStyle()),
                easing = FastOutSlowInEasing,
            ),
        )

@Composable
fun tuxunExitTransition(): ExitTransition =
    shrinkVertically(animationSpec = tuxunSizeSpec(animStyle())) +
        fadeOut(
            animationSpec = tween(
                durationMillis = tuxunFadeOutMs(animStyle()),
                easing = FastOutSlowInEasing,
            ),
        )

@Composable
fun tuxunFadeEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = tuxunFadeInMs(animStyle()),
        easing = FastOutSlowInEasing,
    ),
)

@Composable
fun tuxunFadeExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = tuxunFadeOutMs(animStyle()),
        easing = FastOutSlowInEasing,
    ),
)

// ---------------------------------------------------------------------------
// One-shot entrance modifiers. Each plays once when the element first
// enters composition; the style scales distance, length and bounce.
// ---------------------------------------------------------------------------

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.2f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/**
 * Whole-page entrance: content fades in while rising a few dp and
 * settling with a hint of overshoot (Spring), a plain rise (Soft) or a
 * fade only (Reduced). Apply to a screen's root content container.
 */
fun Modifier.tuxunPageEnter(): Modifier = composed {
    val style = LocalAnimStyle.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = when (style) {
                    AnimationStyle.Spring -> 380
                    AnimationStyle.Soft -> 240
                    AnimationStyle.Reduced -> 140
                },
                easing = LinearEasing,
            ),
        )
    }

    val distance = when (style) {
        AnimationStyle.Spring -> 28.dp
        AnimationStyle.Soft -> 14.dp
        AnimationStyle.Reduced -> 0.dp
    }
    val eased = when (style) {
        AnimationStyle.Spring -> easeOutBack(progress.value)
        AnimationStyle.Soft -> easeOutCubic(progress.value)
        AnimationStyle.Reduced -> progress.value
    }

    this
        .alpha(progress.value.coerceIn(0f, 1f))
        .offset {
            IntOffset(0, ((1f - eased) * distance.toPx()).roundToInt())
        }
}

/**
 * First-appearance pop for home tiles: scales up from 0.9 while fading
 * in. Reduced keeps only the fade.
 */
fun Modifier.tuxunAppear(): Modifier = composed {
    val style = LocalAnimStyle.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = when (style) {
                AnimationStyle.Spring -> spring(dampingRatio = 0.5f, stiffness = 420f)
                AnimationStyle.Soft -> spring(dampingRatio = 0.85f, stiffness = 520f)
                AnimationStyle.Reduced -> tween(durationMillis = 120)
            },
        )
    }

    val scale = if (style == AnimationStyle.Reduced) {
        1f
    } else {
        0.9f + 0.1f * progress.value
    }

    this
        .alpha(progress.value.coerceIn(0f, 1f))
        .scale(scale)
}

/**
 * Staggered entrance for rows revealed together (proxy group children):
 * row [index] waits a little longer than the previous one (capped, so
 * long lists never feel slow) and drifts up while fading in. Reduced
 * drops the wait and the drift, keeping a short fade.
 */
fun Modifier.tuxunStagger(index: Int): Modifier = composed {
    val style = LocalAnimStyle.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (style != AnimationStyle.Reduced) {
            delay(min(index * 30, 240).toLong())
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = tuxunFadeInMs(style),
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val drift = if (style == AnimationStyle.Reduced) 0.dp else 10.dp

    this
        .alpha(progress.value.coerceIn(0f, 1f))
        .offset {
            IntOffset(0, ((1f - progress.value) * drift.toPx()).roundToInt())
        }
}

/** Fade in once when the element first composes (log rows). */
fun Modifier.tuxunFadeInOnce(): Modifier = composed {
    val style = LocalAnimStyle.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = tuxunFadeInMs(style),
                easing = FastOutSlowInEasing,
            ),
        )
    }

    this.alpha(progress.value.coerceIn(0f, 1f))
}

/**
 * A quick scale pulse whenever [trigger] turns true (filter chips when
 * selected). Soft halves the pulse; Reduced never pulses.
 */
fun Modifier.tuxunPopOnSelect(trigger: Boolean, peak: Float = 1.12f): Modifier = composed {
    val style = LocalAnimStyle.current
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(trigger) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (trigger && style != AnimationStyle.Reduced) {
            val effectivePeak = if (style == AnimationStyle.Soft) {
                1f + (peak - 1f) * 0.5f
            } else {
                peak
            }
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = effectivePeak,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tuxunSnapSpec(style),
            )
        }
    }

    this.scale(scale.value)
}

/** Spring stiffness for the sliding segmented indicator. */
fun <T> tuxunSegmentedSpec(style: AnimationStyle): FiniteAnimationSpec<T> = when (style) {
    // A touch of overshoot so the pill visibly slides and settles.
    AnimationStyle.Spring -> spring(dampingRatio = 0.55f, stiffness = 380f)
    AnimationStyle.Soft -> spring(dampingRatio = 0.92f, stiffness = 560f)
    AnimationStyle.Reduced -> tween(durationMillis = 120)
}

/** Plain fade spec for lazy-list item fade in/out (animateItem). */
fun <T> tuxunFadeSpec(style: AnimationStyle, entering: Boolean): FiniteAnimationSpec<T> = tween(
    durationMillis = if (entering) tuxunFadeInMs(style) else tuxunFadeOutMs(style),
    easing = FastOutSlowInEasing,
)

/**
 * Press feedback shared by buttons and cards: while [interactionSource]
 * reports a press the element shrinks to [pressedScale] and springs
 * back on release. Each caller tunes its own depth (big circle 0.96,
 * buttons 0.97, cards 0.98). Reduced never scales.
 */
@Composable
fun Modifier.tuxunPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val style = LocalAnimStyle.current
    val pressed by interactionSource.collectIsPressedAsState()
    val target = when {
        style == AnimationStyle.Reduced -> 1f
        pressed -> pressedScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tuxunPressSpec(style),
        label = "tuxunPressScale",
    )

    return this.scale(scale)
}
