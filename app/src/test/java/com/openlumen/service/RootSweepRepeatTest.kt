package com.openlumen.service

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.openlumen.PresetKeyResolver
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineCapability
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C325. A single "turn off" tap ran the blunt root reset three times: the
 * command runs it, the `enabled = false` that command writes comes back round
 * the preference collector and runs it again, and `stopSelf` runs it a third
 * time on the way down. `RootSweepCostTest` measures what one pass spends in
 * `su` calls; this measures how many passes a turn-off makes.
 *
 * The sweep is counted through the diagnostics timeline because that is the
 * only place the controller reports it, and `Su`'s counting hook is internal
 * to `core-engine`. A pass that ran reports what it found; a pass that was
 * skipped says so, because "none" would claim it looked and found nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootSweepRepeatTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lines = CopyOnWriteArrayList<String>()

    @Before fun captureTheTimeline() {
        DiagnosticsLog.installTestWriter { lines += it }
    }

    @After fun releaseTheTimeline() {
        DiagnosticsLog.clearTestWriter()
    }

    private fun controller(vararg engines: ColorEngine) = EngineController(
        context = context,
        probe = DriverProbe(engines = engines.toList()),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test"
    )

    private fun resetPasses() = lines.filter { it.contains("hard reset secure=") }
    private fun sweepsRun() = resetPasses().count { !it.contains("SF=already swept") }

    @Test fun `one turn-off sweeps the root transforms once`() = runBlocking {
        val controller = controller()

        // The three calls a turn-off makes, in the order LumenService makes them.
        controller.hardClearOutputs("turn off from intent", blunt = true)
        controller.hardClearOutputs("filter disabled")
        controller.clearRootTransformsForShutdown()

        assertThat(sweepsRun()).isEqualTo(1)
        assertThat(resetPasses()).hasSize(2)
        assertThat(resetPasses().last()).contains("SF=already swept")
    }

    @Test fun `the secure rows are still cleared on a pass that skips the sweep`() = runBlocking {
        // The latch covers the root half only. Ordering between the command and
        // the preference write is not fixed, so whichever blunt pass arrives
        // second still has to do the secure work the first one did not.
        val controller = controller()

        controller.hardClearOutputs("filter disabled")
        controller.hardClearOutputs("turn off from intent", blunt = true)

        val blunt = resetPasses().last()
        assertThat(blunt).contains("SF=already swept")
        // Nothing seeded these rows, so "none" here means the sweep looked.
        assertThat(blunt).contains("secure=none")
    }

    @Test fun `shutdown on its own still sweeps`() = runBlocking {
        // Positive control for the first test: the third call is capable of
        // sweeping, and stays capable when the service is stopped some way
        // that did not go through a hard clear first.
        controller().clearRootTransformsForShutdown()

        assertThat(sweepsRun()).isEqualTo(1)
    }

    @Test fun `an apply arms the sweep again`() = runBlocking {
        val engine = ApplyingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controller(engine)

        controller.hardClearOutputs("turn off from intent", blunt = true)
        controller.ensureEngineFor(Preferences(engine = EngineKindDto.SurfaceFlinger))
        controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        controller.hardClearOutputs("turn off from intent", blunt = true)

        assertThat(engine.applies).isEqualTo(1)
        assertThat(sweepsRun()).isEqualTo(2)
    }
}

/** Applies whatever it is given, so the controller reaches its committed state. */
private class ApplyingEngine(override val kind: EngineKind) : ColorEngine {
    var applies = 0
        private set

    override val capabilities: Set<EngineCapability> = emptySet()
    override suspend fun isAvailable(context: Context) = true
    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        applies++
        return EngineResult.Success
    }
    override suspend fun clear(context: Context) = EngineResult.Success
}
