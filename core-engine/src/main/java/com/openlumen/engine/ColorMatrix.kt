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

    /** Per-channel scalar after dim is folded in. */
    fun scaledRgb(): FloatArray = floatArrayOf(
        r * (1f - effectiveDim),
        g * (1f - effectiveDim),
        b * (1f - effectiveDim)
    )

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
