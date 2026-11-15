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
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

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

    /** A Night Light driver that is granted and clamps to AOSP's range (C364). */
    private fun grantedNightLight(): SecureSettingsEngine {
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Secure.putInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_AUTO_MODE, 0)
        Settings.Secure.putInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
        return SecureSettingsEngine.withNightLightRange(2596..4082)
    }

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

    @Test fun `direct boot keeps Night Light for a preset outside its range`() {
        // C364 moves Red to the overlay once the phone is unlocked. This
        // restore is for the lock screen, which no overlay window covers, so
        // a clamped Red there beats none.
        val nightLight = grantedNightLight()
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(nightLight, overlay)

        runBlocking {
            controller.restoreDirectBootState(
                DirectBootState(
                    enabled = true,
                    active = true,
                    engine = EngineKindDto.Auto,
                    matrix = MatrixDto(r = Presets.RED.r, g = Presets.RED.g, b = Presets.RED.b)
                )
            )
        }

        assertThat(overlay.calls).isEmpty()
        assertThat(
            Settings.Secure.getInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 0)
        ).isEqualTo(1)
    }

    @Test fun `an overlay granted later takes a clamped preset on the next resolve`() {
        // Auto keeps its probe between resolves. Without the overlay being
        // read again, Red stayed clamped on Night Light after the user
        // granted "display over other apps".
        val nightLight = grantedNightLight()
        val overlay = RecordingEngine(EngineKind.OVERLAY, available = false)
        val controller = controllerFor(nightLight, overlay)
        val red = Preferences(engine = EngineKindDto.Auto, activePresetKey = "red")

        runBlocking {
            controller.ensureEngineFor(red)
            overlay.available = true
            controller.ensureEngineFor(red)
            controller.applyIfNeeded(true, Presets.RED, transitionDurationMs = 0)
        }

        assertThat(overlay.calls).containsExactly("apply")
        assertThat(overlay.applied.single()).isEqualTo(Presets.RED)
    }

    @Test fun `looking at the overlay again with nothing probed yet probes nothing`() {
        // The app asks on every return. The full probe runs `su` for the
        // root drivers, so with no earlier probe to compare against, this
        // must leave it to the next ordinary resolve.
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER, available = false)
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(surfaceFlinger, overlay)

        val switched = runBlocking { controller.refreshOverlayChoice(prefsFor(EngineKindDto.Auto)) }

        assertThat(switched).isFalse()
        assertThat(surfaceFlinger.availabilityChecks.get()).isEqualTo(0)
        assertThat(overlay.availabilityChecks.get()).isEqualTo(0)
        assertThat(controller.activeEngineKind()).isNull()
    }

    @Test fun `a granted overlay takes a clamped preset with only the overlay read again`() {
        val nightLight = grantedNightLight()
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER, available = false)
        val overlay = RecordingEngine(EngineKind.OVERLAY, available = false)
        val controller = controllerFor(surfaceFlinger, nightLight, overlay)
        val red = Preferences(engine = EngineKindDto.Auto, activePresetKey = "red")

        val switched = runBlocking {
            controller.ensureEngineFor(red)
            overlay.available = true
            controller.refreshOverlayChoice(red)
        }

        assertThat(switched).isTrue()
        assertThat(controller.activeEngineKind()).isEqualTo(EngineKind.OVERLAY)
        assertThat(surfaceFlinger.availabilityChecks.get()).isEqualTo(1)
    }

    @Test fun `a filter in standby starts on the overlay once it is granted`() {
        // A fresh install on Auto: no root, no Night Light grant, no overlay.
        // The switch is on with nothing to draw, Home asks for "display over
        // other apps", and the return from Settings has to start the filter.
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER, available = false)
        val overlay = RecordingEngine(EngineKind.OVERLAY, available = false)
        val controller = controllerFor(surfaceFlinger, overlay)
        val auto = prefsFor(EngineKindDto.Auto)

        val switched = runBlocking {
            controller.ensureEngineFor(auto)
            overlay.available = true
            controller.refreshOverlayChoice(auto)
        }

        assertThat(switched).isTrue()
        assertThat(controller.activeEngineKind()).isEqualTo(EngineKind.OVERLAY)
    }

    @Test fun `an unchanged overlay answer leaves the driver where it is`() {
        val nightLight = grantedNightLight()
        val overlay = RecordingEngine(EngineKind.OVERLAY, available = false)
        val controller = controllerFor(nightLight, overlay)
        val red = Preferences(engine = EngineKindDto.Auto, activePresetKey = "red")

        val switched = runBlocking {
            controller.ensureEngineFor(red)
            controller.refreshOverlayChoice(red)
        }

        assertThat(switched).isFalse()
        assertThat(controller.activeEngineKind()).isEqualTo(EngineKind.COLOR_DISPLAY_MANAGER)
        // The first resolve and this check. A third read would mean a resolve
        // ran for an answer that had not changed.
        assertThat(overlay.availabilityChecks.get()).isEqualTo(2)
    }

    @Test fun `an unchanged answer in standby runs no probe`() {
        // Standby keeps no probe, so a resolve here would be the full one,
        // `su` included, on every return to the app.
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER, available = false)
        val overlay = RecordingEngine(EngineKind.OVERLAY, available = false)
        val controller = controllerFor(surfaceFlinger, overlay)
        val auto = prefsFor(EngineKindDto.Auto)

        val switched = runBlocking {
            controller.ensureEngineFor(auto)
            controller.refreshOverlayChoice(auto)
        }

        assertThat(switched).isFalse()
        assertThat(controller.activeEngineKind()).isNull()
        assertThat(surfaceFlinger.availabilityChecks.get()).isEqualTo(1)
    }

    @Test fun `a resolve that starts while a turn-off is still clearing installs nothing`() {
        // The clear marks its start, then waits on the old driver. A resolve
        // reading the mark in that gap used to match the finished clear and
        // install anyway.
        val clearing = CompletableDeferred<Unit>()
        val finishClear = CompletableDeferred<Unit>()
        val probed = CompletableDeferred<Unit>()
        val surfaceFlinger = RecordingEngine(
            EngineKind.SURFACE_FLINGER,
            onClear = {
                clearing.complete(Unit)
                finishClear.await()
            }
        )
        val overlay = RecordingEngine(EngineKind.OVERLAY, onAvailability = { probed.complete(Unit) })
        val controller = controllerFor(surfaceFlinger, overlay)

        runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.SurfaceFlinger))
            val clear = scope.launch { controller.hardClearOutputs("filter disabled") }
            withTimeout(5_000) { clearing.await() }
            val resolve = scope.launch { controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay)) }
            withTimeout(5_000) { probed.await() }
            finishClear.complete(Unit)
            withTimeout(5_000) {
                clear.join()
                resolve.join()
            }
        }

        assertThat(controller.activeEngineKind()).isNull()
        assertThat(surfaceFlinger.calls).containsExactly("clear")
    }

    @Test fun `a turn-off during the overlay check is not reported as a switch`() {
        // The service applies the preset when the check says the driver
        // moved. A clear overtaking the check's resolve leaves no driver,
        // which differs from Night Light but is the clear's doing.
        val nightLight = grantedNightLight()
        val reads = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val overlay = RecordingEngine(
            EngineKind.OVERLAY,
            available = false,
            onAvailability = {
                // The first resolve, the check's own read, then the resolve
                // it starts: hold that one.
                if (reads.incrementAndGet() >= 3) {
                    entered.complete(Unit)
                    release.await()
                }
            }
        )
        val controller = controllerFor(nightLight, overlay)
        val red = Preferences(engine = EngineKindDto.Auto, activePresetKey = "red")

        val switched = runBlocking {
            controller.ensureEngineFor(red)
            overlay.available = true
            val check = scope.async { controller.refreshOverlayChoice(red) }
            withTimeout(5_000) { entered.await() }
            controller.hardClearOutputs("filter disabled")
            release.complete(Unit)
            withTimeout(5_000) { check.await() }
        }

        assertThat(switched).isFalse()
        assertThat(controller.activeEngineKind()).isNull()
    }

    @Test fun `a turn-off during a resolve keeps the resolved driver off the screen`() {
        // The resolve suspends in the probe. A turn-off clearing everything
        // in that gap used to be followed by the resolve installing its
        // driver anyway, on a filter that was now off.
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val overlay = RecordingEngine(
            EngineKind.OVERLAY,
            onAvailability = {
                entered.complete(Unit)
                release.await()
            }
        )
        val controller = controllerFor(overlay)

        runBlocking {
            val resolve = scope.launch { controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay)) }
            withTimeout(5_000) { entered.await() }
            controller.hardClearOutputs("filter disabled")
            release.complete(Unit)
            withTimeout(5_000) { resolve.join() }
        }

        assertThat(controller.activeEngineKind()).isNull()
        assertThat(overlay.calls).isEmpty()
    }

    @Test fun `a system overwrite on SurfaceFlinger re-applies the committed target`() {
        // The gate would drop the same target as a duplicate, which is how
        // Night Light used to keep the screen after it overwrote the filter.
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(surfaceFlinger)

        val reasserted = runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.SurfaceFlinger))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.reassertAfterSystemOverwrite()
        }

        assertThat(reasserted).isTrue()
        assertThat(surfaceFlinger.calls).containsExactly("apply", "apply")
        assertThat(surfaceFlinger.applied).containsExactly(Presets.NIGHT, Presets.NIGHT)
    }

    @Test fun `a system overwrite leaves an inactive filter alone`() {
        // With the filter off, the system's Night Light is what the user wants.
        val surfaceFlinger = RecordingEngine(EngineKind.SURFACE_FLINGER)
        val controller = controllerFor(surfaceFlinger)

        val reasserted = runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.SurfaceFlinger))
            controller.applyIfNeeded(false, LumenMatrix.IDENTITY, transitionDurationMs = 0)
            surfaceFlinger.calls.clear()
            controller.reassertAfterSystemOverwrite()
        }

        assertThat(reasserted).isFalse()
        assertThat(surfaceFlinger.calls).isEmpty()
    }

    @Test fun `a system overwrite does not touch drivers the system cannot overwrite`() {
        val overlay = RecordingEngine(EngineKind.OVERLAY)
        val controller = controllerFor(overlay)

        val reasserted = runBlocking {
            controller.ensureEngineFor(prefsFor(EngineKindDto.Overlay))
            controller.applyIfNeeded(true, Presets.NIGHT, transitionDurationMs = 0)
            controller.reassertAfterSystemOverwrite()
        }

        assertThat(reasserted).isFalse()
        assertThat(overlay.calls).containsExactly("apply")
    }
}

/**
 * Records the order of engine calls so a sequence can be asserted.
 *
 * [onAvailability] and [onClear] sit before [applyResult] on purpose: a
 * trailing lambda binds to the last parameter, and a Unit-returning one would
 * swallow `{ EngineResult.Failure("no") }` without a compile error.
 */
private class RecordingEngine(
    override val kind: EngineKind,
    @Volatile var available: Boolean = true,
    private val onAvailability: suspend () -> Unit = {},
    private val onClear: suspend () -> Unit = {},
    private val applyResult: () -> EngineResult = { EngineResult.Success }
) : ColorEngine {
    override val capabilities: Set<EngineCapability> = emptySet()
    val calls = CopyOnWriteArrayList<String>()
    val applied = CopyOnWriteArrayList<LumenMatrix>()
    val availabilityChecks = AtomicInteger()

    override suspend fun isAvailable(context: Context): Boolean {
        availabilityChecks.incrementAndGet()
        onAvailability()
        return available
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult {
        calls += "apply"
        applied += matrix
        return applyResult()
    }

    override suspend fun clear(context: Context): EngineResult {
        calls += "clear"
        onClear()
        return EngineResult.Success
    }
}
