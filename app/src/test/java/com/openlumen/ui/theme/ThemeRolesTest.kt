package com.openlumen.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * C320. Half the Material colour roles were never set, so every plain Card,
 * AlertDialog, Snackbar, TimePicker dial and Slider inactive track painted
 * itself from Material's own baseline greys. An app whose whole point is a
 * controlled palette was mixing off-palette #36343B into its cards, and the
 * warning containers shared a fill with plain ones so they carried no weight.
 */
class ThemeRolesTest {

    /**
     * The roles this app actually paints with. Deliberately not every property
     * on ColorScheme: Material 3 carries a set of "fixed" roles for expressive
     * components nothing here uses, and asserting on those would be asserting
     * about code that never runs.
     */
    private fun paintedRoles(scheme: ColorScheme): Map<String, Color> = mapOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onPrimaryContainer" to scheme.onPrimaryContainer,
        "secondary" to scheme.secondary,
        "onSecondary" to scheme.onSecondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "onTertiary" to scheme.onTertiary,
        "tertiaryContainer" to scheme.tertiaryContainer,
        "onTertiaryContainer" to scheme.onTertiaryContainer,
        "background" to scheme.background,
        "onBackground" to scheme.onBackground,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "surfaceBright" to scheme.surfaceBright,
        "surfaceDim" to scheme.surfaceDim,
        "inverseSurface" to scheme.inverseSurface,
        "inverseOnSurface" to scheme.inverseOnSurface,
        "inversePrimary" to scheme.inversePrimary,
        "scrim" to scheme.scrim,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
        "error" to scheme.error,
        "onError" to scheme.onError,
        "errorContainer" to scheme.errorContainer,
        "onErrorContainer" to scheme.onErrorContainer
    )

    /** Roles still carrying whatever Material's own scheme would have used. */
    private fun rolesLeftAtBaseline(scheme: ColorScheme, baseline: ColorScheme): Set<String> {
        val defaults = paintedRoles(baseline)
        return paintedRoles(scheme).filter { (role, color) -> defaults[role] == color }.keys
    }

    @Test fun `the dark scheme sets every role it paints with`() {
        assertThat(rolesLeftAtBaseline(DarkColors, darkColorScheme())).isEmpty()
    }

    @Test fun `the light scheme sets every role it paints with`() {
        assertThat(rolesLeftAtBaseline(LightColors, lightColorScheme())).isEmpty()
    }

    @Test fun `an unset role is what this catches`() {
        // Positive control. Without it the two assertions above would still
        // pass if paintedRoles quietly stopped returning anything.
        val partial = darkColorScheme(primary = Catppuccin.Mauve)

        assertThat(rolesLeftAtBaseline(partial, darkColorScheme()))
            .contains("surfaceContainerHigh")
    }

    @Test fun `a warning card does not share its fill with a plain card`() {
        // tertiaryContainer carries the solar-location, exact-alarm and
        // recovery cards. It used to be Surface0, the same fill a plain card
        // takes, so those warnings read as ordinary content.
        for (scheme in listOf(DarkColors, LightColors)) {
            assertThat(scheme.tertiaryContainer).isNotEqualTo(scheme.surfaceVariant)
            assertThat(scheme.tertiaryContainer).isNotEqualTo(scheme.surface)
            assertThat(scheme.errorContainer).isNotEqualTo(scheme.surfaceVariant)
            assertThat(scheme.errorContainer).isNotEqualTo(scheme.surface)
            assertThat(scheme.tertiaryContainer).isNotEqualTo(scheme.errorContainer)
        }
    }

    @Test fun `the two schemes are actually different`() {
        assertThat(DarkColors.background).isNotEqualTo(LightColors.background)
        assertThat(DarkColors.onSurface).isNotEqualTo(LightColors.onSurface)
        assertThat(DarkColors.surfaceContainerHigh).isNotEqualTo(LightColors.surfaceContainerHigh)
    }
}
