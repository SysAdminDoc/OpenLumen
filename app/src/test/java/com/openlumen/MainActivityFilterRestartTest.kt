package com.openlumen

import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.LumenService
import com.openlumen.testing.updateWhileLooping
import com.openlumen.widget.widgetPreferencesStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * PR #31. A refused background start now keeps the switch on, so opening
 * the app has to bring the service back; otherwise Home shows the filter on
 * while nothing is running.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityFilterRestartTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val diagnostics = mutableListOf<String>()
    private val activities = mutableListOf<ActivityController<MainActivity>>()

    @Before fun setUp() {
        DiagnosticsLog.installTestWriter { synchronized(diagnostics) { diagnostics += it } }
        LumenService.resetForegroundStateForTest()
        shadowOf(app).clearStartedServices()
    }

    @After fun tearDown() {
        // Robolectric drops the main queue between tests. A preferences write
        // still waiting there for Main would hold the shared store for the
        // rest of the run, so close what this test opened and let it drain.
        activities.forEach { it.close() }
        prefs.updateWhileLooping { it }
        DiagnosticsLog.clearTestWriter()
    }

    private fun lumenStarts(timeoutMs: Long = 5_000): List<Intent> {
        val starts = mutableListOf<Intent>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            while (true) {
                val next = shadowOf(app).nextStartedService ?: break
                if (next.component?.className == LumenService::class.java.name) starts += next
            }
            if (starts.isNotEmpty()) {
                // Give a second start the chance to show up before returning.
                Thread.sleep(300)
                shadowOf(app.mainLooper).idle()
                generateSequence { shadowOf(app).nextStartedService }
                    .filter { it.component?.className == LumenService::class.java.name }
                    .forEach { starts += it }
                return starts
            }
            Thread.sleep(20)
        }
        return starts
    }

    private fun log(): String = synchronized(diagnostics) { diagnostics.joinToString("\n") }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { activities += it }

    @Test fun `opening the app restarts a filter left on with nothing running`() {
        prefs.updateWhileLooping { it.copy(enabled = true) }

        launch()

        assertThat(lumenStarts()).hasSize(1)
        assertThat(log()).contains("service start requested from app-entry under USER_INTERACTION")
    }

    @Test fun `opening the app leaves a filter that is off alone`() {
        prefs.updateWhileLooping { it.copy(enabled = false) }

        launch()

        assertThat(lumenStarts(timeoutMs = 1_500)).isEmpty()
    }
}
