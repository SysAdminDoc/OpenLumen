package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Kelvin
import com.openlumen.engine.Presets
import org.junit.Test

class SecureSettingsEngineTest {

    private val engine = SecureSettingsEngine()

    @Test fun `kelvin inverse maps generated RGB back near source temperature`() {
        for (kelvin in listOf(1800, 3200, 5000, 6500, 8000, 10_000)) {
            val rgb = Kelvin.toRgb(kelvin)

            val inverse = engine.kelvinFromRgbScale(rgb.r, rgb.g, rgb.b)

            assertThat(inverse).isWithin(8).of(kelvin)
        }
    }

    @Test fun `night preset maps into warm range instead of old neutral heuristic`() {
        val inverse = engine.kelvinFromRgbScale(
            Presets.NIGHT.r,
            Presets.NIGHT.g,
            Presets.NIGHT.b
        )

        assertThat(inverse).isAtLeast(3000)
        assertThat(inverse).isAtMost(3800)
    }

   @Test fun `non-finite channels fall back to neutral white`() {
       val inverse = engine.kelvinFromRgbScale(
           Float.NaN,
           Float.POSITIVE_INFINITY,
           Float.NEGATIVE_INFINITY
       )

       assertThat(inverse).isWithin(200).of(6500)
   }

    @Test fun `external Night Light changes prevent restoration`() {
        assertThat(
            shouldRestoreNightDisplay(
                currentActive = true,
                currentTemperature = 4_200,
                lastAppliedTemperature = 4_000
            )
        ).isFalse()
        assertThat(
            shouldRestoreNightDisplay(
                currentActive = false,
                currentTemperature = 4_000,
                lastAppliedTemperature = 4_000
            )
        ).isFalse()
    }

    @Test fun `matching owned state permits restoration`() {
        assertThat(
            shouldRestoreNightDisplay(
                currentActive = true,
                currentTemperature = 4_000,
                lastAppliedTemperature = 4_000
            )
        ).isTrue()
    }

    @Test fun `external Extra Dim changes prevent restoration`() {
        assertThat(
            shouldRestoreReduceBrightColors(
                currentActive = true,
                currentLevel = 40,
                lastAppliedLevel = 25
            )
        ).isFalse()
        assertThat(
            shouldRestoreReduceBrightColors(
                currentActive = false,
                currentLevel = 25,
                lastAppliedLevel = 25
            )
        ).isFalse()
        assertThat(
            shouldRestoreReduceBrightColors(
                currentActive = true,
                currentLevel = 25,
                lastAppliedLevel = 25
            )
        ).isTrue()
    }

    @Test fun `Extra Dim switched on by the user after we deactivated is left alone`() {
        // Session applied dim 0, so the last thing written was a deactivation.
        // The user then turns Extra Dim on at 70 in system Settings. Restoring
        // the captured original here would silently switch their setting back
        // off, which is the one thing the ownership rule exists to prevent.
        assertThat(
            shouldRestoreReduceBrightColors(
                currentActive = true,
                currentLevel = 70,
                lastAppliedLevel = 0
            )
        ).isFalse()
    }

    @Test fun `a deactivation we wrote is still ours while it stays off`() {
        assertThat(
            shouldRestoreReduceBrightColors(
                currentActive = false,
                currentLevel = 0,
                lastAppliedLevel = 0
            )
        ).isTrue()
    }

    @Test fun `dim maps onto the documented 0 to 100 Extra Dim percentage`() {
        assertThat(SecureSettingsEngine.reduceBrightColorsLevel(0f)).isEqualTo(0)
        assertThat(SecureSettingsEngine.reduceBrightColorsLevel(0.25f)).isEqualTo(25)
        assertThat(SecureSettingsEngine.reduceBrightColorsLevel(0.5f)).isEqualTo(50)
        // The dim preference tops out at 0.95, which is the deepest the driver
        // can be asked for; it must not saturate early or overshoot 100.
        assertThat(SecureSettingsEngine.reduceBrightColorsLevel(0.95f)).isEqualTo(95)
        assertThat(SecureSettingsEngine.reduceBrightColorsLevel(1f)).isEqualTo(100)
    }

    @Test fun `out of range and non-finite dim values never leave the valid range`() {
        // Garbage fails safe toward no dim rather than toward a screen slammed
        // to full strength, so every non-finite input maps to 0 — including
        // positive infinity.
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThat(SecureSettingsEngine.reduceBrightColorsLevel(value)).isEqualTo(0)
        }
        for (value in listOf(-1f, -0.01f)) {
            assertThat(SecureSettingsEngine.reduceBrightColorsLevel(value)).isEqualTo(0)
        }
        for (value in listOf(2f, 100f)) {
            assertThat(SecureSettingsEngine.reduceBrightColorsLevel(value))
                .isEqualTo(SecureSettingsEngine.MAX_REDUCE_BRIGHT_LEVEL)
        }
    }

    @Test fun `secure keys are the rows ColorDisplayService observes`() {
        // Typos here are silent: Settings.Secure.putInt on an unknown row
        // succeeds and simply never reaches ColorDisplayService, so the driver
        // would report available and do nothing. Pin the exact strings.
        assertThat(SecureSettingsEngine.KEY_NIGHT_ACTIVATED).isEqualTo("night_display_activated")
        assertThat(SecureSettingsEngine.KEY_NIGHT_TEMPERATURE)
            .isEqualTo("night_display_color_temperature")
        assertThat(SecureSettingsEngine.KEY_NIGHT_AUTO_MODE).isEqualTo("night_display_auto_mode")
        assertThat(SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED)
            .isEqualTo("reduce_bright_colors_activated")
        assertThat(SecureSettingsEngine.KEY_REDUCE_BRIGHT_LEVEL)
            .isEqualTo("reduce_bright_colors_level")
    }

    @Test fun `api floor is the release that introduced the night display keys`() {
        // Releases through 0.7.1 claimed 28. ColorDisplayManager and the
        // night_display_* secure rows both arrive in Q.
        assertThat(SecureSettingsEngine.MIN_API).isEqualTo(29)
        assertThat(SecureSettingsEngine.REDUCE_BRIGHT_COLORS_MIN_API).isEqualTo(31)
    }
}
