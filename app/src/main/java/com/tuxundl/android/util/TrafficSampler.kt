package com.tuxundl.android.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.tuxundl.android.TuxunWidgetProvider
import com.tuxundl.android.common.compat.currentProcessName
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.store.UiStore
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
import org.json.JSONArray
import org.json.JSONObject
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
 * The move into the tunnel's process (1.6.1): until 1.6.0 the one
 * span that could not be recorded was the app (UI) process being
 * killed while the tunnel — a separate process — lived on. The
 * sampler therefore changed homes: the recording loop now runs in
 * the :background process, alongside the tunnel it measures, and
 * the UI process only reads. Single-writer discipline: exactly one
 * process ever records — the :background one — and it publishes
 * everything it records to shared files in filesDir (a light
 * live-state file every tick, the tiered history every 10 ticks),
 * written atomically so a reader never sees a torn page. The UI
 * process mirrors those files into the same [TrafficSnapshot] the
 * home screen has always read, and reloads the daily store from
 * its file as it changes.
 *
 * If the :background process itself restarts mid-connection, the
 * new instance restores the session (counters baseline, session
 * totals, samples, tiered history) from those same files and the
 * first tick spreads the dead span with the usual late-tick rule,
 * so even that gap reads as a plateau instead of a hole. A stale
 * restore (tunnel gone, or the state is old) is discarded and the
 * session starts clean, exactly like a fresh tunnel start.
 */
object TrafficSampler {
    private const val TICK_MS = 1_000L

    /** Ticks at most this late still record as one real-duration point. */
    private const val GAP_LIMIT_MS = 3_000L

    /** Process-name suffix of the tunnel's (:background) process. */
    private const val BACKGROUND_PROCESS_SUFFIX = ":background"

    /** A restorable session state older than this is not resumed. */
    private const val RESTORE_MAX_AGE_MS = 600_000L

    /** A live-state file not refreshed within this span is stale. */
    private const val LIVE_STALE_MS = 5_000L

    private const val LIVE_FILE = "traffic-live.json"
    private const val HISTORY_FILE = "traffic-history.json"
    private const val DAILY_FILE = "traffic-daily.json"

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

    /**
     * True in the :background (tunnel) process — the one writer.
     * False in the UI process, which only mirrors the shared files
     * into [snapshot] / [dailyStore] (1.6.1).
     */
    private var writer = false

    /** Wall-clock time the history file was last written (writer). */
    private var lastHistoryWriteWall = 0L

    /** Ticks since the last history-file write (writer). */
    private var historyTickCount = 0

    /**
     * A previous writer instance's session is loaded and waiting to
     * be continued (writer, set at init): the first step that finds
     * the tunnel up adopts it instead of resetting; if the tunnel
     * is down it is discarded.
     */
    private var restoredSession = false

    // Reader-mode mirror state (UI process only).
    private var readerLiveStamp = -1L
    private var readerHistoryStamp = -1L
    private var readerHistoryPoints: List<TrafficPoint> = emptyList()
    private var readerDisconnects: List<Long> = emptyList()
    private var readerDailyModified = -1L
    private var readerDailyReloadAt = 0L

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

    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        uiStore = UiStore(application)
        dailyStore = DailyTrafficStore(File(application.filesDir, DAILY_FILE))
        runCatching { dailyStore.load() }

        // Role by process (1.6.1): the :background process records,
        // every other process (in practice: the UI) only reads. The
        // clash-broadcast hints the 1.5.3 sampler used are gone with
        // the move — they were delivered in the UI process, which no
        // longer samples; the StatusClient poll below was always the
        // authoritative path and now stands alone (a stop+start
        // inside one polling interval can merge sessions, as it
        // already could whenever the app was invisible).
        writer = application.currentProcessName
            .endsWith(BACKGROUND_PROCESS_SUFFIX)

