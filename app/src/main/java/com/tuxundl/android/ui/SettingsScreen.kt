package com.tuxundl.android.ui

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.util.UpdateCheckResult
import com.tuxundl.android.util.UpdateChecker
import com.tuxundl.android.util.UpdateInfo
import com.tuxundl.android.util.WindowsInfo
import kotlinx.coroutines.launch
import com.tuxundl.android.design.R as DesignR

/** State for the settings home: the manual update-check flow lives here. */
class SettingsUiState {
    var checkingUpdate by mutableStateOf(false)

    /** Non-null while the update-check result dialog is shown. */
    var updateMessage by mutableStateOf<String?>(null)

    /** Download target for the dialog's Download button; null = no update. */
    var updateDownloadUrl by mutableStateOf<String?>(null)

    /** Non-null while the full-screen update page should be shown. */
    var updatePageInfo by mutableStateOf<UpdateInfo?>(null)

    /** Low-traffic subscription alert switch (mirrors UiStore). */
    var trafficAlertEnabled by mutableStateOf(false)

    /** Alert threshold in GiB of remaining traffic (mirrors UiStore). */
    var trafficAlertThresholdGb by mutableStateOf(10)

    /** In-app language tag; "" = follow system (mirrors UiStore). */
    var appLanguage by mutableStateOf("")

    /** True while the Windows-edition lookup is running (1.2.4). */
    var checkingWindows by mutableStateOf(false)

    /** Non-null while the Windows-edition dialog is shown (1.2.4). */
    var windowsInfo by mutableStateOf<WindowsInfo?>(null)

    /**
     * Windows release attached to the latest update-check result
     * (1.2.8): the result dialog offers a button that hands this over
     * to [windowsInfo], opening the usual Windows-edition dialog.
     */
    var updateWindows by mutableStateOf<WindowsInfo?>(null)

    /** Haptic feedback switch (mirrors UiStore, 1.2.5). */
    var hapticsEnabled by mutableStateOf(true)

    /** Haptic strength 0 light / 1 medium / 2 strong (1.2.5). */
    var hapticsStrength by mutableStateOf(1)
}

/**
 * Language choices for the picker: BCP-47 tag to the name shown in the
 * list. Names are written in their own language, as is customary;
 * "" (follow system) is handled separately with a localized label.
 * Only languages with shipped resources are listed.
 */
