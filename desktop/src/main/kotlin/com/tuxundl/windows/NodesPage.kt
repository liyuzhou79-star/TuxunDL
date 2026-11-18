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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// Palette duplicated from Main.kt for now; the pages consolidate into
// a shared theme file as they grow.
private val Indigo get() = ThemeState.accent
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)
private val StarGold = Color(0xFFFFB84D)

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
    var query by remember { mutableStateOf("") }
    var sortByDelay by remember { mutableStateOf(false) }
    var testingAll by remember { mutableStateOf(false) }
    var pickingFastest by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(SettingsStore.favoriteNodes()) }

    fun toggleFavorite(node: String) {
        favorites = if (node in favorites) {
            favorites - node
        } else {
            favorites + node
        }
        SettingsStore.setFavoriteNodes(favorites)
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (testingAll) "测速中…" else "全部测速",
                    color = if (testingAll) TextDim else Indigo,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !testingAll) {
                            val api = CoreBridge.api ?: return@clickable
                            testingAll = true
                            scope.launch {
                                // One core call per group; nodes the
                                // core does not report count as failed.
                                for (group in groups) {
                                    val result = api.groupDelay(group.name)
                                    delays = delays + group.members
                                        .associateWith { result[it] }
                                }
                                testingAll = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索节点", color = TextDim, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = "按延迟排序",
                color = if (sortByDelay) Color.White else TextDim,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sortByDelay) Indigo else PanelDark)
                    .clickable { sortByDelay = !sortByDelay }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
            // Starred nodes gathered into one group on top. A node is
            // selected through the first group that contains it; the
            // search filter and delay sort apply here just like in
            // the regular groups below.
            val favoriteHome = groups
                .flatMap { group -> group.members.map { it to group } }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.first() }
            val displayFavorites = favorites
                .filter { it in favoriteHome }
                .filter {
                    query.isBlank() ||
                        it.contains(query, ignoreCase = true)
                }
                .let { list ->
                    if (sortByDelay) {
                        list.sortedBy { delays[it] ?: Long.MAX_VALUE }
                    } else {
                        list.sorted()
                    }
                }
            if (displayFavorites.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "★ 收藏",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        displayFavorites.forEach { node ->
                            val home = favoriteHome.getValue(node)
                            NodeRow(
                                name = node,
                                selected = node == home.now,
                                delayMs = delays[node],
                                tested = delays.containsKey(node),
                                favorite = true,
                                onToggleFavorite = { toggleFavorite(node) },
                                onSelect = {
                                    val api = CoreBridge.api
                                        ?: return@NodeRow
                                    scope.launch {
                                        if (api.selectProxy(home.name, node)) {
                                            refresh()
                                        }
                                    }
                                },
                                onTest = {
                                    val api = CoreBridge.api
                                        ?: return@NodeRow
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
            items(groups) { group ->
                // Search filters members (a group-name match keeps the
                // whole group); delay sort floats the fastest tested
                // nodes up and leaves untested ones at the bottom.
                val displayMembers = group.members
                    .filter {
                        query.isBlank() ||
                            it.contains(query, ignoreCase = true) ||
                            group.name.contains(query, ignoreCase = true)
                    }
                    .let { list ->
                        if (sortByDelay) {
                            list.sortedBy { member -> delays[member] ?: Long.MAX_VALUE }
                        } else {
                            list
                        }
                    }
                if (displayMembers.isEmpty()) return@items
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (pickingFastest == group.name) {
                                "测速中…"
                            } else {
                                "选最快"
                            },
                            color = if (pickingFastest == null) {
                                Indigo
                            } else {
                                TextDim
                            },
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = pickingFastest == null) {
                                    val api = CoreBridge.api
                                        ?: return@clickable
                                    pickingFastest = group.name
                                    scope.launch {
                                        // One group test, then jump
                                        // straight to the fastest node
                                        // that answered; nodes the core
                                        // does not report are skipped.
                                        val result = api.groupDelay(group.name)
                                        delays = delays + group.members
                                            .associateWith { result[it] }
                                        val best = group.members
                                            .mapNotNull { member ->
                                                result[member]?.let {
                                                    member to it
                                                }
                                            }
                                            .minByOrNull { it.second }
                                        if (best != null &&
                                            api.selectProxy(group.name, best.first)
                                        ) {
                                            refresh()
                                        }
                                        pickingFastest = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    displayMembers.forEach { node ->
                        NodeRow(
                            name = node,
                            selected = node == group.now,
                            delayMs = delays[node],
                            tested = delays.containsKey(node),
                            favorite = node in favorites,
                            onToggleFavorite = { toggleFavorite(node) },
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
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
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
            text = if (favorite) "★" else "☆",
            color = if (favorite) StarGold else TextDim,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleFavorite() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
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
