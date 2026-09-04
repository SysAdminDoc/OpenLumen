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
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C261. `EngineController` owns every apply, clear, ramp and engine-switch
 * decision in the app and had no test that constructed one. That is why the
 * driver fixes of the last few releases were invisible to a green suite: the
 * engines were exercised through static helpers only, and the object that
 * sequences them was not exercised at all.
 *
 * Every test here uses a block body rather than `= runBlocking { … }`, because
 * a Truth assertion like `containsExactly` returns a value and JUnit rejects a
 * test method that is not void.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineControllerBehaviourTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun controllerFor(vararg engines: ColorEngine) = EngineController(
        context = context,
        probe = DriverProbe(engines = engines.toList()),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test"
    )

    private fun prefsFor(kind: EngineKindDto) = Preferences(engine = kind)

    @Test fun `switching driver clears the old one before the new one applies`() {
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(overlay, surfaceFlinger)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.ensureEngineFor(prefsFor(EngineKindDto.SurfaceFlinger))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
        }

        assertThat(overlay.calls).containsExactly("apply", "clear").inOrder()
        assertThat(surfaceFlinger.calls).containsExactly("apply")
        assertThat(surfaceFlinger.applied.single()).isEqualTo(Presets.AMBER)
    }

    @Test fun `an apply the driver rejected is retried rather than deduplicated`() {
        // The gate must only commit a target the engine actually put on the
        // display, or a transient failure silently becomes the new state.
        val failing = RecordingEngine(EngineKind.OVERLAY) { EngineResult.Failure("no") }
        val controller = controllerFor(failing)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        }

        assertThat(failing.calls).containsExactly("apply", "apply")
    }

    @Test fun `an apply the driver accepted is not repeated for the same target`() {
        val engine = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(engine)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        }

        assertThat(engine.calls).containsExactly("apply")
    }

    @Test fun `a switch to a driver that is not registered keeps the current one`() {
        // probe.engineOf returns null for a kind the registry does not hold.
        // Dropping the working engine there would leave the filter dark.
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(overlay)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.ensureEngineFor(prefsFor(EngineKindDto.Kcal))
            controller.applyIfNeeded(true, Presets.AMBER, transitionDurationMs = 0)
        }

        assertThat(overlay.applied).containsExactly(Presets.NIGHT, Presets.AMBER).inOrder()
    }

    @Test fun `cancelling stops a ramp part way through`() {
        val engine = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(engine)
        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            // Land an accepted target first, so the next call is a state flip
            // with something to interpolate from.
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        }

        scope.launch {
            controller.applyIfNeeded(false, LumenMatrix.IDENTITY, transitionDurationMs = 4_000)
        }

        // Positive control: the ramp really did start before we cancel it, so a
        // flat count afterwards means cancellation rather than a ramp that
        // never ran.
        val deadline = System.currentTimeMillis() + 10_000
        while (engine.calls.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(engine.calls.size).isAtLeast(2)

        controller.cancelJobs()
        val afterCancel = engine.calls.size
        Thread.sleep(2_000)

        assertThat(engine.calls.size).isEqualTo(afterCancel)
    }

    @Test fun `direct boot restore applies the mirrored matrix`() {
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(overlay)

        runBlocking {
            controller.restoreDirectBootState(
                DirectBootState(
                    enabled = true,
                    active = true,
                    engine = EngineKindDto.Overlay,
                    matrix = MatrixDto(r = 1f, g = 0.5f, b = 0.25f),
                    amoledBlackClamp = true
                )
            )
        }

        val applied = overlay.applied.single()
        assertThat(applied.g).isEqualTo(0.5f)
        assertThat(applied.b).isEqualTo(0.25f)
        assertThat(applied.amoledClamp).isTrue()
    }

    @Test fun `direct boot restore never selects a root driver`() {
        // Root drivers need `su`, which cannot be reached before the user has
        // unlocked the device for the first time, so a mirrored root selection
        // has to fall back to something rootless.
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val root = RecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(overlay, root)

        runBlocking {
            controller.restoreDirectBootState(
                DirectBootState(enabled = true, active = true, engine = EngineKindDto.SurfaceFlinger)
            )
        }

        assertThat(root.calls).isEmpty()
        assertThat(overlay.calls).containsExactly("apply")
    }
}

/** Records the order of engine calls so a sequence can be asserted. */
private class RecordingEngine(
    override val kind: EngineKind,
    private val applyResult: () -> EngineResult = { EngineResult.Success }
) : ColorEngine {
    override val capabilities: Set<EngineCapability> = emptySet()
    val calls = CopyOnWriteArrayList<String>()
    val applied = CopyOnWriteArrayList<LumenMatrix>()

    override suspend fun isAvailable(context: Context) = true

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        calls += "apply"
        applied += matrix
        return applyResult()
    }

    override suspend fun clear(context: Context): EngineResult {
        calls += "clear"
        return EngineResult.Success
    }
}
