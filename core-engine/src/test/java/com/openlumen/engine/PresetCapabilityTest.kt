package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine
import org.junit.Test

/**
 * C282. Presets are written against the compositor, which takes the whole
 * transform. Every other driver approximates something, and until now it did so
 * silently: the grayscale preset came out as a flat 45% darkening on the root
 * drivers and as nothing at all on the rootless one.
 */
class PresetCapabilityTest {

    @Test fun `grayscale actually desaturates`() {
        // Rec. 709 luminance in every row. Feeding pure red through it has to
        // give the same value on all three outputs, which is what makes it grey
        // rather than a dimmer red.
        val m = Presets.GRAY.surfaceRgbMatrix()
        val redOut = floatArrayOf(m[0], m[3], m[6])

        assertThat(redOut[0]).isWithin(1e-4f).of(0.2126f)
        assertThat(redOut[1]).isWithin(1e-4f).of(redOut[0])
        assertThat(redOut[2]).isWithin(1e-4f).of(redOut[0])

        // Each row sums to 1, so white stays white instead of being darkened.
        for (row in 0 until 3) {
            val sum = m[row * 3] + m[row * 3 + 1] + m[row * 3 + 2]
            assertThat(sum).isWithin(1e-4f).of(1f)
        }
    }

    @Test fun `every preset that mixes channels names a system correction mode`() {
        // The rootless driver cannot take a matrix, so a preset that needs one
        // has to say which of AOSP's own modes stands in for it. Without this
        // the preset silently becomes a no-op there.
        val mixing = Presets.ALL.filter {
            EngineCapability.COLOR_MATRIX in it.matrix.requiredCapabilities()
        }

        assertThat(mixing.map { it.key })
            .containsExactly("gray", "protan", "deutan", "tritan")
        for (entry in mixing) {
            assertThat(entry.matrix.daltonizer).isNotEqualTo(Daltonizer.NONE)
        }
    }

    @Test fun `the correction modes match AOSP's own values`() {
        // AccessibilityManager.DALTONIZER_*: monochromacy is 0, and Settings'
        // daltonizer_type_values lists 12, 11, 13, 0 for deuter/prot/trit/mono.
        assertThat(Presets.GRAY.daltonizer.secureValue).isEqualTo(0)
        assertThat(Presets.PROTAN.daltonizer.secureValue).isEqualTo(11)
        assertThat(Presets.DEUTAN.daltonizer.secureValue).isEqualTo(12)
        assertThat(Presets.TRITAN.daltonizer.secureValue).isEqualTo(13)
        assertThat(Daltonizer.NONE.secureValue).isEqualTo(-1)
    }

    @Test fun `a warm preset needs nothing beyond a channel scale`() {
        // Night, Amber and the rest are pure per-channel scales, so they render
        // the same on every driver and must not be flagged as approximated.
        for (key in listOf("night", "amber", "red", "salmon", "sepia")) {
            val matrix = Presets.byKey(key)!!.matrix
            assertThat(matrix.requiredCapabilities()).isEmpty()
            assertThat(matrix.unsupportedBy(OverlayEngine().capabilities)).isEmpty()
        }
    }

    @Test fun `the compositor honours every preset`() {
        val sf = SurfaceFlingerEngine().capabilities
        for (entry in Presets.ALL) {
            assertThat(entry.matrix.unsupportedBy(sf)).isEmpty()
        }
    }

    @Test fun `scalar drivers report losing the channel mixing they cannot do`() {
        val kcal = KcalEngine().capabilities
        for (key in listOf("gray", "protan", "deutan", "tritan")) {
            assertThat(Presets.byKey(key)!!.matrix.unsupportedBy(kcal))
                .containsExactly(EngineCapability.COLOR_MATRIX)
        }
    }

    @Test fun `the rootless driver covers channel mixing with the system's own correction`() {
        // It cannot take the matrix, but it can select the equivalent AOSP mode,
        // so the preset is honoured rather than approximated.
        val secure = SecureSettingsEngine().capabilities
        for (key in listOf("gray", "protan", "deutan", "tritan")) {
            assertThat(Presets.byKey(key)!!.matrix.unsupportedBy(secure)).isEmpty()
        }
    }

    @Test fun `a hand-built cross-channel matrix with no named mode stays unsupported`() {
        // Cover the other side of the substitution: a custom matrix has no AOSP
        // mode to stand in for it, so the rootless driver must still say so.
        val custom = LumenMatrix(
            hasColorMatrix = true,
            matrixRr = 0.5f,
            matrixRg = 0.5f
        )

        assertThat(custom.unsupportedBy(SecureSettingsEngine().capabilities))
            .containsExactly(EngineCapability.COLOR_MATRIX)
    }

    @Test fun `zero intensity drops the correction mode`() {
        // withIntensity(0) is the identity transform. Leaving the mode set
        // would keep the system correction on with the filter dialled to
        // nothing.
        assertThat(Presets.PROTAN.withIntensity(0f).daltonizer).isEqualTo(Daltonizer.NONE)
        assertThat(Presets.PROTAN.withIntensity(1f).daltonizer)
            .isEqualTo(Daltonizer.PROTANOMALY)
        assertThat(Presets.PROTAN.withIntensity(0f).requiredCapabilities()).isEmpty()
    }

    @Test fun `dim is the only thing a plain dimming preset needs`() {
        assertThat(Presets.DEEP.requiredCapabilities())
            .containsExactly(EngineCapability.SUB_MINIMUM_DIM)
    }
}
