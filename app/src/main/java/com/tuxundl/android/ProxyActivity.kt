package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.design.ProxyDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.ProxyGroupUiState
import com.tuxundl.android.ui.ProxyScreen
import com.tuxundl.android.ui.ProxyUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.tuxundl.android.design.R as DesignR

class ProxyActivity : BaseActivity<ProxyDesign>() {
    private val uiState = ProxyUiState()
    private val uiRequests = Channel<ProxyDesign.Request>(Channel.UNLIMITED)
    private val uiActions = Channel<Action>(Channel.UNLIMITED)

    private sealed interface Action {
        data class SetSort(val sort: ProxySort) : Action
        data class SetExcludeNotSelectable(val exclude: Boolean) : Action
    }

    override suspend fun main() {
        var names = listOf<String>()
        val reloadLock = Semaphore(10)

        suspend fun reloadNames() {
            names = withClash {
                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
            }

            val lastGroup = uiStore.proxyLastGroup

            uiState.groups = names.map { name ->
                ProxyGroupUiState(name).apply {
                    expanded = name == lastGroup
                }
            }

            uiRequests.trySend(ProxyDesign.Request.ReloadAll)
        }

        uiState.mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        uiState.sort = uiStore.proxySort
        uiState.excludeNotSelectable = uiStore.proxyExcludeNotSelectable

        reloadNames()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newNames = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                            }

                            if (newNames != names) {
                                reloadNames()
                            }
                        }
                        else -> Unit
                    }
                }
                uiActions.onReceive {
                    when (it) {
                        is Action.SetSort -> {
                            uiStore.proxySort = it.sort

                            uiState.sort = it.sort

                            uiRequests.trySend(ProxyDesign.Request.ReloadAll)
                        }
                        is Action.SetExcludeNotSelectable -> {
                            uiStore.proxyExcludeNotSelectable = it.exclude

                            uiState.excludeNotSelectable = it.exclude

                            reloadNames()
                        }
                    }
                }
                uiRequests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> {
                            reloadNames()
                        }
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                uiRequests.trySend(ProxyDesign.Request.Reload(idx))
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            val index = it.index

                            if (index in names.indices) {
                                launch {
                                    val group = reloadLock.withPermit {
                                        withClash {
                                            queryProxyGroup(names[index], uiStore.proxySort)
                                        }
                                    }

                                    uiState.groups.getOrNull(index)?.apply {
                                        type = group.type
                                        now = group.now
                                        proxies = group.proxies
                                        selectable = group.type == "Selector"
                                        loaded = true
                                        urlTesting = false
                                    }
                                }
                            }
                        }
                        is ProxyDesign.Request.Select -> {
                            withClash {
                                patchSelector(names[it.index], it.name)
                            }

                            uiState.groups.getOrNull(it.index)?.now = it.name
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            val index = it.index

                            uiState.groups.getOrNull(index)?.urlTesting = true

                            launch {
                                withClash {
                                    healthCheck(names[index])
                                }

                                uiRequests.send(ProxyDesign.Request.Reload(index))
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            launch {
                                uiState.snackbarHostState.showSnackbar(
                                    message = getString(DesignR.string.mode_switch_tips),
                                    duration = SnackbarDuration.Short,
                                )
                            }

                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)

                                o.mode = it.mode

                                patchOverride(Clash.OverrideSlot.Session, o)
                            }

                            uiState.mode = it.mode
                        }
                    }
                }
            }
        }
    }

    override fun onStopped(cause: String?) {
        super.onStopped(cause)

        // BaseActivity shows stop causes through the Design toast, which the
        // Compose proxy screen no longer has; surface it as a snackbar instead.
        if (cause != null && activityStarted) {
            launch {
                uiState.snackbarHostState.showSnackbar(message = cause)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val darkTheme = when (uiStore.darkMode) {
            DarkMode.Auto -> resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            DarkMode.ForceLight -> false
            DarkMode.ForceDark -> true
        }

        setContent {
            TuxunDLTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ProxyScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onSortChange = { sort -> uiActions.trySend(Action.SetSort(sort)) },
                        onExcludeNotSelectableChange = { exclude ->
                            uiActions.trySend(Action.SetExcludeNotSelectable(exclude))
                        },
                        onExpandedGroupChange = { name -> uiStore.proxyLastGroup = name },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
