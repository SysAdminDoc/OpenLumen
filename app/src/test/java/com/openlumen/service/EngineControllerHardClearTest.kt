package com.openlumen.service

import android.content.Context
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.PresetKeyResolver
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.prefs.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C291. `night_display_activated`, `reduce_bright_colors_activated` and
 * `accessibility_display_daltonizer_enabled` are persistent system settings.
 * The user may have had any of them on before OpenLumen was installed, and the
 * blunt reset cannot tell whose they are, so only the emergency paths may run
 * it. An ordinary disable used to run it too, which meant switching the filter
 * off took the user's own Night Light, Extra Dim and colour correction with it
 * on every driver, including the rootless overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineControllerHardClearTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver

    @Before fun grantSecureSettingsAndSeedUserRows() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        for (key in USER_OWNED_ROWS) {
            Settings.Secure.putInt(resolver, key, 1)
        }
    }

    private fun controller(): EngineController = EngineController(
        context = context,
        // No overlay engine registered, so the overlay branch of the hard clear
        // is a no-op and the secure rows are the only thing under test.
        probe = DriverProbe(engines = listOf(NeverAvailableEngine())),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = CoroutineScope(SupervisorJob()),
        isUserUnlocked = { true },
        logTag = "test"
    )

    private fun rows(): List<Int> =
        USER_OWNED_ROWS.map { Settings.Secure.getInt(resolver, it, 0) }

    @Test fun `an ordinary disable leaves the user's own secure settings alone`() = runBlocking {
        controller().hardClearOutputs("filter disabled")

        assertThat(rows()).containsExactly(1, 1, 1).inOrder()
    }

    @Test fun `shutdown leaves the user's own secure settings alone`() = runBlocking {
        controller().clearRootTransformsForShutdown()

        assertThat(rows()).containsExactly(1, 1, 1).inOrder()
    }

    @Test fun `an explicit turn-off still switches every known transform off`() = runBlocking {
        // The escape hatch has to work when nothing in this process owns the
        // rows any more, which is the whole reason the blunt reset exists.
        controller().hardClearOutputs("turn off from intent", blunt = true)

        assertThat(rows()).containsExactly(0, 0, 0).inOrder()
    }

    private companion object {
        val USER_OWNED_ROWS = listOf(
            SecureSettingsEngine.KEY_NIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_CORRECTION_ENABLED
        )
    }
}

/** Stands in for the driver registry without letting anything reach a display. */
private class NeverAvailableEngine : ColorEngine {
    override val kind = EngineKind.SURFACE_FLINGER
    override val capabilities: Set<EngineCapability> = emptySet()
    override suspend fun isAvailable(context: Context) = false
    override suspend fun apply(context: Context, matrix: LumenMatrix) = EngineResult.Success
    override suspend fun clear(context: Context) = EngineResult.Success
}
