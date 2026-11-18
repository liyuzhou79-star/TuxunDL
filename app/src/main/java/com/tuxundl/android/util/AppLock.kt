package com.tuxundl.android.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import com.tuxundl.android.AppLockActivity
import com.tuxundl.android.design.store.UiStore
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * App lock secrets (1.5.0). Only a salted SHA-256 of the secret is
 * ever stored (salt + hash live in [UiStore]); the secret itself is
 * never written anywhere. Pattern secrets are the visited dot
 * indices joined by commas, PINs their digit string, passwords the
 * typed text.
 */
object AppLock {
    const val TYPE_PATTERN = 0
    const val TYPE_PIN = 1
    const val TYPE_PASSWORD = 2

    /** True when a secret is stored and the lock is switched on. */
    fun isConfigured(store: UiStore): Boolean =
        store.appLockEnabled && store.appLockHash.isNotEmpty()

    /** Store a new secret (setup and change both go through here). */
    fun setSecret(store: UiStore, type: Int, secret: String) {
        val salt = ByteArray(16)
            .also { SecureRandom().nextBytes(it) }
            .toHex()
        store.appLockSalt = salt
        store.appLockHash = hash(salt, secret)
        store.appLockType = type
        store.appLockEnabled = true
    }

    /** Constant-time check of a candidate against the stored hash. */
    fun verify(store: UiStore, secret: String): Boolean {
        if (store.appLockHash.isEmpty()) return false

        val candidate = hash(store.appLockSalt, secret)
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            store.appLockHash.toByteArray(Charsets.UTF_8),
        )
    }

    /** Remove the lock entirely (only after verifying the old secret). */
    fun clear(store: UiStore) {
        store.appLockEnabled = false
        store.appLockHash = ""
        store.appLockSalt = ""
    }

    private fun hash(salt: String, secret: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$secret".toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

/**
 * The app-lock gate (1.5.0). Watches activity starts/stops for the
 * whole process: a cold start begins locked; when the app has spent
 * longer than the user's chosen timeout in the background, the next
 * foreground activity is covered by [AppLockActivity] before the
 * user can interact. Unlocking (or setting/changing the secret in
 * settings) clears [locked] for this session; backgrounding re-arms
 * it according to the timeout (0 minutes = at once).
 *
 * While the lock is configured, every activity also gets
 * FLAG_SECURE: the recents list shows a blank card and screenshots
 * are blocked, so the lock cannot be peeked around.
 */
object AppLockManager {
    @Volatile
    var locked: Boolean = false
        private set

    @Volatile
    private var backgroundedAtMs = 0L

    @Volatile
    private var lockScreenAlive = false

    private var startedCount = 0

    fun attach(application: Application) {
        locked = AppLock.isConfigured(UiStore(application))

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    if (activity is AppLockActivity) {
                        lockScreenAlive = true
                    } else if (AppLock.isConfigured(UiStore(activity))) {
                        activity.window.addFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    }
                }

                override fun onActivityStarted(activity: Activity) {
                    if (activity is AppLockActivity) return

                    startedCount++
                    if (startedCount == 1) onForeground(activity)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (activity is AppLockActivity) return

                    startedCount--
                    if (startedCount == 0) onBackground(activity)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (activity is AppLockActivity) {
                        lockScreenAlive = false
                    }
                }

                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit
            },
        )
    }

    /** Successful unlock from the lock screen. */
    fun onUnlocked() {
        locked = false
        backgroundedAtMs = 0L
    }

    /** The secret was just set or changed in settings by its owner. */
    fun onSecretChanged() {
        locked = false
        backgroundedAtMs = 0L
    }

    /** The lock was turned off in settings. */
    fun onLockDisabled() {
        locked = false
        backgroundedAtMs = 0L
    }

    /**
     * Add or clear FLAG_SECURE on every live activity after the lock
     * is toggled in settings, so the change applies without waiting
     * for activities to be recreated.
     */
    fun refreshSecureFlags() {
        ApplicationObserver.createdActivities.forEach { activity ->
            if (activity is AppLockActivity) return@forEach

            if (AppLock.isConfigured(UiStore(activity))) {
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            } else {
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }
        }
    }

    private fun onForeground(activity: Activity) {
        val store = UiStore(activity)
        if (!AppLock.isConfigured(store)) {
            locked = false
            return
        }

        val backgroundedAt = backgroundedAtMs
        if (!locked && backgroundedAt > 0L) {
            val relockMs = store.appLockRelockMinutes.toLong() * 60_000L
            if (SystemClock.elapsedRealtime() - backgroundedAt >= relockMs) {
                locked = true
            }
        }
        backgroundedAtMs = 0L

        if (locked && !lockScreenAlive) {
            activity.startActivity(
                Intent(activity, AppLockActivity::class.java),
            )
        }
    }

    private fun onBackground(activity: Activity) {
        val store = UiStore(activity)
        backgroundedAtMs = SystemClock.elapsedRealtime()

        // "Immediately" arms the lock the moment the app leaves the
        // foreground; the timed options are judged on return.
        if (AppLock.isConfigured(store) &&
            store.appLockRelockMinutes == 0
        ) {
            locked = true
        }
    }
}
