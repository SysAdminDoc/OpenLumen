package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Kelvin
import org.junit.Test

class DayMatrixTest {

    private fun day(kelvin: Int = 4000, dim: Float = 0f) =
        Preferences(schedule = ScheduleDto(dayFilter = true, dayKelvin = kelvin, dayDim = dim))

    @Test fun `the day matrix is the chosen temperature with its own dim`() {
        val m = day(kelvin = 4000, dim = 0.2f).dayMatrix()
        val rgb = Kelvin.toRgb(4000)

        assertThat(m.r).isEqualTo(rgb.r)
        assertThat(m.g).isEqualTo(rgb.g)
        assertThat(m.b).isEqualTo(rgb.b)
        assertThat(m.dim).isEqualTo(0.2f)
    }

    @Test fun `the evening preset does not leak into the day`() {
        val p = day().copy(activePresetKey = "night", presetIntensity = 1f, dim = 0.5f)

        assertThat(p.dayMatrix().dim).isEqualTo(0f)
        assertThat(p.dayMatrix().b).isEqualTo(Kelvin.toRgb(4000).b)
    }

    @Test fun `out of range values are clamped rather than applied`() {
        assertThat(day(kelvin = 100).dayMatrix().b)
            .isEqualTo(Kelvin.toRgb(ScheduleDto.DAY_KELVIN_MIN).b)
        assertThat(day(dim = Float.NaN).dayMatrix().dim).isEqualTo(0f)
        assertThat(day(dim = 5f).dayMatrix().dim).isEqualTo(ScheduleDto.DAY_DIM_MAX)
    }
}
