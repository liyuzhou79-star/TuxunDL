package com.tuxundl.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuxundl.android.core.model.Provider
import com.tuxundl.android.design.ProvidersDesign
import com.tuxundl.android.design.util.elapsedIntervalString
import com.tuxundl.android.design.util.type
import com.tuxundl.android.design.R as DesignR

/** State holder for the Compose providers screen. */
class ProvidersUiState {
    var providers by mutableStateOf<List<Provider>>(emptyList())
    var updating by mutableStateOf<Set<Int>>(emptySet())

    /** Local updated-at overrides after a successful manual update. */
    var updatedAt by mutableStateOf<Map<Int, Long>>(emptyMap())

    /** Refreshed once a minute by the activity so elapsed labels stay fresh. */
    var now by mutableStateOf(System.currentTimeMillis())

    val snackbarHostState = SnackbarHostState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    state: ProvidersUiState,
    onRequest: (ProvidersDesign.Request) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbarHostState) },
        topBar = {
            SubTopBar(
                title = stringResource(DesignR.string.providers),
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            state.providers.forEachIndexed { index, provider ->
                                if (index !in state.updating &&
                                    provider.vehicleType != Provider.VehicleType.Inline
                                ) {
                                    state.updating = state.updating + index
                                    onRequest(ProvidersDesign.Request.Update(index, provider))
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_baseline_sync),
                            contentDescription = null,
                        )
                    }
                },
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
            itemsIndexed(state.providers) { index, provider ->
                val itemStyle = LocalAnimStyle.current
                TuxunCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tuxunFadeSpec(itemStyle, entering = true),
                            placementSpec = tuxunPlacementSpec(itemStyle),
                            fadeOutSpec = tuxunFadeSpec(itemStyle, entering = false),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = provider.type(context),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = (
                                    state.now - (state.updatedAt[index] ?: provider.updatedAt)
                                    ).elapsedIntervalString(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index in state.updating) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(24.dp),
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    state.updating = state.updating + index
                                    onRequest(ProvidersDesign.Request.Update(index, provider))
                                },
                            ) {
                                Icon(
                                    painter = painterResource(DesignR.drawable.ic_baseline_update),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
