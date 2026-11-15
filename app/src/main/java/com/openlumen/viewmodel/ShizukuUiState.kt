package com.openlumen.viewmodel

import com.openlumen.shizuku.ShizukuGrantOutcome
import com.openlumen.shizuku.ShizukuState

/**
 * What the Driver tab's Shizuku action shows (C254).
 *
 * @property outcome the last attempt's result until the screen consumes a
 *   success, the next attempt starts, or Shizuku leaves the state the attempt
 *   ended in. A failure stays while the user is looking at the same Shizuku
 *   it came from.
 */
data class ShizukuUiState(
    val state: ShizukuState = ShizukuState.NOT_INSTALLED,
    val granting: Boolean = false,
    val outcome: ShizukuGrantOutcome? = null
)

/**
 * One tap's result and the Shizuku state it ended in. "Not allowed in
 * Shizuku" under "Shizuku is not running" describes a server that has gone,
 * and it would come back if that server started again, so the result only
 * holds while Shizuku stays where the attempt left it.
 */
internal data class ShizukuAttempt(val outcome: ShizukuGrantOutcome, val state: ShizukuState) {
    fun keptIn(now: ShizukuState): ShizukuAttempt? = takeIf { state == now }
}
