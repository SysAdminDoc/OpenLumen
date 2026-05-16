package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KelvinTest {

    @Test fun `6500K is approximately neutral white`() {
        val rgb = Kelvin.toRgb(6500)
        // All three channels should be close to 1.0 at neutral white.
        assertThat(rgb.r).isWithin(0.05f).of(1.0f)
        assertThat(rgb.g).isWithin(0.05f).of(1.0f)
        assertThat(rgb.b).isWithin(0.10f).of(1.0f)
    }

    @Test fun `warm tones suppress blue more than red`() {
        val warm = Kelvin.toRgb(3200)
        // At candle-to-incandescent temperatures, red is saturated and blue is dim.
        assertThat(warm.r).isEqualTo(1.0f)
        assertThat(warm.b).isLessThan(warm.r)
        assertThat(warm.b).isLessThan(warm.g)
    }

    @Test fun `cool tones suppress red more than blue`() {
        val cool = Kelvin.toRgb(10_000)
        assertThat(cool.b).isEqualTo(1.0f)
        assertThat(cool.r).isLessThan(cool.b)
    }

    @Test fun `clamps below MIN_K to MIN_K behavior`() {
        // 0 K is unphysical and would crash the log() calls. Verify the clamp.
        val lowest = Kelvin.toRgb(Kelvin.MIN_K)
        val belowMin = Kelvin.toRgb(0)
        assertThat(belowMin).isEqualTo(lowest)
    }

    @Test fun `clamps above MAX_K to MAX_K behavior`() {
        val highest = Kelvin.toRgb(Kelvin.MAX_K)
        val aboveMax = Kelvin.toRgb(50_000)
        assertThat(aboveMax).isEqualTo(highest)
    }

    @Test fun `every output stays between zero and one`() {
        for (k in Kelvin.MIN_K..Kelvin.MAX_K step 500) {
            val rgb = Kelvin.toRgb(k)
            assertThat(rgb.r).isAtLeast(0f)
            assertThat(rgb.r).isAtMost(1f)
            assertThat(rgb.g).isAtLeast(0f)
            assertThat(rgb.g).isAtMost(1f)
            assertThat(rgb.b).isAtLeast(0f)
            assertThat(rgb.b).isAtMost(1f)
        }
    }
}
