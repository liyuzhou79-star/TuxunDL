package com.tuxundl.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tuxundl.android.R
import com.tuxundl.android.design.model.AnimationStyle
import com.tuxundl.android.design.util.toBytesString
import kotlinx.coroutines.launch

/**
 * The mascot easter egg: tapping the home-screen mascot eight times in
 * three seconds pops this dialog in with a spring. It shows the big
 * mascot, three lifetime stats (traffic carried, time connected, days
 * used — all fed by MainActivity into [MainUiState]) and a "pat my
 * head" button: the mascot squishes, turns happy-eyed and, from the
 * third pat on, answers with a cheeky line.
 */
@Composable
fun EasterEggDialog(
    state: MainUiState,
    onClose: () -> Unit,
) {
    val style = animStyle()

    // Entrance: spring scale + fade on the dialog surface (fade only
    // in the Reduced style).
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(
            targetValue = 1f,
            animationSpec = when (style) {
                AnimationStyle.Spring -> spring(dampingRatio = 0.55f, stiffness = 380f)
                AnimationStyle.Soft -> spring(dampingRatio = 0.85f, stiffness = 480f)
                AnimationStyle.Reduced -> tween(durationMillis = 140)
            },
        )
    }

    val squish = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var patCount by remember { mutableIntStateOf(0) }

    val patLine = when {
        patCount <= 0 -> null
        patCount < 3 -> stringResource(R.string.egg_pat_line_1)
        patCount % 2 == 1 -> stringResource(R.string.egg_pat_line_2)
        else -> stringResource(R.string.egg_pat_line_3)
    }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = enter.value.coerceIn(0f, 1f)
                    alpha = p
                    val scale = if (style == AnimationStyle.Reduced) {
                        1f
                    } else {
                        0.85f + 0.15f * p
                    }
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Mascot(
                    size = 120.dp,
                    squish = squish.value,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.egg_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Three lifetime stat bricks.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EggStat(
                        value = (state.lifetimeUp + state.lifetimeDown).toBytesString(),
                        label = stringResource(R.string.egg_stat_traffic),
                        modifier = Modifier.weight(1f),
                    )
                    EggStat(
                        value = formatDuration(state.lifetimeConnectedSeconds * 1000L),
                        label = stringResource(R.string.egg_stat_duration),
                        modifier = Modifier.weight(1f),
                    )
                    EggStat(
                        value = state.daysUsed.toString(),
                        label = stringResource(R.string.egg_stat_days),
                        modifier = Modifier.weight(1f),
                    )
                }

                // The pat response line (empty until the first pat).
                AnimatedContent(
                    targetState = patLine,
                    transitionSpec = {
                        fadeIn(tween(tuxunFadeInMs(style))) togetherWith
                            fadeOut(tween(tuxunFadeOutMs(style)))
                    },
                    label = "patLine",
                ) { line ->
                    Text(
                        text = line ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            patCount++
                            if (style != AnimationStyle.Reduced) {
                                scope.launch {
                                    squish.snapTo(0f)
                                    squish.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 90),
                                    )
                                    squish.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tuxunSnapSpec<Float>(style),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.egg_pat))
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.egg_got_it))
                    }
                }
            }
        }
    }
}

/** One value-over-label stat brick of the easter egg. */
@Composable
private fun EggStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
