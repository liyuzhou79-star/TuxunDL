package com.tuxundl.android.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tuxundl.android.BuildConfig
import com.tuxundl.android.R
import com.tuxundl.android.design.NewProfileDesign
import com.tuxundl.android.design.model.ProfileProvider
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose new-profile screen. */
class NewProfileUiState {
    var providers by mutableStateOf<List<ProfileProvider>>(emptyList())

    val snackbarHostState = SnackbarHostState()
}

/**
 * Add-profile page (reworked in 1.4.0): the subscription URL box sits
 * on top — clipboard import is folded into the field as its paste
 * button, so there is no separate clipboard row any more — and the
 * other ways in (QR scan, album, file, external providers) are
 * compact rows, one size smaller than the old full-height cards.
 * Creating from the URL box prefills the new profile's source (and
 * name), so the properties screen opens ready to save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProfileScreen(
    state: NewProfileUiState,
    onRequest: (NewProfileDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    val style = animStyle()
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val urlProvider = state.providers
        .filterIsInstance<ProfileProvider.Url>().firstOrNull()
    val fileProvider = state.providers
        .filterIsInstance<ProfileProvider.File>().firstOrNull()
    val qrProvider = state.providers
        .filterIsInstance<ProfileProvider.QR>().firstOrNull()
    val externals = state.providers
        .filterIsInstance<ProfileProvider.External>()

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
            // --- Subscription URL box ------------------------------
            item {
                TuxunCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tuxunFadeSpec(style, entering = true),
                        placementSpec = tuxunPlacementSpec(style),
                        fadeOutSpec = tuxunFadeSpec(style, entering = false),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PasteUrlField(
                            value = url,
                            onValueChange = { url = it },
                            label = stringResource(R.string.onboarding_sub_url),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = {
                                Text(text = stringResource(R.string.onboarding_sub_name))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                urlProvider?.let { provider ->
                                    onRequest(
                                        NewProfileDesign.Request.Create(
                                            provider = provider,
                                            source = url.trim(),
                                            name = name.trim(),
                                        ),
                                    )
                                }
                            },
                            enabled = url.isNotBlank() && urlProvider != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.profile_add_import))
                        }
                    }
                }
            }

            // --- Compact import entries: scan / album / file -------
            item {
                TuxunCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tuxunFadeSpec(style, entering = true),
                        placementSpec = tuxunPlacementSpec(style),
                        fadeOutSpec = tuxunFadeSpec(style, entering = false),
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (qrProvider != null) {
                            ImportEntryRow(
                                icon = qrProvider.icon,
                                title = qrProvider.name,
                                summary = qrProvider.summary,
                                onClick = {
                                    onRequest(
                                        NewProfileDesign.Request.LaunchScanner(qrProvider),
                                    )
                                },
                            )
                            // Divider only when a row follows: on
                            // TV the album row below is hidden, so
                            // the file row is the next one (and it
                            // brings no divider of its own there).
                            if (!BuildConfig.IS_TV || fileProvider != null) {
                                ImportDivider()
                            }
                        }
                        // TV Beta: album row hidden — its ML Kit
                        // decoder needs Google Play services to
                        // deliver the barcode model, which TVs do
                        // not have. Same gate as ImportActionsRow.
                        if (!BuildConfig.IS_TV) {
                            ImportEntryRow(
                                iconRes = DesignR.drawable.ic_baseline_image,
                                title = stringResource(R.string.import_from_album),
                                summary = stringResource(R.string.import_from_album_summary),
                                onClick = { onRequest(NewProfileDesign.Request.ScanFromAlbum) },
                            )
                        }
                        if (fileProvider != null) {
                            if (!BuildConfig.IS_TV) {
                                ImportDivider()
                            }
                            ImportEntryRow(
                                icon = fileProvider.icon,
                                title = fileProvider.name,
                                summary = fileProvider.summary,
                                onClick = {
                                    onRequest(
                                        NewProfileDesign.Request.Create(fileProvider),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // --- External providers (apps offering a URL) ----------
            items(externals) { provider ->
                ProviderCard(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tuxunFadeSpec(style, entering = true),
                        placementSpec = tuxunPlacementSpec(style),
                        fadeOutSpec = tuxunFadeSpec(style, entering = false),
                    ),
                    provider = provider,
                    onClick = {
                        onRequest(NewProfileDesign.Request.Create(provider))
                    },
                    onLongClick = {
                        onRequest(NewProfileDesign.Request.OpenDetail(provider))
                        true
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** One compact import row inside the shared card (icon + title + summary). */
@Composable
private fun ImportEntryRow(
    icon: Drawable?,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Resource-icon variant of [ImportEntryRow]. */
@Composable
private fun ImportEntryRow(
    iconRes: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val icon = provider.icon
            if (icon != null) {
                val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.bodyLarge,
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
