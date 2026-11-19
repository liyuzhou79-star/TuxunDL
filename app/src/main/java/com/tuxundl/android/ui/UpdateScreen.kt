package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
import com.tuxundl.android.util.SelfUpdate
import com.tuxundl.android.util.UpdateInfo
import com.tuxundl.android.design.R as DesignR

/**
 * Full-screen "new version" page, shown once per launch when the update
 * check finds a newer release (and from the manual check in Settings).
 *
 * Closing it — the X, "later", or the system back button — only closes
 * the page: the home update card stays as the reminder, and nothing is
 * written to the dismissed-version preference (that stays the home
 * card's own close button, exactly as before).
 *
 * In-app install (1.6.1): when the release carries an APK asset for
 * this device, the primary button downloads it right here through
 * [SelfUpdate] — progress, size + signature verification, then the
 * system installer — instead of handing the link to the browser.
 * The browser route remains as the fallback: when the release has
 * no matching asset (e.g. the check came from the Atom feed), and
 * whenever an in-app attempt fails.
 */
@Composable
fun UpdateFullScreen(
    info: UpdateInfo,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val targetUrl = info.downloadUrl ?: info.pageUrl

    // The installed version, so the page can show "current -> latest"
    // instead of only naming the new one (1.6.0).
    val currentVersion = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    // The shared self-update state, narrowed to this page's version:
    // a state left over from another version renders as Idle here.
    val sharedState by SelfUpdate.state.collectAsState()
    val selfState = if (sharedState.version == info.version) {
        sharedState
    } else {
        SelfUpdate.State.Idle
    }

    var permissionHintVisible by remember(info.version) {
        mutableStateOf(false)
    }
    var autoLaunched by remember(info.version) { mutableStateOf(false) }

    // A finished, verified download goes straight to the installer
    // once; after that the button offers the install again (the user
    // may have backed out of the system dialog).
    LaunchedEffect(selfState) {
        if (selfState is SelfUpdate.State.Ready && !autoLaunched) {
            autoLaunched = true
            SelfUpdate.install(context, selfState.file)
        }
    }

    fun openInBrowser() {
        if (targetUrl != null) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)),
            )
        }
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .tuxunPageEnter()
                    // Keep the page clear of the system bars (1.5.3):
                    // the app draws edge-to-edge, and the bottom
                    // buttons ("update now" / "later") used to sit
                    // right on top of the gesture bar, which ate
                    // their taps — the page looked impossible to exit.
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_close),
                            contentDescription = null,
                        )
                    }
                }

                Icon(
                    painter = painterResource(DesignR.drawable.ic_baseline_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.update_page_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "v${info.version}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (currentVersion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.update_current_version,
                            currentVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.publishedAt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.update_published_date,
                            info.publishedAt.take(10),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (info.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.update_notes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TuxunCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            Text(
                                text = info.notes.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // One-time permission guidance (1.6.1): shown after
                // the user taps Update while the "install unknown
                // apps" grant is missing.
                if (permissionHintVisible &&
                    (selfState is SelfUpdate.State.Idle ||
                        selfState is SelfUpdate.State.Failed)
                ) {
                    Text(
                        text = stringResource(
                            R.string.update_install_permission_hint,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    SelfUpdate.unknownSourcesSettingsIntent(
                                        context,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.update_install_permission_open,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (selfState is SelfUpdate.State.Failed) {
                    Text(
                        text = stringResource(
                            when (selfState.reason) {
                                SelfUpdate.FailReason.NETWORK ->
                                    R.string.update_download_failed_network

                                SelfUpdate.FailReason.SIZE_MISMATCH ->
                                    R.string.update_download_failed_size

                                SelfUpdate.FailReason.SIGNATURE_MISMATCH ->
                                    R.string.update_download_failed_signature

                                SelfUpdate.FailReason.OTHER ->
                                    R.string.update_download_failed_other
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (selfState is SelfUpdate.State.Downloading) {
                    val total = selfState.totalBytes
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (selfState.downloadedBytes.toFloat() / total)
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (selfState is SelfUpdate.State.Verifying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Button(
                    onClick = {
                        when (val s = selfState) {
                            is SelfUpdate.State.Ready ->
                                SelfUpdate.install(context, s.file)

                            is SelfUpdate.State.Downloading,
                            is SelfUpdate.State.Verifying -> Unit

                            else -> when {
                                info.downloadUrl == null -> openInBrowser()

                                !SelfUpdate.canInstall(context) -> {
                                    permissionHintVisible = true
                                    runCatching {
                                        context.startActivity(
                                            SelfUpdate
                                                .unknownSourcesSettingsIntent(
                                                    context,
                                                ),
                                        )
                                    }
                                }

                                else -> SelfUpdate.start(context, info)
                            }
                        }
                    },
                    enabled = when (selfState) {
                        is SelfUpdate.State.Downloading,
                        is SelfUpdate.State.Verifying -> false

                        else -> targetUrl != null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when (val s = selfState) {
                            is SelfUpdate.State.Downloading ->
                                if (s.totalBytes > 0) {
                                    stringResource(
                                        R.string.update_downloading_percent,
                                        (s.downloadedBytes * 100 / s.totalBytes)
                                            .toInt(),
                                    )
                                } else {
                                    stringResource(
                                        R.string.update_downloading_plain,
                                    )
                                }

                            is SelfUpdate.State.Verifying ->
                                stringResource(R.string.update_download_verifying)

                            is SelfUpdate.State.Ready ->
                                stringResource(R.string.update_install_now)

                            is SelfUpdate.State.Failed ->
                                stringResource(R.string.update_download_retry)

                            else -> stringResource(R.string.update_now)
                        },
                    )
                }

                if (selfState is SelfUpdate.State.Downloading) {
                    TextButton(
                        onClick = { SelfUpdate.cancel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                }

                if (selfState is SelfUpdate.State.Failed &&
                    targetUrl != null
                ) {
                    TextButton(
                        onClick = { openInBrowser() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_use_browser),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.update_later))
                }
            }
        }
    }
}
