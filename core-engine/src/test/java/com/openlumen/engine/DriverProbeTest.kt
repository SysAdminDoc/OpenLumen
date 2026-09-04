package com.openlumen.engine

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DriverProbeTest {

    @Test fun `auto picks highest-rank available root engine before rootless engines`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            checkNotNull(probe.pickBestFrom(
               probes(
                   engines,
                   EngineKind.COLOR_DISPLAY_MANAGER to true,
                   EngineKind.SURFACE_FLINGER to true,
                   EngineKind.KCAL to true,
                   EngineKind.OVERLAY to true
               )
            )).kind
        ).isEqualTo(EngineKind.SURFACE_FLINGER)
    }

    @Test fun `auto falls back to CDM when no root engine is available but CDM is`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            checkNotNull(probe.pickBestFrom(
               probes(
                   engines,
                   EngineKind.COLOR_DISPLAY_MANAGER to true,
                   EngineKind.SURFACE_FLINGER to false,
                   EngineKind.KCAL to false,
                   EngineKind.OVERLAY to true
               )
            )).kind
        ).isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `auto falls back to overlay when neither root nor CDM is available`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            checkNotNull(probe.pickBestFrom(
                probes(
                    engines,
                    EngineKind.COLOR_DISPLAY_MANAGER to false,
                    EngineKind.SURFACE_FLINGER to false,
                    EngineKind.KCAL to false,
                    EngineKind.OVERLAY to true
                )
            )).kind
        ).isEqualTo(EngineKind.OVERLAY)
    }

    @Test fun `auto returns no engine when every probe is unavailable`() {
        val engines = engines()
        val probe = DriverProbe(engines)

        assertThat(
            probe.pickBestFrom(
                probes(
                    engines,
                    EngineKind.COLOR_DISPLAY_MANAGER to false,
                    EngineKind.SURFACE_FLINGER to false,
                    EngineKind.KCAL to false,
                    EngineKind.OVERLAY to false
                )
            )
        ).isNull()
    }

    @Test fun `shared auto kind resolver includes CDM before overlay`() {
        val engines = engines()
        val kind = DriverProbe.bestAvailableKind(
            probes(
                engines,
                EngineKind.COLOR_DISPLAY_MANAGER to true,
                EngineKind.SURFACE_FLINGER to false,
                EngineKind.KCAL to false,
                EngineKind.OVERLAY to true
            )
        )

        assertThat(kind).isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
    }

    @Test fun `a pin the device cannot honour does not describe what the preset loses`() {
        // C300. An unavailable pin does not run, so reading its capabilities
        // warned about losses the user would never see and hid the ones they
        // would. The Presets detail now describes whatever Auto falls back to.
        val engines = listOf(
            FakeEngine(EngineKind.KCAL, capabilities = setOf(EngineCapability.PER_CHANNEL_GAMMA)),
            FakeEngine(EngineKind.OVERLAY, capabilities = setOf(EngineCapability.COLOR_MATRIX))
        )
        val probes = listOf(
            DriverProbe.Probe(engines[0], available = false),
            DriverProbe.Probe(engines[1], available = true)
        )

        assertThat(DriverProbe.activeCapabilities(probes, pinned = EngineKind.KCAL))
            .containsExactly(EngineCapability.COLOR_MATRIX)
    }

    @Test fun `a forced pin describes the driver that will actually run`() {
        // Forcing exists because su detection is unreliable on root-hiding
        // setups: the service runs the pinned driver anyway and reports a real
        // failure rather than moving the user to a weaker one. Skipping an
        // unavailable pin here without asking about the override made the
        // Presets detail describe a driver the service was not going to use.
        val engines = listOf(
            FakeEngine(EngineKind.KCAL, capabilities = setOf(EngineCapability.PER_CHANNEL_GAMMA)),
            FakeEngine(EngineKind.OVERLAY, capabilities = setOf(EngineCapability.COLOR_MATRIX))
        )
        val probes = listOf(
            DriverProbe.Probe(engines[0], available = false),
            DriverProbe.Probe(engines[1], available = true)
        )

        assertThat(
            DriverProbe.activeCapabilities(
                probes = probes,
                pinned = EngineKind.KCAL,
                forcePinned = true
            )
        ).containsExactly(EngineCapability.PER_CHANNEL_GAMMA)
    }

    @Test fun `a pin the device can honour is still the one described`() {
        // Positive control: the fallback above has to come from availability,
        // not from the pin being ignored outright.
        val engines = listOf(
            FakeEngine(EngineKind.KCAL, capabilities = setOf(EngineCapability.PER_CHANNEL_GAMMA)),
            FakeEngine(EngineKind.OVERLAY, capabilities = setOf(EngineCapability.COLOR_MATRIX))
        )
        val probes = listOf(
            DriverProbe.Probe(engines[0], available = true),
            DriverProbe.Probe(engines[1], available = true)
        )

        assertThat(DriverProbe.activeCapabilities(probes, pinned = EngineKind.KCAL))
            .containsExactly(EngineCapability.PER_CHANNEL_GAMMA)
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
        private val available: Boolean = false,
        override val capabilities: Set<EngineCapability> = emptySet()
   ) : ColorEngine {
       override suspend fun isAvailable(context: Context): Boolean = available
        override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult = EngineResult.Success
        override suspend fun clear(context: Context): EngineResult = EngineResult.Success
   }
}
