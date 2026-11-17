package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.setFileName
import com.tuxundl.android.design.LogsDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.LogFile
import com.tuxundl.android.ui.LogsScreen
import com.tuxundl.android.ui.LogsUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.logsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class LogsActivity : BaseActivity<LogsDesign>() {
    private val uiState = LogsUiState()
    private val uiRequests = Channel<LogsDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            val files = withContext(Dispatchers.IO) {
                                loadFiles()
                            }

                            uiState.files = files
                        }
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        LogsDesign.Request.StartLogcat -> {
                            startActivity(LogcatActivity::class.intent)
                            finish()
                        }
                        LogsDesign.Request.DeleteAll -> {
                            uiState.showDeleteAllConfirm = true
                        }
                        is LogsDesign.Request.OpenFile -> {
                            startActivity(LogcatActivity::class.intent.setFileName(it.file.fileName))
                        }
                    }
                }
            }
        }
    }

    private fun loadFiles(): List<LogFile> {
        val list = cacheDir.resolve("logs").listFiles()?.toList() ?: emptyList()

        return list.mapNotNull { LogFile.parseFromFileName(it.name) }
    }

    private fun deleteAllLogs() {
        logsDir.deleteRecursively()
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
                    LogsScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onDeleteAllConfirmed = {
                            launch {
                                withContext(Dispatchers.IO) {
                                    deleteAllLogs()
                                }

                                events.trySend(Event.ActivityStart)
                            }
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
