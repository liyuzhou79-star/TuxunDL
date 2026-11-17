@file:Suppress("BlockingMethodInNonBlockingContext")

package com.tuxundl.android

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.grantPermissions
import com.tuxundl.android.common.util.ticker
import com.tuxundl.android.common.util.uuid
import com.tuxundl.android.design.FilesDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.File
import com.tuxundl.android.remote.FilesClient
import com.tuxundl.android.service.model.Profile
import com.tuxundl.android.ui.FilesScreen
import com.tuxundl.android.ui.FilesUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.fileName
import com.tuxundl.android.util.withProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.Stack
import java.util.concurrent.TimeUnit

class FilesActivity : BaseActivity<FilesDesign>() {
    private val uiState = FilesUiState()
    private val uiRequests = Channel<FilesDesign.Request>(Channel.UNLIMITED)

    private lateinit var client: FilesClient
    private lateinit var root: String
    private val stack = Stack<String>()

    private var pendingImportUri: Uri? = null

    override suspend fun main() {
        val uuid = intent.uuid ?: return finish()
        val profile = withProfile { queryByUUID(uuid) } ?: return finish()
        root = uuid.toString()

        client = FilesClient(this)

        uiState.configurationEditable = profile.type != Profile.Type.Url
        fetch()

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ActivityStop -> {
                            fetch()
                        }
                        else -> Unit
                    }
                }
                uiRequests.onReceive {
                    try {
                        when (it) {
                            FilesDesign.Request.PopStack -> {
                                if (stack.empty()) {
                                    finish()
                                } else {
                                    stack.pop()
                                }
                            }
                            is FilesDesign.Request.OpenDirectory -> {
                                stack.push(it.file.id)
                            }
                            is FilesDesign.Request.OpenFile -> {
                                startActivityForResult(
                                    ActivityResultContracts.StartActivityForResult(),
                                    Intent(Intent.ACTION_VIEW).setDataAndType(
                                        client.buildDocumentUri(it.file.id),
                                        "text/plain"
                                    ).grantPermissions()
                                )
                            }
                            is FilesDesign.Request.DeleteFile -> {
                                client.deleteDocument(it.file.id)
                            }
                            is FilesDesign.Request.RenameFile -> {
                                // Handled by the Compose rename dialog instead:
                                // it calls renameFile() after the user confirms.
                                uiState.renameFile = it.file
                            }
                            is FilesDesign.Request.ImportFile -> {
                                val uri: Uri? = startActivityForResult(
                                    ActivityResultContracts.GetContent(),
                                    "*/*"
                                )

                                if (uri != null) {
                                    if (it.file == null) {
                                        pendingImportUri = uri
                                        uiState.importNameDefault = uri.fileName ?: "File"
                                    } else {
                                        client.copyDocument(it.file!!.id, uri)
                                    }
                                }
                            }
                            is FilesDesign.Request.ExportFile -> {
                                val uri: Uri? = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/plain"),
                                    it.file.name
                                )

                                if (uri != null) {
                                    client.copyDocument(uri, it.file.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        showError(e)
                    }

                    fetch()
                }
                if (activityStarted) {
                    ticker.onReceive {
                        uiState.now = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        if (!::client.isInitialized || !::root.isInitialized) return

        val documentId = stack.lastOrNull() ?: root
        val files = if (stack.empty()) {
            val list = client.list(documentId)
            val config = list.firstOrNull { it.id.endsWith("config.yaml") }

            if (config == null || config.size > 0) list else listOf(config)
        } else {
            client.list(documentId)
        }

        uiState.files = files
        uiState.currentInBaseDir = stack.empty()
    }

    private fun renameFile(file: File, newName: String) {
        launch {
            try {
                client.renameDocument(file.id, newName)
            } catch (e: Exception) {
                showError(e)
            }

            fetch()
        }
    }

    private fun importWithName(name: String) {
        val uri = pendingImportUri ?: return
        pendingImportUri = null

        launch {
            try {
                client.importDocument(stack.last(), uri, name)
            } catch (e: Exception) {
                showError(e)
            }

            fetch()
        }
    }

    private suspend fun showError(e: Exception) {
        uiState.snackbarHostState.showSnackbar(
            message = e.message ?: "Unknown",
            duration = SnackbarDuration.Long,
        )
    }

    override fun onBackPressed() {
        uiRequests.trySend(FilesDesign.Request.PopStack)
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
                    FilesScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onRenameConfirmed = { file, name -> renameFile(file, name) },
                        onImportNameConfirmed = { name -> importWithName(name) },
                        onBack = { onBackPressed() },
                    )
                }
            }
        }
    }
}
