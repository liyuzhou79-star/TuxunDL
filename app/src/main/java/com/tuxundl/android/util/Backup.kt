package com.tuxundl.android.util

import android.content.Context
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.design.model.AppInfoSort
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.service.remote.IProfileManager
import com.tuxundl.android.service.util.customDnsFile
import com.tuxundl.android.service.util.customRulesDir
import com.tuxundl.android.service.util.sendProfileChanged
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore for TuxunDL 1.0.0.
 *
 * One .json file, written through the system file picker so the user
 * chooses where it goes. Contents:
 *
 * - profiles: name / type / source (subscription URL) / interval / active
 *   flag. The Age secret key is NEVER exported (it is a password), and
 *   the file carries a warning that it contains the user's subscription
 *   links. Only Url profiles can be rebuilt on restore; File and
 *   External profiles are exported for reference and counted as skipped.
 * - custom_rules: raw contents of filesDir/custom-rules/<uuid>.json,
 *   keyed by the profile uuid, written back verbatim.
 * - custom_dns: raw contents of filesDir/custom-dns.json (or null).
 * - favorites: the "group|node" keys from UiStore.
 * - prefs: theme / appearance / card toggles and order / proxy sort and
 *   a few other UiStore preferences. The disclaimer flag, the running
 *   timer, the dismissed-update version and the widget cache are NOT
 *   exported (they are per-device runtime state, not settings).
 */
const val BACKUP_FORMAT_VERSION = 1

data class BackupRestoreResult(
    val restoredProfiles: Int,
    val skippedProfiles: Int,
    val failedProfiles: Int,
    val rulesRestored: Int,
    val dnsRestored: Boolean,
    val prefsRestored: Boolean,
)

fun buildBackupJson(
    context: Context,
    uiStore: UiStore,
    profiles: List<Profile>,
    versionName: String,
): JSONObject {
    val profilesJson = JSONArray()

    for (profile in profiles) {
        profilesJson.put(
            JSONObject()
                .put("name", profile.name)
                .put("type", profile.type.name)
                .put("source", profile.source)
                .put("interval", profile.interval)
                .put("active", profile.active),
        )
    }

    val rulesJson = JSONObject()
    val rulesDir = context.customRulesDir()

    if (rulesDir.exists()) {
        for (file in rulesDir.listFiles().orEmpty()) {
            if (!file.isFile || !file.name.endsWith(".json")) continue

            runCatching { rulesJson.put(file.nameWithoutExtension, file.readText()) }
        }
    }

    val dnsFile = context.customDnsFile()
    val dnsJson: String? = if (dnsFile.exists()) {
        runCatching { dnsFile.readText() }.getOrNull()
    } else {
        null
    }

    val prefsJson = JSONObject()
        .put("theme_palette", uiStore.themePalette.name)
        .put("custom_theme_color", uiStore.customThemeColor)
        .put("dark_mode", uiStore.darkMode.name)
        .put("show_traffic_card", uiStore.showTrafficCard)
        .put("show_proxy_card", uiStore.showProxyCard)
        .put("show_profile_card", uiStore.showProfileCard)
        .put("show_providers_card", uiStore.showProvidersCard)
        .put("show_traffic_monitor_card", uiStore.showTrafficMonitorCard)
        .put("home_card_order", uiStore.homeCardOrder)
        .put("proxy_sort", uiStore.proxySort.name)
        .put("proxy_line", uiStore.proxyLine)
        .put("proxy_exclude_not_selectable", uiStore.proxyExcludeNotSelectable)
        .put("access_control_sort", uiStore.accessControlSort.name)
        .put("access_control_reverse", uiStore.accessControlReverse)
        .put("access_control_system_app", uiStore.accessControlSystemApp)
        .put("hide_from_recents", uiStore.hideFromRecents)

    val favoritesJson = JSONArray()
    for (key in uiStore.favoriteProxies) {
        favoritesJson.put(key)
    }

    return JSONObject()
        .put("tuxundl_backup_version", BACKUP_FORMAT_VERSION)
        .put("app", "TuxunDL")
        .put("version", versionName)
        .put("exported_at", System.currentTimeMillis())
        .put("profiles", profilesJson)
        .put("custom_rules", rulesJson)
        .put("custom_dns", dnsJson)
        .put("favorites", favoritesJson)
        .put("prefs", prefsJson)
}

/**
 * Restore a backup parsed from [root]. Profiles are rebuilt through the
 * profile manager (create + commit, which fetches the subscription);
 * a Url profile whose source already exists is skipped, not duplicated.
 * Rules and DNS files are written back verbatim and the active profile
 * is nudged with ACTION_PROFILE_CHANGED so the background process
 * reloads with the restored files in place.
 */
