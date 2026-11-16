package com.tuxundl.android

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.MainScreen
import com.tuxundl.android.ui.MainUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.startClashService
import com.tuxundl.android.util.stopClashService
import com.tuxundl.android.util.withClash
import com.tuxundl.android.util.withProfile
import com.tuxundl.android.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.tuxundl.android.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    private val uiState = MainUiState()
    private val uiRequests = Channel<MainDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> fetch()
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        MainDesign.Request.OpenLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        MainDesign.Request.OpenHelp ->
                            startActivity(HelpActivity::class.intent)
                        MainDesign.Request.OpenAbout ->
                            uiState.aboutVersion = queryAppVersionName()
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        uiState.clashRunning = clashRunning

        uiState.showTraffic = uiStore.showTrafficCard
        uiState.showProxy = uiStore.showProxyCard
        uiState.showProfile = uiStore.showProfileCard
        uiState.showProviders = uiStore.showProvidersCard

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        uiState.mode = state.mode
        uiState.hasProviders = providers.isNotEmpty()

        val profileName = withProfile { queryActive()?.name }
        uiState.profileName = profileName
    }

    private suspend fun fetchTraffic() {
        val total = withClash { queryTrafficTotal() }
        uiState.traffic = total
    }

    private suspend fun startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            val result = uiState.snackbarHostState.showSnackbar(
                message = getString(DesignR.string.no_profile_selected),
                actionLabel = getString(DesignR.string.profiles),
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed) {
                startActivity(ProfilesActivity::class.intent)
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            uiState.snackbarHostState.showSnackbar(
                message = getString(DesignR.string.unable_to_start_vpn),
                duration = SnackbarDuration.Long,
            )
        }
    }

    override fun onStopped(cause: String?) {
        super.onStopped(cause)

        // BaseActivity shows stop causes through the Design toast, which the
        // Compose home screen no longer has; surface it as a snackbar instead.
        if (cause != null && activityStarted) {
            launch {
                uiState.snackbarHostState.showSnackbar(message = cause)
            }
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setupShortcuts()
    }

    private fun setupShortcuts() {
        // Skip dynamic shortcut setup when the app icon is hidden.
        if (uiStore.hideAppIcon) return

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_all))
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_on))
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_toggle_off))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }
}
