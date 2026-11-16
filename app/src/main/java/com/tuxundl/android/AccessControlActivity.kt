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

    override suspend fun main() {
        val service = ServiceStore(this)

        val stored = withContext(Dispatchers.IO) {
            service.accessControlPackages.toMutableSet()
        }

        selected.clear()
        selected.addAll(stored)

        defer {
            withContext(Dispatchers.IO) {
                val changed = selected != service.accessControlPackages
                service.accessControlPackages = selected
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
            TuxunDLTheme(darkTheme = darkTheme, palette = uiStore.themePalette) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccessControlScreen(
                        state = uiState,
                        uiStore = uiStore,
                        selected = selected,
                        onRequest = { request -> uiRequests.trySend(request) },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
