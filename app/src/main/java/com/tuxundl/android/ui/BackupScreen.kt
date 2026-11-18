package com.tuxundl.android.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.R as DesignR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** State for the backup & restore screen. */
class BackupUiState {
    var working by mutableStateOf(false)

    /** Weekly automatic backup switch (mirrors UiStore). */
    var autoBackupEnabled by mutableStateOf(false)

    /** Epoch millis of the last automatic backup; 0 = never. */
    var lastAutoBackupAt by mutableStateOf(0L)

    /** Lines shown in the result dialog after a restore (or export). */
    var resultLines by mutableStateOf<List<String>?>(null)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(R.string.backup_restore),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .tuxunPageEnter()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionCard(title = stringResource(R.string.backup_restore)) {
                Text(
                    text = stringResource(R.string.backup_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            SectionCard(title = stringResource(R.string.backup_export)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_export_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val exportInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onExport,
                        enabled = !state.working,
                        interactionSource = exportInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tuxunPressScale(exportInteraction, pressedScale = 0.97f),
                    ) {
                        Text(text = stringResource(R.string.backup_export))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.backup_import)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_import_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val importInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onImport,
                        enabled = !state.working,
                        interactionSource = importInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tuxunPressScale(importInteraction, pressedScale = 0.97f),
                    ) {
                        Text(text = stringResource(R.string.backup_import))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.backup_auto_title)) {
                Text(
                    text = stringResource(R.string.backup_auto_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                SwitchRow(
                    title = stringResource(R.string.backup_auto_switch),
                    summary = if (state.lastAutoBackupAt > 0L) {
                        stringResource(
                            R.string.backup_auto_last,
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(state.lastAutoBackupAt)),
                        )
                    } else {
                        stringResource(R.string.backup_auto_never)
                    },
                    checked = state.autoBackupEnabled,
                    onCheckedChange = onAutoBackupToggle,
                )
            }

            if (state.working) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.backup_working),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val lines = state.resultLines
    if (lines != null) {
        AlertDialog(
            onDismissRequest = { state.resultLines = null },
            title = { Text(text = stringResource(R.string.backup_restore)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.resultLines = null }) {
                    Text(text = stringResource(DesignR.string.ok))
                }
            },
        )
    }
}
