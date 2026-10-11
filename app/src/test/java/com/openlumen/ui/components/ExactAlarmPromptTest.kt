package com.openlumen.ui.components

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

class ExactAlarmPromptTest {

    @Test fun `asks when a timed schedule runs without exact alarms`() {
        for (mode in listOf(ScheduleModeDto.FixedTime, ScheduleModeDto.Solar, ScheduleModeDto.UntilNextAlarm)) {
            assertThat(shouldPromptForExactAlarm(true, mode, canScheduleExactAlarms = false, dismissedThisSession = false))
                .isTrue()
        }
    }

    @Test fun `does not ask once granted, dismissed, or with nothing to time`() {
        assertThat(shouldPromptForExactAlarm(true, ScheduleModeDto.Solar, canScheduleExactAlarms = true, dismissedThisSession = false))
            .isFalse()
        assertThat(shouldPromptForExactAlarm(true, ScheduleModeDto.Solar, canScheduleExactAlarms = false, dismissedThisSession = true))
            .isFalse()
        assertThat(shouldPromptForExactAlarm(false, ScheduleModeDto.Solar, canScheduleExactAlarms = false, dismissedThisSession = false))
            .isFalse()
        for (mode in listOf(ScheduleModeDto.AlwaysOn, ScheduleModeDto.AlwaysOff)) {
            assertThat(shouldPromptForExactAlarm(true, mode, canScheduleExactAlarms = false, dismissedThisSession = false))
                .isFalse()
        }
    }
}
