package com.openlumen.service

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
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
 * on the next boot; that means it cannot say the clear happened.
 *
 * The caller writes a nonce and the service echoes it. Not a timestamp:
 * `System.currentTimeMillis` moves backward on an NTP correction, which would
 * let a note from an earlier turn-off satisfy a later one and silently retire
 * the fallback, and `SystemClock.elapsedRealtime` resets on reboot, which has
 * the same effect against a note that outlived the boot. A nonce is true or
 * false regardless of what either clock does.
 *
 * Kept in device-protected storage so it works before first unlock, which is
 * when the emergency path matters most.
 */
internal object TurnOffAcknowledgement {

    private const val REQUEST_FILE = "turn-off-request"
    private const val ACK_FILE = "turn-off-ack"
    private const val TAG = "OpenLumen/TurnOffAck"

    /** How long a caller waits before deciding the service is not coming back. */
    const val WAIT_BUDGET_MS = 2_000L

    private const val POLL_INTERVAL_MS = 100L

    private fun file(context: Context, name: String): File =
        File(context.createDeviceProtectedStorageContext().filesDir, name)

    /**
     * Claim a turn-off and get the token the service has to echo.
     *
     * Writing the request is what makes the answer unambiguous: the service
     * reads this file, so an acknowledgement can only ever refer to a request
     * that was actually made.
     */
    fun requestTurnOff(context: Context): String {
        val nonce = UUID.randomUUID().toString()
        runCatching { file(context, REQUEST_FILE).writeText(nonce) }
            .onFailure { Log.w(TAG, "could not record turn-off request: ${it.message}") }
        return nonce
    }

    /** Called by the service once the display is actually down. */
    fun record(context: Context) {
        val requested = runCatching {
            file(context, REQUEST_FILE).takeIf { it.exists() }?.readText()?.trim()
        }.getOrNull()
        if (requested.isNullOrEmpty()) return

        runCatching { file(context, ACK_FILE).writeText(requested) }
            .onFailure { Log.w(TAG, "could not record turn-off ack: ${it.message}") }
    }

    /** The nonce of the last acknowledged turn-off, or null if there is none. */
    fun lastAcknowledged(context: Context): String? =
        runCatching {
            file(context, ACK_FILE).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    /**
     * Wait for an acknowledgement of [nonce].
     *
     * Returns false when the budget runs out, which is the caller's signal to
     * clear the display itself. The budget is short on purpose: this runs
     * inside a broadcast receiver's goAsync window.
     */
    suspend fun awaitAcknowledgement(
        context: Context,
        nonce: String,
        budgetMs: Long = WAIT_BUDGET_MS
    ): Boolean {
        var waited = 0L
        while (waited < budgetMs) {
            if (lastAcknowledged(context) == nonce) return true
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return lastAcknowledged(context) == nonce
    }
}
