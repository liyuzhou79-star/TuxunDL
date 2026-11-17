package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.service.util.CustomDnsSettings
import com.tuxundl.android.design.R as DesignR

/** Editable state for the custom DNS screen; one server per line. */
class DnsSettingsUiState {
    var enabled by mutableStateOf(false)
    var nameserverText by mutableStateOf("")
    var fallbackText by mutableStateOf("")

    val snackbarHostState = SnackbarHostState()

    fun load(settings: CustomDnsSettings) {
        enabled = settings.enabled
        nameserverText = settings.nameservers.joinToString("\n")
        fallbackText = settings.fallback.joinToString("\n")
    }

    fun toSettings(): CustomDnsSettings = CustomDnsSettings(
        enabled = enabled,
        nameservers = nameserverText.toLines(),
        fallback = fallbackText.toLines(),
    )

    private fun String.toLines(): List<String> =
        split("\n").map { it.trim() }.filter { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    state: DnsSettingsUiState,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(R.string.custom_dns),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_save),
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
                .tuxunPageEnter()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SectionCard(title = stringResource(R.string.custom_dns)) {
                SwitchRow(
                    title = stringResource(R.string.dns_enable),
                    summary = stringResource(R.string.dns_summary),
                    checked = state.enabled,
                    onCheckedChange = { state.enabled = it },
                )
            }

            SectionCard(title = stringResource(R.string.dns_nameserver)) {
                OutlinedTextField(
                    value = state.nameserverText,
                    onValueChange = { state.nameserverText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    label = { Text(text = stringResource(R.string.dns_nameserver_hint)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionCard(title = stringResource(R.string.dns_fallback)) {
                OutlinedTextField(
                    value = state.fallbackText,
                    onValueChange = { state.fallbackText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    label = { Text(text = stringResource(R.string.dns_fallback_hint)) },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
