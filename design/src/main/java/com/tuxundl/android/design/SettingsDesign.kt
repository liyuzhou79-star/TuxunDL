package com.tuxundl.android.design

import android.content.Context
import android.view.View
import com.tuxundl.android.design.databinding.DesignSettingsBinding
import com.tuxundl.android.design.util.applyFrom
import com.tuxundl.android.design.util.bindAppBarElevation
import com.tuxundl.android.design.util.layoutInflater
import com.tuxundl.android.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartNetwork, StartOverride, StartMetaFeature, StartAppearance, StartRules, CheckUpdate,
        StartAccessControl, StartCustomDns, StartBackup,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}