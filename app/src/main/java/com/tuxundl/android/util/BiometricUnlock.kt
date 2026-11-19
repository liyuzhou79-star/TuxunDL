package com.tuxundl.android.util

import android.content.Context
import androidx.biometric.BiometricManager
import com.tuxundl.android.design.store.UiStore

/**
 * Biometric availability for the app lock (1.6.1).
 *
 * One question, asked the same way by the settings toggle (which
 * only appears when the answer is yes) and by the lock screen
 * (which only offers the prompt then): can this device authenticate
 * a strong biometric right now — hardware present AND at least one
 * fingerprint / face enrolled? Anything less (no hardware, nothing
 * enrolled, hardware temporarily unavailable) hides the feature
 * completely instead of showing a door that cannot open.
 */
object BiometricUnlock {

    /** True when a strong biometric can be used immediately. */
    fun isAvailable(context: Context): Boolean {
        return try {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    /** True when the lock screen should offer biometric unlock. */
    fun shouldOffer(context: Context, store: UiStore): Boolean =
        store.appLockBiometric && isAvailable(context)
}
