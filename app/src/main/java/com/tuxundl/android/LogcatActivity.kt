package com.tuxundl.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.common.compat.startForegroundServiceCompat
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.common.util.fileName
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.core.model.LogMessage
import com.tuxundl.android.design.LogcatDesign
import com.tuxundl.android.design.dialog.withModelProgressBar
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.LogFile
import com.tuxundl.android.log.LogcatFilter
import com.tuxundl.android.log.LogcatReader
import com.tuxundl.android.ui.LogcatScreen
import com.tuxundl.android.ui.LogcatUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.logsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tuxundl.android.design.R

class LogcatActivity : BaseActivity<LogcatDesign>() {
    private var conn: ServiceConnection? = null

    private val uiState = LogcatUiState()
    private val uiRequests = Channel<LogcatDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        val fileName = intent?.fileName

        if (fileName != null) {
            val file = LogFile.parseFromFileName(fileName) ?: return showInvalid()

            return mainLocalFile(file)
        }

        return mainStreaming()
    }

    private suspend fun mainLocalFile(file: LogFile) {
        val messages = try {
            LogcatReader(this, file).readAll()
        } catch (e: Exception) {
            Log.e("Fail to read log file ${file.fileName}: ${e.message}")
            return showInvalid()
        }

        uiState.streaming = false
        uiState.messages = messages

        while (isActive) {
            when (uiRequests.receive()) {
                LogcatDesign.Request.Delete -> {
                    withContext(Dispatchers.IO) {
                        logsDir.resolve(file.fileName).delete()
                    }

                    finish()
                }
                LogcatDesign.Request.Export -> {
                    val output = startActivityForResult(
                        ActivityResultContracts.CreateDocument("text/plain"),
                        file.fileName
                    )

                    if (output != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                writeLogTo(messages, file, output)
                            }

                            uiState.snackbarHostState.showSnackbar(
                                message = getString(R.string.file_exported),
                                duration = SnackbarDuration.Long,
                            )
                        } catch (e: Exception) {
                            uiState.snackbarHostState.showSnackbar(
                                message = e.message ?: "Unknown",
                                duration = SnackbarDuration.Long,
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun mainStreaming() {
        uiState.streaming = true

        startForegroundServiceCompat(LogcatService::class.intent)

        val logcat = bindLogcatService()
        val ticker = ticker(500)

        var initial = true

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        LogcatDesign.Request.Close -> {
                            stopService(LogcatService::class.intent)
                            startActivity(LogsActivity::class.intent)
                            finish()
                        }
                        else -> Unit
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        val snapshot = logcat.snapshot(initial) ?: return@onReceive

                        uiState.messages = snapshot.messages

                        initial = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        conn?.apply(this::unbindService)

        super.onDestroy()
    }

    private suspend fun bindLogcatService(): LogcatService {
        return suspendCoroutine { ctx ->
            bindService(LogcatService::class.intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val srv = service!!.queryLocalInterface("") as LogcatService

                    ctx.resume(srv)

                    conn = this
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    conn = null
                }
            }, Context.BIND_AUTO_CREATE)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun writeLogTo(messages: List<LogMessage>, file: LogFile, uri: Uri) {
        LogcatFilter(OutputStreamWriter(contentResolver.openOutputStream(uri)), this).use {
            withContext(Dispatchers.Main) {
                withModelProgressBar {
                    configure {
                        isIndeterminate = true
                        max = messages.size
                    }

                    withContext(Dispatchers.IO) {
                        it.writeHeader(file.date)

                        messages.forEachIndexed { idx, msg ->
                            configure {
                                isIndeterminate = false
                                progress = idx
                            }

                            it.writeMessage(msg)
                        }
                    }
                }
            }
        }
    }

    private fun showInvalid() {
        Toast.makeText(this, R.string.invalid_log_file, Toast.LENGTH_LONG).show()
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
                    LogcatScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
