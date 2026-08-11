package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contract coverage for the file lock shared by DiagnosticsLog readers and
 * writers. Reflection is intentional here: lock identity, not lock visibility,
 * is the concurrency guarantee the implementation must preserve.
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogConcurrencyTest {

    @Test
    fun `read waits for the same lock used by writers`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        DiagnosticsLog.clear(context)
        val lock = DiagnosticsLog::class.java.getDeclaredField("writeLock").let { field ->
            field.isAccessible = true
            field.get(DiagnosticsLog)!!
        }
        val enteredRead = CountDownLatch(1)
        val finishedRead = CountDownLatch(1)
        val reader = thread(start = false, name = "diagnostics-read-contract") {
            enteredRead.countDown()
            DiagnosticsLog.read(context)
            finishedRead.countDown()
        }

        synchronized(lock) {
            reader.start()
            assertThat(enteredRead.await(1, TimeUnit.SECONDS)).isTrue()
            assertThat(finishedRead.await(100, TimeUnit.MILLISECONDS)).isFalse()
        }

        assertThat(finishedRead.await(1, TimeUnit.SECONDS)).isTrue()
        reader.join(1_000)
    }
}
