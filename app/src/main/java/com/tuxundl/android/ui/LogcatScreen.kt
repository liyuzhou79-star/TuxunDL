package com.tuxundl.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.tuxundl.android.core.model.LogMessage
import com.tuxundl.android.design.LogcatDesign
import com.tuxundl.android.design.util.format
import kotlinx.coroutines.launch
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose logcat screen. */
class LogcatUiState {
    var messages by mutableStateOf<List<LogMessage>>(emptyList())
    var streaming by mutableStateOf(false)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun LogcatScreen(
    state: LogcatUiState,
    onRequest: (LogcatDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(DesignR.string.copied)

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.logcat),
                onBack = onBack,
                actions = {
                    if (state.streaming) {
                        IconButton(onClick = { onRequest(LogcatDesign.Request.Close) }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_close),
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(onClick = { onRequest(LogcatDesign.Request.Delete) }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_delete),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = { onRequest(LogcatDesign.Request.Export) }) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            reverseLayout = state.streaming,
        ) {
            items(state.messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                val data = ClipData.newPlainText("log", message.message)
                                context.getSystemService<ClipboardManager>()
                                    ?.setPrimaryClip(data)

                                scope.launch {
                                    state.snackbarHostState.showSnackbar(
                                        message = copiedText,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row {
                        Text(
                            text = message.level.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "  " + message.time.format(context, false, true),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
