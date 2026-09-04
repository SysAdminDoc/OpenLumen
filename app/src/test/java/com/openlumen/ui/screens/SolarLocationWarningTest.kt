package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

/**
 * C303. The warning card keyed off the location alone, so every mode showed a
 * warning about the one mode that needs one. A fresh install opened the
 * Schedule tab on it.
 */
class SolarLocationWarningTest {

    @Test fun `a mode that needs no location does not warn about one`() {
        for (mode in ScheduleModeDto.entries.filter { it != ScheduleModeDto.Solar }) {
            assertThat(shouldWarnAboutSolarLocation(mode, locationValid = false)).isFalse()
        }
    }

    @Test fun `the solar mode still warns when the location is missing`() {
        assertThat(shouldWarnAboutSolarLocation(ScheduleModeDto.Solar, locationValid = false))
            .isTrue()
    }

    @Test fun `the solar mode with a location does not warn`() {
        // Positive control for the two above: the mode alone must not decide
        // it, or the card would sit under a working solar schedule forever.
        assertThat(shouldWarnAboutSolarLocation(ScheduleModeDto.Solar, locationValid = true))
            .isFalse()
    }
}
