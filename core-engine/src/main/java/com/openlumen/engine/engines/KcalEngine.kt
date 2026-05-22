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
 *
 * **`kcal_min` policy (roadmap C166).** Per-channel KCAL scalars can drive a
 * subpixel down to zero, which on some Qualcomm panels causes flicker or a
 * brief black-frame artifact at the channel boundary. The driver's
 * `kcal_min` node is a global floor that prevents any final value from
 * dropping below the configured threshold. We do NOT overwrite the user's
 * existing `kcal_min` unconditionally — that would silently change a
 * kernel parameter the user might have tuned themselves. Instead we:
 *
 *  1. At probe time, read the current value once and remember it as
 *     `originalMin`.
 *  2. On `apply`, only write a higher minimum (`SAFETY_MIN`) if the user's
 *     original was lower, and only on the first apply since probe.
 *  3. On `clear`, restore the original value if we changed it.
 *
 * This keeps OpenLumen from silently mutating kernel state on root devices
 * and means uninstalling the app leaves the system in the state the user
 * found it.
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
        // When the kernel doesn't expose `kcal_min`, OR our probe couldn't
        // read the user's existing minimum, the C166 raise-and-restore
        // path can't kick in. On those panels a per-channel write of 0
        // can cause flicker or a black-frame artifact at the boundary
        // (this is what `kcal_min` exists to prevent). Clamp the scaled
        // channels at the app layer to the same SAFETY_MIN floor as a
        // defensive fallback. AMOLED-clamp users opting into true zero
        // already accept that risk and write through `LumenMatrix.scaledRgb`
        // which can return 0; we only enforce this floor when AMOLED
        // clamp is off so we don't surprise the opt-in workflow.
        val needAppLevelFloor =
            paths.min == null || paths.originalMin == null
        val appFloor = if (needAppLevelFloor && !matrix.amoledClamp) SAFETY_MIN else 0
        val s = matrix.scaledRgb()
        val r = (s[0] * 256f).toInt().coerceIn(appFloor, 256)
        val g = (s[1] * 256f).toInt().coerceIn(appFloor, 256)
        val b = (s[2] * 256f).toInt().coerceIn(appFloor, 256)

        // C166: only touch kcal_min when the user's original value is
        // below our safety floor, and only once per probed session. The
        // `set -e` plus the trailing `|| true` on the min write keeps the
        // script idempotent if the path raced to be removed between
        // probe and apply.
        val shouldRaiseMin =
            paths.min != null &&
                paths.originalMin != null &&
                paths.originalMin < SAFETY_MIN &&
                !paths.raisedMin.value
        val minWriteScript = if (shouldRaiseMin) {
            "echo '$SAFETY_MIN' > '${paths.min}' 2>/dev/null || true\n"
        } else ""

        val script = buildString {
            append("set -e\n")
            append("echo '1' > '").append(paths.enable).append("'\n")
            append(minWriteScript)
            append("echo '$r $g $b' > '").append(paths.rgb).append("'\n")
        }
        val exit = Su.runShell(script)
        if (exit == 0 && shouldRaiseMin) {
            // Latch the per-session "we raised the min" flag so a subsequent
            // apply doesn't waste a write doing the same thing. clear() reads
            // this latch to decide whether to restore.
            paths.raisedMin.value = true
        }
        invalidateOnFailure(exit, paths, "apply")
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val paths = resolvedPaths ?: return@withContext
        // C166: only restore kcal_min if we actually raised it during this
        // probed session. If we never touched it, leave the user's kernel
        // configuration alone.
        val restoreMin = paths.min != null &&
            paths.originalMin != null &&
            paths.raisedMin.value
        val minRestoreScript = if (restoreMin) {
            "echo '${paths.originalMin}' > '${paths.min}' 2>/dev/null || true\n"
        } else ""
        val exit = Su.runShell(
            buildString {
                append("set -e\n")
                append("echo '256 256 256' > '").append(paths.rgb).append("'\n")
                append("echo '0' > '").append(paths.enable).append("'\n")
                append(minRestoreScript)
            }
        )
        if (exit == 0 && restoreMin) {
            paths.raisedMin.value = false
        }
        invalidateOnFailure(exit, paths, "clear")
        Unit
    }

    private fun invalidateOnFailure(exitCode: Int, paths: Paths, operation: String) {
        if (exitCode == 0) return
        Log.w(TAG, "$operation failed for KCAL at ${paths.base} (exit=$exitCode); invalidating probe cache")
        resolvedPaths = null
        // 127 / -1 typically mean su itself is gone (Magisk denied or
        // uninstalled while we were running). Drop the process-wide su
        // cache too so the next driver probe re-checks instead of returning
        // a stale "available" we can't actually use.
        Su.resetCacheIfSuLikelyFailed(exitCode)
    }

    /**
     * Walk [CANDIDATE_BASES] looking for a sysfs root that has both
     * `kcal` and `kcal_enable` nodes. Sets [resolvedPaths] on success.
     * Caller is responsible for the `Su.isAvailable()` gate; we skip
     * the probe entirely if there's no root shell.
     *
     * When `kcal_min` is present, we also read its current value into
     * `Paths.originalMin` so the C166 "raise-and-restore" policy in
     * `apply` / `clear` has the user's pre-OpenLumen state recorded.
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
                val originalMin = if (resolvedMin != null) readIntOrNull("cat '$resolvedMin'") else null
                val paths = Paths(
                    base = base,
                    rgb = rgbPath,
                    enable = enablePath,
                    min = resolvedMin,
                    originalMin = originalMin
                )
                Log.d(
                    TAG,
                    "probe: KCAL at $base (rgb=${paths.rgb}, enable=${paths.enable}," +
                        " min=${paths.min ?: "absent"}, originalMin=${paths.originalMin ?: "n/a"})"
                )
                resolvedPaths = paths
                return true
            }
        }
        Log.w(TAG, "probe: no known KCAL sysfs surface found")
        return false
    }

    /**
     * Best-effort: run [cmd] under su and parse the first integer out of
     * stdout. Returns null if the command fails, returns nothing, or the
     * value doesn't parse. Used only by the probe path so we can be
     * relaxed about failures.
     */
    private suspend fun readIntOrNull(cmd: String): Int? {
        val r = Su.runCommand(cmd)
        if (r.exitCode != 0) return null
        return r.stdout
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.toIntOrNull()
    }

    /**
     * Probe state. `raisedMin` is a per-session latch — `apply` flips it
     * to `true` only after we successfully wrote our SAFETY_MIN over a
     * smaller original; `clear` reads it to decide whether to restore.
     * Wrapped in a tiny mutable holder so the data class itself stays
     * immutable for cache identity semantics.
     */
    private data class Paths(
        val base: String,
        val rgb: String,
        val enable: String,
        /** Some kernel forks omit kcal_min; null when not present. */
        val min: String?,
        /** Value of `kcal_min` at probe time; null when not readable. */
        val originalMin: Int?,
        val raisedMin: BoolHolder = BoolHolder()
    )

    private class BoolHolder { @Volatile var value: Boolean = false }

    private companion object {
        const val TAG = "OpenLumen/KCAL"

        /**
         * Floor on the post-scaling subpixel value. KCAL's `kcal_min` is
         * a global per-channel minimum the driver enforces after our
         * RGB scalars apply. Without it, aggressive scaling like
         * `(r=10, g=0, b=0)` can drive subpixels fully off, which on
         * some panels produces flicker. 20/256 ≈ 8% — visually
         * imperceptible in normal use but enough to keep the panel
         * stable. The value was inherited from the original CF.Lumen
         * reference; revisit if device reports show a different
         * threshold is required for a given panel.
         */
        const val SAFETY_MIN: Int = 20

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
