package com.openlumen.engine

/**
 * 4x5 color matrix in RGBA-out, RGBA-in column-major SurfaceFlinger order.
 *
 * SurfaceFlinger's 1015 transaction code accepts a 16-element float[] representing
 * a 4x4 matrix (no translation column; that is what the per-channel `bias` provides).
 * We carry bias separately because most consumer engines only animate RGB scale +
 * a global darken term and skip cross-channel terms.
 */
data class LumenMatrix(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val biasR: Float = 0f,
    val biasG: Float = 0f,
    val biasB: Float = 0f,
    val dim: Float = 0f,
    val gammaR: Float = 1f,
    val gammaG: Float = 1f,
    val gammaB: Float = 1f
) {
    /** Scale factor [0,1] for a final brightness reduction beyond panel minimum. */
    val effectiveDim: Float get() = dim.finiteIn(0f, 0.95f, default = 0f)

    /**
     * Per-channel scalar after dim and per-channel gamma have been folded in.
     *
     * Gamma math: effective = pow(scale * (1 - dim), 1 / gamma).
     * At gamma=1.0 (default) this collapses to the simple `scale * (1 - dim)` form.
     * Gamma > 1.0 lifts mid-tones (less aggressive); gamma < 1.0 deepens them.
     *
     * For overlay/scalar engines this is a "feel" knob, not a per-pixel gamma —
     * a true gamma LUT would require touching the GPU shader, which root engines
     * effectively do via SurfaceFlinger's color matrix but is out of reach for
     * the overlay path.
     */
    fun scaledRgb(): FloatArray {
        val dimFactor = (1f - effectiveDim).toDouble()
        val rd = (r.finiteIn(0f, 1f, default = 1f).toDouble() * dimFactor).coerceAtLeast(0.0)
        val gd = (g.finiteIn(0f, 1f, default = 1f).toDouble() * dimFactor).coerceAtLeast(0.0)
        val bd = (b.finiteIn(0f, 1f, default = 1f).toDouble() * dimFactor).coerceAtLeast(0.0)
        return floatArrayOf(
            Math.pow(rd, 1.0 / gammaR.finiteIn(0.05f, 5f, default = 1f)).toFloat(),
            Math.pow(gd, 1.0 / gammaG.finiteIn(0.05f, 5f, default = 1f)).toFloat(),
            Math.pow(bd, 1.0 / gammaB.finiteIn(0.05f, 5f, default = 1f)).toFloat()
        )
    }

    /** 4x4 row-major matrix for SurfaceFlinger. */
    fun toSurfaceFlinger16(): FloatArray {
        val s = scaledRgb()
        return floatArrayOf(
            s[0], 0f,   0f,   0f,
            0f,   s[1], 0f,   0f,
            0f,   0f,   s[2], 0f,
            biasR.finiteIn(-1f, 1f, default = 0f),
            biasG.finiteIn(-1f, 1f, default = 0f),
            biasB.finiteIn(-1f, 1f, default = 0f),
            1f
        )
    }

    /** ARGB color for the rootless overlay fallback. */
    fun toOverlayArgb(): Int {
        val s = scaledRgb()
        val tintStrength = maxOf(1f - s[0], 1f - s[1], 1f - s[2]).coerceIn(0f, 1f)
        val overlayAlpha = maxOf(effectiveDim * 0.80f, tintStrength * 0.70f)
            .coerceIn(0f, 0.80f)
        val a = (overlayAlpha * 255f).toInt().coerceIn(0, 204)
        val rr = (s[0] * 255f).toInt().coerceIn(0, 255)
        val gg = (s[1] * 255f).toInt().coerceIn(0, 255)
        val bb = (s[2] * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    /**
     * Linearly interpolate every field of this matrix toward [target] by
     * factor `t` (clamped to 0..1). At `t = 0` returns `this`; at `t = 1`
     * returns [target]. Each field is interpolated independently — there's
     * no perceptual color-space conversion. This is the right primitive for
     * smooth schedule transitions (roadmap C23/C24): the visual result is
     * a steady glide, and the user sees consecutive intermediate matrices
     * apply over time.
     */
    fun lerp(target: LumenMatrix, t: Float): LumenMatrix {
        val u = t.coerceIn(0f, 1f)
        if (u <= 0f) return this
        if (u >= 1f) return target
        return LumenMatrix(
            r = lerpF(r, target.r, u),
            g = lerpF(g, target.g, u),
            b = lerpF(b, target.b, u),
            biasR = lerpF(biasR, target.biasR, u),
            biasG = lerpF(biasG, target.biasG, u),
            biasB = lerpF(biasB, target.biasB, u),
            dim = lerpF(dim, target.dim, u),
            gammaR = lerpF(gammaR, target.gammaR, u),
            gammaG = lerpF(gammaG, target.gammaG, u),
            gammaB = lerpF(gammaB, target.gammaB, u)
        )
    }

    private fun lerpF(a: Float, b: Float, u: Float): Float = a + (b - a) * u

    private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
        if (isFinite()) coerceIn(min, max) else default

    companion object {
        val IDENTITY = LumenMatrix()
    }
}
