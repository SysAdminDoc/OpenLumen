package com.openlumen.diagnostics

import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.effectiveMatrix

/**
 * Pure function: convert a [Preferences] snapshot to the [LumenMatrix] the
 * engine would receive next. Mirrors `LumenService.matrixFor()` so the UI
 * can render previews (color swatches, blue-channel-suppression indicators)
 * without coupling to the service or kicking off an actual engine apply.
 *
 * Both the service and the UI call this; they must stay in sync. If you
 * change the math here, change it in the service too — and ideally factor
 * the service path to call back into this object instead of duplicating.
 *
 * Tied to roadmap candidate **C61** (Melanopic / blue-suppression
 * indicator) — the UI uses `[2]` of the returned `scaledRgb()` to derive
 * the indicator value.
 */
object MatrixPreview {

    /**
     * Compute the effective [LumenMatrix] for the given preferences.
     * Mirrors LumenService.matrixFor() — preset OR custom matrix, intensity
     * lerp, gamma, the preset-owned dim combined with the user's additional
     * dim control, contrast scaling + center bias, AMOLED clamp pass-through.
     */
    /**
     * The effective matrix for these preferences.
     *
     * The computation itself is [effectiveMatrix] in core-prefs. It used to
     * live here, in a class named for previews, with a comment asking whoever
     * changed it to remember to change the service too. The service called
     * this anyway, so there was one implementation with two names and a note
     * where the shared ownership should have been.
     */
    fun matrixFor(p: Preferences): LumenMatrix = p.effectiveMatrix()

    /**
     * Blue-channel suppression as a 0..1 fraction. 0.0 means full blue
     * (identity); 1.0 means blue is fully removed. Computed from the
     * effective scaled-RGB triplet so it honors intensity, dim, contrast,
     * gamma, and AMOLED clamp.
     *
     * This is a physical measurement of the output, not a health metric —
     * see `docs/health-evidence.md` for what the app does and does not
     * claim. Surfaced as a numeric indicator in the Home tab for users
     * who want to know how much they've turned blue down.
     */
    fun blueSuppression(p: Preferences): Float {
        val rgb = transformedWhiteRgb(matrixFor(p))
        val blue = rgb.getOrNull(2)?.coerceIn(0f, 1f) ?: 1f
        return (1f - blue).coerceIn(0f, 1f)
    }

    /**
     * Relative luminance reduction for transformed white, using Rec. 709 /
     * sRGB luminance weights. This is a display-output metric, not a sleep
     * or medical efficacy claim.
     */
    fun perceivedLuminanceReduction(p: Preferences): Float {
        val matrix = matrixFor(p)
        val rgb = transformedWhiteRgb(matrix)
        val r = rgb.getOrNull(0) ?: 1f
        val g = rgb.getOrNull(1) ?: 1f
        val b = rgb.getOrNull(2) ?: 1f
        val luminance =
            (0.2126f * r.coerceIn(0f, 1f)) +
                (0.7152f * g.coerceIn(0f, 1f)) +
                (0.0722f * b.coerceIn(0f, 1f))
        return (1f - luminance).coerceIn(0f, 1f)
    }

    private fun transformedWhiteRgb(matrix: LumenMatrix): FloatArray {
        if (!matrix.hasColorMatrix) return matrix.scalarRgb()
        val m = matrix.surfaceRgbMatrix()
        return floatArrayOf(
            (m[0] + m[1] + m[2] + matrix.biasR).coerceIn(0f, 1f),
            (m[3] + m[4] + m[5] + matrix.biasG).coerceIn(0f, 1f),
            (m[6] + m[7] + m[8] + matrix.biasB).coerceIn(0f, 1f)
        )
    }

    private const val MATRIX_COEFF_MIN = -4f
    private const val MATRIX_COEFF_MAX = 4f
    private const val MAX_DIM = 0.95f
}
