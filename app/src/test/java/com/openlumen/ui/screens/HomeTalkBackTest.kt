package com.openlumen.ui.screens

import android.app.Application
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
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

/**
 * What TalkBack lands on when it walks Home, read from the merged semantics
 * tree it is built from.
 *
 * On an Android 17 emulator with TalkBack 17, every Home slider read as
 * "0 percent, slider" with its name as a separate stop after it, the visible
 * label and value above it were two more stops, the channel sliders were
 * introduced as "capital R", the fine-adjust buttons ended in "minus", and
 * "Preview" was read twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeTalkBackTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<MainActivity>? = null

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
    }

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
    }

    private fun s(id: Int) = app.getString(id)

    private fun loadedHome(): List<SemanticsNode> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val loadedMarkers = setOf(s(R.string.home_filter_on), s(R.string.home_filter_off))
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle()
            val nodes = ComposeSemantics.nodes(c.get())
            // Home draws a placeholder until preferences load.
            if (nodes.any { node -> node.texts().any { it in loadedMarkers } }) return nodes
            Thread.sleep(20)
        }
        error("Home never finished loading")
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun SemanticsNode.descriptions(): List<String> =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun SemanticsNode.spoken(): List<String> = descriptions() + texts()

    private val SemanticsNode.isSlider: Boolean
        get() = SemanticsProperties.ProgressBarRangeInfo in config

    private val sliderNames: List<String>
        get() = listOf(
            R.string.home_intensity, R.string.home_dim, R.string.home_contrast,
            R.string.home_rgb_red_name, R.string.home_rgb_green_name, R.string.home_rgb_blue_name,
            R.string.home_kelvin_title,
            R.string.home_gamma_red_name, R.string.home_gamma_green_name, R.string.home_gamma_blue_name
        ).map(::s)

    @Test fun `each slider carries its name as text and its value as state`() {
        val sliders = loadedHome().filter { it.isSlider }

        assertThat(sliders.flatMap { it.texts() }).containsAtLeastElementsIn(sliderNames)
        for (slider in sliders) {
            // A contentDescription on a merging node with children becomes a
            // separate accessibility node, which TalkBack visits on its own.
            assertWithMessage("description on slider ${slider.texts()}").that(slider.descriptions()).isEmpty()
            assertWithMessage("state on slider ${slider.texts()}")
                .that(slider.config.getOrNull(SemanticsProperties.StateDescription)).isNotEmpty()
        }
    }

    @Test fun `each slider name is spoken once, by the slider`() {
        val nodes = loadedHome()

        for (name in sliderNames) {
            val speakers = nodes.filter { name in it.spoken() }
            assertWithMessage("nodes speaking \"$name\"").that(speakers).hasSize(1)
            assertWithMessage("\"$name\" is spoken by its slider").that(speakers.single().isSlider).isTrue()
        }
    }

    @Test fun `the one-letter channel labels are not read`() {
        val spoken = loadedHome().flatMap { it.spoken() }

        for (id in listOf(
            R.string.channel_red_short, R.string.channel_green_short, R.string.channel_blue_short,
            R.string.gamma_red_short, R.string.gamma_green_short, R.string.gamma_blue_short
        )) {
            assertThat(spoken).doesNotContain(s(id))
        }
    }

    @Test fun `the fine-adjust buttons speak their names without the glyph`() {
        val nodes = loadedHome()

        for (id in listOf(R.string.home_dim_fine_decrease, R.string.home_dim_fine_increase)) {
            val button = nodes.single { s(id) in it.descriptions() }
            assertWithMessage("text merged into \"${s(id)}\"").that(button.texts()).isEmpty()
        }
    }

    @Test fun `each switch row is one stop that says its title and state`() {
        val nodes = loadedHome()

        for (id in listOf(R.string.home_amoled_clamp_title, R.string.home_grayscale_title)) {
            val speakers = nodes.filter { s(id) in it.spoken() }
            assertWithMessage("nodes speaking \"${s(id)}\"").that(speakers).hasSize(1)
            assertWithMessage("\"${s(id)}\" row is the switch")
                .that(speakers.single().config.getOrNull(SemanticsProperties.ToggleableState)).isNotNull()
        }
    }

    @Test fun `preview is read once`() {
        val preview = s(R.string.home_preview)

        assertThat(loadedHome().filter { preview in it.spoken() }).hasSize(1)
    }
}
