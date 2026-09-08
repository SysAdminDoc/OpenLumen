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
import com.openlumen.engine.Presets
import com.openlumen.engine.Su
import com.openlumen.engine.engines.SecureSettingsEngine
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C325. A single "turn off" tap ran the blunt root reset three times: the
 * command runs it, the `enabled = false` that command writes comes back round
 * the preference collector and runs it again, and `stopSelf` runs it a third
 * time on the way down. Every pass spawns `su`, which on a rooted device is a
 * visible stall and, the first time, a grant prompt.
 *
 * Counted through a scripted `su` rather than through the diagnostics log,
 * because the cost being saved is the subprocess and nothing else is a
 * measurement of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootSweepRepeatTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val commands = CopyOnWriteArrayList<String>()

    @Before fun fakeAWorkingRoot() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        Su.setCachedAvailableForTest(true)
        Su.commandRunnerForTest = { line ->
            commands += line
            // What an accepted backdoor transaction prints: 1015 is a void
            // call, so the reply parcel is empty and renders as NULL.
            Su.SuResult(exitCode = 0, stdout = "Result: Parcel(NULL)\n", stderr = "")
        }
        Su.shellRunnerForTest = { 0 }
    }

    @After fun restoreRealRoot() {
        Su.clearTestRunners()
        Su.setCachedAvailableForTest(null)
    }

    private fun controller(vararg engines: ColorEngine) = EngineController(
        context = context,
        probe = DriverProbe(engines = engines.toList()),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test"
    )

    private fun disableTransactions() =
        commands.count { it.startsWith("service call SurfaceFlinger") }

    @Test fun `one turn-off issues exactly one disable transaction`() = runBlocking {
        val controller = controller()

        // The three calls a turn-off makes, in the order LumenService makes them.
        controller.hardClearOutputs("turn off from intent", blunt = true)
        controller.hardClearOutputs("filter disabled")
        controller.clearRootTransformsForShutdown()

        assertThat(disableTransactions()).isEqualTo(1)
    }

    @Test fun `each pass would sweep on its own`() = runBlocking {
        // Positive control for the count above: three separate controllers,
        // which is what three separate processes would be, still sweep three
        // times. The saving is the repeats within one turn-off, not the sweep.
        controller().hardClearOutputs("turn off from intent", blunt = true)
        controller().hardClearOutputs("filter disabled")
        controller().clearRootTransformsForShutdown()

        assertThat(disableTransactions()).isEqualTo(3)
    }

    // Block body, not `= runBlocking { … }`: containsExactly returns a value
    // and JUnit rejects a test method that is not void.
    @Test fun `the secure rows are still cleared on a pass that skips the sweep`() {
      runBlocking {
        // The latch covers the root half only. Ordering between the command and
        // the preference write is not fixed, so whichever blunt pass arrives
        // second still has to do the secure work the first one did not.
        for (key in USER_OWNED_ROWS) {
            Settings.Secure.putInt(context.contentResolver, key, 1)
        }
        val controller = controller()

        controller.hardClearOutputs("filter disabled")
        controller.hardClearOutputs("turn off from intent", blunt = true)

        assertThat(disableTransactions()).isEqualTo(1)
        assertThat(USER_OWNED_ROWS.map { Settings.Secure.getInt(context.contentResolver, it, 1) })
            .containsExactly(0, 0, 0)
      }
    }

    @Test fun `an apply arms the sweep again`() = runBlocking {
        val engine = ApplyingEngine(EngineKind.SURFACE_FLINGER, EngineResult.Success)
        val controller = controller(engine)

        controller.hardClearOutputs("turn off from intent", blunt = true)
        controller.ensureEngineFor(Preferences(engine = EngineKindDto.SurfaceFlinger))
        controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        controller.hardClearOutputs("turn off from intent", blunt = true)

        assertThat(engine.applies).isEqualTo(1)
        assertThat(disableTransactions()).isEqualTo(2)
    }

    @Test fun `an apply that reported failure still arms the sweep`() = runBlocking {
        // A driver that reports failure may still have reached the panel: a su
        // that times out mid-transaction, or a `set -e` script whose later
        // write failed after the earlier one landed. Both are why the blunt
        // sweep exists, so a failure must not be the case that skips it.
        val engine = ApplyingEngine(EngineKind.SURFACE_FLINGER, EngineResult.Failure("timed out"))
        val controller = controller(engine)

        controller.hardClearOutputs("turn off from intent", blunt = true)
        controller.ensureEngineFor(Preferences(engine = EngineKindDto.SurfaceFlinger))
        controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        controller.hardClearOutputs("turn off from intent", blunt = true)

        assertThat(engine.applies).isAtLeast(1)
        assertThat(disableTransactions()).isEqualTo(2)
    }

    @Test fun `a sweep that su refused is retried rather than latched`() = runBlocking {
        // The sweep returns normally with nothing done when su is unavailable
        // or the grant was refused, and that is the case that most needs the
        // next pass to try again.
        Su.setCachedAvailableForTest(false)
        val controller = controller()

        controller.hardClearOutputs("turn off from intent", blunt = true)
        assertThat(disableTransactions()).isEqualTo(0)

        // The user answers the prompt a second late.
        Su.setCachedAvailableForTest(true)
        controller.clearRootTransformsForShutdown()

        assertThat(disableTransactions()).isEqualTo(1)
    }

    private companion object {
        val USER_OWNED_ROWS = listOf(
            SecureSettingsEngine.KEY_NIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_CORRECTION_ENABLED
        )
    }
}

/** Records how many applies it saw and reports whatever the test asked for. */
private class ApplyingEngine(
    override val kind: EngineKind,
    private val result: EngineResult
) : ColorEngine {
    var applies = 0
        private set

    override val capabilities: Set<EngineCapability> = emptySet()
    override suspend fun isAvailable(context: Context) = true
    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        applies++
        return result
    }
    override suspend fun clear(context: Context) = EngineResult.Success
}
