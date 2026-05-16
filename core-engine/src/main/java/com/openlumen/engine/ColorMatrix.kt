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
    val effectiveDim: Float get() = dim.coerceIn(0f, 0.95f)

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
        val rd = (r.toDouble() * dimFactor).coerceAtLeast(0.0)
        val gd = (g.toDouble() * dimFactor).coerceAtLeast(0.0)
        val bd = (b.toDouble() * dimFactor).coerceAtLeast(0.0)
        return floatArrayOf(
            Math.pow(rd, 1.0 / gammaR.coerceAtLeast(0.05f)).toFloat(),
            Math.pow(gd, 1.0 / gammaG.coerceAtLeast(0.05f)).toFloat(),
            Math.pow(bd, 1.0 / gammaB.coerceAtLeast(0.05f)).toFloat()
        )
    }

    /** 4x4 row-major matrix for SurfaceFlinger. */
    fun toSurfaceFlinger16(): FloatArray {
        val s = scaledRgb()
        return floatArrayOf(
            s[0], 0f,   0f,   0f,
            0f,   s[1], 0f,   0f,
            0f,   0f,   s[2], 0f,
            biasR, biasG, biasB, 1f
        )
    }

    /** ARGB color for overlay multiply blend. */
    fun toOverlayArgb(): Int {
        val s = scaledRgb()
        val a = (effectiveDim * 0.80f * 255f).toInt().coerceIn(0, 204)
        val rr = (s[0] * 255f).toInt().coerceIn(0, 255)
        val gg = (s[1] * 255f).toInt().coerceIn(0, 255)
        val bb = (s[2] * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    companion object {
        val IDENTITY = LumenMatrix()
    }
}
