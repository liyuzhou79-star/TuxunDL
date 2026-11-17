package com.tuxundl.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.tuxundl.android.common.constants.Intents
import com.tuxundl.android.design.store.UiStore
import com.tuxundl.android.remote.StatusClient

/**
 * Home-screen widget: TuxunDL logo, running state, current node, and live
 * up/down rates. Tapping the toggle button starts/stops the proxy through
 * the same [ExternalControlActivity] intent the launcher shortcuts use;
 * tapping anywhere else opens the app.
 *
 * State comes from [StatusClient] (authoritative running state, works while
 * the app is closed) plus the last-known node/rates MainActivity persists
 * into [UiStore] while it is open. The widget never talks to the Binder
 * directly: the service is only bound while the app is visible, so a
 * Binder call from here could wait forever.
 */
class TuxunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        /** Refresh every placed widget (safe to call from any thread). */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TuxunWidgetProvider::class.java),
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val uiStore = UiStore(context)
            val running = StatusClient(context).currentProfile() != null
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250,
            )
            // Narrow (2-cell) placements get the compact layout without rates.
            val compact = minWidth < 220
            val views = RemoteViews(
                context.packageName,
                if (compact) R.layout.widget_tuxun_small else R.layout.widget_tuxun,
            )

            val toggleIntent = Intent(Intents.ACTION_TOGGLE_CLASH)
                .setClassName(context, ExternalControlActivity::class.java.name)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            val togglePending = PendingIntent.getActivity(
                context, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, togglePending)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            views.setTextViewText(
                R.id.widget_status,
                context.getString(
                    if (running) R.string.widget_running else R.string.widget_stopped,
                ),
            )
            views.setTextViewText(
                R.id.widget_node,
                uiStore.widgetNodeName.ifEmpty {
                    context.getString(R.string.widget_no_node)
                },
            )
            views.setImageViewResource(
                R.id.widget_dot,
                if (running) R.drawable.widget_dot_running
                else R.drawable.widget_dot_stopped,
            )

            if (!compact) {
                // Rates are only meaningful while running and freshly sampled.
                val fresh = running &&
                    System.currentTimeMillis() - uiStore.widgetUpdatedAt < 30_000L
                val up = if (fresh) formatRate(uiStore.widgetUpRate) else "—"
                val down = if (fresh) formatRate(uiStore.widgetDownRate) else "—"
                views.setTextViewText(R.id.widget_rates, "↑ $up  ↓ $down")
            }

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun formatRate(bytesPerSecond: Long): String = when {
            bytesPerSecond >= 1024L * 1024 * 1024 ->
                String.format("%.2f GiB/s", bytesPerSecond / (1024.0 * 1024 * 1024))
            bytesPerSecond >= 1024L * 1024 ->
                String.format("%.2f MiB/s", bytesPerSecond / (1024.0 * 1024))
            bytesPerSecond >= 1024L ->
                String.format("%.2f KiB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}
