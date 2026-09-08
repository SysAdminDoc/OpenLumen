package com.openlumen.service

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.DisplayEmergencyReset
import com.openlumen.engine.EngineKind
import com.openlumen.engine.engines.SecureSettingsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C341. A driver whose `clear()` failed may have left a transform on the
 * display, so the escalation exists. It has to be scoped to the family that
 * driver could actually have written: zeroing the secure rows because a root
 * driver's `su` call was denied destroys settings that driver never touched,
 * and picking a different driver on the Driver tab is a routine action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EscalationScopeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver

    @Before fun grantSecureSettingsAndSeedUserRows() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        for (key in USER_OWNED_ROWS) {
            Settings.Secure.putInt(resolver, key, 1)
        }
    }

    private fun rows(): List<Int> =
        USER_OWNED_ROWS.map { Settings.Secure.getInt(resolver, it, 0) }

    @Test fun `only the secure driver's failure clears the secure rows`() {
        assertThat(EngineController.clearsSecureRows(EngineKind.COLOR_DISPLAY_MANAGER)).isTrue()
        for (kind in listOf(EngineKind.SURFACE_FLINGER, EngineKind.KCAL, EngineKind.OVERLAY)) {
            assertThat(EngineController.clearsSecureRows(kind)).isFalse()
        }
    }

    @Test fun `only a root driver's failure clears the root transforms`() {
        for (kind in listOf(EngineKind.SURFACE_FLINGER, EngineKind.KCAL)) {
            assertThat(EngineController.clearsRootTransforms(kind)).isTrue()
        }
        for (kind in listOf(EngineKind.COLOR_DISPLAY_MANAGER, EngineKind.OVERLAY)) {
            assertThat(EngineController.clearsRootTransforms(kind)).isFalse()
        }
    }

    @Test fun `the overlay driver has no persistent state to escalate to`() {
        // Its clear() removes a window; there is nothing for a blunt system
        // write to undo, and running one would only destroy other drivers' work.
        assertThat(EngineController.escalatesToBluntReset(EngineKind.OVERLAY)).isFalse()
        for (kind in listOf(
            EngineKind.COLOR_DISPLAY_MANAGER,
            EngineKind.SURFACE_FLINGER,
            EngineKind.KCAL
        )) {
            assertThat(EngineController.escalatesToBluntReset(kind)).isTrue()
        }
    }

    @Test fun `a root-scoped reset leaves the secure rows alone`() = runBlocking {
        DisplayEmergencyReset.clearRootTransforms(context = null, roots = true)

        assertThat(rows()).containsExactly(1, 1, 1).inOrder()
    }

    @Test fun `a secure-scoped reset still clears the secure rows`() = runBlocking {
        val result = DisplayEmergencyReset.clearRootTransforms(context = context, roots = false)

        assertThat(rows()).containsExactly(0, 0, 0).inOrder()
        assertThat(result.surfaceFlingerCodes).isEmpty()
        assertThat(result.kcalPaths).isEmpty()
    }

    private companion object {
        val USER_OWNED_ROWS = listOf(
            SecureSettingsEngine.KEY_NIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_CORRECTION_ENABLED
        )
    }
}
