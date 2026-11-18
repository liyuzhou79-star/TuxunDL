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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxundl.android.R
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

                Button(
                    onClick = {
                        if (targetUrl != null) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)),
                            )
                        }
                        onClose()
                    },
                    enabled = targetUrl != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.update_now))
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
