package com.openlumen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.LumenService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 1x1 home-screen widget — single button that toggles the filter on/off.
 *
 * Tied to roadmap candidate **C19**. The widget reuses the existing
 * `LumenService.ACTION_TOGGLE` intent, so its behavior matches the Quick
 * Settings tile exactly.
 *
 * Refresh model:
 * - System fires [onUpdate] when the widget is added, when the host
 *   re-binds, and every [updatePeriodMillis] (set to 30 min in
 *   `widget_toggle_info.xml`, which is the platform minimum).
 * - [LumenService.observePreferences] broadcasts [ACTION_REFRESH] on every
 *   prefs emission, so the visible state stays in sync with the toggle in
 *   the app and the tile within tens of milliseconds.
 * - [onReceive] picks up [ACTION_REFRESH] and routes it back through
 *   [onUpdate] for the currently-installed widget instances.
 */
@AndroidEntryPoint
class ToggleWidget : AppWidgetProvider() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ToggleWidget::class.java)
            )
            if (ids.isNotEmpty()) onUpdate(context, mgr, ids)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // BroadcastReceivers have a 10-second budget. We do one cheap read,
        // with a 1-second cap so a misbehaving DataStore never wedges the
        // receiver. On timeout we render the "off" state, which is the
        // conservative default.
        val enabled = runCatching {
            runBlocking {
                withTimeoutOrNull(1_000) { prefs.flow.first().enabled } ?: false
            }
        }.getOrDefault(false)

        appWidgetIds.forEach { id -> renderOne(context, appWidgetManager, id, enabled) }
    }

    private fun renderOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        enabled: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_toggle)
        views.setTextViewText(
            R.id.widget_label,
            context.getString(if (enabled) R.string.tile_on else R.string.tile_off)
        )

        // The button click routes through LumenService.ACTION_TOGGLE — same
        // path the QS tile uses. We don't open the app on tap because that
        // defeats the purpose of a 1x1 toggle.
        val toggleIntent = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_TOGGLE)
        val togglePending = PendingIntent.getService(
            context,
            REQUEST_TOGGLE,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, togglePending)

        // Long-press hint: an explicit secondary action would be nicer, but
        // RemoteViews has no long-press affordance prior to Compose for
        // widgets. We make the label area open the app as a discoverability
        // fallback.
        val tapIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tapPending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_label, tapPending)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.openlumen.widget.action.REFRESH"
        private const val REQUEST_TOGGLE = 1001
        private const val REQUEST_OPEN = 1002

        /**
         * Helper for `LumenService` to nudge any installed widget instances
         * after a prefs emission. Cheap when no widgets are installed.
         */
        fun broadcastRefresh(context: Context) {
            val intent = Intent(context, ToggleWidget::class.java)
                .setAction(ACTION_REFRESH)
            context.sendBroadcast(intent)
        }
    }
}
