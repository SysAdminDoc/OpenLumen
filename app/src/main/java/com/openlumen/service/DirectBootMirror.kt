package com.openlumen.service

import android.util.Log
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.toDto
import com.openlumen.prefs.toMatrix
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

/**
 * The mapping between a matrix and its persisted form lives in core-prefs now,
 * next to the DTO, because both core modules needed it and only one of them
 * could see the other. These keep the names the mirror already used.
 */
internal fun LumenMatrix.toMatrixDto(): MatrixDto = toDto()

internal fun DirectBootState.toLumenMatrix(): LumenMatrix =
    matrix.toMatrix(amoledClamp = amoledBlackClamp)
