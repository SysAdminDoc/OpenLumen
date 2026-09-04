package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.Daltonizer
import org.junit.Test

/**
 * The custom RGB state reads only what the custom controls set.
 *
 * A saved profile or an imported backup round-trips whatever its snapshot
 * held, so the persisted matrix can carry a dim, a bias or a correction mode
 * that the custom screen never offered. Reading those back would change what
 * the screen renders for that profile, which is the kind of silent behaviour
 * change a refactor is not allowed to make.
 */
class CustomMatrixBaseTest {

    private val loaded = Preferences(
        activePresetKey = "custom",
        presetIntensity = 1f,
        dim = 0f,
        contrast = 1f,
        customMatrix = MatrixDto(
            r = 1f,
            g = 0.7f,
            b = 0.4f,
            dim = 0.5f,
            biasR = 0.2f,
            daltonizer = "MONOCHROMACY"
        )
    )

    @Test fun `a dim stored in the snapshot does not darken the screen`() {
        // The dim control is its own preference and composes with the
        // preset's. Reading a second one out of the matrix would dim twice.
        assertThat(loaded.effectiveMatrix().dim).isEqualTo(0f)
    }

    @Test fun `a bias stored in the snapshot is not applied`() {
        assertThat(loaded.effectiveMatrix().biasR).isEqualTo(0f)
    }

    @Test fun `a correction mode stored in the snapshot is not selected`() {
        // Correction modes belong to the named presets. A custom RGB state
        // that claimed one would make the rootless driver render grayscale
        // for a user who set three channel sliders.
        assertThat(loaded.effectiveMatrix().daltonizer).isEqualTo(Daltonizer.NONE)
    }

    @Test fun `the channel scales and the matrix do come through`() {
        // Positive control: the fields the custom screen does set have to
        // survive, or this is not reading the custom state at all.
        val matrix = loaded.effectiveMatrix()

        // Within a tolerance: withIntensity(1f) is a lerp, not an identity,
        // so the values come back a float epsilon away from what went in.
        assertThat(matrix.r).isWithin(1e-5f).of(1f)
        assertThat(matrix.g).isWithin(1e-5f).of(0.7f)
        assertThat(matrix.b).isWithin(1e-5f).of(0.4f)
    }

    @Test fun `the full mapping still carries everything, for the direct-boot mirror`() {
        // toMatrix is a different job: it round-trips a whole matrix through
        // storage, so it has to keep the fields this one drops.
        val full = loaded.customMatrix.toMatrix()

        assertThat(full.dim).isEqualTo(0.5f)
        assertThat(full.biasR).isEqualTo(0.2f)
        assertThat(full.daltonizer).isEqualTo(Daltonizer.MONOCHROMACY)
    }
}
