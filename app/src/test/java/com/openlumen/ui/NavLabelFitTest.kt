package com.openlumen.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.openlumen.ui.theme.OpenLumenTheme
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C56 on a device: at 2x font size the bottom bar read "Hom e", "Sche dule",
 * "Drive r". Real text measurement, so the graphics mode is native.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class NavLabelFitTest {

    @After fun resetFontScale() {
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun labelLayouts(): List<Pair<String, TextLayoutResult>> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            OpenLumenTheme(darkTheme = true) {
                LumenBottomNavigation(currentRoute = Dest.Home.route, onDestinationSelected = {})
            }
        }
        ComposeSemantics.settle()
        return ComposeSemantics.unmergedNodes(activity).mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                ?: return@mapNotNull null
            val layout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
                ?: return@mapNotNull null
            val out = mutableListOf<TextLayoutResult>()
            layout(out)
            text to out.single()
        }
    }

    private fun assertWholeWords(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val layouts = labelLayouts()

        assertThat(layouts).hasSize(Dest.all.size)
        layouts.forEach { (text, layout) ->
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertWithMessage("\"$text\" at ${fontScale}x breaks inside a word after line $line")
                    .that(text[end - 1].isWhitespace() || text[end].isWhitespace())
                    .isTrue()
            }
            val detail = "lines=${layout.lineCount} size=${layout.size} font=${layout.layoutInput.style.fontSize} " +
                "lineHeight=${layout.layoutInput.style.lineHeight} constraints=${layout.layoutInput.constraints}"
            // The semantics layout is rebuilt at the slot's full width, not the
            // label's, so didOverflowWidth is always true for a short label.
            // Each line's ink against the label's own width says what it means
            // to; the space a line wraps after hangs past the edge unseen.
            for (line in 0 until layout.lineCount) {
                val boxes = (layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true))
                    .map { layout.getBoundingBox(it) }
                val ink = boxes.maxOf { it.right } - boxes.minOf { it.left }
                assertWithMessage("\"$text\" at ${fontScale}x is wider than its label on line $line: $detail")
                    .that(ink).isAtMost(layout.size.width.toFloat())
            }
            assertWithMessage("\"$text\" at ${fontScale}x is cut off at the bottom: $detail")
                .that(layout.didOverflowHeight).isFalse()
            assertWithMessage("\"$text\" at ${fontScale}x needs more lines than it may take: $detail")
                .that(layout.multiParagraph.didExceedMaxLines).isFalse()
        }
    }

    @Test fun `english labels stay whole at twice the font size`() = assertWholeWords(2f)

    @Test fun `english labels stay whole at the default size`() = assertWholeWords(1f)

    @Config(qualifiers = "+es")
    @Test fun `spanish labels stay whole, Controlador included`() {
        assertWholeWords(1f)
        assertWholeWords(2f)
    }

    @Config(qualifiers = "+de")
    @Test fun `german labels stay whole, Startseite included`() {
        assertWholeWords(1f)
        assertWholeWords(2f)
    }

    @Config(qualifiers = "+fr")
    @Test fun `french labels stay whole`() = assertWholeWords(2f)

    @Test fun `a label gets a line per word, two at most`() {
        assertThat(navLabelMaxLines("Schedule")).isEqualTo(1)
        assertThat(navLabelMaxLines("Ajustes predefinidos")).isEqualTo(2)
        assertThat(navLabelMaxLines("Acerca de algo")).isEqualTo(2)
        assertThat(navLabelMaxLines(" Info ")).isEqualTo(1)
    }
}
