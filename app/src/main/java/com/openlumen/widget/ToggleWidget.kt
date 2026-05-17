package com.openlumen.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.prefs.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 1x1 home-screen widget rendered with Glance.
 *
 * Tied to roadmap candidates C19 and C123. User actions still route through
 * [WidgetActionReceiver] so Android 15+ foreground-service start rejections
 * are handled by the existing recovery path.
 */
class ToggleWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ToggleGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            broadcastRefresh(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.openlumen.widget.action.REFRESH"

        /**
         * Helper for `LumenService` to nudge any installed widget instances
         * after a prefs emission. Cheap when no widgets are installed.
         */
        fun broadcastRefresh(context: Context) {
            WidgetUpdateScope.launch {
                ToggleGlanceWidget().updateAll(context.applicationContext)
            }
        }
    }
}

private class ToggleGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enabled = runCatching {
            withTimeoutOrNull(WIDGET_READ_TIMEOUT_MS) {
                PreferencesStore(context.applicationContext).flow.first().enabled
            } ?: false
        }.getOrDefault(false)

        val label = context.getString(if (enabled) R.string.tile_on else R.string.tile_off)
        val contentDescription = context.getString(R.string.home_toggle)
        val toggleAction = actionSendBroadcast(
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(WidgetActionReceiver.ACTION_TOGGLE)
        )
        val openAppAction = actionStartActivity<MainActivity>()

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetColors.Surface)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_lumen_tile),
                    contentDescription = contentDescription,
                    modifier = GlanceModifier
                        .size(32.dp)
                        .clickable(toggleAction)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = label,
                    modifier = GlanceModifier.clickable(openAppAction),
                    style = TextStyle(
                        color = WidgetColors.Text,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

internal object WidgetColors {
    val Surface = ColorProvider(Color(0xFF1E1E2E))
    val Text = ColorProvider(Color(0xFFCDD6F4))
    val MutedText = ColorProvider(Color(0xFFA6ADC8))
}

internal const val WIDGET_READ_TIMEOUT_MS = 1_000L

private val WidgetUpdateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
