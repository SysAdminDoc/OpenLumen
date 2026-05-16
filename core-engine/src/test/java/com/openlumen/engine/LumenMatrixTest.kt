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

    @Test fun `overlay ARGB packs dim into alpha at 80 percent ceiling`() {
        val argb = LumenMatrix(r = 1f, g = 0f, b = 0f, dim = 1f).toOverlayArgb()
        val alpha = (argb ushr 24) and 0xFF
        // 0.95 effective dim * 0.80 cap = 0.76 -> ~194/255
        assertThat(alpha).isAtLeast(190)
        assertThat(alpha).isAtMost(204)
    }

    @Test fun `SurfaceFlinger16 matrix has identity layout for IDENTITY`() {
        val m = LumenMatrix.IDENTITY.toSurfaceFlinger16()
        // Row-major 4x4: diagonal should be (1, 1, 1, 1); off-diagonal zero except bias row.
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
