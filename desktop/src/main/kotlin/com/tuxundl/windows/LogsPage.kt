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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val Indigo = Color(0xFF5B5BD6)
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

/**
 * Logs page: tails of the app log (bridge decisions, errors) and the
 * core log (mihomo's own output), both kept in the app data folder.
 */
@Composable
internal fun LogsPage() {
    var source by remember { mutableStateOf("app") }
    var tick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    val file = File(
        CoreBridge.workDir,
        if (source == "app") "app.log" else "core.log",
    )
    val text = remember(source, tick, query) {
        if (!file.exists()) {
            "暂无日志"
        } else {
            runCatching {
                val lines = file.readLines().takeLast(300)
                val shown = if (query.isBlank()) {
                    lines
                } else {
                    lines.filter { it.contains(query, ignoreCase = true) }
                }
                if (shown.isEmpty()) {
                    "没有包含「$query」的日志行"
                } else {
                    shown.joinToString("\n")
                }
            }.getOrDefault("日志读取失败")
        }
    }

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
                LogTab("应用", source == "app") { source = "app" }
                LogTab("核心", source == "core") { source = "core" }
                LogTab("刷新", false) { tick++ }
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

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDark)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Text(
                text = text,
                color = Color(0xFFC9C9D6),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
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
