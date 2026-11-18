package com.tuxundl.android.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tuxundl.android.ExternalControlActivity
import com.tuxundl.android.MainActivity
import com.tuxundl.android.R
import com.tuxundl.android.ScheduleReceiver
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.remote.Remote
import com.tuxundl.android.remote.StatusClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import com.tuxundl.android.service.R as ServiceR

/**
 * Scheduling engine for the home panel's two timed rules (1.5.4):
 *
 *  - timed on/off: start the proxy at one time of day, stop it at
 *    another;
 *  - night auto-switch: inside a night window either switch the
 *    tunnel to Global mode or turn the proxy off. When the window
 *    ends after a Global switch, the mode recorded at window start
 *    is restored — unless the user changed the mode by hand during
 *    the night, which is always respected.
 *
 * One engine drives every rule: [reschedule] computes the single
 * next event across all enabled rules and arms one alarm for it;
 * when it fires, [execute] runs it and re-arms. Everything reuses
 * the paths the UI already uses — [startClashService] /
 * [stopClashService] for the tunnel (the same calls the power
 * button, the QS tile and the boot receiver make) and the Session /
 * Persist override slots through [withClash] for the mode (the
 * same calls the home mode selector makes). The Persist write makes
 * the night mode survive core restarts inside the window.
 *
 * Android background limits, honestly: starts are attempted from an
 * exact alarm, which the platform lists as an exemption from the
 * Android 12+ background foreground-service start restriction. If
 * the exact-alarm permission is missing we fall back to an inexact
 * alarm (timing may drift), and if a start is still blocked — or
 * the VPN consent was never granted — a notification offers a
 * one-tap start instead of failing silently.
 */
object ProxyScheduler {
    const val ACTION_EVENT = "com.tuxundl.android.action.SCHEDULE_EVENT"
    const val EXTRA_KIND = "kind"

    const val KIND_SCHEDULE_ON = "schedule_on"
    const val KIND_SCHEDULE_OFF = "schedule_off"
    const val KIND_NIGHT_START = "night_start"
    const val KIND_NIGHT_END = "night_end"

    /** Night action values stored in [UiStore.nightAction]. */
    const val NIGHT_ACTION_GLOBAL = 0
    const val NIGHT_ACTION_STOP = 1

    private const val REQUEST_CODE = 1540
    private const val NOTIFICATION_ID = 1541
    private const val CHANNEL_ID = "proxy_schedule"
    private const val CLASH_TIMEOUT_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether exact alarms may be scheduled right now. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false

        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * (Re)arm the single alarm for the next event across all enabled
     * rules, or disarm when no rule is on. Called whenever a setting
     * changes, when the app opens, after every fired event, and on
     * boot / clock changes (from [ScheduleReceiver]).
     */
    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val store = UiStore(appContext)

        val next = nextEvent(store, System.currentTimeMillis())

        if (next == null) {
            alarmManager.cancel(operation(appContext, null))
            return
        }

        val op = operation(appContext, next.kind)

