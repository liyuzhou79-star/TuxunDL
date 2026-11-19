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
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.store.UiStore.Companion.mainActivityAlias
import com.tuxundl.android.ui.SettingsScreen
import com.tuxundl.android.ui.SettingsUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.AppLock
import com.tuxundl.android.util.BiometricUnlock
import com.tuxundl.android.util.ApplicationObserver
import com.tuxundl.android.util.UpdateCheckCenter
import com.tuxundl.android.util.UpdateCheckResult
import com.tuxundl.android.util.UpdateCheckSnapshot
import com.tuxundl.android.util.UpdateFailure
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.text.DateFormat
import java.util.Date

class SettingsActivity : BaseActivity<SettingsDesign>() {
    private val uiState = SettingsUiState()
    private val uiRequests = Channel<SettingsDesign.Request>(Channel.UNLIMITED)

    /**
     * True while a manual check started from this screen is running.
     * Separate from the shared state's checking flag: an automatic
     * check running in the background must not swallow a tap here —
     * the center starts a forced-fresh run for the manual tap even
     * while an automatic one is in flight.
     */
    private var manualCheckRunning = false

    // Same permission-request pattern as MainActivity (which asks for
    // the foreground-service notification); here it is only fired when
    // the user turns the traffic alert on.
    private val notificationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { }

    /**
     * The persistent override behind the privacy section's connection
     * switches (1.5.0): the same object the override settings page
     * edits, loaded once here and patched back on every toggle.
     */
    private var persistOverride: ConfigurationOverride? = null

