package com.tuxundl.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tuxundl.android.design.NewProfileDesign
import com.tuxundl.android.design.model.ProfileProvider
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose new-profile screen. */
class NewProfileUiState {
    var providers by mutableStateOf<List<ProfileProvider>>(emptyList())

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProfileScreen(
    state: NewProfileUiState,
    onRequest: (NewProfileDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.create_profile),
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.providers) { provider ->
                ProviderCard(
                    modifier = Modifier.animateItem(),
                    provider = provider,
                    onClick = {
                        if (provider is ProfileProvider.QR) {
                            onRequest(NewProfileDesign.Request.LaunchScanner(provider))
                        } else {
                            onRequest(NewProfileDesign.Request.Create(provider))
                        }
                    },
                    onLongClick = {
                        if (provider is ProfileProvider.External) {
                            onRequest(NewProfileDesign.Request.OpenDetail(provider))
                            true
                        } else {
                            false
                        }
                    },
                )
            }

            item {
                AlbumScanCard(
                    modifier = Modifier.animateItem(),
                    onClick = { onRequest(NewProfileDesign.Request.ScanFromAlbum) },
                )
            }
        }
    }
}

/** Pick an image from the album and scan the QR code inside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumScanCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TuxunCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(DesignR.drawable.ic_baseline_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    text = stringResource(com.tuxundl.android.R.string.import_from_album),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(com.tuxundl.android.R.string.import_from_album_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProviderCard(
    provider: ProfileProvider,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    TuxunCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick() },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val icon = provider.icon
            if (icon != null) {
                val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Column {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = provider.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
