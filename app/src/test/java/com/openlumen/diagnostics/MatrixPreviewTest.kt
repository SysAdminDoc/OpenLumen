package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import org.junit.Test

class MatrixPreviewTest {

    @Test fun `perceived luminance reduction is zero for identity custom matrix`() {
        val prefs = Preferences(
            activePresetKey = "custom",
            customMatrix = MatrixDto(),
            presetIntensity = 1f,
            dim = 0f,
            contrast = 1f
        )

        val reduction = MatrixPreview.perceivedLuminanceReduction(prefs)

        assertThat(reduction).isWithin(0.0001f).of(0f)
    }

    @Test fun `perceived luminance reduction follows dim factor`() {
        val prefs = Preferences(
            activePresetKey = "custom",
            customMatrix = MatrixDto(),
            presetIntensity = 1f,
            dim = 0.5f,
            contrast = 1f
        )

        val reduction = MatrixPreview.perceivedLuminanceReduction(prefs)

        assertThat(reduction).isWithin(0.0001f).of(0.5f)
    }

    @Test fun `perceived luminance reduction uses sRGB channel weights`() {
        val prefs = Preferences(
            activePresetKey = "custom",
            customMatrix = MatrixDto(r = 1f, g = 0.5f, b = 0f),
            presetIntensity = 1f,
            dim = 0f,
            contrast = 1f
        )

        val reduction = MatrixPreview.perceivedLuminanceReduction(prefs)

        assertThat(reduction).isWithin(0.0001f).of(0.4298f)
    }

    @Test fun `preset intensity applies to CVD matrix coefficients`() {
        val prefs = Preferences(
            activePresetKey = "protan",
            presetIntensity = 0.5f,
            dim = 0f,
            contrast = 1f
        )

        val matrix = MatrixPreview.matrixFor(prefs)

        assertThat(matrix.hasColorMatrix).isTrue()
        assertThat(matrix.matrixRr).isWithin(0.0001f).of(0.55619f)
        assertThat(matrix.matrixRg).isWithin(0.0001f).of(0.44381f)
        assertThat(matrix.matrixGg).isWithin(0.0001f).of(0.94381f)
    }

    @Test fun `perceived luminance reduction treats CVD white point as white`() {
        val prefs = Preferences(
            activePresetKey = "deutan",
            presetIntensity = 1f,
            dim = 0f,
            contrast = 1f
        )

        val reduction = MatrixPreview.perceivedLuminanceReduction(prefs)

        assertThat(reduction).isWithin(0.0001f).of(0f)
    }
}
