package com.tuxundl.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.design.HelpDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.HelpScreen
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import kotlinx.coroutines.isActive

class HelpActivity : BaseActivity<HelpDesign>() {
    override suspend fun main() {
        while (isActive) {
            events.receive()
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
                    HelpScreen(
                        openLink = {
                            startActivity(Intent(Intent.ACTION_VIEW).setData(it))
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
