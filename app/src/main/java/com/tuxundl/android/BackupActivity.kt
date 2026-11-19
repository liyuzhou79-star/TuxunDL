package com.tuxundl.android

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.tuxundl.android.design.AppSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.BackupScreen
import com.tuxundl.android.ui.BackupUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.AutoBackupFile
import com.tuxundl.android.util.autoBackupDir
import com.tuxundl.android.util.buildBackupJson
import com.tuxundl.android.util.deleteAutoBackup
import com.tuxundl.android.util.listAutoBackups
import com.tuxundl.android.util.restoreBackupJson
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hosts backup & restore.
 *
 * Export builds one JSON document (profiles, custom rules, custom DNS,
 * favorites and preferences — see Backup.kt) and hands it to the system
 * file picker; import reads a picked file back, validates the
 * `tuxundl_backup_version` field and restores it piece by piece,
 * reporting how many items were restored and how many were skipped.
 * Uses AppSettingsDesign as its (never instantiated) design type, like
 * RulesActivity and DnsSettingsActivity.
 */
class BackupActivity : BaseActivity<AppSettingsDesign>() {
    private val uiState = BackupUiState()

    /** Bytes of the backup waiting to be written by the file picker. */
    private var pendingExport: ByteArray? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        lifecycleScope.launch {
            val bytes = pendingExport
            pendingExport = null

            if (uri == null || bytes == null) return@launch

            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Cannot open output")
                }
            }.isSuccess

            if (ok) {
                uiState.resultLines = listOf(getString(R.string.backup_exported))
            } else {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_export_failed),
                )
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        lifecycleScope.launch {
            uiState.working = true

            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    } ?: throw IllegalStateException("Cannot open input")
                }

                val result = withProfile {
                    restoreBackupJson(
                        this@BackupActivity,
                        uiStore,
                        JSONObject(text),
                        this,
                    )
                }

                uiState.resultLines = listOf(
                    getString(
                        R.string.backup_result_profiles,
                        result.restoredProfiles,
                        result.skippedProfiles,
                        result.failedProfiles,
                    ),
                    getString(R.string.backup_result_rules, result.rulesRestored),
                    getString(
                        if (result.dnsRestored) {
                            R.string.backup_result_dns_yes
                        } else {
                            R.string.backup_result_dns_no
                        },
                    ),
                    getString(
                        if (result.prefsRestored) {
                            R.string.backup_result_prefs_yes
                        } else {
                            R.string.backup_result_prefs_no
                        },
                    ),
                    getString(
                        if (result.serviceRestored) {
                            R.string.backup_result_service_yes
                        } else {
                            R.string.backup_result_service_no
                        },
                    ),
                )
            } catch (e: Exception) {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_invalid),
                )
            } finally {
                uiState.working = false
            }
        }
    }

    override suspend fun main() {
        // No design events to handle; all work is driven from the UI
        // callbacks in onCreate (lifecycleScope) above.
    }

    /** Reload the automatic backup file list into the UI state (1.5.1). */
    private fun refreshAutoBackups() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { listAutoBackups() }
            uiState.autoBackups = files
        }
    }

    /**
     * Export one automatic backup (1.5.1): read the file and hand it to
     * the same system file picker the manual export uses, so the user
     * chooses where it goes. No FileProvider change needed.
     */
    private fun onExportAutoBackup(file: AutoBackupFile) {
        if (uiState.working) return

        lifecycleScope.launch {
            uiState.working = true

            try {
                val bytes = withContext(Dispatchers.IO) {
                    File(autoBackupDir(), file.name).readBytes()
                }

                pendingExport = bytes
                exportLauncher.launch(file.name)
            } catch (e: Exception) {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_export_failed),
                )
            } finally {
                uiState.working = false
            }
        }
    }

    /** Delete one automatic backup (1.5.1); the UI already asked twice. */
    private fun onDeleteAutoBackup(file: AutoBackupFile) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { deleteAutoBackup(file.name) }

            if (ok) {
                refreshAutoBackups()
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_auto_deleted),
                )
            } else {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_auto_delete_failed),
                )
            }
        }
    }

    private fun onExport() {
        if (uiState.working) return

        lifecycleScope.launch {
            uiState.working = true

            try {
                val profiles = withProfile { queryAll() }
                val versionName = packageManager
                    .getPackageInfo(packageName, 0).versionName ?: ""

                val json = withContext(Dispatchers.IO) {
                    buildBackupJson(this@BackupActivity, uiStore, profiles, versionName)
                }

                pendingExport = json.toString(2).toByteArray(Charsets.UTF_8)

                val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                exportLauncher.launch("TuxunDL-backup-$stamp.json")
            } catch (e: Exception) {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.backup_export_failed),
                )
            } finally {
                uiState.working = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiState.autoBackupEnabled = uiStore.autoBackupEnabled
        uiState.lastAutoBackupAt = uiStore.lastAutoBackupAt

        refreshAutoBackups()

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BackupScreen(
                        state = uiState,
                        onExport = { onExport() },
                        onImport = { importLauncher.launch(arrayOf("application/json")) },
                        onAutoBackupToggle = { enabled ->
                            uiStore.autoBackupEnabled = enabled
                            uiState.autoBackupEnabled = enabled
                        },
                        onExportAutoBackup = { file -> onExportAutoBackup(file) },
                        onDeleteAutoBackup = { file -> onDeleteAutoBackup(file) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
