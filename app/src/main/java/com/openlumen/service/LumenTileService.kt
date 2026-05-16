package com.openlumen.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
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
            val enabled = try { prefs.flow.first().enabled } catch (_: Throwable) { false }
            qsTile?.apply {
                state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        super.onDestroy()
    }
}
