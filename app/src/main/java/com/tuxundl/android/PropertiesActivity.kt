package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.setUUID
import com.tuxundl.android.common.util.uuid
import com.tuxundl.android.core.model.FetchStatus
import com.tuxundl.android.design.PropertiesDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.ui.PropertiesScreen
import com.tuxundl.android.ui.PropertiesUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import com.tuxundl.android.design.R

class PropertiesActivity : BaseActivity<PropertiesDesign>() {
    private var canceled: Boolean = false
    private lateinit var original: Profile
    private lateinit var current: Profile

    private val uiState = PropertiesUiState()
    private val uiRequests = Channel<PropertiesDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        setResult(RESULT_CANCELED)

        val uuid = intent.uuid ?: return finish()

        original = withProfile { queryByUUID(uuid) } ?: return finish()
        current = original
        uiState.profile = original

        defer {
            canceled = true

            withProfile { release(uuid) }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStop -> {
                            if (!canceled && current != original) {
                                withProfile {
                                    patch(current.uuid, current.name, current.source, current.interval, current.ageSecretKey)
                                }
                            }
                        }
                        Event.ServiceRecreated -> {
                            finish()
                        }
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        PropertiesDesign.Request.BrowseFiles -> {
                            startActivity(FilesActivity::class.intent.setUUID(uuid))
                        }
                        PropertiesDesign.Request.Commit -> {
                            verifyAndCommit()
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (uiState.progressing) {
            return
        }

        if (!::original.isInitialized || !::current.isInitialized || original == current) {
            finish()
        } else {
            uiState.showExitConfirm = true
        }
    }

    private suspend fun verifyAndCommit() {
        val profile = current

        when {
            profile.name.isBlank() -> {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.empty_name),
                    duration = SnackbarDuration.Long,
                )
            }
            profile.type != Profile.Type.File && profile.source.isBlank() -> {
                uiState.snackbarHostState.showSnackbar(
                    message = getString(R.string.invalid_url),
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {
                try {
                    uiState.progressing = true
                    uiState.progressText = getString(R.string.initializing)
                    uiState.progressIndeterminate = true

                    try {
                        withProfile {
                            patch(profile.uuid, profile.name, profile.source, profile.interval, profile.ageSecretKey)

                            coroutineScope {
                                commit(profile.uuid) {
                                    launch {
                                        applyStatus(it)
                                    }
                                }
                            }
                        }
                    } finally {
                        uiState.progressing = false
                    }

                    setResult(RESULT_OK)

                    finish()
                } catch (e: Exception) {
                    uiState.snackbarHostState.showSnackbar(
                        message = e.message ?: e.toString(),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    private fun applyStatus(status: FetchStatus) {
        when (status.action) {
            FetchStatus.Action.FetchConfiguration -> {
                uiState.progressText = getString(R.string.format_fetching_configuration, status.args[0])
                uiState.progressIndeterminate = true
            }
            FetchStatus.Action.FetchProviders -> {
                uiState.progressText = getString(R.string.format_fetching_provider, status.args[0])
                uiState.progressIndeterminate = false
                uiState.progressMax = status.max
                uiState.progressValue = status.progress
            }
            FetchStatus.Action.SubscriptionInfo -> Unit
            FetchStatus.Action.Verifying -> {
                uiState.progressText = getString(R.string.verifying)
                uiState.progressIndeterminate = false
                uiState.progressMax = status.max
                uiState.progressValue = status.progress
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
                    PropertiesScreen(
                        state = uiState,
                        onProfileChange = { profile ->
                            current = profile
                            uiState.profile = profile
                        },
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { onBackPressed() },
                        onExitConfirmed = { finish() },
                    )
                }
            }
        }
    }
}
