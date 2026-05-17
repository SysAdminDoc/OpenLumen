package com.openlumen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.presetDisplayName
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.LumenService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 4x1 home-screen widget — four preset chips mapped to the first four
 * entries of [Preferences.favoritePresetKeys]. Tap a chip to set that
 * preset as active.
 *
 * Tied to roadmap candidate **C20**. Shares the [ToggleWidget] refresh
 * pattern: `LumenService.observePreferences()` broadcasts
 * [ACTION_REFRESH] on every prefs emission so chip selection stays
 * coherent with the in-app state.
 */
@AndroidEntryPoint
class PresetWidget : AppWidgetProvider() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, PresetWidget::class.java)
            )
            if (ids.isNotEmpty()) onUpdate(context, mgr, ids)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 1-second budget mirrors ToggleWidget. If DataStore is slow, we
        // render the widget in its "no favorites" state and the next emission
        // brings the chips back.
        val snapshot: Preferences = runCatching {
            runBlocking {
                withTimeoutOrNull(1_000) { prefs.flow.first() }
            }
        }.getOrNull() ?: Preferences()
        appWidgetIds.forEach { id -> renderOne(context, appWidgetManager, id, snapshot) }
    }

    private fun renderOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        snapshot: Preferences
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_preset)

        val favoritesResolved = snapshot.favoritePresetKeys
            .asSequence()
            .mapNotNull { key -> Presets.byKey(key) }
            .take(SLOT_COUNT)
            .toList()

        for (slotIndex in 0 until SLOT_COUNT) {
            val slot = SLOTS[slotIndex]
            val entry = favoritesResolved.getOrNull(slotIndex)
            if (entry == null) {
                views.setViewVisibility(slot.containerId, View.GONE)
                continue
            }
            views.setViewVisibility(slot.containerId, View.VISIBLE)
            views.setTextViewText(slot.labelId, presetDisplayName(context, entry.key, entry.displayName))
            views.setInt(
                slot.swatchId,
                "setColorFilter",
                rgbToColor(entry.matrix.r, entry.matrix.g, entry.matrix.b)
            )

            val selectIntent = Intent(context, WidgetActionReceiver::class.java)
                .setAction(WidgetActionReceiver.ACTION_SET_PRESET)
                .putExtra(LumenService.EXTRA_PRESET_KEY, entry.key)
                // Distinct data URI per slot so PendingIntent.FLAG_UPDATE_CURRENT
                // doesn't collapse the four slots into one shared intent.
                .setData(android.net.Uri.parse("openlumen-preset://${entry.key}"))
            val pending = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST + slotIndex,
                selectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(slot.containerId, pending)
        }

        // If the user has no favorites at all, the chips are all hidden and
        // we surface a single tappable hint that opens the app to the
        // Presets screen so they can mark favorites. We don't deep-link
        // into Presets specifically; the user can tap the Presets tab.
        val hintVisible = favoritesResolved.isEmpty()
        views.setViewVisibility(R.id.widget_preset_hint, if (hintVisible) View.VISIBLE else View.GONE)
        val openAppIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openAppPending = PendingIntent.getActivity(
            context,
            BASE_REQUEST + SLOT_COUNT,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_preset_hint, openAppPending)

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    /** Pack a 0..1 float triplet into an opaque ARGB int. */
    private fun rgbToColor(r: Float, g: Float, b: Float): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    companion object {
        const val ACTION_REFRESH = "com.openlumen.widget.action.PRESET_REFRESH"

        private const val SLOT_COUNT = 4
        private const val BASE_REQUEST = 2000

        private data class Slot(val containerId: Int, val swatchId: Int, val labelId: Int)

        private val SLOTS: List<Slot> = listOf(
            Slot(R.id.widget_preset_slot0, R.id.widget_preset_swatch0, R.id.widget_preset_label0),
            Slot(R.id.widget_preset_slot1, R.id.widget_preset_swatch1, R.id.widget_preset_label1),
            Slot(R.id.widget_preset_slot2, R.id.widget_preset_swatch2, R.id.widget_preset_label2),
            Slot(R.id.widget_preset_slot3, R.id.widget_preset_swatch3, R.id.widget_preset_label3)
        )

        /** Mirror of [ToggleWidget.broadcastRefresh]. Cheap when no instances. */
        fun broadcastRefresh(context: Context) {
            val intent = Intent(context, PresetWidget::class.java)
                .setAction(ACTION_REFRESH)
            context.sendBroadcast(intent)
        }
    }
}
