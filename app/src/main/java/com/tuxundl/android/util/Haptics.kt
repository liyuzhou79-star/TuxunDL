package com.tuxundl.android.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.tuxundl.android.design.store.UiStore

/**
 * Haptic feedback for key actions (1.2.5): a short tap when a node is
 * picked, a double buzz when the core connects, one longer buzz when
 * it disconnects. Strength is user-tunable in settings
 * (0 light / 1 medium / 2 strong); the switch there turns it all off.
 */
object Haptics {

    enum class Kind { Tap, Success, Disconnect }

    /** Strength index meaning "use the stored custom amplitude" (1.4.0). */
    const val CUSTOM_STRENGTH = 3

    private fun amplitude(strength: Int): Int = when (strength) {
        0 -> 70
        2 -> 255
        else -> 140
    }

    private fun timings(kind: Kind): LongArray = when (kind) {
        Kind.Tap -> longArrayOf(0, 30)
        Kind.Success -> longArrayOf(0, 40, 60, 40)
        Kind.Disconnect -> longArrayOf(0, 90)
    }

    /** Buzz with the user's stored settings; no-op when disabled. */
    fun buzz(context: Context, store: UiStore, kind: Kind) {
        if (!store.hapticsEnabled) return

        val amp = if (store.hapticsStrength == CUSTOM_STRENGTH) {
            store.hapticsCustomAmplitude
        } else {
            amplitude(store.hapticsStrength)
        }

        vibrate(context, amp, kind)
    }

    /** Buzz at an explicit strength (the settings preview uses this). */
    fun buzz(context: Context, strength: Int, kind: Kind) {
        vibrate(context, amplitude(strength), kind)
    }

    /** Buzz at an explicit amplitude, 1..255 (custom slider preview, 1.4.0). */
    fun buzzAmplitude(context: Context, amplitude: Int, kind: Kind) {
        vibrate(context, amplitude.coerceIn(1, 255), kind)
    }

    private fun vibrate(context: Context, amp: Int, kind: Kind) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE)
            as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = timings(kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = IntArray(pattern.size) { index ->
                if (index % 2 == 0) 0 else amp
            }
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, amplitudes, -1),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
