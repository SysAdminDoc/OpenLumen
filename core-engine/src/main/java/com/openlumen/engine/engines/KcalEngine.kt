package com.openlumen.engine.engines

import android.content.Context
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes to the KCAL kernel driver's sysfs nodes. Requires:
 *   - root
 *   - Qualcomm SoC
 *   - a custom kernel that exposes a KCAL sysfs surface
 *
 * KCAL is a scalar-per-channel driver (no cross-channel matrix). We map LumenMatrix to
 * RGB triplets in 0–256 and combine the dim factor into the per-channel scalar.
 *
 * Tied to roadmap candidate **C04** (KCAL variant probing). Different kernel forks
 * place the KCAL surface in different directories — most commonly
 * `/sys/devices/platform/kcal_ctrl.0/`, but `/sys/class/misc/kcal/` and a couple
 * of others show up on minority kernels. The engine now probes a list of known
 * roots at `isAvailable()` time and caches the winner.
 */
class KcalEngine : ColorEngine {
    override val kind = EngineKind.KCAL

    @Volatile private var resolvedPaths: Paths? = null

    /**
     * Diagnostic: which KCAL sysfs directory did the probe pick? Exposed so
     * the driver report can record the exact path the engine is writing to.
     */
    val activeBasePath: String?
        get() = resolvedPaths?.base

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        // Short-circuit if a previous probe already resolved the sysfs
        // surface — otherwise every conflated prefs emission re-spawns one
        // su subprocess per candidate root. `invalidateOnFailure` resets
        // the cache when an apply/clear fails (e.g. the kernel module was
        // unloaded), so a stale path gets re-probed the next time around.
        resolvedPaths?.let { return@withContext true }
        probeLocked()
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val paths = resolvedPaths ?: run {
            // Same defense as SurfaceFlingerEngine.apply — re-probe once
            // before silently no-op'ing. A user who pinned KCAL otherwise
            // sees an "available" engine that does nothing.
            if (probeLocked()) resolvedPaths else null
        } ?: run {
            Log.w(TAG, "apply: no resolved KCAL sysfs path; tint will not be visible")
            return@withContext
        }
        val s = matrix.scaledRgb()
        val r = (s[0] * 256f).toInt().coerceIn(0, 256)
        val g = (s[1] * 256f).toInt().coerceIn(0, 256)
        val b = (s[2] * 256f).toInt().coerceIn(0, 256)
        // kcal_min is optional on some kernels — we still attempt the write; the
        // shell's `>` redirect will fail quietly on a missing path without
        // breaking the rest of the script.
        val script = buildString {
            append("set -e\n")
            append("echo '1' > '").append(paths.enable).append("'\n")
            if (paths.min != null) {
                append("echo '20' > '").append(paths.min).append("' 2>/dev/null || true\n")
            }
            append("echo '$r $g $b' > '").append(paths.rgb).append("'\n")
        }
        invalidateOnFailure(Su.runShell(script), paths, "apply")
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val paths = resolvedPaths ?: return@withContext
        val exit = Su.runShell(
            """
            set -e
            echo '256 256 256' > '${paths.rgb}'
            echo '0' > '${paths.enable}'
            """.trimIndent()
        )
        invalidateOnFailure(exit, paths, "clear")
        Unit
    }

    private fun invalidateOnFailure(exitCode: Int, paths: Paths, operation: String) {
        if (exitCode == 0) return
        Log.w(TAG, "$operation failed for KCAL at ${paths.base} (exit=$exitCode); invalidating probe cache")
        resolvedPaths = null
    }

    /**
     * Walk [CANDIDATE_BASES] looking for a sysfs root that has both
     * `kcal` and `kcal_enable` nodes. Sets [resolvedPaths] on success.
     * Caller is responsible for the `Su.isAvailable()` gate; we skip
     * the probe entirely if there's no root shell.
     */
    private suspend fun probeLocked(): Boolean {
        if (!Su.isAvailable()) return false
        for (base in CANDIDATE_BASES) {
            val rgbPath = "$base/kcal"
            val enablePath = "$base/kcal_enable"
            val minPath = "$base/kcal_min"
            val test = Su.runCommand(
                "test -e '$rgbPath' && test -e '$enablePath' && echo ok"
            )
            if (test.exitCode == 0 && test.stdout.contains("ok")) {
                val hasMin = Su.runCommand("test -e '$minPath' && echo ok")
                val resolvedMin = if (hasMin.exitCode == 0 && hasMin.stdout.contains("ok")) minPath else null
                val paths = Paths(base = base, rgb = rgbPath, enable = enablePath, min = resolvedMin)
                Log.d(TAG, "probe: KCAL at $base (rgb=${paths.rgb}, enable=${paths.enable}, min=${paths.min ?: "absent"})")
                resolvedPaths = paths
                return true
            }
        }
        Log.w(TAG, "probe: no known KCAL sysfs surface found")
        return false
    }

    private data class Paths(
        val base: String,
        val rgb: String,
        val enable: String,
        /** Some kernel forks omit kcal_min; null when not present. */
        val min: String?
    )

    private companion object {
        const val TAG = "OpenLumen/KCAL"

        /**
         * Known KCAL sysfs roots, most-common first. New forks land here when a
         * driver report (Driver tab → Share report) shows a device whose kernel
         * exposes the KCAL nodes under a different parent.
         */
        val CANDIDATE_BASES: List<String> = listOf(
            // OnePlus / Nothing / OmniROM and the majority of Qualcomm custom kernels.
            "/sys/devices/platform/kcal_ctrl.0",
            // Some Snapdragon LineageOS branches.
            "/sys/module/msm_drm/parameters",
            // Older AnyKernel ROMs (rare; included for completeness).
            "/sys/class/misc/kcal"
        )
    }
}
