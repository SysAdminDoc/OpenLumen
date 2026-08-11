package com.openlumen.ui.components

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabeledSliderSemanticsTest {

    @Test
    fun allAdjustableSliderVariantsExposeNameAndCurrentValue() {
        val variants = listOf(
            "Intensity" to "50 percent",
            "Dim below minimum" to "10 percent",
            "Contrast" to "1.00 times",
            "Red channel" to "Red channel 25 percent",
            "Color temperature" to "6500 Kelvin",
            "Red gamma" to "γR gamma 1.00",
            "Sunset offset" to "Sunset offset: -15 m",
            "Sunrise offset" to "Sunrise offset: 20 m",
            "Ambient-light threshold" to "40 lux threshold"
        )

        variants.forEach { (name, valueDescription) ->
            val semantics = SemanticsConfiguration()
            semantics.applyLabeledSliderSemantics(name, valueDescription)

            assertThat(semantics[SemanticsProperties.ContentDescription])
                .containsExactly(name)
            assertThat(semantics[SemanticsProperties.StateDescription])
                .isEqualTo(valueDescription)
        }
    }
}
