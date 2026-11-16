package com.tuxundl.android

import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.design.MetaFeatureSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.MetaFeatureSettingsScreen
import com.tuxundl.android.ui.OverrideUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.clashDir
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.tuxundl.android.design.R

class MetaFeatureSettingsActivity : BaseActivity<MetaFeatureSettingsDesign>() {
    private val uiState = OverrideUiState()
    private val uiRequests = Channel<MetaFeatureSettingsDesign.Request>(Channel.UNLIMITED)
    private var configuration by mutableStateOf<ConfigurationOverride?>(null)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }
        this.configuration = configuration

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        MetaFeatureSettingsDesign.Request.ResetOverride -> {
                            uiState.showResetConfirm = true
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoIp -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoIp)
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoSite -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoSite)
                        }
                        MetaFeatureSettingsDesign.Request.ImportCountry -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportCountry)
                        }
                        MetaFeatureSettingsDesign.Request.ImportASN -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportASN)
                        }
                    }
                }
            }
        }
    }

    private fun resetOverride() {
        defer {
            withClash {
                clearOverride(Clash.OverrideSlot.Persist)
            }
        }

        finish()
    }

    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )

    private suspend fun importGeoFile(uri: Uri?, importType: MetaFeatureSettingsDesign.Request) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else ""
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(getString(R.string.geofile_unknown_db_format_message,
                            validDatabaseExtensions.joinToString("/")))
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                    return
                }
                val outputFileName = when (importType) {
                    MetaFeatureSettingsDesign.Request.ImportGeoIp ->
                        "geoip$ext"
                    MetaFeatureSettingsDesign.Request.ImportGeoSite ->
                        "geosite$ext"
                    MetaFeatureSettingsDesign.Request.ImportCountry ->
                        "country$ext"
                    MetaFeatureSettingsDesign.Request.ImportASN ->
                        "ASN$ext"
                    else -> ""
                }

                withContext(Dispatchers.IO) {
                    val outputFile = File(clashDir, outputFileName)
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs ->
                            ins?.copyTo(outs)
                        }
                    }
                }
                Toast.makeText(this, getString(R.string.geofile_imported, displayName),
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val config = configuration
                    if (config != null) {
                        MetaFeatureSettingsScreen(
                            configuration = config,
                            state = uiState,
                            onRequest = { request -> uiRequests.trySend(request) },
                            onResetConfirmed = { resetOverride() },
                            onBack = { finish() },
                        )
                    }
                }
            }
        }
    }
}
