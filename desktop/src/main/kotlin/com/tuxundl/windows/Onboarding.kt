package com.tuxundl.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Same palette convention as the other pages: the accent follows
// ThemeState, the neutrals are this file's own constants.
private val Indigo get() = ThemeState.accent
private val IndigoSoft get() = ThemeState.accentSoft
private val BgDark = Color(0xFF14141C)
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)
private val WarnRed = Color(0xFFFF6B6B)

private val STEP_TITLES = listOf("欢迎", "免责声明", "添加订阅", "安装与数据目录", "主题与完成")

/**
 * The disclaimer text, shared verbatim with the Android edition's
 * first-run gate (values-zh disclaimer_body) so both editions make
 * the same promise in the same words.
 */
private val DISCLAIMER_BODY = """
    TuxunDL 是一款开源免费工具，仅供学习与技术交流使用。

    本软件本身不提供任何代理或网络服务，也不出售、不分发任何节点或订阅。使用到的节点、订阅与设定档，均由使用者自行提供，使用者须对其来源与使用行为自行负责。

    使用者应遵守所在地之法律法规，不得将本软件用于任何违法用途。因使用本软件所产生的一切后果，由使用者自行承担，开发者不负任何责任。

    点击「同意并继续」即代表你已阅读、理解并接受以上内容。
""".trimIndent()

/**
 * First-run onboarding, mirroring the Android edition's five steps
 * plus the desktop-only install/data directory step. Shown by
 * Main.kt while [SettingsStore.onboardingDone] is false; finishing
 * (or agreeing through to the end) persists the flag, so it never
 * appears again. Every step reads and writes the same stores the
 * main UI uses — nothing here is a throwaway copy.
 */
@Composable
internal fun OnboardingScreen(onFinish: () -> Unit, onExit: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    // Hoisted so the footer can gate "同意并继续" on the disclaimer
    // actually having been scrolled to the end.
    val disclaimerScroll = rememberScrollState()
    val disclaimerRead = disclaimerScroll.maxValue == 0 ||
        disclaimerScroll.value >= disclaimerScroll.maxValue - 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STEP_TITLES.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                index < step -> IndigoSoft
                                index == step -> Indigo
                                else -> Color(0xFF3A3A4E)
                            },
                        ),
                )
                if (index < STEP_TITLES.lastIndex) Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "第 ${step + 1} 步，共 ${STEP_TITLES.size} 步 · ${STEP_TITLES[step]}",
                color = TextDim,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> WelcomeStep()
                1 -> DisclaimerStep(disclaimerScroll, disclaimerRead)
                2 -> SubscriptionStep()
                3 -> DataDirStep()
                else -> ThemeStep()
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                WizardTextButton("上一步") { step-- }
            }
            if (step == 1) {
                Spacer(Modifier.width(8.dp))
                WizardTextButton("不同意，退出") { onExit() }
            }
            Spacer(Modifier.weight(1f))
            when (step) {
                0 -> Button(onClick = { step = 1 }) { Text("开始") }
                1 -> Button(
                    onClick = { step = 2 },
                    enabled = disclaimerRead,
                ) { Text("同意并继续") }
                2 -> Button(onClick = { step = 3 }) { Text("下一步") }
                3 -> Button(onClick = { step = 4 }) { Text("下一步") }
                else -> Button(
                    onClick = {
                        SettingsStore.setOnboardingDone(true)
                        onFinish()
                    },
                ) { Text("完成，进入主界面") }
            }
        }
    }
}

/** Secondary wizard action, styled like the settings page's buttons. */
@Composable
private fun WizardTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PanelDark)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WindowMascot(size = 84)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "TuxunDL",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "开源免费的代理工具。节点和订阅都由你自己提供，程序只负责连接。\n接下来用五步把第一次设置走完，随时能跳过订阅那步。",
            color = TextDim,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${Platform.editionName} 版 v${DesktopUpdateCheck.CURRENT_VERSION}",
            color = TextDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DisclaimerStep(
    scroll: androidx.compose.foundation.ScrollState,
    read: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "免责声明",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelDark)
                .padding(18.dp),
        ) {
            Text(
                text = DISCLAIMER_BODY,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.verticalScroll(scroll),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (read) {
                "已读完。点「同意并继续」进入下一步。"
            } else {
                "请先把全文滚动读完，「同意并继续」才会亮起来。"
            },
            color = if (read) IndigoSoft else TextDim,
            fontSize = 13.sp,
        )
    }
}

