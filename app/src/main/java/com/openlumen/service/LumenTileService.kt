package com.openlumen.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
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
        // Replace any stale scope from a previous binding.
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
                    current.copy(enabled = toggledTo)
                }
                if (toggledTo) {
                    val intent = Intent(this@LumenTileService, LumenService::class.java)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    } catch (t: Throwable) {
                        Log.e(tag, "startForegroundService failed: ${t.message}", t)
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
        }
    }

    private fun subtitleFor(p: Preferences?): CharSequence {
        if (p == null || !p.enabled) {
            return getString(R.string.tile_subtitle_off)
        }
        val presetName = Presets.byKey(p.activePresetKey)?.displayName
            ?: p.activePresetKey.replaceFirstChar { it.uppercaseChar() }
        return getString(R.string.tile_subtitle_on, presetName)
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        super.onDestroy()
    }
}
