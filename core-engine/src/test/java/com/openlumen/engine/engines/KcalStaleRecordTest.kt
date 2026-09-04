package com.openlumen.engine.engines

import com.google.common.truth.Truth.assertThat
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
 * C326. The restore record outlives the process that wrote it, which is the
 * point of it, but it can also outlive the state it describes. A user who
 * raises `kcal_min` by hand after we recorded their old value gets that old
 * value written back over their own the next time the filter is switched off.
 * Refusing to overwrite a floor the user chose is the same rule C166 applies
 * on the way in; it just was not applied on the way out.
 *
 * The distinguishing detail is that our own raise writes exactly
 * [KcalEngine.SAFETY_MIN], so a floor reading back as the safety minimum is
 * ours and a floor above it is theirs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KcalStaleRecordTest {

    private val base = KcalEngine.CANDIDATE_BASES.first()
    private val scripts = mutableListOf<String>()
    private var shellExit: Int = 0

    /** The user's pre-OpenLumen floor, below the safety floor so we raise it. */
    private val originalMin = 10

    private var kernelMin = originalMin

    private val context get() = RuntimeEnvironment.getApplication()
    private val recordFile get() = File(context.filesDir, "kcal-min-restore")
    private val tint = LumenMatrix(r = 1f, g = 0.7f, b = 0.4f)

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
        recordFile.delete()
    }

    /** Leaves a record on disk the way a failed apply does. */
    private fun recordARaise() = runBlocking {
        shellExit = 1
        KcalEngine().apply(context, tint)
        assertThat(recordFile.exists()).isTrue()
        scripts.clear()
        shellExit = 0
    }

    @Test fun `a floor the user raised past the safety minimum retires the old record`() =
        runBlocking {
            recordARaise()

            // The user has since set their floor to 35 by hand. Nothing we
            // write ever produces that number.
            kernelMin = 35
            val engine = KcalEngine()
            engine.apply(context, tint)
            scripts.clear()
            engine.clear(context)

            assertThat(scripts.single()).doesNotContain("kcal_min")
            assertThat(recordFile.exists()).isFalse()
        }

    @Test fun `our own raise reading back is not mistaken for the user's tuning`() = runBlocking {
        // Positive control for the test above. The floor now reads exactly the
        // safety minimum, which is what our own write puts there, so the record
        // has to survive and the user's 10 has to come back.
        recordARaise()

        kernelMin = KcalEngine.SAFETY_MIN
        val engine = KcalEngine()
        engine.apply(context, tint)
        scripts.clear()
        engine.clear(context)

        assertThat(scripts.single()).contains("echo '$originalMin' > '$base/kcal_min'")
    }

    @Test fun `a record for a floor the kernel no longer exposes is retired`() = runBlocking {
        recordARaise()

        // The module was rebuilt without kcal_min. The record describes a node
        // that is not there any more.
        Su.commandRunnerForTest = { command ->
            when {
                command.contains("$base/kcal_min") -> Su.SuResult(1, "", "")
                command.contains("$base/kcal_enable") -> Su.SuResult(0, "ok", "")
                else -> Su.SuResult(1, "", "")
            }
        }
        val engine = KcalEngine()
        engine.apply(context, tint)
        scripts.clear()
        engine.clear(context)

        assertThat(scripts.single()).doesNotContain("kcal_min")
        assertThat(recordFile.exists()).isFalse()
    }

    @Test fun `retiring a record does not disturb a panel that never had one`() = runBlocking {
        // Nothing on disk, a floor already above the safety minimum: the engine
        // has no business writing kcal_min in either direction.
        kernelMin = 64
        val engine = KcalEngine()

        shellExit = 0
        engine.apply(context, tint)
        engine.clear(context)

        assertThat(scripts).hasSize(2)
        assertThat(scripts.none { it.contains("kcal_min") }).isTrue()
        assertThat(recordFile.exists()).isFalse()
    }
}
