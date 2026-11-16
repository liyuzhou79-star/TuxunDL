package com.tuxundl.android.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.tuxundl.android.core.model.TunnelState
import com.tuxundl.android.core.util.trafficTotal
import com.tuxundl.android.design.databinding.DesignAboutBinding
import com.tuxundl.android.design.databinding.DesignMainBinding
import com.tuxundl.android.design.util.layoutInflater
import com.tuxundl.android.design.util.resolveThemedColor
import com.tuxundl.android.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    sealed class Request {
        object ToggleStatus : Request()
        object OpenProxy : Request()
        object OpenProfiles : Request()
        object OpenProviders : Request()
        object OpenLogs : Request()
        object OpenSettings : Request()
        object OpenHelp : Request()
        object OpenAbout : Request()

        /** Switch the tunnel mode (session override), like ProxyDesign.PatchMode. */
        data class SetMode(val mode: TunnelState.Mode) : Request()
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}