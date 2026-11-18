package com.tuxundl.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.util.Haptics
import kotlin.math.roundToInt

/**
 * Haptic tuning block shared by onboarding step 4 and the settings
 * dialog (1.4.0): the global switch, four strength presets
 * (light / medium / strong / custom) and — for the custom preset —
 * an amplitude slider. Every pick previews immediately, so tuning
 * is felt rather than guessed. Works directly on [UiStore], the
 * same pattern the onboarding steps already use.
 */
@Composable
fun HapticsTuning(
    store: UiStore,
    modifier: Modifier = Modifier,
    showSwitch: Boolean = true,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(store.hapticsEnabled) }
    var strength by remember { mutableStateOf(store.hapticsStrength) }
    var custom by remember {
        mutableStateOf(store.hapticsCustomAmplitude.toFloat())
    }

    fun preview() {
        if (!enabled) return

        if (strength == Haptics.CUSTOM_STRENGTH) {
            Haptics.buzzAmplitude(
                context,
                custom.roundToInt(),
                Haptics.Kind.Tap,
            )
        } else {
            Haptics.buzz(context, strength, Haptics.Kind.Tap)
        }
    }

    Column(modifier = modifier) {
        if (showSwitch) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.haptics),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TuxunSwitch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        store.hapticsEnabled = it
                        // Switching on previews the current feel.
                        if (it) preview()
                    },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.haptics_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = stringResource(R.string.haptics_strength),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TuxunSegmentedRow(
            options = listOf(
                stringResource(R.string.haptics_light),
                stringResource(R.string.haptics_medium),
                stringResource(R.string.haptics_strong),
                stringResource(R.string.haptics_custom),
            ),
            selectedIndex = strength,
            onSelect = { index ->
                strength = index
                store.hapticsStrength = index
                // Preview the picked strength right away.
                if (enabled) {
                    if (index == Haptics.CUSTOM_STRENGTH) {
                        Haptics.buzzAmplitude(
                            context,
                            custom.roundToInt(),
                            Haptics.Kind.Tap,
                        )
                    } else {
                        Haptics.buzz(context, index, Haptics.Kind.Tap)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = strength == Haptics.CUSTOM_STRENGTH,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.haptics_custom),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${custom.roundToInt() * 100 / 255}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = custom,
                    onValueChange = { custom = it },
                    onValueChangeFinished = {
                        val amp = custom.roundToInt().coerceIn(10, 255)
                        custom = amp.toFloat()
                        store.hapticsCustomAmplitude = amp
                        if (enabled) {
                            Haptics.buzzAmplitude(
                                context,
                                amp,
                                Haptics.Kind.Tap,
                            )
                        }
                    },
                    valueRange = 10f..255f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
