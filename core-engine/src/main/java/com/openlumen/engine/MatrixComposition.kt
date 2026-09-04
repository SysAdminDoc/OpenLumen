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
