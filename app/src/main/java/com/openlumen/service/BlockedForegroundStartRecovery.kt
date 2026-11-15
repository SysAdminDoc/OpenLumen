package com.openlumen.service

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.openlumen.prefs.EngineKindDto

/**
 * The app-entry handshake used after a background foreground-service start
 * is refused: the tile or widget keeps the switch on and opens MainActivity
 * with [LumenServiceStarter.ACTION_START_BLOCKED], and the visible activity
 * finishes the start.
 */
internal object BlockedForegroundStartRecovery {
    /**
     * Whether [intent] hands over a refused start. A relaunch from Recents
     * replays the intent the task was started with, so it is not a new
     * handover: acting on it would start a filter the user may have turned
     * off since.
     */
    fun isPending(intent: Intent?): Boolean =
        intent != null &&
            intent.action == LumenServiceStarter.ACTION_START_BLOCKED &&
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

    /**
     * Same rule as the overlay permission card: Auto may fall back to the
     * overlay, the pinned root and secure-settings drivers never do.
     */
    fun overlayNeeded(engine: EngineKindDto): Boolean =
        engine == EngineKindDto.Auto || engine == EngineKindDto.Overlay

    /**
     * Whether the activity can start the service without walking into an
     * overlay install it is not allowed to make. A pinned root driver needs
     * no overlay, so it starts at once.
     */
    fun canStartNow(overlayNeeded: Boolean, overlayGranted: Boolean): Boolean =
        !overlayNeeded || overlayGranted
}

/**
 * A refused tile or widget start that MainActivity still owes the user.
 * [id] tells one handover from the next; [reason] is the caller's
 * [LumenServiceStarter.EXTRA_BLOCKED_REASON].
 */
data class BlockedStartNotice(
    val id: Long,
    val reason: String?,
    val phase: Phase = Phase.Checking
) {
    enum class Phase {
        /** Preferences are being read; nothing is shown yet. */
        Checking,

        /** The driver needs overlay access first; started on return once granted. */
        NeedsOverlay,

        /** The start from the app was refused too; waits for Retry. */
        StartFailed
    }

    enum class Source { Tile, Widget, Other }

    val source: Source
        get() = when (reason) {
            "tile" -> Source.Tile
            "widget_toggle", "widget_preset" -> Source.Widget
            else -> Source.Other
        }

    val visible: Boolean get() = phase != Phase.Checking

    fun save(out: Bundle) {
        out.putLong(KEY_ID, id)
        out.putString(KEY_REASON, reason)
        out.putString(KEY_PHASE, phase.name)
    }

    companion object {
        private const val KEY_ID = "com.openlumen.blockedStart.id"
        private const val KEY_REASON = "com.openlumen.blockedStart.reason"
        private const val KEY_PHASE = "com.openlumen.blockedStart.phase"

        fun from(intent: Intent?): BlockedStartNotice? {
            if (!BlockedForegroundStartRecovery.isPending(intent)) return null
            return BlockedStartNotice(
                id = SystemClock.elapsedRealtimeNanos(),
                reason = intent?.getStringExtra(LumenServiceStarter.EXTRA_BLOCKED_REASON)
            )
        }

        /** What [save] wrote, or null once the notice was dealt with. */
        fun restore(saved: Bundle): BlockedStartNotice? {
            if (!saved.containsKey(KEY_ID)) return null
            val phase = Phase.entries.firstOrNull { it.name == saved.getString(KEY_PHASE) }
                ?: Phase.Checking
            return BlockedStartNotice(saved.getLong(KEY_ID), saved.getString(KEY_REASON), phase)
        }
    }
}
