package com.openlumen.service

import android.app.PendingIntent
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.schedule.ScheduleMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleAlarmOrchestratorTest {
    private val logs = mutableListOf<String>()

    @After fun tearDown() {
        DiagnosticsLog.clearTestWriter()
    }

    @Test fun `allowed exact alarm schedules exact transition`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)

        orchestrator(alarms).rescheduleNextTransition(testMode)

        assertThat(alarms.exactCalls).isEqualTo(1)
        assertThat(alarms.inexactCalls).isEqualTo(0)
        assertThat(inexactFallbackLogs()).isEmpty()
    }

    @Test fun `denied exact alarm permission schedules inexact transition and logs degradation`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = false)

        orchestrator(alarms).rescheduleNextTransition(testMode)

        assertThat(alarms.exactCalls).isEqualTo(0)
        assertThat(alarms.inexactCalls).isEqualTo(1)
        assertThat(inexactFallbackLogs()).containsExactly(
            "exact alarms unavailable; scheduled inexact transition"
        )
    }

    @Test fun `security exception from exact alarm schedules inexact transition and logs degradation`() {
        val alarms = FakeScheduleAlarmOps(
            exactAllowed = true,
            throwOnExact = true
        )

        orchestrator(alarms).rescheduleNextTransition(testMode)

        assertThat(alarms.exactCalls).isEqualTo(1)
        assertThat(alarms.inexactCalls).isEqualTo(1)
        assertThat(inexactFallbackLogs()).containsExactly(
            "exact alarm rejected; scheduled inexact transition"
        )
    }

    private fun orchestrator(alarms: FakeScheduleAlarmOps): ScheduleAlarmOrchestrator {
        DiagnosticsLog.installTestWriter { line -> logs += line }
        return ScheduleAlarmOrchestrator(
            context = RuntimeEnvironment.getApplication(),
            logTag = "OpenLumen/Test",
            alarmOpsProvider = { alarms },
            nowMs = { NOW_MS },
            nextTransitionProvider = { transitionAt }
        )
    }

    private fun inexactFallbackLogs(): List<String> =
        logs.mapNotNull { line ->
            line.substringAfter(" WARN SCHEDULE ", missingDelimiterValue = "")
                .takeIf { it.contains("inexact transition") }
        }

    private class FakeScheduleAlarmOps(
        private val exactAllowed: Boolean,
        private val throwOnExact: Boolean = false
    ) : ScheduleAlarmOps {
        var exactCalls = 0
            private set
        var inexactCalls = 0
            private set
        var cancelCalls = 0
            private set

        override fun canScheduleExactAlarms(): Boolean = exactAllowed

        override fun setExactAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
            exactCalls += 1
            if (throwOnExact) throw SecurityException("revoked")
        }

        override fun setAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
            inexactCalls += 1
        }

        override fun cancel(pi: PendingIntent) {
            cancelCalls += 1
        }

        override fun nextAlarmClockTriggerTime(): Long? = null
    }

    private companion object {
        private const val NOW_MS = 1_700_000_000_000L
        private val transitionAt = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(NOW_MS + 120_000L),
            ZoneId.systemDefault()
        )
        private val testMode = ScheduleMode.FixedTime(
            LocalTime.of(22, 0),
            LocalTime.of(7, 0)
        )
    }
}
