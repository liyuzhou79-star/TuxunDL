package com.tuxundl.android.util

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.tuxundl.android.common.compat.startForegroundServiceCompat
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.service.ClashService
import com.tuxundl.android.service.TunService
import com.tuxundl.android.service.util.sendBroadcastSelf

fun Context.startClashService(): Intent? {
    val startTun = UiStore(this).enableVpn

    if (startTun) {
        val vpnRequest = VpnService.prepare(this)
        if (vpnRequest != null)
            return vpnRequest

        startForegroundServiceCompat(TunService::class.intent)
    } else {
        startForegroundServiceCompat(ClashService::class.intent)
    }

    return null
}

fun Context.stopClashService() {
    sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP))
}