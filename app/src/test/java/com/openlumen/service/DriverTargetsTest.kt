package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.prefs.dayMatrix
import com.openlumen.prefs.effectiveMatrix
import org.junit.Test

/**
 * C364. Auto weighs the System Night Light driver against every matrix the
 * settings will put on screen, so the list has to hold exactly those: the day
 * filter only when the schedule ever shows it, and the far end of the
 * progressive ramp when there is one.
 */
class DriverTargetsTest {

    private fun prefs(
        mode: ScheduleModeDto = ScheduleModeDto.FixedTime,
        dayFilter: Boolean = false,
        presetKey: String = "night",
        progressive: Boolean = false
    ) = Preferences(
        activePresetKey = presetKey,
        presetIntensity = 0.5f,
        schedule = ScheduleDto(
            mode = mode,
            dayFilter = dayFilter,
            dayKelvin = 5500,
            progressiveIntensity = progressive,
            progressiveEndIntensity = 1f
        )
    )

    @Test fun `the preset alone when there is no day filter`() {
        val p = prefs()
        assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix())
    }

    @Test fun `the day filter joins it when the schedule leaves a gap`() {
        val p = prefs(dayFilter = true)
        assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix(), p.dayMatrix()).inOrder()
        assertThat(dayFilterTarget(p)).isEqualTo(p.dayMatrix())
    }

    @Test fun `a schedule with no gap never shows the day filter`() {
        val p = prefs(mode = ScheduleModeDto.AlwaysOn, dayFilter = true)
        assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix())
        assertThat(dayFilterTarget(p)).isNull()
    }

    @Test fun `the Off preset leaves the day off too`() {
        val p = prefs(dayFilter = true, presetKey = Preferences.OFF_PRESET_KEY)
        assertThat(dayFilterTarget(p)).isNull()
    }

    @Test fun `the progressive ramp adds its far end`() {
        val p = prefs(progressive = true)
        val end = p.copy(presetIntensity = 1f).effectiveMatrix()

        assertThat(end).isNotEqualTo(p.effectiveMatrix())
        assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix(), end).inOrder()
    }

    @Test fun `modes that never run the ramp leave its far end out`() {
        // progressiveIntensityAt answers null without an opening moment, so
        // the far end never reaches the screen in these modes. Solar here has
        // no location, which stands the schedule down.
        for (mode in listOf(ScheduleModeDto.AlwaysOn, ScheduleModeDto.UntilNextAlarm, ScheduleModeDto.Solar)) {
            val p = prefs(mode = mode, progressive = true)
            assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix())
        }
    }

    @Test fun `a window that starts where it ends never runs the ramp`() {
        // activeWindowStart has no opening moment for it, so the far end
        // never shows and must not move Auto off Night Light.
        val base = prefs(progressive = true)
        val p = base.copy(
            schedule = base.schedule.copy(startHour = 21, startMinute = 0, endHour = 21, endMinute = 0)
        )

        assertThat(driverTargets(p)).containsExactly(p.effectiveMatrix())
    }
}
