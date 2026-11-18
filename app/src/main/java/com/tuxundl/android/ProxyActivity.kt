package com.tuxundl.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import com.tuxundl.android.common.util.intent
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.design.ProxyDesign
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.ui.ProxyGroupUiState
import com.tuxundl.android.ui.ProxyScreen
import com.tuxundl.android.ui.ProxyUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
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
        data class ToggleFavorite(val group: String, val node: String) : Action

        /** Single-node delay test (probes only that node; see ProxyScreen). */
        data class TestNode(val group: String, val node: String) : Action
    }

    override suspend fun main() {
        var names = listOf<String>()
        val reloadLock = Semaphore(10)

        // Groups with a group-wide delay test in flight. When such a
        // group's reload lands, all its testing nodes settle into
        // testedNodes (see the Reload handler).
        val pendingTestGroups = mutableSetOf<String>()

        // Single-node tests that already finished and are waiting for
        // their group's reload to settle just that node ("group|node"
        // keys). Group tests do not use this set.
        val completedTestNodes = mutableSetOf<String>()

        // Record a finished delay test (single-node or whole-group)
        // for the "last tested" line on the proxy screen; persisted
        // so it survives leaving and re-entering the page.
        fun markTestFinished() {
            val now = System.currentTimeMillis()

            uiStore.proxyLastTestAt = now
            uiState.lastTestAt = now
        }

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
        uiState.favorites = uiStore.favoriteProxies
        uiState.lastTestAt = uiStore.proxyLastTestAt

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
                        is Action.ToggleFavorite -> {
                            val key = "${it.group}|${it.node}"
                            val updated = uiState.favorites.toMutableSet().apply {
                                if (!add(key)) remove(key)
                            }

                            uiStore.favoriteProxies = updated
                            uiState.favorites = updated
                        }
                        is Action.TestNode -> {
                            val key = "${it.group}|${it.node}"

                            if (key !in uiState.testingNodes) {
                                val index = names.indexOf(it.group)

                                if (index >= 0) {
                                    uiState.testingNodes = uiState.testingNodes + key

                                    launch {
                                        // Single-node delay test: only
                                        // this proxy is probed (the
                                        // native equivalent of
                                        // /proxies/{name}/delay); the
                                        // group then reloads to read
                                        // its new delay back. Every
                                        // other node keeps its last
                                        // result and stays idle.
                                        withClash {
                                            healthCheckProxy(it.node)
                                        }

                                        completedTestNodes += key

                                        markTestFinished()

                                        uiRequests.send(ProxyDesign.Request.Reload(index))
                                    }
                                }
                            }
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

                                        // Settle finished tests into
                                        // the tested set so a 65535
                                        // delay reads as "timeout", not
                                        // "not tested". A group test
                                        // settles every testing node of
                                        // the group; single-node tests
                                        // settle only their own node.
                                        val prefix = "$name|"
                                        val finished = if (name in pendingTestGroups) {
                                            pendingTestGroups.remove(name)

                                            uiState.testingNodes
                                                .filter { key -> key.startsWith(prefix) }
                                        } else {
                                            uiState.testingNodes
                                                .filter { key ->
                                                    key.startsWith(prefix) &&
                                                        key in completedTestNodes
                                                }
                                        }

                                        if (finished.isNotEmpty()) {
                                            uiState.testingNodes =
                                                uiState.testingNodes - finished
                                            uiState.testedNodes =
                                                uiState.testedNodes + finished
                                            completedTestNodes
                                                .removeAll(finished.toSet())
                                        }
                                    }
                                }
                            }
                        }
                        is ProxyDesign.Request.Select -> {
                            withClash {
                                patchSelector(names[it.index], it.name)
                            }

                            uiState.groups.getOrNull(it.index)?.now = it.name

                            com.tuxundl.android.util.Haptics.buzz(
                                this@ProxyActivity,
                                uiStore,
                                com.tuxundl.android.util.Haptics.Kind.Tap,
                            )
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            val index = it.index

                            // Mark every node of the group as testing so
                            // rows show "testing" instead of stale delays
                            // while the health check runs.
                            uiState.groups.getOrNull(index)?.let { group ->
                                group.urlTesting = true
                                pendingTestGroups += group.name
                                uiState.testingNodes = uiState.testingNodes +
                                    group.proxies.map { "${group.name}|${it.name}" }
                            }

                            launch {
                                withClash {
                                    healthCheck(names[index])
                                }

                                markTestFinished()

                                uiRequests.send(ProxyDesign.Request.Reload(index))

                                com.tuxundl.android.util.Haptics.buzz(
                                    this@ProxyActivity,
                                    uiStore,
                                    com.tuxundl.android.util.Haptics.Kind.Success,
                                )
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ProxyScreen(
                        state = uiState,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onSortChange = { sort -> uiActions.trySend(Action.SetSort(sort)) },
                        onExcludeNotSelectableChange = { exclude ->
                            uiActions.trySend(Action.SetExcludeNotSelectable(exclude))
                        },
                        onExpandedGroupChange = { name -> uiStore.proxyLastGroup = name },
                        onToggleFavorite = { group, node ->
                            uiActions.trySend(Action.ToggleFavorite(group, node))
                        },
                        onTestNode = { group, node ->
                            uiActions.trySend(Action.TestNode(group, node))
                        },
                        onOpenRules = { startActivity(RulesActivity::class.intent) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
