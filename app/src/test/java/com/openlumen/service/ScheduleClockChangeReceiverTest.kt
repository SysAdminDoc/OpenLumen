package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
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
}
