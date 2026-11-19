package com.tuxundl.android

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.ui.AppLockScreen
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.AppLock
import com.tuxundl.android.util.AppLockManager
import java.util.Locale

/**
 * The app-lock gate (1.5.0). A deliberately thin activity: it is
 * launched over whatever the user was doing when the lock engages,
 * shows only the unlock screen (FLAG_SECURE, excluded from recents)
 * and finishes the moment the right secret is entered. Back does
 * not dismiss it — it sends the whole task to the background, and
 * coming back reopens this gate because the lock is still engaged.
 */
// A FragmentActivity (not a plain ComponentActivity) since 1.6.1:
// the biometric prompt on the lock screen is hosted by it.
class AppLockActivity : FragmentActivity() {

    // Same in-app language wrap as BaseActivity, so the gate speaks
    // the language the user picked for the app.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyAppLanguage(newBase))
    }

    private fun applyAppLanguage(base: Context): Context {
        val tag = UiStore(base).appLanguage
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val uiStore = UiStore(this)

        // Stale launch (lock turned off, or already unlocked): leave.
        if (!AppLock.isConfigured(uiStore) || !AppLockManager.locked) {
            finish()
            return
        }

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(
                darkTheme = darkTheme,
                palette = uiStore.themePalette,
                customSeed = customSeedColor(uiStore),
                animStyle = uiStore.animationStyle,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppLockScreen(
                        type = uiStore.appLockType,
                        onUnlocked = { finish() },
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // No way around the gate: park the task instead.
        moveTaskToBack(true)
    }
}
