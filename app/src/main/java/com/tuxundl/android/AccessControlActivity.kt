package com.tuxundl.android

import android.Manifest.permission.INTERNET
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.getSystemService
import com.tuxundl.android.design.AccessControlDesign
import com.tuxundl.android.design.model.AppInfo
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.util.toAppInfo
import com.tuxundl.android.service.store.ServiceStore
import com.tuxundl.android.ui.AccessControlScreen
import com.tuxundl.android.ui.AccessControlUiState
import com.tuxundl.android.ui.theme.TuxunDLTheme
import com.tuxundl.android.ui.theme.customSeedColor
import com.tuxundl.android.util.startClashService
import com.tuxundl.android.util.stopClashService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class AccessControlActivity : BaseActivity<AccessControlDesign>() {
    private val uiState = AccessControlUiState()
    private val uiRequests = Channel<AccessControlDesign.Request>(Channel.UNLIMITED)
    private val selected: MutableSet<String> = mutableSetOf()
    private val serviceStore by lazy { ServiceStore(this) }

    override suspend fun main() {
        val service = serviceStore

        val stored = withContext(Dispatchers.IO) {
            service.accessControlPackages.toMutableSet()
        }

        selected.clear()
        selected.addAll(stored)

        // Prune packages whose apps are gone. An uninstalled app used
        // to stay in the stored set forever: invisible in the list (so
        // the user could not remove it), carried into clipboard
        // exports, and silently ignored by the tunnel builder.
        val installed = withContext(Dispatchers.IO) {
            packageManager.getInstalledPackages(0)
                .map { it.packageName }
                .toSet()
        }

        if (selected.retainAll(installed)) {
            withContext(Dispatchers.IO) {
                service.accessControlPackages = selected.toSet()
            }
        }

        // Snapshots for the exit-time comparison: the store itself is
        // now written through on every change (see onCreate), so the
        // restart decision must compare against the entry state, not
        // against the live store value.
        val initialPackages = selected.toSet()
        val initialMode = service.accessControlMode
        uiState.mode = initialMode

        defer {
            withContext(Dispatchers.IO) {
                val changed = selected != initialPackages ||
                    uiState.mode != initialMode
                service.accessControlPackages = selected
                service.accessControlMode = uiState.mode
                if (clashRunning && changed) {
                    stopClashService()
                    while (clashRunning) {
                        delay(200)
                    }
                    startClashService()
                }
            }
        }

        uiRequests.send(AccessControlDesign.Request.ReloadApps)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                uiRequests.onReceive {
                    when (it) {
                        AccessControlDesign.Request.ReloadApps -> {
                            uiState.apps = loadApps(selected)
                            uiState.loaded = true
                        }

                        AccessControlDesign.Request.SelectAll -> {
                            val all = withContext(Dispatchers.Default) {
                                uiState.apps.map(AppInfo::packageName)
                            }

                            selected.clear()
                            selected.addAll(all)

                            uiState.selectionTick++
                        }

                        AccessControlDesign.Request.SelectNone -> {
                            selected.clear()

                            uiState.selectionTick++
                        }

                        AccessControlDesign.Request.SelectInvert -> {
                            val all = withContext(Dispatchers.Default) {
                                uiState.apps.map(AppInfo::packageName).toSet() - selected
                            }

                            selected.clear()
                            selected.addAll(all)

                            uiState.selectionTick++
                        }

                        AccessControlDesign.Request.Import -> {
                            val clipboard = getSystemService<ClipboardManager>()
                            val data = clipboard?.primaryClip

                            if (data != null && data.itemCount > 0) {
                                val packages = data.getItemAt(0).text.split("\n").toSet()
                                val all = uiState.apps.map(AppInfo::packageName).intersect(packages)

                                selected.clear()
                                selected.addAll(all)
                            }

                            uiState.selectionTick++
                        }

                        AccessControlDesign.Request.Export -> {
                            val clipboard = getSystemService<ClipboardManager>()

                            val data = ClipData.newPlainText(
                                "packages",
                                selected.joinToString("\n")
                            )

                            clipboard?.setPrimaryClip(data)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadApps(selected: Set<String>): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val reverse = uiStore.accessControlReverse
            val sort = uiStore.accessControlSort
            val systemApp = uiStore.accessControlSystemApp

            val base = compareByDescending<AppInfo> { it.packageName in selected }
            val comparator = if (reverse) base.thenDescending(sort) else base.then(sort)

            val pm = packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

            packages.asSequence()
                .filter {
                    it.packageName != packageName
                }
                .filter {
                    it.applicationInfo != null
                }
                .filter {
                    it.requestedPermissions?.contains(INTERNET) == true || it.applicationInfo!!.uid < android.os.Process.FIRST_APPLICATION_UID
                }
                .filter {
                    systemApp || !it.isSystemApp
                }
                .map {
                    it.toAppInfo(pm)
                }
                .sortedWith(comparator)
                .toList()
        }

    private val PackageInfo.isSystemApp: Boolean
        get() {
            return applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
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
            // Persist the selection the moment it changes instead of
            // only in the exit defer: if the process dies while this
            // page is open (task swiped away, system reclaim), the
            // toggles used to be lost with it. The loaded flag guards
            // the initial composition, which runs before main() has
            // read the stored set into [selected].
            androidx.compose.runtime.LaunchedEffect(
                uiState.selectionTick,
                uiState.loaded,
            ) {
                if (uiState.loaded) {
                    val snapshot = selected.toSet()
                    withContext(Dispatchers.IO) {
                        serviceStore.accessControlPackages = snapshot
                    }
                }
            }

            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette, customSeed = customSeedColor(uiStore), animStyle = uiStore.animationStyle) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccessControlScreen(
                        state = uiState,
                        uiStore = uiStore,
                        selected = selected,
                        running = clashRunning,
                        onModeChange = { mode ->
                            uiState.mode = mode
                            serviceStore.accessControlMode = mode
                        },
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
