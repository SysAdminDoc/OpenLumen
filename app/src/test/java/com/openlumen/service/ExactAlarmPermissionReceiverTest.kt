package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

class ExactAlarmPermissionReceiverTest {

    @Test fun `only exact alarm permission broadcast is handled`() {
        assertThat(
            ExactAlarmPermissionReceiver.shouldHandle(
                ExactAlarmPermissionReceiver.ACTION_PERMISSION_STATE_CHANGED
            )
        ).isTrue()
        assertThat(ExactAlarmPermissionReceiver.shouldHandle(null)).isFalse()
        assertThat(ExactAlarmPermissionReceiver.shouldHandle("other.action")).isFalse()
    }

    @Test fun `reconciliation requires enabled timed schedule`() {
        assertThat(
            ExactAlarmPermissionReceiver.shouldReconcile(
                Preferences(
                    enabled = true,
                    schedule = Preferences().schedule.copy(mode = ScheduleModeDto.FixedTime)
                )
            )
        ).isTrue()
        assertThat(
            ExactAlarmPermissionReceiver.shouldReconcile(
                Preferences(enabled = false)
            )
        ).isFalse()
        assertThat(
            ExactAlarmPermissionReceiver.shouldReconcile(
                Preferences(enabled = true, schedule = Preferences().schedule.copy(mode = ScheduleModeDto.AlwaysOn))
            )
        ).isFalse()
    }
}
