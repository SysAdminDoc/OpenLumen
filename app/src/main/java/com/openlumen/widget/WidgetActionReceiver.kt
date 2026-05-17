package com.openlumen.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlumen.engine.Presets
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.PresetCycle
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                when (intent.action) {
                    ACTION_TOGGLE -> toggle(context)
                    ACTION_SET_PRESET -> setPreset(context, intent.getStringExtra(LumenService.EXTRA_PRESET_KEY))
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Widget action failed: ${t.message}", t)
            } finally {
                refreshWidgets(context)
                pending.finish()
            }
        }
    }

    private suspend fun toggle(context: Context) {
        var toggledTo = false
        prefs.update { current ->
            toggledTo = !current.enabled
            current.copy(enabled = toggledTo)
        }

        if (toggledTo) {
            val result = LumenServiceStarter.start(context, logTag = TAG)
            if (!result.started) {
                prefs.update { it.copy(enabled = false) }
                if (result.foregroundStartNotAllowed) {
                    LumenServiceStarter.openAppAfterBlockedStart(context, TAG, "widget_toggle")
                }
            }
        } else {
            runCatching { context.stopService(Intent(context, LumenService::class.java)) }
                .onFailure { Log.w(TAG, "Widget stopService failed: ${it.message}", it) }
        }
    }

    private suspend fun setPreset(context: Context, rawKey: String?) {
        val key = rawKey
            ?.takeIf { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }
            ?.takeIf { it == "custom" || Presets.byKey(it) != null }
            ?: return

        var shouldStartService = false
        prefs.update { current ->
            shouldStartService = current.enabled
            PresetCycle.setActiveKey(current, key)
        }
        if (!shouldStartService) return

        val result = LumenServiceStarter.start(
            context = context,
            intent = Intent(context, LumenService::class.java).setAction(LumenService.ACTION_REEVALUATE),
            logTag = TAG
        )
        if (!result.started && result.foregroundStartNotAllowed) {
            LumenServiceStarter.openAppAfterBlockedStart(context, TAG, "widget_preset")
        }
    }

    private fun refreshWidgets(context: Context) {
        runCatching { ToggleWidget.broadcastRefresh(context) }
        runCatching { PresetWidget.broadcastRefresh(context) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.openlumen.widget.action.TOGGLE"
        const val ACTION_SET_PRESET = "com.openlumen.widget.action.SET_PRESET"
        private const val TAG = "OpenLumen/WidgetAction"
    }
}
