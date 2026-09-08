package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmbientLightGateTest {

    @Test fun `reading below threshold engages the gate`() {
        val gate = AmbientLightGate()

        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 9.9f)).isTrue()
    }

    @Test fun `readings in hysteresis band retain engaged state`() {
        val gate = AmbientLightGate()
        gate.update(enabled = true, thresholdLux = 10f, lux = 5f)

        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 10.5f)).isTrue()
        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 10.9f)).isTrue()
    }

    @Test fun `reading above disengage threshold clears the gate exactly once`() {
        val gate = AmbientLightGate()
        gate.update(enabled = true, thresholdLux = 10f, lux = 5f)

        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 11f)).isFalse()
        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 10.5f)).isFalse()
    }

    @Test fun `disabled or invalid readings clear stale activation`() {
        val gate = AmbientLightGate()
        gate.update(enabled = true, thresholdLux = 10f, lux = 5f)

        assertThat(gate.update(enabled = false, thresholdLux = 10f, lux = 5f)).isFalse()
        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 5f)).isTrue()
        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = -1f)).isFalse()
    }

    @Test fun `changing threshold starts a fresh decision`() {
        val gate = AmbientLightGate()
        gate.update(enabled = true, thresholdLux = 10f, lux = 5f)

        assertThat(gate.update(enabled = true, thresholdLux = 2f, lux = 5f)).isFalse()
        assertThat(gate.update(enabled = true, thresholdLux = 10f, lux = 5f)).isTrue()
    }
}
