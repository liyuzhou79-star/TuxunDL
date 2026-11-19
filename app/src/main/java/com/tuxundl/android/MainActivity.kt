package com.tuxundl.android

import android.app.PendingIntent
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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.tuxundl.android.common.compat.getColorCompat
import com.tuxundl.android.common.compat.pendingIntentFlags
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.core.Clash
import com.tuxundl.android.design.MainDesign
import com.tuxundl.android.design.model.DarkMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.ui.DisclaimerScreen
import com.tuxundl.android.ui.HOME_BRICK_ALL_IDS
import com.tuxundl.android.ui.HOME_BRICK_DEFAULT_ORDER
import com.tuxundl.android.ui.MainScreen
import com.tuxundl.android.ui.MainUiState
import com.tuxundl.android.ui.OnboardingScreen
import com.tuxundl.android.ui.TrafficSample
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.DailyTrafficStore
import com.tuxundl.android.util.ExitInfoLookup
import com.tuxundl.android.util.ProxyScheduler
import com.tuxundl.android.util.DelaySample
import com.tuxundl.android.util.SavedConnection
import com.tuxundl.android.util.SavedConnectionStore
import com.tuxundl.android.util.TrafficSampler
import com.tuxundl.android.util.UpdateAllTracker
import com.tuxundl.android.util.UpdateCheckCenter
import com.tuxundl.android.util.UpdateCheckResult
import com.tuxundl.android.util.UpdateCheckSnapshot
import com.tuxundl.android.util.summarizeQuality
import com.tuxundl.android.util.startClashService
import com.tuxundl.android.util.stopClashService
import com.tuxundl.android.util.withClash
import com.tuxundl.android.util.withProfile
import com.tuxundl.android.core.bridge.*
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.tuxundl.android.design.R as DesignR

class MainActivity : BaseActivity<MainDesign>() {
    private val uiState = MainUiState()
    private val uiRequests = Channel<MainDesign.Request>(Channel.UNLIMITED)

    // Onboarding step 3 import launchers (1.4.0): the same three ways
    // in as the profiles page — camera scan (quickie), album QR
    // decode (ML Kit), and a plain file pick. Outcomes are reported
    // back to the step through uiState's import signal.
    private val onboardingScanLauncher =
        registerForActivityResult(ScanQRCode()) { result ->
            handleOnboardingScan(result)
        }

