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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.awt.Desktop
import java.net.URI

private val Indigo = Color(0xFF5B5BD6)
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

/**
 * Settings page: connection mode and the local mixed port. Changes
 * take effect on the next connect from the home page.
 */
@Composable
internal fun SettingsPage() {
    var tun by remember { mutableStateOf(SettingsStore.tunMode()) }
    var portText by remember { mutableStateOf(SettingsStore.mixedPort().toString()) }
    var note by remember { mutableStateOf<String?>(null) }
    var updateText by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "设置",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(24.dp))

        Text(text = "连接模式", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "系统代理不用管理员权限；TUN 接管全部流量，需要以管理员身份运行本程序（提权启动在后续版本补齐）。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeChip("系统代理", !tun) {
                tun = false
                SettingsStore.setTunMode(false)
                note = "已切换为系统代理，下次连接生效"
            }
            ModeChip("TUN", tun) {
                tun = true
                SettingsStore.setTunMode(true)
                note = "已切换为 TUN，下次连接生效"
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "本地混合端口", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() }.take(5) },
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "保存",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Indigo)
                    .clickable {
                        val port = portText.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            SettingsStore.setMixedPort(port)
                            note = "端口已保存，下次连接生效"
                        } else {
                            note = "端口要在 1024 到 65535 之间"
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (note != null) {
            Spacer(Modifier.height(16.dp))
            Text(text = note!!, color = TextDim, fontSize = 14.sp)
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "检查更新", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "当前版本 Windows v${WindowsUpdateCheck.CURRENT_VERSION}，只认 win-v 开头的 Windows 发布。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "检查更新",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Indigo)
                    .clickable {
                        updateText = "检查中…"
                        scope.launch {
                            val result = WindowsUpdateCheck.check()
                            if (result.newerVersion != null) {
                                updateText = "发现新版 v${result.newerVersion}"
                                updateUrl = result.pageUrl
                            } else {
                                updateText = "已是最新版"
                                updateUrl = null
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (updateText != null) {
                Text(text = updateText!!, color = TextDim, fontSize = 14.sp)
            }
            if (updateUrl != null) {
                Text(
                    text = "打开下载页",
                    color = Indigo,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            runCatching {
                                Desktop.getDesktop().browse(URI(updateUrl!!))
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Indigo else PanelDark)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
