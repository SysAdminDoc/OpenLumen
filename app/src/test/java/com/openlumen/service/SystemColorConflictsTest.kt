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

    private val grayOn = SystemColorState(
        readingModeActive = 1,
        correctionEnabled = 1,
        correctionMode = SystemColorSettingsWatcher.CORRECTION_MODE_GRAYSCALE
    )

    @Test fun `the overlay names the system gray that takes its tint away`() {
        // #32. The overlay is part of the frame the system grays, so its tint
        // goes gray too, and the only fix is switching the system gray off.
        assertThat(systemColorConflicts(EngineKind.OVERLAY, grayOn)).containsExactly(
            SystemColorConflict.READING_MODE_ON,
            SystemColorConflict.GRAYSCALE_CORRECTION_ON
        )
    }

    @Test fun `a colour correction that is not grayscale leaves the overlay tint alone`() {
        val deuteranomaly = SystemColorState(correctionEnabled = 1, correctionMode = 12)
        assertThat(systemColorConflicts(EngineKind.OVERLAY, deuteranomaly)).isEmpty()
        assertThat(deuteranomaly.systemGrayscale).isFalse()
    }

    @Test fun `the drivers that keep the tint over the system gray report nothing for it`() {
        // SurfaceFlinger composes the gray into its own matrix, and the
        // secure-settings and KCAL drivers combine with it by themselves.
        for (kind in listOf(EngineKind.SURFACE_FLINGER, EngineKind.COLOR_DISPLAY_MANAGER, EngineKind.KCAL)) {
            assertThat(systemColorConflicts(kind, grayOn)).isEmpty()
        }
    }

    @Test fun `either system gray counts as the screen being gray`() {
        assertThat(SystemColorState(readingModeActive = 1).systemGrayscale).isTrue()
        assertThat(
            SystemColorState(
                correctionEnabled = 1,
                correctionMode = SystemColorSettingsWatcher.CORRECTION_MODE_GRAYSCALE
            ).systemGrayscale
        ).isTrue()
        // Grayscale selected but correction switched off draws nothing.
        assertThat(
            SystemColorState(
                correctionEnabled = 0,
                correctionMode = SystemColorSettingsWatcher.CORRECTION_MODE_GRAYSCALE
            ).systemGrayscale
        ).isFalse()
        assertThat(SystemColorState(readingModeActive = 0).systemGrayscale).isFalse()
        assertThat(SystemColorState().systemGrayscale).isFalse()
    }
}
