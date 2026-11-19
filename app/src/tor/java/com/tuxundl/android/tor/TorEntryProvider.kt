package com.tuxundl.android.tor

import android.content.Context
import android.content.Intent

/**
 * Phone flavours (alpha, meta): the real Tor entry.
 */
object TorEntryProvider {

    fun create(): TorEntry = Real

    private object Real : TorEntry {
        override val available: Boolean = true

        override fun isActive(context: Context): Boolean = TorEgress.active

        override fun launch(context: Context) {
            val intent = Intent(context, TorActivity::class.java)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        override suspend fun exitTorMode(context: Context) {
            TorEgress.exitTorMode()
        }
    }
}
