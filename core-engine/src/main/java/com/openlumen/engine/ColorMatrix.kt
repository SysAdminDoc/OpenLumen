package com.openlumen.engine

/**
 * 4x5 color matrix in RGBA-out, RGBA-in transform form.
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
    val gammaB: Float = 1f,
    /**
     * AMOLED black clamp (roadmap **C66**). When set, any channel scalar
     * computed by [scaledRgb] below [AMOLED_CLAMP_THRESHOLD] snaps to 0.
     * On OLED panels this turns the relevant subpixels fully off, which
     * is a measurable power saving in the warm/dim end of the tinting
     * range. On LCD panels this is a no-op (the backlight stays lit
     * regardless of pixel value).
     */
    val amoledClamp: Boolean = false,
    /**
     * Optional full RGB transform for matrix-capable engines. The scalar
     * [r], [g], and [b] fields remain the fallback for CDM, KCAL, and the
     * rootless overlay path, which cannot consume cross-channel terms.
     */
    val hasColorMatrix: Boolean = false,
    val matrixRr: Float = 1f,
    val matrixRg: Float = 0f,
    val matrixRb: Float = 0f,
    val matrixGr: Float = 0f,
    val matrixGg: Float = 1f,
    val matrixGb: Float = 0f,
    val matrixBr: Float = 0f,
    val matrixBg: Float = 0f,
    val matrixBb: Float = 1f
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
        val scaled = floatArrayOf(
            Math.pow(rd, 1.0 / gammaR.finiteIn(0.05f, 5f, default = 1f)).toFloat(),
            Math.pow(gd, 1.0 / gammaG.finiteIn(0.05f, 5f, default = 1f)).toFloat(),
            Math.pow(bd, 1.0 / gammaB.finiteIn(0.05f, 5f, default = 1f)).toFloat()
        )
        if (amoledClamp) {
            for (i in scaled.indices) {
                if (scaled[i] < AMOLED_CLAMP_THRESHOLD) scaled[i] = 0f
            }
        }
        return scaled
    }

    /**
     * Row-major 3x3 RGB transform for matrix-capable engines. Normal scalar
     * matrices are represented as a diagonal transform; CVD presets can supply
     * off-diagonal terms while still retaining scalar fallbacks.
     */
    fun surfaceRgbMatrix(): FloatArray {
        if (!hasColorMatrix) {
            val s = scaledRgb()
            return floatArrayOf(
                s[0], 0f, 0f,
                0f, s[1], 0f,
                0f, 0f, s[2]
            )
        }
        val dimFactor = 1f - effectiveDim
        val rowR = rowScale(dimFactor, gammaR)
        val rowG = rowScale(dimFactor, gammaG)
        val rowB = rowScale(dimFactor, gammaB)
        return floatArrayOf(
            matrixRr.matrixCoeff(default = 1f) * rowR,
            matrixRg.matrixCoeff(default = 0f) * rowR,
            matrixRb.matrixCoeff(default = 0f) * rowR,
            matrixGr.matrixCoeff(default = 0f) * rowG,
            matrixGg.matrixCoeff(default = 1f) * rowG,
            matrixGb.matrixCoeff(default = 0f) * rowG,
            matrixBr.matrixCoeff(default = 0f) * rowB,
            matrixBg.matrixCoeff(default = 0f) * rowB,
            matrixBb.matrixCoeff(default = 1f) * rowB
        )
    }

    /** 4x4 column-major matrix for SurfaceFlinger. */
    fun toSurfaceFlinger16(): FloatArray {
        val m = surfaceRgbMatrix()
        return floatArrayOf(
            m[0], m[3], m[6], 0f,
            m[1], m[4], m[7], 0f,
            m[2], m[5], m[8], 0f,
            biasR.finiteIn(-1f, 1f, default = 0f),
            biasG.finiteIn(-1f, 1f, default = 0f),
            biasB.finiteIn(-1f, 1f, default = 0f),
            1f
        )
    }

    /** ARGB color for the rootless overlay fallback. */
    fun toOverlayArgb(): Int {
        val s = scaledRgb()
        val deficitR = (1f - s[0].coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val deficitG = (1f - s[1].coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val deficitB = (1f - s[2].coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val overlayAlpha = maxOf(deficitR, deficitG, deficitB).coerceIn(0f, MAX_OVERLAY_ALPHA)
        if (overlayAlpha <= 0f) return 0
        val a = (overlayAlpha * 255f).toInt().coerceIn(0, 204)
        val rr = overlayChannel(deficitR, overlayAlpha)
        val gg = overlayChannel(deficitG, overlayAlpha)
        val bb = overlayChannel(deficitB, overlayAlpha)
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
        val useMatrix = hasColorMatrix || target.hasColorMatrix
        val fromMatrix = matrixFieldsOrDiagonal()
        val toMatrix = target.matrixFieldsOrDiagonal()
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
            gammaB = lerpF(gammaB, target.gammaB, u),
            amoledClamp = if (u < 0.5f) amoledClamp else target.amoledClamp,
            hasColorMatrix = useMatrix,
            matrixRr = lerpF(fromMatrix[0], toMatrix[0], u),
            matrixRg = lerpF(fromMatrix[1], toMatrix[1], u),
            matrixRb = lerpF(fromMatrix[2], toMatrix[2], u),
            matrixGr = lerpF(fromMatrix[3], toMatrix[3], u),
            matrixGg = lerpF(fromMatrix[4], toMatrix[4], u),
            matrixGb = lerpF(fromMatrix[5], toMatrix[5], u),
            matrixBr = lerpF(fromMatrix[6], toMatrix[6], u),
            matrixBg = lerpF(fromMatrix[7], toMatrix[7], u),
            matrixBb = lerpF(fromMatrix[8], toMatrix[8], u)
        )
    }

    /** Scale preset strength from identity to this matrix. */
    fun withIntensity(strength: Float): LumenMatrix {
        val u = strength.coerceIn(0f, 1f)
        val scalar = copy(
            r = 1f + (r - 1f) * u,
            g = 1f + (g - 1f) * u,
            b = 1f + (b - 1f) * u,
            dim = dim * u,
            biasR = biasR * u,
            biasG = biasG * u,
            biasB = biasB * u,
            gammaR = 1f + (gammaR - 1f) * u,
            gammaG = 1f + (gammaG - 1f) * u,
            gammaB = 1f + (gammaB - 1f) * u
        )
        if (!hasColorMatrix) return scalar
        return scalar.copy(
            matrixRr = lerpF(1f, matrixRr, u),
            matrixRg = lerpF(0f, matrixRg, u),
            matrixRb = lerpF(0f, matrixRb, u),
            matrixGr = lerpF(0f, matrixGr, u),
            matrixGg = lerpF(1f, matrixGg, u),
            matrixGb = lerpF(0f, matrixGb, u),
            matrixBr = lerpF(0f, matrixBr, u),
            matrixBg = lerpF(0f, matrixBg, u),
            matrixBb = lerpF(1f, matrixBb, u)
        )
    }

    private fun rowScale(dimFactor: Float, gamma: Float): Float {
        val scaled = Math.pow(
            dimFactor.coerceIn(0f, 1f).toDouble(),
            1.0 / gamma.finiteIn(0.05f, 5f, default = 1f)
        ).toFloat()
        return if (amoledClamp && scaled < AMOLED_CLAMP_THRESHOLD) 0f else scaled
    }

    private fun matrixFieldsOrDiagonal(): FloatArray =
        if (hasColorMatrix) {
            floatArrayOf(
                matrixRr, matrixRg, matrixRb,
                matrixGr, matrixGg, matrixGb,
                matrixBr, matrixBg, matrixBb
            )
        } else {
            floatArrayOf(
                r, 0f, 0f,
                0f, g, 0f,
                0f, 0f, b
            )
        }

    private fun lerpF(a: Float, b: Float, u: Float): Float = a + (b - a) * u

    private fun Float.finiteIn(min: Float, max: Float, default: Float): Float =
        if (isFinite()) coerceIn(min, max) else default

    private fun Float.matrixCoeff(default: Float): Float =
        finiteIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX, default = default)

    private fun overlayChannel(deficit: Float, alpha: Float): Int =
        ((1f - (deficit / alpha)).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)

    companion object {
        val IDENTITY = LumenMatrix()

        /**
         * Channel scalar below this value snaps to 0 when `amoledClamp` is
         * set. 0.02 is below the noise floor of typical 8-bit panels (which
         * map roughly 1/255 = 0.004 per LSB), so the clamp only affects
         * values the panel would otherwise display as very dim — exactly the
         * range where OLED true-black savings are useful.
         */
        const val AMOLED_CLAMP_THRESHOLD: Float = 0.02f

        private const val MATRIX_COEFF_MIN: Float = -4f
        private const val MATRIX_COEFF_MAX: Float = 4f
        private const val MAX_OVERLAY_ALPHA: Float = 0.80f
    }
}
