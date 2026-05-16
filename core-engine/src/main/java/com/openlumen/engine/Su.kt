package com.openlumen.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Minimal `su` wrapper. We never bundle Magisk/libsu — most users either have
 * Magisk-managed `su` in PATH or they don't have root at all. Probing is cheap.
 */
object Su {
    @Volatile private var cachedAvailable: Boolean? = null

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        cachedAvailable?.let { return@withContext it }
        val ok = runCommand("id").outExitCode == 0 &&
            runCommand("id").stdout.contains("uid=0")
        cachedAvailable = ok
        ok
    }

    suspend fun runCommand(vararg cmd: String): SuResult = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su", "-c", cmd.joinToString(" "))
                .redirectErrorStream(false)
                .start()
        } catch (_: Throwable) {
            return@withContext SuResult(127, "", "su not on PATH")
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val outReader = BufferedReader(InputStreamReader(proc.inputStream))
        val errReader = BufferedReader(InputStreamReader(proc.errorStream))
        val exit = withTimeoutOrNull(3_000L) {
            outReader.forEachLine { out.appendLine(it) }
            errReader.forEachLine { err.appendLine(it) }
            proc.waitFor()
        } ?: run {
            proc.destroy()
            -1
        }
        SuResult(exit, out.toString().trim(), err.toString().trim())
    }

    /** Pipe [stdinText] into `su -c sh` and return the exit code. */
    suspend fun runShell(stdinText: String): Int = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su").redirectErrorStream(true).start()
        } catch (_: Throwable) {
            return@withContext 127
        }
        OutputStreamWriter(proc.outputStream).use { it.write(stdinText); it.write("\nexit\n") }
        proc.waitFor()
    }

    data class SuResult(val outExitCode: Int, val stdout: String, val stderr: String)
}
