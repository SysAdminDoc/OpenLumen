package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.EngineResult
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Su
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C257. The engine raises `kcal_min` to [KcalEngine.SAFETY_MIN] on panels whose
 * kernel floor sits below it, and promises to put the user's value back. These
 * drive the whole apply/clear path against a scripted shell, because the
 * promise only breaks in the seam between the two: the min write carries
 * `|| true` so it lands even when a later write in the same script fails, and
 * that failure then invalidates the path cache the original value used to live
 * in.
 */
// Robolectric 4.16.1 ships no SDK 37 shadows, and a library module has no
// targetSdk for the picker to fall back on, so it would otherwise try the
// compileSdk and fail to start. 35 matches what `app` resolves to.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KcalMinRestoreTest {

    private val base = KcalEngine.CANDIDATE_BASES.first()
    private val scripts = mutableListOf<String>()

    /** Exit code the scripted shell returns for the next `runShell`. */
    private var shellExit: Int = 0

    /** The user's pre-OpenLumen floor, deliberately below the safety floor. */
    private val originalMin = 12

    /** What the next `cat kcal_min` reports. Moves when a raise lands. */
    private var kernelMin = 12

    @Before fun installScriptedShell() {
        kernelMin = originalMin
        Su.setCachedAvailableForTest(true)
        Su.commandRunnerForTest = { command ->
            when {
                command.contains("$base/kcal_min") && command.startsWith("cat") ->
                    Su.SuResult(0, "$kernelMin", "")
                command.contains("$base/kcal_min") -> Su.SuResult(0, "ok", "")
                command.contains("$base/kcal_enable") -> Su.SuResult(0, "ok", "")
                else -> Su.SuResult(1, "", "")
            }
        }
        Su.shellRunnerForTest = { script ->
            scripts += script
            shellExit
        }
    }

    @After fun removeScriptedShell() {
        Su.clearTestRunners()
        Su.setCachedAvailableForTest(null)
        File(RuntimeEnvironment.getApplication().filesDir, "kcal-min-restore").delete()
    }

    @Test fun `a failed apply that already raised the minimum still restores it on clear`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val engine = KcalEngine()

            // The rgb write fails. The min write ahead of it carries `|| true`,
            // so on a real device the kernel floor is raised anyway.
            shellExit = 1
            val applied = engine.apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))

            assertThat(applied).isInstanceOf(EngineResult.Failure::class.java)
            assertThat(scripts.single()).contains("echo '${KcalEngine.SAFETY_MIN}' > '$base/kcal_min'")

            // The failure invalidated the path cache, taking `originalMin` with
            // it, and never latched `appliedNonIdentity`. clear() has to find
            // the user's value anyway.
            scripts.clear()
            shellExit = 0
            val cleared = engine.clear(context)

            assertThat(cleared).isEqualTo(EngineResult.Success)
            assertThat(scripts.single()).contains("echo '$originalMin' > '$base/kcal_min'")
        }

    @Test fun `the record is dropped once the minimum is back, so a second clear leaves it alone`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val engine = KcalEngine()

            shellExit = 0
            engine.apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))
            engine.clear(context)
            scripts.clear()

            engine.clear(context)

            assertThat(scripts.single()).doesNotContain("kcal_min")
        }

    @Test fun `a partly applied raise is not mistaken for the user's original`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()

        // First apply raises the floor and fails on the rgb write, which
        // invalidates the path cache.
        shellExit = 1
        KcalEngine().apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))
        scripts.clear()

        // The kernel clamped our 20 to 15 rather than taking it whole, so the
        // next probe reads 15. That is our own write coming back, not anything
        // the user chose. It is still under the safety floor, so the raise is
        // retried, and the record must keep the 12 we saw the first time.
        kernelMin = 15
        val restarted = KcalEngine()
        shellExit = 0
        restarted.apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))

        assertThat(scripts.single())
            .contains("echo '${KcalEngine.SAFETY_MIN}' > '$base/kcal_min'")

        scripts.clear()
        restarted.clear(context)

        assertThat(scripts.single()).contains("echo '$originalMin' > '$base/kcal_min'")
    }

    @Test fun `the persisted record outlives the object that wrote it`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()

        shellExit = 1
        KcalEngine().apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))
        scripts.clear()

        // Nothing in memory carries over a process death, so the file is the
        // only thing that can tell the next run which value to put back.
        val onDisk = File(context.filesDir, "kcal-min-restore").readText()
        assertThat(KcalEngine.MinRestore.decode(onDisk))
            .isEqualTo(KcalEngine.MinRestore(originalMin, raised = true))

        shellExit = 0
        KcalEngine().clear(context)

        assertThat(scripts.single()).contains("echo '$originalMin' > '$base/kcal_min'")
        assertThat(File(context.filesDir, "kcal-min-restore").exists()).isFalse()
    }

    @Test fun `an engine that never raised the minimum does not write it on clear`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        // This kernel's floor is already above the safety floor, so C166 says
        // leave the user's tuning alone in both directions.
        Su.commandRunnerForTest = { command ->
            when {
                command.contains("$base/kcal_min") && command.startsWith("cat") ->
                    Su.SuResult(0, "64", "")
                command.contains("$base/kcal_min") -> Su.SuResult(0, "ok", "")
                command.contains("$base/kcal_enable") -> Su.SuResult(0, "ok", "")
                else -> Su.SuResult(1, "", "")
            }
        }
        val engine = KcalEngine()

        shellExit = 0
        engine.apply(context, LumenMatrix(r = 1f, g = 0.7f, b = 0.4f))
        engine.clear(context)

        assertThat(scripts).hasSize(2)
        assertThat(scripts.none { it.contains("kcal_min") }).isTrue()
    }
}
