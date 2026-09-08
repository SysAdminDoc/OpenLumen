package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.ScheduleMode
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * C310. Both decisions were private methods on LumenService, reachable only
 * through the service itself, so the rules that keep a corrupt import from
 * throwing inside the foreground service and keep the light sensor from
 * overruling an explicit off were carried by reading alone.
 */
class ScheduleDecisionTest {

    private fun prefs(
        presetKey: String = "night",
        mode: ScheduleModeDto = ScheduleModeDto.FixedTime
    ) = Preferences(activePresetKey = presetKey, schedule = ScheduleDto(mode = mode))

    @Test fun `an ordinary schedule turns the filter on from either trigger`() {
        val p = prefs()

        assertThat(shouldFilterBeActive(p, scheduleActive = true, lightActive = false)).isTrue()
        assertThat(shouldFilterBeActive(p, scheduleActive = false, lightActive = true)).isTrue()
        assertThat(shouldFilterBeActive(p, scheduleActive = true, lightActive = true)).isTrue()
        assertThat(shouldFilterBeActive(p, scheduleActive = false, lightActive = false)).isFalse()
    }

    @Test fun `the light sensor does not overrule the Off preset`() {
        // A user who picks Off and walks into a dark room should not have the
        // screen tint itself.
        val p = prefs(presetKey = Preferences.OFF_PRESET_KEY)

        assertThat(shouldFilterBeActive(p, scheduleActive = true, lightActive = true)).isFalse()
    }

    @Test fun `the light sensor does not overrule an Always off schedule`() {
        val p = prefs(mode = ScheduleModeDto.AlwaysOff)

        assertThat(shouldFilterBeActive(p, scheduleActive = true, lightActive = true)).isFalse()
    }

    @Test fun `a solar schedule with no location stands down instead of throwing`() {
        // The calculator is called from the foreground service, so a corrupt
        // import must not reach it.
        for (schedule in listOf(
            ScheduleDto(mode = ScheduleModeDto.Solar, latitude = null, longitude = null),
            ScheduleDto(mode = ScheduleModeDto.Solar, latitude = Double.NaN, longitude = 0.0),
            ScheduleDto(mode = ScheduleModeDto.Solar, latitude = 0.0, longitude = Double.NaN),
            ScheduleDto(mode = ScheduleModeDto.Solar, latitude = 91.0, longitude = 0.0)
        )) {
            assertThat(mapScheduleMode(schedule, nextAlarmAt = null))
                .isEqualTo(ScheduleMode.AlwaysOff)
        }
    }

    @Test fun `a solar schedule with a location is still solar`() {
        // Positive control: standing down has to come from the location being
        // unusable, not from the mode.
        val mode = mapScheduleMode(
            ScheduleDto(mode = ScheduleModeDto.Solar, latitude = 40.7128, longitude = -74.0060),
            nextAlarmAt = null
        )

        assertThat(mode).isInstanceOf(ScheduleMode.Solar::class.java)
    }

    @Test fun `a timezone this device does not have degrades to the device zone`() {
        val mode = mapScheduleMode(
            ScheduleDto(
                mode = ScheduleModeDto.Solar,
                latitude = 40.7128,
                longitude = -74.0060,
                solarTimezone = "Not/AZone"
            ),
            nextAlarmAt = null
        ) as ScheduleMode.Solar

        assertThat(mode.locationZoneId).isNull()
    }

    @Test fun `a timezone this device does have is kept`() {
        // Positive control for the case above.
        val mode = mapScheduleMode(
            ScheduleDto(
                mode = ScheduleModeDto.Solar,
                latitude = 40.7128,
                longitude = -74.0060,
                solarTimezone = "America/New_York"
            ),
            nextAlarmAt = null
        ) as ScheduleMode.Solar

        assertThat(mode.locationZoneId).isEqualTo(ZoneId.of("America/New_York"))
    }

    @Test fun `impossible clock values from an import are clamped`() {
        val mode = mapScheduleMode(
            ScheduleDto(
                mode = ScheduleModeDto.FixedTime,
                startHour = 25,
                startMinute = 70,
                endHour = -3,
                endMinute = 999
            ),
            nextAlarmAt = null
        ) as ScheduleMode.FixedTime

        assertThat(mode.start).isEqualTo(LocalTime.of(23, 59))
        assertThat(mode.end).isEqualTo(LocalTime.of(0, 59))
    }

    @Test fun `solar offsets are clamped to three hours either way`() {
        val mode = mapScheduleMode(
            ScheduleDto(
                mode = ScheduleModeDto.Solar,
                latitude = 40.7128,
                longitude = -74.0060,
                sunsetOffsetMin = 5_000,
                sunriseOffsetMin = -5_000
            ),
            nextAlarmAt = null
        ) as ScheduleMode.Solar

        assertThat(mode.sunsetOffsetMin).isEqualTo(180)
        assertThat(mode.sunriseOffsetMin).isEqualTo(-180)
    }

    @Test fun `the alarm mode carries the alarm it was given`() {
        val alarm = ZonedDateTime.parse("2026-09-04T07:30:00-04:00[America/New_York]")

        val mode = mapScheduleMode(
            ScheduleDto(mode = ScheduleModeDto.UntilNextAlarm, startHour = 22, startMinute = 15),
            nextAlarmAt = alarm
        ) as ScheduleMode.UntilNextAlarm

        assertThat(mode.start).isEqualTo(LocalTime.of(22, 15))
        assertThat(mode.nextAlarmAt).isEqualTo(alarm)
    }
}
