package com.tuxundl.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The connections page: every connection the core is holding open,
 * refreshed every three seconds, with per-connection and close-all
 * actions. This is the page the 1.1.0 update adds.
 */
@Composable
fun ConnectionsPage() {
    var connections by remember {
        mutableStateOf<List<MihomoApi.ConnectionInfo>>(emptyList())
    }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val api = CoreBridge.api

    suspend fun refresh() {
        if (api != null && CoreBridge.running) {
            connections = api.connections()
        } else {
            connections = emptyList()
        }
        loaded = true
    }

    LaunchedEffect(CoreBridge.running) {
        refresh()
        while (true) {
            delay(3000)
            refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "连接",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (connections.isNotEmpty()) {
                Text(
                    text = "全部断开",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Indigo)
                        .clickable {
                            scope.launch {
                                api?.closeAllConnections()
                                refresh()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                !CoreBridge.running -> "尚未连接"
                loaded -> "当前 ${connections.size} 条连接，每 3 秒自动刷新"
                else -> "正在读取…"
            },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (loaded && connections.isEmpty()) {
            Text(
                text = if (CoreBridge.running) {
                    "现在没有打开的连接，浏览网页或打开 App 后这里会多起来。"
                } else {
                    "先在首页点大圆按钮连上，这里才会显示连接。"
                },
                color = TextDim,
                fontSize = 14.sp,
            )
        }

        LazyColumn {
            items(connections, key = { it.id }) { conn ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conn.target.ifBlank { "（未知目标）" },
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = listOf(
                                conn.network.uppercase(),
                                conn.node,
                                conn.rule,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = "↓ ${formatBytes(conn.download)}  ↑ ${formatBytes(conn.upload)}",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "断开",
                        color = Color(0xFFFF8080),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    api?.closeConnection(conn.id)
                                    refresh()
                                }
                            }
                            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}
