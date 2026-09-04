package com.openlumen.service

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.delay

/**
 * A note the service leaves once a turn-off has actually cleared the display.
 *
 * `startForegroundService` returning without throwing means only that the
 * request was accepted. The service still has to reach `startForeground`
 * within a few seconds or the system kills it, and the turn-off runs on a
 * scope that `onDestroy` cancels. So a start that "succeeded" can drop the
 * work entirely, and the receiver's fallback never fires because nothing
 * reported a failure.
 *
 * The direct-boot mirror cannot answer this. It is written before the clear,
 * deliberately, so that a process killed mid-clear does not put the tint back
 * on the next boot. Something written after the clear is the only thing that
 * says the clear happened.
 *
 * Kept in device-protected storage so it works before first unlock, which is
 * when the emergency path matters most.
 */
internal object TurnOffAcknowledgement {

    private const val FILE_NAME = "turn-off-ack"
    private const val TAG = "OpenLumen/TurnOffAck"

    /** How long a caller waits before deciding the service is not coming back. */
    const val WAIT_BUDGET_MS = 2_000L

    private const val POLL_INTERVAL_MS = 100L

    private fun file(context: Context): File =
        File(context.createDeviceProtectedStorageContext().filesDir, FILE_NAME)

    /** Called by the service once the display is actually down. */
    fun record(context: Context, atMillis: Long = System.currentTimeMillis()) {
        runCatching { file(context).writeText(atMillis.toString()) }
            .onFailure { Log.w(TAG, "could not record turn-off ack: ${it.message}") }
    }

    /** The last acknowledged turn-off, or null if there has never been one. */
    fun lastAcknowledgedAt(context: Context): Long? =
        runCatching { file(context).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() }
            .getOrNull()

    /**
     * Wait for an acknowledgement newer than [since].
     *
     * Returns false when the budget runs out, which is the caller's signal to
     * clear the display itself. The budget is short on purpose: this runs
     * inside a broadcast receiver's goAsync window.
     */
    suspend fun awaitAfter(
        context: Context,
        since: Long,
        budgetMs: Long = WAIT_BUDGET_MS
    ): Boolean {
        var waited = 0L
        while (waited < budgetMs) {
            val acknowledged = lastAcknowledgedAt(context)
            if (acknowledged != null && acknowledged >= since) return true
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return lastAcknowledgedAt(context)?.let { it >= since } == true
    }
}
