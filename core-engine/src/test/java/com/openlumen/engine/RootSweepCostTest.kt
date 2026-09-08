package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.engines.KcalEngine
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C325. The blunt reset is not free: every pass spawns `su`, which on a
 * rooted device is a visible stall and, the first time, a grant prompt. One
 * turn-off used to run three identical passes.
 *
 * `RootSweepRepeatTest` counts what a turn-off spends. This pins what a single
 * pass spends, which is the number that makes the repeats worth removing: one
 * transaction and one probe per candidate base. It is a characterisation test,
 * not a regression guard for the repeat fix, so it does not go red without it;
 * what it catches is a later change that turns one pass into several, such as
 * probing every candidate code rather than the one AOSP confirms.
 */
// Robolectric 4.16.1 ships no SDK 37 shadows and a library module has no
// targetSdk to fall back on, so the picker needs telling. 35 matches `app`.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootSweepCostTest {

    private val commands = CopyOnWriteArrayList<String>()

    @Before fun fakeAWorkingRoot() {
        Su.setCachedAvailableForTest(true)
        Su.commandRunnerForTest = { line ->
            commands += line
            // What an accepted backdoor transaction actually prints: 1015 is a
            // void call, so the reply parcel is empty and renders as NULL.
            Su.SuResult(exitCode = 0, stdout = "Result: Parcel(NULL)\n", stderr = "")
        }
        Su.shellRunnerForTest = { 0 }
    }

    @After fun restoreRealRoot() {
        Su.clearTestRunners()
        Su.setCachedAvailableForTest(null)
    }

    private fun disableTransactions() =
        commands.count { it.startsWith("service call SurfaceFlinger") }

    @Test fun `a root sweep issues one disable transaction`() {
        val result = runBlocking {
            DisplayEmergencyReset.clearRootTransforms(context = null, roots = true)
        }

        assertThat(disableTransactions()).isEqualTo(1)
        assertThat(result.surfaceFlingerCodes).hasSize(1)
        // The KCAL half is the rest of the bill, and it is per candidate base.
        assertThat(commands.count { it.startsWith("test -e") })
            .isEqualTo(KcalEngine.CANDIDATE_BASES.size)
    }

    @Test fun `a skipped root sweep spawns nothing at all`() {
        val result = runBlocking {
            DisplayEmergencyReset.clearRootTransforms(context = null, roots = false)
        }

        assertThat(commands).isEmpty()
        assertThat(result.surfaceFlingerCodes).isEmpty()
        assertThat(result.kcalPaths).isEmpty()
    }
}
