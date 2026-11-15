package com.openlumen.viewmodel

import com.openlumen.shizuku.ShizukuGrantOutcome
import com.openlumen.shizuku.ShizukuState

/**
 * What the Driver tab's Shizuku action shows (C254).
 *
 * @property outcome the last attempt's result until the screen consumes a
 *   success, or the next attempt starts. A failure stays, so the card can say
 *   what went wrong for as long as the user is looking at it.
 */
data class ShizukuUiState(
    val state: ShizukuState = ShizukuState.NOT_INSTALLED,
    val granting: Boolean = false,
    val outcome: ShizukuGrantOutcome? = null
)
