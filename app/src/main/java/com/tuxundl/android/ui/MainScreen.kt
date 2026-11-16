package com.tuxundl.android.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.core.util.trafficDownload
import com.tuxundl.android.core.util.trafficTotal
import com.tuxundl.android.core.util.trafficUpload
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.R as DesignR

/**
 * State holder for the Compose home screen.
 *
 * All values are written by MainActivity (from withClash / withProfile,
 * exactly like the old MainDesign setters did) and read by [MainScreen].
 */
class MainUiState {
    var clashRunning by mutableStateOf(false)
    var mode by mutableStateOf(TunnelState.Mode.Rule)
    var profileName by mutableStateOf<String?>(null)
    var hasProviders by mutableStateOf(false)

    /** Packed traffic value from IClashManager.queryTrafficTotal(). */
    var traffic by mutableStateOf(0L)

    /** Non-null while the About dialog should be shown. */
    var aboutVersion by mutableStateOf<String?>(null)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.application_name)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state = state, onRequest = onRequest)
            TrafficCard(state = state)
            EntryCard(
                icon = DesignR.drawable.ic_baseline_apps,
                title = DesignR.string.proxy,
                subtext = modeText(state.mode),
                onClick = { onRequest(MainDesign.Request.OpenProxy) },
            )
            EntryCard(
                icon = DesignR.drawable.ic_baseline_view_list,
                title = DesignR.string.profile,
                subtext = state.profileName?.let {
                    stringResource(DesignR.string.format_profile_activated, it)
                } ?: stringResource(DesignR.string.not_selected),
                onClick = { onRequest(MainDesign.Request.OpenProfiles) },
            )
            if (state.clashRunning && state.hasProviders) {
                EntryCard(
                    icon = DesignR.drawable.ic_baseline_swap_vertical_circle,
                    title = DesignR.string.providers,
                    subtext = null,
                    onClick = { onRequest(MainDesign.Request.OpenProviders) },
                )
            }
            EntryCard(
                icon = DesignR.drawable.ic_baseline_assignment,
                title = DesignR.string.logs,
                subtext = null,
                onClick = { onRequest(MainDesign.Request.OpenLogs) },
            )
            EntryCard(
                icon = DesignR.drawable.ic_baseline_settings,
                title = DesignR.string.settings,
                subtext = null,
                onClick = { onRequest(MainDesign.Request.OpenSettings) },
            )
            EntryCard(
                icon = DesignR.drawable.ic_baseline_help_center,
                title = DesignR.string.help,
                subtext = null,
                onClick = { onRequest(MainDesign.Request.OpenHelp) },
            )
            EntryCard(
                icon = DesignR.drawable.ic_baseline_info,
                title = DesignR.string.about,
                subtext = null,
                onClick = { onRequest(MainDesign.Request.OpenAbout) },
            )
        }
    }

    val aboutVersion = state.aboutVersion
    if (aboutVersion != null) {
        AlertDialog(
            onDismissRequest = { state.aboutVersion = null },
            title = { Text(text = stringResource(R.string.application_name)) },
            text = { Text(text = aboutVersion) },
            confirmButton = {
                TextButton(onClick = { state.aboutVersion = null }) {
                    Text(text = stringResource(DesignR.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCard(
    state: MainUiState,
    onRequest: (MainDesign.Request) -> Unit,
) {
    val running = state.clashRunning

    Card(
        onClick = { onRequest(MainDesign.Request.ToggleStatus) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (running) {
                        DesignR.drawable.ic_outline_check_circle
                    } else {
                        DesignR.drawable.ic_outline_not_interested
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = stringResource(
                        if (running) DesignR.string.running else DesignR.string.stopped,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = if (running) {
                        stringResource(
                            DesignR.string.format_traffic_forwarded,
                            state.traffic.trafficTotal(),
                        )
                    } else {
                        stringResource(DesignR.string.tap_to_start)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TrafficCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.traffic),
                style = MaterialTheme.typography.titleMedium,
            )
            TrafficRow(
                labelText = stringResource(R.string.traffic_upload),
                value = state.traffic.trafficUpload(),
            )
            TrafficRow(
                labelText = stringResource(R.string.traffic_download),
                value = state.traffic.trafficDownload(),
            )
        }
    }
}

@Composable
private fun TrafficRow(
    labelText: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    subtext: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (subtext != null) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun modeText(mode: TunnelState.Mode): String {
    return stringResource(
        when (mode) {
            TunnelState.Mode.Direct -> DesignR.string.direct_mode
            TunnelState.Mode.Global -> DesignR.string.global_mode
            TunnelState.Mode.Rule -> DesignR.string.rule_mode
            else -> DesignR.string.rule_mode
        },
    )
}
