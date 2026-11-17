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
