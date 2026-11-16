package com.tuxundl.android.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.R
import com.tuxundl.android.design.SettingsDesign
import com.tuxundl.android.design.R as DesignR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRequest: (SettingsDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_settings,
                title = stringResource(DesignR.string.app),
                onClick = { onRequest(SettingsDesign.Request.StartApp) },
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
                icon = DesignR.drawable.ic_baseline_meta,
                title = stringResource(DesignR.string.meta_features),
                onClick = { onRequest(SettingsDesign.Request.StartMetaFeature) },
            )
            SettingsEntryCard(
                icon = DesignR.drawable.ic_baseline_brightness_4,
                title = stringResource(R.string.appearance),
                onClick = { onRequest(SettingsDesign.Request.StartAppearance) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsEntryCard(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
            )
        }
    }
}
