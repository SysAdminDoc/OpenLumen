package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.LumenMatrix
import org.junit.Test

class ApplyDecisionGateTest {
    private val warmMatrix = LumenMatrix(r = 1.0f, g = 0.72f, b = 0.38f, dim = 0.2f)
    private val warmerMatrix = LumenMatrix(r = 1.0f, g = 0.58f, b = 0.22f, dim = 0.35f)

    @Test fun `first active emission dispatches`() {
        val gate = ApplyDecisionGate()

        val decision = gate.next(shouldBeActive = true, matrix = warmMatrix)

        assertThat(decision).isEqualTo(
            ApplyDecision(matrix = warmMatrix, isStateFlip = true)
        )
    }

    @Test fun `duplicate target does not dispatch until target changes`() {
        val gate = ApplyDecisionGate()

        assertThat(gate.next(shouldBeActive = true, matrix = warmMatrix)).isNotNull()
        assertThat(gate.next(shouldBeActive = true, matrix = warmMatrix)).isNull()

        val changed = gate.next(shouldBeActive = true, matrix = warmerMatrix)

        assertThat(changed).isEqualTo(
            ApplyDecision(matrix = warmerMatrix, isStateFlip = false)
        )
    }

    @Test fun `reset dispatches same active target to a new engine`() {
        val gate = ApplyDecisionGate()

        assertThat(gate.next(shouldBeActive = true, matrix = warmMatrix)).isNotNull()
        assertThat(gate.next(shouldBeActive = true, matrix = warmMatrix)).isNull()

        gate.reset()

        assertThat(gate.next(shouldBeActive = true, matrix = warmMatrix)).isEqualTo(
            ApplyDecision(matrix = warmMatrix, isStateFlip = true)
        )
    }

    @Test fun `reset dispatches identity for inactive first emission`() {
        val gate = ApplyDecisionGate()

        assertThat(gate.next(shouldBeActive = false, matrix = LumenMatrix.IDENTITY)).isNotNull()
        assertThat(gate.next(shouldBeActive = false, matrix = LumenMatrix.IDENTITY)).isNull()

        gate.reset()

        assertThat(gate.next(shouldBeActive = false, matrix = LumenMatrix.IDENTITY)).isEqualTo(
            ApplyDecision(matrix = LumenMatrix.IDENTITY, isStateFlip = false)
        )
    }
}
