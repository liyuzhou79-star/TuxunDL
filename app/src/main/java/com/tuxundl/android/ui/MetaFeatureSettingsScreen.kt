package com.tuxundl.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.getSystemService
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.design.MetaFeatureSettingsDesign
import kotlinx.coroutines.launch
import com.tuxundl.android.design.R as DesignR

/** Compose version of the old MetaFeatureSettingsDesign preference list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaFeatureSettingsScreen(
    configuration: ConfigurationOverride,
    state: OverrideUiState,
    onRequest: (MetaFeatureSettingsDesign.Request) -> Unit,
    onResetConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    val dontModify = stringResource(DesignR.string.dont_modify)

    var snifferEnable by remember { mutableStateOf(configuration.sniffer.enable) }
    val snifferDepsEnabled = snifferEnable != false

    var ageHelperHybrid by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.meta_features),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onRequest(MetaFeatureSettingsDesign.Request.ResetOverride) }) {
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
            SectionCard(title = stringResource(DesignR.string.age_key_category)) {
                ClickRow(
                    title = stringResource(DesignR.string.age_key_type_x25519),
                    summary = stringResource(DesignR.string.age_key_generate_summary),
                    onClick = { ageHelperHybrid = false },
                )
                ClickRow(
                    title = stringResource(DesignR.string.age_key_type_hybrid),
                    summary = stringResource(DesignR.string.age_key_generate_summary),
                    onClick = { ageHelperHybrid = true },
                )
            }

            SectionCard(title = stringResource(DesignR.string.settings)) {
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.unified_delay),
                    get = { configuration.unifiedDelay },
                    set = { configuration.unifiedDelay = it },
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.geodata_mode),
                    get = { configuration.geodataMode },
                    set = { configuration.geodataMode = it },
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.tcp_concurrent),
                    get = { configuration.tcpConcurrent },
                    set = { configuration.tcpConcurrent = it },
                )
                OverrideSelectRow(
                    title = stringResource(DesignR.string.find_process_mode),
                    options = listOf(
                        null,
                        ConfigurationOverride.FindProcessMode.Off,
                        ConfigurationOverride.FindProcessMode.Strict,
                        ConfigurationOverride.FindProcessMode.Always,
                    ),
                    labels = listOf(
                        stringResource(DesignR.string.dont_modify),
                        stringResource(DesignR.string.off),
                        stringResource(DesignR.string.strict),
                        stringResource(DesignR.string.always),
                    ),
                    get = { configuration.findProcessMode },
                    set = { configuration.findProcessMode = it },
                )
            }

            SectionCard(title = stringResource(DesignR.string.sniffer_setting)) {
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.strategy),
                    get = { configuration.sniffer.enable },
                    set = {
                        configuration.sniffer.enable = it
                        snifferEnable = it
                    },
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.sniff_http_ports),
                    get = { configuration.sniffer.sniff.http.ports },
                    set = { configuration.sniffer.sniff.http.ports = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.sniff_http_override_destination),
                    get = { configuration.sniffer.sniff.http.overrideDestination },
                    set = { configuration.sniffer.sniff.http.overrideDestination = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.sniff_tls_ports),
                    get = { configuration.sniffer.sniff.tls.ports },
                    set = { configuration.sniffer.sniff.tls.ports = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.sniff_tls_override_destination),
                    get = { configuration.sniffer.sniff.tls.overrideDestination },
                    set = { configuration.sniffer.sniff.tls.overrideDestination = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.sniff_quic_ports),
                    get = { configuration.sniffer.sniff.quic.ports },
                    set = { configuration.sniffer.sniff.quic.ports = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.sniff_quic_override_destination),
                    get = { configuration.sniffer.sniff.quic.overrideDestination },
                    set = { configuration.sniffer.sniff.quic.overrideDestination = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.force_dns_mapping),
                    get = { configuration.sniffer.forceDnsMapping },
                    set = { configuration.sniffer.forceDnsMapping = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.parse_pure_ip),
                    get = { configuration.sniffer.parsePureIp },
                    set = { configuration.sniffer.parsePureIp = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideBoolSelectRow(
                    title = stringResource(DesignR.string.override_destination),
                    get = { configuration.sniffer.overrideDestination },
                    set = { configuration.sniffer.overrideDestination = it },
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.force_domain),
                    get = { configuration.sniffer.forceDomain },
                    set = { configuration.sniffer.forceDomain = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.skip_domain),
                    get = { configuration.sniffer.skipDomain },
                    set = { configuration.sniffer.skipDomain = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.skip_src_address),
                    get = { configuration.sniffer.skipSrcAddress },
                    set = { configuration.sniffer.skipSrcAddress = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
                OverrideListRow(
                    title = stringResource(DesignR.string.skip_dst_address),
                    get = { configuration.sniffer.skipDstAddress },
                    set = { configuration.sniffer.skipDstAddress = it },
                    placeholder = dontModify,
                    enabled = snifferDepsEnabled,
                )
            }

            SectionCard(title = stringResource(DesignR.string.geox_files)) {
                ClickRow(
                    title = stringResource(DesignR.string.import_geoip_file),
                    summary = stringResource(DesignR.string.press_to_import),
                    onClick = { onRequest(MetaFeatureSettingsDesign.Request.ImportGeoIp) },
                )
                ClickRow(
                    title = stringResource(DesignR.string.import_geosite_file),
                    summary = stringResource(DesignR.string.press_to_import),
                    onClick = { onRequest(MetaFeatureSettingsDesign.Request.ImportGeoSite) },
                )
                ClickRow(
                    title = stringResource(DesignR.string.import_country_file),
                    summary = stringResource(DesignR.string.press_to_import),
                    onClick = { onRequest(MetaFeatureSettingsDesign.Request.ImportCountry) },
                )
                ClickRow(
                    title = stringResource(DesignR.string.import_asn_file),
                    summary = stringResource(DesignR.string.press_to_import),
                    onClick = { onRequest(MetaFeatureSettingsDesign.Request.ImportASN) },
                )
            }
        }
    }

    val hybrid = ageHelperHybrid
    if (hybrid != null) {
        AgeKeyHelperDialog(
            hybrid = hybrid,
            snackbarHostState = state.snackbarHostState,
            onDismiss = { ageHelperHybrid = null },
        )
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

/** Compose version of the old age key helper dialog. */
@Composable
private fun AgeKeyHelperDialog(
    hybrid: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var secretKey by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }

    val secretValid = secretKey.isBlank() || Clash.veritySecretKeys(secretKey)
    val publicValid = publicKey.isBlank() || Clash.verityPublicKeys(publicKey)
    val copiedText = stringResource(DesignR.string.copied)

    fun copy(label: String, value: String) {
        if (value.isBlank()) return

        val data = ClipData.newPlainText(label, value)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

        scope.launch {
            snackbarHostState.showSnackbar(
                message = copiedText,
                duration = SnackbarDuration.Short,
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        if (hybrid) DesignR.string.age_key_type_hybrid
                        else DesignR.string.age_key_type_x25519
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(DesignR.string.age_secret_key)) },
                    isError = !secretValid,
                    supportingText = if (!secretValid) {
                        { Text(text = stringResource(DesignR.string.age_secret_key_error)) }
                    } else {
                        null
                    },
                )
                Row {
                    TextButton(onClick = {
                        val keyPair = if (hybrid) {
                            Clash.genHybridKeyPair()
                        } else {
                            Clash.genX25519KeyPair()
                        }

                        secretKey = keyPair.secretKey
                        publicKey = keyPair.publicKey
                    }) {
                        Text(text = stringResource(DesignR.string.age_key_generate))
                    }
                    TextButton(onClick = { copy("age_secret_key", secretKey) }) {
                        Text(text = stringResource(DesignR.string.age_key_copy))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(DesignR.string.age_public_key)) },
                    isError = !publicValid,
                    supportingText = if (!publicValid) {
                        { Text(text = stringResource(DesignR.string.age_public_key_error)) }
                    } else {
                        null
                    },
                )
                Row {
                    TextButton(onClick = {
                        publicKey = Clash.toPublicKeys(secretKey).firstOrNull() ?: ""
                    }) {
                        Text(text = stringResource(DesignR.string.age_key_to_public))
                    }
                    TextButton(onClick = { copy("age_public_key", publicKey) }) {
                        Text(text = stringResource(DesignR.string.age_key_copy))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(DesignR.string.close))
                    }
                }
            }
        }
    }
}
