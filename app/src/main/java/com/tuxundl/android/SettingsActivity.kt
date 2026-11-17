package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.SettingsScreen
import com.tuxundl.android.ui.SettingsUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.UpdateChecker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<SettingsDesign>() {
    private val uiState = SettingsUiState()
    private val uiRequests = Channel<SettingsDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                        SettingsDesign.Request.StartAppearance ->
                            startActivity(AppearanceActivity::class.intent)
                        SettingsDesign.Request.StartRules ->
                            startActivity(RulesActivity::class.intent)
                        SettingsDesign.Request.StartAccessControl ->
                            startActivity(AccessControlActivity::class.intent)
                        SettingsDesign.Request.StartCustomDns ->
                            startActivity(DnsSettingsActivity::class.intent)
                        SettingsDesign.Request.StartBackup ->
                            startActivity(BackupActivity::class.intent)
                        SettingsDesign.Request.CheckUpdate ->
                            checkUpdate()
                    }
                }
            }
        }
    }

    private fun checkUpdate() {
        if (uiState.checkingUpdate) return

        launch {
            uiState.checkingUpdate = true

            val info = UpdateChecker.fetchLatest()

            uiState.checkingUpdate = false

            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: ""

            when {
                info == null ->
                    uiState.updateMessage = getString(R.string.update_check_failed)
                UpdateChecker.isNewer(info.version, current) -> {
                    uiState.updateMessage =
                        getString(R.string.update_new_found, info.version)
                    uiState.updateDownloadUrl = info.downloadUrl ?: info.pageUrl
                }
                else ->
                    uiState.updateMessage = getString(R.string.update_latest)
            }
        }
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
