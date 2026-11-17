package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.design.AppSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ThemePalette
import com.tuxundl.android.ui.AppearanceScreen
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.ApplicationObserver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

/**
 * Hosts the Compose appearance screen (palette, dark mode, home cards).
 *
 * Uses AppSettingsDesign as its (never instantiated) design type, like the
 * other Compose activities reuse their Design class for the base type.
 */
class AppearanceActivity : BaseActivity<AppSettingsDesign>() {
    override suspend fun main() {
        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
            }
        }
    }

    private fun recreateAll() {
        ApplicationObserver.createdActivities.forEach {
            it.recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppearanceScreen(
                        uiStore = uiStore,
                        darkTheme = darkTheme,
                        onPaletteChange = { palette: ThemePalette ->
                            uiStore.themePalette = palette
                            recreateAll()
                        },
                        onDarkModeChange = { mode: DarkMode ->
                            uiStore.darkMode = mode
                            recreateAll()
                        },
                        onCustomColorChange = { argb: Int ->
                            uiStore.customThemeColor = argb
                            recreateAll()
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
