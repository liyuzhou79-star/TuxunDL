package com.tuxundl.android.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.tuxundl.android.TuxunWidgetProvider
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.remote.Broadcasts
import com.tuxundl.android.remote.Remote
import com.tuxundl.android.remote.StatusClient
import com.tuxundl.android.service.RemoteService
import com.tuxundl.android.service.remote.IClashManager
import com.tuxundl.android.service.remote.IRemoteService
import com.tuxundl.android.service.remote.unwrap
import com.tuxundl.android.ui.trafficDownloadBytes
import com.tuxundl.android.ui.trafficUploadBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToLong

/** One second of traffic: upload/download rates in bytes per second. */
data class TrafficRatePoint(
    val upRate: Long,
    val downRate: Long,
)

/**
 * Immutable view of everything the sampler has recorded, replaced
 * wholesale once per tick. MainActivity mirrors it into the home UI
 * state; nothing else reads the core's counters.
 */
data class TrafficSnapshot(
    val running: Boolean = false,
    /** Packed cumulative counters, as queryTrafficTotal() returns them. */
    val packedTotal: Long = 0L,
    /** Latest per-second rates (bytes/s) — the power circle's numbers. */
    val upRate: Long = 0L,
    val downRate: Long = 0L,
    /** Per-second rates for the home monitor chart, last 120. */
    val samples: List<TrafficRatePoint> = emptyList(),
    /** Tiered 2-hour history for the fullscreen chart. */
    val historyPoints: List<TrafficPoint> = emptyList(),
    /** Instants where the core's counters reset (a drop / reconnect). */
    val disconnectTimes: List<Long> = emptyList(),
    val todayUp: Long = 0L,
    val todayDown: Long = 0L,
    val todayConnectedSeconds: Long = 0L,
    val sessionUpBytes: Long = 0L,
    val sessionDownBytes: Long = 0L,
    val stallCount: Int = 0,
)

/**
 * Process-level traffic sampler (1.5.3).
 *
 * Until 1.5.2 the sampling lived in MainActivity: its 1-second ticker
 * fed the charts, the daily totals and the live rates. That tied the
 * recording to the home activity twice over — the activity's
 * MainScope is cancelled on destroy, and Remote unbinds the clash
 * service (and unregisters the state broadcasts) whenever the app
 * becomes invisible, which silently stopped the ticker branch. On
 * top of that, any tick delayed by more than 3 s had its whole span
 * discarded as a "gap", so time spent in other apps showed up as
 * blank chart even when a tick eventually arrived.
 *
 * This sampler lives at application scope instead: started once from
 * MainApplication, it watches the tunnel state through StatusClient
 * (a provider call that works regardless of visibility; the clash
 * broadcasts, when registered, only provide a faster hint), holds
 * its OWN binding to RemoteService while the tunnel runs, and ticks
 * once per second for as long as the connection lives — activity
 * or not.
 * It is the single place that reads the core's cumulative counters;
 * the rates on the home screen and both charts are all derived from
 * the points recorded here.
 *
 * A tick that arrives late (the process was frozen while the tunnel
 * kept forwarding) no longer drops its span: the accumulated bytes
 * are spread evenly over the elapsed seconds as ordinary 1-second
 * points, so the chart shows a flat plateau at the true average rate
 * — never a hole, never a single fake spike. A counter reset hidden
 * inside such a span is not stamped as a disconnect, because its
 * moment is unknowable (the 1.4.0 rule, kept).
 *
 * The one span that still cannot be recorded is the app process
 * itself being killed while the tunnel (a separate process) lives
 * on; the sampler then re-baselines on the next process start.
 */
object TrafficSampler {
    private const val TICK_MS = 1_000L

    /** Ticks at most this late still record as one real-duration point. */
    private const val GAP_LIMIT_MS = 3_000L

    @Volatile
    var snapshot: TrafficSnapshot = TrafficSnapshot()
        private set

