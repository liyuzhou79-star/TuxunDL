package com.tuxundl.android.design

import android.content.Context
import android.view.View
import com.tuxundl.android.design.databinding.DesignAppCrashedBinding
import com.tuxundl.android.design.util.applyFrom
import com.tuxundl.android.design.util.bindAppBarElevation
import com.tuxundl.android.design.util.layoutInflater
import com.tuxundl.android.design.util.root

class AppCrashedDesign(context: Context) : Design<Unit>(context) {
    private val binding = DesignAppCrashedBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    fun setAppLogs(logs: String) {
        binding.logsView.text = logs
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
    }
}