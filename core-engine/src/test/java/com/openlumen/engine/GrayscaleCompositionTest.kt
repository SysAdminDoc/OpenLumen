package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * #32. A tint over gray: the picture loses its colour first and the tint is
 * applied to what is left, so amber stays amber. The expected values come from
 * the Rec. 709 luma weights and the preset's own channel scales, worked out by
 * hand rather than read off the implementation.
 */
class GrayscaleCompositionTest {

    private val eps = 1e-4f

    private fun LumenMatrix.rows(): List<Float> = listOf(
        matrixRr, matrixRg, matrixRb,
        matrixGr, matrixGg, matrixGb,
        matrixBr, matrixBg, matrixBb
    )

    /** What the matrix does to one source pixel, row-major. */
    private fun LumenMatrix.transform(r: Float, g: Float, b: Float): List<Float> = listOf(
        matrixRr * r + matrixRg * g + matrixRb * b,
        matrixGr * r + matrixGg * g + matrixGb * b,
        matrixBr * r + matrixBg * g + matrixBb * b
    )

    private fun assertClose(actual: List<Float>, expected: List<Float>) {
        assertThat(actual.size).isEqualTo(expected.size)
        actual.zip(expected).forEach { (a, e) -> assertThat(a).isWithin(eps).of(e) }
    }

    @Test fun `gray on its own sends every channel the source's luma`() {
        val gray = LumenMatrix.IDENTITY.withGrayscale(1f)

        assertThat(gray.hasColorMatrix).isTrue()
        val luma = listOf(LUMA_R, LUMA_G, LUMA_B)
        assertClose(gray.rows(), luma + luma + luma)
        // Pure red comes out as the gray of its luma on all three channels.
        assertClose(gray.transform(1f, 0f, 0f), listOf(0.2126f, 0.2126f, 0.2126f))
    }

    @Test fun `amber over gray keeps the amber and drops the other colours`() {
        val amberGray = Presets.AMBER.withGrayscale(1f)

        // White still renders as the amber tint, so the warmth is unchanged.
        assertClose(amberGray.transform(1f, 1f, 1f), listOf(1.00f, 0.55f, 0.10f))
        // Pure green and pure blue now differ only in brightness: both are the
        // amber tint scaled by their luma, with no hue of their own left.
        assertClose(amberGray.transform(0f, 1f, 0f), listOf(0.7152f, 0.55f * 0.7152f, 0.10f * 0.7152f))
        assertClose(amberGray.transform(0f, 0f, 1f), listOf(0.0722f, 0.55f * 0.0722f, 0.10f * 0.0722f))
    }

    @Test fun `a scalar preset's channel gamma survives the move to a matrix`() {
        // The matrix path applies gamma to the dim factor only, so the gamma
        // has to ride on the diagonal: white must come out at pow(0.5, 1/2),
        // the same value the scalar path renders for this preset.
        val withGamma = LumenMatrix(r = 0.5f, gammaR = 2f).withGrayscale(1f)

        assertThat(withGamma.transform(1f, 1f, 1f)[0]).isWithin(eps).of(0.70711f)
    }

    @Test fun `half grayscale mixes the source and its gray evenly`() {
        val half = LumenMatrix.IDENTITY.withGrayscale(0.5f)

        assertClose(
            half.transform(1f, 0f, 0f),
            listOf(0.5f + 0.5f * 0.2126f, 0.5f * 0.2126f, 0.5f * 0.2126f)
        )
    }

    @Test fun `going gray twice is the same as once`() {
        // Each luma row sums to one, so gray of gray is gray. A second pass,
        // say the system's reading mode on top of the user's own switch, must
        // not darken or shift anything.
        val once = Presets.AMBER.withGrayscale(1f)
        val twice = once.withGrayscale(1f)

        assertClose(twice.rows(), once.rows())
        assertThat(twice.desaturation).isWithin(eps).of(1f)
    }

    @Test fun `zero or a broken amount changes nothing`() {
        assertThat(Presets.AMBER.withGrayscale(0f)).isSameInstanceAs(Presets.AMBER)
        assertThat(Presets.AMBER.withGrayscale(Float.NaN)).isSameInstanceAs(Presets.AMBER)
    }

    @Test fun `the gray stage is marked for drivers that cannot multiply`() {
        val amberGray = Presets.AMBER.withGrayscale(1f)

        assertThat(amberGray.daltonizer).isEqualTo(Daltonizer.MONOCHROMACY)
        assertThat(amberGray.desaturation).isWithin(eps).of(1f)
        // The scalars stay the tint, which is what the secure-settings driver
        // turns into a Night Light temperature over the system's gray.
        assertThat(amberGray.r).isWithin(eps).of(Presets.AMBER.r)
        assertThat(amberGray.g).isWithin(eps).of(Presets.AMBER.g)
        assertThat(amberGray.b).isWithin(eps).of(Presets.AMBER.b)
    }

    @Test fun `a correction preset's scalars are not mistaken for a tint`() {
        // Grayscale's own scalars are a fallback for its correction mode, so
        // marking them as a tint over gray would switch Night Light on for a
        // preset that has no tint at all.
        val gray = Presets.GRAY.withGrayscale(1f)

        assertThat(gray.daltonizer).isEqualTo(Daltonizer.MONOCHROMACY)
        assertThat(gray.desaturation).isEqualTo(0f)
    }

    @Test fun `ramps and intensity carry the gray stage with them`() {
        val amberGray = Presets.AMBER.withGrayscale(1f)

        assertThat(Presets.AMBER.lerp(amberGray, 0.5f).desaturation).isWithin(eps).of(0.5f)
        assertThat(amberGray.withIntensity(0.25f).desaturation).isWithin(eps).of(0.25f)
    }
}
