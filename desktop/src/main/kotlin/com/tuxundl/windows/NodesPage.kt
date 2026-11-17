package com.tuxundl.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Palette duplicated from Main.kt for now; the pages consolidate into
// a shared theme file as they grow.
private val Indigo = Color(0xFF5B5BD6)
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

private data class ProxyGroup(
    val name: String,
    val now: String?,
    val members: List<String>,
)

private val GROUP_TYPES = setOf("Selector", "Fallback", "URLTest", "LoadBalance")

/**
 * Node page: proxy groups and their nodes from the running core.
 * Tap a node to select it in its group; the delay label on the right
 * runs a fresh delay test for that node.
 */
@Composable
internal fun NodesPage() {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<ProxyGroup>>(emptyList()) }
    var delays by remember { mutableStateOf<Map<String, Long?>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }

    fun refresh() {
        val api = CoreBridge.api ?: return
        scope.launch {
            val all = api.proxies()
            groups = all.entries
                .mapNotNull { (name, obj) ->
                    val type = obj["type"]?.jsonPrimitive?.content
                    if (type !in GROUP_TYPES) return@mapNotNull null
                    val members = obj["all"]?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?: return@mapNotNull null
                    ProxyGroup(
                        name = name,
                        now = obj["now"]?.jsonPrimitive?.content,
                        members = members,
                    )
                }
                // GLOBAL first, then the rest in core order.
                .sortedBy { if (it.name == "GLOBAL") 0 else 1 }
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
                text = "节点",
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

        Spacer(Modifier.height(16.dp))

        if (loaded && groups.isEmpty()) {
            Text(
                text = "当前配置里没有可选的节点组",
                color = TextDim,
                fontSize = 15.sp,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(groups) { group ->
                Column {
                    Text(
                        text = group.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    group.members.forEach { node ->
                        NodeRow(
                            name = node,
                            selected = node == group.now,
                            delayMs = delays[node],
                            tested = delays.containsKey(node),
                            onSelect = {
                                val api = CoreBridge.api ?: return@NodeRow
                                scope.launch {
                                    if (api.selectProxy(group.name, node)) refresh()
                                }
                            },
                            onTest = {
                                val api = CoreBridge.api ?: return@NodeRow
                                scope.launch {
                                    delays = delays + (node to api.delay(node))
                                }
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    name: String,
    selected: Boolean,
    delayMs: Long?,
    tested: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Indigo else PanelDark)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                !tested -> "测速"
                delayMs == null -> "超时 · 再测"
                else -> "$delayMs ms"
            },
            color = if (selected) Color.White else TextDim,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onTest() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
