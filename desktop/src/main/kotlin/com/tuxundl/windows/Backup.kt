package com.tuxundl.windows

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Backup and restore: the subscription list and all settings as one
 * JSON file the user keeps wherever they like. Fetched subscription
 * configs are not part of the backup — after a restore they are
 * re-fetched from the subscription URLs. The file holds no secrets:
 * this app has none to begin with (settings are modes and ports,
 * subscriptions are names and URLs).
 */
object BackupManager {

    @Serializable
    data class Backup(
        val format: Int = 1,
        val exportedAt: Long = 0,
        val subscriptions: List<Subscription> = emptyList(),
        val settings: JsonObject = JsonObject(emptyMap()),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Write the backup file; returns how many subscriptions it holds. */
    fun export(to: File): Result<Int> = runCatching {
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            subscriptions = SubscriptionStore.load(),
            settings = SettingsStore.snapshot(),
        )
        to.writeText(json.encodeToString(backup))
        backup.subscriptions.size
    }

    /** Restore from a backup file; returns the subscription count. */
    fun import(from: File): Result<Int> = runCatching {
        val backup = json.decodeFromString<Backup>(from.readText())
        require(backup.format == 1) { "备份格式版本不支持：${backup.format}" }
        SubscriptionStore.replaceAll(backup.subscriptions)
        SettingsStore.restore(backup.settings)
        backup.subscriptions.size
    }

    /** One week in milliseconds: the automatic backup interval. */
    private const val AUTO_INTERVAL_MS = 7L * 24 * 3600 * 1000

    /** The folder weekly automatic backups are written into. */
    fun autoBackupDir(): File = File(CoreBridge.workDir, "backups")

    /**
     * The automatic backup files currently on disk, newest first.
     * Only files this app wrote (the auto-backup name pattern)
     * count — anything else the user dropped into the folder is
     * left alone and not listed.
     */
    fun listAutoBackups(): List<File> {
        val dir = autoBackupDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("tuxundl-backup-auto-") &&
                f.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * The weekly automatic backup, checked once at startup: when
     * the setting is on and the last automatic backup is over a
     * week old (or never happened), export a fresh one — same
     * format as the manual export — into the backups/ folder of
     * the data dir, named with its date and time. The check is
     * best-effort: any failure is left for the next launch, and
     * the manual export in settings is unaffected either way.
     */
    fun maybeAutoBackup() {
        if (!SettingsStore.autoBackupWeekly()) return
        val now = System.currentTimeMillis()
        if (now - SettingsStore.lastAutoBackupAt() < AUTO_INTERVAL_MS) return
        val dir = autoBackupDir().apply { mkdirs() }
        val stamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
        )
        val file = File(dir, "tuxundl-backup-auto-$stamp.json")
        if (export(file).isSuccess) {
            SettingsStore.setLastAutoBackupAt(now)
        }
    }
}

/**
 * The system file dialog for picking a backup file (save or open).
 * Returns null when the user cancels.
 */
internal fun pickBackupFile(save: Boolean): File? {
    val dialog = java.awt.FileDialog(
        null as java.awt.Frame?,
        if (save) "导出备份" else "导入备份",
        if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD,
    )
    if (save) dialog.file = "tuxundl-backup.json"
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
