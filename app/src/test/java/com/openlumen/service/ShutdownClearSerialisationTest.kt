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
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C326. Every engine call the controller makes is serialised by its apply lock
 * except the one `onDestroy` makes, which went straight to the engine. A
 * shutdown could therefore land in the middle of an apply, and the KCAL driver
 * raises the kernel's brightness floor in one write and records what it raised
 * in another: a clear between the two restores a floor that has not been
 * raised yet, then throws away the record the apply is about to depend on, and
 * the panel is left at our floor with nothing saying the user's value was ever
 * different.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShutdownClearSerialisationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun controllerFor(engine: ColorEngine, scope: CoroutineScope) = EngineController(
        context = context,
        probe = DriverProbe(engines = listOf(engine)),
        prefs = PreferencesStore(context, PresetKeyResolver.knownKeys),
        scope = scope,
        isUserUnlocked = { true },
        logTag = "test"
    )

    @Test fun `a shutdown clear waits for an apply already in flight`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val calls = CopyOnWriteArrayList<String>()
        val releaseApply = CompletableDeferred<Unit>()
        val engine = GatedEngine(calls, releaseApply)
        val controller = controllerFor(engine, scope)

        controller.ensureEngineFor(Preferences(engine = EngineKindDto.Kcal))
        val applying = scope.launch {
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        }
        while (!calls.contains("apply-start")) yield()

        val clearing = scope.launch { controller.clearActiveEngineForShutdown() }
        // The wait is what makes an unserialised clear visible: without the
        // lock it reaches the engine immediately, so the assertion below would
        // find it. With the lock there is nothing to wait for, and the apply is
        // still parked, so the length only decides how long a regression takes
        // to show rather than whether it shows at all.
        delay(300)

        assertThat(calls).containsExactly("apply-start")

        releaseApply.complete(Unit)
        applying.join()
        clearing.join()

        assertThat(calls).containsExactly("apply-start", "apply-end", "clear").inOrder()
        scope.cancel()
    }

    @Test fun `a shutdown clear on an idle controller still reaches the engine`() = runBlocking {
        // Positive control. A lock that never let go would pass the test above
        // by doing nothing at all.
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val calls = CopyOnWriteArrayList<String>()
        val engine = GatedEngine(calls, CompletableDeferred(Unit))
        val controller = controllerFor(engine, scope)

        controller.ensureEngineFor(Preferences(engine = EngineKindDto.Kcal))
        controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
        controller.clearActiveEngineForShutdown()

        assertThat(calls).containsExactly("apply-start", "apply-end", "clear").inOrder()
        scope.cancel()
    }
}

/** Holds its apply open until the test lets go, and records the order of both. */
private class GatedEngine(
    private val calls: MutableList<String>,
    private val releaseApply: CompletableDeferred<Unit>
) : ColorEngine {
    override val kind = EngineKind.KCAL
    override val capabilities: Set<EngineCapability> = emptySet()
    override suspend fun isAvailable(context: Context) = true

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        calls += "apply-start"
        releaseApply.await()
        calls += "apply-end"
        return EngineResult.Success
    }

    override suspend fun clear(context: Context): EngineResult {
        calls += "clear"
        return EngineResult.Success
    }
}
