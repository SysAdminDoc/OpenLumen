package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * C263. `log` used to append, and past the size cap rewrite the whole file, on
 * whichever thread called it. `LumenService.onCreate`, the screen and unlock
 * receiver path and three sites in `EngineController` all call it from the main
 * thread. The write is now handed off; these cover the two things that can go
 * wrong when you do that.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogThreadingTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearLog() {
        DiagnosticsLog.clear(context)
    }

    @After fun cleanUp() {
        DiagnosticsLog.clear(context)
    }

    private fun writeLock(): Any =
        DiagnosticsLog::class.java.getDeclaredField("writeLock").let { field ->
            field.isAccessible = true
            field.get(DiagnosticsLog)!!
        }

    /** Wait for the deferred writer to catch up, without sleeping blindly. */
    private fun awaitLines(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (DiagnosticsLog.read(context).lines().count { it.isNotBlank() } >= count) return
            Thread.sleep(10)
        }
        throw AssertionError(
            "only ${DiagnosticsLog.read(context).lines().count { it.isNotBlank() }} of " +
                "$count lines were written before the deadline"
        )
    }

    @Test fun `the caller is not blocked by a busy writer`() {
        // Hold the file lock the writer needs. A synchronous log() would park
        // the caller here for the whole hold; the deferred one must not.
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread(name = "diag-lock-holder") {
            synchronized(writeLock()) {
                holding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue()

        val returned = CountDownLatch(1)
        thread(name = "diag-caller") {
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.INFO,
                DiagnosticsLog.Category.SERVICE,
                "while the writer is busy"
            )
            returned.countDown()
        }

        val promptly = returned.await(5, TimeUnit.SECONDS)
        release.countDown()
        holder.join()

        assertThat(promptly).isTrue()
    }

    @Test fun `lines keep call order across the handoff`() {
        // The whole risk of deferring the write: two writes landing out of the
        // order they were asked for, or timestamps taken late on a pool thread.
        // The line is built on the caller's thread and the writer is
        // single-threaded, so neither can happen.
        repeat(20) { i ->
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.INFO,
                DiagnosticsLog.Category.ENGINE,
                "event $i"
            )
        }
        awaitLines(20)

        val lines = DiagnosticsLog.read(context).lines().filter { it.isNotBlank() }
        assertThat(lines).hasSize(20)
        lines.forEachIndexed { index, line -> assertThat(line).endsWith("event $index") }

        val timestamps = lines.map { it.substringBefore(' ') }
        assertThat(timestamps).isInOrder()
    }

    @Test fun `the timestamp is when the event happened, not when the queue drained`() {
        // Block the writer, queue a backlog behind it, then let go. Every event
        // was asked for before the release, so every timestamp must predate it.
        // A timestamp taken as each queued write finally runs would land after
        // the release instead, dating a burst of service events to whenever the
        // file happened to free up. That is the whole point of a timeline.
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread(name = "diag-lock-holder") {
            synchronized(writeLock()) {
                holding.countDown()
                release.await(20, TimeUnit.SECONDS)
            }
        }
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue()

        repeat(BACKLOG_EVENTS) { i ->
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.ENGINE,
                "queued $i"
            )
        }

        val releasedAt = Instant.now()
        release.countDown()
        holder.join()
        awaitLines(BACKLOG_EVENTS)

        val stamps = DiagnosticsLog.read(context)
            .lines()
            .filter { it.isNotBlank() }
            .map { Instant.parse(it.substringBefore(' ')) }

        assertThat(stamps).hasSize(BACKLOG_EVENTS)
        // The drain has to have taken real time, or the assertion below is
        // vacuous: if everything finished within the clock's granularity of the
        // release, both stamping strategies would look identical.
        val drainMillis = Duration.between(releasedAt, Instant.now()).toMillis()
        assertThat(drainMillis).isGreaterThan(0L)
        for (stamp in stamps) {
            // At most, not strictly before: the last log() call and the release
            // can land in the same clock tick. A write-time stamp would be
            // strictly after the release, which this still catches.
            assertThat(stamp).isAtMost(releasedAt)
        }
    }

    @Test fun `the blocking variant is readable as soon as it returns`() {
        // Shutdown paths need this: the process may not survive long enough for
        // a queued write to run.
        DiagnosticsLog.logBlocking(
            context,
            DiagnosticsLog.Level.INFO,
            DiagnosticsLog.Category.SERVICE,
            "onDestroy"
        )

        assertThat(DiagnosticsLog.read(context)).contains("onDestroy")
    }

    private companion object {
        /**
         * Deep enough that draining the queue takes measurable time, so a
         * write-time timestamp would land after the lock release, short enough
         * not to drag the suite or trip the log's own size cap.
         */
        const val BACKLOG_EVENTS = 150
    }
}
