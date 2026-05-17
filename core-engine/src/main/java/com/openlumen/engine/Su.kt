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
        val drainer = Thread({
            try {
                proc.inputStream.bufferedReader().use { it.readText() }
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
        val exit = withTimeoutOrNull(timeoutMs) {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    reader.forEachLine { combined.appendLine(it) }
                }
            } catch (_: IOException) { /* process already gone */ }
            proc.waitFor()
        } ?: run {
            Log.w(TAG, "su command timed out after ${timeoutMs}ms: ${cmdline.take(120)}")
            proc.destroyForcibly()
            -1
        }
        SuResult(exit, combined.toString().trim(), "")
    }

    /**
     * @param exitCode 0 on success, 127 if `su` isn't on PATH, -1 on timeout, otherwise
     *  whatever the subprocess returned.
     * @param stdout combined stdout+stderr (we use redirectErrorStream)
     * @param stderr always empty since stderr is merged.
     */
    data class SuResult(val exitCode: Int, val stdout: String, val stderr: String)
}
