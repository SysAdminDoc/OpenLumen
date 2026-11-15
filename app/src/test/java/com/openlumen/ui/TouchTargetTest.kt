package com.openlumen.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertWithMessage
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.service.LumenService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Every control on every tab is at least 48dp each way, the size Android's
 * accessibility guidance and Accessibility Scanner hold a tap target to.
 *
 * Scanner found six of the transition-duration rows on Schedule at 38dp: a
 * selectable Row whose RadioButton has no onClick of its own never picks up
 * Material's minimum, so the row was only as tall as its text.
 *
 * The size measured is the whole layout node's, which is what Compose hands
 * accessibility services: an IconButton draws at 40dp and reports the 48dp
 * that minimumInteractiveComponentSize pads it to. Compose also stretches a
 * small hit area to 48dp when it hit-tests a tap, but it doesn't report that,
 * and neighbouring rows share the stretch, so it doesn't count here.
 *
 * The window is tall so that no tab scrolls and nothing is clipped away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h4000dp-xhdpi")
class TouchTargetTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
    }

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
    }

    private fun SemanticsNode.label(): String =
        (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
            .joinToString(" / ").ifEmpty { "(unnamed)" }

    private fun SemanticsNode.sizeDp(density: Float) =
        layoutInfo.width / density to layoutInfo.height / density

    /** Waits until the tab stops changing, text and layout both, which is when it has loaded. */
    private fun settled(activity: MainActivity) {
        var last = emptyList<String>()
        var stable = 0
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && stable < 5) {
            ComposeSemantics.settle()
            val now = ComposeSemantics.nodes(activity).map { "${it.label()} ${it.layoutInfo.width}x${it.layoutInfo.height}" }
            stable = if (now == last) stable + 1 else 0
            last = now
            Thread.sleep(20)
        }
    }

    @Test fun `every clickable on every tab is at least 48dp each way`() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val activity = c.get()
        val density = activity.resources.displayMetrics.density
        // Home last. As the start destination it is composed before the
        // first frame and Robolectric never places it, so it is measured on
        // the way back to it, the way it is when a user returns to it.
        val tabs = listOf(R.string.nav_schedule, R.string.nav_presets, R.string.nav_driver,
            R.string.nav_about, R.string.nav_home).map(app::getString)

        val measured = mutableListOf<String>()
        val small = buildList {
            for (tab in tabs) {
                settled(activity)
                ComposeSemantics.click(activity, tab)
                settled(activity)
                val clickables = ComposeSemantics.nodes(activity).filter { SemanticsActions.OnClick in it.config }
                // Five tabs plus something of the tab's own.
                if (clickables.size > tabs.size) measured += tab
                for (node in clickables) {
                    val (width, height) = node.sizeDp(density)
                    if (width < 47.5f || height < 47.5f) {
                        add("$tab: ${node.label()} is %.0f x %.0f dp".format(width, height))
                    }
                }
            }
        }

        assertWithMessage("controls under 48dp").that(small).isEmpty()
        assertWithMessage("tabs measured").that(measured).containsExactlyElementsIn(tabs)
    }
}
