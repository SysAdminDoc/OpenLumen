package com.openlumen.engine.engines

import android.content.Context
import android.os.Build
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineResult
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Applies a 4x4 color matrix at the SurfaceFlinger level via `service call SurfaceFlinger`.
 *
 * The colour matrix lives at transaction 1015, unchanged from `android-10.0.0_r1`
 * through `android-16.0.0_r1` and on `main`. Earlier releases of this file claimed the
 * code drifts across versions and probed 1023/1030/1036 as fallbacks; none of those was
 * ever verified against AOSP, and 1023 is the colour mode, not the matrix. The probe
 * runs at isAvailable() time and caches the winner.
 *
 * SurfaceFlinger's transaction expects an enable flag followed by the 16 matrix slots.
 * Disable is a bare `i32 0`. Each float is written as a 32-bit value in the Parcel;
 * the wire format `service call` expects is `i32 <value>` per int slot, with floats
 * reinterpreted to their IEEE-754 bits.
 *
 * Tied to roadmap candidate **C03** (SurfaceFlinger code registry). [CANDIDATE_CODES] is
 * the canonical home of known working codes; add one only with a citation to the AOSP
 * `SurfaceFlinger.cpp` onTransact case that implements it, not from a driver report
 * alone — [isSuccessfulServiceCall] can now tell a rejected code from an accepted one,
 * so a wrong guess produces a real failure rather than a silent no-op. The cache is
 * per-process and rebuilt on first probe of each cold start.
 */
class SurfaceFlingerEngine : ColorEngine {
    override val kind = EngineKind.SURFACE_FLINGER

    @Volatile private var workingCode: Int? = null
    private val probeMutex = Mutex()

    /**
     * Diagnostic: which transaction code is the engine currently using? Exposed so
     * `DriverReport` and future driver-compatibility analytics can record exactly
     * which code worked on a given device, not just "SF works".
     */
    val activeTransactionCode: Int?
        get() = workingCode

    /**
     * True once a probe has issued the disable transaction, which resets
     * SurfaceFlinger's client colour matrix. Surfaced in the driver report so a
     * user whose other colour tool went neutral can see why.
     */
    @Volatile var probeResetClientMatrix: Boolean = false
        private set

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Short-circuit if a previous probe already found a working code.
        // Without this, a slider drag on an Auto-mode preference (which
        // triggers DriverProbe.pickBest on every conflated emission) re-spawns
        // up to three `su service call SurfaceFlinger ...` subprocesses per
        // emission — a real performance bug. The cache is invalidated by
        // apply/clear when a write fails (see invalidateOnFailure), so a code
        // that's gone stale gets re-probed naturally on next call.
        ensureWorkingCode() != null
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult = withContext(Dispatchers.IO) {
        // If the cache is empty — first call after construction or
        // invalidation from a failed apply/clear — re-probe once before
        // silently no-op'ing. Without this, a user who pinned SurfaceFlinger
        // can land in a state where the engine claims to be active but
        // every apply() is a no-op because no code was probed yet.
        val code = ensureWorkingCode() ?: run {
            Log.w(TAG, "apply: no working SurfaceFlinger transaction code; tint will not be visible")
            return@withContext EngineResult.Failure("no working SurfaceFlinger transaction code")
        }
        val res = Su.runCommand(buildServiceCall(code, matrix))
        invalidateOnFailure(res, code, "apply")
        if (isSuccessfulServiceCall(res)) {
            EngineResult.Success
        } else {
            EngineResult.Failure("SurfaceFlinger apply failed for code $code")
        }
    }

    override suspend fun clear(context: Context): EngineResult = withContext(Dispatchers.IO) {
        val code = workingCode ?: return@withContext EngineResult.Success
        val res = Su.runCommand(buildDisableServiceCallCommand(code))
        invalidateOnFailure(res, code, "clear")
        if (isSuccessfulServiceCall(res)) {
            EngineResult.Success
        } else {
            EngineResult.Failure("SurfaceFlinger clear failed for code $code")
        }
    }

    private suspend fun ensureWorkingCode(): Int? {
        workingCode?.let { return it }
        return probeMutex.withLock {
            workingCode ?: if (probeLocked()) workingCode else null
        }
    }

    /**
     * Try the disable transaction with each candidate code; the first one whose
     * reply parcel is not an error is the winner. Sets [workingCode] on success.
     */
    private suspend fun probeLocked(): Boolean {
        if (!Su.isAvailable()) return false
        val candidates = candidatesFor(Build.VERSION.SDK_INT)
        for (code in candidates) {
            // The only way to test a backdoor code is to invoke it, and the
            // cheapest invocation is the disable form. That resets
            // SurfaceFlinger's *client* colour matrix, so if some other app
            // (or a previous OpenLumen process) had one set, probing drops it.
            // Record that rather than doing it silently: the Driver tab and the
            // driver report surface this so a "my other filter turned itself
            // off" report is explainable.
            val res = Su.runCommand(buildDisableServiceCallCommand(code))
            if (isSuccessfulServiceCall(res)) {
                // Only a transaction the service accepted actually cleared
                // anything. Setting this before the call made the report claim
                // a reset on devices where every candidate was rejected.
                probeResetClientMatrix = true
                Log.i(
                    TAG,
                    "probe: code $code accepted (api ${Build.VERSION.SDK_INT}); " +
                        "any client color matrix is now reset"
                )
                workingCode = code
                return true
            }
            Log.w(
                TAG,
                "probe: code $code rejected (exit=${res.exitCode}, stdout=${res.stdout.take(160)})"
            )
        }
        Log.w(TAG, "probe: no SurfaceFlinger color-transform code worked (tried ${candidates.toList()})")
        return false
    }

