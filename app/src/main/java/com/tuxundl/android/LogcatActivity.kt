package com.tuxundl.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.tuxundl.android.common.compat.startForegroundServiceCompat
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.common.util.fileName
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.core.model.LogMessage
import com.tuxundl.android.design.LogcatDesign
import com.tuxundl.android.design.dialog.withModelProgressBar
import com.tuxundl.android.design.model.LogFile
import com.tuxundl.android.design.ui.ToastDuration
import com.tuxundl.android.design.util.showExceptionToast
import com.tuxundl.android.log.LogcatFilter
import com.tuxundl.android.log.LogcatReader
import com.tuxundl.android.util.logsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.tuxundl.android.design.R

class LogcatActivity : BaseActivity<LogcatDesign>() {
    private var conn: ServiceConnection? = null

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

        val design = LogcatDesign(this, false)

        setContentDesign(design)

        design.patchMessages(messages, 0, messages.size)

        while (isActive) {
            when (design.requests.receive()) {
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

                            design.showToast(R.string.file_exported, ToastDuration.Long)
                        } catch (e: Exception) {
                            design.showExceptionToast(e)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun mainStreaming() {
        val design = LogcatDesign(this, true)

        setContentDesign(design)

        startForegroundServiceCompat(LogcatService::class.intent)

        val logcat = bindLogcatService()
        val ticker = ticker(500)

        var initial = true

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
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

                        design.patchMessages(snapshot.messages, snapshot.removed, snapshot.appended)

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
}