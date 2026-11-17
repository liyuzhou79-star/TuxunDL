package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.design.AppSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.service.util.CustomRule
import com.tuxundl.android.service.util.customRulesFile
import com.tuxundl.android.service.util.loadCustomRules
import com.tuxundl.android.service.util.saveCustomRules
import com.tuxundl.android.service.util.sendProfileChanged
import com.tuxundl.android.ui.RulesScreen
import com.tuxundl.android.ui.RulesUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.withClash
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Hosts the custom routing rules editor.
 *
 * Rules live in a per-profile JSON file (see CustomRules.kt in the service
 * module): this activity reads and writes the file for the active profile,
 * and every save nudges the background process with ACTION_PROFILE_CHANGED
 * so the configuration module reloads the profile with the new rules
 * spliced in. Uses AppSettingsDesign as its (never instantiated) design
 * type, like AppearanceActivity.
 */
class RulesActivity : BaseActivity<AppSettingsDesign>() {
    private val uiState = RulesUiState()
    private val saveRequests = Channel<List<CustomRule>>(Channel.UNLIMITED)

    private var profileUuid: UUID? = null

    override suspend fun main() {
        loadActiveProfile()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    if (it == Event.ProfileChanged) {
                        // Follow profile switches while the editor is open
                        // (our own saves also arrive here; loadActiveProfile
                        // then just re-reads the file we just wrote).
                        val before = profileUuid
                        loadActiveProfile()
                        if (profileUuid != before) {
                            refreshGroupNames()
                        }
                    }
                }
                saveRequests.onReceive { rules ->
                    val uuid = profileUuid ?: return@onReceive

                    withContext(Dispatchers.IO) {
                        saveCustomRules(customRulesFile(uuid), rules)
                    }

                    uiState.rules = rules

                    // Trigger a profile reload in the background process;
                    // the injection happens there, right before Clash.load.
                    sendProfileChanged(uuid)

                    uiState.snackbarHostState.showSnackbar(
                        message = getString(R.string.rules_saved),
                    )
                }
            }
        }
    }

    private suspend fun loadActiveProfile() {
        val active = withProfile { queryActive() }

        profileUuid = active?.uuid
        uiState.hasProfile = active != null
        uiState.profileName = active?.name

        uiState.rules = if (active != null) {
            withContext(Dispatchers.IO) {
                loadCustomRules(customRulesFile(active.uuid))
            }
        } else {
            emptyList()
        }

        refreshGroupNames()
    }

    private fun refreshGroupNames() {
        launch {
            uiState.groupNames = try {
                withClash { queryProxyGroupNames(false) }
            } catch (e: Exception) {
                // Core not loaded (proxy stopped): presets only.
                emptyList()
            }
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
                    RulesScreen(
                        state = uiState,
                        onRulesChanged = { rules -> saveRequests.trySend(rules) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
