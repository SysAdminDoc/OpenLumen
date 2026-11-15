package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Daltonizer
import com.openlumen.engine.LUMA_G
import com.openlumen.engine.Presets
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * #32. Grayscale is a switch on top of whatever preset is active, so the tint
 * survives turning it on and off, and it travels with profiles and backups the
 * way the other Home switches do.
 */
class GrayscalePreferenceTest {

    private val eps = 1e-4f

    @Test fun `the switch takes the active preset to a tinted gray`() {
        val m = Preferences(activePresetKey = "amber", grayscale = true).effectiveMatrix()

        assertThat(m.hasColorMatrix).isTrue()
        assertThat(m.daltonizer).isEqualTo(Daltonizer.MONOCHROMACY)
        // Pure green renders as the amber tint scaled by green's luma.
        assertThat(m.matrixRg).isWithin(eps).of(Presets.AMBER.r * LUMA_G)
        assertThat(m.matrixGg).isWithin(eps).of(Presets.AMBER.g * LUMA_G)
        assertThat(m.matrixBg).isWithin(eps).of(Presets.AMBER.b * LUMA_G)
    }

    @Test fun `switching it off gives the same tint back in colour`() {
        val off = Preferences(activePresetKey = "amber", grayscale = false).effectiveMatrix()

        assertThat(off.hasColorMatrix).isFalse()
        assertThat(off.daltonizer).isEqualTo(Daltonizer.NONE)
        assertThat(off.desaturation).isEqualTo(0f)
    }

    @Test fun `the Off preset stays off with the switch on`() {
        val m = Preferences(activePresetKey = Preferences.OFF_PRESET_KEY, grayscale = true)
            .effectiveMatrix()

        assertThat(m.hasColorMatrix).isFalse()
        assertThat(m.daltonizer).isEqualTo(Daltonizer.NONE)
    }

    @Test fun `profiles keep the switch`() {
        val saved = Profiles.snapshot(Preferences(activePresetKey = "amber", grayscale = true))
        val restored = Profiles.apply(Preferences(grayscale = false), saved)

        assertThat(saved.grayscale).isTrue()
        assertThat(restored.grayscale).isTrue()
    }

    @Test fun `a stored blob from before the switch reads as off`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(Preferences.serializer(), "{}")

        assertThat(decoded.grayscale).isFalse()
        assertThat(json.decodeFromString(MatrixDto.serializer(), "{}").desaturation).isEqualTo(0f)
    }

    @Test fun `the gray stage survives the matrix's stored form`() {
        val dto = Preferences(activePresetKey = "amber", grayscale = true).effectiveMatrix().toDto()

        assertThat(dto.desaturation).isWithin(eps).of(1f)
        assertThat(dto.toMatrix().desaturation).isWithin(eps).of(1f)
        assertThat(dto.copy(desaturation = Float.NaN).toMatrix().desaturation).isEqualTo(0f)
    }
}
