package com.openlumen.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.openlumen.PresetKeyResolver
import com.openlumen.R
import com.openlumen.engine.Presets
import com.openlumen.presetDisplayName
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.PresetCycle
import com.openlumen.prefs.touchPreset
import com.openlumen.prefs.withFilterEnabled
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The preset a favourite-slot tile stands for: the [slot]th favourite, counted
 * from 1, after the same cleanup the preset cycle does. Null when the user has
 * fewer favourites than that, which shows the tile as unavailable (#32).
 */
internal fun presetSlotKey(
    p: Preferences,
    slot: Int,
    isKnown: (String) -> Boolean = PresetKeyResolver::isKnown
): String? = p.favoritePresetKeys.filter(isKnown).distinct().getOrNull(slot - 1)

/** A slot tile is lit while the filter is on and showing that slot's preset. */
internal fun presetSlotIsActive(p: Preferences, key: String): Boolean =
    p.enabled && p.activePresetKey == key && key != Preferences.OFF_PRESET_KEY

/**
 * Tapping a slot tile: switch to its preset and turn the filter on, or turn
 * the filter off when that preset is already showing, so one tile works as
 * an on/off switch for its preset. A slot holding the Off preset turns the
 * filter off, which is what choosing Off means.
 */
internal fun Preferences.afterPresetSlotTap(
    key: String,
    isKnown: (String) -> Boolean = PresetKeyResolver::isKnown
): Preferences {
    if (key == Preferences.OFF_PRESET_KEY || presetSlotIsActive(this, key)) {
        return withFilterEnabled(false)
    }
    if (!isKnown(key)) return this
    return PresetCycle.setActiveKey(this, key, isKnown)
        .touchPreset(key)
        .withFilterEnabled(true)
}

/** The grayscale tile is lit while the filter is on with grayscale under it. */
internal fun grayscaleTileActive(p: Preferences): Boolean = p.enabled && p.grayscale

/**
 * Tapping the grayscale tile: drop the gray and keep the tint when it is
 * showing, otherwise add it and make sure the filter is on, so the tile flips
 * between a tinted gray and the same tint in colour (#32). Turning gray on
 * with the filter off would otherwise do nothing anyone could see.
 */
internal fun Preferences.afterGrayscaleTap(): Preferences =
    if (grayscaleTileActive(this)) copy(grayscale = false)
    else copy(grayscale = true).withFilterEnabled(true)

/**
 * Shared plumbing for the tiles that change the filter in one tap: an atomic
 * preference update, starting the service when the tap turned the filter on,
 * and the same blocked-start recovery the main tile uses.
 */
abstract class FilterActionTileService : TileService() {

    protected abstract val logTag: String

    /** The preferences after a tap, or the same instance for a tap that does nothing. */
    protected abstract fun afterTap(current: Preferences): Preferences

    /** Paint the tile for [snapshot]; the caller calls `updateTile`. */
    protected abstract fun render(tile: Tile, snapshot: Preferences)

    @Inject lateinit var prefs: PreferencesStore

    private var scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
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
                val completed = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                    var turnedOn = false
                    prefs.update { current ->
                        val next = afterTap(current)
                        turnedOn = next.enabled && !current.enabled
                        next
                    }
                    if (turnedOn) {
                        val result = LumenServiceStarter.start(
                            this@FilterActionTileService,
                            logTag = logTag,
                            exemption = LumenServiceStarter.Exemption.USER_INTERACTION,
                            source = "tile"
                        )
                        if (!result.started) {
                            if (result.foregroundStartNotAllowed) {
                                openAppAfterBlockedStart()
                            } else {
                                prefs.update { it.copy(enabled = false) }
                            }
                        }
                    }
                    true
                } == true
                if (!completed) Log.w(logTag, "Tile action timed out; retaining the previous tile state")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(logTag, "tile onClick failed: ${t.message}", t)
            } finally {
                refreshTile()
            }
        }
    }

    private fun refreshTile() {
        scope.launch {
            try {
                val snapshot = withTimeout(PREF_READ_TIMEOUT_MS) { prefs.flow.first() }
                qsTile?.apply {
                    render(this, snapshot)
                    updateTile()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // Same as the main tile: updateTile() outside a listening
                // window throws on some OEM forks.
                Log.w(logTag, "qsTile refresh failed: ${t.message}")
            }
        }
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
            Log.w(logTag, "Could not open app after blocked tile start: ${it.message}", it)
        }
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_TIMEOUT_MS = 8_000L
        const val PREF_READ_TIMEOUT_MS = 3_000L
        const val REQUEST_BLOCKED_START = 2102
    }
}

/** Grayscale under the active tint, on and off from the shade (#32). */
@AndroidEntryPoint
class GrayscaleTileService : FilterActionTileService() {
    override val logTag = "OpenLumen/GrayTile"

    override fun afterTap(current: Preferences): Preferences = current.afterGrayscaleTap()

    override fun render(tile: Tile, snapshot: Preferences) {
        tile.label = getString(R.string.tile_label_grayscale)
        tile.state = if (grayscaleTileActive(snapshot)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (snapshot.enabled) {
                getString(R.string.tile_subtitle_on, activePresetName(snapshot))
            } else {
                getString(R.string.tile_subtitle_off)
            }
        }
    }

    private fun activePresetName(p: Preferences): String = presetDisplayName(
        context = this,
        key = p.activePresetKey,
        fallback = Presets.byKey(p.activePresetKey)?.displayName ?: p.activePresetKey,
        override = p.presetNameOverrides[p.activePresetKey]
    )
}

/**
 * One tile per favourite slot (#32). Tiles are declared in the manifest, so
 * the shade can't offer one per preset; four slots follow the favourites
 * list instead, and reordering favourites in the app changes what they show.
 */
abstract class PresetSlotTileService : FilterActionTileService() {
    protected abstract val slot: Int

    /** The slot's fixed name, also the tile's label in the manifest. */
    protected abstract val slotLabel: Int

    override fun afterTap(current: Preferences): Preferences {
        val key = presetSlotKey(current, slot) ?: return current
        return current.afterPresetSlotTap(key)
    }

    override fun render(tile: Tile, snapshot: Preferences) {
        val key = presetSlotKey(snapshot, slot)
        if (key == null) {
            tile.label = getString(slotLabel)
            tile.state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(R.string.tile_subtitle_slot_empty)
            return
        }
        tile.label = presetDisplayName(
            context = this,
            key = key,
            fallback = Presets.byKey(key)?.displayName ?: key,
            override = snapshot.presetNameOverrides[key]
        )
        val active = presetSlotIsActive(snapshot, key)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(if (active) R.string.tile_on else R.string.tile_off)
        }
    }
}

@AndroidEntryPoint
class PresetSlot1TileService : PresetSlotTileService() {
    override val logTag = "OpenLumen/SlotTile1"
    override val slot = 1
    override val slotLabel = R.string.tile_label_preset_1
}

@AndroidEntryPoint
class PresetSlot2TileService : PresetSlotTileService() {
    override val logTag = "OpenLumen/SlotTile2"
    override val slot = 2
    override val slotLabel = R.string.tile_label_preset_2
}

@AndroidEntryPoint
class PresetSlot3TileService : PresetSlotTileService() {
    override val logTag = "OpenLumen/SlotTile3"
    override val slot = 3
    override val slotLabel = R.string.tile_label_preset_3
}

@AndroidEntryPoint
class PresetSlot4TileService : PresetSlotTileService() {
    override val logTag = "OpenLumen/SlotTile4"
    override val slot = 4
    override val slotLabel = R.string.tile_label_preset_4
}
