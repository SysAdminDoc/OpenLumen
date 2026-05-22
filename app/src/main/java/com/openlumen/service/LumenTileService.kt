package com.openlumen.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.presetDisplayName
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.toggledFilterEnabled
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile — one-tap toggle from the system shade.
 *
 * Lifecycle: a new TileService is created per listening session. We use a fresh
 * CoroutineScope tied to onCreate→onDestroy so async work doesn't leak across
 * sessions. The toggle uses prefs.update {} (atomic with respect to the stored value)
 * rather than a read-then-write, so rapid double-taps never observe inconsistent state.
 *
 * Subtitle (API 29+) shows the active preset name when the filter is on so the
 * tile communicates *what* is engaged, not just whether something is engaged.
 * Tied to roadmap candidate C18. Long-press routes to MainActivity via the
 * manifest's `PREFERENCES_ACTIVITY` meta-data (C17).
 */
@AndroidEntryPoint
class LumenTileService : TileService() {

    private val tag = "OpenLumen/Tile"

    @Inject lateinit var prefs: PreferencesStore

    private var scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // If `onDestroy` didn't run (rare but documented on some OEMs that
        // skip the call when ripping a tile binding), cancel the prior scope
        // before swapping so its in-flight work doesn't leak past this
        // binding.
        (scope.coroutineContext[Job])?.cancel()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            try {
                var toggledTo = false
                prefs.update { current ->
                    toggledTo = !current.enabled
                    current.toggledFilterEnabled()
                }
                if (toggledTo) {
                    val result = LumenServiceStarter.start(this@LumenTileService, logTag = tag)
                    if (!result.started) {
                        prefs.update { it.copy(enabled = false) }
                        if (result.foregroundStartNotAllowed) {
                            openAppAfterBlockedStart()
                        }
                    }
                }
                refreshTile()
            } catch (t: Throwable) {
                Log.e(tag, "tile onClick failed: ${t.message}", t)
            }
        }
    }

    private fun refreshTile() {
        scope.launch {
            val snapshot: Preferences? = try { prefs.flow.first() } catch (_: Throwable) { null }
            try {
                qsTile?.apply {
                    val enabled = snapshot?.enabled == true
                    state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    // Tile.subtitle landed in API 29. On older API it's silently
                    // ignored, so the version gate is for *intent*, not safety.
                    if (Build.VERSION.SDK_INT >= 29) {
                        subtitle = subtitleFor(snapshot)
                    }
                    updateTile()
                }
            } catch (t: Throwable) {
                // Calling updateTile() outside an active onStartListening
                // window throws on some OEM forks; swallow rather than
                // crashing the service process.
                Log.w(tag, "qsTile update failed: ${t.message}")
            }
        }
    }

    private fun subtitleFor(p: Preferences?): CharSequence {
        if (p == null || !p.enabled) {
            return getString(R.string.tile_subtitle_off)
        }
        val presetName = Presets.byKey(p.activePresetKey)
            ?.let { presetDisplayName(this, it.key, it.displayName) }
            ?: p.activePresetKey.replaceFirstChar { it.uppercaseChar() }
        return getString(R.string.tile_subtitle_on, presetName)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppAfterBlockedStart() {
        val intent = LumenServiceStarter.blockedStartIntent(this, "tile")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    REQUEST_BLOCKED_START,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure {
            Log.w(tag, "Could not open app after blocked tile start: ${it.message}", it)
        }
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_BLOCKED_START = 2101
    }
}
