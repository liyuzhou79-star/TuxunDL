package com.tuxundl.android

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import com.tuxundl.android.core.Clash
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.DisclaimerScreen
import com.tuxundl.android.ui.MainScreen
import com.tuxundl.android.ui.MainUiState
import com.tuxundl.android.ui.TrafficSample
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.ui.trafficDownloadBytes
import com.tuxundl.android.ui.trafficUploadBytes
import com.tuxundl.android.util.DailyTrafficStore
import com.tuxundl.android.util.UpdateChecker
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
import kotlin.math.roundToLong
import com.tuxundl.android.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    private val uiState = MainUiState()
    private val uiRequests = Channel<MainDesign.Request>(Channel.UNLIMITED)

    // Previous packed-total sample, for deriving per-second rates.
    private var prevTrafficUp = 0L
    private var prevTrafficDown = 0L
    private var prevTrafficAt = 0L

    // Throttle widget persistence: write rates at most every 5 ticks.
    private var widgetTickCount = 0

    // Per-day forwarded traffic (filesDir/traffic-daily.json). Only
    // traffic seen while this activity is alive is counted; the file is
    // written at most once a minute plus once when the activity stops.
    private lateinit var dailyTraffic: DailyTrafficStore
    private var dailyTickCount = 0

    override suspend fun main() {
        dailyTraffic = DailyTrafficStore(filesDir.resolve("traffic-daily.json"))

        withContext(Dispatchers.IO) {
            dailyTraffic.load()
        }
        refreshDailyState()

        fetch()
        checkUpdateOnStartup()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart -> {
                            uiStore.runningSince = System.currentTimeMillis()
                            resetTrafficSampling()
                            fetch()
                            TuxunWidgetProvider.requestUpdate(this@MainActivity)
                        }
                        Event.ClashStop -> {
                            uiStore.runningSince = 0L
                            resetTrafficSampling()
                            uiState.trafficSamples = emptyList()
                            fetch()
                            TuxunWidgetProvider.requestUpdate(this@MainActivity)
                        }
                        Event.ActivityStart,
                        Event.ServiceRecreated,
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
                        is MainDesign.Request.SetMode -> {
                            launch {
                                uiState.snackbarHostState.showSnackbar(
                                    message = getString(DesignR.string.mode_switch_tips),
                                    duration = SnackbarDuration.Short,
                                )
                            }

                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)

                                o.mode = it.mode

                                patchOverride(Clash.OverrideSlot.Session, o)
                            }

                            uiState.mode = it.mode
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
                        MainDesign.Request.TestCurrentGroup -> {
                            val group = uiState.mainGroupName

                            if (group != null && !uiState.testingNode) {
                                uiState.testingNode = true

                                launch {
                                    try {
                                        withClash { healthCheck(group) }
                                        fetchCurrentNode()
                                    } catch (e: Exception) {
                                        // Core stopped mid-test: keep last delay.
                                    } finally {
                                        uiState.testingNode = false
                                    }
                                }
                            }
                        }
                        MainDesign.Request.TestAllGroups -> {
                            if (!uiState.testingAll) {
                                uiState.testingAll = true

                                launch {
                                    try {
                                        val names = withClash {
                                            queryProxyGroupNames(false)
                                        }

                                        for (name in names) {
                                            withClash { healthCheck(name) }
                                        }

                                        fetchCurrentNode()
                                    } catch (e: Exception) {
                                        // Core stopped mid-test: keep last delays.
                                    } finally {
                                        uiState.testingAll = false
                                    }
                                }
                            }
                        }
                        MainDesign.Request.DismissUpdate -> {
                            uiState.updateInfo?.let {
                                uiStore.dismissedUpdate = it.version
                            }
                            uiState.updateInfo = null
                        }
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
        uiState.runningSince = uiStore.runningSince
        uiStore.widgetRunning = clashRunning

        if (!clashRunning) {
            uiState.connectedMillis = 0L
        }

        uiState.showTraffic = uiStore.showTrafficCard
        uiState.showProxy = uiStore.showProxyCard
        uiState.showProfile = uiStore.showProfileCard
        uiState.showProviders = uiStore.showProvidersCard
        uiState.showTrafficMonitor = uiStore.showTrafficMonitorCard
        uiState.cardOrder = parseCardOrder(uiStore.homeCardOrder)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        uiState.mode = state.mode
        uiState.hasProviders = providers.isNotEmpty()

        val profile = withProfile { queryActive() }
        uiState.profileName = profile?.name
        uiState.subUpload = profile?.upload ?: 0L
        uiState.subDownload = profile?.download ?: 0L
        uiState.subTotal = profile?.total ?: 0L
        uiState.subExpire = profile?.expire ?: 0L

        fetchCurrentNode()
    }

    /** Stored order first; any known key missing from it keeps the default tail order. */
    private fun parseCardOrder(stored: String): List<String> {
        val parsed = stored.split(",")
            .map { it.trim() }
            .filter { it in HOME_CARD_KEYS }
            .distinct()

        return parsed + HOME_CARD_KEYS.filter { it !in parsed }
    }

    /**
     * Find the main Selector group (the first one, like the proxy screen
     * lists them) and mirror its current node + delay into the UI state
     * for the home screen's node tile.
     */
    private suspend fun fetchCurrentNode() {
        val result = runCatching {
            withClash {
                var found: Triple<String, String, Int>? = null

                for (name in queryProxyGroupNames(false)) {
                    val group = queryProxyGroup(name, uiStore.proxySort)

                    if (group.type == "Selector") {
                        val delay = group.proxies
                            .firstOrNull { it.name == group.now }?.delay ?: -1

                        found = Triple(name, group.now, delay)
                        break
                    }
                }

                found
            }
        }.getOrNull()

        uiState.mainGroupName = result?.first
        uiState.currentNodeName = result?.second
        uiState.currentNodeDelay = result?.third ?: -1
        uiStore.widgetNodeName = result?.second ?: ""
    }

    /** One silent update check per activity start; failures stay invisible. */
    private fun checkUpdateOnStartup() {
        launch {
            val info = UpdateChecker.fetchLatest() ?: return@launch
            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: return@launch

            if (UpdateChecker.isNewer(info.version, current) &&
                uiStore.dismissedUpdate != info.version
            ) {
                uiState.updateInfo = info
            }
        }
    }

    /** Push the stored daily totals into the home-screen state. */
    private fun refreshDailyState() {
        val todayKey = dailyTraffic.todayKey()
        val today = dailyTraffic.today(todayKey)

        uiState.todayUp = today.up
        uiState.todayDown = today.down
        uiState.last7Days = dailyTraffic.previousDays(7, todayKey)
    }

    private fun resetTrafficSampling() {
        prevTrafficUp = 0L
        prevTrafficDown = 0L
        prevTrafficAt = 0L
    }

    private suspend fun fetchTraffic() {
        val total = withClash { queryTrafficTotal() }
        uiState.traffic = total

        // Per-second rates for the monitor chart. The Binder API exposes
        // totals only (queryTrafficNow is not part of IClashManager), so
        // the rate is the delta of the totals between ticks — the same
        // derivation the power circle uses for its live speed.
        val up = trafficUploadBytes(total)
        val down = trafficDownloadBytes(total)
        val now = SystemClock.elapsedRealtime()

        if (prevTrafficAt != 0L && now > prevTrafficAt) {
            val seconds = (now - prevTrafficAt) / 1000.0
            val sample = TrafficSample(
                upRate = ((up - prevTrafficUp).coerceAtLeast(0) / seconds).roundToLong(),
                downRate = ((down - prevTrafficDown).coerceAtLeast(0) / seconds).roundToLong(),
            )

            uiState.trafficSamples = (uiState.trafficSamples + sample).takeLast(60)

            // Daily totals: accumulate the raw byte deltas for today.
            // Only traffic while the app is open is counted (see the
            // note on the monitor card); the file is written at most
            // once a minute, plus once when the activity stops.
            val todayKey = dailyTraffic.todayKey()
            dailyTraffic.add(
                todayKey,
                (up - prevTrafficUp).coerceAtLeast(0),
                (down - prevTrafficDown).coerceAtLeast(0),
            )
            val today = dailyTraffic.today(todayKey)
            uiState.todayUp = today.up
            uiState.todayDown = today.down

            dailyTickCount++
            if (dailyTickCount % 60 == 0) {
                withContext(Dispatchers.IO) { dailyTraffic.save() }
                uiState.last7Days = dailyTraffic.previousDays(7, todayKey)
            }

            // Persist the latest rates for the home-screen widget, throttled
            // to one write every 5 seconds so the prefs file is not churned.
            widgetTickCount++
            if (widgetTickCount % 5 == 0) {
                uiStore.widgetUpRate = sample.upRate
                uiStore.widgetDownRate = sample.downRate
                uiStore.widgetUpdatedAt = System.currentTimeMillis()
                TuxunWidgetProvider.requestUpdate(this@MainActivity)
            }
        }

        prevTrafficUp = up
        prevTrafficDown = down
        prevTrafficAt = now

        val since = uiStore.runningSince
        uiState.runningSince = since
        uiState.connectedMillis =
            if (since > 0L) System.currentTimeMillis() - since else 0L
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

    override fun onStop() {
        // Flush the daily totals before the process may be killed.
        if (::dailyTraffic.isInitialized) {
            runCatching { dailyTraffic.save() }
        }

        super.onStop()
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
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            // 1.x is the official line: mark it on the About screen.
            val official = if (versionName?.startsWith("1.") == true) {
                getString(R.string.version_official)
            } else {
                ""
            }

            "TuxunDL $versionName$official\n" +
                Bridge.nativeCoreVersion().replace("_", "-")
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

        uiState.disclaimerAccepted = uiStore.disclaimerAccepted

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (uiState.disclaimerAccepted) {
                        MainScreen(
                            state = uiState,
                            onRequest = { request -> uiRequests.trySend(request) },
                        )
                    } else {
                        DisclaimerScreen(
                            onAgree = {
                                uiStore.disclaimerAccepted = true
                                uiState.disclaimerAccepted = true
                            },
                            onDisagree = { finish() },
                        )
                    }
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
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_toggle))
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
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_start))
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
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_stop))
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }

    companion object {
        /** All orderable home cards, in their default display order. */
        private val HOME_CARD_KEYS =
            listOf("monitor", "traffic", "proxy", "profile", "providers")
    }
}
