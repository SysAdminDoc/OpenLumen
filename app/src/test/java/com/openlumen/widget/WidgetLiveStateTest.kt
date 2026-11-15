package com.openlumen.widget

import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import com.openlumen.R
import com.openlumen.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Both widgets read the preferences once, before provideContent. While a
 * widget's Glance session is still running, an update only recomposes the
 * content it already has, so the value read when the session started is the
 * one it keeps drawing: on an Android 17 Pixel, turning the filter off from
 * the widget left the widget saying On.
 *
 * Each test hands the content a starting value that the store no longer
 * agrees with, the state a running session is in after a turn-off, and
 * expects the content to draw what the store says. The Glance test harness
 * keeps only the tree from the first time composition settles, so the store
 * is changed before the content starts rather than while it is showing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLiveStateTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun `the toggle widget draws the store, not the value it started with`() =
        runGlanceAppWidgetUnitTest {
            setContext(app)
            val startedWith = Preferences(enabled = true)
            val store = MutableStateFlow(startedWith.copy(enabled = false))

            provideComposable { ToggleWidgetContent(store, startedWith) }

            onNode(hasText(app.getString(R.string.tile_off))).assertExists()
            onNode(hasText(app.getString(R.string.tile_on))).assertDoesNotExist()
        }

    @Test fun `the preset widget drops its active ring once the store says off`() =
        runGlanceAppWidgetUnitTest {
            setContext(app)
            val startedWith = Preferences(
                enabled = true,
                activePresetKey = "amber",
                favoritePresetKeys = listOf("night", "amber", "red", "deep")
            )
            val store = MutableStateFlow(startedWith.copy(enabled = false))

            provideComposable { PresetWidgetContent(store, startedWith) }

            onNode(hasContentDescriptionEqualTo(app.getString(R.string.widget_preset_slot, "Amber"))).assertExists()
            onNode(hasContentDescriptionEqualTo(app.getString(R.string.widget_preset_slot_active, "Amber")))
                .assertDoesNotExist()
        }
}
