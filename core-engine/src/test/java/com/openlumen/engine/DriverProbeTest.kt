package com.openlumen.engine

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DriverProbeTest {

    @Test fun `auto picks highest-rank available root engine before rootless engines`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            probe.pickBestFrom(
                probes(
                    engines,
                    EngineKind.COLOR_DISPLAY_MANAGER to true,
                    EngineKind.SURFACE_FLINGER to true,
                    EngineKind.KCAL to true,
                    EngineKind.OVERLAY to true
                )
            ).kind
        ).isEqualTo(EngineKind.SURFACE_FLINGER)
    }

    @Test fun `auto falls back to CDM when no root engine is available but CDM is`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            probe.pickBestFrom(
                probes(
                    engines,
                    EngineKind.COLOR_DISPLAY_MANAGER to true,
                    EngineKind.SURFACE_FLINGER to false,
                    EngineKind.KCAL to false,
                    EngineKind.OVERLAY to true
                )
            ).kind
        ).isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `auto falls back to overlay when neither root nor CDM is available`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            probe.pickBestFrom(
                probes(
                    engines,
                    EngineKind.COLOR_DISPLAY_MANAGER to false,
                    EngineKind.SURFACE_FLINGER to false,
                    EngineKind.KCAL to false,
                    EngineKind.OVERLAY to true
                )
            ).kind
        ).isEqualTo(EngineKind.OVERLAY)
    }

    private fun engines(): List<ColorEngine> = EngineKind.entries.map { FakeEngine(it) }

    private fun probes(
        engines: List<ColorEngine>,
        vararg availability: Pair<EngineKind, Boolean>
    ): List<DriverProbe.Probe> {
        val availabilityByKind = availability.toMap()
        return engines
            .map { DriverProbe.Probe(it, availabilityByKind.getValue(it.kind)) }
            .sortedByDescending { it.engine.kind.rank }
    }

    private class FakeEngine(
        override val kind: EngineKind,
        private val available: Boolean = false
    ) : ColorEngine {
        override suspend fun isAvailable(context: Context): Boolean = available
        override suspend fun apply(context: Context, matrix: LumenMatrix) = Unit
        override suspend fun clear(context: Context) = Unit
    }
}
