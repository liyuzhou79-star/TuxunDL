package com.tuxundl.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.tuxundl.android.design.preference.NullableTextAdapter
import com.tuxundl.android.design.R as DesignR

/**
 * Rows binding directly to a mutable field of ConfigurationOverride,
 * mirroring the old preference DSL semantics:
 *
 * - null value -> the row shows the placeholder text ("Don't modify")
 * - empty value -> the row shows the empty text (e.g. "Disabled")
 * - list/map null -> placeholder, empty -> "Empty", else "N elements"
 *
 * The field is read once at first composition (like the old preferences
 * did) and written back on every edit; the owning activity persists the
 * whole object through its defer { patchOverride(...) } hook.
 */

@Composable
fun <T> OverrideSelectRow(
    title: String,
    options: List<T>,
    labels: List<String>,
    get: () -> T,
    set: (T) -> Unit,
    enabled: Boolean = true,
) {
    var current by remember { mutableStateOf(get()) }

    SelectRow(
        title = title,
        options = labels,
        selectedIndex = options.indexOf(current),
        enabled = enabled,
        onSelect = { index ->
            val value = options[index]
            current = value
            set(value)
        },
    )
}

/** Tri-state (null / true / false) select used all over the override pages. */
@Composable
fun OverrideBoolSelectRow(
    title: String,
    get: () -> Boolean?,
    set: (Boolean?) -> Unit,
    enabled: Boolean = true,
    labels: List<String>? = null,
) {
    OverrideSelectRow(
        title = title,
        options = listOf(null, true, false),
        labels = labels ?: listOf(
            stringResource(DesignR.string.dont_modify),
            stringResource(DesignR.string.enabled),
            stringResource(DesignR.string.disabled),
        ),
        get = get,
        set = set,
        enabled = enabled,
    )
}

@Composable
fun OverrideTextRow(
    title: String,
    get: () -> String?,
    set: (String?) -> Unit,
    placeholder: String,
    empty: String,
    enabled: Boolean = true,
) {
    var current by remember { mutableStateOf(get()) }
    var editing by remember { mutableStateOf(false) }

    ValueRow(
        title = title,
        value = when {
            current == null -> placeholder
            current!!.isEmpty() -> empty
            else -> current
        },
        enabled = enabled,
        onClick = { editing = true },
    )

    if (editing) {
        TextInputDialog(
            title = title,
            initial = current ?: "",
            hint = title,
            onDismiss = { editing = false },
            onConfirm = { text ->
                editing = false
                current = text
                set(text)
            },
            onReset = {
                editing = false
                current = null
                set(null)
            },
        )
    }
}

@Composable
fun OverridePortRow(
    title: String,
    get: () -> Int?,
    set: (Int?) -> Unit,
    placeholder: String,
    empty: String,
    enabled: Boolean = true,
) {
    var current by remember { mutableStateOf(get()) }
    var editing by remember { mutableStateOf(false) }

    ValueRow(
        title = title,
        value = when {
            current == null -> placeholder
            current == 0 -> empty
            else -> current.toString()
        },
        enabled = enabled,
        onClick = { editing = true },
    )

    if (editing) {
        TextInputDialog(
            title = title,
            initial = NullableTextAdapter.Port.from(current) ?: "",
            hint = title,
            onDismiss = { editing = false },
            onConfirm = { text ->
                editing = false
                val value = NullableTextAdapter.Port.to(text)
                current = value
                set(value)
            },
            onReset = {
                editing = false
                current = null
                set(null)
            },
        )
    }
}

@Composable
fun OverrideListRow(
    title: String,
    get: () -> List<String>?,
    set: (List<String>?) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    var current by remember { mutableStateOf(get()) }
    var editing by remember { mutableStateOf(false) }

    ValueRow(
        title = title,
        value = when {
            current == null -> placeholder
            current!!.isEmpty() -> stringResource(DesignR.string.empty)
            else -> stringResource(DesignR.string.format_elements, current!!.size)
        },
        enabled = enabled,
        onClick = { editing = true },
    )

    if (editing) {
        ListEditDialog(
            title = title,
            initial = current ?: emptyList(),
            onDismiss = { editing = false },
            onApply = { newList ->
                editing = false
                current = newList
                set(newList)
            },
        )
    }
}

@Composable
fun OverrideMapRow(
    title: String,
    get: () -> Map<String, String>?,
    set: (Map<String, String>?) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    var current by remember { mutableStateOf(get()) }
    var editing by remember { mutableStateOf(false) }

    ValueRow(
        title = title,
        value = when {
            current == null -> placeholder
            current!!.isEmpty() -> stringResource(DesignR.string.empty)
            else -> stringResource(DesignR.string.format_elements, current!!.size)
        },
        enabled = enabled,
        onClick = { editing = true },
    )

    if (editing) {
        MapEditDialog(
            title = title,
            initial = current ?: emptyMap(),
            onDismiss = { editing = false },
            onApply = { newMap ->
                editing = false
                current = newMap
                set(newMap)
            },
        )
    }
}