suspend fun restoreBackupJson(
    context: Context,
    uiStore: UiStore,
    root: JSONObject,
    profileManager: IProfileManager,
): BackupRestoreResult {
    if (root.optInt("tuxundl_backup_version", -1) != BACKUP_FORMAT_VERSION) {
        throw IllegalArgumentException("Not a TuxunDL backup")
    }

    var restoredProfiles = 0
    var skippedProfiles = 0
    var failedProfiles = 0
    var rulesRestored = 0
    var dnsRestored = false
    var prefsRestored = false

    val existing = runCatching { profileManager.queryAll() }.getOrNull().orEmpty()
    val existingSources = existing
        .filter { it.type == Profile.Type.Url }
        .map { it.source.trim() }
        .toSet()

    // Which profile was active in the backup, matched by (type, source)
    // once the new uuids are known.
    var activeSource: String? = null
    var activeType: String? = null

    val profilesJson = root.optJSONArray("profiles")
    if (profilesJson != null) {
        for (i in 0 until profilesJson.length()) {
            val item = profilesJson.optJSONObject(i) ?: continue
            val type = item.optString("type", "")
            val source = item.optString("source", "").trim()
            val name = item.optString("name", "").ifEmpty { "Profile" }

            if (item.optBoolean("active", false)) {
                activeSource = source
                activeType = type
            }

            // Only Url profiles can be rebuilt from a backup; File and
            // External profiles point at device-local content.
            if (type != Profile.Type.Url.name || source.isEmpty()) {
                skippedProfiles++
                continue
            }
            if (source in existingSources) {
                skippedProfiles++
                continue
            }

            val ok = runCatching {
                val uuid = profileManager.create(
                    Profile.Type.Url,
                    name,
                    source,
                    null,
                )
                profileManager.commit(uuid, null)
            }.isSuccess

            if (ok) restoredProfiles++ else failedProfiles++
        }
    }

    // Rules: raw file contents back into filesDir/custom-rules/.
    val rulesJson = root.optJSONObject("custom_rules")
    if (rulesJson != null) {
        val rulesDir = context.customRulesDir()
        rulesDir.mkdirs()

        for (uuid in rulesJson.keys()) {
            val text = rulesJson.optString(uuid, "")
            if (text.isEmpty()) continue

            runCatching {
                rulesDir.resolve("$uuid.json").writeText(text)
                rulesRestored++
            }
        }
    }

    // DNS: raw file contents back into filesDir/custom-dns.json.
    if (!root.isNull("custom_dns")) {
        val text = root.optString("custom_dns", "")
        if (text.isNotEmpty()) {
            runCatching {
                val dnsFile = context.customDnsFile()
                dnsFile.parentFile?.mkdirs()
                dnsFile.writeText(text)
                dnsRestored = true
            }
        }
    }

    // Preferences (the disclaimer flag and runtime state are excluded).
    val prefs = root.optJSONObject("prefs")
    if (prefs != null) {
        runCatching {
            prefs.optString("theme_palette", "").takeIf { it.isNotEmpty() }?.let {
                uiStore.themePalette = enumValueOrNull<ThemePalette>(it) ?: uiStore.themePalette
            }
            if (prefs.has("custom_theme_color")) {
                uiStore.customThemeColor = prefs.optInt("custom_theme_color", 0)
            }
            prefs.optString("dark_mode", "").takeIf { it.isNotEmpty() }?.let {
                uiStore.darkMode = enumValueOrNull<DarkMode>(it) ?: uiStore.darkMode
            }
            if (prefs.has("show_traffic_card")) uiStore.showTrafficCard = prefs.optBoolean("show_traffic_card")
            if (prefs.has("show_proxy_card")) uiStore.showProxyCard = prefs.optBoolean("show_proxy_card")
            if (prefs.has("show_profile_card")) uiStore.showProfileCard = prefs.optBoolean("show_profile_card")
            if (prefs.has("show_providers_card")) uiStore.showProvidersCard = prefs.optBoolean("show_providers_card")
            if (prefs.has("show_traffic_monitor_card")) uiStore.showTrafficMonitorCard = prefs.optBoolean("show_traffic_monitor_card")
            if (prefs.has("home_card_order")) uiStore.homeCardOrder = prefs.optString("home_card_order", "")
            prefs.optString("proxy_sort", "").takeIf { it.isNotEmpty() }?.let {
                uiStore.proxySort = enumValueOrNull<ProxySort>(it) ?: uiStore.proxySort
            }
            if (prefs.has("proxy_line")) uiStore.proxyLine = prefs.optInt("proxy_line")
            if (prefs.has("proxy_exclude_not_selectable")) uiStore.proxyExcludeNotSelectable = prefs.optBoolean("proxy_exclude_not_selectable")
            prefs.optString("access_control_sort", "").takeIf { it.isNotEmpty() }?.let {
                uiStore.accessControlSort = enumValueOrNull<AppInfoSort>(it) ?: uiStore.accessControlSort
            }
            if (prefs.has("access_control_reverse")) uiStore.accessControlReverse = prefs.optBoolean("access_control_reverse")
            if (prefs.has("access_control_system_app")) uiStore.accessControlSystemApp = prefs.optBoolean("access_control_system_app")
            if (prefs.has("hide_from_recents")) uiStore.hideFromRecents = prefs.optBoolean("hide_from_recents")

            prefsRestored = true
        }
    }

    // Favorites.
    val favoritesJson = root.optJSONArray("favorites")
    if (favoritesJson != null) {
        val favorites = mutableSetOf<String>()
        for (i in 0 until favoritesJson.length()) {
            favoritesJson.optString(i, "").takeIf { it.isNotEmpty() }?.let { favorites += it }
        }
        if (favorites.isNotEmpty()) {
            uiStore.favoriteProxies = favorites
        }
    }

    // Restore the active profile, then reload it so restored rules/DNS
    // take effect (only when something actually changed).
    val after = runCatching { profileManager.queryAll() }.getOrNull().orEmpty()
    val target = after.firstOrNull {
        it.type.name == activeType && it.source.trim() == activeSource
    }

    if (target != null) {
        runCatching { profileManager.setActive(target) }
    }

    if ((rulesRestored > 0 || dnsRestored) && target != null) {
        context.sendProfileChanged(target.uuid)
    }

    return BackupRestoreResult(
        restoredProfiles = restoredProfiles,
        skippedProfiles = skippedProfiles,
        failedProfiles = failedProfiles,
        rulesRestored = rulesRestored,
        dnsRestored = dnsRestored,
        prefsRestored = prefsRestored,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? {
    return runCatching { enumValueOf<T>(name) }.getOrNull()
}

/** Minimum gap between two automatic backups: 7 days. */
const val AUTO_BACKUP_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L

/** App-private directory holding the automatic backups. */
fun Context.autoBackupDir(): File = File(filesDir, "backups")

/** True when the automatic backup is enabled and one is due. */
fun isAutoBackupDue(
    uiStore: UiStore,
    now: Long = System.currentTimeMillis(),
): Boolean {
    if (!uiStore.autoBackupEnabled) return false

    val last = uiStore.lastAutoBackupAt
    return last == 0L || now - last >= AUTO_BACKUP_INTERVAL_MS
}

/**
 * Weekly automatic backup (1.3.5): when [isAutoBackupDue], build the
 * usual backup document and write it into the app-private backups/
 * directory under a dated file name, then stamp
 * [UiStore.lastAutoBackupAt]. Same format as a manual export; returns
 * true when a file was written. Callers wrap this in runCatching — a
 * failed backup must never break app startup.
 */
fun runAutoBackupIfDue(
    context: Context,
    uiStore: UiStore,
    profiles: List<Profile>,
    versionName: String,
): Boolean {
    if (!isAutoBackupDue(uiStore)) return false

    val json = buildBackupJson(context, uiStore, profiles, versionName)
    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val dir = context.autoBackupDir().apply { mkdirs() }

    dir.resolve("TuxunDL-autobackup-$stamp.json").writeText(json.toString(2))
    uiStore.lastAutoBackupAt = System.currentTimeMillis()

    return true
}

/** One automatic backup file kept in the app-private backups/ directory. */
data class AutoBackupFile(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * Automatic backups on this device, newest first (1.5.1). Only the
 * .json files the weekly auto-backup writes are listed.
 */
fun Context.listAutoBackups(): List<AutoBackupFile> {
    val dir = autoBackupDir()
    if (!dir.exists()) return emptyList()
    return dir.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".json") }
        .sortedByDescending { it.lastModified() }
        .map { AutoBackupFile(it.name, it.length(), it.lastModified()) }
}

/**
 * Delete one automatic backup (1.5.1). Only a plain file name inside
 * backups/ is accepted, so a path can never escape the directory.
 */
fun Context.deleteAutoBackup(name: String): Boolean {
    if (name.contains('/') || name.contains('\\') || !name.endsWith(".json")) return false
    val file = File(autoBackupDir(), name)
    return runCatching { file.delete() }.getOrDefault(false)
}
