package com.tuxundl.android.design.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tuxundl.android.common.store.Store
import com.tuxundl.android.common.store.asStoreProvider
import com.tuxundl.android.core.model.ProxySort
import com.tuxundl.android.design.model.AnimationStyle
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

    /**
     * Whole-app animation style (Appearance settings): Spring = full
     * springy motion, Soft = calmer and quicker, Reduced = fades only.
     * Read by every screen through the theme's composition local.
     */
    var animationStyle: AnimationStyle by store.enum(
        key = "animation_style",
        defaultValue = AnimationStyle.Spring,
        values = AnimationStyle.values()
    )

    /**
     * Custom theme seed colour as an ARGB int; 0 = not set, use [themePalette].
     * Picking a custom colour overrides the preset palette until a preset
     * is picked again (which clears this back to 0).
     */
    var customThemeColor: Int by store.int(
        key = "custom_theme_color",
        defaultValue = 0
    )

    /** Last-known state for the home-screen widget (written by MainActivity). */
    var widgetRunning: Boolean by store.boolean(
        key = "widget_running",
        defaultValue = false
    )

    /** Current node name for the home-screen widget; "" = none selected. */
    var widgetNodeName: String by store.string(
        key = "widget_node_name",
        defaultValue = ""
    )

    /** Last sampled up/down rates (bytes/s) for the home-screen widget. */
    var widgetUpRate: Long by store.long(
        key = "widget_up_rate",
        defaultValue = 0L
    )

    var widgetDownRate: Long by store.long(
        key = "widget_down_rate",
        defaultValue = 0L
    )

    /** Epoch millis of the last widget rate sample. */
    var widgetUpdatedAt: Long by store.long(
        key = "widget_updated_at",
        defaultValue = 0L
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

    /** Log viewer level filter ("all" / "error" / "warning" / "info"); persists across restarts. */
    var logLevelFilter: String by store.string(
        key = "log_level_filter",
        defaultValue = "all"
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
            get() = ComponentName(this, "$packageName.MainActivityAlias")
    }
}