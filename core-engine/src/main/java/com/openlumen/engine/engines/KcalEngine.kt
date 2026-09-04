package com.openlumen.engine.engines

import android.content.Context
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineResult
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Writes to the KCAL kernel driver's sysfs nodes. Requires:
 *   - root
 *   - Qualcomm SoC
 *   - a custom kernel that exposes a KCAL sysfs surface
 *
 * KCAL is a scalar-per-channel driver (no cross-channel matrix). We map LumenMatrix to
 * RGB triplets in 0–255 and combine the dim factor into the per-channel scalar.
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

    /**
     * A scalar-per-channel kernel driver: no cross-channel terms, so grayscale
     * and the colour-vision presets fall back to their channel scales. Gamma is
     * folded into those scales by `LumenMatrix.scaledRgb`, and the panel driver
     * sits below the backlight so dim reaches past its minimum.
     */
    override val capabilities: Set<EngineCapability> = setOf(
        EngineCapability.PER_CHANNEL_GAMMA,
        EngineCapability.SUB_MINIMUM_DIM
    )

    @Volatile private var resolvedPaths: Paths? = null
    private val probeMutex = Mutex()

    /**
     * True while a non-identity transform this engine wrote is believed to
     * still be on the panel. Drives [clear]'s decision to re-probe rather than
     * report a success it did not perform (C256).
     */
    @Volatile private var appliedNonIdentity: Boolean = false

    /**
     * Kept outside [Paths] so `invalidateOnFailure` cannot discard it (C257).
     *
     * Read and written only through [loadRestore] and [storeRestore]. The
     * field and the file are one record in two places, and they were moved by
     * two separate statements: a clear that landed between them left the
     * kernel node and the record disagreeing about whether we had raised it
     * (C326).
     */
    @Volatile private var minRestore: MinRestore? = null
    private val restoreMutex = Mutex()

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
        ensureResolvedPaths() != null
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult = withContext(Dispatchers.IO) {
        val paths = ensureResolvedPaths() ?: run {
            Log.w(TAG, "apply: no resolved KCAL sysfs path; tint will not be visible")
            return@withContext EngineResult.Failure("no resolved KCAL sysfs path")
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
        val s = matrix.scalarRgb()
        val r = toKcalScalar(s[0], appFloor)
        val g = toKcalScalar(s[1], appFloor)
        val b = toKcalScalar(s[2], appFloor)

        // C166: only touch kcal_min when the user's original value is below our
        // safety floor. The `set -e` plus the trailing `|| true` on the min
        // write keeps the script idempotent if the path raced to be removed
        // between probe and apply.
        // C257: adopt any record a previous process left behind first. The min
        // write is best-effort, so it can silently fail and leave the kernel
        // still reporting a floor below SAFETY_MIN; the raise is retried in
        // that case, but the original must stay the value we saw the first
        // time, never one of our own raises read back.
        val existingRestore = loadRestore(context)
        val shouldRaiseMin =
            paths.min != null &&
                paths.originalMin != null &&
                paths.originalMin < SAFETY_MIN
        // C326: a record can outlive the state it describes. The kernel may
        // have stopped exposing `kcal_min` at all, or the user may have tuned
        // the floor above our own since we wrote it -- and `clear` trusts the
        // record, so it would put their value back to whatever we saw months
        // ago. Refusing to overwrite a floor the user chose is the same rule
        // C166 applies on the way in.
        //
        // Strictly greater, deliberately. Our own raise writes exactly
        // SAFETY_MIN and reads back as SAFETY_MIN, so treating equality as the
        // user's tuning would throw away the record for every panel we had
        // actually raised.
        val staleRecord = existingRestore != null && (
            paths.min == null ||
                (paths.originalMin != null && paths.originalMin > SAFETY_MIN)
            )
        if (staleRecord) {
            Log.i(TAG, "retiring a kcal_min record this panel has outgrown")
            storeRestore(context, null)
        }
        val minWriteScript = if (shouldRaiseMin) {
            // Latch before the script runs, not after it succeeds. The min
            // write carries `|| true`, so it can land while a later rgb write
            // fails the script — latching on overall success left the node
            // raised with no record that we had done it.
            val record = existingRestore?.takeIf { it.raised && !staleRecord }
                ?: MinRestore(originalMin = paths.originalMin, raised = true)
            storeRestore(context, record)
            "echo '$SAFETY_MIN' > '${paths.min}' 2>/dev/null || true\n"
        } else ""

        val script = buildString {
            append("set -e\n")
            append("echo '1' > '").append(paths.enable).append("'\n")
            append(minWriteScript)
            append("echo '$r $g $b' > '").append(paths.rgb).append("'\n")
        }
        val exit = Su.runShell(script)
        invalidateOnFailure(exit, paths, "apply")
        if (exit == 0) {
            if (matrix != LumenMatrix.IDENTITY) appliedNonIdentity = true
            EngineResult.Success
        } else {
            EngineResult.Failure("KCAL apply failed with exit code $exit")
        }
    }

    override suspend fun clear(context: Context): EngineResult = withContext(Dispatchers.IO) {
        // C166 / C257: read the durable record first. A raised `kcal_min` is
        // kernel state we own even when the apply that raised it failed, and
        // `appliedNonIdentity` is only latched on a successful apply — so
        // gating the re-probe on that alone let the one case C257 exists for
        // (min raised, rgb write failed, paths invalidated) return early and
        // strand the node.
        val record = loadRestore(context)
        val hasRaisedMin = record?.raised == true
        // C256: invalidateOnFailure drops resolvedPaths after a failed apply,
        // so a panel left tinted was exactly the case this reported as a
        // successful clear. Re-probe once, and report failure if the sysfs
        // surface still cannot be resolved.
        val paths = resolvedPaths
            ?: (if (appliedNonIdentity || hasRaisedMin) ensureResolvedPaths() else null)
            ?: return@withContext clearWithoutPaths(appliedNonIdentity || hasRaisedMin)
        // Take the original from the durable record rather than from `paths`,
        // which a failed apply may already have discarded.
        val restoreMin = paths.min != null && hasRaisedMin
        val minRestoreScript = if (restoreMin) {
            "echo '${record.originalMin}' > '${paths.min}' 2>/dev/null || true\n"
        } else ""
        val exit = Su.runShell(
            buildString {
                append("set -e\n")
                append("echo '$MAX_SCALAR $MAX_SCALAR $MAX_SCALAR' > '").append(paths.rgb).append("'\n")
                append("echo '0' > '").append(paths.enable).append("'\n")
                append(minRestoreScript)
            }
        )
        if (exit == 0 && restoreMin) {
            storeRestore(context, null)
        }
        invalidateOnFailure(exit, paths, "clear")
        if (exit == 0) {
            appliedNonIdentity = false
            EngineResult.Success
        } else {
            EngineResult.Failure("KCAL clear failed with exit code $exit")
        }
    }

    private suspend fun ensureResolvedPaths(): Paths? {
        resolvedPaths?.let { return it }
        return probeMutex.withLock {
            resolvedPaths ?: if (probeLocked()) resolvedPaths else null
        }
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
     * Probe state. The `kcal_min` restore record deliberately does NOT live
     * here (C257): `invalidateOnFailure` discards `resolvedPaths` on any failed
     * write, which took the user's original minimum with it and left the
     * kernel node raised with nothing able to put it back. It lives in
     * [minRestore] and on disk instead.
     */
    private data class Paths(
        val base: String,
        val rgb: String,
        val enable: String,
        /** Some kernel forks omit kcal_min; null when not present. */
        val min: String?,
        /** Value of `kcal_min` at probe time; null when not readable. */
        val originalMin: Int?
    )

    /**
     * The user's `kcal_min` before OpenLumen raised it, plus whether the raise
     * actually went out. Survives cache invalidation because it is a separate
     * field, and survives a process restart because it is mirrored to a file
     * in `filesDir` — a crash between raising the minimum and restoring it
     * would otherwise strand the kernel node permanently.
     */
    internal data class MinRestore(val originalMin: Int, val raised: Boolean) {
        fun encode(): String = "$originalMin $raised"

        companion object {
            fun decode(raw: String): MinRestore? {
                val parts = raw.trim().split(' ')
                if (parts.size != 2) return null
                val value = parts[0].toIntOrNull() ?: return null
                if (value < 0 || value > MAX_SCALAR) return null
                val raised = when (parts[1]) {
                    "true" -> true
                    "false" -> false
                    else -> return null
                }
                return MinRestore(value, raised)
            }
        }
    }

    /** The record this engine is working from, adopting a previous process's. */
    private suspend fun loadRestore(context: Context): MinRestore? = restoreMutex.withLock {
        minRestore ?: readPersistedRestore(context)?.also { minRestore = it }
    }

    /** Move both halves together, or a crash between them strands the node. */
    private suspend fun storeRestore(context: Context, record: MinRestore?) =
        restoreMutex.withLock {
            minRestore = record
            persistRestore(context, record)
        }

    private fun restoreFile(context: Context) = java.io.File(context.filesDir, MIN_RESTORE_FILE)

    private fun readPersistedRestore(context: Context): MinRestore? = runCatching {
        val file = restoreFile(context)
        if (!file.isFile || file.length() > MAX_RESTORE_FILE_BYTES) null
        else MinRestore.decode(file.readText())
    }.getOrNull()

    private fun persistRestore(context: Context, record: MinRestore?) {
        runCatching {
            val file = restoreFile(context)
            if (record == null) file.delete() else file.writeText(record.encode())
        }.onFailure { Log.w(TAG, "could not persist kcal_min restore record: ${it.message}") }
    }

    companion object {

        /** Durable mirror of the `kcal_min` restore record (C257). */
        private const val MIN_RESTORE_FILE = "kcal-min-restore"

        /** The record is two short tokens; anything larger is corrupt. */
        private const val MAX_RESTORE_FILE_BYTES = 64L

        /**
         * What `clear()` reports when the sysfs surface cannot be resolved,
         * even after a re-probe (C256).
         *
         * Success is only honest when this engine never wrote to the panel. If
         * it did, the panel is still tinted and `invalidateOnFailure` has
         * dropped the paths, so the service has to hear about it and escalate.
         */
        internal fun clearWithoutPaths(appliedNonIdentity: Boolean): EngineResult =
            if (appliedNonIdentity) {
                EngineResult.Failure("KCAL transform is applied but no sysfs path could be resolved")
            } else {
                EngineResult.Success
            }

        const val TAG = "OpenLumen/KCAL"

        /**
         * Floor on the post-scaling subpixel value. KCAL's `kcal_min` is
         * a global per-channel minimum the driver enforces after our
         * RGB scalars apply. Without it, aggressive scaling like
         * `(r=10, g=0, b=0)` can drive subpixels fully off, which on
         * some panels produces flicker. 20/256 ≈ 8% — visually
         * imperceptible in normal use but enough to keep the panel
         * stable. 20/255 is visually subtle but enough to keep the panel
         * away from zero on kernels without a readable `kcal_min`. The
         * value was inherited from the original CF.Lumen
         * reference; revisit if device reports show a different
         * threshold is required for a given panel.
         */
        const val SAFETY_MIN: Int = 20
        const val MAX_SCALAR: Int = 255

        internal fun toKcalScalar(scale: Float, floor: Int): Int {
            val clampedFloor = floor.coerceIn(0, MAX_SCALAR)
            val clampedScale = if (scale.isNaN() || scale.isInfinite()) 1f else scale.coerceIn(0f, 1f)
            return (clampedScale * MAX_SCALAR).toInt().coerceIn(clampedFloor, MAX_SCALAR)
        }

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

        suspend fun clearKnownPaths(): List<String> {
            if (!Su.isAvailable()) return emptyList()
            val cleared = mutableListOf<String>()
            for (base in CANDIDATE_BASES) {
                val rgbPath = "$base/kcal"
                val enablePath = "$base/kcal_enable"
                val test = Su.runCommand(
                    "test -e '$rgbPath' && test -e '$enablePath' && echo ok"
                )
                if (test.exitCode != 0 || !test.stdout.contains("ok")) continue
                val exit = Su.runShell(
                    buildString {
                        append("set -e\n")
                        append("echo '$MAX_SCALAR $MAX_SCALAR $MAX_SCALAR' > '").append(rgbPath).append("'\n")
                        append("echo '0' > '").append(enablePath).append("'\n")
                    }
                )
                Su.resetCacheIfSuLikelyFailed(exit)
                if (exit == 0) cleared += base
            }
            return cleared
        }
    }
}
