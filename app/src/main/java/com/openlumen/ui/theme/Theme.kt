package com.openlumen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val DarkColors = darkColorScheme(
    primary = Catppuccin.Mauve,
    onPrimary = Catppuccin.Crust,
    primaryContainer = Catppuccin.Surface1,
    onPrimaryContainer = Catppuccin.Text,
    secondary = Catppuccin.Pink,
    onSecondary = Catppuccin.Crust,
    secondaryContainer = Catppuccin.Surface0,
    onSecondaryContainer = Catppuccin.Text,
    tertiary = Catppuccin.Teal,
    onTertiary = Catppuccin.Crust,
    // Peach over Surface0 rather than Surface0 itself: a warning card that
    // shares its fill with every plain card is not a warning. The Solar
    // location, exact-alarm and recovery cards all use this role.
    tertiaryContainer = Catppuccin.PeachContainer,
    onTertiaryContainer = Catppuccin.Peach,
    background = Catppuccin.Amoled,
    onBackground = Catppuccin.Text,
    surface = Catppuccin.Mantle,
    onSurface = Catppuccin.Text,
    surfaceVariant = Catppuccin.Surface0,
    onSurfaceVariant = Catppuccin.Subtext1,
    // The surface-container roles are what Card, AlertDialog, Snackbar,
    // TimePicker and the Slider's inactive track actually paint with. Leaving
    // them unset meant Material's own baseline greys, so an app whose whole
    // point is a controlled palette mixed off-palette #36343B into every
    // card. They step Crust to Surface0 the way Material steps its own.
    surfaceContainerLowest = Catppuccin.Crust,
    surfaceContainerLow = Catppuccin.Mantle,
    surfaceContainer = Catppuccin.Base,
    surfaceContainerHigh = Catppuccin.Surface0,
    surfaceContainerHighest = Catppuccin.Surface1,
    surfaceBright = Catppuccin.Surface0,
    surfaceDim = Catppuccin.Amoled,
    // Inverse roles carry the Snackbar and tooltips: light text ground with
    // dark text on it.
    inverseSurface = Catppuccin.Text,
    inverseOnSurface = Catppuccin.Base,
    inversePrimary = Latte.Mauve,
    scrim = Catppuccin.Crust,
    outline = Catppuccin.Overlay1,
    outlineVariant = Catppuccin.Surface2,
    error = Catppuccin.Red,
    onError = Catppuccin.Crust,
    errorContainer = Catppuccin.RedContainer,
    onErrorContainer = Catppuccin.Red
)

// Full Catppuccin Latte mapping. Previously only primary/secondary/tertiary
// were set, leaving every other role on the Material baseline — which paired a
// pastel-purple primary with white onPrimary and gave unreadable secondary text
// on light surfaces. Each role here mirrors the Mocha structure with Latte tones
// chosen for WCAG-AA contrast (e.g. Subtext1 on the Crust card surface ≈ 4.7:1).
internal val LightColors = lightColorScheme(
    primary = Latte.Mauve,
    onPrimary = Latte.Base,
    primaryContainer = Latte.Surface0,
    onPrimaryContainer = Latte.Text,
    secondary = Latte.Pink,
    onSecondary = Latte.Base,
    secondaryContainer = Latte.Surface0,
    onSecondaryContainer = Latte.Text,
    tertiary = Latte.Teal,
    onTertiary = Latte.Base,
    tertiaryContainer = Latte.PeachContainer,
    onTertiaryContainer = Latte.Peach,
    background = Latte.Base,
    onBackground = Latte.Text,
    surface = Latte.Base,
    onSurface = Latte.Text,
    surfaceVariant = Latte.Crust,
    onSurfaceVariant = Latte.Subtext1,
    surfaceContainerLowest = Latte.Base,
    surfaceContainerLow = Latte.Mantle,
    surfaceContainer = Latte.Crust,
    surfaceContainerHigh = Latte.Surface0,
    surfaceContainerHighest = Latte.Surface1,
    surfaceBright = Latte.Base,
    surfaceDim = Latte.Crust,
    inverseSurface = Latte.Text,
    inverseOnSurface = Latte.Base,
    inversePrimary = Catppuccin.Mauve,
    scrim = Latte.Text,
    outline = Latte.Overlay1,
    outlineVariant = Latte.Surface1,
    error = Latte.Red,
    onError = Latte.Base,
    errorContainer = Latte.RedContainer,
    onErrorContainer = Latte.Red
)

/**
 * Which scheme [OpenLumenTheme] applied, as opposed to what the system asked
 * for. The two differ whenever a caller passes `darkTheme` explicitly, which
 * the screenshot suite does for every light-theme render, and the widgets and
 * previews can do too.
 */
internal val LocalDarkTheme = staticCompositionLocalOf { true }

/**
 * Theme-aware R/G/B channel-indicator colors for slider tracks and channel
 * preview bars. Read inside any composable under [OpenLumenTheme]; resolves to
 * the Mocha or Latte channel hues to match the active theme's surfaces.
 *
 * It used to default to [isSystemInDarkTheme], so a Latte UI rendered on a
 * device in dark mode got Mocha channel pastels on light surfaces.
 */
@Composable
internal fun lumenChannelColors(darkTheme: Boolean = LocalDarkTheme.current): ChannelColors =
    if (darkTheme) DarkChannelColors else LightChannelColors

@Composable
fun OpenLumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MaterialTheme.typography,
            shapes = OpenLumenShapes,
            content = content
        )
    }
}