    private val onboardingAlbumLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                reportOnboardingImport(IMPORT_CANCELLED)
            } else {
                lifecycleScope.launch {
                    val text = com.tuxundl.android.util.decodeQrFromImage(
                        this@MainActivity,
                        uri,
                    )
                    val ok = !text.isNullOrBlank() &&
                        addSubscriptionFromOnboarding(
                            text.trim(),
                            getString(DesignR.string.new_profile),
                        )
                    reportOnboardingImport(
                        if (ok) IMPORT_OK else IMPORT_FAILED,
                    )
                }
            }
        }

    private val onboardingFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                reportOnboardingImport(IMPORT_CANCELLED)
            } else {
                lifecycleScope.launch {
                    val ok = addFileProfileFromOnboarding(
                        uri.toString(),
                        getString(DesignR.string.new_profile),
                    )
                    reportOnboardingImport(
                        if (ok) IMPORT_OK else IMPORT_FAILED,
                    )
                }
            }
        }

    // Traffic sampling itself (counters, rates, the tiered 2-hour
    // history, daily totals, widget rates) lives in TrafficSampler
    // since 1.5.3: it ticks at application scope for as long as the
    // tunnel runs, independent of this activity and of visibility.
    // This activity only mirrors its snapshot into the UI state.

    // Per-day forwarded traffic (filesDir/traffic-daily.json). The
    // store is owned by TrafficSampler, which feeds it; this activity
    // reads it for the week / lifetime views (refreshDailyState).
    private lateinit var dailyTraffic: DailyTrafficStore
    private var dailyTickCount = 0

    // User-saved connection snapshots (filesDir/saved-connections.json).
    private lateinit var savedConnections: SavedConnectionStore

    // Periodic quality probe of the main group (every 15 ticks while
    // running): healthCheck + read back the current node's delay.
    private var qualityTickCount = 0
    private var qualityProbing = false

    // Exit-IP lookup for the home exit info card: one lookup per
    // connection session, re-run when the current node changes. The
    // key is "runningSince|nodeName"; a result that lands after the
    // key moved on is dropped.
    private var exitInfoKey: String? = null
    private var exitInfoJob: Job? = null

    // Traffic-alert cadence: re-read the subscription every 30 ticks
    // and re-evaluate whether the low-traffic notification should fire.
    private var alertTickCount = 0

    // "Update all subscriptions" batch tracking for the power
    // circle's long-press quick menu (1.4.1): the same tracker the
    // profiles screen uses, so a batch fired from home produces the
    // same one-line summary when the last profile reports back.
    private val updateAllTracker = UpdateAllTracker { succeeded, failedNames ->
        launch {
            val message = if (failedNames.isEmpty()) {
                getString(DesignR.string.toast_update_all_done, succeeded)
            } else {
                getString(
                    DesignR.string.toast_update_all_summary,
                    succeeded,
                    failedNames.size,
                    failedNames.joinToString(", "),
                )
            }
            uiState.snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long,
            )
        }
    }

    override suspend fun main() {
        dailyTraffic = TrafficSampler.dailyStore
        savedConnections = SavedConnectionStore(filesDir.resolve("saved-connections.json"))

        // First-launch day for the easter egg's "days used" counter.
        if (uiStore.firstLaunchDay == 0L) {
            uiStore.firstLaunchDay = System.currentTimeMillis() / 86_400_000L
        }

        withContext(Dispatchers.IO) {
            uiState.savedConnections = savedConnections.load()
        }
        refreshDailyState()

        fetch()
        checkUpdateOnStartup()
        maybeAutoBackup()

        // Keep the 1.5.4 proxy schedule aligned whenever the app
        // opens: re-arm the next alarm, and reconcile the night
        // mode window in the background (it binds the remote
        // service on demand, so it must not block the event loop).
        ProxyScheduler.reschedule(this)
        launch { ProxyScheduler.reconcileNight(this@MainActivity) }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart -> {
                            uiStore.runningSince = System.currentTimeMillis()
                            com.tuxundl.android.util.Haptics.buzz(
                                this@MainActivity,
                                uiStore,
                                com.tuxundl.android.util.Haptics.Kind.Success,
                            )
                            resetSessionStats()
                            fetch()
                            TuxunWidgetProvider.requestUpdate(this@MainActivity)
                        }
                        Event.ClashStop -> {
                            uiStore.runningSince = 0L
                            com.tuxundl.android.util.Haptics.buzz(
                                this@MainActivity,
                                uiStore,
                                com.tuxundl.android.util.Haptics.Kind.Disconnect,
                            )
                            resetSessionStats()
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
                        MainDesign.Request.UpdateAllProfiles -> {
                            // Same target set and firing loop as the
                            // profiles screen's "update all"; the
                            // tracker turns the per-profile callbacks
                            // into one summary (see onFinished above).
                            // A batch already in flight swallows
                            // repeat taps.
                            if (!updateAllTracker.isActive) {
                                var targetCount = -1

                                try {
                                    withProfile {
                                        val targets = queryAll().filter { p ->
                                            p.imported && p.type != Profile.Type.File
                                        }

                                        targetCount = targets.size
                                        updateAllTracker.begin(
                                            targets.map { it.uuid },
                                        )
                                        targets.forEach { p -> update(p.uuid) }
                                    }
                                } catch (e: Exception) {
                                    // Firing the updates failed midway:
                                    // abandon the batch so no summary
                                    // is ever shown for it.
                                    updateAllTracker.abandon()
                                    targetCount = -1
                                }

                                if (targetCount == 0) {
                                    launch {
                                        uiState.snackbarHostState.showSnackbar(
                                            message = getString(
                                                DesignR.string.toast_update_all_none,
                                            ),
                                            duration = SnackbarDuration.Short,
                                        )
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
                        MainDesign.Request.OpenTrafficAdvanced -> {
                            refreshDailyState()
                            uiState.showTrafficAdvanced = true
                        }
                        MainDesign.Request.SaveCurrentConnection -> {
                            if (clashRunning) {
                                val summary = summarizeQuality(uiState.delaySamples)

                                savedConnections.add(
                                    SavedConnection(
                                        id = SavedConnection.newId(),
                                        savedAtMillis = System.currentTimeMillis(),
                                        profileName = uiState.profileName ?: "",
                                        nodeName = uiState.currentNodeName ?: "",
                                        durationMillis = uiState.connectedMillis,
                                        upBytes = uiState.sessionUpBytes,
                                        downBytes = uiState.sessionDownBytes,
                                        avgDelayMs = summary.avgDelayMs,
                                        jitterMs = summary.jitterMs,
                                        failRate = summary.failRate,
                                        rating = summary.rating.name,
                                    ),
                                )
                                uiState.savedConnections = savedConnections.snapshot()
                            }
                        }
                        is MainDesign.Request.DeleteSavedConnection -> {
                            savedConnections.remove(it.id)
                            uiState.savedConnections = savedConnections.snapshot()
                        }
                        is MainDesign.Request.SetBrickOrder -> {
                            uiStore.brickOrder = it.order
                            uiState.brickOrder = parseBrickOrder(it.order)
                        }
                        is MainDesign.Request.SetCardOrder -> {
                            uiStore.homeCardOrder = it.order
                            uiState.cardOrder = parseCardOrder(it.order)
                        }
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        syncTraffic()
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
        uiState.brickOrder = parseBrickOrder(uiStore.brickOrder)

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

        evaluateTrafficAlert()

        fetchCurrentNode()

        refreshExitInfo()
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
     * Parse a stored brick order. The known-id set and the default
     * order both come from the single definition in HomeBricks.kt.
     * A stored order is honoured exactly as written (older versions
     * stored every brick); only an empty/unusable value falls back
     * to the current default set.
     */
    private fun parseBrickOrder(stored: String): List<String> {
        val parsed = stored.split(",")
            .map { it.trim() }
            .filter { it in HOME_BRICK_ALL_IDS }
            .distinct()

        return parsed.ifEmpty { HOME_BRICK_DEFAULT_ORDER }
    }

    /**
     * Per-session UI counters behind the advanced traffic panel;
     * reset on start/stop. (The sampler clears its own recorded
     * history on the same transitions; these are the UI mirrors.)
     */
    private fun resetSessionStats() {
        qualityTickCount = 0
        uiState.sessionUpBytes = 0L
        uiState.sessionDownBytes = 0L
        uiState.stallCount = 0
        uiState.delaySamples = emptyList()
        uiState.delayHistory = emptyList()
        uiState.historyPoints = emptyList()
        uiState.disconnectTimes = emptyList()
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

    /**
     * Update state on the home screen (1.6.0): everything renders
     * from [UpdateCheckCenter]'s single shared snapshot — the same
     * state Settings reads — so the home card can never disagree
     * with a Settings check again. The last known verdict is applied
     * instantly (it survives process death), a throttled automatic
     * check refreshes it in the background, and the collector keeps
     * mirroring later results (e.g. a manual check run from
     * Settings) for the rest of this activity's life.
     */
    private fun checkUpdateOnStartup() {
        // TV Beta: no update checks. The checker only knows the
        // phone line's tags, so it could only ever misreport here;
        // TV updates are sideloaded installs of newer Beta APKs.
        if (BuildConfig.IS_TV) return

        launch {
            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: return@launch

            applyUpdateSnapshot(
                UpdateCheckCenter.peek(this@MainActivity, current),
            )
            UpdateCheckCenter.check(
                this@MainActivity,
                current,
                manual = false,
            )
        }

        launch {
            UpdateCheckCenter.state.collect { snapshot ->
                applyUpdateSnapshot(snapshot)
            }
        }
    }

    /**
     * Mirror one shared snapshot onto the home card / full-screen
     * page. A newer release lights the card and — once per version
     * per launch, unless the user already dismissed that version —
     * the page. A definitive up-to-date retires a card an earlier
     * check lit; a failure changes nothing here (the last successful
     * verdict stands, and Settings shows the failure itself).
     */
    private fun applyUpdateSnapshot(snapshot: UpdateCheckSnapshot) {
        when (val result = snapshot.result) {
            is UpdateCheckResult.UpdateAvailable -> {
                val info = result.info

                // A dismissed version wins over everything: no card, no page.
                if (uiStore.dismissedUpdate != info.version) {
                    uiState.updateInfo = info

                    if (fullScreenUpdateShownFor != info.version) {
                        fullScreenUpdateShownFor = info.version
                        uiState.updatePageInfo = info
                    }
                } else {
                    uiState.updateInfo = null
                }
            }

            is UpdateCheckResult.UpToDate -> {
                uiState.updateInfo = null
            }

            is UpdateCheckResult.Failed, null -> Unit
        }
    }

    /**
     * Weekly automatic backup (1.3.5): if the user enabled it on the
     * backup screen and the last automatic backup is over 7 days old
     * (or never ran), write one into filesDir/backups at startup.
     * Best-effort — any failure is swallowed so startup never breaks,
     * and the profile service is only touched when a backup is due.
     */
    private suspend fun maybeAutoBackup() {
        runCatching {
            if (com.tuxundl.android.util.isAutoBackupDue(uiStore)) {
                val profiles = withProfile { queryAll() }
                val versionName = packageManager
                    .getPackageInfo(packageName, 0).versionName ?: ""

                withContext(Dispatchers.IO) {
                    com.tuxundl.android.util.runAutoBackupIfDue(
                        this@MainActivity,
                        uiStore,
                        profiles,
                        versionName,
                    )
                }
            }
        }
    }

    /** Push the stored daily totals into the home-screen state. */
    private fun refreshDailyState() {
        // The store is fed by TrafficSampler on its own thread;
        // reads take the same lock as its writes.
        synchronized(dailyTraffic) {
            val todayKey = dailyTraffic.todayKey()
            val today = dailyTraffic.today(todayKey)

            uiState.todayUp = today.up
            uiState.todayDown = today.down
            uiState.currentWeekDays = dailyTraffic.weekDays(todayKey, 0)
            uiState.lastWeekDays = dailyTraffic.weekDays(todayKey, 1)
            uiState.recentDays = dailyTraffic.recentDays(todayKey, 7)

            // Easter-egg lifetime stats and the advanced panel's 24h window.
            uiState.lifetimeUp = dailyTraffic.lifetimeUp()
            uiState.lifetimeDown = dailyTraffic.lifetimeDown()
            uiState.lifetimeConnectedSeconds = dailyTraffic.lifetimeConnectedSeconds()
            uiState.todayConnectedSeconds = dailyTraffic.dayConnectedSeconds(todayKey)

            val firstDay = uiStore.firstLaunchDay
            val todayDay = System.currentTimeMillis() / 86_400_000L
            uiState.daysUsed = if (firstDay > 0L) {
                (todayDay - firstDay + 1L).toInt().coerceAtLeast(1)
            } else {
                1
            }

            val currentHour = System.currentTimeMillis() / 3_600_000L
            uiState.hourlyBuckets = dailyTraffic.hourlyBuckets(currentHour - 23, currentHour)
        }
    }

    /**
     * Low-traffic subscription alert. Evaluated whenever fresh
     * subscription figures land (fetch + every 30 s while running) —
     * like the traffic stats, it only ever runs while the app is open.
     *
     * Fires once when the remaining traffic drops to the user's
     * threshold; re-arms when the remainder recovers to 1.2x the
     * threshold, or when the total grows (renewal / profile switch).
     * Profiles without a total cap never trigger it.
     */
    private fun evaluateTrafficAlert() {
        if (!uiStore.trafficAlertEnabled) return

        val total = uiState.subTotal
        if (total <= 0L) return

        if (total > uiStore.trafficAlertLastTotal) {
            uiStore.trafficAlertNotified = false
        }
        uiStore.trafficAlertLastTotal = total

        val remaining = total - (uiState.subUpload + uiState.subDownload)
        val thresholdBytes =
            uiStore.trafficAlertThresholdGb.toLong() * GIB_BYTES

        if (remaining <= thresholdBytes) {
            if (!uiStore.trafficAlertNotified) {
                uiStore.trafficAlertNotified = true
                postTrafficAlert(remaining)
            }
        } else if (remaining > thresholdBytes * 12 / 10) {
            uiStore.trafficAlertNotified = false
        }
    }

    /** Post the low-traffic notification (channel created on demand). */
    private fun postTrafficAlert(remainingBytes: Long) {
        val manager = NotificationManagerCompat.from(this)

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                TRAFFIC_ALERT_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            ).setName(getString(R.string.traffic_alert_channel)).build(),
        )

        val openApp = PendingIntent.getActivity(
            this,
            TRAFFIC_ALERT_NOTIFICATION_ID,
            MainActivity::class.intent
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
        )

        val remainingGb = String.format(
            "%.1f",
            remainingBytes.coerceAtLeast(0L).toDouble() / GIB_BYTES,
        )

        val notification = NotificationCompat.Builder(this, TRAFFIC_ALERT_CHANNEL_ID)
            .setSmallIcon(com.tuxundl.android.service.R.drawable.ic_logo_tuxundl)
            .setColor(getColorCompat(com.tuxundl.android.service.R.color.color_tuxundl))
            .setContentTitle(getString(R.string.traffic_alert_title))
            .setContentText(
                getString(
                    R.string.traffic_alert_text,
                    uiState.profileName ?: "",
                    remainingGb,
                ),
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(TRAFFIC_ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted (Android 13+): the
            // alert simply stays silent; the in-app card still warns.
        }
    }

    /**
     * Keep the exit info card's lookup in step with the live
     * session: cleared while stopped, one lookup per (session
     * start, current node) pair. The request runs from this
     * process, whose traffic the tunnel always carries (see
     * [ExitInfoLookup]), so the answer is the node's exit.
     * Called from fetch() and from the per-second sync; the key
     * check makes repeat calls free.
     */
    private fun refreshExitInfo() {
        if (!clashRunning) {
            exitInfoKey = null
            exitInfoJob?.cancel()
            exitInfoJob = null
            uiState.exitIp = null
            uiState.exitCountry = ""
            uiState.exitCity = ""
            uiState.exitCountryCode = ""
            uiState.exitLookupFailed = false
            return
        }

        val key = "${uiStore.runningSince}|${uiState.currentNodeName.orEmpty()}"

        if (key == exitInfoKey) return

        exitInfoKey = key
        uiState.exitIp = null
        uiState.exitCountry = ""
        uiState.exitCity = ""
        uiState.exitCountryCode = ""
        uiState.exitLookupFailed = false

        exitInfoJob?.cancel()
        exitInfoJob = launch {
            val info = withContext(Dispatchers.IO) { ExitInfoLookup.fetch() }

            if (exitInfoKey != key) return@launch

            if (info != null) {
                uiState.exitIp = info.ip
                uiState.exitCountry = info.country
                uiState.exitCity = info.city
                uiState.exitCountryCode = info.countryCode
            } else {
                uiState.exitLookupFailed = true
            }
        }
    }

    /**
     * One quality probe: run the existing group health check, then read
     * the current node's fresh delay back through [fetchCurrentNode].
     * The core reports a failed/untested node as 65535, which the panel
     * counts as a failed probe (-1). Results stay in memory (last 40).
     */
    private fun launchQualityProbe() {
        val group = uiState.mainGroupName ?: return
        if (qualityProbing) return

        qualityProbing = true

        launch {
            try {
                withClash { healthCheck(group) }
                fetchCurrentNode()

                val delay = uiState.currentNodeDelay
                val sample = DelaySample(
                    atMillis = System.currentTimeMillis(),
                    delayMs = if (delay in 1..65534) delay else -1,
                )
                uiState.delaySamples = (uiState.delaySamples + sample).takeLast(40)
                uiState.delayHistory = (uiState.delayHistory + sample).takeLast(480)
            } catch (e: Exception) {
                // Core stopped mid-probe: count it as a failed probe.
                val sample = DelaySample(System.currentTimeMillis(), -1)
                uiState.delaySamples = (uiState.delaySamples + sample).takeLast(40)
                uiState.delayHistory = (uiState.delayHistory + sample).takeLast(480)
            } finally {
                qualityProbing = false
            }
        }
    }

    /**
     * Per-second UI sync while the tunnel runs (1.5.3): mirror the
     * TrafficSampler's latest snapshot into the UI state — the same
     * recorded points feed the rates, both charts and the totals, so
     * the numbers can never disagree — and run the cadences that are
     * UI concerns (quality probes, the traffic alert, the week view).
     * The sampling itself no longer happens here: the sampler ticks
     * at application scope, so the recording continues while this
     * activity is in the background or destroyed.
     */
    private suspend fun syncTraffic() {
        val snap = TrafficSampler.snapshot

        uiState.traffic = snap.packedTotal
        uiState.upRate = snap.upRate
        uiState.downRate = snap.downRate
        uiState.trafficSamples = snap.samples.map {
            TrafficSample(it.upRate, it.downRate)
        }
        uiState.historyPoints = snap.historyPoints
        uiState.disconnectTimes = snap.disconnectTimes
        uiState.todayUp = snap.todayUp
        uiState.todayDown = snap.todayDown
        uiState.todayConnectedSeconds = snap.todayConnectedSeconds
        uiState.sessionUpBytes = snap.sessionUpBytes
        uiState.sessionDownBytes = snap.sessionDownBytes
        uiState.stallCount = snap.stallCount

        val since = uiStore.runningSince
        uiState.runningSince = since
        uiState.connectedMillis =
            if (since > 0L) System.currentTimeMillis() - since else 0L

        // A node switch mid-session produces no dedicated event;
        // the per-second sync catches it through the lookup key.
        refreshExitInfo()

        // Quality probe of the current group every 15 seconds.
        qualityTickCount++
        if (qualityTickCount % 15 == 0) {
            launchQualityProbe()
        }

        // Traffic alert: refresh the subscription figures every
        // 30 seconds, then re-evaluate (cheap local Binder reads).
        alertTickCount++
        if (alertTickCount % 30 == 0) {
            val profile = withProfile { queryActive() }

            if (profile != null) {
                uiState.profileName = profile.name
                uiState.subUpload = profile.upload
                uiState.subDownload = profile.download
                uiState.subTotal = profile.total
                uiState.subExpire = profile.expire
            }

            evaluateTrafficAlert()
        }

        // Week / lifetime views re-read the sampler's store once a
        // minute (the sampler itself persists it on the same cadence).
        dailyTickCount++
        if (dailyTickCount % 60 == 0) {
            refreshDailyState()
        }
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

    override fun onResume() {
        super.onResume()

        // Every return to the home screen replays its entrance
        // animation (MainScreen watches this counter).
        uiState.entranceTick++
    }

    override fun onStop() {
        // Flush the daily totals before the process may be killed.
        TrafficSampler.flush()

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

    /**
     * Feed the quick menu's "update all" batch (1.4.1). The super
     * calls keep the base class's event flow untouched; updates that
     * are not batch members change nothing here.
     */
    override fun onProfileUpdateCompleted(uuid: UUID?) {
        super.onProfileUpdateCompleted(uuid)

        if (uuid != null) {
            updateAllTracker.onCompleted(uuid)
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        super.onProfileUpdateFailed(uuid, reason)

        if (uuid == null) return

        if (updateAllTracker.onFailed(uuid)) {
            launch {
                var name: String? = null
                withProfile {
                    name = queryByUUID(uuid)?.name
                }
                updateAllTracker.onFailureNameResolved(uuid, name)
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

    /**
     * Onboarding step 3: import a subscription URL exactly like the
     * profiles flow does (create, fetch, activate when nothing is
     * active yet). False when anything along the way fails.
     */
    private suspend fun addSubscriptionFromOnboarding(
        url: String,
        name: String,
    ): Boolean = runCatching {
        withProfile {
            val uuid = create(Profile.Type.Url, name, url)
            try {
                // commit() is the step the properties screen runs on
                // save: it fetches the subscription and turns the
                // pending profile into an imported one. The old code
                // called update() here, which is a no-op for a profile
                // that was never imported, so onboarding profiles
                // stayed pending forever and the main screen demanded
                // a re-edit + save before they could be used (1.4.0).
                commit(uuid)
            } catch (e: Exception) {
                // Fetch/validation failed: drop the pending profile so
                // a retry does not pile up half-created entries.
                runCatching { release(uuid) }
                throw e
            }
            if (queryActive() == null) {
                queryByUUID(uuid)?.let { setActive(it) }
            }
        }
        true
    }.getOrDefault(false)

    /**
     * Onboarding step 3, file import: create a File profile from the
     * picked content URI and commit it right away, mirroring what
     * the properties screen does when a file is chosen there.
     */
    private suspend fun addFileProfileFromOnboarding(
        uri: String,
        name: String,
    ): Boolean = runCatching {
        withProfile {
            val uuid = create(Profile.Type.File, name, uri)
            try {
                commit(uuid)
            } catch (e: Exception) {
                runCatching { release(uuid) }
                throw e
            }
            if (queryActive() == null) {
                queryByUUID(uuid)?.let { setActive(it) }
            }
        }
        true
    }.getOrDefault(false)

    /** Camera-scan outcome for the onboarding import buttons. */
    private fun handleOnboardingScan(result: QRResult) {
        when (result) {
            is QRSuccess -> {
                val url = result.content.rawValue
                    ?: result.content.rawBytes?.let { String(it) }.orEmpty()

                lifecycleScope.launch {
                    val ok = url.isNotBlank() &&
                        addSubscriptionFromOnboarding(
                            url.trim(),
                            getString(DesignR.string.new_profile),
                        )
                    reportOnboardingImport(if (ok) IMPORT_OK else IMPORT_FAILED)
                }
            }
            QRUserCanceled -> reportOnboardingImport(IMPORT_CANCELLED)
            QRMissingPermission -> reportOnboardingImport(IMPORT_FAILED)
            is QRError -> reportOnboardingImport(IMPORT_FAILED)
        }
    }

    /** Publish an import outcome to the onboarding step, with feedback. */
    private fun reportOnboardingImport(outcome: Int) {
        uiState.onboardingImportOutcome = outcome
        uiState.onboardingImportNonce++

        when (outcome) {
            IMPORT_OK -> com.tuxundl.android.util.Haptics.buzz(
                this,
                uiStore,
                com.tuxundl.android.util.Haptics.Kind.Success,
            )
            IMPORT_FAILED -> com.tuxundl.android.util.Haptics.buzz(
                this,
                uiStore,
                com.tuxundl.android.util.Haptics.Kind.Disconnect,
            )
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

        // Upgraders who accepted the disclaimer on an older version
        // count as onboarding-complete: only fresh installs see the
        // 1.3.0 onboarding flow.
        if (uiStore.disclaimerAccepted && !uiStore.onboardingCompleted) {
            uiStore.onboardingCompleted = true
        }
        uiState.disclaimerAccepted = uiStore.disclaimerAccepted
        uiState.onboardingCompleted = uiStore.onboardingCompleted
        val startingLanguage = uiStore.appLanguage

        // The onboarding theme step restyles the app live, so the
        // palette is held as state here (elsewhere it is read once).
        var paletteState by mutableStateOf(uiStore.themePalette)

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = paletteState, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        uiState.disclaimerAccepted -> MainScreen(
                            state = uiState,
                            onRequest = { request -> uiRequests.trySend(request) },
                        )
                        !uiState.onboardingCompleted -> OnboardingScreen(
                            store = uiStore,
                            onPaletteSelected = { palette ->
                                uiStore.themePalette = palette
                                paletteState = palette
                            },
                            onAddSubscription = { url, name ->
                                addSubscriptionFromOnboarding(url, name)
                            },
                            onImportScan = { onboardingScanLauncher.launch(null) },
                            onImportAlbum = { onboardingAlbumLauncher.launch("image/*") },
                            onImportFile = { onboardingFileLauncher.launch("*/*") },
                            importOutcome = uiState.onboardingImportOutcome,
                            importNonce = uiState.onboardingImportNonce,
                            onFinish = {
                                uiStore.onboardingCompleted = true
                                uiStore.disclaimerAccepted = true
                                uiState.onboardingCompleted = true
                                if (uiStore.appLanguage != startingLanguage) {
                                    // Land on the main screen in the
                                    // language picked in step 1.
                                    recreate()
                                } else {
                                    uiState.disclaimerAccepted = true
                                }
                            },
                        )
                        else -> DisclaimerScreen(
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
        /** Notification channel + id for the low-traffic alert. */
        private const val TRAFFIC_ALERT_CHANNEL_ID = "subscription_alerts"
        private const val TRAFFIC_ALERT_NOTIFICATION_ID = 0x7A01
        private const val GIB_BYTES = 1024L * 1024L * 1024L

        /** Onboarding import outcomes (see MainUiState.onboardingImportOutcome). */
        private const val IMPORT_OK = 1
        private const val IMPORT_FAILED = 2
        private const val IMPORT_CANCELLED = 3

        /** All orderable home cards, in their default display order. */
        private val HOME_CARD_KEYS =
            listOf("monitor", "traffic", "proxy", "profile", "providers")

        /**
         * Version the full-screen update page was already shown for in
         * this process (i.e. this app launch). Static on purpose: it
         * survives activity recreation but resets when the app restarts,
         * which is exactly "once per version per launch".
         */
        private var fullScreenUpdateShownFor: String? = null
    }
}
