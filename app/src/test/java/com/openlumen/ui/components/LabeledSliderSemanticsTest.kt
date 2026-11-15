package com.openlumen.ui.components

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabeledSliderSemanticsTest {

    @Test
    fun allAdjustableSliderVariantsExposeNameAndCurrentValue() {
        val variants = listOf(
            "Intensity" to "50 percent",
            "Dim below minimum" to "10 percent",
            "Contrast" to "1.00 times",
            "Red channel" to "25 percent",
            "Color temperature" to "6500 Kelvin",
            "Red gamma" to "1.00",
            "Sunset offset" to "Sunset offset: -15 min",
            "Sunrise offset" to "Sunrise offset: 20 min",
            "Ambient-light threshold" to "40 lux threshold"
        )

        variants.forEach { (name, valueDescription) ->
            val semantics = SemanticsConfiguration()
            semantics.applyLabeledSliderSemantics(name, valueDescription)

            assertThat(semantics[SemanticsProperties.Text].map { it.text })
                .containsExactly(name)
            assertThat(semantics[SemanticsProperties.StateDescription])
                .isEqualTo(valueDescription)
        }
    }

    @Test
    fun theNameIsNeverADescription() {
        // A Material 3 Slider merges its descendants and has children, and
        // Compose turns a contentDescription on such a node into a separate
        // accessibility node. TalkBack 17 read that as its own stop after the
        // slider, which said only "0 percent, slider".
        val semantics = SemanticsConfiguration()
        semantics.applyLabeledSliderSemantics("Intensity", "50 percent")

        assertThat(semantics.getOrNull(SemanticsProperties.ContentDescription)).isNull()
    }
}
