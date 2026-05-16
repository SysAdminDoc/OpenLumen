package com.openlumen

import android.content.Context
import java.io.File
import java.time.Instant

/**
 * Local-only uncaught-exception logger. Writes to `filesDir/crash.log` (kept under
 * 64 KB by truncating to last ~32 KB when exceeded). NEVER sends anything anywhere
 * over the network — this app has no INTERNET permission. The user can open the log
 * via About → "View crash log" and share manually if they want to file an issue.
 */
object CrashLogger {

    private const val MAX_BYTES = 64 * 1024L
    private const val TRIM_TO_BYTES = 32 * 1024
    private const val FILENAME = "crash.log"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String {
        val f = File(context.filesDir, FILENAME)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(context: Context): Boolean {
        val f = File(context.filesDir, FILENAME)
        return !f.exists() || f.delete()
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val f = File(context.filesDir, FILENAME)
        val header = "\n=== ${Instant.now()} on thread ${thread.name} ===\n"
        f.appendText(header)
        f.appendText(throwable.stackTraceToString())
        if (f.length() > MAX_BYTES) trimHead(f)
    }

    /** Keep the most recent ~TRIM_TO_BYTES bytes by reading the tail and rewriting. */
    private fun trimHead(f: File) {
        val raw = f.readBytes()
        val keep = if (raw.size <= TRIM_TO_BYTES) raw
                   else raw.copyOfRange(raw.size - TRIM_TO_BYTES, raw.size)
        f.writeBytes(keep)
    }
}
