package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.core.model.LogMessage
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.design.OverrideSettingsDesign
import com.tuxundl.android.design.R as DesignR

/** State holder for the two override settings screens. */
class OverrideUiState {
    var showResetConfirm by mutableStateOf(false)

    val snackbarHostState = SnackbarHostState()
}

/** Compose version of the old OverrideSettingsDesign preference list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideSettingsScreen(
    configuration: ConfigurationOverride,
    state: OverrideUiState,
    onRequest: (OverrideSettingsDesign.Request) -> Unit,
    onResetConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    val dontModify = stringResource(DesignR.string.dont_modify)
    val disabledText = stringResource(DesignR.string.disabled)
    val defaultText = stringResource(DesignR.string.default_)

    var dnsEnable by remember { mutableStateOf(configuration.dns.enable) }
    val dnsDepsEnabled = dnsEnable != false

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.override),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onRequest(OverrideSettingsDesign.Request.ResetOverride) }) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_delete),
                            contentDescription = null,
                        )
                    }
                },
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
            CategoryHeader(text = stringResource(DesignR.string.general))

            OverridePortRow(
                title = stringResource(DesignR.string.http_port),
                get = { configuration.httpPort },
                set = { configuration.httpPort = it },
                placeholder = dontModify,
                empty = disabledText,
            )
            OverridePortRow(
                title = stringResource(DesignR.string.socks_port),
                get = { configuration.socksPort },
                set = { configuration.socksPort = it },
                placeholder = dontModify,
                empty = disabledText,
            )
            OverridePortRow(
                title = stringResource(DesignR.string.redirect_port),
                get = { configuration.redirectPort },
                set = { configuration.redirectPort = it },
                placeholder = dontModify,
                empty = disabledText,
            )
            OverridePortRow(
                title = stringResource(DesignR.string.tproxy_port),
                get = { configuration.tproxyPort },
                set = { configuration.tproxyPort = it },
                placeholder = dontModify,
                empty = disabledText,
            )
            OverridePortRow(
                title = stringResource(DesignR.string.mixed_port),
                get = { configuration.mixedPort },
                set = { configuration.mixedPort = it },
                placeholder = dontModify,
                empty = disabledText,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.authentication),
                get = { configuration.authentication },
                set = { configuration.authentication = it },
                placeholder = dontModify,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.allow_lan),
                get = { configuration.allowLan },
                set = { configuration.allowLan = it },
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.ipv6),
                get = { configuration.ipv6 },
                set = { configuration.ipv6 = it },
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.bind_address),
                get = { configuration.bindAddress },
                set = { configuration.bindAddress = it },
                placeholder = dontModify,
                empty = defaultText,
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.external_controller),
                get = { configuration.externalController },
                set = { configuration.externalController = it },
                placeholder = dontModify,
                empty = defaultText,
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.external_controller_tls),
                get = { configuration.externalControllerTLS },
                set = { configuration.externalControllerTLS = it },
                placeholder = dontModify,
                empty = defaultText,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.allow_origins),
                get = { configuration.externalControllerCors.allowOrigins },
                set = { configuration.externalControllerCors.allowOrigins = it },
                placeholder = dontModify,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.allow_private_network),
                get = { configuration.externalControllerCors.allowPrivateNetwork },
                set = { configuration.externalControllerCors.allowPrivateNetwork = it },
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.secret),
                get = { configuration.secret },
                set = { configuration.secret = it },
                placeholder = dontModify,
                empty = defaultText,
            )
            OverrideSelectRow(
                title = stringResource(DesignR.string.mode),
                options = listOf(
                    null,
                    TunnelState.Mode.Direct,
                    TunnelState.Mode.Global,
                    TunnelState.Mode.Rule,
                ),
                labels = listOf(
                    stringResource(DesignR.string.dont_modify),
                    stringResource(DesignR.string.direct_mode),
                    stringResource(DesignR.string.global_mode),
                    stringResource(DesignR.string.rule_mode),
                ),
                get = { configuration.mode },
                set = { configuration.mode = it },
            )
            OverrideSelectRow(
                title = stringResource(DesignR.string.log_level),
                options = listOf(
                    null,
                    LogMessage.Level.Info,
                    LogMessage.Level.Warning,
                    LogMessage.Level.Error,
                    LogMessage.Level.Debug,
                    LogMessage.Level.Silent,
                ),
                labels = listOf(
                    stringResource(DesignR.string.dont_modify),
                    stringResource(DesignR.string.info),
                    stringResource(DesignR.string.warning),
                    stringResource(DesignR.string.error),
                    stringResource(DesignR.string.debug),
                    stringResource(DesignR.string.silent),
                ),
                get = { configuration.logLevel },
                set = { configuration.logLevel = it },
            )
            OverrideMapRow(
                title = stringResource(DesignR.string.hosts),
                get = { configuration.hosts },
                set = { configuration.hosts = it },
                placeholder = dontModify,
            )

            CategoryHeader(text = stringResource(DesignR.string.dns))

            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.strategy),
                get = { configuration.dns.enable },
                set = {
                    configuration.dns.enable = it
                    dnsEnable = it
                },
                labels = listOf(
                    stringResource(DesignR.string.dont_modify),
                    stringResource(DesignR.string.force_enable),
                    stringResource(DesignR.string.use_built_in),
                ),
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.prefer_h3),
                get = { configuration.dns.preferH3 },
                set = { configuration.dns.preferH3 = it },
                enabled = dnsDepsEnabled,
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.listen),
                get = { configuration.dns.listen },
                set = { configuration.dns.listen = it },
                placeholder = dontModify,
                empty = disabledText,
                enabled = dnsDepsEnabled,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.append_system_dns),
                get = { configuration.app.appendSystemDns },
                set = { configuration.app.appendSystemDns = it },
                enabled = dnsDepsEnabled,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.ipv6),
                get = { configuration.dns.ipv6 },
                set = { configuration.dns.ipv6 = it },
                enabled = dnsDepsEnabled,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.use_hosts),
                get = { configuration.dns.useHosts },
                set = { configuration.dns.useHosts = it },
                enabled = dnsDepsEnabled,
            )
            OverrideSelectRow(
                title = stringResource(DesignR.string.enhanced_mode),
                options = listOf(
                    null,
                    ConfigurationOverride.DnsEnhancedMode.None,
                    ConfigurationOverride.DnsEnhancedMode.FakeIp,
                    ConfigurationOverride.DnsEnhancedMode.Mapping,
                ),
                labels = listOf(
                    stringResource(DesignR.string.dont_modify),
                    stringResource(DesignR.string.disabled),
                    stringResource(DesignR.string.fakeip),
                    stringResource(DesignR.string.mapping),
                ),
                get = { configuration.dns.enhancedMode },
                set = { configuration.dns.enhancedMode = it },
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.name_server),
                get = { configuration.dns.nameServer },
                set = { configuration.dns.nameServer = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.fallback),
                get = { configuration.dns.fallback },
                set = { configuration.dns.fallback = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.default_name_server),
                get = { configuration.dns.defaultServer },
                set = { configuration.dns.defaultServer = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.fakeip_filter),
                get = { configuration.dns.fakeIpFilter },
                set = { configuration.dns.fakeIpFilter = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideSelectRow(
                title = stringResource(DesignR.string.fakeip_filter_mode),
                options = listOf(
                    null,
                    ConfigurationOverride.FilterMode.BlackList,
                    ConfigurationOverride.FilterMode.WhiteList,
                    ConfigurationOverride.FilterMode.Rule,
                ),
                labels = listOf(
                    stringResource(DesignR.string.dont_modify),
                    stringResource(DesignR.string.blacklist),
                    stringResource(DesignR.string.whitelist),
                    stringResource(DesignR.string.rule),
                ),
                get = { configuration.dns.fakeIPFilterMode },
                set = { configuration.dns.fakeIPFilterMode = it },
                enabled = dnsDepsEnabled,
            )
            OverrideBoolSelectRow(
                title = stringResource(DesignR.string.geoip_fallback),
                get = { configuration.dns.fallbackFilter.geoIp },
                set = { configuration.dns.fallbackFilter.geoIp = it },
                enabled = dnsDepsEnabled,
            )
            OverrideTextRow(
                title = stringResource(DesignR.string.geoip_fallback_code),
                get = { configuration.dns.fallbackFilter.geoIpCode },
                set = { configuration.dns.fallbackFilter.geoIpCode = it },
                placeholder = dontModify,
                empty = stringResource(DesignR.string.raw_cn),
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.domain_fallback),
                get = { configuration.dns.fallbackFilter.domain },
                set = { configuration.dns.fallbackFilter.domain = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideListRow(
                title = stringResource(DesignR.string.ipcidr_fallback),
                get = { configuration.dns.fallbackFilter.ipcidr },
                set = { configuration.dns.fallbackFilter.ipcidr = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
            OverrideMapRow(
                title = stringResource(DesignR.string.name_server_policy),
                get = { configuration.dns.nameserverPolicy },
                set = { configuration.dns.nameserverPolicy = it },
                placeholder = dontModify,
                enabled = dnsDepsEnabled,
            )
        }
    }

    if (state.showResetConfirm) {
        ConfirmDialog(
            title = stringResource(DesignR.string.reset_override_settings),
            message = stringResource(DesignR.string.reset_override_settings_message),
            onDismiss = { state.showResetConfirm = false },
            onConfirm = {
                state.showResetConfirm = false
                onResetConfirmed()
            },
        )
    }
}
