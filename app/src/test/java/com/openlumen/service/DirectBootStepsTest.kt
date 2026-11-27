package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Kelvin
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.prefs.dayMatrix
import com.openlumen.prefs.toDto
import com.openlumen.schedule.SolarCalculator
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * A reboot just before a schedule flip, with the phone left locked past it:
 * the Locked Boot restore has only the mirror to go on, so the mirror has to
 * carry the flips.
 */
class DirectBootStepsTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val night = Kelvin.toRgb(2500)

    private fun prefs(dayFilter: Boolean = true, schedule: ScheduleDto = fixed2100to0700(dayFilter)) =
        Preferences(
            enabled = true,
            activePresetKey = Preferences.CUSTOM_PRESET_KEY,
            customMatrix = MatrixDto(r = night.r, g = night.g, b = night.b),
            schedule = schedule
        )

    private fun fixed2100to0700(dayFilter: Boolean) = ScheduleDto(
        mode = ScheduleModeDto.FixedTime,
        startHour = 21,
        startMinute = 0,
        endHour = 7,
        endMinute = 0,
        dayFilter = dayFilter,
        dayKelvin = 4000
    )

    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    @Test fun `a night restore carries the morning and the evening after it`() {
        val p = prefs()
        val mode = mapScheduleMode(p.schedule, nextAlarmAt = null)

        val steps = directBootSteps(p, mode, now = at(27, 6, 26))

        assertThat(steps.map { it.atEpochMs }).containsExactly(ms(at(27, 7, 0)), ms(at(27, 21, 0)))
            .inOrder()
        assertThat(steps[0].active).isTrue()
        assertThat(steps[0].matrix).isEqualTo(p.dayMatrix().toDto())
        assertThat(steps[1].active).isTrue()
        assertThat(steps[1].matrix.r).isWithin(1e-4f).of(night.r)
        assertThat(steps[1].matrix.b).isWithin(1e-4f).of(night.b)
    }

    @Test fun `without the day filter the morning step switches the tint off`() {
        val p = prefs(dayFilter = false)
        val mode = mapScheduleMode(p.schedule, nextAlarmAt = null)

        val steps = directBootSteps(p, mode, now = at(27, 6, 26))

        assertThat(steps[0].atEpochMs).isEqualTo(ms(at(27, 7, 0)))
        assertThat(steps[0].active).isFalse()
        assertThat(steps[1].active).isTrue()
    }

    @Test fun `the solar morning step lands on sunrise with the day filter`() {
        val lat = 54.35
        val lng = 18.65
        val p = prefs(
            schedule = ScheduleDto(
                mode = ScheduleModeDto.Solar,
                latitude = lat,
                longitude = lng,
                dayFilter = true,
                dayKelvin = 4000
            )
        )
        val mode = mapScheduleMode(p.schedule, nextAlarmAt = null)
        val sunrise = SolarCalculator.computeTimes(at(27, 0, 0).toLocalDate(), lat, lng, zone).sunrise

        val steps = directBootSteps(p, mode, now = at(27, 7, 26))

        assertThat(steps[0].atEpochMs / 1000).isEqualTo(sunrise.toEpochSecond())
        assertThat(steps[0].matrix).isEqualTo(p.dayMatrix().toDto())
        assertThat(steps[1].active).isTrue()
    }

    @Test fun `always on has no steps`() {
        val p = prefs(schedule = ScheduleDto(mode = ScheduleModeDto.AlwaysOn))
        val mode = mapScheduleMode(p.schedule, nextAlarmAt = null)

        assertThat(directBootSteps(p, mode, now = at(27, 6, 26))).isEmpty()
    }

    @Test fun `the restore after the morning flip shows the day and arms the evening`() {
        val p = prefs()
        val mode = mapScheduleMode(p.schedule, nextAlarmAt = null)
        val mirrored = DirectBootState(
            enabled = true,
            active = true,
            matrix = MatrixDto(r = night.r, g = night.g, b = night.b),
            upcoming = directBootSteps(p, mode, now = at(26, 21, 0))
        )

        val before = mirrored.at(ms(at(27, 6, 59)))
        val after = mirrored.at(ms(at(27, 8, 20)))

        assertThat(before.matrix.r).isWithin(1e-4f).of(night.r)
        assertThat(before.upcoming).hasSize(2)
        assertThat(after.active).isTrue()
        assertThat(after.matrix).isEqualTo(p.dayMatrix().toDto())
        assertThat(after.upcoming.map { it.atEpochMs }).containsExactly(ms(at(27, 21, 0)))
    }
}
