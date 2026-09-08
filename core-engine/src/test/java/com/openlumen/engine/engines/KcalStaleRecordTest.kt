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

    /**
     * Every shell the run issued, as one string.
     *
     * A clear that restores `kcal_min` now runs two: the tint reset on its
     * own first, because `onDestroy` caps the clear at two seconds and one
     * `su` round-trip can take four, and the floor restore after it. These
     * assertions are about which writes happen, not how many shells carry
     * them.
     */
    private fun allScripts() = scripts.joinToString("\n")

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

            assertThat(allScripts()).doesNotContain("kcal_min")
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

        assertThat(allScripts()).contains("echo '$originalMin' > '$base/kcal_min'")
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

        assertThat(allScripts()).doesNotContain("kcal_min")
        assertThat(recordFile.exists()).isFalse()
    }

    @Test fun `clear does not write an original the panel has outgrown`() = runBlocking {
        // The check in apply is bypassed by every early return there, so the
        // one on the clear path is what actually has to hold. Straight from a
        // record to a clear, as after a process restart.
        recordARaise()
        kernelMin = 35

        KcalEngine().clear(context)

        assertThat(allScripts()).doesNotContain("kcal_min")
        assertThat(recordFile.exists()).isFalse()
    }

    @Test fun `a floor raised while the filter is running is not overwritten`() = runBlocking {
        // The probe reads kcal_min once and caches it, so a user who tunes the
        // floor mid-session is invisible to the cached value. Only a live read
        // at clear time sees it.
        val engine = KcalEngine()
        engine.apply(context, tint)
        kernelMin = 35
        scripts.clear()

        engine.clear(context)

        assertThat(allScripts()).doesNotContain("kcal_min")
    }

    @Test fun `our own raise read back live still restores the user's value`() = runBlocking {
        // Positive control for both checks above. After our raise the live
        // read returns exactly the safety minimum, and treating that as the
        // user's tuning would strand every panel we had actually raised.
        val engine = KcalEngine()
        engine.apply(context, tint)
        kernelMin = KcalEngine.SAFETY_MIN
        scripts.clear()

        engine.clear(context)

        assertThat(allScripts()).contains("echo '$originalMin' > '$base/kcal_min'")
    }

    @Test fun `a clear that lands inside an apply cannot erase the record`() = runBlocking {
        // The shutdown clear cannot take a lock: onDestroy calls it from
        // runBlocking on a parked main Looper, so waiting for one waits
        // forever. It can therefore arrive between the record write and the
        // script, restore a floor the script has not raised yet, and delete
        // the record the raise is about to depend on. The record is written
        // again after the script for exactly this.
        val engine = KcalEngine()
        var interleaved = false
        Su.shellRunnerForTest = { script ->
            scripts += script
            if (!interleaved && script.contains("$base/kcal_min")) {
                interleaved = true
                engine.clear(context)
            }
            0
        }

        engine.apply(context, tint)

        assertThat(interleaved).isTrue()
        assertThat(KcalEngine.MinRestore.decode(recordFile.readText()))
            .isEqualTo(KcalEngine.MinRestore(originalMin, raised = true))
    }

    @Test fun `an uninterrupted apply and clear still end with no record`() = runBlocking {
        // Positive control for the re-assertion: writing the record again must
        // not leave one behind on the ordinary path.
        val engine = KcalEngine()
        engine.apply(context, tint)
        engine.clear(context)

        assertThat(recordFile.exists()).isFalse()
    }

    @Test fun `the tint reset goes first, with no su call in front of it`() = runBlocking {
        // onDestroy caps the whole clear at two seconds and one su round-trip
        // has a four-second timeout of its own, so anything that spawns a
        // shell before the tint reset can spend the entire budget and leave
        // the panel tinted. The floor is brightness, not tint, and can wait
        // for the next clear.
        val trace = mutableListOf<String>()
        val commandRunner = Su.commandRunnerForTest!!
        Su.commandRunnerForTest = { command ->
            trace += "command: $command"
            commandRunner(command)
        }
        Su.shellRunnerForTest = { script ->
            trace += if (script.contains("kcal_min")) "shell: floor" else "shell: tint"
            scripts += script
            shellExit
        }
        // One engine, so the sysfs probe happens on the apply and the clear
        // starts with its paths already resolved. A fresh engine would probe
        // first, which is a cost C256 accepts and this test is not about.
        val engine = KcalEngine()
        engine.apply(context, tint)
        trace.clear()

        engine.clear(context)

        assertThat(trace.first()).isEqualTo("shell: tint")
        assertThat(trace.filter { it.startsWith("shell: ") })
            .containsExactly("shell: tint", "shell: floor").inOrder()
        // And the read that decides the floor comes after the tint reset.
        assertThat(trace.indexOfFirst { it.startsWith("command: cat") })
            .isGreaterThan(trace.indexOf("shell: tint"))
    }

    @Test fun `a floor it cannot read is left alone and the record is kept`() = runBlocking {
        // The read is what tells the user's floor from ours. Falling back to
        // the value cached at probe time would overwrite their floor on
        // exactly the evidence this call exists to distrust, and keeping the
        // record means the next clear tries again.
        recordARaise()
        Su.commandRunnerForTest = { command ->
            when {
                command.startsWith("cat") -> Su.SuResult(1, "", "")
                command.contains("$base/kcal_min") -> Su.SuResult(0, "ok", "")
                command.contains("$base/kcal_enable") -> Su.SuResult(0, "ok", "")
                else -> Su.SuResult(1, "", "")
            }
        }

        KcalEngine().clear(context)

        assertThat(allScripts()).doesNotContain("kcal_min")
        assertThat(recordFile.exists()).isTrue()
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
        assertThat(allScripts()).doesNotContain("kcal_min")
        assertThat(recordFile.exists()).isFalse()
    }
}
