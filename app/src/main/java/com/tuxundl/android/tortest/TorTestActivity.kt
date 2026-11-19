package com.tuxundl.android.tortest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tor connectivity spike screen (tor-spike branch only).
 * Functional only: pick a transport, start Tor, watch bootstrap,
 * test the exit through check.torproject.org.
 */
class TorTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9059FF),
                    background = Color(0xFF1C0B33),
                    surface = Color(0xFF2B1B4D)
                )
            ) {
                TorTestScreen()
            }
        }
    }
}

@Composable
private fun TorTestScreen() {
    val context = LocalContext.current
    val state by TorManager.state.collectAsState()
    var mode by remember { mutableStateOf(TorManager.Mode.SNOWFLAKE) }
    var customText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Column {
                Text("TuxunDL Tor 连通性测试", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "内置 tor 0.4.9.17 + IPtProxy 5.8.0（lyrebird / snowflake）。" +
                        "SOCKS: 127.0.0.1:${TorManager.SOCKS_PORT}。本包与正式版共存，仅用于验证国内网络下哪种传输能连通。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))

                TorManager.Mode.entries.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == candidate,
                                enabled = !state.running,
                                onClick = { mode = candidate }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == candidate,
                            onClick = { mode = candidate },
                            enabled = !state.running
                        )
                        Text(candidate.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (mode == TorManager.Mode.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("网桥行（每行一条）") },
                        placeholder = {
                            Text("例如：\nwebtunnel [2001:db8::1]:443 ABCDEF… url=https://… ver=0.0.3\nobfs4 1.2.3.4:443 ABCDEF… cert=… iat-mode=0")
                        },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !state.running
                    )
                }
                if (mode == TorManager.Mode.CUSTOM || mode == TorManager.Mode.DIRECT) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "提示：WebTunnel 目前没有公开发布的内置桥，只能通过自定义网桥粘贴桥行测试。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row {
                    Button(
                        onClick = { TorManager.start(context, mode, customText) },
                        enabled = !state.running
                    ) {
                        Text("启动 Tor")
                    }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    OutlinedButton(
                        onClick = { TorManager.stop() },
                        enabled = state.running
                    ) {
                        Text("停止")
                    }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    OutlinedButton(
                        onClick = { TorManager.testExit() },
                        enabled = state.running && !state.testing
                    ) {
                        Text(if (state.testing) "测试中…" else "测试出口")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("状态：${state.status}", style = MaterialTheme.typography.bodyLarge)
                if (state.bootstrap >= 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.bootstrap / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Bootstrap: ${state.bootstrap}%")
                }
                state.exitResult?.let { result ->
                    Spacer(Modifier.height(6.dp))
                    Text("出口测试：$result", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(16.dp))
                Text("日志", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
        }

        items(state.logs) { line ->
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}
