package com.openlumen.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Color-temperature conversion. Tied to roadmap candidate **C65** (Kelvin
 * temperature UI). Maps a temperature in Kelvin to a 0..1 RGB triplet
 * suitable for [LumenMatrix].
 *
 * Algorithm: Tanner Helland's approximation
 * (https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html).
 * Accurate for the 1000..40000 K range; we clamp inputs into 1000..10000 K
 * because OpenLumen only needs the warm-to-cool spectrum for display
 * tinting and a 10 000 K cap stays well under the formula's reliability
 * boundary.
 *
 * The returned scalars are meant to multiply the receiver's RGB channels.
 * For OpenLumen's matrix model, this means a "Kelvin" picker writes
 * `LumenMatrix.r/g/b` to these values; per-channel gamma and intensity
 * apply on top as usual.
 */
object Kelvin {

    /** Reasonable user-facing slider range for display tinting. */
    const val MIN_K: Int = 1000
    const val MAX_K: Int = 10_000

    /** A sensible default for evening reading: roughly equivalent to OpenLumen's "Night" preset. */
    const val DEFAULT_K: Int = 3200

    data class Rgb(val r: Float, val g: Float, val b: Float)

    /**
     * Convert a temperature in Kelvin to an RGB scalar triplet in 0..1.
     *
     * Input is clamped to [MIN_K]..[MAX_K]. Below 6600 K the red channel is
     * saturated; above 6600 K the blue channel is saturated. At 6600 K the
     * output is approximately neutral white.
     */
    fun toRgb(kelvin: Int): Rgb {
        val temp = kelvin.coerceIn(MIN_K, MAX_K).toDouble() / 100.0

        val red: Double = if (temp <= 66.0) {
            255.0
        } else {
            329.698727446 * (temp - 60.0).pow(-0.1332047592)
        }

        val green: Double = if (temp <= 66.0) {
            99.4708025861 * ln(temp) - 161.1195681661
        } else {
            288.1221695283 * (temp - 60.0).pow(-0.0755148492)
        }

        val blue: Double = when {
            temp >= 66.0 -> 255.0
            temp <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(temp - 10.0) - 305.0447927307
        }

        return Rgb(
            r = norm(red),
            g = norm(green),
            b = norm(blue)
        )
    }

    /** Clamp a 0..255 channel into 0..1, defending against the formula's edge over/undershoot. */
    private fun norm(c: Double): Float =
        max(0.0, min(255.0, c)).let { (it / 255.0).toFloat() }
}