        try {
            if (canScheduleExact(appContext)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.atMillis,
                    op,
                )
            } else {
                // No exact-alarm permission: an inexact alarm still
                // fires, just possibly a few minutes late.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next.atMillis,
                    op,
                )
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call:
            // degrade to inexact rather than dropping the schedule.
            Log.w("Exact alarm rejected, falling back to inexact: $e")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, op)
        }
    }

    /**
     * Panel hook: settings were just written to the store, so re-arm,
     * and if the night rule was switched off mid-window, undo the
     * night state it had applied.
     */
    fun settingsChanged(context: Context) {
        val appContext = context.applicationContext

        reschedule(appContext)

        val store = UiStore(appContext)
        if (!store.nightEnabled &&
            (store.scheduleNightActive || store.scheduleNightPersistApplied)
        ) {
            scope.launch { restoreNight(appContext, store) }
        }
    }

    /** Run one fired event, then re-arm for the following one. */
    suspend fun execute(context: Context, kind: String) {
        val appContext = context.applicationContext
        val store = UiStore(appContext)

        try {
            when (kind) {
                KIND_SCHEDULE_ON ->
                    if (store.scheduleEnabled) startProxy(appContext)

                KIND_SCHEDULE_OFF ->
                    if (store.scheduleEnabled && isRunning(appContext)) {
                        appContext.stopClashService()
                    }

                KIND_NIGHT_START ->
                    if (store.nightEnabled) nightStart(appContext, store)

                KIND_NIGHT_END ->
                    if (store.nightEnabled) nightEnd(appContext, store)
            }
        } catch (e: Exception) {
            Log.w("Scheduled event $kind failed: $e", e)
        } finally {
            reschedule(appContext)
        }
    }

    /**
     * Re-align after boot / clock change / app open: re-arm, and
     * reconcile the night Global state with the actual window (an
     * alarm may have been missed while the device was off). The
     * night Stop action is deliberately not reconciled — stopping a
     * tunnel the user may have just started by hand would fight
     * them; its alarms still fire on schedule.
     */
    suspend fun reconcileNight(context: Context) {
        val appContext = context.applicationContext
        val store = UiStore(appContext)

        reschedule(appContext)

        if (!store.nightEnabled || store.nightAction != NIGHT_ACTION_GLOBAL) return

        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val inside = inWindow(minutes, store.nightStartMinutes, store.nightEndMinutes)

        if (inside && !store.scheduleNightActive) {
            nightStart(appContext, store)
        } else if (!inside && store.scheduleNightActive) {
            restoreNight(appContext, store)
        }
    }

    /** Fire-and-forget entry for [ScheduleReceiver]'s alarm events. */
    fun handleEventAsync(context: Context, kind: String, onDone: () -> Unit) {
        scope.launch {
            try {
                execute(context, kind)
            } finally {
                onDone()
            }
        }
    }

    /** Fire-and-forget entry for boot / clock-change realignment. */
    fun handleRealignAsync(context: Context, onDone: () -> Unit) {
        scope.launch {
            try {
                reconcileNight(context)
            } catch (e: Exception) {
                Log.w("Schedule realign failed: $e", e)
            } finally {
                onDone()
            }
        }
    }

    private fun operation(context: Context, kind: String?): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ACTION_EVENT)
            .putExtra(EXTRA_KIND, kind)

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class Event(val kind: String, val atMillis: Long)

    private fun nextEvent(store: UiStore, now: Long): Event? {
        val candidates = mutableListOf<Event>()

        fun daily(kind: String, minutesOfDay: Int) {
            candidates += Event(kind, nextOccurrence(now, minutesOfDay))
        }

        if (store.scheduleEnabled) {
            daily(KIND_SCHEDULE_ON, store.scheduleOnMinutes)
            daily(KIND_SCHEDULE_OFF, store.scheduleOffMinutes)
        }
        if (store.nightEnabled) {
            daily(KIND_NIGHT_START, store.nightStartMinutes)
            daily(KIND_NIGHT_END, store.nightEndMinutes)
        }

        return candidates.minByOrNull { it.atMillis }
    }

    /** Next wall-clock occurrence of [minutesOfDay] strictly after [now]. */
    private fun nextOccurrence(now: Long, minutesOfDay: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        cal.set(Calendar.MINUTE, minutesOfDay % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)

        return cal.timeInMillis
    }

    /** Window check that stays correct for windows crossing midnight. */
    private fun inWindow(minutes: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            minutes in start until end
        } else {
            minutes >= start || minutes < end
        }
    }

    private fun isRunning(context: Context): Boolean {
        return StatusClient(context).currentProfile() != null
    }

    /**
     * Start through the exact call the power button uses. A non-null
     * return is the system's VPN-consent intent (never granted), and
     * a thrown exception means the platform blocked the background
     * start — both degrade to a notification instead of silence.
     */
    private fun startProxy(context: Context) {
        if (isRunning(context)) return

        val consentMissing = try {
            context.startClashService() != null
        } catch (e: Exception) {
            Log.w("Scheduled start blocked by the system: $e", e)
            notifyStartFailed(context, consentMissing = false)
            return
        }

        if (consentMissing) {
            notifyStartFailed(context, consentMissing = true)
        }
    }

    private suspend fun nightStart(context: Context, store: UiStore) {
        if (store.nightAction == NIGHT_ACTION_STOP) {
            if (isRunning(context)) context.stopClashService()

            store.scheduleNightActive = true
            return
        }

        // Record the current session mode before touching anything,
        // so the night end can put it back.
        val applied = withClashBound(context) {
            val session = queryOverride(Clash.OverrideSlot.Session)

            if (!store.scheduleNightActive) {
                store.schedulePrevSessionMode = session.mode?.name ?: ""
            }
            if (session.mode != TunnelState.Mode.Global) {
                session.mode = TunnelState.Mode.Global
                patchOverride(Clash.OverrideSlot.Session, session)
            }

            val persist = queryOverride(Clash.OverrideSlot.Persist)

            if (persist.mode != TunnelState.Mode.Global) {
                persist.mode = TunnelState.Mode.Global
                patchOverride(Clash.OverrideSlot.Persist, persist)
                store.scheduleNightPersistApplied = true
            }

            true
        }

        if (applied == true) store.scheduleNightActive = true
    }

    private suspend fun nightEnd(context: Context, store: UiStore) {
        if (store.nightAction == NIGHT_ACTION_STOP) {
            // The night end intentionally does not restart the
            // proxy; morning starts belong to the timed on/off rule.
            store.scheduleNightActive = false
            return
        }

        restoreNight(context, store)
    }

    /**
     * Undo a scheduler-applied night Global state: drop the Persist
     * mode the scheduler wrote, and — only if the session mode is
     * still Global, i.e. nobody changed it by hand during the
     * night — restore the session mode recorded at window start.
     */
    private suspend fun restoreNight(context: Context, store: UiStore) {
        if (store.nightAction == NIGHT_ACTION_STOP) {
            store.scheduleNightActive = false
            store.scheduleNightPersistApplied = false
            store.schedulePrevSessionMode = ""
            return
        }

        val prevMode = store.schedulePrevSessionMode

        withClashBound(context) {
            if (store.scheduleNightPersistApplied) {
                val persist = queryOverride(Clash.OverrideSlot.Persist)

                if (persist.mode == TunnelState.Mode.Global) {
                    persist.mode = null
                    patchOverride(Clash.OverrideSlot.Persist, persist)
                }
            }

            val session = queryOverride(Clash.OverrideSlot.Session)

            if (session.mode == TunnelState.Mode.Global) {
                session.mode = when (prevMode) {
                    TunnelState.Mode.Global.name -> TunnelState.Mode.Global
                    TunnelState.Mode.Direct.name -> TunnelState.Mode.Direct
                    TunnelState.Mode.Rule.name -> TunnelState.Mode.Rule
                    // No recorded session mode: clearing the field
                    // returns the tunnel to the profile's own mode.
                    else -> null
                }
                patchOverride(Clash.OverrideSlot.Session, session)
            }

            true
        }

        store.scheduleNightActive = false
        store.scheduleNightPersistApplied = false
        store.schedulePrevSessionMode = ""
    }

    /**
     * [withClash] waits for the remote service binding, which the
     * app normally only holds while visible. Bind it on demand for
     * background work, bound the wait, and hand the binding back
     * when no activity exists (when one does, the app's visibility
     * observer owns it).
     */
    private suspend fun <T> withClashBound(
        context: Context,
        block: suspend com.tuxundl.android.service.remote.IClashManager.() -> T,
    ): T? {
        Remote.service.bind()

        return try {
            withTimeoutOrNull(CLASH_TIMEOUT_MS) {
                withClash(block = block)
            }
        } finally {
            if (ApplicationObserver.createdActivities.isEmpty()) {
                Remote.service.unbind()
            }
        }
    }

    /**
     * Fallback for a scheduled start the system would not let
     * through: a high-priority notification. Its button opens the
     * external-control entry with the start action — a real user
     * tap, which is itself an exemption from the background start
     * restriction. When the VPN consent is what is missing, the tap
     * opens the main screen instead, where the normal start flow
     * asks for it.
     */
    private fun notifyStartFailed(context: Context, consentMissing: Boolean) {
        val notificationManager =
            context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.schedule_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val tapIntent = if (consentMissing) {
            MainActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            ExternalControlActivity::class.intent
                .setAction(Intents.ACTION_START_CLASH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val tap = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ServiceR.drawable.ic_logo_tuxundl)
            .setContentTitle(context.getString(R.string.schedule_start_failed_title))
            .setContentText(
                context.getString(
                    if (consentMissing) {
                        R.string.schedule_start_consent_text
                    } else {
                        R.string.schedule_start_failed_text
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .addAction(
                0,
                context.getString(R.string.schedule_start_now),
                tap,
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted: nothing more we can do.
            Log.w("Scheduled start notification blocked: $e")
        }
    }
}
