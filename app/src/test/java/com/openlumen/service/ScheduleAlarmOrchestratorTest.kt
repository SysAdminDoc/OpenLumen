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
        assertThat(alarms.lastPendingIntent?.isForegroundService).isTrue()
        assertThat(inexactFallbackLogs()).isEmpty()
    }

    @Test
    @Config(sdk = [31, 35])
    fun `direct service alarm remains the primary path across supported API levels`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)

        orchestrator(alarms).rescheduleNextTransition(testMode)

        assertThat(alarms.lastPendingIntent?.isForegroundService).isTrue()
        assertThat(alarms.lastPendingIntent?.isBroadcast).isFalse()
    }

    @Test fun `denied exact alarm permission schedules inexact transition and logs degradation`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = false)

        orchestrator(alarms).rescheduleNextTransition(testMode)

        assertThat(alarms.exactCalls).isEqualTo(0)
        assertThat(alarms.inexactCalls).isEqualTo(1)
        assertThat(alarms.lastPendingIntent?.isBroadcast).isTrue()
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

    @Test fun `blocked start retry uses bounded inexact alarm`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)

        orchestrator(alarms).scheduleBlockedStartRetry(attempt = 2, delayMs = 5_000L)

        assertThat(alarms.exactCalls).isEqualTo(0)
        assertThat(alarms.inexactCalls).isEqualTo(1)
        assertThat(alarms.cancelCalls).isEqualTo(3)
        assertThat(alarms.lastPendingIntent?.isBroadcast).isTrue()
        assertThat(logs.any { it.contains("scheduled blocked-service retry 2 in 5s") }).isTrue()
    }

    @Test fun `permission reconciliation rearms once per permission state`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)
        val alarmOrchestrator = orchestrator(alarms)

        assertThat(alarmOrchestrator.rescheduleIfExactAlarmPermissionChanged(testMode)).isTrue()
        assertThat(alarmOrchestrator.rescheduleIfExactAlarmPermissionChanged(testMode)).isFalse()

        alarms.exactAllowed = false

        assertThat(alarmOrchestrator.rescheduleIfExactAlarmPermissionChanged(testMode)).isTrue()
        assertThat(alarmOrchestrator.rescheduleIfExactAlarmPermissionChanged(testMode)).isFalse()
        assertThat(alarms.exactCalls).isEqualTo(1)
        assertThat(alarms.inexactCalls).isEqualTo(1)
        assertThat(alarms.lastPendingIntent?.isBroadcast).isTrue()
    }

    @Test fun `normal scheduling establishes permission state for reconciliation`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)
        val alarmOrchestrator = orchestrator(alarms)

        alarmOrchestrator.rescheduleNextTransition(testMode)

        assertThat(alarmOrchestrator.rescheduleIfExactAlarmPermissionChanged(testMode)).isFalse()
        assertThat(alarms.exactCalls).isEqualTo(1)
    }

    @Test fun `repeated scheduling with unchanged signature does not rearm`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)
        val alarmOrchestrator = orchestrator(alarms)

        alarmOrchestrator.rescheduleNextTransition(testMode)
        alarmOrchestrator.rescheduleNextTransition(testMode)

        assertThat(alarms.cancelCalls).isEqualTo(3)
        assertThat(alarms.exactCalls).isEqualTo(1)
        assertThat(logs.count { it.contains("scheduled next transition") }).isEqualTo(1)
    }

    @Test fun `expired trigger permits the next transition check to rearm`() {
        val alarms = FakeScheduleAlarmOps(exactAllowed = true)
        var now = NOW_MS
        val alarmOrchestrator = ScheduleAlarmOrchestrator(
            context = RuntimeEnvironment.getApplication(),
            logTag = "OpenLumen/Test",
            alarmOpsProvider = { alarms },
            nowMs = { now },
            nextTransitionProvider = { transitionAt }
        )

        alarmOrchestrator.rescheduleNextTransition(testMode)
        now = NOW_MS + 120_001L
        alarmOrchestrator.rescheduleNextTransition(testMode)

        assertThat(alarms.cancelCalls).isEqualTo(6)
        assertThat(alarms.exactCalls).isEqualTo(2)
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
        var exactAllowed: Boolean,
        private val throwOnExact: Boolean = false
    ) : ScheduleAlarmOps {
        var exactCalls = 0
            private set
        var inexactCalls = 0
            private set
        var cancelCalls = 0
            private set
        var lastPendingIntent: PendingIntent? = null
            private set

        override fun canScheduleExactAlarms(): Boolean = exactAllowed

        override fun setExactAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
            exactCalls += 1
            lastPendingIntent = pi
            if (throwOnExact) throw SecurityException("revoked")
        }

        override fun setAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
            inexactCalls += 1
            lastPendingIntent = pi
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
