package com.openlumen.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.math.pow
import org.junit.Test

/**
 * C336. A selected filter chip and a disabled switch were the same colour as
 * their neighbours.
 *
 * Material's selected chip fill is `secondaryContainer`, and this theme maps
 * that to one step off the surface in both palettes. Measured against the
 * screen behind it that is 1.40:1 in dark and 1.37:1 in light, so the only
 * thing separating a selected chip from an unselected one was a difference
 * neither of them could carry. Colour was also the only state carrier: no
 * icon, no shape change, nothing for a user the colour does not reach.
 *
 * WCAG 2.1 1.4.11 wants 3:1 for the parts of a control that identify its
 * state. `primary` clears that in both themes, which is why the chip uses it.
 */
class SelectedStateContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    /**
     * What an unselected chip shows: it has no fill of its own, so the ground
     * behind it is what a selected one has to stand out from.
     */
    private fun groundsUnder(scheme: ColorScheme) = listOf(
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest
    )

    private fun assertSelectedFillStandsOut(name: String, scheme: ColorScheme) {
        for ((ground, color) in groundsUnder(scheme)) {
            val ratio = contrast(scheme.primary, color)
            assertWithMessage("$name: selected chip fill on $ground")
                .that(ratio)
                .isAtLeast(3.0)
        }
    }

    @Test fun `a selected chip stands out from every ground it sits on`() {
        // Tie the measurement to the fill the chip actually asks for. Measuring
        // `primary` alone said nothing about the chip: swapping its fill back to
        // secondaryContainer left this passing, because the token it measured
        // had not moved.
        val chip = File(
            "src/main/java/com/openlumen/ui/components/LumenFilterChip.kt"
        ).readText()
        assertWithMessage("the selected fill is the role this measures")
            .that(chip)
            .contains("selectedContainerColor = MaterialTheme.colorScheme.primary")

        assertSelectedFillStandsOut("dark", DarkColors)
        assertSelectedFillStandsOut("light", LightColors)
    }

    @Test fun `the fill Material would have used does not, which is why it is not used`() {
        // The measurement behind the change. If this ever starts passing, the
        // palette moved and the chip could go back to the Material role.
        for ((name, scheme) in listOf("dark" to DarkColors, "light" to LightColors)) {
            val ratio = contrast(scheme.secondaryContainer, scheme.surface)
            assertWithMessage("$name: secondaryContainer on surface")
                .that(ratio)
                .isLessThan(3.0)
        }
    }

    @Test fun `a selected chip is identifiable with colour removed`() {
        // The check mark, not the fill. A user who cannot separate the two
        // greys, or who is looking at a greyscale screenshot, still has to be
        // able to tell which chip is on.
        val chip = File(
            "src/main/java/com/openlumen/ui/components/LumenFilterChip.kt"
        ).readText()

        assertWithMessage("the selected chip draws a check")
            .that(chip).contains("R.drawable.ic_check")
        assertWithMessage("and only when it is selected")
            .that(chip).contains("leadingIcon = if (selected)")
    }

    @Test fun `every chip in the app goes through the wrapper`() {
        // A bare FilterChip would take the Material fill back and lose the
        // check, and it would do it silently.
        val screens = File("src/main/java/com/openlumen/ui").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "LumenFilterChip.kt" }

        // A bare call, not the wrapper's own name inside it.
        val bare = Regex("(?<![A-Za-z])FilterChip\\(")
        for (file in screens) {
            assertWithMessage("${file.name} uses a bare FilterChip")
                .that(bare.containsMatchIn(file.readText()))
                .isFalse()
        }
    }

    @Test fun `a driver the device cannot use is dimmed as a whole row`() {
        // Only the radio button went grey, so the one control most of the row
        // does not touch was the only thing saying the driver is unavailable.
        // The screenshot baselines carry what it looks like; this pins that
        // the row and not just its label carries the state.
        val driver = File("src/main/java/com/openlumen/ui/screens/DriverScreen.kt").readText()

        assertWithMessage("the row dims when it is not selectable")
            .that(driver).contains(".alpha(if (selectable) 1f else 0.38f)")
    }

    @Test fun `a switch has an edge in every state it can be in`() {
        // The disabled track is surfaceVariant, on cards that are often
        // surfaceVariant too, and the unchecked track is outlineVariant, which
        // is barely a step off either. The border is what makes the control
        // visible at all in those two states.
        val switch = File(
            "src/main/java/com/openlumen/ui/components/LumenSwitch.kt"
        ).readText()

        assertWithMessage("the track draws a border")
            .that(switch).contains(".border(")

        // The border's job is to give the control an edge against what is
        // behind it, so that is what gets measured. `outline` was the obvious
        // choice and it reaches 1.48:1 on a light unchecked track, which is
        // not an edge.
        for ((name, scheme) in listOf("dark" to DarkColors, "light" to LightColors)) {
            for ((ground, color) in groundsUnder(scheme)) {
                assertWithMessage("$name: switch border on $ground")
                    .that(contrast(scheme.onSurfaceVariant, color))
                    .isAtLeast(3.0)
            }
        }
    }
}
