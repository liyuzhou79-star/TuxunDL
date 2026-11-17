package com.tuxundl.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.tuxundl.android.R
import com.tuxundl.android.core.model.LogMessage
import com.tuxundl.android.design.LogcatDesign
import com.tuxundl.android.design.util.format
import kotlinx.coroutines.launch
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose logcat screen. */
class LogcatUiState {
    var messages by mutableStateOf<List<LogMessage>>(emptyList())

    /** Search keyword typed by the user; blank shows everything. */
    var searchQuery by mutableStateOf("")

    /** Level filter: "all" / "error" / "warning" / "info"; persisted in UiStore. */
    var levelFilter by mutableStateOf("all")

    /** Messages after search + level filtering; computed off the main thread. */
    var filteredMessages by mutableStateOf<List<LogMessage>>(emptyList())
    var streaming by mutableStateOf(false)

    val snackbarHostState = SnackbarHostState()
}

/** Level filter options in display order: key to label string resource. */
private val logLevelOptions = listOf(
    "all" to R.string.log_level_all,
    "error" to R.string.log_level_error,
    "warning" to R.string.log_level_warning,
    "info" to R.string.log_level_info,
)

/** Pure filter: keyword match (case-insensitive, content only) + level. */
fun filterLogMessages(
    messages: List<LogMessage>,
    query: String,
    levelFilter: String,
): List<LogMessage> {
    if (query.isBlank() && levelFilter == "all") return messages

    return messages.filter { message ->
        (query.isBlank() || message.message.contains(query, ignoreCase = true)) &&
            matchesLogLevel(message.level, levelFilter)
    }
}

private fun matchesLogLevel(level: LogMessage.Level, filter: String): Boolean {
    return when (filter) {
        "error" -> level == LogMessage.Level.Error
        "warning" -> level == LogMessage.Level.Warning
        // Debug lines are informational, so they ride along with Info.
        "info" -> level == LogMessage.Level.Info || level == LogMessage.Level.Debug
        else -> true // "all" (and anything unknown): show everything.
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun LogcatScreen(
    state: LogcatUiState,
    onRequest: (LogcatDesign.Request) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLevelFilterChange: (String) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Always-visible log search (same look as the node page):
            // live filter over message content, blank shows everything.
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = {
                    state.searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            state.searchQuery = ""
                            onSearchQueryChange("")
                        }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_close),
                                contentDescription = null,
                            )
                        }
                    }
                },
                placeholder = {
                    Text(text = stringResource(DesignR.string.search))
                },
            )

            // Level filter chips; the choice is saved and restored on reopen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                logLevelOptions.forEach { (key, labelRes) ->
                    FilterChip(
                        selected = state.levelFilter == key,
                        onClick = {
                            state.levelFilter = key
                            onLevelFilterChange(key)
                        },
                        label = { Text(text = stringResource(labelRes)) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                reverseLayout = state.streaming,
            ) {
                if (state.filteredMessages.isEmpty() && state.messages.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.log_search_no_result),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                items(state.filteredMessages) { message ->
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
}
