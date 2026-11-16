package com.tuxundl.android.design

import android.content.Context
import android.view.View
import com.tuxundl.android.design.databinding.DesignSettingsCommonBinding
import com.tuxundl.android.design.model.Behavior
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.preference.*
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.design.util.applyFrom
import com.tuxundl.android.design.util.bindAppBarElevation
import com.tuxundl.android.design.util.layoutInflater
import com.tuxundl.android.design.util.root
import com.tuxundl.android.service.store.ServiceStore

class AppSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    behavior: Behavior,
    running: Boolean,
    onHideIconChange: (hide: Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            category(R.string.behavior)

            switch(
                value = behavior::autoRestart,
                icon = R.drawable.ic_baseline_restore,
                title = R.string.auto_restart,
                summary = R.string.allow_clash_auto_restart,
            )

            category(R.string.interface_)

            selectableList(
                value = uiStore::darkMode,
                values = DarkMode.values(),
                valuesText = arrayOf(
                    R.string.follow_system_android_10,
                    R.string.always_light,
                    R.string.always_dark
                ),
                icon = R.drawable.ic_baseline_brightness_4,
                title = R.string.dark_mode
            ) {
                listener = OnChangedListener {
                    requests.trySend(Request.ReCreateAllActivities)
                }
            }

            switch(
                value = uiStore::hideAppIcon,
                icon = R.drawable.ic_baseline_hide,
                title = R.string.hide_app_icon_title,
                summary = R.string.hide_app_icon_desc,
            ) {
                listener = OnChangedListener {
                    onHideIconChange(uiStore::hideAppIcon.get())
                }
            }

            switch(
                value = uiStore::hideFromRecents,
                icon = R.drawable.ic_baseline_stack,
                title = R.string.hide_from_recents_title,
                summary = R.string.hide_from_recents_desc,
            ) {
                listener = OnChangedListener {
                    requests.trySend(Request.ReCreateAllActivities)
                }
            }

            category(R.string.service)

            switch(
                value = srvStore::dynamicNotification,
                icon = R.drawable.ic_baseline_domain,
                title = R.string.show_traffic,
                summary = R.string.show_traffic_summary
            ) {
                enabled = !running
            }
        }

        binding.content.addView(screen.root)
    }
}