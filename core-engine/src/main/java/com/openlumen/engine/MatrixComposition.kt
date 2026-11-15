package com.openlumen.engine

/**
 * Composing two dim reductions, and scaling a matrix's contrast.
 *
 * Both lived in the app module, inside a class named for previews, even though
 * the service was the main caller. The arithmetic belongs with the matrix it
 * operates on.
 */

/** The overlay driver's ceiling, and the slider's, so nothing composes past it. */
const val MAX_DIM: Float = 0.95f

private const val MATRIX_COEFF_MIN = -4f
private const val MATRIX_COEFF_MAX = 4f

/**
 * Rec. 709 luma weights. The same ones `DisplayTransformManager` uses for the
 * system's own grayscale and LineageOS uses for reading mode, so a gray made
 * here matches the gray the user already knows from those.
 */
const val LUMA_R: Float = 0.2126f
const val LUMA_G: Float = 0.7152f
const val LUMA_B: Float = 0.0722f

/**
 * Take the picture to gray first and then apply this matrix to it (#32).
 *
 * The point is a tint that survives the gray: amber over gray reads as amber
 * monochrome, not as gray, which is what someone sensitive to colour asks for
 * when they want to drop colour and keep the warmth. Row-major arithmetic is
 * `this x G`, where `G = (1 - amount) I + amount L` and every row of `L` is the
 * luma weights, so the gray is computed from the source pixel and only then
 * tinted.
 *
 * A scalar preset becomes a diagonal matrix here. Its entries carry the
 * channel gamma, because the matrix path applies gamma to the dim factor
 * alone; with `pow(r, 1 / gamma)` on the diagonal the product comes out as
 * `pow(r * (1 - dim), 1 / gamma)`, the same value the scalar path renders.
 *
 * Drivers that cannot multiply read the result through [LumenMatrix.daltonizer]
 * and [LumenMatrix.desaturation]; the overlay and KCAL keep only the tint,
 * because neither can take colour out of the picture.
 */
fun LumenMatrix.withGrayscale(amount: Float = 1f): LumenMatrix {
    if (!amount.isFinite()) return this
    val a = amount.coerceIn(0f, 1f)
    if (a == 0f) return this
    val base = if (hasColorMatrix) {
        floatArrayOf(
            matrixRr, matrixRg, matrixRb,
            matrixGr, matrixGg, matrixGb,
            matrixBr, matrixBg, matrixBb
        )
    } else {
        floatArrayOf(
            gammaFolded(r, gammaR), 0f, 0f,
            0f, gammaFolded(g, gammaG), 0f,
            0f, 0f, gammaFolded(b, gammaB)
        )
    }
    val luma = floatArrayOf(LUMA_R, LUMA_G, LUMA_B)
    val gray = FloatArray(9) { i ->
        val row = i / 3
        val col = i % 3
        (if (row == col) 1f - a else 0f) + a * luma[col]
    }
    val m = FloatArray(9) { i ->
        val row = i / 3
        val col = i % 3
        base[row * 3] * gray[col] + base[row * 3 + 1] * gray[3 + col] + base[row * 3 + 2] * gray[6 + col]
    }
    // A correction mode means the scalars are that mode's fallback rather
    // than a tint, so there is no tint to keep over the gray and the mode
    // alone renders it. Otherwise the gray stage composes with any earlier
    // one, so applying this twice never undoes it: a matrix an earlier pass
    // already marked carries its own monochromacy, and its scalars are still
    // the tint.
    val scalarsAreATint = daltonizer == Daltonizer.NONE || desaturation > 0f
    val combined = if (scalarsAreATint) {
        1f - (1f - desaturation.coerceIn(0f, 1f)) * (1f - a)
    } else {
        0f
    }
    val grayEnough = if (scalarsAreATint) combined >= 0.5f else a >= 0.5f
    return copy(
        hasColorMatrix = true,
        matrixRr = m[0].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixRg = m[1].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixRb = m[2].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGr = m[3].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGg = m[4].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGb = m[5].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBr = m[6].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBg = m[7].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBb = m[8].coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        daltonizer = if (grayEnough) Daltonizer.MONOCHROMACY else daltonizer,
        desaturation = combined
    )
}

private fun gammaFolded(scale: Float, gamma: Float): Float {
    val s = if (scale.isFinite()) scale.coerceIn(0f, 1f) else 1f
    val g = if (gamma.isFinite()) gamma.coerceIn(0.05f, 5f) else 1f
    return Math.pow(s.toDouble(), 1.0 / g).toFloat()
}

/**
 * Two dims as one.
 *
 * Multiplicative rather than additive, so either control at zero is neutral,
 * both at full does not exceed the single-control limit, and the result is
 * what a user reading "a 50 percent preset plus a 50 percent slider" expects
 * to see rather than a black screen.
 */
fun composeDim(presetDim: Float, userDim: Float): Float {
    val preset = presetDim.coerceIn(0f, MAX_DIM)
    val user = userDim.coerceIn(0f, MAX_DIM)
    return (1f - (1f - preset) * (1f - user)).coerceIn(0f, MAX_DIM)
}

/**
 * Scale this matrix's contrast around mid grey.
 *
 * The bias term is what keeps the midpoint fixed: scaling the channels alone
 * would darken everything as contrast rises. Cross-channel terms scale too
 * where the matrix carries them, so a colour-vision preset keeps its shape.
 */
fun LumenMatrix.withContrast(contrast: Float): LumenMatrix {
    if (contrast == 1f) return this
    val bias = (1f - contrast) * 0.5f
    val contrasted = copy(
        r = (r * contrast).coerceIn(0f, 2f),
        g = (g * contrast).coerceIn(0f, 2f),
        b = (b * contrast).coerceIn(0f, 2f),
        biasR = (biasR + bias).coerceIn(-1f, 1f),
        biasG = (biasG + bias).coerceIn(-1f, 1f),
        biasB = (biasB + bias).coerceIn(-1f, 1f)
    )
    if (!hasColorMatrix) return contrasted
    return contrasted.copy(
        matrixRr = (matrixRr * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixRg = (matrixRg * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixRb = (matrixRb * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGr = (matrixGr * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGg = (matrixGg * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixGb = (matrixGb * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBr = (matrixBr * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBg = (matrixBg * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX),
        matrixBb = (matrixBb * contrast).coerceIn(MATRIX_COEFF_MIN, MATRIX_COEFF_MAX)
    )
}
