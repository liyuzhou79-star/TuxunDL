package com.tuxundl.android.tor

import android.content.Context
import com.tuxundl.android.core.Clash
import com.tuxundl.android.core.model.ConfigurationOverride
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whole-device Tor mode (1.7.3): the state machine that points
 * the main tunnel at the Tor engine — and back.
 *
 * The ordering is the safety property, and it is enforced here,
 * in one place:
 *
 *   connect:  Tor starts → bootstrap 100% → the exit is verified
 *             to be Tor (IsTor via Tor's own check service) → only
 *             then is the egress marker written into the session
 *             override, which makes the core reload with GLOBAL
 *             reduced to the Tor SOCKS proxy alone. Until that
 *             moment the tunnel keeps whatever egress it had;
 *             there is no window where traffic is told "Tor" but
 *             sent anywhere else.
 *   disconnect / death: the marker is removed first (the core
 *             reloads back to the profile's own egress), Tor is
 *             stopped second. If the tor process dies on its own,
 *             [TorManager.onUnexpectedExit] lands here and does
 *             the same cut-back immediately; if this whole UI
 *             process dies, the tunnel is left pointing at a dead
 *             SOCKS port — connections fail, nothing goes direct —
 *             and the service-process watchdog disarms the marker
 *             from the heartbeat within seconds.
 */
object TorEgress {

    enum class Phase {
        OFF,
        STARTING,
        VERIFYING,
        ARMING,
        ACTIVE,
        FAILED,
        INTERRUPTED
    }

    data class State(
        val phase: Phase = Phase.OFF,
        val message: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connectSession = 0

    val active: Boolean
        get() = _state.value.phase == Phase.ACTIVE

    init {
        TorManager.onUnexpectedExit = {
            if (active) {
                scope.launch {
                    disarm()
                    _state.value = State(
                        Phase.INTERRUPTED,
                        "Tor 进程意外退出，全机流量已切回原来的出口"
                    )
                }
            }
        }
    }

    /**
     * Drive the full connect sequence. [ensureTunnel] is supplied
     * by the page: it makes sure the main tunnel is running
     * (profile present, VPN permission granted, service started)
     * and reports whether that worked.
     */
    fun connect(
        context: Context,
        mode: TorManager.Mode,
        customText: String,
        ensureTunnel: suspend () -> Boolean
    ) {
        if (_state.value.phase == Phase.ACTIVE ||
            _state.value.phase == Phase.STARTING ||
            _state.value.phase == Phase.VERIFYING ||
            _state.value.phase == Phase.ARMING
        ) {
            return
        }
        val appContext = context.applicationContext
        val sid = ++connectSession
        scope.launch {
            try {
                // 1) Tor itself first. If it is already up (a stall
                // recovered, a retry after a failed flip), skip
                // straight to verification.
                if (!TorManager.state.value.running) {
                    _state.value = State(Phase.STARTING, "正在启动 Tor…")
                    TorManager.start(appContext, mode, customText)
                } else {
                    _state.value = State(Phase.STARTING, "Tor 正在连接…")
                }
                if (!awaitTorConnected(sid)) {
                    if (sid == connectSession) {
                        _state.value = State(
                            Phase.FAILED,
                            TorManager.state.value.status
                        )
                    }
                    return@launch
                }
                if (sid != connectSession) return@launch

                // 2) Prove the exit is Tor before touching egress.
                _state.value = State(Phase.VERIFYING, "Tor 已连通，正在验证出口…")
                if (!TorManager.verifyExit()) {
                    TorManager.stop()
                    if (sid == connectSession) {
                        _state.value = State(
                            Phase.FAILED,
                            "出口验证未通过：没能确认流量真的经 Tor，已取消切换（全机流量没有动）"
                        )
                    }
                    return@launch
                }
                if (sid != connectSession) return@launch

                // 3) Arm: the marker makes the next config load
                // exit via Tor only. Arming before the tunnel
                // (re)starts means even its first load is Tor-only.
                _state.value = State(Phase.ARMING, "正在把全机流量切到 Tor…")
                arm()

                // 4) The tunnel itself must be up to carry it.
                if (!ensureTunnel()) {
                    disarm()
                    TorManager.stop()
                    if (sid == connectSession) {
                        _state.value = State(
                            Phase.FAILED,
                            "主隧道没有启动起来（没有可用配置，或未授予 VPN 权限），已取消切换"
                        )
                    }
                    return@launch
                }

                // 5) Confirm the running core really flipped to
                // global (Tor-only) before claiming success.
                if (!confirmTorMode()) {
                    disarm()
                    if (sid == connectSession) {
                        _state.value = State(
                            Phase.FAILED,
                            "隧道没能切到洋葱出口，已恢复原来的出口"
                        )
                    }
                    return@launch
                }
                if (sid == connectSession) {
                    _state.value = State(Phase.ACTIVE)
                }
            } catch (e: Exception) {
                if (sid == connectSession) {
                    _state.value = State(Phase.FAILED, "切换失败：${e.message}")
                }
            }
        }
    }

    /** Poll TorManager until bootstrap completes or the attempt dies. */
    private suspend fun awaitTorConnected(sid: Int): Boolean {
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            if (sid != connectSession) return false
            val snapshot = TorManager.state.value
            if (snapshot.bootstrap >= 100) return true
            if (!snapshot.running && snapshot.bootstrap < 0) return false
            delay(300)
        }
        return TorManager.state.value.bootstrap >= 100
    }

    /** Poll the core until the reloaded config reports global mode. */
    private suspend fun confirmTorMode(): Boolean {
        repeat(30) {
            val mode = try {
                withClash { queryTunnelState().mode }
            } catch (e: Exception) {
                null
            }
            if (mode == TunnelState.Mode.Global) return true
            delay(300)
        }
        return false
    }

    /** User-initiated disconnect: egress back first, Tor second. */
    fun disconnect() {
        connectSession++
        scope.launch {
            disarm()
            TorManager.stop()
            _state.value = State(Phase.OFF)
        }
    }

    /**
     * Leave Tor mode from elsewhere (home mode selector): the
     * disarm is written before this returns, so a mode patch that
     * follows can never resurrect the marker from a stale snapshot.
     */
    suspend fun exitTorMode() {
        connectSession++
        disarm()
        TorManager.stop()
        _state.value = State(Phase.OFF)
    }

    /**
     * Housekeeping for page entry: if a previous UI process armed
     * egress and died before anyone cleaned up, and Tor is not
     * running now, clear the stale marker (the service watchdog
     * would catch it too; this just makes the page honest sooner).
     */
    fun sweepStaleArm() {
        if (active || TorManager.state.value.running) return
        scope.launch {
            try {
                val armed = withClash {
                    queryOverride(Clash.OverrideSlot.Session).tor?.enabled == true
                }
                if (armed) disarm()
            } catch (_: Exception) {
                // No remote service yet: nothing is routing either.
            }
        }
    }

    private suspend fun arm() {
        withClash {
            val override = queryOverride(Clash.OverrideSlot.Session)
            override.tor = ConfigurationOverride.TorEgress(
                enabled = true,
                socksPort = TorManager.SOCKS_PORT
            )
            patchOverride(Clash.OverrideSlot.Session, override)
        }
    }

    private suspend fun disarm() {
        withClash {
            val override = queryOverride(Clash.OverrideSlot.Session)
            if (override.tor != null) {
                override.tor = null
                patchOverride(Clash.OverrideSlot.Session, override)
            }
        }
    }
}
