package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.viewmodel.shouldRevertPinnedEngineToAuto
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

    @Test fun `a probe that says unavailable sends an unforced pin back to Auto`() {
        assertThat(
            shouldRevertPinnedEngineToAuto(
                selected = EngineKindDto.SurfaceFlinger,
                forcePinned = false,
                probeSaysAvailable = false
            )
        ).isTrue()
    }

    @Test fun `a forced pin survives the probe that runs on every app launch`() {
        // C292. `refreshProbes` runs from the ViewModel's init block, so this
        // decision is taken every time the app opens. Reverting here undid the
        // user's selection and hid the override switch with it, because the
        // Driver tab only offers that switch while a driver is pinned.
        assertThat(
            shouldRevertPinnedEngineToAuto(
                selected = EngineKindDto.SurfaceFlinger,
                forcePinned = true,
                probeSaysAvailable = false
            )
        ).isFalse()
    }

    @Test fun `an available pin and Auto itself are never reverted`() {
        assertThat(
            shouldRevertPinnedEngineToAuto(
                selected = EngineKindDto.Kcal,
                forcePinned = false,
                probeSaysAvailable = true
            )
        ).isFalse()
        assertThat(
            shouldRevertPinnedEngineToAuto(
                selected = EngineKindDto.Auto,
                forcePinned = false,
                probeSaysAvailable = false
            )
        ).isFalse()
    }

    @Test fun `the UI revert and the service resolution answer the same question`() {
        // The defect was two code paths disagreeing about one rule, so pin the
        // agreement rather than the two answers separately.
        for (forced in listOf(false, true)) {
            for (available in listOf(false, true)) {
                assertThat(
                    shouldRevertPinnedEngineToAuto(
                        selected = EngineKindDto.SurfaceFlinger,
                        forcePinned = forced,
                        probeSaysAvailable = available
                    )
                ).isEqualTo(
                    !EngineController.honourPinnedEngine(
                        forcePinned = forced,
                        probeSaysAvailable = available
                    )
                )
            }
        }
    }
}
