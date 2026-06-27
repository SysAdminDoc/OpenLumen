package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LumenMatrixTest {

    @Test fun `identity matrix produces full RGB scalars`() {
        val rgb = LumenMatrix.IDENTITY.scaledRgb()
        assertThat(rgb[0]).isWithin(EPS).of(1f)
        assertThat(rgb[1]).isWithin(EPS).of(1f)
        assertThat(rgb[2]).isWithin(EPS).of(1f)
    }

    @Test fun `dim of 0_5 halves RGB scalars at gamma 1`() {
        val rgb = LumenMatrix(r = 1f, g = 1f, b = 1f, dim = 0.5f).scaledRgb()
        assertThat(rgb[0]).isWithin(EPS).of(0.5f)
        assertThat(rgb[1]).isWithin(EPS).of(0.5f)
        assertThat(rgb[2]).isWithin(EPS).of(0.5f)
    }

    @Test fun `dim is clamped to 0_95 effective`() {
        // Even if user passes 0.99, effectiveDim caps at 0.95.
        val rgb = LumenMatrix(r = 1f, g = 1f, b = 1f, dim = 0.99f).scaledRgb()
        assertThat(rgb[0]).isWithin(EPS).of(1f - 0.95f)
    }

    @Test fun `negative dim is treated as zero`() {
        val rgb = LumenMatrix(r = 1f, g = 1f, b = 1f, dim = -0.5f).scaledRgb()
        assertThat(rgb[0]).isWithin(EPS).of(1f)
    }

    @Test fun `gamma equal to 1 is a no-op`() {
        val noGamma = LumenMatrix(r = 0.5f, g = 0.5f, b = 0.5f, gammaR = 1f, gammaG = 1f, gammaB = 1f).scaledRgb()
        val baseline = LumenMatrix(r = 0.5f, g = 0.5f, b = 0.5f).scaledRgb()
        assertThat(noGamma[0]).isWithin(EPS).of(baseline[0])
        assertThat(noGamma[1]).isWithin(EPS).of(baseline[1])
        assertThat(noGamma[2]).isWithin(EPS).of(baseline[2])
    }

    @Test fun `gamma above 1 lifts mid-tones`() {
        // pow(0.5, 1/2) = sqrt(0.5) ~= 0.707
        val lifted = LumenMatrix(r = 0.5f, gammaR = 2f).scaledRgb()
        assertThat(lifted[0]).isWithin(0.01f).of(0.707f)
    }

    @Test fun `gamma near zero does not divide by zero`() {
        // Clamping protects against gamma <= 0.05.
        val rgb = LumenMatrix(r = 0.5f, gammaR = 0f).scaledRgb()
        // pow(0.5, 1/0.05) = pow(0.5, 20) = ~9.5e-7 — should not throw / produce NaN.
        assertThat(rgb[0]).isFinite()
        assertThat(rgb[0]).isAtLeast(0f)
    }

    @Test fun `NaN and infinite inputs are coerced to safe finite output`() {
        val matrix = LumenMatrix(
            r = Float.NaN,
            g = Float.POSITIVE_INFINITY,
            b = -1f,
            biasR = Float.NaN,
            gammaR = Float.NaN,
            dim = Float.NaN
        )

        val rgb = matrix.scaledRgb()
        assertThat(rgb[0]).isWithin(EPS).of(1f)
        assertThat(rgb[1]).isWithin(EPS).of(1f)
        assertThat(rgb[2]).isWithin(EPS).of(0f)
        assertThat(matrix.toSurfaceFlinger16()[12]).isWithin(EPS).of(0f)
    }

    @Test fun `overlay ARGB is transparent for identity`() {
        val argb = LumenMatrix.IDENTITY.toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        assertThat(alpha).isEqualTo(0)
    }

    @Test fun `overlay ARGB has alpha for tint even when dim is zero`() {
        val argb = LumenMatrix(r = 1f, g = 0.78f, b = 0.55f, dim = 0f).toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        assertThat(alpha).isGreaterThan(0)
    }

    @Test fun `overlay ARGB uses complementary source color for tint`() {
        val argb = LumenMatrix(r = 1f, g = 0.78f, b = 0.55f, dim = 0f).toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        assertThat(alpha).isWithin(1).of(114)
        assertThat(red).isEqualTo(255)
        assertThat(green).isWithin(1).of(130)
        assertThat(blue).isEqualTo(0)
    }

    @Test fun `overlay ARGB uses black source color for pure dim`() {
        val argb = LumenMatrix(r = 1f, g = 1f, b = 1f, dim = 0.5f).toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF

        assertThat(alpha).isWithin(1).of(127)
        assertThat(red).isEqualTo(0)
        assertThat(green).isEqualTo(0)
        assertThat(blue).isEqualTo(0)
    }

    @Test fun `overlay ARGB packs dim into alpha at 80 percent ceiling`() {
        val argb = LumenMatrix(r = 1f, g = 0f, b = 0f, dim = 1f).toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        assertThat(alpha).isEqualTo(204)
    }

    @Test fun `lerp at t equals zero returns the receiver`() {
        val a = LumenMatrix(r = 1f, g = 0.5f, b = 0.25f, dim = 0.3f)
        val b = LumenMatrix(r = 0.4f, g = 0.8f, b = 0.9f, dim = 0.6f)

        val result = a.lerp(b, 0f)

        assertThat(result).isEqualTo(a)
    }

    @Test fun `lerp at t equals one returns the target`() {
        val a = LumenMatrix(r = 1f, g = 0.5f, b = 0.25f, dim = 0.3f)
        val b = LumenMatrix(r = 0.4f, g = 0.8f, b = 0.9f, dim = 0.6f)

        val result = a.lerp(b, 1f)

        assertThat(result).isEqualTo(b)
    }

    @Test fun `lerp at t equals half interpolates each field`() {
        val a = LumenMatrix(r = 1.0f, g = 0.0f, b = 0.5f, dim = 0.0f)
        val b = LumenMatrix(r = 0.0f, g = 1.0f, b = 0.5f, dim = 0.5f)

        val mid = a.lerp(b, 0.5f)

        assertThat(mid.r).isWithin(EPS).of(0.5f)
        assertThat(mid.g).isWithin(EPS).of(0.5f)
        assertThat(mid.b).isWithin(EPS).of(0.5f)
        assertThat(mid.dim).isWithin(EPS).of(0.25f)
    }

    @Test fun `lerp clamps t into the unit interval`() {
        val a = LumenMatrix(r = 1f)
        val b = LumenMatrix(r = 0f)

        assertThat(a.lerp(b, -10f).r).isEqualTo(1f)
        assertThat(a.lerp(b, 10f).r).isEqualTo(0f)
    }

    @Test fun `matrix intensity interpolates off diagonal coefficients from identity`() {
        val matrix = LumenMatrix(
            hasColorMatrix = true,
            matrixRr = 0.2f,
            matrixRg = 0.8f,
            matrixRb = 0f,
            matrixGr = 0.2f,
            matrixGg = 0.8f,
            matrixGb = 0f,
            matrixBr = 0f,
            matrixBg = 0f,
            matrixBb = 1f
        )

        val half = matrix.withIntensity(0.5f)

        assertThat(half.hasColorMatrix).isTrue()
        assertThat(half.matrixRr).isWithin(EPS).of(0.6f)
        assertThat(half.matrixRg).isWithin(EPS).of(0.4f)
        assertThat(half.matrixGg).isWithin(EPS).of(0.9f)
        assertThat(half.matrixBb).isWithin(EPS).of(1f)
    }

    @Test fun `surface matrix carries CVD off diagonal terms`() {
        val m = Presets.PROTAN.toSurfaceFlinger16()

        assertThat(m[0]).isWithin(EPS).of(0.11238f)
        assertThat(m[1]).isWithin(EPS).of(0.11238f)
        assertThat(m[2]).isWithin(EPS).of(0.00401f)
        assertThat(m[4]).isWithin(EPS).of(0.88762f)
        assertThat(m[5]).isWithin(EPS).of(0.88762f)
        assertThat(m[6]).isWithin(EPS).of(-0.00401f)
        assertThat(m[10]).isWithin(EPS).of(1f)
    }

    @Test fun `SurfaceFlinger16 matrix has identity layout for IDENTITY`() {
        val m = LumenMatrix.IDENTITY.toSurfaceFlinger16()
        // Column-major 4x4: diagonal should be (1, 1, 1, 1); off-diagonal zero except bias column.
        assertThat(m[0]).isEqualTo(1f)
        assertThat(m[5]).isEqualTo(1f)
        assertThat(m[10]).isEqualTo(1f)
        assertThat(m[15]).isEqualTo(1f)
        assertThat(m[1]).isEqualTo(0f)
        assertThat(m[4]).isEqualTo(0f)
    }

    private companion object {
        const val EPS = 1e-5f
    }
}