    /** The daily-totals store; owned here, read by MainActivity for the week views. */
    lateinit var dailyStore: DailyTrafficStore
        private set

    private var started = false
    private lateinit var app: Application
    private lateinit var uiStore: UiStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Own binding to the remote service, held only while sampling.
    // (Remote.service's binding is visibility-driven and unbinds in
    // the background — exactly what used to stop the old ticker.)
    @Volatile
    private var clashManager: IClashManager? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            clashManager = try {
                service.unwrap(IRemoteService::class).clash()
            } catch (e: Exception) {
                Log.w("TrafficSampler: unwrap remote failed: $e")
                null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clashManager = null
        }
    }

    // Sampling state; only touched from the sampler loop.
    private val history = TrafficHistoryRecorder()
    private var samples: List<TrafficRatePoint> = emptyList()
    private var prevUp = 0L
    private var prevDown = 0L
    private var prevAt = 0L
    private var lastPackedTotal = 0L
    private var sessionUp = 0L
    private var sessionDown = 0L
    private var stallCount = 0
    private var zeroStreak = 0
    private var saveTickCount = 0
    private var widgetTickCount = 0
    private var sampling = false

    // Start/stop hints from the clash broadcasts. The broadcasts are
    // only registered while the app is visible, so these are a
    // prompt-path optimisation for the visible case (a stop+start
    // within one polling interval would otherwise merge sessions);
    // the StatusClient poll in step() is the always-available path
    // and stays authoritative. Written on the main thread, consumed
    // by the sampler loop.
    @Volatile
    private var startHint = false

    @Volatile
    private var stopHint = false

    @Volatile
    private var rebaselineHint = false

    private val broadcastObserver = object : Broadcasts.Observer {
        override fun onStarted() {
            startHint = true
        }

        override fun onStopped(cause: String?) {
            stopHint = true
        }

        override fun onServiceRecreated() {
            rebaselineHint = true
        }

        override fun onProfileChanged() = Unit
        override fun onProfileUpdateCompleted(uuid: java.util.UUID?) = Unit
        override fun onProfileUpdateFailed(uuid: java.util.UUID?, reason: String?) = Unit
        override fun onProfileLoaded() = Unit
    }

    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        uiStore = UiStore(application)
        dailyStore = DailyTrafficStore(File(application.filesDir, "traffic-daily.json"))
        runCatching { dailyStore.load() }
        Remote.broadcasts.addObserver(broadcastObserver)
        scope.launch { loop() }
    }

    /** Flush the daily totals (MainActivity still calls this on stop). */
    fun flush() {
        if (!::dailyStore.isInitialized) return
        synchronized(dailyStore) {
            runCatching { dailyStore.save() }
        }
    }

    private suspend fun loop() {
        while (true) {
            try {
                step()
            } catch (e: Exception) {
                Log.w("TrafficSampler step failed: $e", e)
            }
            delay(TICK_MS)
        }
    }

    /** True while the tunnel service runs; works in any visibility state. */
    private fun tunnelUp(): Boolean =
        runCatching { StatusClient(app).currentProfile() != null }
            .getOrDefault(false)

    private suspend fun step() {
        // Broadcast hints first (visible case): they are fresher
        // than the poll and keep start/stop aligned with the events
        // the UI reacts to.
        if (stopHint) {
            stopHint = false
            startHint = false
            if (sampling) {
                stopSampling()
                return
            }
        }
        if (startHint) {
            startHint = false
            if (!sampling) {
                startSampling()
                return
            }
        }
        if (rebaselineHint) {
            rebaselineHint = false
            // The service process was recreated: re-baseline instead
            // of deriving a delta across the restart.
            prevAt = 0L
        }

        if (!sampling) {
            if (tunnelUp()) startSampling()
            return
        }

        val manager = clashManager
        if (manager == null) {
            // Binding lost (the service process restarted?) or still
            // connecting: if the tunnel is gone, wind down; otherwise
            // (re)bind and try again next second.
            if (!tunnelUp()) {
                stopSampling()
            } else {
                bind()
            }
            return
        }

        val total = try {
            manager.queryTrafficTotal()
        } catch (e: Exception) {
            // Dead binder mid-restart: drop the manager; the state is
            // re-checked on the next tick before anything is recorded.
            clashManager = null
            return
        }

        onTotal(total)

        // The totals call can keep succeeding against a stale manager
        // for a while after a stop; verify the tunnel itself too.
        if (!tunnelUp()) {
            stopSampling()
        }
    }

    private fun startSampling() {
        sampling = true
        resetSession()
        bind()
        publish(upRate = 0L, downRate = 0L)
    }

    private fun stopSampling() {
        sampling = false
        unbind()
        flush()
        resetSession()
        publish(upRate = 0L, downRate = 0L)
    }

    private fun resetSession() {
        prevUp = 0L
        prevDown = 0L
        prevAt = 0L
        sessionUp = 0L
        sessionDown = 0L
        stallCount = 0
        zeroStreak = 0
        saveTickCount = 0
        widgetTickCount = 0
        samples = emptyList()
        history.clear()
    }

    private fun bind() {
        if (bound) return
        try {
            app.bindService(
                RemoteService::class.intent,
                connection,
                Context.BIND_AUTO_CREATE,
            )
            bound = true
        } catch (e: Exception) {
            Log.w("TrafficSampler bind failed: $e", e)
        }
    }

    private fun unbind() {
        if (!bound) return
        bound = false
        clashManager = null
        app.unbindServiceSilent(connection)
    }

    /**
     * Bytes between two reads of a cumulative core counter. A drop
     * means the counter restarted (core restart / reconnect): count
     * everything the new counter has seen so far.
     */
    private fun counterDelta(previous: Long, current: Long): Long =
        if (current >= previous) current - previous else current

    /** [index]-th of [parts] near-equal shares of [total]; shares sum to [total]. */
    private fun share(total: Long, parts: Long, index: Long): Long {
        val base = total / parts
        return if (index < total % parts) base + 1L else base
    }

    private fun onTotal(total: Long) {
        val up = trafficUploadBytes(total)
        val down = trafficDownloadBytes(total)
        val now = SystemClock.elapsedRealtime()
        lastPackedTotal = total

        if (prevAt == 0L || now <= prevAt) {
            // First read of the session: baseline only, no rates yet.
            prevUp = up
            prevDown = down
            prevAt = now
            publish(upRate = 0L, downRate = 0L)
            return
        }

        val elapsedMs = now - prevAt
        val deltaUp = counterDelta(prevUp, up)
        val deltaDown = counterDelta(prevDown, down)
        val counterReset = up < prevUp || down < prevDown
        val wallNow = System.currentTimeMillis()

        val upRate: Long
        val downRate: Long

        if (elapsedMs <= GAP_LIMIT_MS) {
            // Normal tick: one point with its real duration, rates
            // over the actually elapsed time (the loop is nominally
            // 1 s, but Binder + scheduling jitter make it drift).
            val seconds = elapsedMs / 1000.0
            upRate = (deltaUp / seconds).roundToLong()
            downRate = (deltaDown / seconds).roundToLong()

            if (counterReset) {
                // Cumulative counters dropped: the core restarted or
                // the tunnel reconnected — a real drop moment.
                history.recordDisconnect(wallNow)
            }
            history.record(
                startMillis = wallNow - elapsedMs,
                durationMillis = elapsedMs,
                upBytes = deltaUp,
                downBytes = deltaDown,
            )
            samples = (samples + TrafficRatePoint(upRate, downRate)).takeLast(120)
        } else {
            // Late tick: the process was frozen (background / doze)
            // while the tunnel kept forwarding, and the counters
            // accumulated the whole span. Spread the bytes evenly
            // over the elapsed seconds as ordinary 1-second points:
            // the chart reads a flat plateau at the true average
            // rate instead of a hole or one smeared spike. A counter
            // reset hidden inside the span is not stamped as a
            // disconnect — its moment is unknowable.
            val seconds = (elapsedMs / 1000L).coerceAtLeast(1L)
            val spanStart = wallNow - seconds * 1000L
            val newSamples = ArrayList<TrafficRatePoint>(seconds.toInt())

            for (i in 0L until seconds) {
                val upShare = share(deltaUp, seconds, i)
                val downShare = share(deltaDown, seconds, i)
                history.record(
                    startMillis = spanStart + i * 1000L,
                    durationMillis = 1000L,
                    upBytes = upShare,
                    downBytes = downShare,
                )
                newSamples += TrafficRatePoint(upShare, downShare)
            }
            samples = (samples + newSamples).takeLast(120)

            upRate = (deltaUp / (elapsedMs / 1000.0)).roundToLong()
            downRate = (deltaDown / (elapsedMs / 1000.0)).roundToLong()
        }

        prevUp = up
        prevDown = down
        prevAt = now

        onDeltas(deltaUp, deltaDown, elapsedMs, upRate, downRate)
    }

    /** Bookkeeping shared by normal and spread ticks. */
    private fun onDeltas(
        deltaUp: Long,
        deltaDown: Long,
        elapsedMs: Long,
        upRate: Long,
        downRate: Long,
    ) {
        // Daily totals: accumulate the raw byte deltas for today,
        // plus the real connected time (a frozen span still counts —
        // the tunnel was up throughout it).
        val todayKey = dailyStore.todayKey()
        synchronized(dailyStore) {
            dailyStore.add(todayKey, deltaUp, deltaDown)
            dailyStore.addHourly(
                System.currentTimeMillis() / 3_600_000L,
                deltaUp,
                deltaDown,
            )
            dailyStore.addConnected(
                todayKey,
                (elapsedMs / 1000L).coerceAtLeast(1L),
            )
        }

        // This session's totals, for the advanced panel and for
        // snapshotting a saved connection.
        sessionUp += deltaUp
        sessionDown += deltaDown

        // Stall detection: 5+ consecutive ticks of zero rate (after
        // the session has carried something) count once.
        if (upRate == 0L && downRate == 0L) {
            zeroStreak++
            if (zeroStreak == 5 && sessionUp + sessionDown > 0L) {
                stallCount++
            }
        } else {
            zeroStreak = 0
        }

        // Persist the daily totals once a minute.
        saveTickCount++
        if (saveTickCount % 60 == 0) {
            flush()
        }

        // Persist the latest rates for the home-screen widget,
        // throttled to one write every 5 seconds.
        widgetTickCount++
        if (widgetTickCount % 5 == 0) {
            uiStore.widgetUpRate = upRate
            uiStore.widgetDownRate = downRate
            uiStore.widgetUpdatedAt = System.currentTimeMillis()
            TuxunWidgetProvider.requestUpdate(app)
        }

        publish(upRate, downRate)
    }

    private fun publish(upRate: Long, downRate: Long) {
        val todayKey = dailyStore.todayKey()
        val today = synchronized(dailyStore) { dailyStore.today(todayKey) }
        snapshot = TrafficSnapshot(
            running = sampling,
            packedTotal = lastPackedTotal,
            upRate = upRate,
            downRate = downRate,
            samples = samples,
            historyPoints = history.points(),
            disconnectTimes = history.disconnects(),
            todayUp = today.up,
            todayDown = today.down,
            todayConnectedSeconds = synchronized(dailyStore) {
                dailyStore.dayConnectedSeconds(todayKey)
            },
            sessionUpBytes = sessionUp,
            sessionDownBytes = sessionDown,
            stallCount = stallCount,
        )
    }
}
