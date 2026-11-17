package com.tuxundl.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.service.util.CustomRule
import com.tuxundl.android.design.R as DesignR

/**
 * State holder for the custom routing rules editor.
 *
 * Written by RulesActivity (rules come from the per-profile JSON file,
 * group names from withClash) and read by [RulesScreen]. Every mutation is
 * handed back through onRulesChanged; the activity persists it.
 */
class RulesUiState {
    var hasProfile by mutableStateOf(false)
    var profileName by mutableStateOf<String?>(null)
    var rules by mutableStateOf<List<CustomRule>>(emptyList())
    var groupNames by mutableStateOf<List<String>>(emptyList())

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RulesScreen(
    state: RulesUiState,
    onRulesChanged: (List<CustomRule>) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(R.string.custom_rules),
                onBack = onBack,
                actions = {
                    if (state.hasProfile) {
                        IconButton(onClick = { adding = true }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_add),
                                contentDescription = stringResource(R.string.rule_add),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.hasProfile -> {
                CenteredHint(
                    text = stringResource(R.string.rules_no_profile),
                    modifier = Modifier.padding(padding),
                )
            }
            state.rules.isEmpty() -> {
                CenteredHint(
                    text = stringResource(R.string.rules_empty),
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        state.rules,
                        key = { index, rule -> "$index:${rule.type}:${rule.payload}:${rule.target}" },
                    ) { index, rule ->
                        RuleRow(
                            rule = rule,
                            isFirst = index == 0,
                            isLast = index == state.rules.lastIndex,
                            onToggleEnabled = { enabled ->
                                onRulesChanged(
                                    state.rules.toMutableList().apply {
                                        set(index, rule.copy(enabled = enabled))
                                    },
                                )
                            },
                            onMoveUp = {
                                onRulesChanged(state.rules.swapped(index, index - 1))
                            },
                            onMoveDown = {
                                onRulesChanged(state.rules.swapped(index, index + 1))
                            },
                            onDelete = {
                                onRulesChanged(
                                    state.rules.toMutableList().apply { removeAt(index) },
                                )
                            },
                            onClick = { editingIndex = index },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        RuleEditDialog(
            initial = null,
            groupNames = state.groupNames,
            onDismiss = { adding = false },
            onConfirm = { rule ->
                adding = false
                onRulesChanged(state.rules + rule)
            },
        )
    }

    val editing = editingIndex
    if (editing != null && editing in state.rules.indices) {
        RuleEditDialog(
            initial = state.rules[editing],
            groupNames = state.groupNames,
            onDismiss = { editingIndex = null },
            onConfirm = { rule ->
                editingIndex = null
                onRulesChanged(
                    state.rules.toMutableList().apply { set(editing, rule) },
                )
            },
        )
    }
}

@Composable
private fun CenteredHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleRow(
    rule: CustomRule,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TuxunCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rule.type == "MATCH") {
                        "MATCH"
                    } else {
                        "${rule.type} · ${rule.payload}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("→ ")
                        append(rule.target)
                        if (rule.noResolve && rule.type in CustomRule.NO_RESOLVE_TYPES) {
                            append(" · no-resolve")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggleEnabled,
            )

            Column {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_arrow_upward),
                        contentDescription = null,
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_baseline_arrow_downward),
                        contentDescription = null,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RuleEditDialog(
    initial: CustomRule?,
    groupNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (CustomRule) -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: CustomRule.TYPES.first()) }
    var payload by remember { mutableStateOf(initial?.payload ?: "") }
    var target by remember { mutableStateOf(initial?.target ?: "DIRECT") }
    var noResolve by remember { mutableStateOf(initial?.noResolve ?: false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }

    val isMatch = type == "MATCH"
    val showNoResolve = type in CustomRule.NO_RESOLVE_TYPES
    val valid = target.isNotBlank() && (isMatch || payload.isNotBlank())

    val targetPresets = buildList {
        add("DIRECT")
        add("REJECT")
        groupNames.forEach { if (it !in this) add(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) R.string.rule_add else R.string.rule_edit,
                ),
            )
        },
        text = {
            Column {
                SelectRow(
                    title = stringResource(R.string.rule_type),
                    options = CustomRule.TYPES,
                    selectedIndex = CustomRule.TYPES.indexOf(type).coerceAtLeast(0),
                    onSelect = { index -> type = CustomRule.TYPES[index] },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.rule_payload)) },
                    enabled = !isMatch,
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = stringResource(R.string.rule_target)) },
                        singleLine = true,
                    )
                    Box {
                        IconButton(onClick = { targetMenuExpanded = true }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_arrow_downward),
                                contentDescription = stringResource(R.string.rule_target),
                            )
                        }
                        DropdownMenu(
                            expanded = targetMenuExpanded,
                            onDismissRequest = { targetMenuExpanded = false },
                        ) {
                            targetPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(text = preset) },
                                    onClick = {
                                        target = preset
                                        targetMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.rule_custom_target_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (showNoResolve) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.rule_no_resolve),
                        checked = noResolve,
                        onCheckedChange = { noResolve = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CustomRule(
                            type = type,
                            payload = if (isMatch) "" else payload.trim(),
                            target = target.trim(),
                            noResolve = if (showNoResolve) noResolve else false,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
                enabled = valid,
            ) {
                Text(text = stringResource(DesignR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(DesignR.string.cancel))
            }
        },
    )
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this

    return toMutableList().apply {
        val tmp = this[a]
        this[a] = this[b]
        this[b] = tmp
    }
}
