package com.openlumen

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Presets
import org.junit.Test

/**
 * C304. "custom" is not a Presets entry, so byKey returned null for it and
 * every caller fell back to something of its own: the Home card showed the raw
 * key, the tile uppercased it to "Custom", and the Presets screen showed the
 * real label. Three names for one state, one of them an internal key.
 */
class PresetLabelsTest {

    @Test fun `the custom state has a localised name like every other preset`() {
        assertThat(presetNameRes("custom")).isEqualTo(R.string.presets_custom)
    }

    @Test fun `every catalogue preset still resolves to a string resource`() {
        // Positive control: the entry above must be an addition, not a change
        // that swallowed the rest of the table.
        for (entry in Presets.ALL) {
            assertThat(presetNameRes(entry.key)).isNotNull()
        }
    }

    @Test fun `a key that names nothing still resolves to nothing`() {
        assertThat(presetNameRes("not-a-preset")).isNull()
    }
}
