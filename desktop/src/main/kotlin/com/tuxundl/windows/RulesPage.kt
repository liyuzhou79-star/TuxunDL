package com.tuxundl.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

private val Indigo get() = ThemeState.accent
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

private data class RuleRow(
    val type: String,
    val payload: String,
    val proxy: String,
)

/**
 * Rules page: the effective rule list of the running core, in the
 * order the core evaluates them. Read-only; rules come from the
 * active subscription config.
 */
@Composable
internal fun RulesPage(
    searchFocusRequester: FocusRequester = FocusRequester(),
) {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<RuleRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }

    // Filter chips are generated from the types actually present.
    val ruleTypes = remember(rules) { rules.map { it.type }.distinct() }

    val shownRules = rules.filter { rule ->
        (typeFilter == null || rule.type == typeFilter) &&
            (query.isBlank() ||
                rule.type.contains(query, ignoreCase = true) ||
                rule.payload.contains(query, ignoreCase = true) ||
                rule.proxy.contains(query, ignoreCase = true))
    }

    fun refresh() {
        val api = CoreBridge.api ?: return
        scope.launch {
            rules = api.rules().map { obj ->
                RuleRow(
                    type = obj["type"]?.jsonPrimitive?.content ?: "",
                    payload = obj["payload"]?.jsonPrimitive?.content ?: "",
                    proxy = obj["proxy"]?.jsonPrimitive?.content ?: "",
                )
            }
            loaded = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (CoreBridge.api == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "核心还没启动，先到首页点连接",
                color = TextDim,
                fontSize = 16.sp,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (query.isBlank() && typeFilter == null) {
                    "规则（${rules.size}）"
                } else {
                    "规则（${shownRules.size}/${rules.size}）"
                },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "刷新",
                color = Indigo,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { refresh() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索规则（域名、类型或走向）", color = TextDim, fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
        )

        if (ruleTypes.size > 1) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuleChip("全部", typeFilter == null) { typeFilter = null }
                ruleTypes.forEach { type ->
                    RuleChip(type, typeFilter == type) { typeFilter = type }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loaded && rules.isEmpty()) {
            Text(
                text = "当前配置没有规则",
                color = TextDim,
                fontSize = 15.sp,
            )
        }
        if (loaded && rules.isNotEmpty() && shownRules.isEmpty()) {
            Text(
                text = "没有对得上的规则，换个关键词试试",
                color = TextDim,
                fontSize = 15.sp,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shownRules) { rule ->
                Column {
                    Text(
                        text = "${rule.type}  ${rule.payload}".trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "→ ${rule.proxy}",
                        color = TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.White else TextDim,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Indigo else PanelDark)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
