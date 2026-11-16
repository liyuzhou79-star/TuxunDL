package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.PropertiesDesign
import com.tuxundl.android.design.util.ValidatorAgeSecretKey
import com.tuxundl.android.design.util.ValidatorAutoUpdateInterval
import com.tuxundl.android.design.util.ValidatorHttpUrl
import com.tuxundl.android.design.util.ValidatorNotBlank
import com.tuxundl.android.service.model.Profile
import java.util.concurrent.TimeUnit
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose profile-properties screen. */
class PropertiesUiState {
    var profile by mutableStateOf<Profile?>(null)

    var progressing by mutableStateOf(false)
    var progressText by mutableStateOf<String?>(null)
    var progressIndeterminate by mutableStateOf(true)
    var progressValue by mutableStateOf(0)
    var progressMax by mutableStateOf(0)

    /** Set by the activity when a back press needs the exit-without-saving dialog. */
    var showExitConfirm by mutableStateOf(false)

    /** Field editor currently open, if any. */
    var editField by mutableStateOf<Field?>(null)

    val snackbarHostState = SnackbarHostState()

    enum class Field {
        Name, Url, AgeSecretKey, Interval
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesScreen(
    state: PropertiesUiState,
    onProfileChange: (Profile) -> Unit,
    onRequest: (PropertiesDesign.Request) -> Unit,
    onBack: () -> Unit,
    onExitConfirmed: () -> Unit,
) {
    val profile = state.profile

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(text = stringResource(DesignR.string.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (state.progressing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp),
                        )
                    } else {
                        IconButton(onClick = { onRequest(PropertiesDesign.Request.Commit) }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_save),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(DesignR.string.tips_properties),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (profile != null) {
                ValueRow(
                    title = stringResource(DesignR.string.name),
                    value = profile.name,
                    onClick = { state.editField = PropertiesUiState.Field.Name },
                )
                ValueRow(
                    title = stringResource(DesignR.string.url),
                    value = profile.source,
                    onClick = {
                        if (profile.type != Profile.Type.External) {
                            state.editField = PropertiesUiState.Field.Url
                        }
                    },
                )
                ValueRow(
                    title = stringResource(DesignR.string.age_secret_key),
                    value = profile.ageSecretKey,
                    onClick = { state.editField = PropertiesUiState.Field.AgeSecretKey },
                )
                ValueRow(
                    title = stringResource(DesignR.string.auto_update),
                    value = if (profile.interval == 0L) {
                        stringResource(DesignR.string.disabled)
                    } else {
                        stringResource(
                            DesignR.string.format_minutes,
                            TimeUnit.MILLISECONDS.toMinutes(profile.interval),
                        )
                    },
                    onClick = { state.editField = PropertiesUiState.Field.Interval },
                )
                ClickRow(
                    title = stringResource(DesignR.string.browse_files),
                    onClick = { onRequest(PropertiesDesign.Request.BrowseFiles) },
                )
            }
        }
    }

    if (profile != null) {
        when (state.editField) {
            PropertiesUiState.Field.Name -> TextInputDialog(
                title = stringResource(DesignR.string.name),
                initial = profile.name,
                hint = stringResource(DesignR.string.properties),
                errorText = stringResource(DesignR.string.should_not_be_blank),
                validator = ValidatorNotBlank,
                onDismiss = { state.editField = null },
                onConfirm = { text ->
                    state.editField = null
                    if (text != profile.name) onProfileChange(profile.copy(name = text))
                },
            )
            PropertiesUiState.Field.Url -> TextInputDialog(
                title = stringResource(DesignR.string.url),
                initial = profile.source,
                hint = stringResource(DesignR.string.profile_url),
                errorText = stringResource(DesignR.string.accept_http_content),
                validator = ValidatorHttpUrl,
                onDismiss = { state.editField = null },
                onConfirm = { text ->
                    state.editField = null
                    if (text != profile.source) onProfileChange(profile.copy(source = text))
                },
            )
            PropertiesUiState.Field.AgeSecretKey -> TextInputDialog(
                title = stringResource(DesignR.string.age_secret_key),
                initial = profile.ageSecretKey ?: "",
                hint = stringResource(DesignR.string.age_secret_key_hint),
                errorText = stringResource(DesignR.string.age_secret_key_error),
                validator = ValidatorAgeSecretKey,
                onDismiss = { state.editField = null },
                onConfirm = { text ->
                    state.editField = null
                    val newKey = text.ifBlank { null }
                    if (newKey != profile.ageSecretKey) {
                        onProfileChange(profile.copy(ageSecretKey = newKey))
                    }
                },
            )
            PropertiesUiState.Field.Interval -> TextInputDialog(
                title = stringResource(DesignR.string.auto_update),
                initial = TimeUnit.MILLISECONDS.toMinutes(profile.interval)
                    .let { if (it == 0L) "" else it.toString() },
                hint = stringResource(DesignR.string.auto_update_minutes),
                errorText = stringResource(DesignR.string.at_least_15_minutes),
                validator = ValidatorAutoUpdateInterval,
                onDismiss = { state.editField = null },
                onConfirm = { text ->
                    state.editField = null
                    val minutes = text.toLongOrNull() ?: 0
                    val interval = TimeUnit.MINUTES.toMillis(minutes)
                    if (interval != profile.interval) {
                        onProfileChange(profile.copy(interval = interval))
                    }
                },
            )
            null -> Unit
        }
    }

    if (state.progressing) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                Column {
                    Text(
                        text = state.progressText
                            ?: stringResource(DesignR.string.initializing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (state.progressIndeterminate || state.progressMax <= 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = (state.progressValue.toFloat() / state.progressMax.toFloat())
                                .coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (state.showExitConfirm) {
        ConfirmDialog(
            title = stringResource(DesignR.string.exit_without_save),
            message = stringResource(DesignR.string.exit_without_save_warning),
            onDismiss = { state.showExitConfirm = false },
            onConfirm = {
                state.showExitConfirm = false
                onExitConfirmed()
            },
        )
    }
}
