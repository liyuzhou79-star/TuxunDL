package com.tuxundl.android

import android.app.PendingIntent
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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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
import com.tuxundl.android.ui.trafficDownloadBytes
import com.tuxundl.android.ui.trafficUploadBytes
import com.tuxundl.android.util.DailyTrafficStore
import com.tuxundl.android.util.DelaySample
import com.tuxundl.android.util.SavedConnection
import com.tuxundl.android.util.SavedConnectionStore
import com.tuxundl.android.util.UpdateChecker
import com.tuxundl.android.util.UpdateCheckResult
import com.tuxundl.android.util.summarizeQuality
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

    // Tiered 2-hour history behind the fullscreen traffic chart
    // (1.4.0): 1-second points for 10 minutes, 10-second buckets
    // beyond that, plus the counter-reset instants that mark a drop.
    private val trafficHistory = com.tuxundl.android.util.TrafficHistoryRecorder()

    // Throttle widget persistence: write rates at most every 5 ticks.
    private var widgetTickCount = 0

    // Per-day forwarded traffic (filesDir/traffic-daily.json). Only
    // traffic seen while this activity is alive is counted; the file is
    // written at most once a minute plus once when the activity stops.
    private lateinit var dailyTraffic: DailyTrafficStore
    private var dailyTickCount = 0

    // User-saved connection snapshots (filesDir/saved-connections.json).
    private lateinit var savedConnections: SavedConnectionStore

    // Consecutive seconds with zero up/down rate while running; a streak
    // of 5+ counts as one stall in the advanced traffic panel.
    private var zeroRateStreak = 0

    // Periodic quality probe of the main group (every 15 ticks while
    // running): healthCheck + read back the current node's delay.
    private var qualityTickCount = 0
    private var qualityProbing = false

    // Traffic-alert cadence: re-read the subscription every 30 ticks
    // and re-evaluate whether the low-traffic notification should fire.
    private var alertTickCount = 0

    override suspend fun main() {
        dailyTraffic = DailyTrafficStore(filesDir.resolve("traffic-daily.json"))
        savedConnections = SavedConnectionStore(filesDir.resolve("saved-connections.json"))

        // First-launch day for the easter egg's "days used" counter.
        if (uiStore.firstLaunchDay == 0L) {
            uiStore.firstLaunchDay = System.currentTimeMillis() / 86_400_000L
        }

        withContext(Dispatchers.IO) {
            dailyTraffic.load()
            uiState.savedConnections = savedConnections.load()
        }
        refreshDailyState()

        fetch()
        checkUpdateOnStartup()
        maybeAutoBackup()

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
                            resetTrafficSampling()
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
                            resetTrafficSampling()
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

    /** Per-session counters behind the advanced traffic panel; reset on start/stop. */
    private fun resetSessionStats() {
        zeroRateStreak = 0
        qualityTickCount = 0
        uiState.sessionUpBytes = 0L
        uiState.sessionDownBytes = 0L
        uiState.stallCount = 0
        uiState.delaySamples = emptyList()
        uiState.delayHistory = emptyList()
        trafficHistory.clear()
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
     * One update check per activity start, using the shared three-state
     * result. Up-to-date and failed stay invisible here; a newer release
     * lights the home update card and — once per version per launch,
     * unless the user already dismissed that version — the full-screen
     * update page.
     */
    private fun checkUpdateOnStartup() {
        launch {
            val current = packageManager
                .getPackageInfo(packageName, 0).versionName ?: return@launch

            val result = UpdateChecker.check(this@MainActivity, current)

            if (result is UpdateCheckResult.UpdateAvailable) {
                val info = result.info

                // A dismissed version wins over everything: no card, no page.
                if (uiStore.dismissedUpdate != info.version) {
                    uiState.updateInfo = info

                    if (fullScreenUpdateShownFor != info.version) {
                        fullScreenUpdateShownFor = info.version
                        uiState.updatePageInfo = info
                    }
                }
            }
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

    private fun resetTrafficSampling() {
        prevTrafficUp = 0L
        prevTrafficDown = 0L
        prevTrafficAt = 0L
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
     * Bytes between two reads of a cumulative core counter. A drop
     * means the counter restarted (core restart / reconnect): count
     * everything the new counter has seen so far.
     */
    private fun counterDelta(previous: Long, current: Long): Long =
        if (current >= previous) current - previous else current

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
            // Rates use the actually elapsed time between ticks (the
            // loop is nominally 1s but Binder + scheduling jitter make
            // it drift), and the byte deltas are counter-reset aware:
            // when the core restarts or the tunnel reconnects, its
            // cumulative counters drop back toward zero — the bytes
            // counted since the restart are the current value itself,
            // not a negative span to clamp away (1.3.0).
            val seconds = (now - prevTrafficAt) / 1000.0
            val deltaUp = counterDelta(prevTrafficUp, up)
            val deltaDown = counterDelta(prevTrafficDown, down)
            val sample = TrafficSample(
                upRate = (deltaUp / seconds).roundToLong(),
                downRate = (deltaDown / seconds).roundToLong(),
            )

            // The monitor chart keeps the last 2 minutes (120 samples).
            uiState.trafficSamples = (uiState.trafficSamples + sample).takeLast(120)

            // Long-window history (1.4.0): the same deltas feed the
            // tiered recorder behind the fullscreen chart. A tick
            // that took far longer than a second means the app was
            // paused (background / doze): the span stays a gap — no
            // point is recorded — instead of one smeared average, and
            // a counter reset hidden inside a gap is not stamped as
            // a disconnect at resume time either.
            val tickDurationMs = now - prevTrafficAt
            if (tickDurationMs <= HISTORY_GAP_LIMIT_MS) {
                val wallNow = System.currentTimeMillis()

                if (up < prevTrafficUp || down < prevTrafficDown) {
                    // Cumulative counters dropped: the core restarted
                    // or the tunnel reconnected — a real drop moment.
                    trafficHistory.recordDisconnect(wallNow)
                }
                trafficHistory.record(
                    startMillis = wallNow - tickDurationMs,
                    durationMillis = tickDurationMs,
                    upBytes = deltaUp,
                    downBytes = deltaDown,
                )
                uiState.historyPoints = trafficHistory.points()
                uiState.disconnectTimes = trafficHistory.disconnects()
            }

            // Daily totals: accumulate the raw byte deltas for today.
            // Only traffic while the app is open is counted (see the
            // note on the monitor card); the file is written at most
            // once a minute, plus once when the activity stops.
            val todayKey = dailyTraffic.todayKey()
            dailyTraffic.add(todayKey, deltaUp, deltaDown)
            dailyTraffic.addHourly(
                System.currentTimeMillis() / 3_600_000L,
                deltaUp,
                deltaDown,
            )
            dailyTraffic.addConnected(todayKey, 1L)
            val today = dailyTraffic.today(todayKey)
            uiState.todayUp = today.up
            uiState.todayDown = today.down
            uiState.todayConnectedSeconds = dailyTraffic.dayConnectedSeconds(todayKey)

            // This session's totals, for the advanced panel and for
            // snapshotting a saved connection.
            uiState.sessionUpBytes += deltaUp
            uiState.sessionDownBytes += deltaDown

            // Stall detection: 5+ consecutive seconds of zero rate
            // (after the session has carried something) count once.
            if (sample.upRate == 0L && sample.downRate == 0L) {
                zeroRateStreak++
                if (zeroRateStreak == 5 &&
                    uiState.sessionUpBytes + uiState.sessionDownBytes > 0L
                ) {
                    uiState.stallCount++
                }
            } else {
                zeroRateStreak = 0
            }

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

            dailyTickCount++
            if (dailyTickCount % 60 == 0) {
                withContext(Dispatchers.IO) { dailyTraffic.save() }
                refreshDailyState()
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

    override fun onResume() {
        super.onResume()

        // Every return to the home screen replays its entrance
        // animation (MainScreen watches this counter).
        uiState.entranceTick++
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

        /**
         * Ticks longer than this are sampling gaps (app paused), not
         * data: the 2-hour history records nothing for the span.
         */
        private const val HISTORY_GAP_LIMIT_MS = 3_000L

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
