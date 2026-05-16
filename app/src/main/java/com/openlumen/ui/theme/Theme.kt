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

private val LightColors = lightColorScheme(
    primary = Catppuccin.Mauve,
    secondary = Catppuccin.Pink,
    tertiary = Catppuccin.Teal
)

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
