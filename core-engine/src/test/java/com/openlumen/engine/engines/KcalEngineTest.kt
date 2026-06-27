package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KcalEngineTest {

    @Test fun `KCAL scalar uses standard 0 to 255 range`() {
        assertThat(KcalEngine.toKcalScalar(1f, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(0.5f, 0)).isEqualTo(127)
        assertThat(KcalEngine.toKcalScalar(0f, 0)).isEqualTo(0)
    }

    @Test fun `KCAL scalar honors app-level floor inside standard range`() {
        assertThat(KcalEngine.toKcalScalar(0f, KcalEngine.SAFETY_MIN))
            .isEqualTo(KcalEngine.SAFETY_MIN)
        assertThat(KcalEngine.toKcalScalar(1f, KcalEngine.SAFETY_MIN))
            .isEqualTo(255)
    }

    @Test fun `KCAL scalar clamps non-finite and out-of-range input`() {
        assertThat(KcalEngine.toKcalScalar(Float.NaN, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(Float.POSITIVE_INFINITY, 0)).isEqualTo(255)
        assertThat(KcalEngine.toKcalScalar(-1f, 0)).isEqualTo(0)
        assertThat(KcalEngine.toKcalScalar(2f, 0)).isEqualTo(255)
    }
}
