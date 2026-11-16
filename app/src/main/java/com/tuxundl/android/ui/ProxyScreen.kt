package com.tuxundl.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.core.model.Proxy
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.design.ProxyDesign
import com.tuxundl.android.design.R as DesignR

/**
 * State of one proxy group on the Compose proxy screen.
 *
 * Written by ProxyActivity (from withClash, exactly like the old
 * ProxyDesign.updateGroup did) and read by [ProxyScreen].
 */
class ProxyGroupUiState(
    val name: String,
) {
    var type by mutableStateOf("")
    var now by mutableStateOf("")
    var selectable by mutableStateOf(false)
    var proxies by mutableStateOf<List<Proxy>>(emptyList())
    var loaded by mutableStateOf(false)
    var urlTesting by mutableStateOf(false)
    var expanded by mutableStateOf(false)
}

/**
 * State holder for the Compose proxy screen.
 *
 * All values are written by ProxyActivity and read by [ProxyScreen].
 */
class ProxyUiState {
    /** Session override mode from queryOverride(Session); null = do not modify. */
    var mode by mutableStateOf<TunnelState.Mode?>(null)
    var groups by mutableStateOf<List<ProxyGroupUiState>>(emptyList())
    var sort by mutableStateOf(ProxySort.Default)
    var excludeNotSelectable by mutableStateOf(false)

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    state: ProxyUiState,
    onRequest: (ProxyDesign.Request) -> Unit,
    onSortChange: (ProxySort) -> Unit,
    onExcludeNotSelectableChange: (Boolean) -> Unit,
    onExpandedGroupChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(DesignR.string.proxy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    ProxyOverflowMenu(
                        state = state,
                        onRequest = onRequest,
                        onSortChange = onSortChange,
                        onExcludeNotSelectableChange = onExcludeNotSelectableChange,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ModeSelector(
                mode = state.mode,
                onModeChange = { onRequest(ProxyDesign.Request.PatchMode(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (state.groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(DesignR.string.proxy_empty_tips),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                // group name -> its current selection, for subgroup subtitles
                val links = state.groups.associate { it.name to it.now }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.groups, key = { _, group -> group.name }) { index, group ->
                        GroupCard(
                            group = group,
                            links = links,
                            onToggleExpand = {
                                group.expanded = !group.expanded
                                if (group.expanded) {
                                    onExpandedGroupChange(group.name)
                                }
                            },
                            onUrlTest = { onRequest(ProxyDesign.Request.UrlTest(index)) },
                            onSelect = { name ->
                                onRequest(ProxyDesign.Request.Select(index, name))
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyOverflowMenu(
    state: ProxyUiState,
    onRequest: (ProxyDesign.Request) -> Unit,
    onSortChange: (ProxySort) -> Unit,
    onExcludeNotSelectableChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_baseline_more_vert),
                contentDescription = stringResource(DesignR.string.more),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Mode: "do not modify" (the three modes live in the segmented row)
            DropdownMenuItem(
                text = { Text(text = stringResource(DesignR.string.dont_modify)) },
                leadingIcon = { MenuCheckIcon(checked = state.mode == null) },
                onClick = {
                    expanded = false
                    onRequest(ProxyDesign.Request.PatchMode(null))
                },
            )
            // Sort
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(DesignR.string.sort) + ": " +
                            stringResource(DesignR.string.default_),
                    )
                },
                leadingIcon = { MenuCheckIcon(checked = state.sort == ProxySort.Default) },
                onClick = {
                    expanded = false
                    onSortChange(ProxySort.Default)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(DesignR.string.sort) + ": " +
                            stringResource(DesignR.string.name),
                    )
                },
                leadingIcon = { MenuCheckIcon(checked = state.sort == ProxySort.Title) },
                onClick = {
                    expanded = false
                    onSortChange(ProxySort.Title)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(DesignR.string.sort) + ": " +
                            stringResource(DesignR.string.delay),
                    )
                },
                leadingIcon = { MenuCheckIcon(checked = state.sort == ProxySort.Delay) },
                onClick = {
                    expanded = false
                    onSortChange(ProxySort.Delay)
                },
            )
            // Filter
            DropdownMenuItem(
                text = { Text(text = stringResource(DesignR.string.not_selectable)) },
                leadingIcon = { MenuCheckIcon(checked = state.excludeNotSelectable) },
                onClick = {
                    expanded = false
                    onExcludeNotSelectableChange(!state.excludeNotSelectable)
                },
            )
        }
    }
}

@Composable
private fun MenuCheckIcon(checked: Boolean) {
    Box(modifier = Modifier.size(24.dp)) {
        if (checked) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_outline_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: TunnelState.Mode?,
    onModeChange: (TunnelState.Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        TunnelState.Mode.Rule to DesignR.string.rule_mode,
        TunnelState.Mode.Global to DesignR.string.global_mode,
        TunnelState.Mode.Direct to DesignR.string.direct_mode,
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        modes.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {},
                label = { Text(text = stringResource(label)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    group: ProxyGroupUiState,
    links: Map<String, String>,
    onToggleExpand: () -> Unit,
    onUrlTest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (group.type.isNotEmpty()) {
                        Text(
                            text = group.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (group.now.isNotEmpty()) {
                        Text(
                            text = group.now,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (group.urlTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                    )
                } else {
                    IconButton(onClick = onUrlTest) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_flash_on),
                            contentDescription = stringResource(DesignR.string.delay_test),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (group.expanded) {
                if (!group.loaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        group.proxies.forEach { proxy ->
                            ProxyRow(
                                proxy = proxy,
                                selected = proxy.name == group.now,
                                links = links,
                                enabled = group.selectable,
                                onClick = { onSelect(proxy.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyRow(
    proxy: Proxy,
    selected: Boolean,
    links: Map<String, String>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val title = if (proxy.isGroup) proxy.name else proxy.title
    val subtitle = if (proxy.isGroup) {
        val linkNow = links[proxy.name]
        if (linkNow == null) {
            proxy.type
        } else {
            "%s(%s)".format(proxy.type, linkNow.ifEmpty { "*" })
        }
    } else {
        proxy.subtitle
    }
    val delayText = if (proxy.delay > 0) "${proxy.delay} ms" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = delayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_outline_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
