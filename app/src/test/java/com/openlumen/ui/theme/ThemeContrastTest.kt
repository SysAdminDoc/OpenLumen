package com.openlumen.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import kotlin.math.pow
import org.junit.Test

/**
 * Every pair of roles the app puts text on, checked against WCAG AA.
 *
 * C320 filled in the roles that were still on the Material baseline, and in
 * doing so gave the light theme a warning card whose own text sat at 2.28:1 on
 * it, and a secondary-text colour that could not reach 4.5:1 on the darkest
 * container it now had. Nothing caught either one: the tests that came with
 * that change only asked whether a role had been set, never whether anyone
 * could read what was on it.
 */
class ThemeContrastTest {

    /** WCAG 2.1 relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

/**
     * Foreground/background pairs Material actually paints together.
     *
     * surfaceContainerHighest is in here, and has to be. A Card takes it by
     * default, so it is the ground most of this app's text sits on. That was
     * worth getting wrong once: leaving it out to make a darker ramp pass was
     * excusing the exact pairing the user reads.
     */
    private fun textPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onBackground on background", scheme.onBackground, scheme.background),
        Triple("onSurface on surface", scheme.onSurface, scheme.surface),
        Triple("onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant),
        Triple("onSurface on surfaceContainerLowest", scheme.onSurface, scheme.surfaceContainerLowest),
        Triple("onSurface on surfaceContainerLow", scheme.onSurface, scheme.surfaceContainerLow),
        Triple("onSurface on surfaceContainer", scheme.onSurface, scheme.surfaceContainer),
        Triple("onSurface on surfaceContainerHigh", scheme.onSurface, scheme.surfaceContainerHigh),
        Triple("onSurface on surfaceContainerHighest", scheme.onSurface, scheme.surfaceContainerHighest),
        Triple("onSurfaceVariant on surfaceContainerLowest", scheme.onSurfaceVariant, scheme.surfaceContainerLowest),
        Triple("onSurfaceVariant on surfaceContainerLow", scheme.onSurfaceVariant, scheme.surfaceContainerLow),
        Triple("onSurfaceVariant on surfaceContainer", scheme.onSurfaceVariant, scheme.surfaceContainer),
        Triple("onSurfaceVariant on surfaceContainerHigh", scheme.onSurfaceVariant, scheme.surfaceContainerHigh),
        Triple("onSurfaceVariant on surfaceContainerHighest", scheme.onSurfaceVariant, scheme.surfaceContainerHighest),
        Triple("onPrimary on primary", scheme.onPrimary, scheme.primary),
        Triple("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
        Triple("onSecondary on secondary", scheme.onSecondary, scheme.secondary),
        Triple("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer),
        Triple("onTertiary on tertiary", scheme.onTertiary, scheme.tertiary),
        Triple("onTertiaryContainer on tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer),
        Triple("onError on error", scheme.onError, scheme.error),
        Triple("onErrorContainer on errorContainer", scheme.onErrorContainer, scheme.errorContainer),
        Triple("inverseOnSurface on inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface),
        // A Snackbar's action label. Material paints it with inversePrimary on
        // inverseSurface, and this app uses the default for the profile-delete
        // Undo, so it is text a user has to read under time pressure.
        Triple("inversePrimary on inverseSurface", scheme.inversePrimary, scheme.inverseSurface)
    )

    /**
     * Controls rather than text. WCAG's floor for something you have to see
     * and position is 3:1 against what is next to it, not 4.5:1.
     */
    private fun controlPairs(scheme: ColorScheme, channels: ChannelColors): List<Triple<String, Color, Color>> =
        listOf("red" to channels.red, "green" to channels.green, "blue" to channels.blue)
            .flatMap { (name, channel) ->
                listOf(
                    Triple("$name channel thumb on the card", channel, scheme.surfaceContainerHighest),
                    Triple("$name channel fill against its own unfilled track", channel, scheme.outlineVariant)
                )
            }

    private fun failuresIn(scheme: ColorScheme): List<String> =
        textPairs(scheme)
            .map { (name, fg, bg) -> name to contrast(fg, bg) }
            .filter { (_, ratio) -> ratio < 4.5 }
            .map { (name, ratio) -> "$name is %.2f:1".format(ratio) }

    @Test fun `every text pair in the dark scheme clears AA`() {
        assertThat(failuresIn(DarkColors)).isEmpty()
    }

    @Test fun `every text pair in the light scheme clears AA`() {
        assertThat(failuresIn(LightColors)).isEmpty()
    }

    @Test fun `the ratio is computed the way WCAG defines it`() {
        // Positive control. Without it, a broken formula that returned a large
        // number for everything would make both assertions above vacuous.
        assertThat(contrast(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(contrast(Color.White, Color.White)).isWithin(0.01).of(1.0)
        // The pairing this project actually got wrong: Latte's own Peach on
        // the warning-card ground it was paired with.
        assertThat(contrast(Latte.Peach, Latte.PeachContainer)).isLessThan(4.5)
    }

    @Test fun `an outline is visible on the card it is drawn on`() {
        // A Card and a Slider both default to surfaceContainerHighest, so a
        // slider in a card drew its unfilled track in exactly the card's own
        // colour and the control looked like it stopped at the thumb. The same
        // colour was the outlined buttons' border, which disappeared with it.
        // Both take the outline tone now, so this is what has to stay apart.
        for (scheme in listOf(DarkColors, LightColors)) {
            assertThat(contrast(scheme.outlineVariant, scheme.surfaceContainerHighest))
                .isGreaterThan(1.3)
            assertThat(contrast(scheme.outlineVariant, scheme.surfaceVariant))
                .isGreaterThan(1.3)
        }
    }

    @Test fun `a slider you have to aim is visible in both themes`() {
        // The RGB and gamma sliders paint their thumb and filled track with the
        // channel's own colour. Latte's Green managed 2.17:1 against the card
        // and 1.55:1 against its own unfilled track, so the green slider
        // disappeared into itself in the light theme.
        val failures = buildList {
            for ((scheme, channels) in listOf(
                DarkColors to DarkChannelColors,
                LightColors to LightChannelColors
            )) {
                for ((name, fg, bg) in controlPairs(scheme, channels)) {
                    val ratio = contrast(fg, bg)
                    if (ratio < 3.0) add("$name is %.2f:1".format(ratio))
                }
            }
        }

        assertThat(failures).isEmpty()
    }

    @Test fun `no container role paints the same colour as the surface behind it`() {
        // A surfaceContainerHigh component sitting on a surfaceVariant surface
        // had no edge at all in the dark scheme, because both were Surface0.
        for (scheme in listOf(DarkColors, LightColors)) {
            val containers = listOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest
            )

            assertThat(containers).containsNoDuplicates()
            assertThat(containers).doesNotContain(scheme.surfaceVariant)
        }
    }
}
