package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.BuildConfig
import com.tuxundl.android.R
import com.tuxundl.android.design.R as DesignR

/**
 * Shared building blocks of the add-profile UI (1.4.0): the
 * subscription page's add box and onboarding step 3 use the exact
 * same components, so both places offer the same ways in — a URL
 * field with the clipboard folded into it, plus compact scan /
 * album / file actions.
 */

/** URL field with a paste button inside it (clipboard import lives here now). */
@Composable
fun PasteUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val clipboard = LocalClipboardManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
        trailingIcon = {
            TextButton(
                onClick = {
                    clipboard.getText()?.text
                        ?.takeIf { it.isNotBlank() }
                        ?.let { onValueChange(it.trim()) }
                },
                enabled = enabled,
            ) {
                Text(text = stringResource(R.string.onboarding_paste))
            }
        },
    )
}

/** Three compact import actions: scan a QR code, pick from album, pick a file. */
@Composable
fun ImportActionsRow(
    onScan: () -> Unit,
    onAlbum: () -> Unit,
    onFile: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ImportActionButton(
            iconRes = DesignR.drawable.baseline_qr_code_scanner,
            label = stringResource(R.string.import_action_scan),
            onClick = onScan,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        // TV Beta: the album entry is hidden. Its decoder
        // (util/QrDecode.kt) uses the unbundled ML Kit barcode
        // model, which is delivered by Google Play services —
        // absent on TVs, where the decode could only fail. The
        // camera entry stays: quickie bundles its model and needs
        // no Google services (on a camera-less TV it just reports
        // an error), and file import works everywhere.
        if (!BuildConfig.IS_TV) {
            ImportActionButton(
                iconRes = DesignR.drawable.ic_baseline_image,
                label = stringResource(R.string.import_action_album),
                onClick = onAlbum,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        ImportActionButton(
            iconRes = DesignR.drawable.ic_baseline_attach_file,
            label = stringResource(R.string.import_action_file),
            onClick = onFile,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ImportActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TuxunCard(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .then(
                    if (enabled) {
                        Modifier
                    } else {
                        Modifier.alpha(0.45f)
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
