package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isActive
import com.openlumen.schedule.nextTransition
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class FilterPhaseTest {

    private val fixed = ScheduleMode.FixedTime(LocalTime.of(21, 0), LocalTime.of(7, 0))

    private fun prefs(
        dayFilter: Boolean = true,
        presetKey: String = "night",
        mode: ScheduleModeDto = ScheduleModeDto.FixedTime
    ) = Preferences(
        activePresetKey = presetKey,
        schedule = ScheduleDto(mode = mode, dayFilter = dayFilter)
    )

    @Test fun `inside the window the preset wins over the day filter`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = true, lightActive = false))
            .isEqualTo(FilterPhase.NIGHT)
    }

    @Test fun `outside the window the day filter takes over`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.DAY)
    }

    @Test fun `the light sensor still brings the preset during the day`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = false, lightActive = true))
            .isEqualTo(FilterPhase.NIGHT)
    }

    @Test fun `without the day filter the gap stays off`() {
        assertThat(filterPhase(prefs(dayFilter = false), fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }

    @Test fun `every windowed mode has a day`() {
        val modes = listOf(
            fixed,
            ScheduleMode.Solar(latitude = 52.2, longitude = 21.0),
            ScheduleMode.UntilNextAlarm(start = LocalTime.of(22, 0))
        )
        for (mode in modes) {
            assertThat(filterPhase(prefs(), mode, scheduleActive = false, lightActive = false))
                .isEqualTo(FilterPhase.DAY)
        }
    }

    @Test fun `the Off preset is off all day`() {
        val p = prefs(presetKey = Preferences.OFF_PRESET_KEY)
        assertThat(filterPhase(p, fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }

    @Test fun `always off and a solar schedule without location never become day`() {
        // A solar schedule with no usable location maps to AlwaysOff. Reading
        // that as "outside the window" would tint the screen around the clock.
        val p = prefs(mode = ScheduleModeDto.Solar)
        assertThat(filterPhase(p, ScheduleMode.AlwaysOff, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }

    // Issue #20 as it was reported: a 5500 K day, a 3400 K night and a window
    // from 20:00 to 07:00. Each case runs the service's own decision chain:
    // the persisted schedule, the window test, the phase and the matrix.

    private val zone = ZoneId.of("America/New_York")
    private val night3400 = Kelvin.toRgb(3400)
    private val day5500 = Kelvin.toRgb(5500)
    private val issue20 = Preferences(
        enabled = true,
        activePresetKey = Preferences.CUSTOM_PRESET_KEY,
        customMatrix = MatrixDto(r = night3400.r, g = night3400.g, b = night3400.b),
        schedule = ScheduleDto(
            mode = ScheduleModeDto.FixedTime,
            startHour = 20,
            startMinute = 0,
            endHour = 7,
            endMinute = 0,
            dayFilter = true,
            dayKelvin = 5500
        )
    )

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, second, 0, zone)

    private fun onScreen(now: ZonedDateTime): Pair<FilterPhase, LumenMatrix> {
        val mode = mapScheduleMode(issue20.schedule, nextAlarmAt = null)
        val phase = filterPhase(issue20, mode, isActive(mode, now), lightActive = false)
        return phase to phaseMatrix(issue20, phase, now)
    }

    private fun assertTemperature(matrix: LumenMatrix, rgb: Kelvin.Rgb) {
        assertThat(matrix.r).isWithin(1e-4f).of(rgb.r)
        assertThat(matrix.g).isWithin(1e-4f).of(rgb.g)
        assertThat(matrix.b).isWithin(1e-4f).of(rgb.b)
    }

    @Test fun `issue 20 the evening preset takes over at 20 00`() {
        val (before, beforeMatrix) = onScreen(at(14, 19, 59, 59))
        val (after, afterMatrix) = onScreen(at(14, 20, 0))

        assertThat(before).isEqualTo(FilterPhase.DAY)
        assertTemperature(beforeMatrix, day5500)
        assertThat(after).isEqualTo(FilterPhase.NIGHT)
        assertTemperature(afterMatrix, night3400)
    }

    @Test fun `issue 20 the day temperature comes back at 07 00`() {
        val (before, beforeMatrix) = onScreen(at(15, 6, 59, 59))
        val (after, afterMatrix) = onScreen(at(15, 7, 0))

        assertThat(before).isEqualTo(FilterPhase.NIGHT)
        assertTemperature(beforeMatrix, night3400)
        assertThat(after).isEqualTo(FilterPhase.DAY)
        assertTemperature(afterMatrix, day5500)
    }

    @Test fun `issue 20 the running service is woken at both edges`() {
        // The service sets its next alarm from nextTransition after every
        // apply, so these two instants are where it re-evaluates. Nothing has
        // to restart it for the hand-over.
        val mode = mapScheduleMode(issue20.schedule, nextAlarmAt = null)

        assertThat(nextTransition(mode, at(14, 12, 0))).isEqualTo(at(14, 20, 0))
        assertThat(nextTransition(mode, at(14, 20, 0))).isEqualTo(at(15, 7, 0))
        assertThat(nextTransition(mode, at(15, 7, 0))).isEqualTo(at(15, 20, 0))
    }

    @Test fun `issue 20 one service instance fades at each edge and nowhere else`() {
        val tracker = FilterPhaseTracker()
        val fades = listOf(
            at(14, 12, 0),
            at(14, 19, 59, 59),
            at(14, 20, 0),
            at(14, 23, 30),
            at(15, 6, 59, 59),
            at(15, 7, 0),
            at(15, 9, 0)
        ).map { tracker.changedTo(onScreen(it).first) }

        // The first apply counts as a change; then only the two edges.
        assertThat(fades).containsExactly(true, false, true, false, false, true, false).inOrder()
    }

    @Test fun `a filter left off for the day keeps the identity matrix`() {
        // Positive control for the day filter: without it the gap is clear.
        val off = issue20.copy(schedule = issue20.schedule.copy(dayFilter = false))
        val mode = mapScheduleMode(off.schedule, nextAlarmAt = null)
        val phase = filterPhase(off, mode, isActive(mode, at(14, 12, 0)), lightActive = false)

        assertThat(phase).isEqualTo(FilterPhase.OFF)
        assertThat(phaseMatrix(off, phase, at(14, 12, 0))).isEqualTo(LumenMatrix.IDENTITY)
    }

    @Test fun `the first phase a service sees counts as a change`() {
        // A service that restored a tint before the first unlock has something
        // on screen already; the first apply after unlock has to fade from it.
        val tracker = FilterPhaseTracker()

        assertThat(tracker.changedTo(FilterPhase.NIGHT)).isTrue()
        assertThat(tracker.changedTo(FilterPhase.NIGHT)).isFalse()
        assertThat(tracker.changedTo(FilterPhase.DAY)).isTrue()
    }
}
