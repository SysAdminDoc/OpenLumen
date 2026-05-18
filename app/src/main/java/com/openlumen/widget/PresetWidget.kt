package com.openlumen.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.presetDisplayName
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.LumenService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 4x1 home-screen widget rendered with Glance.
 *
 * Tied to roadmap candidates C20 and C123. The widget renders the first
 * four favorite presets as responsive Glance columns while preserving the
 * existing [WidgetActionReceiver.ACTION_SET_PRESET] action path.
 */
class PresetWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PresetGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            broadcastRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.openlumen.widget.action.PRESET_REFRESH"

        /** Mirror of [ToggleWidget.broadcastRefresh]. Cheap when no instances. */
        fun broadcastRefresh(context: Context) {
            PresetWidgetUpdateScope.launch {
                PresetGlanceWidget().updateAll(context.applicationContext)
            }
        }
    }
}

private class PresetGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching {
            withTimeoutOrNull(WIDGET_READ_TIMEOUT_MS) {
                PreferencesStore(context.applicationContext).flow.first()
            }
        }.getOrNull() ?: Preferences()

        val activePresetKey = snapshot.activePresetKey
        val slots = snapshot.favoritePresetKeys
            .asSequence()
            .mapNotNull { key -> Presets.byKey(key) }
            .take(SLOT_COUNT)
            .map { entry ->
                PresetSlotUi(
                    label = presetDisplayName(context, entry.key, entry.displayName),
                    color = Color(
                        red = entry.matrix.r.coerceIn(0f, 1f),
                        green = entry.matrix.g.coerceIn(0f, 1f),
                        blue = entry.matrix.b.coerceIn(0f, 1f),
                        alpha = 1f
                    ),
                    // C169: highlight the currently-active favorite so a
                    // user glancing at the widget can tell at a glance
                    // which preset is engaged without opening the app.
                    // Active matches only when the filter is on AND the
                    // active preset matches this slot's key — an "off"
                    // filter would otherwise paint the last-used preset
                    // as if it were the live one.
                    isActive = snapshot.enabled && entry.key == activePresetKey,
                    action = actionSendBroadcast(
                        Intent(context, WidgetActionReceiver::class.java)
                            .setAction(WidgetActionReceiver.ACTION_SET_PRESET)
                            .putExtra(LumenService.EXTRA_PRESET_KEY, entry.key)
                            .setData(Uri.parse("openlumen-preset://${entry.key}"))
                    )
                )
            }
            .toList()

        val openAppAction = actionStartActivity<MainActivity>()
        val hint = context.getString(R.string.widget_preset_hint)

        provideContent {
            if (slots.isEmpty()) {
                EmptyPresetWidget(hint = hint, openAppAction = openAppAction)
            } else {
                PresetSlots(slots = slots)
            }
        }
    }
}

private data class PresetSlotUi(
    val label: String,
    val color: Color,
    val isActive: Boolean,
    val action: Action
)

@androidx.compose.runtime.Composable
private fun EmptyPresetWidget(hint: String, openAppAction: Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .padding(8.dp)
            .clickable(openAppAction),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = hint,
            style = TextStyle(
                color = WidgetColors.MutedText,
                textAlign = TextAlign.Center
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun PresetSlots(slots: List<PresetSlotUi>) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        slots.forEach { slot ->
            // C169: the active chip gets a contrast-ring wrapper (Surface1
            // background under the colored swatch) and a bolder label so
            // the active preset reads at a glance. Inactive slots stay
            // visually minimal so the active slot stands out without
            // making the widget look noisy.
            val labelWeight = if (slot.isActive) FontWeight.Bold else FontWeight.Normal
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clickable(slot.action),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (slot.isActive) {
                    Box(
                        modifier = GlanceModifier
                            .size(24.dp)
                            .background(WidgetColors.ActiveRing),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(16.dp)
                                .background(ColorProvider(slot.color))
                        ) {}
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(20.dp)
                            .background(ColorProvider(slot.color))
                    ) {}
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = slot.label,
                    style = TextStyle(
                        color = if (slot.isActive) WidgetColors.Text else WidgetColors.MutedText,
                        textAlign = TextAlign.Center,
                        fontWeight = labelWeight
                    )
                )
            }
        }
    }
}

private const val SLOT_COUNT = 4

private val PresetWidgetUpdateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
