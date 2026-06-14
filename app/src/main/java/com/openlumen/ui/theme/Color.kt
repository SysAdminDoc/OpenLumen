package com.openlumen.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Catppuccin Mocha palette + AMOLED true-black surface.
 * Per the user's "no pill backdrops" rule, surface containers in the UI use
 * RoundedCornerShape values capped at 12dp anywhere a backdrop appears.
 */
internal object Catppuccin {
    val Rosewater = Color(0xFFF5E0DC)
    val Flamingo  = Color(0xFFF2CDCD)
    val Pink      = Color(0xFFF5C2E7)
    val Mauve     = Color(0xFFCBA6F7)
    val Red       = Color(0xFFF38BA8)
    val Maroon    = Color(0xFFEBA0AC)
    val Peach     = Color(0xFFFAB387)
    val Yellow    = Color(0xFFF9E2AF)
    val Green     = Color(0xFFA6E3A1)
    val Teal      = Color(0xFF94E2D5)
    val Sky       = Color(0xFF89DCEB)
    val Sapphire  = Color(0xFF74C7EC)
    val Blue      = Color(0xFF89B4FA)
    val Lavender  = Color(0xFFB4BEFE)
    val Text      = Color(0xFFCDD6F4)
    val Subtext1  = Color(0xFFBAC2DE)
    val Subtext0  = Color(0xFFA6ADC8)
    val Overlay2  = Color(0xFF9399B2)
    val Overlay1  = Color(0xFF7F849C)
    val Overlay0  = Color(0xFF6C7086)
    val Surface2  = Color(0xFF585B70)
    val Surface1  = Color(0xFF45475A)
    val Surface0  = Color(0xFF313244)
    val Base      = Color(0xFF1E1E2E)
    val Mantle    = Color(0xFF181825)
    val Crust     = Color(0xFF11111B)
    val Amoled    = Color(0xFF000000)
}

/**
 * Catppuccin Latte palette — the light-theme counterpart to [Catppuccin]
 * (Mocha). Same hue identity, inverted tonal layering so the product reads as
 * one design system across light and dark. Values are the official Latte
 * flavor (https://catppuccin.com/palette).
 */
internal object Latte {
    val Rosewater = Color(0xFFDC8A78)
    val Flamingo  = Color(0xFFDD7878)
    val Pink      = Color(0xFFEA76CB)
    val Mauve     = Color(0xFF8839EF)
    val Red       = Color(0xFFD20F39)
    val Maroon    = Color(0xFFE64553)
    val Peach     = Color(0xFFFE640B)
    val Yellow    = Color(0xFFDF8E1D)
    val Green     = Color(0xFF40A02B)
    val Teal      = Color(0xFF179299)
    val Sky       = Color(0xFF04A5E5)
    val Sapphire  = Color(0xFF209FB5)
    val Blue      = Color(0xFF1E66F5)
    val Lavender  = Color(0xFF7287FD)
    val Text      = Color(0xFF4C4F69)
    val Subtext1  = Color(0xFF5C5F77)
    val Subtext0  = Color(0xFF6C6F85)
    val Overlay2  = Color(0xFF7C7F93)
    val Overlay1  = Color(0xFF8C8FA1)
    val Overlay0  = Color(0xFF9CA0B0)
    val Surface2  = Color(0xFFACB0BE)
    val Surface1  = Color(0xFFBCC0CC)
    val Surface0  = Color(0xFFCCD0DA)
    val Base      = Color(0xFFEFF1F5)
    val Mantle    = Color(0xFFE6E9EF)
    val Crust     = Color(0xFFDCE0E8)
}

/**
 * R/G/B channel-indicator colors used by the slider tracks (HomeScreen) and
 * the per-channel preview bars (PresetsScreen). These denote literal red /
 * green / blue light channels, so they stay recognizably red/green/blue in
 * both themes rather than swapping to an accent token — but they were
 * previously hardcoded inconsistently (Mocha pastels in one screen, unrelated
 * brights in another). Centralizing here makes channel identity one source of
 * truth and lets each theme pick a variant tuned for its surface contrast.
 */
@androidx.compose.runtime.Immutable
internal data class ChannelColors(val red: Color, val green: Color, val blue: Color)

internal val DarkChannelColors = ChannelColors(
    red = Catppuccin.Red,
    green = Catppuccin.Green,
    blue = Catppuccin.Blue
)

/** Latte channel hues — more saturated so they read on the light surfaces. */
internal val LightChannelColors = ChannelColors(
    red = Latte.Red,
    green = Latte.Green,
    blue = Latte.Blue
)
