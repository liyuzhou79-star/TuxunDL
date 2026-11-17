package com.tuxundl.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.design.R as DesignR

/** Compose version of the old HelpDesign content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    openLink: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.help),
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
                .padding(vertical = 8.dp),
        ) {
            HtmlText(
                text = stringResource(DesignR.string.tips_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionCard(title = stringResource(DesignR.string.document)) {
                ClickRow(
                    title = stringResource(DesignR.string.clash_wiki),
                    summary = stringResource(DesignR.string.clash_wiki_url),
                    onClick = {
                        openLink(Uri.parse(context.getString(DesignR.string.clash_wiki_url)))
                    },
                )
                ClickRow(
                    title = stringResource(DesignR.string.clash_meta_wiki),
                    summary = stringResource(DesignR.string.clash_meta_wiki_url),
                    onClick = {
                        openLink(Uri.parse(context.getString(DesignR.string.clash_meta_wiki_url)))
                    },
                )
            }

            SectionCard(title = stringResource(DesignR.string.sources)) {
                ClickRow(
                    title = stringResource(DesignR.string.clash_meta_core),
                    summary = stringResource(DesignR.string.clash_meta_core_url),
                    onClick = {
                        openLink(Uri.parse(context.getString(DesignR.string.clash_meta_core_url)))
                    },
                )
                ClickRow(
                    title = stringResource(DesignR.string.clash_meta_for_android),
                    summary = stringResource(DesignR.string.meta_github_url),
                    onClick = {
                        openLink(Uri.parse(context.getString(DesignR.string.meta_github_url)))
                    },
                )
            }
        }
    }
}