        if (writer) {
            restoreSessionIfAny()
            scope.launch { loop() }
        } else {
            scope.launch { readerLoop() }
        }
    }

    /**
     * Flush the daily totals (MainActivity still calls this on
     * stop). Writer mode saves; reader mode must never overwrite
     * the writer's file with its own staler in-memory copy, so it
     * reloads from disk instead.
     */
    fun flush() {
        if (!::dailyStore.isInitialized) return
        synchronized(dailyStore) {
            runCatching {
                if (writer) dailyStore.save() else dailyStore.load()
            }
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
        if (!sampling) {
            if (tunnelUp()) {
                if (restoredSession) {
                    // Continue the session a previous instance of
                    // this process was recording: the baseline was
                    // restored at init, so the first total spreads
                    // the dead span like any other late tick.
                    restoredSession = false
                    sampling = true
                    bind()
                } else {
                    startSampling()
                }
            } else if (restoredSession) {
                // The tunnel went away while this process was dead;
                // the restored session is simply over.
                restoredSession = false
                resetSession()
                writeHistoryFile()
                publish(upRate = 0L, downRate = 0L)
            }
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
        writeHistoryFile()
        publish(upRate = 0L, downRate = 0L)
    }

    private fun stopSampling() {
        sampling = false
        unbind()
        flush()
        resetSession()
        writeHistoryFile()
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
        historyTickCount = 0
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

        // Persist the tiered history for the UI process (and for a
        // possible restart of this one) every 10 seconds.
        historyTickCount++
        if (historyTickCount % 10 == 0) {
            writeHistoryFile()
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

        if (writer) writeLiveFile()
    }

    // ------------------------------------------------------------------
    // Shared files (1.6.1). The writer publishes; the reader mirrors.
    // Every write is tmp-file + rename, so a cross-process reader can
    // never observe a half-written page.
    // ------------------------------------------------------------------

    private fun liveFile() = File(app.filesDir, LIVE_FILE)

    private fun historyFile() = File(app.filesDir, HISTORY_FILE)

    private fun dailyFile() = File(app.filesDir, DAILY_FILE)

    private fun writeAtomic(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w("TrafficSampler write ${file.name} failed: $e")
        }
    }

    private fun readJson(file: File): JSONObject? {
        return try {
            if (!file.exists()) return null
            JSONObject(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /** The light live-state file: everything the home screen needs. */
    private fun writeLiveFile() {
        val snap = snapshot
        val samplesJson = JSONArray()
        for (sample in snap.samples) {
            samplesJson.put(
                JSONArray().put(sample.upRate).put(sample.downRate),
            )
        }

        val json = JSONObject()
            .put("v", 1)
            .put("updatedAtWall", System.currentTimeMillis())
            .put("historyStamp", lastHistoryWriteWall)
            .put("running", snap.running)
            .put("packedTotal", snap.packedTotal)
            .put("upRate", snap.upRate)
            .put("downRate", snap.downRate)
            .put("samples", samplesJson)
            .put("todayUp", snap.todayUp)
            .put("todayDown", snap.todayDown)
            .put("todayConn", snap.todayConnectedSeconds)
            .put("sessionUp", snap.sessionUpBytes)
            .put("sessionDown", snap.sessionDownBytes)
            .put("stallCount", snap.stallCount)
            // Writer recovery state: the next instance of this
            // process resumes the session from exactly here.
            .put("prevUp", prevUp)
            .put("prevDown", prevDown)
            .put("prevAtElapsed", prevAt)
            .put("zeroStreak", zeroStreak)

        writeAtomic(liveFile(), json.toString())
    }

    /** The tiered 2-hour history + disconnect marks. */
    private fun writeHistoryFile() {
        val pointsJson = JSONArray()
        for (point in history.points()) {
            pointsJson.put(
                JSONArray()
                    .put(point.startMillis)
                    .put(point.windowMillis)
                    .put(point.coveredMillis)
                    .put(point.upBytes)
                    .put(point.downBytes),
            )
        }
        val disconnectsJson = JSONArray()
        for (at in history.disconnects()) {
            disconnectsJson.put(at)
        }

        val json = JSONObject()
            .put("v", 1)
            .put("points", pointsJson)
            .put("disconnects", disconnectsJson)

        writeAtomic(historyFile(), json.toString())
        lastHistoryWriteWall = System.currentTimeMillis()
    }

    /**
     * Writer init: adopt the session a previous instance of this
     * process left behind, when the live file says it was running
     * and is fresh enough to still be the same connection. The
     * first step() decides whether it is really continued (tunnel
     * up) or discarded (tunnel gone).
     */
    private fun restoreSessionIfAny() {
        val live = readJson(liveFile()) ?: return
        if (!live.optBoolean("running")) return

        val updatedAtWall = live.optLong("updatedAtWall", 0L)
        if (System.currentTimeMillis() - updatedAtWall >
            RESTORE_MAX_AGE_MS
        ) {
            return
        }

        readJson(historyFile())?.let { stored ->
            val points = mutableListOf<TrafficPoint>()
            val pointsJson = stored.optJSONArray("points")
            if (pointsJson != null) {
                for (i in 0 until pointsJson.length()) {
                    val entry = pointsJson.optJSONArray(i) ?: continue
                    points += TrafficPoint(
                        startMillis = entry.optLong(0),
                        windowMillis = entry.optLong(1),
                        coveredMillis = entry.optLong(2),
                        upBytes = entry.optLong(3),
                        downBytes = entry.optLong(4),
                    )
                }
            }
            val disconnects = mutableListOf<Long>()
            val disconnectsJson = stored.optJSONArray("disconnects")
            if (disconnectsJson != null) {
                for (i in 0 until disconnectsJson.length()) {
                    disconnects += disconnectsJson.optLong(i)
                }
            }
            history.restore(points, disconnects)
        }
        lastHistoryWriteWall = historyFile().lastModified()

        val restoredSamples = mutableListOf<TrafficRatePoint>()
        val samplesJson = live.optJSONArray("samples")
        if (samplesJson != null) {
            for (i in 0 until samplesJson.length()) {
                val entry = samplesJson.optJSONArray(i) ?: continue
                restoredSamples += TrafficRatePoint(
                    upRate = entry.optLong(0),
                    downRate = entry.optLong(1),
                )
            }
        }
        samples = restoredSamples.takeLast(120)

        prevUp = live.optLong("prevUp")
        prevDown = live.optLong("prevDown")
        prevAt = live.optLong("prevAtElapsed")
        zeroStreak = live.optInt("zeroStreak")
        sessionUp = live.optLong("sessionUp")
        sessionDown = live.optLong("sessionDown")
        stallCount = live.optInt("stallCount")
        lastPackedTotal = live.optLong("packedTotal")

        restoredSession = true
    }

    // ------------------------------------------------------------------
    // Reader mode (UI process): mirror the shared files into the
    // same snapshot / daily store the UI has always consumed.
    // ------------------------------------------------------------------

    private suspend fun readerLoop() {
        while (true) {
            try {
                readerStep()
            } catch (e: Exception) {
                Log.w("TrafficSampler reader step failed: $e", e)
            }
            delay(TICK_MS)
        }
    }

    private fun readerStep() {
        val now = System.currentTimeMillis()
        val live = readJson(liveFile())

        if (live == null) {
            if (snapshot.running) snapshot = TrafficSnapshot()
            return
        }

        val updatedAtWall = live.optLong("updatedAtWall", 0L)
        val stale = now - updatedAtWall > LIVE_STALE_MS

        if (stale) {
            // The writer is gone (its process hosts the tunnel, so
            // the tunnel is gone too): show the session as stopped,
            // keeping the last recorded chart on screen.
            if (snapshot.running) {
                snapshot = snapshot.copy(running = false, upRate = 0L, downRate = 0L)
            }
            return
        }

        // The daily store backs the week views: reload it when the
        // writer's saves land, at most every few seconds.
        val dailyModified = dailyFile().lastModified()
        if (dailyModified != readerDailyModified &&
            now - readerDailyReloadAt > 4_000L
        ) {
            readerDailyModified = dailyModified
            readerDailyReloadAt = now
            synchronized(dailyStore) {
                runCatching { dailyStore.load() }
            }
        }

        if (updatedAtWall == readerLiveStamp) return
        readerLiveStamp = updatedAtWall

        val historyStamp = live.optLong("historyStamp", 0L)
        if (historyStamp != readerHistoryStamp) {
            readerHistoryStamp = historyStamp
            readJson(historyFile())?.let { stored ->
                val points = mutableListOf<TrafficPoint>()
                val pointsJson = stored.optJSONArray("points")
                if (pointsJson != null) {
                    for (i in 0 until pointsJson.length()) {
                        val entry = pointsJson.optJSONArray(i) ?: continue
                        points += TrafficPoint(
                            startMillis = entry.optLong(0),
                            windowMillis = entry.optLong(1),
                            coveredMillis = entry.optLong(2),
                            upBytes = entry.optLong(3),
                            downBytes = entry.optLong(4),
                        )
                    }
                }
                val disconnects = mutableListOf<Long>()
                val disconnectsJson = stored.optJSONArray("disconnects")
                if (disconnectsJson != null) {
                    for (i in 0 until disconnectsJson.length()) {
                        disconnects += disconnectsJson.optLong(i)
                    }
                }
                readerHistoryPoints = points
                readerDisconnects = disconnects
            }
        }

        val mirroredSamples = mutableListOf<TrafficRatePoint>()
        val samplesJson = live.optJSONArray("samples")
        if (samplesJson != null) {
            for (i in 0 until samplesJson.length()) {
                val entry = samplesJson.optJSONArray(i) ?: continue
                mirroredSamples += TrafficRatePoint(
                    upRate = entry.optLong(0),
                    downRate = entry.optLong(1),
                )
            }
        }

        snapshot = TrafficSnapshot(
            running = live.optBoolean("running"),
            packedTotal = live.optLong("packedTotal"),
            upRate = live.optLong("upRate"),
            downRate = live.optLong("downRate"),
            samples = mirroredSamples,
            historyPoints = readerHistoryPoints,
            disconnectTimes = readerDisconnects,
            todayUp = live.optLong("todayUp"),
            todayDown = live.optLong("todayDown"),
            todayConnectedSeconds = live.optLong("todayConn"),
            sessionUpBytes = live.optLong("sessionUp"),
            sessionDownBytes = live.optLong("sessionDown"),
            stallCount = live.optInt("stallCount"),
        )
    }
}
