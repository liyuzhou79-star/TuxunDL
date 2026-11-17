package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.design.R as DesignR

/** State for the settings home: the manual update-check flow lives here. */
class SettingsUiState {
    var checkingUpdate by mutableStateOf(false)

    /** Non-null while the update-check result dialog is shown. */
    var updateMessage by mutableStateOf<String?>(null)

    /** Download target for the dialog's Download button; null = no update. */
    var updateDownloadUrl by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRequest: (SettingsDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.settings),
                onBack = onBack,
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
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_settings,
                title = stringResource(DesignR.string.app),
                onClick = { onRequest(SettingsDesign.Request.StartApp) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_dns,
                title = stringResource(DesignR.string.network),
                onClick = { onRequest(SettingsDesign.Request.StartNetwork) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_extension,
                title = stringResource(DesignR.string.override),
                onClick = { onRequest(SettingsDesign.Request.StartOverride) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_view_list,
                title = stringResource(R.string.custom_rules),
                onClick = { onRequest(SettingsDesign.Request.StartRules) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_apps,
                title = stringResource(R.string.per_app_proxy),
                onClick = { onRequest(SettingsDesign.Request.StartAccessControl) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_dns,
                title = stringResource(R.string.custom_dns),
                onClick = { onRequest(SettingsDesign.Request.StartCustomDns) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_restore,
                title = stringResource(R.string.backup_restore),
                onClick = { onRequest(SettingsDesign.Request.StartBackup) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_meta,
                title = stringResource(DesignR.string.meta_features),
                onClick = { onRequest(SettingsDesign.Request.StartMetaFeature) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_brightness_4,
                title = stringResource(R.string.appearance),
                onClick = { onRequest(SettingsDesign.Request.StartAppearance) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_update,
                title = stringResource(R.string.check_update),
                trailing = {
                    if (state.checkingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                onClick = { onRequest(SettingsDesign.Request.CheckUpdate) },
            )
        }
    }

    val message = state.updateMessage
    if (message != null) {
        val context = LocalContext.current
        val downloadUrl = state.updateDownloadUrl

        AlertDialog(
            onDismissRequest = {
                state.updateMessage = null
                state.updateDownloadUrl = null
            },
            title = { Text(text = stringResource(R.string.check_update)) },
            text = { Text(text = message) },
            confirmButton = {
                if (downloadUrl != null) {
                    TextButton(
                        onClick = {
                            state.updateMessage = null
                            state.updateDownloadUrl = null
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)),
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.update_download))
                    }
                } else {
                    TextButton(
                        onClick = { state.updateMessage = null },
                    ) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            },
            dismissButton = {
                if (downloadUrl != null) {
                    TextButton(
                        onClick = {
                            state.updateMessage = null
                            state.updateDownloadUrl = null
                        },
                    ) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsEntryCard(
    @DrawableRes icon: Int,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    TuxunCard(
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
    }
}
