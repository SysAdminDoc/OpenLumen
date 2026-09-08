package com.openlumen.engine

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Test

class SuProcessTimeoutTest {

    @Test fun processWaitUsesHardDeadline() {
        val process = DeadlineRecordingProcess()

        assertThat(Su.waitForProcessExit(process, timeoutMs = 137L)).isFalse()
        assertThat(process.timeoutMs).isEqualTo(137L)
        assertThat(process.timeoutUnit).isEqualTo(TimeUnit.MILLISECONDS)
    }

    private class DeadlineRecordingProcess : Process() {
        var timeoutMs: Long = -1L
        var timeoutUnit: TimeUnit? = null

        override fun getOutputStream() = ByteArrayOutputStream()

        override fun getInputStream() = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            timeoutMs = timeout
            timeoutUnit = unit
            return false
        }

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
    }
}
