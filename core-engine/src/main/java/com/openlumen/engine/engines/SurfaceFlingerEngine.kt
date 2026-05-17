package com.openlumen.engine.engines

import android.content.Context
import android.os.Build
import android.util.Log
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
 *
 * Tied to roadmap candidate **C03** (SurfaceFlinger code registry). The candidate-code
 * list below is the canonical home of known working codes; add a new tuple when a
 * driver report (Driver tab → Share report) shows a device that needs a fresh code.
 * Until we have a more elaborate per-device override mechanism, the cache is
 * per-process and rebuilt on first probe of each cold start.
 */
class SurfaceFlingerEngine : ColorEngine {
    override val kind = EngineKind.SURFACE_FLINGER

    @Volatile private var workingCode: Int? = null

    /**
     * Diagnostic: which transaction code is the engine currently using? Exposed so
     * `DriverReport` and future driver-compatibility analytics can record exactly
     * which code worked on a given device, not just "SF works".
     */
    val activeTransactionCode: Int?
        get() = workingCode

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!Su.isAvailable()) return@withContext false
        // Try the identity matrix with each candidate code; the first one that returns 0
        // without "Service: SurfaceFlinger not found" is our winner.
        val candidates = candidatesFor(Build.VERSION.SDK_INT)
        for (code in candidates) {
            val res = Su.runCommand(buildServiceCall(code, LumenMatrix.IDENTITY))
            if (res.exitCode == 0 && !res.stdout.contains("not found", ignoreCase = true)) {
                Log.d(TAG, "probe: code $code worked (api ${Build.VERSION.SDK_INT})")
                workingCode = code
                return@withContext true
            }
        }
        Log.w(TAG, "probe: no SurfaceFlinger color-transform code worked (tried ${candidates.toList()})")
        false
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val code = workingCode ?: return@withContext
        val res = Su.runCommand(buildServiceCall(code, matrix))
        invalidateOnFailure(res, code, "apply")
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val code = workingCode ?: return@withContext
        val res = Su.runCommand(buildServiceCall(code, LumenMatrix.IDENTITY))
        invalidateOnFailure(res, code, "clear")
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

    /**
     * Per-API candidate list for SurfaceFlinger's `setDisplayColorTransform` code.
     * Exposed as a function (not a constant) so tests can call it without
     * touching the cached working-code state.
     *
     * Ordering matters: we try the most-common-for-this-API code first. The order
     * below tracks AOSP master at the time of writing; new entries should
     * preserve "most-common-for-this-API first."
     */
    internal fun candidatesFor(api: Int): IntArray = when {
        // Android 16 (API 36) preview shows the same path as 15; if a new code drifts
        // in the stable release, add it here.
        api >= 36 -> intArrayOf(1015, 1030, 1036)
        // Android 13+ added a few new transaction codes; 1030 is the most common
        // after 1015 on Pixel-family Android 13/14/15 builds.
        api >= 33 -> intArrayOf(1015, 1030, 1036)
        // Android 10..12 saw 1023 appear on some OEM forks.
        api >= 29 -> intArrayOf(1015, 1023, 1030)
        // Pre-10 reliably uses 1015.
        else -> intArrayOf(1015)
    }

    private fun invalidateOnFailure(res: Su.SuResult, code: Int, operation: String) {
        if (res.exitCode == 0 && !res.stdout.contains("not found", ignoreCase = true)) return
        Log.w(
            TAG,
            "$operation failed for SurfaceFlinger code $code " +
                "(exit=${res.exitCode}, stdout=${res.stdout.take(160)}); invalidating probe cache"
        )
        workingCode = null
    }

    private companion object {
        const val TAG = "OpenLumen/SurfaceFlinger"
    }
}
