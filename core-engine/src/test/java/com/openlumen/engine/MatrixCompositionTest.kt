package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * C274. The dim composition and the contrast scaling lived in the app module,
 * in a class named for previews, while the service was the main caller. They
 * are matrix arithmetic and they belong with the matrix, so these are their
 * tests in their new home. The expected values are worked out from the rule,
 * not read off the implementation.
 */
class MatrixCompositionTest {

    private val eps = 1e-5f

    @Test fun `either dim alone passes straight through`() {
        assertThat(composeDim(0f, 0.4f)).isWithin(eps).of(0.4f)
        assertThat(composeDim(0.4f, 0f)).isWithin(eps).of(0.4f)
        assertThat(composeDim(0f, 0f)).isWithin(eps).of(0f)
    }

    @Test fun `two dims multiply what is left rather than adding what is taken`() {
        // Half the light, then half of what remains, is three quarters gone.
        // Adding would give exactly one, a black screen, which is not what a
        // user reading "50 percent preset plus 50 percent slider" expects.
        assertThat(composeDim(0.5f, 0.5f)).isWithin(eps).of(0.75f)
        assertThat(composeDim(0.2f, 0.3f)).isWithin(eps).of(0.44f)
    }

    @Test fun `composition never exceeds what one control could reach`() {
        // The overlay driver caps at this, and so does the slider, so no pair
        // of inputs may compose past it.
        assertThat(composeDim(0.95f, 0.95f)).isWithin(eps).of(0.95f)
        assertThat(composeDim(5f, 5f)).isWithin(eps).of(0.95f)
        assertThat(composeDim(-1f, -1f)).isWithin(eps).of(0f)
    }

    @Test fun `contrast of one changes nothing at all`() {
        val matrix = Presets.NIGHT

        assertThat(matrix.withContrast(1f)).isEqualTo(matrix)
    }

    @Test fun `raising contrast scales the channels and pulls the midpoint back`() {
        val flat = LumenMatrix(r = 1f, g = 1f, b = 1f)

        val raised = flat.withContrast(1.2f)

        assertThat(raised.r).isWithin(eps).of(1.2f)
        // Without the bias the whole image would brighten instead of gaining
        // contrast, so it has to move the other way by half the change.
        assertThat(raised.biasR).isWithin(eps).of(-0.1f)
        assertThat(raised.biasG).isWithin(eps).of(-0.1f)
        assertThat(raised.biasB).isWithin(eps).of(-0.1f)
    }

    @Test fun `lowering contrast lifts the midpoint`() {
        val flat = LumenMatrix(r = 1f, g = 1f, b = 1f)

        val lowered = flat.withContrast(0.6f)

        assertThat(lowered.r).isWithin(eps).of(0.6f)
        assertThat(lowered.biasR).isWithin(eps).of(0.2f)
    }

    @Test fun `a cross-channel matrix keeps its shape through contrast`() {
        val mixed = LumenMatrix(
            hasColorMatrix = true,
            matrixRr = 0.5f,
            matrixRg = 0.25f,
            matrixGg = 0.8f
        )

        val raised = mixed.withContrast(2f)

        assertThat(raised.matrixRr).isWithin(eps).of(1f)
        assertThat(raised.matrixRg).isWithin(eps).of(0.5f)
        assertThat(raised.matrixGg).isWithin(eps).of(1.6f)
    }

    @Test fun `a scalar matrix gains no cross-channel terms`() {
        // Positive control for the case above: contrast must not turn a
        // diagonal matrix into a mixing one.
        val flat = LumenMatrix(r = 1f, g = 1f, b = 1f)

        val raised = flat.withContrast(2f)

        assertThat(raised.hasColorMatrix).isFalse()
        assertThat(raised.matrixRg).isWithin(eps).of(flat.matrixRg)
    }
}
