package com.openlumen.engine.engines

import android.content.ContextWrapper
import android.os.Looper
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * C262. `installView` posts to the main looper and waits two seconds. When that
 * wait expires it reports failure, but the posted block keeps running, and
 * `EngineController` has already dropped the engine by the time it installs a
 * full-screen tinted window. Nothing then owns that window, and nothing removes
 * it: the user is left staring through a tint with no way to clear it short of
 * a reboot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlayEngineInstallTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun grantOverlayPermission() {
        // Without SYSTEM_ALERT_WINDOW installView bails before it posts
        // anything, so every assertion about window counts would pass for the
        // wrong reason.
        ShadowSettings.setCanDrawOverlays(true)
    }

    private fun windowManager() =
        context.getSystemService(WindowManager::class.java)

    /** Windows actually registered with WindowManager, not the engine's own bookkeeping. */
    private fun attachedOverlayCount(): Int =
        (shadowOf(windowManager()) as ShadowWindowManagerImpl).views.size

    @Test fun `an install that lands after the caller gave up removes its own window`() {
        // Hold the main looper past the bound so the caller times out, then let
        // the queued install run. Robolectric's looper is paused by default in
        // this mode, so draining it by hand is what "the main thread was busy
        // and then caught up" looks like.
        val started = CountDownLatch(1)
        val callerFinished = CountDownLatch(1)
        val engine = OverlayEngine()
        var installReported = true

        val caller = thread(name = "overlay-caller") {
            started.countDown()
            installReported = engine.installView(context, Presets.OFF)
            callerFinished.countDown()
        }

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        // Do not drain the looper: let the caller's bounded wait expire first.
        assertThat(
            callerFinished.await(
                OverlayEngine.MAIN_THREAD_TIMEOUT_SECONDS + 5,
                TimeUnit.SECONDS
            )
        ).isTrue()
        caller.join()

        assertThat(installReported).isFalse()

        // Now the main thread catches up and runs the install that the caller
        // already gave up on.
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(attachedOverlayCount()).isEqualTo(0)
    }

    @Test fun `an install the caller waits for keeps its window`() {
        // The positive control: without it, an engine that installed nothing at
        // all would pass the test above.
        val engine = OverlayEngine()
        val result = engine.installView(context, Presets.OFF)

        assertThat(result).isTrue()
        assertThat(attachedOverlayCount()).isEqualTo(1)
    }

    @Test fun `installing again after the window was torn down builds a fresh one`() {
        // Hilt holds this engine as a singleton, so it outlives a foreground
        // service the system kills. The window goes with the service but the
        // engine's own fields do not, and reporting that stale view as still
        // installed would leave the next service with no overlay at all.
        val engine = OverlayEngine()
        engine.installView(context, Presets.OFF)
        val shadow = shadowOf(windowManager()) as ShadowWindowManagerImpl
        val first = shadow.views.single()
        shadow.views.toList().forEach { shadow.removeView(it) }
        assertThat(attachedOverlayCount()).isEqualTo(0)

        val result = engine.installView(context, Presets.OFF)

        assertThat(result).isTrue()
        assertThat(attachedOverlayCount()).isEqualTo(1)
        assertThat(shadow.views.single()).isNotSameInstanceAs(first)
    }

    @Test fun `clearing removes the window`() = kotlinx.coroutines.runBlocking {
        val engine = OverlayEngine()
        engine.installView(context, Presets.OFF)
        assertThat(attachedOverlayCount()).isEqualTo(1)

        engine.clear(context)

        assertThat(attachedOverlayCount()).isEqualTo(0)
    }

    @Test fun `apply reinstalls against the context that owns the window`() =
        kotlinx.coroutines.runBlocking {
            // Only the service's context holds a token that can carry
            // TYPE_APPLICATION_OVERLAY. apply() used to reinstall with whatever
            // context the call site passed, so a reinstall driven from anywhere
            // else built the window against the wrong one.
            val serviceContext = ContextWrapper(context)
            val engine = OverlayEngine()
            engine.installView(serviceContext, Presets.OFF)

            // The service process is killed: the window goes, the singleton's
            // fields stay.
            val shadow = shadowOf(windowManager()) as ShadowWindowManagerImpl
            shadow.views.toList().forEach { shadow.removeView(it) }
            assertThat(attachedOverlayCount()).isEqualTo(0)

            // A caller that is not the service drives the next apply.
            engine.apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))

            assertThat(attachedOverlayCount()).isEqualTo(1)
            assertThat(shadow.views.single().context).isSameInstanceAs(serviceContext)
        }
}
