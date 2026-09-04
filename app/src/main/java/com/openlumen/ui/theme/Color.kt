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

    // Derived tones, not part of Catppuccin. Each one is here because a
    // Material role needed a value the palette does not carry, and each is
    // chosen for its measured contrast against what sits on it. The figures
    // are asserted in ThemeRolesTest, so they cannot drift quietly.

    // Warning and error card fills. Peach on PeachContainer is 6.15:1 and Red
    // on RedContainer is 5.22:1, so a warning reads as a warning next to a
    // plain card instead of sharing its fill.
    val PeachContainer = Color(0xFF463A3B)
    val RedContainer   = Color(0xFF44303E)

    // One step between Surface0 and Surface1, so the container ramp has five
    // distinct values and none of them collides with surfaceVariant.
    val SurfaceContainerHigh = Color(0xFF3B3C4F)

    // inversePrimary, which is what a Snackbar action is painted with. The
    // inverse surface in this scheme is light, so this has to be a dark
    // purple: Latte's own Mauve on it is 3.74:1, under AA. This is 5.50:1.
    val InversePrimary = Color(0xFF6B21C4)

    // The red channel for the sliders. Catppuccin's own Red is 2.88:1 against
    // the unfilled track those controls sit on, just under the 3:1 floor for
    // something you have to aim. This is the same hue, slightly lighter.
    val RedChannel = Color(0xFFF497B1)
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

    // Derived tones, as in Mocha. Latte needs more of them because its
    // palette has only three steps below Surface0, and because dark text on a
    // pale ground is where contrast actually goes wrong.

    // Warning and error card fills, with their own text colours. Catppuccin's
    // Latte Peach (#FE640B) on this ground is 2.28:1 and Latte Red is 3.69:1,
    // both of which fail AA, so the accent stays the card's identity and a
    // darker tone carries the words: 5.97:1 and 5.78:1.
    val PeachContainer = Color(0xFFF7DCC8)
    val PeachText      = Color(0xFF8A3A00)
    val RedContainer   = Color(0xFFF3CBD1)
    val RedText        = Color(0xFF9C0626)

    // Two steps inside the container ramp, so it has five distinct values
    // ending at Surface0 rather than running down to Surface1, where
    // secondary text cannot reach 4.5:1.
    val SurfaceContainer     = Color(0xFFE1E4EC)
    val SurfaceContainerHigh = Color(0xFFD4D8E1)

    // Secondary text. Subtext1 manages only 4.05:1 on the darkest container in
    // this ramp; this is one step darker and clears 4.5:1 on all of them
    // while staying visibly lighter than Text.
    val SecondaryText = Color(0xFF545771)

    // Filled accents. Catppuccin's Latte Pink and Teal are decorative tones,
    // and a filled button painting Base on them reaches only 2.34:1 and
    // 3.31:1. These keep the hue and take it dark enough to carry light text:
    // 5.61:1 and 5.45:1.
    val PinkStrong = Color(0xFFA32F86)
    val TealStrong = Color(0xFF0D6C72)

    // inversePrimary here sits on a dark inverse surface, so it is the light
    // purple. Mocha's own Mauve is 3.93:1 on it; this is 5.60:1.
    val InversePrimary = Color(0xFFE4D0FC)

    // Channel colours for the RGB and gamma sliders. Catppuccin's Latte Red,
    // Green and Blue are chosen to read as text on a light ground, not as a
    // filled control: against the card they are 3.52, 2.17 and 3.18 to one,
    // and against the slider's own unfilled track 2.51, 1.55 and 2.27. Green
    // in particular vanishes into its own track. These are the same hues
    // taken dark enough to clear 3:1 on both, which is the floor for a
    // control you have to be able to see and position.
    val RedChannel   = Color(0xFF930A28)
    val GreenChannel = Color(0xFF26601A)
    val BlueChannel  = Color(0xFF1547AC)
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
    red = Catppuccin.RedChannel,
    green = Catppuccin.Green,
    blue = Catppuccin.Blue
)

/**
 * Latte channel hues, darkened. These paint a slider's thumb and filled track,
 * so they have to be visible against the card behind them and against the
 * unfilled part of their own track, which the palette's own tones are not.
 */
internal val LightChannelColors = ChannelColors(
    red = Latte.RedChannel,
    green = Latte.GreenChannel,
    blue = Latte.BlueChannel
)
