package com.openlumen.viewmodel

import com.openlumen.shizuku.AppSaturationOutcome
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

/**
 * What the Driver tab's per-app saturation card shows (C287).
 *
 * @property supported false before Android 10, which has no per-app color transform.
 * @property problem why the last change did not take, until the next one does.
 */
data class AppSaturationUiState(
    val supported: Boolean = true,
    val busy: Boolean = false,
    val problem: AppSaturationOutcome? = null
)
