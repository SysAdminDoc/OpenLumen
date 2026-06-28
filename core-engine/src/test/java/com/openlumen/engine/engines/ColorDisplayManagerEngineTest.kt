package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import org.junit.Test

class ColorDisplayManagerEngineTest {

    private val engine = ColorDisplayManagerEngine()

    @Test fun `kelvin inverse maps generated RGB back near source temperature`() {
        for (kelvin in listOf(1800, 3200, 5000, 6500, 8000, 10_000)) {
            val rgb = Kelvin.toRgb(kelvin)

            val inverse = engine.kelvinFromRgbScale(rgb.r, rgb.g, rgb.b)

            assertThat(inverse).isWithin(8).of(kelvin)
        }
    }

    @Test fun `night preset maps into warm range instead of old neutral heuristic`() {
        val inverse = engine.kelvinFromRgbScale(
            Presets.NIGHT.r,
            Presets.NIGHT.g,
            Presets.NIGHT.b
        )

        assertThat(inverse).isAtLeast(3000)
        assertThat(inverse).isAtMost(3800)
    }

    @Test fun `non-finite channels fall back to neutral white`() {
        val inverse = engine.kelvinFromRgbScale(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        )

        assertThat(inverse).isWithin(200).of(6500)
    }
}
