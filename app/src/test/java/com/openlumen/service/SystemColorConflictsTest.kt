package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.EngineKind
import org.junit.Test

class SystemColorConflictsTest {

    private val everythingOn = SystemColorState(
        nightLightActivated = 1,
        nightLightAutoMode = 2,
        extraDimActivated = 1,
        inversionEnabled = 1
    )

    @Test fun `only the SurfaceFlinger driver shares the system matrix`() {
        for (kind in EngineKind.entries.filter { it != EngineKind.SURFACE_FLINGER }) {
            assertThat(systemColorConflicts(kind, everythingOn)).isEmpty()
        }
        assertThat(systemColorConflicts(null, everythingOn)).isEmpty()
    }

    @Test fun `every active feature is reported`() {
        assertThat(systemColorConflicts(EngineKind.SURFACE_FLINGER, everythingOn)).containsExactly(
            SystemColorConflict.NIGHT_LIGHT_ON,
            SystemColorConflict.EXTRA_DIM_ON,
            SystemColorConflict.COLOR_INVERSION_ON
        )
    }

    @Test fun `a Night Light schedule is reported while Night Light is off`() {
        for (autoMode in listOf(1, 2)) {
            val state = SystemColorState(nightLightActivated = 0, nightLightAutoMode = autoMode)
            assertThat(systemColorConflicts(EngineKind.SURFACE_FLINGER, state))
                .containsExactly(SystemColorConflict.NIGHT_LIGHT_SCHEDULED)
        }
    }

    @Test fun `everything off is no conflict`() {
        val state = SystemColorState(
            nightLightActivated = 0,
            nightLightAutoMode = 0,
            extraDimActivated = 0,
            inversionEnabled = 0
        )
        assertThat(systemColorConflicts(EngineKind.SURFACE_FLINGER, state)).isEmpty()
    }

    @Test fun `an unreadable row is not reported as a conflict`() {
        assertThat(systemColorConflicts(EngineKind.SURFACE_FLINGER, SystemColorState())).isEmpty()
    }
}
