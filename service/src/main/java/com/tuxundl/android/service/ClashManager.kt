package com.tuxundl.android.service

import android.content.Context
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.*
import com.tuxundl.android.service.data.Selection
import com.tuxundl.android.service.data.SelectionDao
import com.tuxundl.android.service.remote.IClashManager
import com.tuxundl.android.service.remote.ILogObserver
import com.tuxundl.android.service.store.ServiceStore
import com.tuxundl.android.service.util.sendOverrideChanged
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File

class ClashManager(private val context: Context) : IClashManager,
    CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val store = ServiceStore(context)
    private var logReceiver: ReceiveChannel<LogMessage>? = null

    init {
        launch { torEgressWatchdog() }
    }

    /**
     * Tor egress fail-safe (1.7.3): while the session override arms
     * Tor egress, the tunnel's only way out is the Tor SOCKS port,
     * whose owner (the Tor engine) lives in the UI process and keeps
     * a heartbeat file fresh. If the heartbeat goes stale — UI
     * process killed, Tor gone — disarm the marker here in the
     * service process so the tunnel falls back to the profile's own
     * egress instead of aiming at a dead port forever. Failing back
     * is announced in the core log; it can never silently turn into
     * a direct connection the user did not choose, because the
     * restored state is exactly the pre-Tor configuration.
     */
    private suspend fun torEgressWatchdog() {
        val heartbeat = File(context.filesDir, "tor/heartbeat")
        while (true) {
            delay(5_000)
            try {
                val override = Clash.queryOverride(Clash.OverrideSlot.Session)
                val tor = override.tor
                if (tor != null && tor.enabled) {
                    val stale = !heartbeat.exists() ||
                        System.currentTimeMillis() - heartbeat.lastModified() > 20_000
                    if (stale) {
                        val restored = override.copy()
                        restored.tor = null
                        patchOverride(Clash.OverrideSlot.Session, restored)
                        Log.w("Tor heartbeat lost; Tor egress disarmed, profile egress restored")
                    }
                }
            } catch (e: Exception) {
                Log.w("Tor egress watchdog: $e")
            }
        }
    }

    override fun queryTunnelState(): TunnelState {
        return Clash.queryTunnelState()
    }

    override fun queryTrafficTotal(): Long {
        return Clash.queryTrafficTotal()
    }

    override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
        return Clash.queryGroupNames(excludeNotSelectable)
    }

    override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
        return Clash.queryGroup(name, proxySort)
    }

    override fun queryConfiguration(): UiConfiguration {
        return Clash.queryConfiguration()
    }

    override fun queryProviders(): ProviderList {
        return ProviderList(Clash.queryProviders())
    }

    override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
        return Clash.queryOverride(slot)
    }

    override fun patchSelector(group: String, name: String): Boolean {
        return Clash.patchSelector(group, name).also {
            val current = store.activeProfile ?: return@also

            if (it) {
                SelectionDao().setSelected(Selection(current, group, name))
            } else {
                SelectionDao().removeSelected(current, group)
            }
        }
    }

    override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
        Clash.patchOverride(slot, configuration)

        context.sendOverrideChanged()
    }

    override fun clearOverride(slot: Clash.OverrideSlot) {
        Clash.clearOverride(slot)
    }

    override suspend fun healthCheck(group: String, url: String) {
        return Clash.healthCheck(group, url).await()
    }

    override suspend fun healthCheckProxy(name: String, url: String) {
        return Clash.healthCheckProxy(name, url).await()
    }

    override suspend fun updateProvider(type: Provider.Type, name: String) {
        return Clash.updateProvider(type, name).await()
    }

    override fun setLogObserver(observer: ILogObserver?) {
        synchronized(this) {
            logReceiver?.apply {
                cancel()

                Clash.forceGc()
            }

            if (observer != null) {
                logReceiver = Clash.subscribeLogcat().also { c ->
                    launch {
                        try {
                            while (isActive) {
                                observer.newItem(c.receive())
                            }
                        } catch (e: CancellationException) {
                            // intended behavior
                            // ignore
                        } catch (e: Exception) {
                            Log.w("UI crashed", e)
                        } finally {
                            withContext(NonCancellable) {
                                c.cancel()

                                Clash.forceGc()
                            }
                        }
                    }
                }
            }
        }
    }
}