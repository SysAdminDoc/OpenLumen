package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import org.junit.Test

/**
 * C253 / closed issue #16: a pinned driver must survive a probe that says
 * "unavailable" when the user has explicitly overridden the check.
 */
class ForcePinnedEngineTest {

    @Test fun `an available pinned driver is honoured regardless of the override`() {
        assertThat(
            EngineController.honourPinnedEngine(forcePinned = false, probeSaysAvailable = true)
        ).isTrue()
        assertThat(
            EngineController.honourPinnedEngine(forcePinned = true, probeSaysAvailable = true)
        ).isTrue()
    }

    @Test fun `an unavailable pinned driver falls back to Auto by default`() {
        assertThat(
            EngineController.honourPinnedEngine(forcePinned = false, probeSaysAvailable = false)
        ).isFalse()
    }

    @Test fun `forcing keeps an unavailable pinned driver instead of reverting to Auto`() {
        // Root-hiding setups (Magisk DenyList, Shamiko) make the su probe
        // report no root on a rooted device. Without this the app silently
        // reverts the user's SurfaceFlinger selection every time.
        assertThat(
            EngineController.honourPinnedEngine(forcePinned = true, probeSaysAvailable = false)
        ).isTrue()
    }

    @Test fun `the override is off on a fresh install`() {
        assertThat(Preferences().forcePinnedEngine).isFalse()
    }
}
