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
        lastTarget = matrix
        lastShouldBeActive = shouldBeActive
        return ApplyDecision(matrix = matrix, isStateFlip = isStateFlip)
    }
}
