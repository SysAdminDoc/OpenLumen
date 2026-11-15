package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.location.changedZoneId
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

class ScheduleClockChangeReceiverTest {

    @Test fun `clock and next-alarm system events are handled`() {
        assertThat(ScheduleClockChangeReceiver.shouldHandle(ScheduleClockChangeReceiver.ACTION_TIME_CHANGED)).isTrue()
        assertThat(ScheduleClockChangeReceiver.shouldHandle(ScheduleClockChangeReceiver.ACTION_DATE_CHANGED)).isTrue()
        assertThat(ScheduleClockChangeReceiver.shouldHandle(ScheduleClockChangeReceiver.ACTION_TIMEZONE_CHANGED)).isTrue()
        assertThat(ScheduleClockChangeReceiver.shouldHandle(ScheduleClockChangeReceiver.ACTION_NEXT_ALARM_CLOCK_CHANGED)).isTrue()
        assertThat(ScheduleClockChangeReceiver.shouldHandle("other.action")).isFalse()
    }

    @Test fun `only an enabled timed schedule needs clock reconciliation`() {
        val timed = Preferences(
            enabled = true,
            schedule = Preferences().schedule.copy(mode = ScheduleModeDto.FixedTime)
        )
        assertThat(ScheduleClockChangeReceiver.shouldReconcile(timed)).isTrue()
        assertThat(ScheduleClockChangeReceiver.shouldReconcile(timed.copy(enabled = false))).isFalse()
        assertThat(
            ScheduleClockChangeReceiver.shouldReconcile(
                timed.copy(schedule = timed.schedule.copy(mode = ScheduleModeDto.AlwaysOn))
            )
        ).isFalse()
    }

    private val savedInWarsaw = ScheduleDto(
        mode = ScheduleModeDto.Solar,
        latitude = 52.2297,
        longitude = 21.0122,
        locationZoneId = "Europe/Warsaw"
    )

    @Test fun `a timezone change away from the saved zone posts the notice, filter off included`() {
        // Preferences().enabled is false here: the location is still the one
        // the schedule uses the next time the filter is turned on.
        assertThat(Preferences(schedule = savedInWarsaw).enabled).isFalse()
        assertThat(
            ScheduleClockChangeReceiver.zoneNoticeFor(
                ScheduleClockChangeReceiver.ACTION_TIMEZONE_CHANGED, savedInWarsaw, "Europe/London"
            )
        ).isEqualTo(ScheduleClockChangeReceiver.ZoneNotice.POST)
    }

    @Test fun `coming back to the saved zone takes the notice down`() {
        assertThat(
            ScheduleClockChangeReceiver.zoneNoticeFor(
                ScheduleClockChangeReceiver.ACTION_TIMEZONE_CHANGED, savedInWarsaw, "Europe/Warsaw"
            )
        ).isEqualTo(ScheduleClockChangeReceiver.ZoneNotice.CANCEL)
    }

    @Test fun `other clock broadcasts leave the notice alone`() {
        for (action in listOf(
            ScheduleClockChangeReceiver.ACTION_TIME_CHANGED,
            ScheduleClockChangeReceiver.ACTION_DATE_CHANGED,
            ScheduleClockChangeReceiver.ACTION_NEXT_ALARM_CLOCK_CHANGED
        )) {
            assertThat(ScheduleClockChangeReceiver.zoneNoticeFor(action, savedInWarsaw, "Europe/London"))
                .isEqualTo(ScheduleClockChangeReceiver.ZoneNotice.NONE)
        }
    }

    @Test fun `the zone comes from the broadcast, not the process default`() {
        // The process default is updated asynchronously and can still hold
        // the old zone when the receiver runs.
        val stale = { java.time.ZoneId.of("Europe/Warsaw") }
        assertThat(changedZoneId("Europe/London", stale)).isEqualTo("Europe/London")
        assertThat(changedZoneId(null, stale)).isEqualTo("Europe/Warsaw")
        assertThat(changedZoneId("Not/AZone", stale)).isEqualTo("Europe/Warsaw")
    }
}
