package com.tuxundl.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
import com.tuxundl.android.design.R as DesignR

/** Back-arrow top bar shared by all sub screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = { actions() },
    )
}

@Composable
fun CategoryHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * Text for the legacy string resources that carry light HTML markup
 * (they are CDATA strings with <strong>/<b>/<i>/<u>/<br> inside, written
 * for the old Html.fromHtml designs). <strong>/<b> become bold spans,
 * <i> italic, <u> underlined and <br> a newline; every other tag is
 * dropped, so raw markup never leaks onto a Compose screen.
 */
@Composable
fun HtmlText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = remember(text) { parseSimpleHtml(text) },
        style = style,
        color = color,
        modifier = modifier,
    )
}

private val HTML_TAG = Regex("<[^>]*>")

private fun parseSimpleHtml(source: String): AnnotatedString {
    val unescaped = source
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    return buildAnnotatedString {
        var bold = 0
        var italic = 0
        var underline = 0
        var index = 0

        fun appendText(chunk: String) {
            if (chunk.isEmpty()) return

            val style = SpanStyle(
                fontWeight = if (bold > 0) FontWeight.Bold else null,
                fontStyle = if (italic > 0) FontStyle.Italic else null,
                textDecoration = if (underline > 0) TextDecoration.Underline else null,
            )

            if (bold > 0 || italic > 0 || underline > 0) {
                withStyle(style) { append(chunk) }
            } else {
                append(chunk)
            }
        }

        for (match in HTML_TAG.findAll(unescaped)) {
            appendText(unescaped.substring(index, match.range.first))
            index = match.range.last + 1

            when (match.value.lowercase().replace(" ", "")) {
                "<strong>", "<b>" -> bold++
                "</strong>", "</b>" -> if (bold > 0) bold--
                "<i>", "<em>" -> italic++
                "</i>", "</em>" -> if (italic > 0) italic--
                "<u>" -> underline++
                "</u>" -> if (underline > 0) underline--
                "<br>", "<br/>", "</p>" -> append("\n")
                else -> Unit // drop every other tag
            }
        }

        appendText(unescaped.substring(index))
    }
}

/**
 * A settings section: the category header plus its rows grouped into
 * one outlined [TuxunCard], so every settings page shares the same
 * contour language as the home and proxy screens.
 */
@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    CategoryHeader(text = title)
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        TuxunCard(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun ValueRow(
    title: String,
    value: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ClickRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ValueRow(
        title = title,
        value = summary,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * A row that opens a single-choice dialog. Mirrors the old selectableList
 * preference: the current value is shown under the title, tapping opens the
 * option list and picking one applies it immediately.
 */
@Composable
fun SelectRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    var show by remember { mutableStateOf(false) }

    ValueRow(
        title = title,
        value = options.getOrNull(selectedIndex),
        enabled = enabled,
        onClick = { show = true },
    )

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(text = title) },
            text = {
                Column {
                    options.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    show = false
                                    onSelect(index)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = index == selectedIndex,
                                onClick = {
                                    show = false
                                    onSelect(index)
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { show = false }) {
                    Text(text = stringResource(DesignR.string.cancel))
                }
            },
        )
    }
}

/**
 * Text input dialog mirroring the old requestModelTextInput: OK is disabled
 * while [validator] rejects the text, and when [onReset] is given a Reset
 * button is shown (the old dialogs used it for "don't modify").
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    hint: String? = null,
    errorText: String? = null,
    validator: (String) -> Boolean = { true },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val valid = validator(text)

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = hint?.let { { Text(text = it) } },
                    isError = !valid && errorText != null,
                    supportingText = if (!valid && errorText != null) {
                        { Text(text = errorText) }
                    } else {
                        null
                    },
                    singleLine = false,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onReset != null) {
                        TextButton(onClick = onReset) {
                            Text(text = stringResource(DesignR.string.reset))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(text) },
                        enabled = valid,
                    ) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = message?.let { { Text(text = it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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

/**
 * Full-screen list editor mirroring the old editableTextList overlay:
 * Apply returns the edited list, Reset returns null, Cancel keeps the old
 * value (signalled by simply dismissing without calling [onApply]).
 */
@Composable
fun ListEditDialog(
    title: String,
    initial: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>?) -> Unit,
) {
    var items by remember { mutableStateOf(initial) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_close),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { adding = true }) {
                            Text(text = stringResource(R.string.add))
                        }
                    },
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(items) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editIndex = index }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { items = items.toMutableList().apply { removeAt(index) } },
                            ) {
                                Icon(
                                    painter = painterResource(DesignR.drawable.ic_baseline_delete),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onApply(null) }) {
                        Text(text = stringResource(DesignR.string.reset))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onApply(items) }) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            }
        }
    }

    val editing = editIndex
    if (editing != null && editing in items.indices) {
        TextInputDialog(
            title = title,
            initial = items[editing],
            onDismiss = { editIndex = null },
            onConfirm = { text ->
                items = items.toMutableList().apply { set(editing, text) }
                editIndex = null
            },
        )
    }

    if (adding) {
        TextInputDialog(
            title = title,
            initial = "",
            onDismiss = { adding = false },
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    items = items + text
                }
                adding = false
            },
        )
    }
}

/** Full-screen key/value editor mirroring the old editableTextMap overlay. */
@Composable
fun MapEditDialog(
    title: String,
    initial: Map<String, String>,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>?) -> Unit,
) {
    var entries by remember { mutableStateOf(initial.entries.map { it.key to it.value }) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_baseline_close),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { adding = true }) {
                            Text(text = stringResource(R.string.add))
                        }
                    },
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(entries) { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editIndex = index }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.first, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = entry.second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    entries = entries.toMutableList().apply { removeAt(index) }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(DesignR.drawable.ic_baseline_delete),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onApply(null) }) {
                        Text(text = stringResource(DesignR.string.reset))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onApply(entries.toMap()) }) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            }
        }
    }

    val editing = editIndex
    if (editing != null && editing in entries.indices) {
        PairInputDialog(
            title = title,
            initialKey = entries[editing].first,
            initialValue = entries[editing].second,
            onDismiss = { editIndex = null },
            onConfirm = { k, v ->
                entries = entries.toMutableList().apply { set(editing, k to v) }
                editIndex = null
            },
        )
    }

    if (adding) {
        PairInputDialog(
            title = title,
            initialKey = "",
            initialValue = "",
            onDismiss = { adding = false },
            onConfirm = { k, v ->
                entries = entries + (k to v)
                adding = false
            },
        )
    }
}

@Composable
fun PairInputDialog(
    title: String,
    initialKey: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(DesignR.string.key)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(DesignR.string.value)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(key.trim(), value.trim()) },
                        enabled = key.isNotBlank() && value.isNotBlank(),
                    ) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            }
        }
    }
}

/** Small coloured dot previewing a colour, drawn with the theme colour itself. */
@Composable
fun ColorDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(28.dp)) {
        drawCircle(color = color)
    }
}
