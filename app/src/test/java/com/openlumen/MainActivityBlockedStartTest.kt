package com.openlumen

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import com.openlumen.testing.updateWhileLooping
import com.openlumen.ui.ComposeSemantics
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
import org.robolectric.shadows.ShadowSettings

/**
 * C225. A tile or widget start that Android refuses in the background opens
 * MainActivity with ACTION_START_BLOCKED. Home used to look as if nothing had
 * happened; now it says why, offers the overlay setting when that is what is
 * missing, and Retry, and each of those does its job exactly once.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityBlockedStartTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val diagnostics = mutableListOf<String>()
    private val activities = mutableListOf<ActivityController<MainActivity>>()

    private fun s(id: Int) = app.getString(id)
    private val title get() = s(R.string.blocked_start_title)
    private val retry get() = s(R.string.blocked_start_retry)
    private val openSettings get() = s(R.string.perm_overlay_grant)

    @Before fun setUp() {
        DiagnosticsLog.installTestWriter { synchronized(diagnostics) { diagnostics += it } }
        LumenService.resetForegroundStateForTest()
        shadowOf(app).clearStartedServices()
    }

    @After fun tearDown() {
        // A write still waiting for Main when Robolectric empties the queue
        // would hold the shared store for every later test, so close what
        // this test opened and let anything in flight finish first.
        activities.forEach { it.close() }
        prefs.updateWhileLooping { it }
        DiagnosticsLog.clearTestWriter()
        ShadowSettings.setCanDrawOverlays(false)
    }

    private fun filterOn(engine: EngineKindDto = EngineKindDto.Auto) =
        prefs.updateWhileLooping { it.copy(enabled = true, engine = engine) }

    private fun handover(reason: String) = LumenServiceStarter.blockedStartIntent(app, reason)

    private fun launch(intent: Intent? = null): ActivityController<MainActivity> =
        (if (intent == null) Robolectric.buildActivity(MainActivity::class.java)
        else Robolectric.buildActivity(MainActivity::class.java, intent)).setup()
            .also { activities += it }

    /** Texts on screen once Home has loaded and [until] holds, or at the deadline. */
    private fun ActivityController<MainActivity>.texts(
        timeoutMs: Long = 5_000,
        until: (List<String>) -> Boolean = { true }
    ): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var texts = emptyList<String>()
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle()
            texts = ComposeSemantics.texts(get())
            val homeLoaded = s(R.string.home_filter_on) in texts || s(R.string.home_filter_off) in texts
            if (homeLoaded && until(texts)) return texts
            Thread.sleep(20)
        }
        return texts
    }

    /** Lets an asynchronous start that is not supposed to happen have its chance. */
    private fun quietly(ms: Long = 1_000) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle(frames = 2)
            Thread.sleep(20)
        }
    }

    private fun startsFrom(source: String): Int = synchronized(diagnostics) {
        diagnostics.count { "service start requested from $source under USER_INTERACTION" in it }
    }

    private fun waitForStartsFrom(source: String, count: Int, timeoutMs: Long = 5_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && startsFrom(source) < count) {
            ComposeSemantics.settle(frames = 2)
            Thread.sleep(20)
        }
        return startsFrom(source)
    }

    private fun lumenServiceStarts(): Int =
        generateSequence { shadowOf(app).nextStartedService }
            .count { it.component?.className == LumenService::class.java.name }

    @Test fun `a refused tile start with the app closed says why and offers the setting and retry`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()

        val controller = launch(handover("tile"))
        val texts = controller.texts { title in it }

        assertThat(texts).containsAtLeast(
            title,
            s(R.string.blocked_start_reason_tile),
            s(R.string.blocked_start_needs_overlay),
            openSettings,
            retry
        )
        // The notice carries the overlay route, so the separate card is not
        // shown twice over.
        assertThat(texts).doesNotContain(s(R.string.perm_overlay_title))
        val nodes = ComposeSemantics.nodes(controller.get())
        assertThat(nodes.any { it.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite })
            .isTrue()
        val heading = nodes.single { title in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        assertThat(heading.config.contains(SemanticsProperties.Heading)).isTrue()
        quietly()
        assertThat(lumenServiceStarts()).isEqualTo(0)
        assertThat(synchronized(diagnostics) { diagnostics.joinToString("\n") })
            .contains("refused service start handed to the app from tile")
    }

    @Test fun `the same notice reaches an app that is already open on another tab`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()
        val controller = launch()
        controller.texts()
        ComposeSemantics.click(controller.get(), s(R.string.nav_schedule))
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            s(R.string.home_filter_on) in ComposeSemantics.texts(controller.get())
        ) {
            ComposeSemantics.settle()
        }
        assertThat(ComposeSemantics.texts(controller.get())).doesNotContain(s(R.string.home_filter_on))

        controller.newIntent(handover("widget_toggle"))
        val texts = controller.texts { title in it }

        assertThat(texts).containsAtLeast(
            title,
            s(R.string.blocked_start_reason_widget),
            s(R.string.blocked_start_needs_overlay),
            openSettings,
            retry
        )
        quietly()
        assertThat(startsFrom("activity")).isEqualTo(0)
    }

    @Test fun `retry starts the filter once and the notice goes`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()
        val controller = launch(handover("tile"))
        assertThat(controller.texts { title in it }).contains(title)
        shadowOf(app).clearStartedServices()

        ComposeSemantics.click(controller.get(), retry)
        quietly()

        assertThat(startsFrom("retry")).isEqualTo(1)
        assertThat(lumenServiceStarts()).isEqualTo(1)
        assertThat(ComposeSemantics.texts(controller.get())).doesNotContain(title)
        assertThat(startsFrom("activity")).isEqualTo(0)
    }

    @Test fun `open settings goes to the overlay screen once and the filter starts on return`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()
        val controller = launch(handover("tile"))
        assertThat(controller.texts { title in it }).contains(title)
        val overlaySettings = ComponentName("com.android.settings", "com.android.settings.OverlaySettings")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(overlaySettings)
            addIntentFilterForActivity(
                overlaySettings,
                IntentFilter(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                }
            )
        }

        ComposeSemantics.click(controller.get(), openSettings)

        val opened = shadowOf(controller.get()).nextStartedActivity
        assertThat(opened?.action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        assertThat(opened?.data.toString()).isEqualTo("package:${app.packageName}")
        assertThat(shadowOf(controller.get()).nextStartedActivity).isNull()
        assertThat(startsFrom("activity") + startsFrom("retry")).isEqualTo(0)

        // Back from the settings screen with access granted.
        ShadowSettings.setCanDrawOverlays(true)
        controller.pause().stop().restart().start().resume()

        assertThat(waitForStartsFrom("activity", 1)).isEqualTo(1)
        assertThat(controller.texts { title !in it }).doesNotContain(title)
        quietly()
        assertThat(startsFrom("activity")).isEqualTo(1)
    }

    @Test fun `with overlay access it starts once on arrival and a rotation does not start it again`() {
        ShadowSettings.setCanDrawOverlays(true)
        filterOn()

        val controller = launch(handover("tile"))

        assertThat(waitForStartsFrom("activity", 1)).isEqualTo(1)
        assertThat(controller.texts()).doesNotContain(title)

        controller.recreate()
        controller.texts()
        quietly()
        assertThat(startsFrom("activity")).isEqualTo(1)
    }

    @Test fun `a pinned root driver starts without waiting for overlay access`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn(engine = EngineKindDto.SurfaceFlinger)

        val controller = launch(handover("widget_toggle"))

        assertThat(waitForStartsFrom("activity", 1)).isEqualTo(1)
        assertThat(controller.texts()).doesNotContain(title)
    }

    @Test fun `the notice survives a rotation and is not brought back from Recents`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()
        val controller = launch(handover("tile"))
        assertThat(controller.texts { title in it }).contains(title)

        controller.recreate()
        assertThat(controller.texts { title in it }).contains(title)

        val fromRecents = handover("tile").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val relaunched = launch(fromRecents)
        relaunched.texts()
        quietly()
        assertThat(ComposeSemantics.texts(relaunched.get())).doesNotContain(title)
        assertThat(startsFrom("activity") + startsFrom("retry")).isEqualTo(0)
    }

    @Test fun `turning the filter off clears the notice for good`() {
        ShadowSettings.setCanDrawOverlays(false)
        filterOn()
        val controller = launch(handover("tile"))
        assertThat(controller.texts { title in it }).contains(title)

        prefs.updateWhileLooping { it.copy(enabled = false) }
        assertThat(controller.texts { title !in it }).doesNotContain(title)

        filterOn()
        assertThat(controller.texts { s(R.string.home_filter_on) in it }).doesNotContain(title)
    }
}
