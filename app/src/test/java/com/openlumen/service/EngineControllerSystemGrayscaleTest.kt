package com.openlumen.service

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.openlumen.PresetKeyResolver
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.withGrayscale
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * #32. LineageOS reading mode and the system grayscale correction live in the
 * same SurfaceFlinger matrix the root driver writes, so the driver used to
 * wipe the gray on every apply, and wipe it again on turn-off. The driver now
 * carries the system's gray in what it writes and leaves it behind when it
 * lets go. The other drivers never touch that matrix and get the target as is.
 */
@RunWith(RobolectricTestRunner::class)
class EngineControllerSystemGrayscaleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var systemGray = true

    private fun controllerFor(engine: ColorEngine) = EngineController(
        context = context,
        probe = DriverProbe(engines = listOf(engine)),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test",
        systemGrayscale = { systemGray }
    )

    private fun prefsFor(kind: EngineKind) = Preferences(
        engine = when (kind) {
            EngineKind.SURFACE_FLINGER -> EngineKindDto.SurfaceFlinger
            EngineKind.OVERLAY -> EngineKindDto.Overlay
            EngineKind.KCAL -> EngineKindDto.Kcal
            EngineKind.COLOR_DISPLAY_MANAGER -> EngineKindDto.ColorDisplayManager
        }
    )

    private val systemGrayOnly = LumenMatrix.IDENTITY.withGrayscale(1f)

    @Test fun `SurfaceFlinger keeps the system gray under the filter`() {
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
        }

        assertThat(sf.applied.last()).isEqualTo(Presets.AMBER.withGrayscale(1f))
    }

    @Test fun `without a system gray SurfaceFlinger writes the target as is`() {
        systemGray = false
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
        }

        assertThat(sf.applied.last()).isEqualTo(Presets.AMBER)
    }

    @Test fun `the drivers outside that matrix get the target as is`() {
        for (kind in listOf(EngineKind.OVERLAY, EngineKind.KCAL)) {
            val engine = GrayRecordingEngine(kind)
            val controller = controllerFor(engine)

            runBlocking {
                controller.ensureEngineFor(prefsFor(kind))
                controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            }

            assertThat(engine.applied.last()).isEqualTo(Presets.AMBER)
        }
    }

    @Test fun `turning reading mode on is picked up by the next reassert`() {
        systemGray = false
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        val reasserted = runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            systemGray = true
            controller.reassertAfterSystemOverwrite()
        }

        assertThat(reasserted).isTrue()
        assertThat(sf.applied.last()).isEqualTo(Presets.AMBER.withGrayscale(1f))
    }

    @Test fun `an ordinary turn-off leaves the system gray on screen`() {
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            controller.hardClearOutputs("filter disabled")
        }

        assertThat(sf.calls.takeLast(2)).containsExactly("clear", "apply").inOrder()
        assertThat(sf.applied.last()).isEqualTo(systemGrayOnly)
    }

    @Test fun `an emergency reset clears the gray along with everything else`() {
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            controller.hardClearOutputs("turn off from intent", blunt = true)
        }

        assertThat(sf.calls.last()).isEqualTo("clear")
    }

    @Test fun `shutdown hands the gray back instead of clearing to identity`() {
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            controller.clearActiveEngineForShutdown()
        }

        assertThat(sf.calls).doesNotContain("clear")
        assertThat(sf.applied.last()).isEqualTo(systemGrayOnly)
    }

    @Test fun `with no system gray shutdown clears as before`() {
        systemGray = false
        val sf = GrayRecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(sf)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKind.SURFACE_FLINGER))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
            controller.clearActiveEngineForShutdown()
        }

        assertThat(sf.calls.last()).isEqualTo("clear")
    }
}

private class GrayRecordingEngine(override val kind: EngineKind) : ColorEngine {
    override val capabilities: Set<EngineCapability> = emptySet()
    val calls = CopyOnWriteArrayList<String>()
    val applied = CopyOnWriteArrayList<LumenMatrix>()

    override suspend fun isAvailable(context: Context) = true

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        calls += "apply"
        applied += matrix
        return EngineResult.Success
    }

    override suspend fun clear(context: Context): EngineResult {
        calls += "clear"
        return EngineResult.Success
    }
}