internal val LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "en" to "English",
    "zh" to "简体中文",
    "zh-TW" to "繁體中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "ru" to "Русский",
    "vi" to "Tiếng Việt",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRequest: (SettingsDesign.Request) -> Unit,
    onTrafficAlertChange: (Boolean) -> Unit,
    onTrafficAlertThresholdChange: (Int) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onHapticsStrengthChange: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showHapticsDialog by remember { mutableStateOf(false) }
    val screenContext = LocalContext.current
    val screenScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.settings),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .tuxunPageEnter()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionTitle(
                text = stringResource(R.string.settings_group_network),
                first = true,
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_dns,
                title = stringResource(DesignR.string.network),
                onClick = { onRequest(SettingsDesign.Request.StartNetwork) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_extension,
                title = stringResource(DesignR.string.override),
                onClick = { onRequest(SettingsDesign.Request.StartOverride) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_dns,
                title = stringResource(R.string.custom_dns),
                onClick = { onRequest(SettingsDesign.Request.StartCustomDns) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_view_list,
                title = stringResource(R.string.custom_rules),
                onClick = { onRequest(SettingsDesign.Request.StartRules) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_apps,
                title = stringResource(R.string.per_app_proxy),
                onClick = { onRequest(SettingsDesign.Request.StartAccessControl) },
            )

            SettingsSectionTitle(
                text = stringResource(R.string.settings_group_appearance),
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_brightness_4,
                title = stringResource(R.string.appearance),
                onClick = { onRequest(SettingsDesign.Request.StartAppearance) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_vpn_lock,
                title = stringResource(R.string.settings_language),
                trailing = {
                    Text(
                        text = languageDisplayName(state.appLanguage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { showLanguageDialog = true },
            )

            SettingsSectionTitle(
                text = stringResource(R.string.settings_group_alerts),
            )
            SettingsSwitchCard(
                icon = DesignR.drawable.ic_baseline_swap_vert,
                title = stringResource(R.string.traffic_alert),
                summary = stringResource(R.string.traffic_alert_summary),
                checked = state.trafficAlertEnabled,
                onCheckedChange = onTrafficAlertChange,
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_swap_vert,
                title = stringResource(R.string.traffic_alert_threshold),
                trailing = {
                    Text(
                        text = stringResource(
                            R.string.traffic_alert_threshold_value,
                            state.trafficAlertThresholdGb,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { showThresholdDialog = true },
            )
            SettingsSwitchCard(
                icon = DesignR.drawable.ic_baseline_swap_vert,
                title = stringResource(R.string.haptics),
                summary = stringResource(R.string.haptics_summary),
                checked = state.hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_swap_vert,
                title = stringResource(R.string.haptics_strength),
                trailing = {
                    Text(
                        text = stringResource(
                            when (state.hapticsStrength) {
                                0 -> R.string.haptics_light
                                2 -> R.string.haptics_strong
                                else -> R.string.haptics_medium
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { showHapticsDialog = true },
            )

            SettingsSectionTitle(
                text = stringResource(R.string.settings_group_data),
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_restore,
                title = stringResource(R.string.backup_restore),
                onClick = { onRequest(SettingsDesign.Request.StartBackup) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_settings,
                title = stringResource(DesignR.string.app),
                onClick = { onRequest(SettingsDesign.Request.StartApp) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_meta,
                title = stringResource(DesignR.string.meta_features),
                onClick = { onRequest(SettingsDesign.Request.StartMetaFeature) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_update,
                title = stringResource(R.string.check_update),
                trailing = {
                    if (state.checkingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                onClick = { onRequest(SettingsDesign.Request.CheckUpdate) },
            )

            SettingsSectionTitle(
                text = stringResource(R.string.windows_section_title),
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_update,
                title = stringResource(R.string.windows_section_title),
                trailing = {
                    if (state.checkingWindows) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                onClick = {
                    // Look up the newest Windows release (1.2.4). The
                    // checker already collects it alongside the
                    // Android result; here we only surface it.
                    if (!state.checkingWindows) {
                        state.checkingWindows = true
                        screenScope.launch {
                            val current = runCatching {
                                screenContext.packageManager.getPackageInfo(
                                    screenContext.packageName, 0,
                                ).versionName ?: ""
                            }.getOrDefault("")
                            val result = UpdateChecker.check(screenContext, current)
                            state.checkingWindows = false
                            val windows = when (result) {
                                is UpdateCheckResult.UpdateAvailable ->
                                    result.windows
                                is UpdateCheckResult.UpToDate ->
                                    result.windows
                                is UpdateCheckResult.Failed -> null
                            }
                            if (windows != null) {
                                state.windowsInfo = windows
                            } else {
                                state.updateMessage = screenContext.getString(
                                    R.string.windows_check_failed,
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    val windowsInfo = state.windowsInfo
    if (windowsInfo != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { state.windowsInfo = null },
            title = { Text(text = stringResource(R.string.windows_section_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.windows_latest,
                        windowsInfo.version,
                    ) + (if (windowsInfo.publishedAt.isNotBlank()) {
                        "\n" + stringResource(
                            R.string.windows_release_date,
                            windowsInfo.publishedAt.take(10),
                        )
                    } else {
                        ""
                    }) + "\n" + stringResource(R.string.windows_entry_summary),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.windowsInfo = null
                        windowsInfo.pageUrl?.let { url ->
                            screenContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.windows_open_page))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        windowsInfo.pageUrl?.let { url ->
                            clipboard.setText(AnnotatedString(url))
                        }
                        state.windowsInfo = null
                        state.updateMessage = screenContext.getString(
                            R.string.windows_copied,
                        )
                    },
                ) {
                    Text(text = stringResource(R.string.windows_copy_link))
                }
            },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = state.appLanguage,
            onSelect = { tag ->
                showLanguageDialog = false
                onLanguageChange(tag)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showThresholdDialog) {
        ThresholdDialog(
            current = state.trafficAlertThresholdGb,
            onConfirm = { gb ->
                showThresholdDialog = false
                onTrafficAlertThresholdChange(gb)
            },
            onDismiss = { showThresholdDialog = false },
        )
    }

    if (showHapticsDialog) {
        HapticsStrengthDialog(
            current = state.hapticsStrength,
            onSelect = { strength ->
                showHapticsDialog = false
                onHapticsStrengthChange(strength)
            },
            onDismiss = { showHapticsDialog = false },
        )
    }

    // A manual check that finds a release opens the same full-screen
    // update page as the startup check.
    val updatePageInfo = state.updatePageInfo
    if (updatePageInfo != null) {
        UpdateFullScreen(
            info = updatePageInfo,
            onClose = { state.updatePageInfo = null },
        )
    }

    val message = state.updateMessage
    if (message != null) {
        val context = LocalContext.current
        val downloadUrl = state.updateDownloadUrl

        AlertDialog(
            onDismissRequest = {
                state.updateMessage = null
                state.updateDownloadUrl = null
                state.updateWindows = null
            },
            title = { Text(text = stringResource(R.string.check_update)) },
            text = { Text(text = message) },
            confirmButton = {
                if (downloadUrl != null) {
                    TextButton(
                        onClick = {
                            state.updateMessage = null
                            state.updateDownloadUrl = null
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)),
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.update_download))
                    }
                } else {
                    TextButton(
                        onClick = {
                            state.updateMessage = null
                            state.updateWindows = null
                        },
                    ) {
                        Text(text = stringResource(DesignR.string.ok))
                    }
                }
            },
            dismissButton = {
                if (downloadUrl != null) {
                    TextButton(
                        onClick = {
                            state.updateMessage = null
                            state.updateDownloadUrl = null
                        },
                    ) {
                        Text(text = stringResource(DesignR.string.cancel))
                    }
                } else if (state.updateWindows != null) {
                    // The result mentioned a Windows release (1.2.8):
                    // the hint line is now a door — hand the info to
                    // the Windows-edition dialog with its open/copy
                    // actions instead of leaving it as dead text.
                    TextButton(
                        onClick = {
                            val windows = state.updateWindows
                            state.updateMessage = null
                            state.updateWindows = null
                            state.windowsInfo = windows
                        },
                    ) {
                        Text(text = stringResource(R.string.update_windows_view))
                    }
                }
            },
        )
    }
}

/** Single-choice haptic strength picker (light / medium / strong). */
@Composable
private fun HapticsStrengthDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        0 to R.string.haptics_light,
        1 to R.string.haptics_medium,
        2 to R.string.haptics_strong,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.haptics_strength)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    LanguageOptionRow(
                        label = stringResource(label),
                        selected = current == value,
                        onClick = { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(DesignR.string.cancel))
            }
        },
    )
}

/** The current language's list name; "" reads as "follow system". */
@Composable
private fun languageDisplayName(tag: String): String {
    if (tag.isEmpty()) {
        return stringResource(R.string.language_follow_system)
    }

    return LANGUAGE_OPTIONS.firstOrNull { it.first == tag }?.second ?: tag
}

/** Single-choice language picker: follow system + every shipped language. */
@Composable
private fun LanguageDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LanguageOptionRow(
                    label = stringResource(R.string.language_follow_system),
                    selected = current.isEmpty(),
                    onClick = { onSelect("") },
                )
                LANGUAGE_OPTIONS.forEach { (tag, name) ->
                    LanguageOptionRow(
                        label = name,
                        selected = current == tag,
                        onClick = { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(DesignR.string.cancel))
            }
        },
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Numeric threshold picker (1–999 GiB of remaining traffic). */
@Composable
private fun ThresholdDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.traffic_alert_threshold_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() }.take(3)
                },
                singleLine = true,
                suffix = { Text(text = "GB") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = text.toIntOrNull()
                    if (value != null) {
                        onConfirm(value.coerceIn(1, 999))
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(text = stringResource(DesignR.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(DesignR.string.cancel))
            }
        },
    )
}

/** Group heading between settings sections. */
@Composable
private fun SettingsSectionTitle(text: String, first: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = 4.dp,
            top = if (first) 0.dp else 12.dp,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsEntryCard(
    @DrawableRes icon: Int,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    TuxunCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/** A settings card with a title, a small note and the app-wide switch. */
@Composable
private fun SettingsSwitchCard(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    TuxunCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TuxunSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
