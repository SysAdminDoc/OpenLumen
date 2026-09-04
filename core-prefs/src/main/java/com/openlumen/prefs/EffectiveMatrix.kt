package com.openlumen.prefs

import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.composeDim
import com.openlumen.engine.withContrast

/**
 * The [LumenMatrix] a [Preferences] snapshot resolves to.
 *
 * This is the authoritative answer to "what should the screen look like right
 * now", and it used to live in the app module, in a class named for previews,
 * with a comment asking whoever changed it to remember to change the service
 * too. The service called it anyway. It belongs next to the preferences it
 * reads, with the matrix arithmetic itself down in core-engine where the
 * matrix is defined.
 */
fun Preferences.effectiveMatrix(): LumenMatrix {
    if (activePresetKey == Preferences.OFF_PRESET_KEY) return LumenMatrix.IDENTITY

    val raw = Presets.byKey(activePresetKey)?.matrix ?: customMatrix.toMatrix()
    val scaled = raw.withIntensity(presetIntensity.coerceIn(0f, 1f))

    return scaled
        .copy(
            gammaR = customMatrix.gammaR,
            gammaG = customMatrix.gammaG,
            gammaB = customMatrix.gammaB,
            // A preset such as Deep Sleep or PWM Comfort owns a baseline dim,
            // and the preference is an additional control rather than a
            // replacement, so the two compose.
            dim = composeDim(scaled.dim, dim),
            amoledClamp = amoledBlackClamp
        )
        .withContrast(contrast.coerceIn(Preferences.CONTRAST_MIN, Preferences.CONTRAST_MAX))
}

/**
 * The persisted form of a matrix, as a matrix.
 *
 * This mapping existed twice: once in the app's direct-boot mirror and once
 * inline in the preview code, because neither core module could see the other.
 * core-prefs depends on core-engine now, so it lives here and both callers use
 * it.
 */
fun MatrixDto.toMatrix(amoledClamp: Boolean = false): LumenMatrix = LumenMatrix(
    r = r,
    g = g,
    b = b,
    biasR = biasR,
    biasG = biasG,
    biasB = biasB,
    dim = dim,
    gammaR = gammaR,
    gammaG = gammaG,
    gammaB = gammaB,
    amoledClamp = amoledClamp,
    hasColorMatrix = hasColorMatrix,
    matrixRr = matrixRr,
    matrixRg = matrixRg,
    matrixRb = matrixRb,
    matrixGr = matrixGr,
    matrixGg = matrixGg,
    matrixGb = matrixGb,
    matrixBr = matrixBr,
    matrixBg = matrixBg,
    matrixBb = matrixBb,
    daltonizer = com.openlumen.engine.Daltonizer.entries
        .firstOrNull { it.name == daltonizer }
        ?: com.openlumen.engine.Daltonizer.NONE
)

/** The inverse of [toMatrix]. */
fun LumenMatrix.toDto(): MatrixDto = MatrixDto(
    r = r,
    g = g,
    b = b,
    biasR = biasR,
    biasG = biasG,
    biasB = biasB,
    dim = dim,
    gammaR = gammaR,
    gammaG = gammaG,
    gammaB = gammaB,
    hasColorMatrix = hasColorMatrix,
    matrixRr = matrixRr,
    matrixRg = matrixRg,
    matrixRb = matrixRb,
    matrixGr = matrixGr,
    matrixGg = matrixGg,
    matrixGb = matrixGb,
    matrixBr = matrixBr,
    matrixBg = matrixBg,
    matrixBb = matrixBb,
    daltonizer = daltonizer.takeIf { it != com.openlumen.engine.Daltonizer.NONE }?.name
)
