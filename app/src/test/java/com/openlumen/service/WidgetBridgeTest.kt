package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WidgetBridgeTest {
    @Test fun `first widget snapshot broadcasts`() {
        val bridge = bridge()

        assertThat(bridge.shouldBroadcastFor(Preferences())).isTrue()
    }

    @Test fun `invisible preference changes do not broadcast`() {
        val bridge = bridge()
        val first = Preferences(enabled = true, activePresetKey = "night")
        val sliderOnly = first.copy(
            presetIntensity = 0.4f,
            dim = 0.2f,
            transitionDurationMs = 5_000L,
            contrast = 1.4f
        )

        assertThat(bridge.shouldBroadcastFor(first)).isTrue()
        assertThat(bridge.shouldBroadcastFor(sliderOnly)).isFalse()
    }

    @Test fun `visible widget fields broadcast`() {
        val bridge = bridge()
        val first = Preferences(enabled = true, activePresetKey = "night")
        val presetChanged = first.copy(activePresetKey = "amber")
        val favoritesChanged = presetChanged.copy(favoritePresetKeys = listOf("amber", "red"))
        val enabledChanged = favoritesChanged.copy(enabled = false)

        assertThat(bridge.shouldBroadcastFor(first)).isTrue()
        assertThat(bridge.shouldBroadcastFor(presetChanged)).isTrue()
        assertThat(bridge.shouldBroadcastFor(favoritesChanged)).isTrue()
        assertThat(bridge.shouldBroadcastFor(enabledChanged)).isTrue()
    }

    private fun bridge(): WidgetBridge =
        WidgetBridge(context = RuntimeEnvironment.getApplication(), logTag = "test")
}
