package com.tuxundl.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tuxundl.android.util.ProxyScheduler

/**
 * Entry point for the 1.5.4 proxy schedule: alarm events fired by
 * [ProxyScheduler]'s single armed alarm land here, and boot /
 * package-replaced / clock-change broadcasts re-arm and reconcile
 * the schedule (alarms do not survive a reboot, and wall-clock
 * events shift when the user changes the time or time zone).
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ProxyScheduler.ACTION_EVENT -> {
                val kind = intent.getStringExtra(ProxyScheduler.EXTRA_KIND) ?: return
                val pending = goAsync()

                ProxyScheduler.handleEventAsync(context, kind) { pending.finish() }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pending = goAsync()

                ProxyScheduler.handleRealignAsync(context) { pending.finish() }
            }
        }
    }
}
