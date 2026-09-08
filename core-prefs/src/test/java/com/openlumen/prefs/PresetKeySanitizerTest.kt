package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PresetKeySanitizerTest {

    private val known = setOf("night", "amber", "off")

    @Test fun `active unknown key falls back to custom`() {
        assertThat(PresetKeySanitizer.active("removed-preset", known))
            .isEqualTo(Preferences.CUSTOM_PRESET_KEY)
    }

    @Test fun `previous unknown key is discarded`() {
        assertThat(PresetKeySanitizer.previous("removed-preset", known)).isNull()
        assertThat(PresetKeySanitizer.previous("custom", known)).isNull()
    }

    @Test fun `favorites keep only current named presets`() {
        assertThat(
            PresetKeySanitizer.favorites(
                listOf("night", "removed-preset", "custom", "night", "amber", "\u0000"),
                known
            )
        ).containsExactly("night", "amber").inOrder()
    }
}
