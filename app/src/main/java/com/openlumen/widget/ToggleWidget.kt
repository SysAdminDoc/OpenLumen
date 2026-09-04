package com.openlumen.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.ui.theme.Catppuccin
import com.openlumen.ui.theme.Latte
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
                widgetPreferencesStore(context).flow.first().enabled
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
                    .cornerRadius(WidgetColors.CornerRadius)
                    .background(WidgetColors.Surface)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_lumen_tile),
                    contentDescription = contentDescription,
                    modifier = GlanceModifier
                        .size(48.dp)
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

/**
 * Widget colours, per launcher theme.
 *
 * These were fixed Mocha, so a light launcher got two dark tiles sitting in a
 * pale tray. Glance's two-argument ColorProvider picks by the host's own
 * configuration, which is the launcher's, not the app's.
 *
 * The active ring is Mauve rather than a surface tone: as Surface1 on Base it
 * was about 1.6:1 against the tile, which is not a ring anyone can see.
 */
internal object WidgetColors {
    val Surface = ColorProvider(Latte.Base, Catppuccin.Base)
    val Text = ColorProvider(Latte.Text, Catppuccin.Text)
    val MutedText = ColorProvider(Latte.Subtext0, Catppuccin.Subtext0)
    val ActiveRing = ColorProvider(Latte.Mauve, Catppuccin.Mauve)

    /** Rounded corners on every filled box, so the tiles are not raw squares. */
    val CornerRadius = 12.dp
    val SwatchCornerRadius = 6.dp
}

internal const val WIDGET_READ_TIMEOUT_MS = 1_000L

private val WidgetUpdateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
