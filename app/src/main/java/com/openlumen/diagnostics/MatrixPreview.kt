package com.openlumen.diagnostics

import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.prefs.Preferences

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
     * lerp, gamma + dim from the custom matrix, contrast scaling + center
     * bias, AMOLED clamp pass-through.
     */
    fun matrixFor(p: Preferences): LumenMatrix {
        val preset = Presets.byKey(p.activePresetKey)?.matrix
        val raw = preset ?: LumenMatrix(
            r = p.customMatrix.r,
            g = p.customMatrix.g,
            b = p.customMatrix.b
        )
        val t = p.presetIntensity.coerceIn(0f, 1f)
        val intensityScaled = raw.copy(
            r = 1f + (raw.r - 1f) * t,
            g = 1f + (raw.g - 1f) * t,
            b = 1f + (raw.b - 1f) * t,
            gammaR = p.customMatrix.gammaR,
            gammaG = p.customMatrix.gammaG,
            gammaB = p.customMatrix.gammaB,
            dim = p.dim,
            amoledClamp = p.amoledBlackClamp
        )
        return applyContrast(intensityScaled, p.contrast)
    }

    private fun applyContrast(m: LumenMatrix, contrast: Float): LumenMatrix {
        val c = contrast.coerceIn(Preferences.CONTRAST_MIN, Preferences.CONTRAST_MAX)
        if (c == 1f) return m
        val bias = (1f - c) * 0.5f
        return m.copy(
            r = (m.r * c).coerceIn(0f, 2f),
            g = (m.g * c).coerceIn(0f, 2f),
            b = (m.b * c).coerceIn(0f, 2f),
            biasR = (m.biasR + bias).coerceIn(-1f, 1f),
            biasG = (m.biasG + bias).coerceIn(-1f, 1f),
            biasB = (m.biasB + bias).coerceIn(-1f, 1f)
        )
    }

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
        val rgb = matrixFor(p).scaledRgb()
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
        val rgb = matrix.scaledRgb()
        val r = (rgb.getOrNull(0) ?: 1f) + matrix.biasR
        val g = (rgb.getOrNull(1) ?: 1f) + matrix.biasG
        val b = (rgb.getOrNull(2) ?: 1f) + matrix.biasB
        val luminance =
            (0.2126f * r.coerceIn(0f, 1f)) +
                (0.7152f * g.coerceIn(0f, 1f)) +
                (0.0722f * b.coerceIn(0f, 1f))
        return (1f - luminance).coerceIn(0f, 1f)
    }
}
