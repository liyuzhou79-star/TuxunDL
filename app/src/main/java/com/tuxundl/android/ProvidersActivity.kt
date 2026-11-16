package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.design.ProvidersDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.ProvidersScreen
import com.tuxundl.android.ui.ProvidersUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit
import com.tuxundl.android.design.R

class ProvidersActivity : BaseActivity<ProvidersDesign>() {
    private val uiState = ProvidersUiState()
    private val uiRequests = Channel<ProvidersDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        val providers = withClash { queryProviders().sorted() }
        uiState.providers = providers

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newList = withClash { queryProviders().sorted() }

                            if (newList != providers) {
                                startActivity(ProvidersActivity::class.intent)

                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        is ProvidersDesign.Request.Update -> {
                            launch {
                                try {
                                    withClash {
                                        updateProvider(it.provider.type, it.provider.name)
                                    }

                                    uiState.updatedAt = uiState.updatedAt +
                                        (it.index to System.currentTimeMillis())
                                    uiState.updating = uiState.updating - it.index
                                } catch (e: Exception) {
                                    uiState.snackbarHostState.showSnackbar(
                                        message = getString(
                                            R.string.format_update_provider_failure,
                                            it.provider.name,
                                            e.message,
                                        ),
                                        duration = SnackbarDuration.Long,
                                    )

                                    uiState.updating = uiState.updating - it.index
                                }
                            }
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        uiState.now = System.currentTimeMillis()
                    }
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
                    ProvidersScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
