package com.openlumen.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile — one-tap toggle from the system shade.
 * CF.Lumen never shipped a tile. We do.
 */
@AndroidEntryPoint
class LumenTileService : TileService() {

    @Inject lateinit var prefs: PreferencesStore

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val next = !(prefs.flow.first().enabled)
            prefs.update { it.copy(enabled = next) }
            if (next) {
                val intent = Intent(this@LumenTileService, LumenService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            refreshTile()
        }
    }

    private fun refreshTile() {
        scope.launch {
            val enabled = prefs.flow.first().enabled
            qsTile?.apply {
                state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }
}
