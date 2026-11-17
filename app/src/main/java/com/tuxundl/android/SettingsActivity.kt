package com.tuxundl.android

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.SettingsScreen
import com.tuxundl.android.ui.SettingsUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.ApplicationObserver
import com.tuxundl.android.util.UpdateChecker
import com.tuxundl.android.util.UpdateCheckResult
import com.tuxundl.android.util.UpdateFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<SettingsDesign>() {
    private val uiState = SettingsUiState()
    private val uiRequests = Channel<SettingsDesign.Request>(Channel.UNLIMITED)

    // Same permission-request pattern as MainActivity (which asks for
    // the foreground-service notification); here it is only fired when
    // the user turns the traffic alert on.
    private val notificationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { }

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

            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: ""

            val result = UpdateChecker.check(current)

            uiState.checkingUpdate = false

            when (result) {
                is UpdateCheckResult.UpdateAvailable ->
                    // Same full-screen page as the startup check.
                    uiState.updatePageInfo = result.info
                is UpdateCheckResult.UpToDate -> {
                    val base = getString(R.string.update_latest)
                    // Cross-platform hint (1.2.3): when a Windows
                    // release exists, say so — phone users may not
                    // know there is a desktop edition on the same
                    // release page.
                    uiState.updateMessage = if (result.windows != null) {
                        base + "\n" + getString(
                            R.string.update_windows_hint,
                            result.windows.version,
                        )
                    } else {
                        base
                    }
                }
                is UpdateCheckResult.Failed ->
                    uiState.updateMessage = when (val failure = result.failure) {
                        UpdateFailure.Network ->
                            getString(R.string.update_check_failed_network)
                        UpdateFailure.RateLimited ->
                            getString(R.string.update_check_failed_rate_limited)
                        is UpdateFailure.Server ->
                            getString(
                                R.string.update_check_failed_server_code,
                                failure.code,
                            )
                        UpdateFailure.Parse ->
                            getString(R.string.update_check_failed_parse)
                    }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiState.trafficAlertEnabled = uiStore.trafficAlertEnabled
        uiState.trafficAlertThresholdGb = uiStore.trafficAlertThresholdGb
        uiState.hapticsEnabled = uiStore.hapticsEnabled
        uiState.hapticsStrength = uiStore.hapticsStrength
        uiState.appLanguage = uiStore.appLanguage

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onTrafficAlertChange = { enabled ->
                            uiStore.trafficAlertEnabled = enabled
                            uiState.trafficAlertEnabled = enabled

                            if (enabled) {
                                requestNotificationPermissionIfNeeded()
                            }
                        },
                        onTrafficAlertThresholdChange = { gb ->
                            uiStore.trafficAlertThresholdGb = gb
                            uiState.trafficAlertThresholdGb = gb
                        },
                        onHapticsChange = { enabled ->
                            uiStore.hapticsEnabled = enabled
                            uiState.hapticsEnabled = enabled
                            if (enabled) {
                                com.tuxundl.android.util.Haptics.buzz(
                                    this@SettingsActivity,
                                    uiStore,
                                    com.tuxundl.android.util.Haptics.Kind.Success,
                                )
                            }
                        },
                        onHapticsStrengthChange = { strength ->
                            uiStore.hapticsStrength = strength
                            uiState.hapticsStrength = strength
                            // Preview the picked strength right away so
                            // tuning it is felt, not guessed.
                            if (uiStore.hapticsEnabled) {
                                com.tuxundl.android.util.Haptics.buzz(
                                    this@SettingsActivity,
                                    strength,
                                    com.tuxundl.android.util.Haptics.Kind.Tap,
                                )
                            }
                        },
                        onLanguageChange = { tag ->
                            uiStore.appLanguage = tag
                            uiState.appLanguage = tag

                            // Same recreate sweep the appearance page
                            // uses for palette / dark-mode changes.
                            ApplicationObserver.createdActivities.forEach {
                                it.recreate()
                            }
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
