package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.design.OverrideSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.OverrideSettingsScreen
import com.tuxundl.android.ui.OverrideUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class OverrideSettingsActivity : BaseActivity<OverrideSettingsDesign>() {
    private val uiState = OverrideUiState()
    private val uiRequests = Channel<OverrideSettingsDesign.Request>(Channel.UNLIMITED)
    private var configuration by mutableStateOf<ConfigurationOverride?>(null)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }
        this.configuration = configuration

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        OverrideSettingsDesign.Request.ResetOverride -> {
                            uiState.showResetConfirm = true
                        }
                    }
                }
            }
        }
    }

    private fun resetOverride() {
        defer {
            withClash {
                clearOverride(Clash.OverrideSlot.Persist)
            }
        }

        finish()
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val config = configuration
                    if (config != null) {
                        OverrideSettingsScreen(
                            configuration = config,
                            state = uiState,
                            onRequest = { request -> uiRequests.trySend(request) },
                            onResetConfirmed = { resetOverride() },
                            onBack = { finish() },
                        )
                    }
                }
            }
        }
    }
}
