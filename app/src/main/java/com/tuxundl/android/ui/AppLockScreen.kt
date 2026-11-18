package com.tuxundl.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.util.AppLock
import com.tuxundl.android.util.AppLockManager
import com.tuxundl.android.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-screen unlock gate (1.5.0), in the app's own design language:
 * mascot, themed colours and the shared pattern / PIN widgets. A
 * wrong attempt buzzes, shakes the input and explains itself; the
 * right one lets [AppLockManager] stand down and finishes the host
 * activity. Nothing behind this screen is composed until it opens.
 */
@Composable
fun AppLockScreen(
    type: Int,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val uiStore = remember { UiStore(context) }
    val scope = rememberCoroutineScope()
    val style = animStyle()

    var error by remember { mutableStateOf(false) }
    var errorTick by remember { mutableStateOf(0) }
    var pin by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var patternReset by remember { mutableStateOf(0) }

    val shake = remember { Animatable(0f) }

    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(-12f, tween(durationMillis = 50))
        shake.animateTo(12f, tween(durationMillis = 70))
        shake.animateTo(-7f, tween(durationMillis = 60))
        shake.animateTo(0f, tween(durationMillis = 50))
    }

    fun success() {
        Haptics.buzz(context, uiStore, Haptics.Kind.Success)
        AppLockManager.onUnlocked()
        onUnlocked()
    }

    fun fail() {
        Haptics.buzz(context, uiStore, Haptics.Kind.Disconnect)
        error = true
        errorTick++
        scope.launch {
            delay(500)
            error = false
            pin = ""
            password = ""
            patternReset++
        }
    }

    fun trySecret(secret: String) {
        if (AppLock.verify(uiStore, secret)) {
            success()
        } else {
            fail()
        }
    }

    val promptRes = when (type) {
        AppLock.TYPE_PATTERN -> R.string.app_lock_prompt_pattern
        AppLock.TYPE_PASSWORD -> R.string.app_lock_prompt_password
        else -> R.string.app_lock_prompt_pin
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .tuxunPageEnter()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Mascot(size = 84.dp, animated = false)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_lock_locked_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Prompt and error share one slot so the layout never jumps.
        AnimatedContent(
            targetState = error,
            transitionSpec = {
                fadeIn(tuxunFadeSpec(style, entering = true)) togetherWith
                    fadeOut(tuxunFadeSpec(style, entering = false))
            },
            label = "lockPrompt",
        ) { isError ->
            Text(
                text = if (isError) {
                    stringResource(R.string.app_lock_wrong)
                } else {
                    stringResource(promptRes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier.offset {
                IntOffset(shake.value.roundToInt(), 0)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (type) {
                AppLock.TYPE_PATTERN -> {
                    PatternPad(
                        onPatternComplete = { dots ->
                            if (dots.size >= 4) {
                                trySecret(dots.joinToString(","))
                            } else {
                                fail()
                            }
                        },
                        onDotAdded = {
                            Haptics.buzz(context, uiStore, Haptics.Kind.Tap)
                        },
                        error = error,
                        resetSignal = patternReset,
                        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    )
                }

                AppLock.TYPE_PASSWORD -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.isNotEmpty()) trySecret(password)
                            },
                        ),
                        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (password.isNotEmpty()) trySecret(password)
                        },
                    ) {
                        Text(text = stringResource(R.string.app_lock_unlock))
                    }
                }

                else -> {
                    PinDots(count = pin.length, error = error)
                    PinPad(
                        onDigit = { digit ->
                            if (pin.length >= 6 || error) return@PinPad

                            val next = pin + digit.toString()
                            pin = next
                            Haptics.buzz(context, uiStore, Haptics.Kind.Tap)

                            // Codes are 4–6 digits: check every
                            // length from 4 up, so the right code
                            // opens the moment it is complete.
                            if (next.length >= 4) {
                                if (AppLock.verify(uiStore, next)) {
                                    success()
                                } else if (next.length == 6) {
                                    fail()
                                }
                            }
                        },
                        onBackspace = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                                Haptics.buzz(context, uiStore, Haptics.Kind.Tap)
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
