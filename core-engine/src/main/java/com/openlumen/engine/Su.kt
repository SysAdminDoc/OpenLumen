package com.openlumen.engine

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Minimal su wrapper. Process deadlines are enforced by Process.waitFor rather
 * than coroutine cancellation so a blocked Java stream cannot defeat cleanup.
 */
object Su {
    private const val TAG = "OpenLumen/Su"
    private const val PROBE_TIMEOUT_MS = 8_000L
    private const val CMD_TIMEOUT_MS = 4_000L
    private const val CLEANUP_WAIT_MS = 250L

    @Volatile private var cachedAvailable: Boolean? = null
    private val availabilityMutex = Mutex()
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    suspend fun isAvailable(): Boolean =
        probeAvailability { runCommandInternal("id", timeoutMs = PROBE_TIMEOUT_MS) }

    private suspend fun probeAvailability(probe: suspend () -> SuResult): Boolean =
        withContext(Dispatchers.IO) {
            cachedAvailable?.let { return@withContext it }
            availabilityMutex.withLock {
                cachedAvailable?.let { return@withLock it }
                val generation = cacheGeneration.get()
                val result = probe()
                val ok = result.exitCode == 0 && result.stdout.contains("uid=0")
                // A UI refresh may invalidate the cache while the process is
                // blocked in su. Do not publish that stale result into the new
                // generation; the next caller will perform one fresh probe.
                if (cacheGeneration.get() == generation) {
                    cachedAvailable = ok
                    if (!ok) {
                        Log.d(
                            TAG,
                            "su unavailable: exit=" + result.exitCode +
                                " stdout=" + result.stdout.take(120)
                        )
                    }
                }
                ok
            }
        }

    fun resetCache() {
        cacheGeneration.incrementAndGet()
        cachedAvailable = null
    }

    fun resetCacheIfSuLikelyFailed(exitCode: Int) {
        if (exitCode == 127 || exitCode == -1) cachedAvailable = null
    }

    internal fun peekCachedAvailable(): Boolean? = cachedAvailable

    internal fun setCachedAvailableForTest(value: Boolean?) {
        cachedAvailable = value
    }

    /** Test-only single-flight hook; production callers use [isAvailable]. */
    internal suspend fun probeAvailabilityForTest(probe: suspend () -> SuResult): Boolean =
        probeAvailability(probe)

    suspend fun runCommand(commandLine: String): SuResult =
        runCommandInternal(commandLine, timeoutMs = CMD_TIMEOUT_MS)

    /** Pipe stdinText into su and return the exit code. */
    suspend fun runShell(stdinText: String): Int = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su").redirectErrorStream(true).start()
        } catch (e: IOException) {
            Log.d(TAG, "runShell: su not on PATH (" + e.message + ")")
            return@withContext 127
        }

        val drainer = startDrainer(proc, capture = null)
        val writerFailure = AtomicReference<Throwable?>(null)
        val writer = Thread({
            try {
                OutputStreamWriter(proc.outputStream).use {
                    it.write(stdinText)
                    if (!stdinText.endsWith("\n")) it.write("\n")
                    it.write("exit\n")
                }
            } catch (t: Throwable) {
                writerFailure.set(t)
            }
        }, "OpenLumen-su-writer").apply {
            isDaemon = true
            start()
        }

        val completed = waitForProcessExit(proc, CMD_TIMEOUT_MS)
        val exit = when {
            !completed -> {
                Log.w(TAG, "runShell timed out after " + CMD_TIMEOUT_MS + "ms; destroying process")
                terminateProcess(proc)
                -1
            }
            writerFailure.get() != null -> {
                Log.d(TAG, "runShell: stdin write failed (" + writerFailure.get()?.message + ")")
                -1
            }
            else -> proc.exitValue()
        }
        closeProcessStreams(proc)
        joinQuietly(writer)
        joinQuietly(drainer)
        exit
    }

    private suspend fun runCommandInternal(
        cmdline: String,
        timeoutMs: Long
    ): SuResult = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su", "-c", cmdline)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            return@withContext SuResult(127, "", "su not on PATH: " + e.message)
        }

        val capture = BoundedCapture(MAX_CAPTURED_OUTPUT_CHARS)
        val drainer = startDrainer(proc, capture)
        val completed = waitForProcessExit(proc, timeoutMs)
        val exit = if (completed) {
            proc.exitValue()
        } else {
            Log.w(TAG, "su command timed out after " + timeoutMs + "ms: " + cmdline.take(120))
            terminateProcess(proc)
            -1
        }
        closeProcessStreams(proc)
        joinQuietly(drainer)
        if (capture.truncated) {
            Log.w(
                TAG,
                "su output truncated at " + MAX_CAPTURED_OUTPUT_CHARS +
                    " chars: " + cmdline.take(60)
            )
        }
        SuResult(exit, capture.value(), "")
    }

    private fun startDrainer(proc: Process, capture: BoundedCapture?): Thread =
        Thread({
            try {
                proc.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        capture?.append(buffer, count)
                    }
                }
            } catch (_: IOException) {
                // The process is commonly closed by the timeout cleanup path.
            }
        }, "OpenLumen-su-drain").apply {
            isDaemon = true
            start()
        }

    /**
     * Kept internal so JVM tests can prove the process-level wait contract
     * without invoking a real root shell.
     */
    internal fun waitForProcessExit(process: Process, timeoutMs: Long): Boolean =
        try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private fun terminateProcess(process: Process) {
        if (!process.isAlive) return
        runCatching { process.destroy() }
        val exited = runCatching {
            process.waitFor(CLEANUP_WAIT_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!exited && process.isAlive) {
            runCatching { process.destroyForcibly() }
            runCatching { process.waitFor(CLEANUP_WAIT_MS, TimeUnit.MILLISECONDS) }
        }
        closeProcessStreams(process)
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun joinQuietly(thread: Thread) {
        try {
            thread.join(CLEANUP_WAIT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private class BoundedCapture(private val maxChars: Int) {
        private val text = StringBuilder()
        @Volatile var truncated: Boolean = false
            private set

        @Synchronized
        fun append(bytes: ByteArray, count: Int) {
            if (text.length >= maxChars) {
                truncated = true
                return
            }
            val chunk = String(bytes, 0, count, StandardCharsets.UTF_8)
            val room = maxChars - text.length
            text.append(chunk, 0, minOf(room, chunk.length))
            if (chunk.length > room) truncated = true
        }

        @Synchronized
        fun value(): String = text.toString().trim()
    }

    private const val MAX_CAPTURED_OUTPUT_CHARS = 16 * 1024

    data class SuResult(val exitCode: Int, val stdout: String, val stderr: String)
}
