package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import com.openlumen.CrashLogger
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C335. Clearing either log is one tap with no confirmation, which is the way
 * this app is meant to work, but it was also the only destructive action with
 * no way back. The undo on the snackbar hands the snapshot the dialog was
 * already holding back to these.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogUndoTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val diagnostics get() = File(context.filesDir, "diagnostics.log")
    private val crashes get() = File(context.filesDir, "crash.log")

    @Before fun clean() {
        DiagnosticsLog.clearTestWriter()
        diagnostics.delete()
        crashes.delete()
    }

    @After fun cleanUp() {
        diagnostics.delete()
        crashes.delete()
    }

    private fun log(message: String) =
        DiagnosticsLog.logBlocking(
            context,
            DiagnosticsLog.Level.INFO,
            DiagnosticsLog.Category.SERVICE,
            message
        )

    @Test fun `undo puts the diagnostics lines back`() {
        log("before the clear")
        val snapshot = DiagnosticsLog.read(context)
        DiagnosticsLog.clear(context)
        assertThat(DiagnosticsLog.read(context)).isEmpty()

        assertThat(DiagnosticsLog.restore(context, snapshot)).isTrue()

        assertThat(DiagnosticsLog.read(context)).contains("before the clear")
    }

    @Test fun `undo keeps whatever was logged after the clear`() {
        // The service keeps running while the snackbar is on screen, so an
        // undo that overwrote would throw away lines the user never asked to
        // lose. The timeline reads by the stamp on each line, not by position.
        log("before the clear")
        val snapshot = DiagnosticsLog.read(context)
        DiagnosticsLog.clear(context)
        log("after the clear")

        DiagnosticsLog.restore(context, snapshot)

        val restored = DiagnosticsLog.read(context)
        assertThat(restored).contains("before the clear")
        assertThat(restored).contains("after the clear")
    }

    @Test fun `undoing an empty log does nothing`() {
        // Clear on an already empty log offers an undo that has nothing to
        // give back, and a blank write would leave a stray newline behind.
        assertThat(DiagnosticsLog.restore(context, "")).isFalse()
        assertThat(DiagnosticsLog.restore(context, "   \n")).isFalse()
        assertThat(diagnostics.exists()).isFalse()
    }

    @Test fun `a restored log is still held under the size cap`() {
        // A snapshot is bounded by the cap, but a snapshot plus everything
        // logged since is not, and an unbounded log is the thing the cap
        // exists to prevent.
        val snapshot = "x".repeat(60 * 1024) + "\n"
        repeat(40) { log("filler line number $it that is long enough to matter".repeat(4)) }

        DiagnosticsLog.restore(context, snapshot)

        assertThat(diagnostics.length()).isAtMost(64L * 1024)
    }

    @Test fun `undo puts the crash log back`() {
        crashes.writeText("java.lang.IllegalStateException: boom\n")
        val snapshot = CrashLogger.read(context)
        CrashLogger.clear(context)
        assertThat(CrashLogger.read(context)).isEmpty()

        assertThat(CrashLogger.restore(context, snapshot)).isTrue()

        assertThat(CrashLogger.read(context)).contains("boom")
    }

    @Test fun `undoing an empty crash log does nothing`() {
        assertThat(CrashLogger.restore(context, "")).isFalse()
        assertThat(crashes.exists()).isFalse()
    }
}
