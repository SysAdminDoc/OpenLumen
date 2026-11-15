package com.openlumen.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.testing.updateWhileLooping
import com.openlumen.widget.widgetPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * PR #31. Android 12+ refuses a foreground start made from the background,
 * and a sticky restart after a low-memory kill is exactly that. The service
 * used to answer by writing `enabled = false`, so the filter the user left on
 * was silently gone the next time they looked at the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LumenServiceForegroundRefusalTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val alarmManager: AlarmManager
        get() = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val diagnostics = mutableListOf<String>()
    private val services = mutableListOf<ServiceController<LumenService>>()

    @Before fun setUp() {
        DiagnosticsLog.installTestWriter { synchronized(diagnostics) { diagnostics += it } }
        LumenService.resetForegroundStateForTest()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // The store outlives each test in this sandbox. A driver pinned by an
        // earlier class would have a running service write its Auto fallback
        // in the middle of these checks, so every test starts from defaults.
        prefs.updateWhileLooping { Preferences(enabled = true) }
    }

    @After fun tearDown() {
        // A service that turned the filter off keeps collecting preferences
        // on Main. Left running, it reacts to the next test's setUp write
        // with writes of its own, and one caught waiting for Main when
        // Robolectric empties the queue holds the store for good.
        shadowOf(app.mainLooper).idle()
        services.filter { it.get().lifecycle.currentState != Lifecycle.State.DESTROYED }
            .forEach { it.destroy() }
        // Queued behind anything the destroyed services had in flight.
        prefs.updateWhileLooping { it }
        DiagnosticsLog.clearTestWriter()
        LumenService.resetForegroundStateForTest()
        shadowOf(app.getSystemService(ActivityManager::class.java)).setServices(emptyList())
    }

    private fun refusedService(error: Exception): ServiceController<LumenService> {
        val controller = Robolectric.buildService(LumenService::class.java)
        services += controller
        shadowOf(controller.get()).setThrowInStartForeground(error)
        return controller.create()
    }

    /** Alarms that wake [ScheduleAlarmReceiver] to try the start again. */
    private fun retryAlarms(): List<ShadowAlarmManager.ScheduledAlarm> =
        shadowOf(alarmManager).scheduledAlarms.filter { alarm ->
            val pi = alarm.operation ?: return@filter false
            val shadow = shadowOf(pi)
            shadow.isBroadcast &&
                shadow.savedIntent.component?.className == ScheduleAlarmReceiver::class.java.name &&
                shadow.savedIntent.hasExtra(ScheduleAlarmReceiver.EXTRA_RETRY_ATTEMPT)
        }

    /** Runs main-thread work for [ms] of wall time so DataStore writes land. */
    private fun settle(ms: Long = 1_500) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            Thread.sleep(20)
        }
    }

    private suspend fun awaitPrefs(timeoutMs: Long = 10_000, done: (Preferences) -> Boolean): Preferences {
        val deadline = System.currentTimeMillis() + timeoutMs
        var current = prefs.flow.first()
        while (!done(current) && System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            Thread.sleep(20)
            current = prefs.flow.first()
        }
        return current
    }

    private fun log(): String = synchronized(diagnostics) { diagnostics.joinToString("\n") }

    @Test fun `a refused foreground start keeps the filter on and queues an exact retry`() = runBlocking {
        val controller = refusedService(ForegroundServiceStartNotAllowedException("background"))
        settle()

        assertThat(prefs.flow.first().enabled).isTrue()
        assertThat(shadowOf(controller.get()).isStoppedBySelf).isTrue()
        val retry = retryAlarms().single()
        // Only an exact alarm lets the receiver it wakes start the service
        // from the background, so an inexact retry would be refused again.
        assertThat(retry.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(retry.isAllowWhileIdle).isTrue()
        assertThat(log()).contains("startForeground refused; filter kept on, retry 1 queued")
        assertThat(LumenService.inForeground).isFalse()
    }

    @Test fun `the start that follows a refusal neither applies the filter nor drops the retry`() = runBlocking {
        val controller = refusedService(ForegroundServiceStartNotAllowedException("background"))

        // The start that created the service is still delivered after the
        // stopSelf(). Observing preferences from it would reschedule the
        // next transition, and that cancels the retry alarm.
        controller.startCommand(0, 1)
        settle()
        assertThat(retryAlarms()).hasSize(1)

        controller.destroy()
        settle(200)
        assertThat(retryAlarms()).hasSize(1)
        assertThat(prefs.flow.first().enabled).isTrue()
    }

    @Test fun `a turn-off sent to a refused service cancels the retry`() = runBlocking {
        val controller = refusedService(ForegroundServiceStartNotAllowedException("background"))
        assertThat(retryAlarms()).hasSize(1)

        controller.withIntent(
            Intent(app, LumenService::class.java).setAction(LumenService.ACTION_TURN_OFF)
        ).startCommand(0, 1)
        settle(200)

        assertThat(retryAlarms()).isEmpty()
    }

    @Test fun `refusals stop queueing retries once the budget is spent`() = runBlocking {
        repeat(ScheduleAlarmReceiver.MAX_BLOCKED_START_RETRIES + 1) {
            refusedService(ForegroundServiceStartNotAllowedException("background")).destroy()
        }
        settle(200)

        // The last refusal was past the budget: the third retry is still the
        // one pending, nothing was added, and the filter stays on for the
        // next time the app opens.
        val retry = retryAlarms().single()
        assertThat(shadowOf(retry.operation).savedIntent.getIntExtra(ScheduleAlarmReceiver.EXTRA_RETRY_ATTEMPT, 0))
            .isEqualTo(ScheduleAlarmReceiver.MAX_BLOCKED_START_RETRIES)
        assertThat(log()).contains(
            "startForeground refused ${ScheduleAlarmReceiver.MAX_BLOCKED_START_RETRIES + 1} times; waiting for the app to open"
        )
        assertThat(prefs.flow.first().enabled).isTrue()
    }

    /** What ActivityManager reports about this app's service, as Android 17 did with battery use Restricted. */
    private fun reportServiceForeground(foreground: Boolean) {
        val info = ActivityManager.RunningServiceInfo().apply {
            service = ComponentName(app, LumenService::class.java)
            pid = Process.myPid()
            started = true
            this.foreground = foreground
        }
        shadowOf(app.getSystemService(ActivityManager::class.java)).setServices(listOf(info))
    }

    private fun createdService(): ServiceController<LumenService> =
        Robolectric.buildService(LumenService::class.java).also { services += it }.create()

    @Test fun `a foreground start Android quietly ignores is not taken for one`() = runBlocking {
        // With battery use set to Restricted, startForeground returns and
        // nothing changes. The service took that as foreground, so opening
        // the app never asked again, and the next startForegroundService it
        // left unanswered killed the app 30 seconds later.
        reportServiceForeground(false)
        val controller = createdService()
        settle(200)

        assertThat(LumenService.inForeground).isFalse()
        assertThat(log()).contains("startForeground ignored by Android")
        // It keeps filtering for as long as Android lets it run.
        assertThat(shadowOf(controller.get()).isStoppedBySelf).isFalse()
        assertThat(prefs.flow.first().enabled).isTrue()
    }

    @Test fun `the next start asks for the foreground again`() = runBlocking {
        reportServiceForeground(false)
        val controller = createdService()
        settle(200)
        assertThat(LumenService.inForeground).isFalse()

        // Opening the app is the usual next start, and Android lets that one
        // through. Not answering it with startForeground is what got the app
        // killed.
        reportServiceForeground(true)
        controller.startCommand(0, 1)

        assertThat(LumenService.inForeground).isTrue()
    }

    @Test fun `a foreground service that is not listed counts as foreground`() = runBlocking {
        // getRunningServices only promises the caller's own services, and an
        // empty answer is no evidence of anything.
        createdService()
        settle(200)

        assertThat(LumenService.inForeground).isTrue()
        assertThat(log()).doesNotContain("startForeground ignored")
    }

    @Test fun `any other foreground failure still turns the filter off`() = runBlocking {
        // A bad service type or a missing permission will not fix itself, so
        // retrying it would only keep waking the phone.
        refusedService(IllegalStateException("bad type"))

        assertThat(awaitPrefs { !it.enabled }.enabled).isFalse()
        assertThat(retryAlarms()).isEmpty()
        assertThat(log()).contains("startForeground failed: IllegalStateException; filter turned off")
    }
}