/**
 * Optional subscription import, the same flow as the subscriptions
 * page: fetch, save, make it the active profile. Failure only
 * changes the status line — the wizard never gets stuck here.
 */
@Composable
private fun SubscriptionStep() {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var addedNames by remember {
        mutableStateOf(SubscriptionStore.load().map { it.name })
    }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "添加订阅",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "把订阅链接粘贴进来，程序会拉取配置并设为当前订阅，回到主界面点连接就能用。这一步可以跳过——直接点下一步，之后在订阅页也能加。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称（可不填）") },
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                val list = SubscriptionStore.load() + fetched
                                SubscriptionStore.save(list)
                                SubscriptionStore.activate(fetched)
                                addedNames = list.map { it.name }
                                name = ""
                                url = ""
                                status = "已添加「${fetched.name}」并设为当前订阅，还可以继续加下一个，或点下一步"
                            }
                            .onFailure {
                                status = "拉取失败：${it.message ?: "网络不通或链接不对"}。检查一下链接，或者跳过这步之后再加"
                            }
                        busy = false
                    }
                },
            ) {
                Text(if (busy) "拉取中…" else "添加并拉取")
            }
            if (addedNames.isNotEmpty()) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "已有订阅：${addedNames.joinToString("、")}",
                    color = IndigoSoft,
                    fontSize = 13.sp,
                )
            }
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(text = it, color = TextDim, fontSize = 14.sp)
        }
    }
}

/**
 * Install location (display only — the OS installer owns it) and
 * the data dir, which the user can move. The change goes through
 * [CoreBridge.changeDataDir]: existing data is copied across and
 * the new folder takes effect immediately for everything written
 * from now on. First run is the cleanest moment for this — there
 * is barely any data yet.
 */
@Composable
private fun DataDirStep() {
    var dataDir by remember { mutableStateOf(CoreBridge.workDir) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isCustom = dataDir.absolutePath != CoreBridge.defaultWorkDir.absolutePath
    val program = CoreBridge.programFile()

    fun applyChange(target: File) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                CoreBridge.changeDataDir(target)
            }
            busy = false
            if (ok) {
                dataDir = CoreBridge.workDir
                noteIsError = false
                note = "数据目录已切换，现有数据已经一并迁移过去，之后的数据都存到新目录"
            } else {
                noteIsError = true
                note = "切换没有成功，数据目录没有变。可以换一个文件夹再试"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "安装与数据目录",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "程序安装位置", color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "程序本体由安装程序管理，一般不用管它：\n${program.absolutePath}",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        Text(text = "数据目录", color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "订阅、设置、日志和核心文件都存在这里：\n${dataDir.absolutePath}" +
                if (isCustom) "（自定义）" else "（默认位置）",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "想把数据放到别处（比如空间更大的盘），现在就可以改：刚开始用还没多少数据，迁移最干净。之后在设置页的数据文件夹一节也能改。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    val picked = Platform.pickDirectory("选择数据目录")
                    if (picked != null) applyChange(picked)
                },
            ) {
                Text(if (busy) "迁移中…" else "更改数据目录")
            }
            if (isCustom) {
                WizardTextButton("恢复默认目录") {
                    if (!busy) applyChange(CoreBridge.defaultWorkDir)
                }
            }
        }
        note?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = if (noteIsError) WarnRed else IndigoSoft,
                fontSize = 14.sp,
            )
        }
    }
}

/** Theme pick (the same presets and store as the settings page). */
@Composable
private fun ThemeStep() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "主题与完成",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "选个主题色：整个应用的强调色——按钮、选中状态、高亮文字都跟着变。点一下立刻生效，之后在设置页随时能换。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeState.presets.forEach { (name, color) ->
                val selected = ThemeState.accent == color
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape,
                            )
                            .clickable { ThemeState.select(color) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = name,
                        color = if (selected) Color.White else TextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "都设置完了。点「完成，进入主界面」就能开始用；订阅还没加的话，主界面点连接时会提醒你先去订阅页添加。",
            color = TextDim,
            fontSize = 13.sp,
        )
    }
}
