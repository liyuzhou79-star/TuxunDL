package com.tuxundl.android.design.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tuxundl.android.common.store.Store
import com.tuxundl.android.common.store.asStoreProvider
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.design.model.AppInfoSort
import com.tuxundl.android.design.model.DarkMode
import com.tuxundl.android.design.model.ThemePalette

class UiStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = DarkMode.Auto,
        values = DarkMode.values()
    )

    var themePalette: ThemePalette by store.enum(
        key = "theme_palette",
        defaultValue = ThemePalette.Default,
        values = ThemePalette.values()
    )

    var showTrafficCard: Boolean by store.boolean(
        key = "show_traffic_card",
        defaultValue = true
    )

    var showProxyCard: Boolean by store.boolean(
        key = "show_proxy_card",
        defaultValue = true
    )

    var showProfileCard: Boolean by store.boolean(
        key = "show_profile_card",
        defaultValue = true
    )

    var showProvidersCard: Boolean by store.boolean(
        key = "show_providers_card",
        defaultValue = true
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = context.packageManager.getComponentEnabledSetting(context.mainActivityAlias)
            .let { state ->
                state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                        state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            },
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    /** Favorite proxy nodes, stored as "group|node" keys; favorites pin to the top of their group. */
    var favoriteProxies: Set<String> by store.stringSet(
        key = "favorite_proxies",
        defaultValue = emptySet()
    )

    /** Epoch millis when the current connection started; 0 = not running. */
    var runningSince: Long by store.long(
        key = "running_since",
        defaultValue = 0L
    )

    /** Release version the user dismissed on the home update card; that version stays hidden. */
    var dismissedUpdate: String by store.string(
        key = "dismissed_update",
        defaultValue = ""
    )

    /** Whether the first-run disclaimer has been accepted; false shows the gate screen. */
    var disclaimerAccepted: Boolean by store.boolean(
        key = "disclaimer_accepted",
        defaultValue = false
    )

    var showTrafficMonitorCard: Boolean by store.boolean(
        key = "show_traffic_monitor_card",
        defaultValue = true
    )

    /**
     * Home card order as a comma-separated list of card keys
     * (traffic, proxy, profile, providers, monitor). Empty = default order.
     */
    var homeCardOrder: String by store.string(
        key = "home_card_order",
        defaultValue = ""
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"

        val Context.mainActivityAlias: ComponentName
            get() = ComponentName(this, "com.tuxundl.android.MainActivityAlias")
    }
}