package com.openlumen.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Minimal `su` wrapper. We never bundle Magisk/libsu — most users either have
 * Magisk-managed `su` in PATH or they don't have root at all. Probing is cheap.
 *
 * Hardening notes:
 *  - `redirectErrorStream(true)` merges stderr into stdout, eliminating the classic
 *    "pipe buffer full on the un-read stream" deadlock.
 *  - Stream readers are always closed via `use {}`.
 *  - Every command has a wall-clock timeout; the subprocess is forcibly destroyed if exceeded.
 *  - `isAvailable()` caches the first probe result and tolerates su-prompts that take
 *    a few seconds to grant the first time.
 */
object Su {
    private const val TAG = "OpenLumen/Su"
    private const val PROBE_TIMEOUT_MS = 8_000L  // first-time Magisk grant can take a beat
    private const val CMD_TIMEOUT_MS = 4_000L

    @Volatile private var cachedAvailable: Boolean? = null

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        cachedAvailable?.let { return@withContext it }
        // Single probe — was previously calling `id` twice which doubled latency
        // and could prompt Magisk twice on first run.
        val probe = runCommandInternal("id", timeoutMs = PROBE_TIMEOUT_MS)
        val ok = probe.exitCode == 0 && probe.stdout.contains("uid=0")
        cachedAvailable = ok
        if (!ok) {
            Log.d(TAG, "su unavailable: exit=${probe.exitCode} stdout=${probe.stdout.take(120)}")
        }
        ok
    }

    /** Invalidate the cached availability — call when the user toggles drivers. */
    fun resetCache() { cachedAvailable = null }

    /**
     * Hook for engine `invalidateOnFailure` paths. If the failure looks like
     * the `su` binary itself is gone — exit 127 (not on PATH) or -1 (timeout
     * destroying the subprocess) — drop the cached availability so the next
     * engine call re-probes. Without this, a user who loses Magisk root
     * mid-session keeps a cached `available = true`, the engine's own probe
     * fails, `apply` silently no-ops, and the engine still appears
     * "available" in the Driver tab. Other exit codes (a single failed
     * `service call` write, a permission-denied on a sysfs node, etc.) do
     * NOT invalidate — those are engine-specific, not su-wide.
     */
    fun resetCacheIfSuLikelyFailed(exitCode: Int) {
        if (exitCode == 127 || exitCode == -1) cachedAvailable = null
    }

    /** Test-only peek at the cache slot — null when not yet probed. */
    internal fun peekCachedAvailable(): Boolean? = cachedAvailable

    /** Test-only setter to seed the cache without going through a real su probe. */
    internal fun setCachedAvailableForTest(value: Boolean?) { cachedAvailable = value }

    suspend fun runCommand(vararg cmd: String): SuResult =
        runCommandInternal(cmd.joinToString(" "), timeoutMs = CMD_TIMEOUT_MS)

    /** Pipe [stdinText] into `su -c sh` and return the exit code. */
    suspend fun runShell(stdinText: String): Int = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su").redirectErrorStream(true).start()
        } catch (e: IOException) {
            Log.d(TAG, "runShell: su not on PATH (${e.message})")
            return@withContext 127
        }
        // Drain stdout in parallel with the stdin write so a verbose
        // script can't deadlock the subshell waiting for us to read its
        // pipe before it can accept more input. We spawn the drain on a
        // daemon thread because Dispatchers.IO + a child coroutine would
        // tie shutdown ordering to the parent scope, which is harder to
        // reason about than a self-contained drainer.
        //
        // We discard the output entirely (callers of runShell only care
        // about the exit code) and bound the read with a fixed buffer so
        // a misbehaving script writing MBs of output inside the timeout
        // window can't spike memory before the process is killed.
        val drainer = Thread({
            try {
                proc.inputStream.use { input ->
                    val buf = ByteArray(4096)
                    while (input.read(buf) >= 0) { /* discard */ }
                }
            } catch (_: IOException) { /* process already gone */ }
        }, "OpenLumen-su-drain").apply { isDaemon = true; start() }
        val exit = withTimeoutOrNull(CMD_TIMEOUT_MS) {
            // Write the script, then close stdin so the subshell sees EOF and exits.
            try {
                OutputStreamWriter(proc.outputStream).use {
                    it.write(stdinText)
                    if (!stdinText.endsWith("\n")) it.write("\n")
                    it.write("exit\n")
                }
            } catch (e: IOException) {
                Log.d(TAG, "runShell: stdin write failed (${e.message})")
                proc.destroyForcibly()
                return@withTimeoutOrNull -1
            }
            proc.waitFor()
        } ?: run {
            Log.w(TAG, "runShell timed out after ${CMD_TIMEOUT_MS}ms; destroying process")
            proc.destroyForcibly()
            -1
        }
        // Best-effort wait for the drainer so the process's file
        // descriptors are released promptly.
        try { drainer.join(500) } catch (_: InterruptedException) { /* ignore */ }
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
            return@withContext SuResult(127, "", "su not on PATH: ${e.message}")
        }
        val combined = StringBuilder()
        var truncated = false
        val exit = withTimeoutOrNull(timeoutMs) {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    // forEachLine reads each line as a String of unbounded
                    // length; a misbehaving su writing one giant line (or many
                    // small lines totaling MBs) before the timeout fires could
                    // wedge the process in StringBuilder.append. Cap captured
                    // output and drain the rest into /dev/null.
                    val buffer = CharArray(4096)
                    while (true) {
                        val n = reader.read(buffer)
                        if (n < 0) break
                        if (combined.length < MAX_CAPTURED_OUTPUT_CHARS) {
                            val room = MAX_CAPTURED_OUTPUT_CHARS - combined.length
                            combined.append(buffer, 0, minOf(n, room))
                            if (combined.length >= MAX_CAPTURED_OUTPUT_CHARS) {
                                truncated = true
                            }
                        } else {
                            truncated = true
                        }
                    }
                }
            } catch (_: IOException) { /* process already gone */ }
            proc.waitFor()
        } ?: run {
            Log.w(TAG, "su command timed out after ${timeoutMs}ms: ${cmdline.take(120)}")
            proc.destroyForcibly()
            -1
        }
        if (truncated) {
            Log.w(TAG, "su output truncated at $MAX_CAPTURED_OUTPUT_CHARS chars: ${cmdline.take(60)}")
        }
        SuResult(exit, combined.toString().trim(), "")
    }

    /**
     * Hard cap on captured `su` output. 16 KiB is generous for our use cases
     * (success exit code + a short status line is the norm) and small enough
     * to bound worst-case memory even if every command spammed output.
     */
    private const val MAX_CAPTURED_OUTPUT_CHARS = 16 * 1024

    /**
     * @param exitCode 0 on success, 127 if `su` isn't on PATH, -1 on timeout, otherwise
     *  whatever the subprocess returned.
     * @param stdout combined stdout+stderr (we use redirectErrorStream)
     * @param stderr always empty since stderr is merged.
     */
    data class SuResult(val exitCode: Int, val stdout: String, val stderr: String)
}
