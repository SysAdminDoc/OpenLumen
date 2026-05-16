package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * AMOLED true-black clamp (roadmap C66). When the flag is off, the matrix
 * behaves exactly as it did before C66 landed. When on, scaled-RGB values
 * below [LumenMatrix.AMOLED_CLAMP_THRESHOLD] snap to zero.
 */
class AmoledClampTest {

    @Test fun `clamp off keeps small values intact`() {
        val rgb = LumenMatrix(r = 0.01f, g = 0.01f, b = 0.01f, amoledClamp = false).scaledRgb()
        // 0.01 is below the clamp threshold but above 0; should not snap.
        assertThat(rgb[0]).isWithin(1e-6f).of(0.01f)
        assertThat(rgb[1]).isWithin(1e-6f).of(0.01f)
        assertThat(rgb[2]).isWithin(1e-6f).of(0.01f)
    }

    @Test fun `clamp on snaps sub-threshold to zero`() {
        val rgb = LumenMatrix(r = 0.01f, g = 0.01f, b = 0.01f, amoledClamp = true).scaledRgb()
        assertThat(rgb[0]).isEqualTo(0f)
        assertThat(rgb[1]).isEqualTo(0f)
        assertThat(rgb[2]).isEqualTo(0f)
    }

    @Test fun `clamp on leaves above-threshold values untouched`() {
        val rgb = LumenMatrix(r = 0.5f, g = 0.3f, b = 0.1f, amoledClamp = true).scaledRgb()
        // 0.5, 0.3, 0.1 all > 0.02 threshold → preserved.
        assertThat(rgb[0]).isWithin(1e-6f).of(0.5f)
        assertThat(rgb[1]).isWithin(1e-6f).of(0.3f)
        assertThat(rgb[2]).isWithin(1e-6f).of(0.1f)
    }

    @Test fun `clamp on with dim driving channel toward zero snaps cleanly`() {
        // r=0.1, dim=0.95 → scaled r ≈ 0.005 (below threshold).
        val rgb = LumenMatrix(r = 0.1f, g = 0.1f, b = 0.1f, dim = 0.95f, amoledClamp = true).scaledRgb()
        assertThat(rgb[0]).isEqualTo(0f)
        assertThat(rgb[1]).isEqualTo(0f)
        assertThat(rgb[2]).isEqualTo(0f)
    }
}