    private fun buildServiceCall(code: Int, matrix: LumenMatrix): String =
        buildServiceCallCommand(code, matrix)

    /**
     * Candidate list for SurfaceFlinger's colour-matrix backdoor. Exposed as a
     * function (not a constant) so tests can call it without touching the
     * cached working-code state; the `api` argument is retained because the
     * list is allowed to become version-dependent again if AOSP ever moves the
     * code, and callers already pass it.
     *
     * The list is deliberately just [CANDIDATE_CODES]. Earlier releases also
     * tried 1023, 1030 and 1036, none of which was ever verified against AOSP
     * as a colour-matrix code — and 1023 demonstrably is not, it is the colour
     * mode. Those entries were also unreachable, because the old success test
     * accepted the first candidate unconditionally. Rather than guess, keep the
     * one code the source confirms and let [isSuccessfulServiceCall] report a
     * genuine rejection if a future build moves it.
     */
    internal fun candidatesFor(@Suppress("UNUSED_PARAMETER") api: Int): IntArray = CANDIDATE_CODES

    private fun invalidateOnFailure(res: Su.SuResult, code: Int, operation: String) {
        if (isSuccessfulServiceCall(res)) return
        Log.w(
            TAG,
            "$operation failed for SurfaceFlinger code $code " +
                "(exit=${res.exitCode}, stdout=${res.stdout.take(160)}); invalidating probe cache"
        )
        workingCode = null
        // If the failure looks like the su binary itself is gone (exit 127 /
        // -1 timeout), invalidate the process-wide su availability cache so
        // the next probe re-checks rather than racing back to the same
        // "su says it works but every command fails" state.
        Su.resetCacheIfSuLikelyFailed(res.exitCode)
    }

    companion object {
        const val TAG = "OpenLumen/SurfaceFlinger"

        /**
         * `case 1015` in `services/surfaceflinger/SurfaceFlinger.cpp`, verified
         * byte-identical from `android-10.0.0_r1` through `android-16.0.0_r1`
         * and on `main`. Neighbours in the same backdoor range are different
         * features: 1014 is the daltonizer, 1022 saturation, 1023 colour mode.
         */
        val CANDIDATE_CODES: IntArray = intArrayOf(1015)

        internal fun buildServiceCallCommand(code: Int, matrix: LumenMatrix): String {
            val m = matrix.toSurfaceFlinger16()
            val sb = StringBuilder("service call SurfaceFlinger ").append(code).append(" i32 1")
            for (f in m) {
                val bits = f.toRawBits()
                sb.append(" i32 ").append(bits)
            }
            return sb.toString()
        }

        internal fun buildDisableServiceCallCommand(code: Int): String =
            "service call SurfaceFlinger $code i32 0"

        /**
         * Decide whether a `service call` actually reached the service.
         *
         * The old rule was `exitCode == 0 && !stdout.contains("not found")`,
         * which is true for *every* call to a service that exists, because
         * `cmds/service/service.cpp` discards the value of
         * `service->transact(...)` entirely:
         *
         * ```
         * service->transact(code, data, &reply);
         * aout << "Result: " << reply << endl;
         * ```
         *
         * `result` stays 0 unless the *service name* could not be resolved, in
         * which case it becomes 10 and stderr carries "does not exist". So the
         * exit code says nothing about the transaction.
         *
         * The signal is the reply parcel. `Parcel::print` renders
         * `Error: 0x… "…"` when `errorCheck() != NO_ERROR`, and
         * `IPCThreadState::waitForResponse` calls `reply->setError(err)` on any
         * failed transaction — so a rejected code (PERMISSION_DENIED from
         * SurfaceFlinger's `HARDWARE_TEST` gate, or UNKNOWN_TRANSACTION) prints
         * `Result: Parcel(Error: …)`.
         *
         * Note the inversion against a first reading: `Parcel(NULL)` means
         * SUCCESS here. `Parcel::print` emits NULL for any empty reply, and
         * transaction 1015 is a void backdoor that writes nothing back, so an
         * accepted call produces exactly `Result: Parcel(NULL)`. Treating NULL
         * as failure would report the one code that works as broken on every
         * device.
         */
        internal fun isSuccessfulServiceCall(res: Su.SuResult): Boolean {
            if (res.exitCode != 0) return false
            val out = res.stdout
            if (out.contains("does not exist", ignoreCase = true)) return false
            if (out.contains("not found", ignoreCase = true)) return false
            return !out.contains("Parcel(Error:", ignoreCase = true)
        }

        suspend fun clearKnownColorTransforms(api: Int = Build.VERSION.SDK_INT): List<Int> {
            if (!Su.isAvailable()) return emptyList()
            val cleared = mutableListOf<Int>()
            for (code in SurfaceFlingerEngine().candidatesFor(api)) {
                val res = Su.runCommand(buildDisableServiceCallCommand(code))
                Su.resetCacheIfSuLikelyFailed(res.exitCode)
                if (isSuccessfulServiceCall(res)) cleared += code
            }
            return cleared
        }
    }
}
