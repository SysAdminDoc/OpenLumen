package com.openlumen

import android.app.Application
import android.content.Intent
import android.os.Build
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.EngineKindDto
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

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
        // The store and the service flags outlive the test class in this
        // JVM; a pinned driver or a "running" service left behind would
        // change what the next class's activity sends.
        prefs.updateWhileLooping { it.copy(engine = EngineKindDto.Auto) }
        LumenService.resetForegroundStateForTest()
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

    /** Every LumenService start action seen within [timeoutMs], or up to the first [until] one. */
    private fun startActions(timeoutMs: Long, until: String? = null): List<String?> {
        val actions = mutableListOf<String?>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            generateSequence { shadowOf(app).nextStartedService }
                .filter { it.component?.className == LumenService::class.java.name }
                .forEach { actions += it.action }
            if (until != null && until in actions) return actions
            Thread.sleep(20)
        }
        return actions
    }

    @Test fun `coming back to the app asks a running service on Auto to look at the overlay`() {
        // C364: granting "display over other apps" in Settings changes no
        // setting, so the return to the app is what tells the service. An
        // activity created after the grant has nothing to compare with, so
        // it asks every time.
        prefs.updateWhileLooping { it.copy(enabled = true, engine = EngineKindDto.Auto) }
        LumenService.markRunningForTest()

        launch()

        // Once: on Android 10+ the resume itself sends nothing, the
        // top-resumed call that follows it does.
        assertThat(startActions(timeoutMs = 2_000).count { it == LumenService.ACTION_CHECK_OVERLAY })
            .isEqualTo(1)
    }

    @Test fun `getting the focus back in split screen asks again`() {
        // The activity stays resumed while the user grants the overlay in
        // the other half, so no onResume follows.
        prefs.updateWhileLooping { it.copy(enabled = true, engine = EngineKindDto.Auto) }
        LumenService.markRunningForTest()
        val activity = launch()
        assertThat(startActions(timeoutMs = 5_000, until = LumenService.ACTION_CHECK_OVERLAY))
            .contains(LumenService.ACTION_CHECK_OVERLAY)

        activity.topActivityResumed(false)
        shadowOf(app).clearStartedServices()
        activity.topActivityResumed(true)

        assertThat(startActions(timeoutMs = 5_000, until = LumenService.ACTION_CHECK_OVERLAY))
            .contains(LumenService.ACTION_CHECK_OVERLAY)
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test fun `below Android 10 the resume asks`() {
        // No top-resumed callback exists there.
        prefs.updateWhileLooping { it.copy(enabled = true, engine = EngineKindDto.Auto) }
        LumenService.markRunningForTest()

        launch()

        assertThat(startActions(timeoutMs = 2_000).count { it == LumenService.ACTION_CHECK_OVERLAY })
            .isEqualTo(1)
    }

    @Test fun `a pinned driver is not asked to look at the overlay`() {
        // Granted, so the Driver tab's probe keeps the pin. A pinned driver
        // the probe calls unavailable goes back to Auto on launch, and Auto
        // is asked.
        ShadowSettings.setCanDrawOverlays(true)
        prefs.updateWhileLooping { it.copy(enabled = true, engine = EngineKindDto.Overlay) }
        LumenService.markRunningForTest()

        launch()

        assertThat(startActions(timeoutMs = 1_500)).doesNotContain(LumenService.ACTION_CHECK_OVERLAY)
    }

    @Test fun `a service that is not running is not started to look at the overlay`() {
        // A start from here would race the blocked-start handover, which
        // holds a start back while the overlay is missing.
        prefs.updateWhileLooping { it.copy(enabled = true, engine = EngineKindDto.Auto) }

        launch()

        assertThat(startActions(timeoutMs = 1_500)).doesNotContain(LumenService.ACTION_CHECK_OVERLAY)
    }
}
