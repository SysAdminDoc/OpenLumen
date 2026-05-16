package com.openlumen.engine.engines

import android.content.Context
import android.os.Build
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies a 4x4 color matrix at the SurfaceFlinger level via `service call SurfaceFlinger`.
 *
 * The transaction code for setDisplayColorTransform has shifted across AOSP releases.
 * Historically 1015 on most builds, but some OEM forks renumber. We probe a small set
 * of known codes at isAvailable() time and cache the winner.
 *
 * Each float is written as a 32-bit value in the Parcel; the wire format `service call`
 * expects is `i32 <value>` per int slot, with floats reinterpreted to their IEEE-754 bits.
 */
class SurfaceFlingerEngine : ColorEngine {
    override val kind = EngineKind.SURFACE_FLINGER

    @Volatile private var workingCode: Int? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!Su.isAvailable()) return@withContext false
        // Try the identity matrix with each candidate code; the first one that returns 0
        // without "Service: SurfaceFlinger not found" is our winner.
        val candidates = when {
            Build.VERSION.SDK_INT >= 33 -> intArrayOf(1015, 1030)
            Build.VERSION.SDK_INT >= 29 -> intArrayOf(1015, 1023)
            else -> intArrayOf(1015)
        }
        for (code in candidates) {
            val res = Su.runCommand(buildServiceCall(code, LumenMatrix.IDENTITY))
            if (res.outExitCode == 0 && !res.stdout.contains("not found", ignoreCase = true)) {
                workingCode = code
                return@withContext true
            }
        }
        false
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val code = workingCode ?: return@withContext
        Su.runCommand(buildServiceCall(code, matrix))
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val code = workingCode ?: return@withContext
        Su.runCommand(buildServiceCall(code, LumenMatrix.IDENTITY))
        Unit
    }

    private fun buildServiceCall(code: Int, matrix: LumenMatrix): String {
        val m = matrix.toSurfaceFlinger16()
        val sb = StringBuilder("service call SurfaceFlinger ").append(code)
        for (f in m) {
            // Reinterpret IEEE 754 bits to int, since `service call` wants i32 slots.
            val bits = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).getInt(0)
            sb.append(" i32 ").append(bits)
        }
        return sb.toString()
    }
}
