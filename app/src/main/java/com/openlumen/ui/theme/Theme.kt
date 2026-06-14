package com.openlumen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Catppuccin.Mauve,
    onPrimary = Catppuccin.Crust,
    primaryContainer = Catppuccin.Surface1,
    onPrimaryContainer = Catppuccin.Text,
    secondary = Catppuccin.Pink,
    onSecondary = Catppuccin.Crust,
    tertiary = Catppuccin.Teal,
    onTertiary = Catppuccin.Crust,
    background = Catppuccin.Amoled,
    onBackground = Catppuccin.Text,
    surface = Catppuccin.Mantle,
    onSurface = Catppuccin.Text,
    surfaceVariant = Catppuccin.Surface0,
    onSurfaceVariant = Catppuccin.Subtext1,
    outline = Catppuccin.Overlay1,
    outlineVariant = Catppuccin.Surface2,
    error = Catppuccin.Red,
    onError = Catppuccin.Crust
)

// Full Catppuccin Latte mapping. Previously only primary/secondary/tertiary
// were set, leaving every other role on the Material baseline — which paired a
// pastel-purple primary with white onPrimary and gave unreadable secondary text
// on light surfaces. Each role here mirrors the Mocha structure with Latte tones
// chosen for WCAG-AA contrast (e.g. Subtext1 on the Crust card surface ≈ 4.7:1).
private val LightColors = lightColorScheme(
    primary = Latte.Mauve,
    onPrimary = Latte.Base,
    primaryContainer = Latte.Surface0,
    onPrimaryContainer = Latte.Text,
    secondary = Latte.Pink,
    onSecondary = Latte.Base,
    tertiary = Latte.Teal,
    onTertiary = Latte.Base,
    tertiaryContainer = Latte.Surface0,
    onTertiaryContainer = Latte.Text,
    background = Latte.Base,
    onBackground = Latte.Text,
    surface = Latte.Base,
    onSurface = Latte.Text,
    surfaceVariant = Latte.Crust,
    onSurfaceVariant = Latte.Subtext1,
    outline = Latte.Overlay1,
    outlineVariant = Latte.Surface1,
    error = Latte.Red,
    onError = Latte.Base,
    errorContainer = Latte.Crust,
    onErrorContainer = Latte.Red
)

/**
 * Theme-aware R/G/B channel-indicator colors for slider tracks and channel
 * preview bars. Read inside any composable under [OpenLumenTheme]; resolves to
 * the Mocha or Latte channel hues to match the active theme's surfaces.
 */
@Composable
internal fun lumenChannelColors(darkTheme: Boolean = isSystemInDarkTheme()): ChannelColors =
    if (darkTheme) DarkChannelColors else LightChannelColors

@Composable
fun OpenLumenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        shapes = OpenLumenShapes,
        content = content
    )
}
