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

    /** Epoch millis of the last completed proxy delay test (single-node or whole-group); 0 = never tested. */
    var proxyLastTestAt: Long by store.long(
        key = "proxy_last_test_at",
        defaultValue = 0L
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

    /** Pinned profile uuids (as strings); pinned profiles sort to the top of the profiles list. */
    var pinnedProfiles: Set<String> by store.stringSet(
        key = "pinned_profiles",
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

    /**
     * Whether the 1.3.0 first-run onboarding flow has been completed.
     * Users who already accepted the disclaimer on an older version
     * count as completed (MainActivity treats them as such), so only
     * genuinely fresh installs see the flow.
     */
    var onboardingCompleted: Boolean by store.boolean(
        key = "onboarding_completed",
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

    /**
     * Home brick order as a comma-separated list of brick ids, set by
     * long-press drag on the home screen. Empty = the default set
     * (the five core bricks: node, proxy, profile, logs, settings).
     * Stored orders written by older versions list every brick the
     * user had and are honoured as-is.
     */
    var brickOrder: String by store.string(
        key = "brick_order",
        defaultValue = ""
    )

    /**
     * In-app language override as a BCP-47 tag ("" = follow system).
     * Applied by BaseActivity.attachBaseContext; changing it recreates
     * all activities.
     */
    var appLanguage: String by store.string(
        key = "app_language",
        defaultValue = ""
    )

    /** Whether the low-traffic subscription alert is on (off by default). */
    var trafficAlertEnabled: Boolean by store.boolean(
        key = "traffic_alert_enabled",
        defaultValue = false
    )

    /**
     * Haptic feedback for key actions (connect, disconnect, node
     * select, speed-test finish). On by default (1.2.5).
     */
    var hapticsEnabled: Boolean by store.boolean(
        key = "haptics_enabled",
        defaultValue = true
    )

    /** Haptic strength: 0 = light, 1 = medium, 2 = strong, 3 = custom (1.2.5, custom added 1.4.0). */
    var hapticsStrength: Int by store.int(
        key = "haptics_strength",
        defaultValue = 1
    )

    /** Custom haptic amplitude (1..255), used when strength is 3 (1.4.0). */
    var hapticsCustomAmplitude: Int by store.int(
        key = "haptics_custom_amplitude",
        defaultValue = 140
    )

    /** Remaining-traffic threshold (GiB) at which the alert fires. */
    var trafficAlertThresholdGb: Int by store.int(
        key = "traffic_alert_threshold_gb",
        defaultValue = 10
    )

    /**
     * True once the traffic alert has fired for the current total;
     * re-armed when the remaining traffic recovers well above the
     * threshold or when the total grows (renewal / profile switch).
     */
    var trafficAlertNotified: Boolean by store.boolean(
        key = "traffic_alert_notified",
        defaultValue = false
    )

    /** Last subscription total the alert check saw (renewal detection). */
    var trafficAlertLastTotal: Long by store.long(
        key = "traffic_alert_last_total",
        defaultValue = 0L
    )

    /**
     * Epoch day (UTC) of the first ever MainActivity launch; 0 = not yet
     * recorded. Drives the "days used" counter in the mascot easter egg.
     */
    var firstLaunchDay: Long by store.long(
        key = "first_launch_day",
        defaultValue = 0L
    )

    /** Whether the weekly automatic backup is on (off by default, 1.3.5). */
    var autoBackupEnabled: Boolean by store.boolean(
        key = "auto_backup_enabled",
        defaultValue = false
    )

    /** Epoch millis of the last completed automatic backup; 0 = never (1.3.5). */
    var lastAutoBackupAt: Long by store.long(
        key = "last_auto_backup_at",
        defaultValue = 0L
    )

    /** Whether the app lock is armed (1.5.0, privacy section). */
    var appLockEnabled: Boolean by store.boolean(
        key = "app_lock_enabled",
        defaultValue = false
    )

    /** App-lock kind: 0 = pattern, 1 = numeric PIN, 2 = password (1.5.0). */
    var appLockType: Int by store.int(
        key = "app_lock_type",
        defaultValue = 1
    )

    /**
     * Random salt (hex) for the app-lock secret hash (1.5.0). Only
     * the salt and the hash are ever stored — never the secret.
     */
    var appLockSalt: String by store.string(
        key = "app_lock_salt",
        defaultValue = ""
    )

    /** Salted SHA-256 (hex) of the app-lock secret (1.5.0). */
    var appLockHash: String by store.string(
        key = "app_lock_hash",
        defaultValue = ""
    )

    /**
     * Minutes the app may stay in the background before the lock
     * engages again; 0 = lock immediately (1.5.0).
     */
    var appLockRelockMinutes: Int by store.int(
        key = "app_lock_relock_minutes",
        defaultValue = 1
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"

        val Context.mainActivityAlias: ComponentName
            get() = ComponentName(this, "$packageName.MainActivityAlias")
    }
}