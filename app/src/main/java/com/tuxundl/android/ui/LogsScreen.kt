package com.tuxundl.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.tuxundl.android.design.LogsDesign
import com.tuxundl.android.design.model.LogFile
import com.tuxundl.android.design.util.format
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose logs screen. */
class LogsUiState {
    var files by mutableStateOf<List<LogFile>>(emptyList())
    var showDeleteAllConfirm by mutableStateOf(false)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(
    state: LogsUiState,
    onRequest: (LogsDesign.Request) -> Unit,
    onDeleteAllConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.logs),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onRequest(LogsDesign.Request.DeleteAll) }) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_delete),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TuxunCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequest(LogsDesign.Request.StartLogcat) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_adb),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = stringResource(DesignR.string.clash_logcat),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(DesignR.string.tap_to_start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(DesignR.string.history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(state.files) { file ->
                val itemStyle = LocalAnimStyle.current
                TuxunCard(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tuxunFadeSpec(itemStyle, entering = true),
                            placementSpec = tuxunPlacementSpec(itemStyle),
                            fadeOutSpec = tuxunFadeSpec(itemStyle, entering = false),
                        )
                        .fillMaxWidth()
                        .clickable { onRequest(LogsDesign.Request.OpenFile(file)) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = file.date.format(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (state.showDeleteAllConfirm) {
        ConfirmDialog(
            title = stringResource(DesignR.string.delete_all_logs),
            message = stringResource(DesignR.string.delete_all_logs_warn),
            onDismiss = { state.showDeleteAllConfirm = false },
            onConfirm = {
                state.showDeleteAllConfirm = false
                onDeleteAllConfirmed()
            },
        )
    }
}
