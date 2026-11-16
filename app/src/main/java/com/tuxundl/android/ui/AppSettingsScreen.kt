package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.model.Behavior
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.service.store.ServiceStore
import com.tuxundl.android.design.R as DesignR

/**
 * Compose version of the old AppSettingsDesign preference list.
 *
 * Dark mode writes the same UiStore.darkMode field as before (and as the
 * appearance screen), so the two entry points can never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    uiStore: UiStore,
    srvStore: ServiceStore,
    behavior: Behavior,
    running: Boolean,
    onRecreateAll: () -> Unit,
    onHideIconChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var autoRestart by remember { mutableStateOf(behavior.autoRestart) }
    var darkMode by remember { mutableStateOf(uiStore.darkMode) }
    var hideAppIcon by remember { mutableStateOf(uiStore.hideAppIcon) }
    var hideFromRecents by remember { mutableStateOf(uiStore.hideFromRecents) }
    var dynamicNotification by remember { mutableStateOf(srvStore.dynamicNotification) }

    Scaffold(
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.app),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            CategoryHeader(text = stringResource(DesignR.string.behavior))

            SwitchRow(
                title = stringResource(DesignR.string.auto_restart),
                summary = stringResource(DesignR.string.allow_clash_auto_restart),
                checked = autoRestart,
                onCheckedChange = {
                    autoRestart = it
                    behavior.autoRestart = it
                },
            )

            CategoryHeader(text = stringResource(DesignR.string.interface_))

            SelectRow(
                title = stringResource(DesignR.string.dark_mode),
                options = listOf(
                    stringResource(DesignR.string.follow_system_android_10),
                    stringResource(DesignR.string.always_light),
                    stringResource(DesignR.string.always_dark),
                ),
                selectedIndex = DarkMode.values().indexOf(darkMode),
                onSelect = { index ->
                    val mode = DarkMode.values()[index]
                    darkMode = mode
                    uiStore.darkMode = mode
                    onRecreateAll()
                },
            )

            SwitchRow(
                title = stringResource(DesignR.string.hide_app_icon_title),
                summary = stringResource(DesignR.string.hide_app_icon_desc),
                checked = hideAppIcon,
                onCheckedChange = {
                    hideAppIcon = it
                    uiStore.hideAppIcon = it
                    onHideIconChange(it)
                },
            )

            SwitchRow(
                title = stringResource(DesignR.string.hide_from_recents_title),
                summary = stringResource(DesignR.string.hide_from_recents_desc),
                checked = hideFromRecents,
                onCheckedChange = {
                    hideFromRecents = it
                    uiStore.hideFromRecents = it
                    onRecreateAll()
                },
            )

            CategoryHeader(text = stringResource(DesignR.string.service))

            SwitchRow(
                title = stringResource(DesignR.string.show_traffic),
                summary = stringResource(DesignR.string.show_traffic_summary),
                checked = dynamicNotification,
                enabled = !running,
                onCheckedChange = {
                    dynamicNotification = it
                    srvStore.dynamicNotification = it
                },
            )
        }
    }
}
