package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.design.AppSettingsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.service.util.customDnsFile
import com.tuxundl.android.service.util.loadCustomDns
import com.tuxundl.android.service.util.saveCustomDns
import com.tuxundl.android.service.util.sendProfileChanged
import com.tuxundl.android.ui.DnsSettingsScreen
import com.tuxundl.android.ui.DnsSettingsUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/**
 * Hosts the custom DNS settings.
 *
 * The settings live in one JSON file (see CustomDns.kt in the service
 * module): this activity reads and writes it, and every save nudges the
 * background process with ACTION_PROFILE_CHANGED so the configuration
 * module reloads the active profile with the DNS override spliced in.
 * Uses AppSettingsDesign as its (never instantiated) design type, like
 * RulesActivity.
 */
class DnsSettingsActivity : BaseActivity<AppSettingsDesign>() {
    private val uiState = DnsSettingsUiState()
    private val saveRequests = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun main() {
        val initial = withContext(Dispatchers.IO) {
            loadCustomDns(customDnsFile())
        }
        uiState.load(initial)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                saveRequests.onReceive {
                    val settings = uiState.toSettings()

                    withContext(Dispatchers.IO) {
                        saveCustomDns(customDnsFile(), settings)
                    }

                    // Reload the active profile (if any) so the override
                    // is spliced in by the background process.
                    val active = withProfile { queryActive() }
                    if (active != null) {
                        sendProfileChanged(active.uuid)
                    }

                    uiState.snackbarHostState.showSnackbar(
                        message = getString(R.string.dns_saved),
                    )
                }
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DnsSettingsScreen(
                        state = uiState,
                        onSave = { saveRequests.trySend(Unit) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
