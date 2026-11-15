package com.openlumen.service

import android.app.ActivityManager
import android.app.Application
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.testing.updateWhileLooping
import com.openlumen.widget.widgetPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
 * The Home toggle and the widget turn the filter off by writing enabled=false
 * and then calling stopService straight away. The service's preferences
 * observer is what mirrors the change into the locked-boot copy, and stopping
 * the service cancelled it first: on an Android 17 emulator the copy still
 * said enabled and active after a widget turn-off, so the next reboot would
 * have put the tint back on the lock screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LumenServiceStopMirrorTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val services = mutableListOf<ServiceController<LumenService>>()

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        prefs.updateWhileLooping { Preferences(enabled = true) }
    }

    @After fun tearDown() {
        shadowOf(app.mainLooper).idle()
        services.filter { it.get().lifecycle.currentState != Lifecycle.State.DESTROYED }
            .forEach { it.destroy() }
        prefs.updateWhileLooping { it }
        LumenService.resetForegroundStateForTest()
        shadowOf(app.getSystemService(ActivityManager::class.java)).setServices(emptyList())
    }

    /** A running service whose locked-boot copy says the filter is on. */
    private fun runningServiceWithMirrorOn(): ServiceController<LumenService> {
        val controller = Robolectric.buildService(LumenService::class.java)
        services += controller
        controller.create().startCommand(0, 1)
        settle()
        val store = controller.get().directBootState
        awaitOnLooper { store.update { it.copy(enabled = true, active = true) } }
        return controller
    }

    private fun mirror(controller: ServiceController<LumenService>): DirectBootState =
        runBlocking { controller.get().directBootState.flow.first() }

    /** Runs main-thread work for [ms] of wall time so writes off Main land. */
    private fun settle(ms: Long = 1_500) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            Thread.sleep(20)
        }
    }

    private fun awaitOnLooper(block: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Default).async { block() }
        val deadline = System.currentTimeMillis() + 10_000
        while (!job.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "write did not finish" }
            shadowOf(app.mainLooper).idle()
            Thread.sleep(10)
        }
        runBlocking { job.await() }
    }

    @Test fun `stopping the service right after a turn-off clears the locked-boot copy`() {
        val controller = runningServiceWithMirrorOn()

        // What the Home toggle and the widget do: write the preference off
        // the main thread, then stop the service before Main gets to the
        // observer that would have mirrored it.
        runBlocking { CoroutineScope(Dispatchers.Default).async { prefs.update { it.copy(enabled = false) } }.await() }
        controller.destroy()
        settle()

        val state = mirror(controller)
        assertThat(state.enabled).isFalse()
        assertThat(state.active).isFalse()
    }

    @Test fun `a stop with the filter still on leaves the locked-boot copy alone`() {
        // Positive control: a stop that is not a turn-off, such as a refused
        // foreground start, keeps the tint the user left on for the next boot.
        val controller = runningServiceWithMirrorOn()

        controller.destroy()
        settle()

        val state = mirror(controller)
        assertThat(state.enabled).isTrue()
        assertThat(state.active).isTrue()
    }
}
