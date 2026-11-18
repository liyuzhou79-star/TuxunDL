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
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

private val Indigo get() = ThemeState.accent
private val PanelDark = Color(0xFF1E1E2A)
private val TextDim = Color(0xFF9A9AB0)
private val WarnRed = Color(0xFFC04545)

/**
 * Settings page: connection mode and the local mixed port. Changes
 * take effect on the next connect from the home page.
 */
@Composable
internal fun SettingsPage() {
    var tun by remember { mutableStateOf(SettingsStore.tunMode()) }
    var autoStart by remember { mutableStateOf(SettingsStore.autoStart()) }
    var autoConnect by remember { mutableStateOf(SettingsStore.autoConnect()) }
    var closeToTray by remember { mutableStateOf(SettingsStore.closeToTray()) }
    var autoSort by remember { mutableStateOf(SettingsStore.autoSortAfterTest()) }
    var autoBackup by remember { mutableStateOf(SettingsStore.autoBackupWeekly()) }
    var lastAutoBackup by remember {
        mutableStateOf(SettingsStore.lastAutoBackupAt())
    }
    // Auto-backup files on disk, read when the page opens and
    // re-read after each delete.
    var autoBackupFiles by remember {
        mutableStateOf(BackupManager.listAutoBackups())
    }
    // Two-step delete for one auto-backup file: the first click
    // arms (stores the file name), the second deletes; the armed
    // state clears itself after a few seconds.
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var intervalHours by remember {
        mutableStateOf(SettingsStore.autoUpdateIntervalHours())
    }
    var logLevel by remember { mutableStateOf(SettingsStore.logLevel()) }
    var portText by remember { mutableStateOf(SettingsStore.mixedPort().toString()) }
    var note by remember { mutableStateOf<String?>(null) }
    var updateText by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var coreVersion by remember { mutableStateOf<String?>(null) }
    // Two-step confirm for "恢复默认设置", same pattern as the log
    // page's clear button: first click arms, second click executes.
    var confirmReset by remember { mutableStateOf(false) }
    // The data dir as this page displays it; refreshed after a
    // successful change (CoreBridge.workDir itself is the truth).
    var dataDir by remember { mutableStateOf(CoreBridge.workDir) }
    var dataDirBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coreVersion = runCatching { CoreBridge.api?.version() }.getOrNull()
    }

    // The armed "confirm delete" state disarms itself after 3 seconds.
    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            delay(3000)
            pendingDelete = null
        }
    }

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
            text = when {
                Platform.isMac -> "系统代理不用管理员权限（需要管理员账号），点连接就自动设置好；Mac 版 1.0.0 暂不支持 TUN 模式。"
                Platform.isLinux -> "系统代理不用管理员权限；TUN 接管全部流量，点连接时会通过 pkexec 弹密码框、只把核心以管理员权限启动（程序本身不提权，需要系统装有 pkexec）。"
                else -> "系统代理不用管理员权限；TUN 接管全部流量，点连接时会自动请求管理员权限并重启本程序（系统会弹确认框）。"
            },
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
                if (Platform.isMac) {
                    // Mac 1.0.0 has no TUN support; say so instead
                    // of switching to a mode that cannot connect.
                    note = "Mac 版 1.0.0 暂不支持 TUN 模式，请用系统代理模式"
                } else {
                    tun = true
                    SettingsStore.setTunMode(true)
                    note = "已切换为 TUN，下次连接生效"
                }
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

        Text(text = "开机自启", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                Platform.isMac -> "登录 macOS 时自动打开 TuxunDL（写入 ~/Library/LaunchAgents，只对当前用户生效，不需要管理员权限）。"
                Platform.isLinux -> "登录桌面时自动打开 TuxunDL（写入 ~/.config/autostart，只对当前用户生效，不需要管理员权限）。"
                else -> "登录 Windows 时自动打开 TuxunDL（只对当前用户生效，不需要管理员权限）。"
            },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        ModeChip(if (autoStart) "已开启" else "已关闭", autoStart) {
            autoStart = !autoStart
            SettingsStore.setAutoStart(autoStart)
            note = if (autoStart) "开机自启已开启" else "开机自启已关闭"
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "启动时自动连接", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                Platform.isMac -> "打开 TuxunDL 时，如果已有订阅配置就自动连上（系统代理模式；Mac 版暂不支持 TUN）。"
                Platform.isLinux -> "打开 TuxunDL 时，如果已有订阅配置就自动连上。TUN 模式会先通过 pkexec 请求管理员密码来启动核心。"
                else -> "打开 TuxunDL 时，如果已有订阅配置就自动连上。TUN 模式仍会先弹管理员权限确认并重启本程序。"
            },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        ModeChip(if (autoConnect) "已开启" else "已关闭", autoConnect) {
            autoConnect = !autoConnect
            SettingsStore.setAutoConnect(autoConnect)
            note = if (autoConnect) "启动时自动连接已开启" else "启动时自动连接已关闭"
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "关闭时最小化到系统匣", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "开启时，点窗口的关闭按钮只是把窗口藏到系统匣，程序和连接继续在后台运行；从系统匣菜单可以重新打开主窗口或彻底退出。关掉后，点关闭会直接退出程序（退出前会先断开连接）。改动立刻生效，不用重启。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        ModeChip(if (closeToTray) "已开启" else "已关闭", closeToTray) {
            closeToTray = !closeToTray
            SettingsStore.setCloseToTray(closeToTray)
            note = if (closeToTray) {
                "关闭窗口时将最小化到系统匣"
            } else {
                "关闭窗口时将直接退出程序"
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "测速后自动按延迟排序", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "开启时，节点页每次测速（单个节点、全部测速、选最快）一结束就自动按延迟从低到高排好，最快的浮到最上面，不用再手动点节点页的「按延迟排序」。默认关闭，关着的时候行为和以前完全一样。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        ModeChip(if (autoSort) "已开启" else "已关闭", autoSort) {
            autoSort = !autoSort
            SettingsStore.setAutoSortAfterTest(autoSort)
            note = if (autoSort) {
                "测速后将自动按延迟排序"
            } else {
                "测速后自动排序已关闭"
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "订阅自动更新间隔", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "订阅配置超过这个时长没刷新时，程序会在启动时和打开订阅页时自动更新。节点变得快可以选短一点，订阅稳定就选长一点省流量。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(6, 12, 24, 48).forEach { hours ->
                ModeChip("$hours 小时", intervalHours == hours) {
                    intervalHours = hours
                    SettingsStore.setAutoUpdateIntervalHours(hours)
                    note = "订阅自动更新间隔已设为 $hours 小时"
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "日志级别", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "核心写日志的详细程度。info 是日常默认档；warning、error 只记问题，日志更干净；silent 基本不记。改完不用重启，下次连接时生效（正在连接的话，断开重连一次）。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("info", "warning", "error", "silent").forEach { level ->
                ModeChip(level, logLevel == level) {
                    logLevel = level
                    SettingsStore.setLogLevel(level)
                    note = "日志级别已设为 $level，下次连接生效"
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "主题色", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "整个应用的强调色：按钮、选中状态、高亮文字都跟着变。点一下立刻全应用生效，重启也不会丢。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
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
                            .clickable {
                                ThemeState.select(color)
                                note = "主题色已换成$name"
                            },
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

        Spacer(Modifier.height(28.dp))

        Text(text = "数据备份", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "把订阅列表和全部设置导出成一个 JSON 文件，换电脑或重装时导入回来。订阅的配置文件不在备份里，导入后到订阅页点「更新全部」重新拉取一次，再选一下当前订阅就行。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "导出备份",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Indigo)
                    .clickable {
                        val file = pickBackupFile(save = true)
                        if (file != null) {
                            BackupManager.export(file)
                                .onSuccess { count ->
                                    note = "备份已导出到 ${file.name}" +
                                        "（含 $count 个订阅）"
                                }
                                .onFailure {
                                    note = "导出失败：${it.message}"
                                }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                text = "导入备份",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelDark)
                    .clickable {
                        val file = pickBackupFile(save = false)
                        if (file != null) {
                            BackupManager.import(file)
                                .onSuccess { count ->
                                    tun = SettingsStore.tunMode()
                                    autoStart = SettingsStore.autoStart()
                                    autoConnect = SettingsStore.autoConnect()
                                    closeToTray = SettingsStore.closeToTray()
                                    autoBackup =
                                        SettingsStore.autoBackupWeekly()
                                    lastAutoBackup =
                                        SettingsStore.lastAutoBackupAt()
                                    intervalHours =
                                        SettingsStore.autoUpdateIntervalHours()
                                    logLevel = SettingsStore.logLevel()
                                    portText =
                                        SettingsStore.mixedPort().toString()
                                    note = "已恢复 $count 个订阅和全部设置；" +
                                        "请到订阅页点「更新全部」重新拉取配置"
                                }
                                .onFailure {
                                    note = "导入失败：${it.message}"
                                }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeChip(if (autoBackup) "每周自动备份：已开启" else "每周自动备份：已关闭", autoBackup) {
                autoBackup = !autoBackup
                SettingsStore.setAutoBackupWeekly(autoBackup)
                note = if (autoBackup) {
                    "每周自动备份已开启：启动时超过 7 天没备就自动导出一份到数据文件夹的 backups 里"
                } else {
                    "每周自动备份已关闭"
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "开启后，程序每次启动时检查一次：距上次自动备份超过 7 天（或从没备过），就用和手动导出相同的格式自动存一份到数据文件夹的 backups 子文件夹，文件名带日期时间。只存在本机，不上传任何地方。" +
                if (lastAutoBackup > 0) {
                    "上次自动备份：" + java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        java.util.Locale.getDefault(),
                    ).format(java.util.Date(lastAutoBackup)) + "。"
                } else {
                    ""
                },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "自动备份文件", color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "打开备份文件夹",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelDark)
                    .clickable {
                        val dir = BackupManager.autoBackupDir()
                        dir.mkdirs()
                        note = if (Platform.openInFileManager(dir)) {
                            "已打开自动备份文件夹"
                        } else {
                            "没能自动打开，文件夹在数据文件夹的 backups 子文件夹里"
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (autoBackupFiles.isEmpty()) {
            Text(
                text = "还没有自动备份文件。开启上面的每周自动备份后，存下来的备份会列在这里。",
                color = TextDim,
                fontSize = 13.sp,
            )
        } else {
            autoBackupFiles.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                java.util.Locale.getDefault(),
                            ).format(java.util.Date(file.lastModified())) +
                                " · " + fmtFileSize(file.length()),
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = if (pendingDelete == file.name) {
                            "确认删除？"
                        } else {
                            "删除"
                        },
                        color = if (pendingDelete == file.name) {
                            WarnRed
                        } else {
                            TextDim
                        },
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (pendingDelete == file.name) {
                                    val deleted = runCatching {
                                        file.delete()
                                    }.getOrDefault(false)
                                    pendingDelete = null
                                    autoBackupFiles =
                                        BackupManager.listAutoBackups()
                                    note = if (deleted) {
                                        "已删除自动备份 ${file.name}"
                                    } else {
                                        "删除没有成功，文件可能正被占用"
                                    }
                                } else {
                                    pendingDelete = file.name
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "数据文件夹", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "订阅、设置、日志和核心文件都存在这个文件夹里。要手动备份、找日志排查问题或者卸载前清理时，先到这里。当前位置：" +
                dataDir.absolutePath +
                if (dataDir.absolutePath !=
                    CoreBridge.defaultWorkDir.absolutePath
                ) {
                    "（自定义）"
                } else {
                    "（默认位置）"
                },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "打开数据文件夹",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelDark)
                    .clickable {
                        CoreBridge.workDir.mkdirs()
                        note = if (Platform.openInFileManager(CoreBridge.workDir)) {
                            "已打开数据文件夹"
                        } else {
                            "没能自动打开，文件夹位置就是上面的路径，可以手动打开"
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Text(
                text = if (dataDirBusy) "正在迁移…" else "更改数据目录",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelDark)
                    .clickable {
                        if (dataDirBusy) return@clickable
                        val picked = Platform.pickDirectory("选择数据目录")
                            ?: return@clickable
                        dataDirBusy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                CoreBridge.changeDataDir(picked)
                            }
                            dataDirBusy = false
                            if (ok) {
                                dataDir = CoreBridge.workDir
                                note = "数据目录已切换到 ${dataDir.absolutePath}，" +
                                    "现有数据已经一并迁移过去；当前正在运行的连接不受影响，" +
                                    "重启程序后核心也改用新目录"
                            } else {
                                note = "切换没有成功，数据目录没有变，可以换一个文件夹再试"
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (dataDir.absolutePath !=
                CoreBridge.defaultWorkDir.absolutePath
            ) {
                Text(
                    text = "恢复默认目录",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PanelDark)
                        .clickable {
                            if (dataDirBusy) return@clickable
                            dataDirBusy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    CoreBridge.changeDataDir(
                                        CoreBridge.defaultWorkDir,
                                    )
                                }
                                dataDirBusy = false
                                if (ok) {
                                    dataDir = CoreBridge.workDir
                                    note = "数据目录已恢复默认位置，数据已经一并迁移回去；" +
                                        "重启程序后核心也改用默认目录"
                                } else {
                                    note = "恢复没有成功，数据目录没有变"
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "系统代理维护", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "如果程序异常退出（崩溃、被强制结束），系统代理可能还开着并指向本程序，浏览器会因此上不了网。首页检测到这种情况会提醒你一键清理；这里也可以随时手动重置一次，走的是和断开连接时同一条清理路径，只关系统代理开关。" +
                when {
                    Platform.isLinux -> "Linux 下自动管理的是 GNOME 系统代理；非 GNOME 桌面请在系统设置里手动填写 127.0.0.1 与上面的混合端口。"
                    Platform.isMac -> "Mac 下自动管理的是当前网络服务（一般是 Wi-Fi）的代理；非管理员账号请在系统设置里手动填写 127.0.0.1 与上面的混合端口。"
                    else -> ""
                },
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "重置系统代理",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDark)
                .clickable {
                    val ok = SystemProxy.disable()
                    note = when {
                        !SystemProxy.isSupported() ->
                            "当前桌面环境不支持自动管理系统代理（Linux 仅支持 GNOME），请在系统设置里手动处理"
                        ok -> "系统代理已重置（开关已关闭）"
                        else -> "重置没有成功，系统代理可能还开着"
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(text = "检查更新", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "当前版本 ${Platform.editionName} v${DesktopUpdateCheck.CURRENT_VERSION}，只认 ${DesktopUpdateCheck.TAG_PREFIX} 开头的 ${Platform.editionName} 发布。",
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
                            when (val result = DesktopUpdateCheck.check()) {
                                is DesktopUpdateCheck.Result.UpdateAvailable -> {
                                    updateText = "发现新版 v${result.version}"
                                    updateUrl = result.pageUrl
                                }
                                DesktopUpdateCheck.Result.UpToDate -> {
                                    updateText = "已是最新版"
                                    updateUrl = null
                                }
                                is DesktopUpdateCheck.Result.Failed -> {
                                    updateText = "检查失败：网络或限流，稍后再试"
                                    updateUrl = null
                                }
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

        Spacer(Modifier.height(28.dp))

        Text(text = "恢复默认设置", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "把连接模式、混合端口和这一页的全部开关、主题色恢复成默认。订阅、收藏的节点、置顶的订阅都不会动。模式和端口在下次连接时生效。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                text = if (confirmReset) "再点一次确认恢复" else "恢复默认设置",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (confirmReset) WarnRed else PanelDark)
                    .clickable {
                        if (!confirmReset) {
                            confirmReset = true
                        } else {
                            SettingsStore.resetToDefaults()
                            ThemeState.resetAccent()
                            tun = SettingsStore.tunMode()
                            autoStart = SettingsStore.autoStart()
                            autoConnect = SettingsStore.autoConnect()
                            closeToTray = SettingsStore.closeToTray()
                            autoSort = SettingsStore.autoSortAfterTest()
                            autoBackup = SettingsStore.autoBackupWeekly()
                            intervalHours =
                                SettingsStore.autoUpdateIntervalHours()
                            logLevel = SettingsStore.logLevel()
                            portText = SettingsStore.mixedPort().toString()
                            confirmReset = false
                            note = "已恢复默认设置；订阅和收藏没有动，" +
                                "模式与端口在下次连接时生效"
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (confirmReset) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "取消",
                    color = TextDim,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirmReset = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(text = "关于", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "应用版本：${Platform.editionName} v${DesktopUpdateCheck.CURRENT_VERSION}",
            color = TextDim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "核心版本：" + (coreVersion ?: "核心未运行"),
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    scope.launch {
                        coreVersion = runCatching {
                            CoreBridge.api?.version()
                        }.getOrNull()
                    }
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "核心没在运行时显示「核心未运行」；连上之后点一下这行可以重新查询。",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "项目主页",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PanelDark)
                .clickable {
                    runCatching {
                        Desktop.getDesktop().browse(
                            URI("https://github.com/liyuzhou79-star/TuxunDL"),
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Short human-readable size for one backup file. */
private fun fmtFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
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
