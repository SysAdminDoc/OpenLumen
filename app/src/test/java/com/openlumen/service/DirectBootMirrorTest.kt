package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.DirectBootState
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
}
