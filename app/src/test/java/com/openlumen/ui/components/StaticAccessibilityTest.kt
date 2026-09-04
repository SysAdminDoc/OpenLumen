package com.openlumen.ui.components

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * C280. Every Slider is labelled through labeledSliderSemantics, and the four
 * controls that are not Sliders were not: the diagnostics range slider carried
 * no semantics at all, so TalkBack announced two unnamed adjustable handles
 * with no way to tell which end you had hold of, and the per-channel meter is
 * two nested Boxes, which is a picture as far as a screen reader is concerned.
 *
 * These are Compose modifiers with no value seam, and this module has no
 * Compose test harness, so the assertions are on the source. That is the thing
 * that regressed.
 */
class StaticAccessibilityTest {

    private fun source(path: String) = File("src/main/java/com/openlumen/$path").readText()

    private val about = source("ui/screens/AboutScreen.kt")
    private val presets = source("ui/screens/PresetsScreen.kt")
    private val home = source("ui/screens/HomeScreen.kt")

    @Test fun `the diagnostics range slider has a name and a state`() {
        val block = about.substringAfter("RangeSlider(").substringBefore("Row(")

        assertThat(block).contains("labeledSliderSemantics")
        assertThat(block).contains("R.string.about_diag_log_timeline")
        assertThat(block).contains("about_diag_log_timeline_state")
    }

    @Test fun `the channel meter reports itself as a progress bar`() {
        val block = presets.substringAfter("val meterState =").substringBefore("Text(")

        assertThat(block).contains("progressBarRangeInfo")
        assertThat(block).contains("contentDescription")
        assertThat(block).contains("stateDescription")
    }

    @Test fun `the channel meter reads its percentage from a resource`() {
        // home_percent_value already existed and was unused while this drew
        // its own string, which no language but English can punctuate.
        assertThat(presets).contains("R.string.home_percent_value")
        assertThat(presets).doesNotContain("""(value * 100).toInt()}%""")
    }

    @Test fun `the fine-adjust glyph buttons still have names`() {
        // These carry a bare minus and plus as their only content. A missing
        // description leaves a screen reader announcing the glyph, or nothing.
        assertThat(home).contains("contentDescription = fineDecLabel")
        assertThat(home).contains("contentDescription = fineIncLabel")
    }

    @Test fun `each screen title is a heading`() {
        // TalkBack's heading navigation is how you skip a long scroll screen.
        // There was not one heading anywhere in the app, so it had nothing to
        // jump between on five of them.
        for (path in listOf(
            "ui/screens/ScheduleScreen.kt",
            "ui/screens/DriverScreen.kt",
            "ui/screens/AboutScreen.kt"
        )) {
            assertThat(source(path)).contains("heading()")
        }
    }

    @Test fun `a bottom tab names itself once`() {
        // A merged semantics node reads every description it contains, so a
        // Column description plus an Icon description plus the Text made each
        // tab announce "Home, Home, Home, tab".
        val root = source("ui/OpenLumenRoot.kt")
        val tab = root.substringAfter("role = Role.Tab").substringBefore("Text(")

        assertThat(tab).doesNotContain("contentDescription = label")
        assertThat(tab).contains("contentDescription = null")
    }

    @Test fun `a saved profile is a button that loads it`() {
        // It was selectable with selected = false and Role.RadioButton, so
        // every row announced "not selected, radio button" while its actual
        // action is to load the profile.
        val presets = source("ui/screens/PresetsScreen.kt")
        val row = presets.substringAfter("val loadProfileLabel").substringBefore("Row(")

        assertThat(row).contains("Role.Button")
        assertThat(row).contains("onClickLabel = loadProfileLabel")
        assertThat(row).doesNotContain("Role.RadioButton")
    }

    @Test fun `an error that appears after a tap is announced`() {
        val driver = source("ui/screens/DriverScreen.kt")
        val block = driver.substringAfter("probeError?.let").substringBefore("}")

        assertThat(block).contains("liveRegion")
        assertThat(block).contains("LiveRegionMode.Polite")
    }

    @Test fun `the timeline bounds are not raw instants`() {
        // Positive control for the format test: the formatter has to be what
        // the screen actually calls, not a function nothing reaches.
        assertThat(about).contains("formatLogInstant(bounds.earliest")
        assertThat(about).contains("formatLogInstant(bounds.latest")
        assertThat(about).doesNotContain("bounds.earliest.toString()")
        assertThat(about).doesNotContain("bounds.latest.toString()")
    }
}
