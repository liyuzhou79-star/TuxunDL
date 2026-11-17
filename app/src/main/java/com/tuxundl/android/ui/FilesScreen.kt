package com.tuxundl.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.FilesDesign
import com.tuxundl.android.design.model.File
import com.tuxundl.android.design.util.ValidatorFileName
import com.tuxundl.android.design.util.elapsedIntervalString
import com.tuxundl.android.design.util.toBytesString
import androidx.compose.ui.platform.LocalContext
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose files screen. */
class FilesUiState {
    var files by mutableStateOf<List<File>>(emptyList())
    var currentInBaseDir by mutableStateOf(true)
    var configurationEditable by mutableStateOf(false)

    /** File whose bottom-sheet menu is open, if any. */
    var menuFile by mutableStateOf<File?>(null)

    /** File being renamed, if any. */
    var renameFile by mutableStateOf<File?>(null)

    /** Default name for the import-name prompt, if it is open. */
    var importNameDefault by mutableStateOf<String?>(null)

    /** Refreshed once a minute by the activity so elapsed labels stay fresh. */
    var now by mutableStateOf(System.currentTimeMillis())

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: FilesUiState,
    onRequest: (FilesDesign.Request) -> Unit,
    onRenameConfirmed: (File, String) -> Unit,
    onImportNameConfirmed: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.files),
                onBack = onBack,
                actions = {
                    if (!state.currentInBaseDir) {
                        IconButton(
                            onClick = { onRequest(FilesDesign.Request.ImportFile(null)) },
                        ) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_add),
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
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.files, key = { it.id }) { file ->
                val itemStyle = LocalAnimStyle.current
                TuxunCard(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tuxunFadeSpec(itemStyle, entering = true),
                            placementSpec = tuxunPlacementSpec(itemStyle),
                            fadeOutSpec = tuxunFadeSpec(itemStyle, entering = false),
                        )
                        .fillMaxWidth()
                        .clickable {
                            if (file.isDirectory) {
                                onRequest(FilesDesign.Request.OpenDirectory(file))
                            } else {
                                onRequest(FilesDesign.Request.OpenFile(file))
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (file.isDirectory) DesignR.drawable.ic_outline_folder
                                else DesignR.drawable.ic_outline_article
                            ),
                            contentDescription = null,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!file.isDirectory) {
                                Text(
                                    text = file.size.toBytesString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = (state.now - file.lastModified)
                                        .elapsedIntervalString(context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { state.menuFile = file }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_more_vert),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }

    val menuFile = state.menuFile
    if (menuFile != null) {
        val currentInBase = state.currentInBaseDir

        ModalBottomSheet(onDismissRequest = { state.menuFile = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = menuFile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (!menuFile.isDirectory && (!currentInBase || state.configurationEditable)) {
                    ClickRow(
                        title = stringResource(DesignR.string.import_),
                        onClick = {
                            state.menuFile = null
                            onRequest(FilesDesign.Request.ImportFile(menuFile))
                        },
                    )
                }
                if (!menuFile.isDirectory && menuFile.size > 0) {
                    ClickRow(
                        title = stringResource(DesignR.string.export),
                        onClick = {
                            state.menuFile = null
                            onRequest(FilesDesign.Request.ExportFile(menuFile))
                        },
                    )
                }
                if (!currentInBase) {
                    ClickRow(
                        title = stringResource(DesignR.string.rename),
                        onClick = {
                            state.menuFile = null
                            state.renameFile = menuFile
                        },
                    )
                    ClickRow(
                        title = stringResource(DesignR.string.delete),
                        onClick = {
                            state.menuFile = null
                            onRequest(FilesDesign.Request.DeleteFile(menuFile))
                        },
                    )
                }
            }
        }
    }

    val renameFile = state.renameFile
    if (renameFile != null) {
        TextInputDialog(
            title = stringResource(DesignR.string.file_name),
            initial = renameFile.name,
            errorText = stringResource(DesignR.string.invalid_file_name),
            validator = ValidatorFileName,
            onDismiss = { state.renameFile = null },
            onConfirm = { newName ->
                state.renameFile = null
                onRenameConfirmed(renameFile, newName)
            },
        )
    }

    val importDefault = state.importNameDefault
    if (importDefault != null) {
        TextInputDialog(
            title = stringResource(DesignR.string.file_name),
            initial = importDefault,
            errorText = stringResource(DesignR.string.invalid_file_name),
            validator = ValidatorFileName,
            onDismiss = { state.importNameDefault = null },
            onConfirm = { newName ->
                state.importNameDefault = null
                onImportNameConfirmed(newName)
            },
        )
    }

}
