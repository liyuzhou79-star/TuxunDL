package com.tuxundl.android.tor

import android.content.Context

/**
 * TV flavour: no Tor feature — no engine, no binaries, no entry.
 * The home mode selector hides the Tor option when [available] is
 * false, so nothing here is ever exercised.
 */
object TorEntryProvider {

    fun create(): TorEntry = Stub

    private object Stub : TorEntry {
        override val available: Boolean = false

        override fun isActive(context: Context): Boolean = false

        override fun launch(context: Context) = Unit

        override suspend fun exitTorMode(context: Context) = Unit
    }
}
