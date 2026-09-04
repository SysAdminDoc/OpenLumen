package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.MatrixDto
import org.junit.Test

class DirectBootMirrorTest {
    @Test fun `matrix dto round trips all engine fields`() {
        val matrix = LumenMatrix(
            r = 0.9f,
            g = 0.6f,
            b = 0.3f,
            biasR = -0.1f,
            biasG = 0.2f,
            biasB = -0.3f,
            dim = 0.4f,
            gammaR = 0.8f,
            gammaG = 1.2f,
            gammaB = 1.6f,
            amoledClamp = true,
            hasColorMatrix = true,
            matrixRr = 0.7f,
            matrixRg = 0.1f,
            matrixRb = 0.2f,
            matrixGr = 0.3f,
            matrixGg = 0.8f,
            matrixGb = 0.4f,
            matrixBr = 0.5f,
            matrixBg = 0.6f,
            matrixBb = 0.9f
        )

        val state = DirectBootState(
            active = true,
            matrix = matrix.toMatrixDto(),
            amoledBlackClamp = matrix.amoledClamp
        )

        assertThat(state.toLumenMatrix()).isEqualTo(matrix)
    }

    @Test fun `the mirror carries the system correction mode through locked boot`() {
        // The rootless driver renders Grayscale and the colour-vision presets
        // through this mode, not through the matrix, so a mirror that dropped
        // it restored those presets as no filter at all until the user
        // unlocked. Round-tripping "all engine fields" above did not catch it
        // because the field was missing from the DTO on both sides.
        val state = DirectBootState(
            active = true,
            matrix = Presets.GRAY.toMatrixDto(),
            amoledBlackClamp = Presets.GRAY.amoledClamp
        )

        assertThat(state.toLumenMatrix().daltonizer).isEqualTo(Daltonizer.MONOCHROMACY)
        assertThat(state.toLumenMatrix()).isEqualTo(Presets.GRAY)
    }

    @Test fun `a mode this build does not know decodes as no correction`() {
        // A mirror written by a newer build must not fail the decode or reach
        // the engine with a mode it cannot map.
        val state = DirectBootState(matrix = MatrixDto(daltonizer = "BOGUS"))

        assertThat(state.toLumenMatrix().daltonizer).isEqualTo(Daltonizer.NONE)
    }

    @Test fun `a preset with no mode leaves the field empty`() {
        // Positive control for the two above: the name has to come from the
        // matrix, not be written unconditionally.
        assertThat(Presets.NIGHT.toMatrixDto().daltonizer).isNull()
        assertThat(Presets.GRAY.toMatrixDto().daltonizer).isEqualTo("MONOCHROMACY")
    }
}
