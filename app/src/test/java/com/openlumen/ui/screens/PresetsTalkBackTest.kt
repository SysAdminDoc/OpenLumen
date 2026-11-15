package com.openlumen.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
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
 * What TalkBack reads on Presets. On an Android 17 emulator every preset
 * row's Rename and favorite buttons were thirteen identical "Rename" and "Add
 * to favorites" stops, with nothing saying which preset each one acts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresetsTalkBackTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<MainActivity>? = null
    private var meterHost: ActivityController<ComponentActivity>? = null

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
    }

    @After fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        meterHost?.pause()?.stop()?.destroy()
        ComposeSemantics.settle()
    }

    private fun SemanticsNode.spoken(): List<String> =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** True when [text] is [formatId] filled in with some non-empty name. */
    private fun fills(formatId: Int, text: String): Boolean {
        val (before, after) = app.getString(formatId).split("%1\$s")
        return text.length > before.length + after.length && text.startsWith(before) && text.endsWith(after)
    }

    private fun presetsTab(): List<SemanticsNode> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        val activity = c.get()
        val tab = app.getString(R.string.nav_presets)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && tab !in ComposeSemantics.texts(activity)) {
            ComposeSemantics.settle()
            Thread.sleep(20)
        }
        ComposeSemantics.click(activity, tab)
        val firstPreset = app.getString(R.string.preset_name_amber)
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle()
            val nodes = ComposeSemantics.nodes(activity)
            if (nodes.any { firstPreset in it.spoken() }) return nodes
            Thread.sleep(20)
        }
        error("Presets never showed its list: ${ComposeSemantics.texts(activity)}")
    }

    @Test fun `a channel meter says its name and value once`() {
        // It read "100%. Red channel. capital R. 100%": the row's state and
        // name, then the visible letter and percentage merged in after them.
        // Drawn on its own because the list-to-detail transition does not
        // settle under Robolectric.
        val red = app.getString(R.string.home_rgb_red_name)
        val c = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        meterHost = c
        val activity = c.get()
        activity.setContent { ChannelRow(label = "R", accessibleName = red, value = 1f, color = Color.Red) }
        ComposeSemantics.settle()

        val meters = ComposeSemantics.nodes(activity).filter { red in it.spoken() }
        assertWithMessage("meters in ${ComposeSemantics.texts(activity)}").that(meters).hasSize(1)
        assertThat(meters.single().spoken()).containsExactly(red)
        assertThat(meters.single().config.getOrNull(SemanticsProperties.StateDescription))
            .isEqualTo(app.getString(R.string.home_percent_value, 100))
    }

    @Test fun `rename and favorite buttons name the preset they act on`() {
        val spoken = presetsTab().flatMap { it.spoken() }

        val renames = spoken.filter { fills(R.string.preset_rename_named, it) }
        val favorites = spoken.filter {
            fills(R.string.preset_favorite_named, it) || fills(R.string.preset_unfavorite_named, it)
        }
        assertThat(renames.size).isAtLeast(2)
        // One of each per row: no row's rename button is left unnamed.
        assertThat(renames.size).isEqualTo(favorites.size)
    }
}
