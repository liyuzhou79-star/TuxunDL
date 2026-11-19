package com.tuxundl.android.tor

import android.content.Context

/**
 * Bridge from the main UI to the Tor feature (1.7.3).
 *
 * The Tor engine and page are compiled into the phone flavours
 * only (src/tor); the TV flavour carries a stub instead, so the
 * main source set talks to this interface and every variant
 * supplies its own [TorEntryProvider].
 */
interface TorEntry {

    /** Whether this build contains the Tor feature at all. */
    val available: Boolean

    /** Whether Tor egress is currently armed (whole-device Tor). */
    fun isActive(context: Context): Boolean

    /** Open the full-screen Tor page. */
    fun launch(context: Context)

    /**
     * Leave Tor mode without opening the page (the user picked a
     * different mode on the home selector): disarm the egress,
     * restoring the previous mode, then stop Tor. Suspends until
     * the disarm has been written, so a following mode patch cannot
     * resurrect the Tor marker from a stale override snapshot.
     */
    suspend fun exitTorMode(context: Context)
}
