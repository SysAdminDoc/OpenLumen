package com.openlumen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.openlumen.service.AutomationReceiver
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import com.openlumen.widget.WidgetActionReceiver

/**
 * Launcher long-press shortcuts.
 *
 * A static shortcut has to point at an activity, so this is the smallest one
 * that can exist: it dispatches and finishes without ever laying out. That
 * matters most for the emergency shortcut, which has to work when the screen
 * is unusable enough that reading an adb command off it is not an option.
 *
 * Nothing here decides anything. Each action forwards to the component that
 * already owns that behaviour, so a shortcut cannot drift from what the tile,
 * the notification and the documented escape hatch do.
 */
class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { dispatch(intent?.action) }
            .onFailure { Log.e(TAG, "Shortcut ${intent?.action} failed: ${it.message}", it) }
        finish()
    }

    private fun dispatch(action: String?) {
        when (action) {
            ACTION_TOGGLE -> sendBroadcast(
                // The same receiver the widgets use, which performs the same
                // preference write as the quick-settings tile.
                Intent(this, WidgetActionReceiver::class.java)
                    .setAction(WidgetActionReceiver.ACTION_TOGGLE)
            )

            ACTION_NEXT_PRESET -> LumenServiceStarter.start(
                context = this,
                intent = Intent(this, LumenService::class.java)
                    .setAction(LumenService.ACTION_CYCLE_PRESET),
                logTag = TAG
            )

            // The documented escape hatch, unchanged: the one action that
            // never asks for an automation token, and that clears the display
            // even when the service cannot be started.
            ACTION_EMERGENCY_OFF -> sendBroadcast(
                Intent(this, AutomationReceiver::class.java)
                    .setAction(LumenService.ACTION_TURN_OFF)
            )

            else -> Log.w(TAG, "Unknown shortcut action: $action")
        }
    }

    companion object {
        private const val TAG = "OpenLumen/Shortcut"

        const val ACTION_TOGGLE = "com.openlumen.shortcut.TOGGLE"
        const val ACTION_NEXT_PRESET = "com.openlumen.shortcut.NEXT_PRESET"
        const val ACTION_EMERGENCY_OFF = "com.openlumen.shortcut.EMERGENCY_OFF"

        /** Every action the shortcuts resource is allowed to send. */
        val ACTIONS: List<String> = listOf(ACTION_TOGGLE, ACTION_NEXT_PRESET, ACTION_EMERGENCY_OFF)

        fun intentFor(context: Context, action: String): Intent =
            Intent(context, ShortcutActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
