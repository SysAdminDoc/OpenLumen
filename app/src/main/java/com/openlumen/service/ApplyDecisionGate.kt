package com.openlumen.service

import com.openlumen.engine.LumenMatrix

internal data class ApplyDecision(
    val matrix: LumenMatrix,
    val isStateFlip: Boolean
)

/**
 * Tracks the last target the active engine was asked to display.
 *
 * This deliberately lives outside [LumenService] so the first-emission
 * behavior is covered by JVM tests. Root engines must receive a fresh
 * matrix after every engine switch, even if the requested matrix equals
 * the previous engine's target. Tied to roadmap candidate C117.
 */
internal class ApplyDecisionGate {
    private var lastTarget: LumenMatrix? = null
    private var lastShouldBeActive: Boolean = false

    @Synchronized
    fun reset() {
        lastTarget = null
        lastShouldBeActive = false
    }

    @Synchronized
    fun next(shouldBeActive: Boolean, matrix: LumenMatrix): ApplyDecision? {
        if (shouldBeActive == lastShouldBeActive && matrix == lastTarget) {
            return null
        }

        val isStateFlip = shouldBeActive != lastShouldBeActive
        return ApplyDecision(matrix = matrix, isStateFlip = isStateFlip)
    }

    /**
     * The target last committed while the filter was meant to be on, or null
     * when it is meant to be off or nothing has been committed. This is what
     * the screen should show if something else has overwritten it.
     */
    @Synchronized
    fun committedActiveTarget(): LumenMatrix? = lastTarget?.takeIf { lastShouldBeActive }

    /**
     * Commit a target only after the engine has reported a visible success.
     * Failed operations deliberately leave the previous committed target in
     * place so the same requested state can be retried.
     */
    @Synchronized
    fun commit(shouldBeActive: Boolean, matrix: LumenMatrix) {
        lastTarget = matrix
        lastShouldBeActive = shouldBeActive
    }
}