    override suspend fun main() {
        // The settings entry shows the shared update state (1.6.0):
        // the same snapshot the home card renders. Hydrate it from
        // the persisted verdict at once, then keep mirroring it —
        // including the spinner while any check (auto or manual)
        // is running.
        val currentVersion = packageManager
            .getPackageInfo(packageName, 0).versionName ?: ""

        // TV Beta: the update entries are hidden in SettingsScreen
        // and the checker never runs — its tags are the phone
        // line's, so any verdict would be a misreport. TV updates
        // are sideloaded installs of newer Beta APKs.
        if (!BuildConfig.IS_TV) {
            UpdateCheckCenter.peek(this, currentVersion)

            launch {
                UpdateCheckCenter.state.collect { snapshot ->
                    uiState.checkingUpdate = snapshot.checking
                    uiState.updateCheckSummary = updateSummaryFor(snapshot)
                }
            }
        }

        runCatching {
            val config = withClash { queryOverride(Clash.OverrideSlot.Persist) }
            persistOverride = config
            uiState.allowLan = config.allowLan == true
            uiState.ipv6Enabled = config.ipv6 != false
            uiState.snifferEnabled = config.sniffer.enable == true
            uiState.privacyOverrideLoaded = true
        }

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
        // TV Beta: unreachable (the entry is hidden), but never run
        // the phone line's checker from the TV build.
        if (BuildConfig.IS_TV) return
        if (manualCheckRunning) return

        manualCheckRunning = true

        launch {
            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: ""

            // Manual means manual (1.6.0): the shared center runs a
            // forced-fresh check — no throttle, no ETag replay — and
            // every surface sees this same result.
            val result = UpdateCheckCenter.check(
                this@SettingsActivity,
                current,
                manual = true,
            )

            manualCheckRunning = false

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    // Same full-screen page as the startup check.
                    uiState.updatePageInfo = result.info
                    uiState.updateWindows = null
                }
                is UpdateCheckResult.UpToDate -> {
                    // The Windows hint in this result becomes a button
                    // on the result dialog (1.2.8).
                    uiState.updateWindows = result.windows
                    // Detail line (1.2.6): name the newest version and
                    // its release date, so "already latest" is checkable
                    // instead of a bare claim.
                    val parts = mutableListOf(getString(R.string.update_latest))
                    if (result.publishedAt.isNotBlank()) {
                        parts += getString(
                            R.string.update_latest_detail,
                            result.latestVersion,
                            result.publishedAt.take(10),
                        )
                    } else {
                        parts += getString(
                            R.string.update_latest_version_only,
                            result.latestVersion,
                        )
                    }
                    // Cross-platform hint (1.2.3): when a Windows
                    // release exists, say so — phone users may not
                    // know there is a desktop edition on the same
                    // release page.
                    if (result.windows != null) {
                        parts += getString(
                            R.string.update_windows_hint,
                            result.windows.version,
                        )
                    }
                    uiState.updateMessage = parts.joinToString("\n")
                }
                is UpdateCheckResult.Failed -> {
                    uiState.updateWindows = null
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
    }

    /** One-line state shown under the "check update" settings entry. */
    private fun updateSummaryFor(snapshot: UpdateCheckSnapshot): String? {
        if (snapshot.checking) {
            return getString(R.string.update_checking)
        }

        return when (val result = snapshot.result) {
            is UpdateCheckResult.UpdateAvailable ->
                getString(R.string.update_new_found, result.info.version)

            is UpdateCheckResult.UpToDate -> {
                val time = formatCheckedAt(snapshot.checkedAtMillis)

                if (time != null) {
                    getString(R.string.update_latest) + " · " +
                        getString(R.string.update_last_checked, time)
                } else {
                    getString(R.string.update_latest)
                }
            }

            is UpdateCheckResult.Failed ->
                getString(R.string.update_summary_failed)

            null -> null
        }
    }

    private fun formatCheckedAt(millis: Long): String? {
        if (millis == 0L) return null

        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(Date(millis))
    }

    /** Mutate the persistent override and write it back at once. */
    private fun patchPersistOverride(
        transform: (ConfigurationOverride) -> Unit,
    ) {
        val config = persistOverride ?: return
        transform(config)

        launch {
            runCatching {
                withClash { patchOverride(Clash.OverrideSlot.Persist, config) }
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
        uiState.hapticsCustomAmplitude = uiStore.hapticsCustomAmplitude
        uiState.appLanguage = uiStore.appLanguage
        uiState.hideAppIcon = uiStore.hideAppIcon
        uiState.hideFromRecents = uiStore.hideFromRecents
        uiState.appLockEnabled = AppLock.isConfigured(uiStore)
        uiState.appLockType = uiStore.appLockType
        uiState.appLockBiometric = uiStore.appLockBiometric
        uiState.biometricAvailable = BiometricUnlock.isAvailable(this)

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
                            // tuning it is felt, not guessed. The store
                            // overload resolves the custom preset to
                            // its stored amplitude.
                            if (uiStore.hapticsEnabled) {
                                com.tuxundl.android.util.Haptics.buzz(
                                    this@SettingsActivity,
                                    uiStore,
                                    com.tuxundl.android.util.Haptics.Kind.Tap,
                                )
                            }
                        },
                        onHapticsCustomChange = { amplitude ->
                            uiStore.hapticsCustomAmplitude = amplitude
                            uiState.hapticsCustomAmplitude = amplitude
                            if (uiStore.hapticsEnabled) {
                                com.tuxundl.android.util.Haptics.buzzAmplitude(
                                    this@SettingsActivity,
                                    amplitude,
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
                        onHideAppIconChange = { hide ->
                            uiStore.hideAppIcon = hide
                            uiState.hideAppIcon = hide

                            // Same component toggle the app settings
                            // page used before the switch moved here.
                            val newState = if (hide) {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            }
                            packageManager.setComponentEnabledSetting(
                                mainActivityAlias,
                                newState,
                                PackageManager.DONT_KILL_APP,
                            )
                        },
                        onHideFromRecentsChange = { hide ->
                            uiStore.hideFromRecents = hide
                            uiState.hideFromRecents = hide

                            // BaseActivity applies the flag at create
                            // time, so sweep a recreate like the app
                            // settings page did.
                            ApplicationObserver.createdActivities.forEach {
                                it.recreate()
                            }
                        },
                        onAllowLanChange = { allow ->
                            uiState.allowLan = allow
                            patchPersistOverride { it.allowLan = allow }
                        },
                        onIpv6Change = { on ->
                            uiState.ipv6Enabled = on
                            patchPersistOverride { it.ipv6 = on }
                        },
                        onSnifferChange = { on ->
                            uiState.snifferEnabled = on
                            patchPersistOverride { it.sniffer.enable = on }
                        },
                        onAppLockChanged = {
                            uiState.appLockEnabled =
                                AppLock.isConfigured(uiStore)
                            uiState.appLockType = uiStore.appLockType
                        },
                        onAppLockBiometricChange = { enabled ->
                            uiStore.appLockBiometric = enabled
                            uiState.appLockBiometric = enabled
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
