package com.openlumen.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertWithMessage
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.service.LumenService
import com.openlumen.ui.ComposeSemantics
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C56 on a device: at twice the font size the Presets list read "Am…",
 * "Gra…", "Pro…". A "Rename" text button took most of each row, and even
 * without it a 360dp phone fits only about nine letters on a line, so a
 * two-word name wraps at its space. Real text measurement, so the graphics
 * mode is native, and the window is tall enough that every row is laid out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h4000dp-xhdpi")
class PresetNameFitTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<MainActivity>? = null

    private val names = listOf(
        R.string.preset_name_off, R.string.preset_name_night, R.string.preset_name_amber,
        R.string.preset_name_red, R.string.preset_name_salmon, R.string.preset_name_sepia,
        R.string.preset_name_gray, R.string.preset_name_deep, R.string.preset_name_pwm,
        R.string.preset_name_protan, R.string.preset_name_deutan, R.string.preset_name_tritan
    )

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
    }

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun nameLayouts(): Map<String, TextLayoutResult> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val activity = c.get()
        val tab = app.getString(R.string.nav_presets)
        val wanted = names.map(app::getString).toSet()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && tab !in ComposeSemantics.texts(activity)) {
            ComposeSemantics.settle()
            Thread.sleep(20)
        }
        ComposeSemantics.click(activity, tab)
        var found = emptyMap<String, TextLayoutResult>()
        while (System.currentTimeMillis() < deadline && found.keys != wanted) {
            ComposeSemantics.settle()
            found = ComposeSemantics.unmergedNodes(activity).mapNotNull { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text
                if (text !in wanted) return@mapNotNull null
                val layout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
                    ?: return@mapNotNull null
                val out = mutableListOf<TextLayoutResult>()
                layout(out)
                text!! to out.single()
            }.toMap()
            Thread.sleep(20)
        }
        assertWithMessage("preset names on screen").that(found.keys).containsExactlyElementsIn(wanted)
        return found
    }

    private fun assertWhole(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val cut = nameLayouts().mapNotNull { (text, layout) ->
            val last = layout.lineCount - 1
            val midWord = (0 until last).any { line ->
                val end = layout.getLineEnd(line)
                !text[end - 1].isWhitespace() && !text[end].isWhitespace()
            }
            when {
                layout.isLineEllipsized(last) || layout.multiParagraph.didExceedMaxLines ->
                    "\"$text\" is cut short at ${layout.size.width}px"
                midWord -> "\"$text\" breaks inside a word"
                else -> null
            }
        }
        assertWithMessage("preset names at ${fontScale}x").that(cut).isEmpty()
    }

    @Test fun `every preset name shows in full at twice the font size`() = assertWhole(2f)

    @Test fun `every preset name shows in full at the default size`() = assertWhole(1f)

    @Config(qualifiers = "+de")
    @Test fun `german preset names show in full at the default size`() = assertWhole(1f)
}
