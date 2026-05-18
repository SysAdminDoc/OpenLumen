package com.openlumen

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Local-only uncaught-exception logger. Writes to `filesDir/crash.log` (kept under
 * 64 KB by truncating to last ~32 KB when exceeded). NEVER sends anything anywhere
 * over the network — this app has no INTERNET permission. The user can open the log
 * via About → "View crash log" and share manually if they want to file an issue.
 *
 * Concurrency: an uncaught-exception handler runs on whichever thread threw, so
 * two threads can race into [writeCrash] during a multi-threaded blow-up. The
 * append + size check + trim must be one critical section or the survivor's write
 * can be overwritten by the loser's trim. We synchronize on [writeLock]; the
 * filesystem already serializes individual writes.
 */
object CrashLogger {

    private const val MAX_BYTES = 64 * 1024L
    private const val TRIM_TO_BYTES = 32 * 1024
    private const val FILENAME = "crash.log"
    @Volatile private var installed = false
    private val writeLock = Any()

    fun install(context: Context) {
        if (installed) return
        synchronized(writeLock) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { writeCrash(context, thread, throwable) }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun read(context: Context): String {
        val f = File(context.filesDir, FILENAME)
        // Match the diagnostic-log reader: a brief writeLock acquire so the
        // dialog never observes a partial trim rewrite. CrashLogger writes
        // only fire during an uncaught exception, but if a multi-threaded
        // crash interleaves with a "View crash log" tap, the user could
        // see torn bytes without this.
        synchronized(writeLock) {
            return if (f.exists()) f.readText() else ""
        }
    }

    fun clear(context: Context): Boolean {
        val f = File(context.filesDir, FILENAME)
        synchronized(writeLock) {
            return !f.exists() || f.delete()
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val f = File(context.filesDir, FILENAME)
        val header = "\n=== ${Instant.now()} on thread ${thread.name} ===\n"
        val body = throwable.stackTraceToString()
        synchronized(writeLock) {
            f.appendText(header)
            f.appendText(body)
            if (f.length() > MAX_BYTES) trimHeadLocked(f)
        }
    }

    /** Keep the most recent ~TRIM_TO_BYTES bytes. Caller must hold [writeLock]. */
    private fun trimHeadLocked(f: File) {
        val totalLength = f.length()
        if (totalLength <= TRIM_TO_BYTES) return
        val cutFrom = totalLength - TRIM_TO_BYTES
        val tail = ByteArray(TRIM_TO_BYTES)
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(cutFrom)
            raf.readFully(tail)
        }
        f.writeBytes(tail)
    }
}
