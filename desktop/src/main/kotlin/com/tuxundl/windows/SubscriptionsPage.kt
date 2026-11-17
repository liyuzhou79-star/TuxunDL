package com.tuxundl.windows

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)

/**
 * The subscriptions page: add a subscription by URL, fetch its config,
 * and choose which one the home page connects with. Mirrors the
 * Android edition's profile flow at skeleton depth.
 */
@Composable
fun SubscriptionsPage() {
    val subscriptions = remember {
        mutableStateListOf<Subscription>().apply {
            addAll(SubscriptionStore.load())
        }
    }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist() = SubscriptionStore.save(subscriptions.toList())

    // Opening the page also refreshes any config older than a day, in
    // the background; the list re-reads itself when that lands.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val updated = runCatching { SubscriptionStore.autoUpdateStale() }
            .getOrDefault(0)
        if (updated > 0) {
            subscriptions.clear()
            subscriptions.addAll(SubscriptionStore.load())
            status = "已自动更新 $updated 个订阅"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        Text(
            text = "订阅",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("订阅链接") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = !busy && url.isNotBlank(),
            onClick = {
                val sub = SubscriptionStore.newSubscription(
                    name = name.ifBlank { "未命名订阅" },
                    url = url.trim(),
                )
                busy = true
                status = "正在拉取…"
                scope.launch {
                    SubscriptionStore.fetch(sub)
                        .onSuccess { fetched ->
                            subscriptions.add(fetched)
                            SubscriptionStore.activate(fetched)
                            persist()
                            name = ""
                            url = ""
                            status = "已添加并设为当前订阅"
                        }
                        .onFailure {
                            status = "拉取失败：${it.message}"
                        }
                    busy = false
                }
            },
        ) {
            Text("添加并拉取")
        }

        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, color = TextDim, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(subscriptions, key = { it.id }) { sub ->
                SubscriptionCard(
                    sub = sub,
                    busy = busy,
                    onUpdate = {
                        busy = true
                        status = "正在更新 ${sub.name}…"
                        scope.launch {
                            SubscriptionStore.fetch(sub)
                                .onSuccess { fetched ->
                                    val index = subscriptions.indexOfFirst { it.id == sub.id }
                                    if (index >= 0) subscriptions[index] = fetched
                                    persist()
                                    status = "已更新 ${sub.name}"
                                }
                                .onFailure {
                                    status = "更新失败：${it.message}"
                                }
                            busy = false
                        }
                    },
                    onActivate = {
                        SubscriptionStore.activate(sub)
                        status = "已把 ${sub.name} 设为当前订阅，重新连接后生效"
                    },
                    onDelete = {
                        val remaining = SubscriptionStore.delete(sub, subscriptions.toList())
                        subscriptions.clear()
                        subscriptions.addAll(remaining)
                        persist()
                        status = "已删除 ${sub.name}"
                    },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    sub: Subscription,
    busy: Boolean,
    onUpdate: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = sub.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (sub.updatedAt > 0) {
                    "上次更新：" + SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.getDefault(),
                    ).format(Date(sub.updatedAt))
                } else {
                    "尚未拉取"
                },
                color = TextDim,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(8.dp))

            Row {
                TextButton(enabled = !busy, onClick = onUpdate) {
                    Text("更新")
                }
                TextButton(enabled = !busy, onClick = onActivate) {
                    Text("设为当前")
                }
                TextButton(enabled = !busy, onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}
