package com.tuxundl.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tuxundl.android.design.AccessControlDesign
import com.tuxundl.android.design.model.AppInfo
import com.tuxundl.android.design.model.AppInfoSort
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose access control screen. */
class AccessControlUiState {
    var apps by mutableStateOf<List<AppInfo>>(emptyList())

    /** Bumped whenever the selection set changes so checkboxes re-render. */
    var selectionTick by mutableStateOf(0)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessControlScreen(
    state: AccessControlUiState,
    uiStore: UiStore,
    selected: MutableSet<String>,
    onRequest: (AccessControlDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }

    // One source of truth for the system-apps filter: the switch below and
    // the overflow menu's "system apps" item read and write the same flag.
    // Default off (third-party only) — most users only proxy their own apps.
    var systemApps by remember { mutableStateOf(uiStore.accessControlSystemApp) }
    var sort by remember { mutableStateOf(uiStore.accessControlSort) }
    var reverse by remember { mutableStateOf(uiStore.accessControlReverse) }

    // Live filter over name and package; a blank keyword shows everything.
    // The selection set lives in the Activity and is keyed by package name,
    // so filtering never loses a checked app.
    val displayed = if (keyword.isBlank()) {
        state.apps
    } else {
        state.apps.filter {
            it.label.contains(keyword, ignoreCase = true) ||
                it.packageName.contains(keyword, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(DesignR.string.access_control_packages))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_more_vert),
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(DesignR.string.select_all)) },
                            onClick = {
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.SelectAll)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(DesignR.string.select_none)) },
                            onClick = {
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.SelectNone)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(DesignR.string.select_invert)) },
                            onClick = {
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.SelectInvert)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(DesignR.string.system_apps)) },
                            leadingIcon = {
                                Checkbox(checked = systemApps, onCheckedChange = null)
                            },
                            onClick = {
                                systemApps = !systemApps
                                uiStore.accessControlSystemApp = systemApps
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.ReloadApps)
                            },
                        )
                        listOf(
                            AppInfoSort.Label to DesignR.string.name,
                            AppInfoSort.PackageName to DesignR.string.package_name,
                            AppInfoSort.InstallTime to DesignR.string.install_time,
                            AppInfoSort.UpdateTime to DesignR.string.update_time,
                        ).forEach { (value, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(labelRes)) },
                                leadingIcon = {
                                    RadioButton(
                                        selected = sort == value,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    sort = value
                                    uiStore.accessControlSort = value
                                    menuOpen = false
                                    onRequest(AccessControlDesign.Request.ReloadApps)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(text = stringResource(DesignR.string.reverse)) },
                            leadingIcon = {
                                Checkbox(checked = reverse, onCheckedChange = null)
                            },
                            onClick = {
                                reverse = !reverse
                                uiStore.accessControlReverse = reverse
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.ReloadApps)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(DesignR.string.import_from_clipboard))
                            },
                            onClick = {
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.Import)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(DesignR.string.export_to_clipboard))
                            },
                            onClick = {
                                menuOpen = false
                                onRequest(AccessControlDesign.Request.Export)
                            },
                        )
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
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(DesignR.string.only_third_party_apps),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = !systemApps,
                    onCheckedChange = { thirdPartyOnly ->
                        systemApps = !thirdPartyOnly
                        uiStore.accessControlSystemApp = systemApps
                        onRequest(AccessControlDesign.Request.ReloadApps)
                    },
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(displayed, key = { it.packageName }) { app ->
                    // Reading selectionTick subscribes this item to selection changes.
                    val checked = state.selectionTick.let { app.packageName in selected }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) {
                                    selected.remove(app.packageName)
                                } else {
                                    selected.add(app.packageName)
                                }
                                state.selectionTick++
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { nowChecked ->
                                if (nowChecked) {
                                    selected.add(app.packageName)
                                } else {
                                    selected.remove(app.packageName)
                                }
                                state.selectionTick++
                            },
                        )
                        Image(
                            bitmap = remember(app.packageName) {
                                app.icon.toBitmap(96, 96).asImageBitmap()
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(40.dp),
                        )
                        Column {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
