package com.tuxundl.android.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.NetworkSettingsDesign
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.service.model.AccessControlMode
import com.tuxundl.android.service.store.ServiceStore
import com.tuxundl.android.design.R as DesignR

/** Compose version of the old NetworkSettingsDesign preference list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    uiStore: UiStore,
    srvStore: ServiceStore,
    running: Boolean,
    snackbarHostState: SnackbarHostState,
    onRequest: (NetworkSettingsDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    var enableVpn by remember { mutableStateOf(uiStore.enableVpn) }
    var bypassPrivateNetwork by remember { mutableStateOf(srvStore.bypassPrivateNetwork) }
    var dnsHijacking by remember { mutableStateOf(srvStore.dnsHijacking) }
    var allowBypass by remember { mutableStateOf(srvStore.allowBypass) }
    var allowIpv6 by remember { mutableStateOf(srvStore.allowIpv6) }
    var systemProxy by remember { mutableStateOf(srvStore.systemProxy) }
    var tunStackMode by remember { mutableStateOf(srvStore.tunStackMode) }
    var accessControlMode by remember { mutableStateOf(srvStore.accessControlMode) }

    val optionsUnavailable = stringResource(DesignR.string.options_unavailable)
    LaunchedEffect(running) {
        if (running) {
            snackbarHostState.showSnackbar(
                message = optionsUnavailable,
                duration = SnackbarDuration.Indefinite,
            )
        }
    }

    // VPN-dependent options are disabled while the VPN switch is off or
    // while the service is running, exactly like the old design.
    val depsEnabled = enableVpn && !running

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.network),
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
            SwitchRow(
                title = stringResource(DesignR.string.route_system_traffic),
                summary = stringResource(DesignR.string.routing_via_vpn_service),
                checked = enableVpn,
                enabled = !running,
                onCheckedChange = {
                    enableVpn = it
                    uiStore.enableVpn = it
                },
            )

            CategoryHeader(text = stringResource(DesignR.string.vpn_service_options))

            SwitchRow(
                title = stringResource(DesignR.string.bypass_private_network),
                summary = stringResource(DesignR.string.bypass_private_network_summary),
                checked = bypassPrivateNetwork,
                enabled = depsEnabled,
                onCheckedChange = {
                    bypassPrivateNetwork = it
                    srvStore.bypassPrivateNetwork = it
                },
            )

            SwitchRow(
                title = stringResource(DesignR.string.dns_hijacking),
                summary = stringResource(DesignR.string.dns_hijacking_summary),
                checked = dnsHijacking,
                enabled = depsEnabled,
                onCheckedChange = {
                    dnsHijacking = it
                    srvStore.dnsHijacking = it
                },
            )

            SwitchRow(
                title = stringResource(DesignR.string.allow_bypass),
                summary = stringResource(DesignR.string.allow_bypass_summary),
                checked = allowBypass,
                enabled = depsEnabled,
                onCheckedChange = {
                    allowBypass = it
                    srvStore.allowBypass = it
                },
            )

            SwitchRow(
                title = stringResource(DesignR.string.allow_ipv6),
                summary = stringResource(DesignR.string.allow_ipv6_summary),
                checked = allowIpv6,
                enabled = depsEnabled,
                onCheckedChange = {
                    allowIpv6 = it
                    srvStore.allowIpv6 = it
                },
            )

            if (Build.VERSION.SDK_INT >= 29) {
                SwitchRow(
                    title = stringResource(DesignR.string.system_proxy),
                    summary = stringResource(DesignR.string.system_proxy_summary),
                    checked = systemProxy,
                    enabled = depsEnabled,
                    onCheckedChange = {
                        systemProxy = it
                        srvStore.systemProxy = it
                    },
                )
            }

            SelectRow(
                title = stringResource(DesignR.string.tun_stack_mode),
                options = listOf(
                    stringResource(DesignR.string.tun_stack_system),
                    stringResource(DesignR.string.tun_stack_gvisor),
                    stringResource(DesignR.string.tun_stack_mixed),
                    stringResource(DesignR.string.tun_stack_mips),
                ),
                selectedIndex = listOf("system", "gvisor", "mixed", "mips")
                    .indexOf(tunStackMode).coerceAtLeast(0),
                enabled = depsEnabled,
                onSelect = { index ->
                    val value = listOf("system", "gvisor", "mixed", "mips")[index]
                    tunStackMode = value
                    srvStore.tunStackMode = value
                },
            )

            SelectRow(
                title = stringResource(DesignR.string.access_control_mode),
                options = listOf(
                    stringResource(DesignR.string.allow_all_apps),
                    stringResource(DesignR.string.allow_selected_apps),
                    stringResource(DesignR.string.deny_selected_apps),
                ),
                selectedIndex = AccessControlMode.values().indexOf(accessControlMode),
                enabled = depsEnabled,
                onSelect = { index ->
                    val value = AccessControlMode.values()[index]
                    accessControlMode = value
                    srvStore.accessControlMode = value
                },
            )

            // The old design never disabled this entry with the VPN
            // dependencies, so it stays clickable in every state.
            ClickRow(
                title = stringResource(DesignR.string.access_control_packages),
                summary = stringResource(DesignR.string.access_control_packages_summary),
                onClick = { onRequest(NetworkSettingsDesign.Request.StartAccessControlList) },
            )
        }
    }
}
