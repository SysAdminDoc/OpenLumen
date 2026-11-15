package com.openlumen.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.service.LumenService
import com.openlumen.ui.ComposeSemantics
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * The diagnostics log's timeline range slider, as TalkBack walks it.
 *
 * On an Android 17 emulator with TalkBack 17 it was six stops: the label, a
 * "From ... to ..., Timeline range" stop that could not be adjusted, each
 * thumb as "0.0, slider" with "Range start" or "Range end" after it, and the
 * two bare dates under the track.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsTimelineTalkBackTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val logFile: File get() = File(app.filesDir, "diagnostics.log")
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
        logFile.writeText(
            "2026-11-15T05:00:00Z WARN SERVICE first warning\n" +
                "2026-11-15T06:30:00Z ERROR ENGINE later error\n"
        )
    }

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
        logFile.delete()
    }

    private fun s(id: Int) = app.getString(id)

    private fun SemanticsNode.spoken(): List<String> =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private val SemanticsNode.isSlider: Boolean
        get() = SemanticsProperties.ProgressBarRangeInfo in config

    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle()
            if (check()) return
            Thread.sleep(20)
        }
        error("never saw $what")
    }

    private fun openTimeline(): List<SemanticsNode> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val activity = c.get()
        waitFor("the About tab") { s(R.string.nav_about) in ComposeSemantics.texts(activity) }
        ComposeSemantics.click(activity, s(R.string.nav_about))
        waitFor("the diagnostics log button") { s(R.string.about_view_diag_log) in ComposeSemantics.texts(activity) }
        ComposeSemantics.click(activity, s(R.string.about_view_diag_log))
        waitFor("the timeline thumbs") { ComposeSemantics.dialogNodes().count { it.isSlider } == 2 }
        return ComposeSemantics.dialogNodes()
    }

    /** The two ends of the default WARN + ERROR view, formatted as the dialog formats them. */
    private fun expectedEnds(): Pair<String, String> {
        val lines = DiagnosticsLog.read(app).lineSequence().filter { it.isNotBlank() }.toList()
        val bounds = checkNotNull(
            DiagnosticsLog.timelineBounds(
                lines,
                setOf("WARN", "ERROR"),
                DiagnosticsLog.Category.values().map { it.name }.toSet()
            )
        )
        val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
        return formatLogInstant(bounds.earliest, 0f, bounds, locale) to
            formatLogInstant(bounds.latest, 1f, bounds, locale)
    }

    @Test fun `each thumb says the time it stands for`() {
        val thumbs = openTimeline().filter { it.isSlider }
        val (start, end) = expectedEnds()

        assertThat(thumbs.map { it.config.getOrNull(SemanticsProperties.StateDescription) })
            .containsExactly(start, end)
    }

    @Test fun `each time is spoken once, by its thumb`() {
        val nodes = openTimeline()
        val (start, end) = expectedEnds()

        for (time in listOf(start, end)) {
            val speakers = nodes.filter { n ->
                n.spoken().any { time in it } ||
                    n.config.getOrNull(SemanticsProperties.StateDescription)?.contains(time) == true
            }
            assertWithMessage("nodes saying \"$time\"").that(speakers).hasSize(1)
            assertWithMessage("\"$time\" is said by a thumb").that(speakers.single().isSlider).isTrue()
        }
    }

    @Test fun `each thumb is named for its end`() {
        val thumbs = openTimeline().filter { it.isSlider }

        assertThat(thumbs.flatMap { it.spoken() }).containsExactly(
            s(R.string.about_diag_log_timeline_start),
            s(R.string.about_diag_log_timeline_end)
        )
    }

    @Test fun `adjusting the start thumb moves the start time, never past the end`() {
        val startName = s(R.string.about_diag_log_timeline_start)
        fun startThumb() = ComposeSemantics.dialogNodes().single { it.isSlider && startName in it.spoken() }
        openTimeline()
        val before = startThumb().config.getOrNull(SemanticsProperties.StateDescription)

        checkNotNull(startThumb().config[SemanticsActions.SetProgress].action).invoke(0.5f)
        ComposeSemantics.settle()
        val moved = startThumb()
        assertThat(moved.config[SemanticsProperties.ProgressBarRangeInfo].current).isEqualTo(0.5f)
        assertThat(moved.config.getOrNull(SemanticsProperties.StateDescription)).isNotEqualTo(before)

        checkNotNull(startThumb().config[SemanticsActions.SetProgress].action).invoke(2f)
        ComposeSemantics.settle()
        assertThat(startThumb().config[SemanticsProperties.ProgressBarRangeInfo].current).isEqualTo(1f)
    }

    @Test fun `the timeline label is read once`() {
        val label = s(R.string.about_diag_log_timeline)

        assertThat(openTimeline().filter { label in it.spoken() }).hasSize(1)
    }
}
