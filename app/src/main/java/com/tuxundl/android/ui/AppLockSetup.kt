package com.tuxundl.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.util.AppLock
import com.tuxundl.android.util.AppLockManager
import com.tuxundl.android.util.Haptics
import kotlin.math.roundToInt
import com.tuxundl.android.design.R as DesignR

/** Steps of the app-lock setup/manage flow. */
private enum class LockStep {
    Home, ChooseType, EnterNew, ConfirmNew, VerifyForChange, VerifyForDisable,
}

/**
 * App-lock setup and management (1.5.0), opened from the privacy
 * section in settings. Setting a secret takes two matching entries;
 * changing or removing the lock first asks for the current secret.
 * The red warning is shown before anything is set: a forgotten
 * secret can only be recovered by clearing the app data.
 */
@Composable
fun AppLockSetupDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val uiStore = remember { UiStore(context) }

    var configured by remember {
        mutableStateOf(AppLock.isConfigured(uiStore))
    }
    var step by remember { mutableStateOf(LockStep.Home) }
    var pendingType by remember { mutableStateOf(uiStore.appLockType) }
    var firstSecret by remember { mutableStateOf("") }
    var relockMinutes by remember {
        mutableStateOf(uiStore.appLockRelockMinutes)
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    var errorTick by remember { mutableStateOf(0) }
    var entryReset by remember { mutableStateOf(0) }

    val shake = remember { Animatable(0f) }

    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(-10f, tween(durationMillis = 50))
        shake.animateTo(10f, tween(durationMillis = 70))
        shake.animateTo(-6f, tween(durationMillis = 60))
        shake.animateTo(0f, tween(durationMillis = 50))
    }

    fun buzz(kind: Haptics.Kind) = Haptics.buzz(context, uiStore, kind)

    fun fail(message: String) {
        errorText = message
        errorTick++
        entryReset++
        buzz(Haptics.Kind.Disconnect)
    }

    fun go(target: LockStep) {
        step = target
        errorText = null
        entryReset++
    }

    val wrongText = stringResource(R.string.app_lock_wrong)
    val mismatchText = stringResource(R.string.app_lock_mismatch)
    val passwordRuleText = stringResource(R.string.app_lock_password_rule)

    fun passwordValid(secret: String): Boolean =
        secret.length >= 4 &&
            secret.any { it.isLetter() } &&
            secret.any { it.isDigit() }

    /** A finished entry from the shared secret-input widget. */
    fun onSecretEntered(secret: String) {
        when (step) {
            LockStep.EnterNew -> {
                if (pendingType == AppLock.TYPE_PASSWORD &&
                    !passwordValid(secret)
                ) {
                    fail(passwordRuleText)
                    return
                }
                firstSecret = secret
                go(LockStep.ConfirmNew)
            }

            LockStep.ConfirmNew -> {
                if (secret == firstSecret) {
                    AppLock.setSecret(uiStore, pendingType, secret)
                    AppLockManager.onSecretChanged()
                    AppLockManager.refreshSecureFlags()
                    configured = true
                    buzz(Haptics.Kind.Success)
                    onChanged()
                    onDismiss()
                } else {
                    firstSecret = ""
                    go(LockStep.EnterNew)
                    fail(mismatchText)
                }
            }

            LockStep.VerifyForChange -> {
                if (AppLock.verify(uiStore, secret)) {
                    buzz(Haptics.Kind.Success)
                    go(LockStep.ChooseType)
                } else {
                    fail(wrongText)
                }
            }

            LockStep.VerifyForDisable -> {
                if (AppLock.verify(uiStore, secret)) {
                    AppLock.clear(uiStore)
                    AppLockManager.onLockDisabled()
                    AppLockManager.refreshSecureFlags()
                    configured = false
                    buzz(Haptics.Kind.Success)
                    onChanged()
                    onDismiss()
                } else {
                    fail(wrongText)
                }
            }

            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_lock),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(
                                DesignR.drawable.ic_baseline_close,
                            ),
                            contentDescription = null,
                        )
                    }
                }

                // The warning the user asked for, in red, before any
                // secret exists: there is no back door.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.app_lock_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                MaterialTheme.colorScheme.error
                                    .copy(alpha = 0.08f),
                            )
                            .padding(12.dp),
                    )
                }

                when (step) {
                    LockStep.Home -> {
                        if (configured) {
                            Text(
                                text = stringResource(
                                    R.string.app_lock_current,
                                    stringResource(
                                        when (uiStore.appLockType) {
                                            AppLock.TYPE_PATTERN ->
                                                R.string.app_lock_type_pattern
                                            AppLock.TYPE_PASSWORD ->
                                                R.string.app_lock_type_password
                                            else -> R.string.app_lock_type_pin
                                        },
                                    ),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Text(
                            text = stringResource(R.string.app_lock_relock),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        listOf(0, 1, 5, 10, 30).forEach { minutes ->
                            LockOptionRow(
                                label = if (minutes == 0) {
                                    stringResource(
                                        R.string.app_lock_relock_immediate,
                                    )
                                } else {
                                    stringResource(
                                        R.string.app_lock_relock_minutes,
                                        minutes,
                                    )
                                },
                                selected = relockMinutes == minutes,
                                onClick = {
                                    relockMinutes = minutes
                                    uiStore.appLockRelockMinutes = minutes
                                    buzz(Haptics.Kind.Tap)
                                    onChanged()
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (configured) {
                            Button(
                                onClick = { go(LockStep.VerifyForChange) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.app_lock_change,
                                    ),
                                )
                            }
                            OutlinedButton(
                                onClick = { go(LockStep.VerifyForDisable) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.app_lock_disable,
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    pendingType = uiStore.appLockType
                                    go(LockStep.ChooseType)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.app_lock_setup,
                                    ),
                                )
                            }
                        }
                    }

                    LockStep.ChooseType -> {
                        Text(
                            text = stringResource(R.string.app_lock_choose_type),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LockTypeRow(
                            title = stringResource(R.string.app_lock_type_pattern),
                            summary = stringResource(R.string.app_lock_pattern_rule),
                            selected = pendingType == AppLock.TYPE_PATTERN,
                            onClick = { pendingType = AppLock.TYPE_PATTERN },
                        )
                        LockTypeRow(
                            title = stringResource(R.string.app_lock_type_pin),
                            summary = stringResource(R.string.app_lock_pin_rule),
                            selected = pendingType == AppLock.TYPE_PIN,
                            onClick = { pendingType = AppLock.TYPE_PIN },
                        )
                        LockTypeRow(
                            title = stringResource(R.string.app_lock_type_password),
                            summary = stringResource(R.string.app_lock_password_rule),
                            selected = pendingType == AppLock.TYPE_PASSWORD,
                            onClick = { pendingType = AppLock.TYPE_PASSWORD },
                        )
                        Button(
                            onClick = { go(LockStep.EnterNew) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.app_lock_next))
                        }
                    }

                    LockStep.EnterNew, LockStep.ConfirmNew -> {
                        Text(
                            text = stringResource(
                                when {
                                    step == LockStep.ConfirmNew &&
                                        pendingType == AppLock.TYPE_PATTERN ->
                                        R.string.app_lock_confirm_pattern
                                    step == LockStep.ConfirmNew ->
                                        R.string.app_lock_confirm_generic
                                    pendingType == AppLock.TYPE_PATTERN ->
                                        R.string.app_lock_enter_pattern
                                    pendingType == AppLock.TYPE_PIN ->
                                        R.string.app_lock_enter_pin
                                    else -> R.string.app_lock_enter_password
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SecretEntry(
                            type = pendingType,
                            error = errorText != null,
                            resetSignal = entryReset,
                            actionLabel = stringResource(R.string.app_lock_next),
                            onTooShort = {
                                fail(
                                    if (pendingType == AppLock.TYPE_PATTERN) {
                                        context.getString(
                                            R.string.app_lock_pattern_rule,
                                        )
                                    } else {
                                        passwordRuleText
                                    },
                                )
                            },
                            onBuzz = { buzz(Haptics.Kind.Tap) },
                            onSubmit = { onSecretEntered(it) },
                            modifier = Modifier.offset {
                                IntOffset(shake.value.roundToInt(), 0)
                            },
                        )
                    }

                    LockStep.VerifyForChange, LockStep.VerifyForDisable -> {
                        Text(
                            text = stringResource(R.string.app_lock_verify_old),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        SecretEntry(
                            type = uiStore.appLockType,
                            error = errorText != null,
                            resetSignal = entryReset,
                            actionLabel = stringResource(
                                R.string.app_lock_confirm_action,
                            ),
                            onTooShort = { fail(wrongText) },
                            onBuzz = { buzz(Haptics.Kind.Tap) },
                            onSubmit = { onSecretEntered(it) },
                            modifier = Modifier.offset {
                                IntOffset(shake.value.roundToInt(), 0)
                            },
                        )
                    }
                }

                val shownError = errorText
                if (shownError != null) {
                    Text(
                        text = shownError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Radio row used for the relock timeout choices. */
@Composable
private fun LockOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Radio row with a hint line, for the lock-type choices. */
@Composable
private fun LockTypeRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One secret entry (pattern / PIN / password) for the setup flow.
 * The pattern submits itself when the finger lifts; PIN and password
 * use the action button. [resetSignal] clears the widget after a
 * failed attempt or a step change.
 */
@Composable
private fun SecretEntry(
    type: Int,
    error: Boolean,
    resetSignal: Int,
    actionLabel: String,
    onTooShort: () -> Unit,
    onBuzz: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember(type, resetSignal) { mutableStateOf("") }
    var password by remember(type, resetSignal) { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        when (type) {
            AppLock.TYPE_PATTERN -> {
                PatternPad(
                    onPatternComplete = { dots ->
                        if (dots.size >= 4) {
                            onSubmit(dots.joinToString(","))
                        } else {
                            onTooShort()
                        }
                    },
                    onDotAdded = onBuzz,
                    error = error,
                    resetSignal = resetSignal,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth(),
                )
            }

            AppLock.TYPE_PIN -> {
                PinDots(count = pin.length, error = error)
                PinPad(
                    onDigit = { digit ->
                        if (pin.length < 6) {
                            pin += digit.toString()
                            onBuzz()
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            onBuzz()
                        }
                    },
                )
                Button(
                    onClick = { onSubmit(pin) },
                    enabled = pin.length >= 4,
                ) {
                    Text(text = actionLabel)
                }
            }

            else -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth(),
                )
                Button(
                    onClick = { onSubmit(password) },
                    enabled = password.length >= 4,
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
