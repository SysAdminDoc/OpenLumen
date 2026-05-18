package com.openlumen.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DirectBootState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enabled: Boolean = false,
    val active: Boolean = false,
    val engine: EngineKindDto = EngineKindDto.Auto,
    val matrix: MatrixDto = MatrixDto(),
    val amoledBlackClamp: Boolean = false
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Device-protected mirror of the minimum state needed to restore the active tint
 * before the user unlocks the device for the first time after boot.
 *
 * Backed by `DataStoreFactory.createInDeviceProtectedStorage`, a first-class
 * DataStore 1.2 API for putting a typed DataStore in device-protected
 * storage so it survives FBE-locked boot. We deliberately mirror only what
 * the `LockedBootReceiver → LumenService.ACTION_DIRECT_BOOT_RESTORE` path
 * needs; the full preferences blob (location, profiles, etc.) stays in
 * credential-protected storage and is never read pre-unlock.
 */
class DirectBootStateStore(context: Context) {

    private val dataStore: DataStore<DirectBootState> =
        DataStoreFactory.createInDeviceProtectedStorage(
            context.applicationContext,
            DATASTORE_NAME,
            DirectBootStateSerializer
        )

    val flow: Flow<DirectBootState> = dataStore.data

    suspend fun update(transform: (DirectBootState) -> DirectBootState) {
        dataStore.updateData { current -> sanitizeDirectBootState(transform(current)) }
    }

    suspend fun writeSnapshot(
        enabled: Boolean,
        active: Boolean,
        engine: EngineKindDto,
        matrix: MatrixDto,
        amoledBlackClamp: Boolean
    ) {
        update {
            DirectBootState(
                enabled = enabled,
                active = active,
                engine = engine,
                matrix = matrix,
                amoledBlackClamp = amoledBlackClamp
            )
        }
    }

    private companion object {
        const val DATASTORE_NAME = "openlumen-direct-boot-state"
    }
}

internal object DirectBootStateSerializer : Serializer<DirectBootState> {
    override val defaultValue: DirectBootState = DirectBootState()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): DirectBootState {
        val raw = input.readBytes().toString(Charsets.UTF_8)
        if (raw.isBlank()) return defaultValue
        return runCatching {
            sanitizeDirectBootState(json.decodeFromString(DirectBootState.serializer(), raw))
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: DirectBootState, output: OutputStream) {
        val body = json.encodeToString(sanitizeDirectBootState(t))
        output.write(body.toByteArray(Charsets.UTF_8))
    }
}

internal fun sanitizeDirectBootState(state: DirectBootState): DirectBootState =
    state.copy(
        schemaVersion = DirectBootState.CURRENT_SCHEMA_VERSION,
        matrix = sanitizeDirectBootMatrix(state.matrix)
    )

/**
 * Clamp every numeric field of the mirrored matrix. Includes the optional
 * 3x3 CVD coefficients and the [MatrixDto.hasColorMatrix] gate so a
 * malformed or NaN-laden mirror payload can't reach the engine on
 * Locked Boot restore. Mirrors the bounds applied by
 * [PreferencesStore.sanitizeMatrix].
 */
private fun sanitizeDirectBootMatrix(matrix: MatrixDto): MatrixDto = matrix.copy(
    r = matrix.r.finiteIn(0f, 2f, default = 1f),
    g = matrix.g.finiteIn(0f, 2f, default = 1f),
    b = matrix.b.finiteIn(0f, 2f, default = 1f),
    biasR = matrix.biasR.finiteIn(-1f, 1f, default = 0f),
    biasG = matrix.biasG.finiteIn(-1f, 1f, default = 0f),
    biasB = matrix.biasB.finiteIn(-1f, 1f, default = 0f),
    dim = matrix.dim.finiteIn(0f, 0.95f, default = 0f),
    gammaR = matrix.gammaR.finiteIn(0.05f, 5f, default = 1f),
    gammaG = matrix.gammaG.finiteIn(0.05f, 5f, default = 1f),
    gammaB = matrix.gammaB.finiteIn(0.05f, 5f, default = 1f),
    matrixRr = matrix.matrixRr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f),
    matrixRg = matrix.matrixRg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixRb = matrix.matrixRb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixGr = matrix.matrixGr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixGg = matrix.matrixGg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f),
    matrixGb = matrix.matrixGb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixBr = matrix.matrixBr.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixBg = matrix.matrixBg.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 0f),
    matrixBb = matrix.matrixBb.finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = 1f)
)

private const val MATRIX_COEFF_MIN = -4f
private const val MATRIX_COEFF_MAX = 4f

private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
    if (isFinite()) coerceIn(min, max) else default
