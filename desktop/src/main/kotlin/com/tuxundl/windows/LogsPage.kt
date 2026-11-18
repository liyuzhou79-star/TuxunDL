package com.tuxundl.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay

private val Indigo get() = ThemeState.accent
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)
private val LogText = Color(0xFFC9C9D6)
private val LogWarn = Color(0xFFFFB84D)
private val LogError = Color(0xFFFF6B6B)

/**
 * Logs page: tails of the app log (bridge decisions, errors) and the
 * core log (mihomo's own output), both kept in the app data folder.
 * Lines are colored by level (errors red, warnings amber) and the
 * search keyword is highlighted in place, so a failure stands out
 * instead of hiding in a wall of grey text.
 */
@Composable
internal fun LogsPage() {
    var source by remember { mutableStateOf("app") }
    var tick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var autoRefresh by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // While auto-refresh is on, re-read the current log every 2 seconds.
    LaunchedEffect(autoRefresh, source) {
        while (autoRefresh) {
            delay(2000)
            tick++
        }
    }

    // The armed "confirm clear" state disarms itself after 3 seconds.
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3000)
            confirmClear = false
        }
    }

    val file = File(
        CoreBridge.workDir,
        if (source == "app") "app.log" else "core.log",
    )

    // Keep the newest line in view: on open, on every refresh tick
    // and whenever the shown text changes, jump to the bottom while
    // the auto-scroll switch is on. The small delay lets the scroll
    // state learn the new content height first.
    LaunchedEffect(autoScroll, tick, source, query) {
        if (autoScroll) {
            delay(60)
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // The newest 300 lines of the current source, before filtering.
    // Null when the file is missing or cannot be read.
    val rawLines = remember(source, tick) {
        if (!file.exists()) {
            null
        } else {
            runCatching { file.readLines().takeLast(300) }.getOrNull()
        }
    }
    val lines = remember(rawLines, query) {
        val all = rawLines ?: return@remember emptyList()
        if (query.isBlank()) {
            all
        } else {
            all.filter { it.contains(query, ignoreCase = true) }
        }
    }
    val placeholder = when {
        rawLines == null && !file.exists() -> "暂无日志"
        rawLines == null -> "日志读取失败"
        lines.isEmpty() && query.isNotBlank() -> "没有包含「$query」的日志行"
        lines.isEmpty() -> "暂无日志"
        else -> null
    }
    // Plain-text view of what is on screen, for the export fallback.
    val text = placeholder ?: lines.joinToString("\n")

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "日志",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogTab("应用", source == "app") {
                    source = "app"
                    confirmClear = false
                }
                LogTab("核心", source == "core") {
                    source = "core"
                    confirmClear = false
                }
                LogTab("自动刷新", autoRefresh) { autoRefresh = !autoRefresh }
                LogTab("自动滚到底", autoScroll) { autoScroll = !autoScroll }
                LogTab("导出", false) {
                    val target = pickLogExportFile(source)
                    if (target != null) {
                        exportNote = runCatching {
                            val content = if (file.exists()) {
                                file.readText()
                            } else {
                                text
                            }
                            target.writeText(content)
                            "已导出到 ${target.name}"
                        }.getOrElse { "导出失败：${it.message}" }
                    }
                }
                LogTab("刷新", false) { tick++ }
                LogTab(if (confirmClear) "确认清空？" else "清空", confirmClear) {
                    if (confirmClear) {
                        runCatching { file.writeText("") }
                        confirmClear = false
                        tick++
                    } else {
                        confirmClear = true
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索日志", color = TextDim, fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (exportNote != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = exportNote!!, color = TextDim, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDark)
                .verticalScroll(scrollState)
                .padding(14.dp),
        ) {
            if (placeholder != null) {
                Text(
                    text = placeholder,
                    color = LogText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                lines.forEach { line ->
                    LogLine(line = line, query = query)
                }
            }
        }
    }
}

/**
 * Base color of one log line, by the level it carries: errors red,
 * warnings amber, everything else the plain log grey. Both log
 * sources are plain text, so the level is read from the line itself
 * (the core writes level=error / level=warning; the app log uses
 * words like 错误 / 失败）. A line with no level marker keeps the
 * default color.
 */
private fun logLineColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        "error" in lower || "fatal" in lower || "panic" in lower ||
            "错误" in line || "失败" in line -> LogError
        "warn" in lower || "警告" in line -> LogWarn
        else -> LogText
    }
}

/**
 * One log line: colored by level, with every occurrence of the
 * search keyword highlighted (accent background, bold white text)
 * so matches are visible while scrolling, not just filtered to.
 */
@Composable
private fun LogLine(line: String, query: String) {
    val annotated = remember(line, query) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = logLineColor(line))) {
                append(line.ifEmpty { " " })
            }
            if (query.isNotBlank()) {
                var from = 0
                while (true) {
                    val start = line.indexOf(query, from, ignoreCase = true)
                    if (start < 0) break
                    addStyle(
                        SpanStyle(
                            background = Indigo.copy(alpha = 0.55f),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        ),
                        start,
                        start + query.length,
                    )
                    from = start + query.length
                }
            }
        }
    }
    Text(
        text = annotated,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

/**
 * The system save dialog for exporting a log (same AWT pattern as
 * the backup picker in Backup.kt). Returns null when cancelled.
 */
private fun pickLogExportFile(source: String): File? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        "导出日志",
        java.awt.FileDialog.SAVE,
    )
    dialog.file = if (source == "app") "tuxundl-app.log" else "tuxundl-core.log"
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

@Composable
private fun LogTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.White else TextDim,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Indigo else PanelDark)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
