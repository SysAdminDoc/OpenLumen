package com.openlumen.service

import android.content.Context
import android.util.Log
import com.openlumen.prefs.Preferences
import com.openlumen.widget.PresetWidget
import com.openlumen.widget.ToggleWidget
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns home-screen widget refresh diffing for the foreground service.
 */
internal class WidgetBridge(
    private val context: Context,
    private val logTag: String
) {
    private val lastSnapshot = AtomicReference<WidgetSnapshot?>(null)

    fun maybeBroadcastRefresh(prefs: Preferences) {
        if (!shouldBroadcastFor(prefs)) return
        runCatching { ToggleWidget.broadcastRefresh(context) }
            .onFailure { Log.w(logTag, "ToggleWidget broadcast failed: ${it.message}") }
        runCatching { PresetWidget.broadcastRefresh(context) }
            .onFailure { Log.w(logTag, "PresetWidget broadcast failed: ${it.message}") }
    }

    internal fun shouldBroadcastFor(prefs: Preferences): Boolean {
        val snapshot = WidgetSnapshot.from(prefs)
        return lastSnapshot.getAndSet(snapshot) != snapshot
    }
}

/**
 * Subset of [Preferences] fields the home-screen widgets render. Equality
 * gates refresh broadcasts on no-op-for-widget preference emissions.
 */
internal data class WidgetSnapshot(
    val enabled: Boolean,
    val activePresetKey: String,
    val favoritePresetKeys: List<String>
) {
    companion object {
        fun from(prefs: Preferences): WidgetSnapshot = WidgetSnapshot(
            enabled = prefs.enabled,
            activePresetKey = prefs.activePresetKey,
            favoritePresetKeys = prefs.favoritePresetKeys
        )
    }
}
