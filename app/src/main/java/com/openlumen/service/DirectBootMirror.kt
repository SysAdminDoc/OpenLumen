package com.openlumen.service

import android.util.Log
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mirrors the minimum pre-unlock state needed by Locked Boot restore.
 */
internal class DirectBootMirror(
    private val store: DirectBootStateStore,
    private val logTag: String
) {
    private val lastMirroredState = AtomicReference<DirectBootState?>(null)

    suspend fun mirror(prefs: Preferences, active: Boolean, matrix: LumenMatrix) {
        val next = DirectBootState(
            enabled = prefs.enabled,
            active = active,
            engine = prefs.engine,
            matrix = matrix.toMatrixDto(),
            amoledBlackClamp = matrix.amoledClamp
        )
        if (lastMirroredState.get() == next) return
        runCatching {
            store.writeSnapshot(
                enabled = next.enabled,
                active = next.active,
                engine = next.engine,
                matrix = next.matrix,
                amoledBlackClamp = next.amoledBlackClamp
            )
            lastMirroredState.set(next)
        }.onFailure {
            Log.w(logTag, "direct-boot mirror write failed: ${it.message}")
        }
    }

    suspend fun markDisabled() {
        store.update { it.copy(enabled = false, active = false) }
        lastMirroredState.set(null)
    }

    suspend fun readSnapshot(timeoutMs: Long = 8_000L): DirectBootState =
        withTimeoutOrNull(timeoutMs) { store.flow.first() } ?: DirectBootState()
}

internal fun LumenMatrix.toMatrixDto(): MatrixDto = MatrixDto(
    r = r,
    g = g,
    b = b,
    biasR = biasR,
    biasG = biasG,
    biasB = biasB,
    dim = dim,
    gammaR = gammaR,
    gammaG = gammaG,
    gammaB = gammaB,
    hasColorMatrix = hasColorMatrix,
    matrixRr = matrixRr,
    matrixRg = matrixRg,
    matrixRb = matrixRb,
    matrixGr = matrixGr,
    matrixGg = matrixGg,
    matrixGb = matrixGb,
    matrixBr = matrixBr,
    matrixBg = matrixBg,
    matrixBb = matrixBb
)

internal fun DirectBootState.toLumenMatrix(): LumenMatrix = LumenMatrix(
    r = matrix.r,
    g = matrix.g,
    b = matrix.b,
    biasR = matrix.biasR,
    biasG = matrix.biasG,
    biasB = matrix.biasB,
    dim = matrix.dim,
    gammaR = matrix.gammaR,
    gammaG = matrix.gammaG,
    gammaB = matrix.gammaB,
    amoledClamp = amoledBlackClamp,
    hasColorMatrix = matrix.hasColorMatrix,
    matrixRr = matrix.matrixRr,
    matrixRg = matrix.matrixRg,
    matrixRb = matrix.matrixRb,
    matrixGr = matrix.matrixGr,
    matrixGg = matrix.matrixGg,
    matrixGb = matrix.matrixGb,
    matrixBr = matrix.matrixBr,
    matrixBg = matrix.matrixBg,
    matrixBb = matrix.matrixBb
)
