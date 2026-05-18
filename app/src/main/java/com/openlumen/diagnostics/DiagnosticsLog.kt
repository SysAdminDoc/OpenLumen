package com.openlumen.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Bounded, append-only local event log. Tied to roadmap candidate **C53**
 * (Structured log viewer) and feeds the diagnostics bundle from **C52**.
 *
 * Design rules:
 * - File-backed, app-private. Lives at `filesDir/diagnostics.log`.
 * - One event per line. No JSON; each line is grep-friendly text:
 *   `<ISO-8601 instant> <LEVEL> <category> <message>`
 * - Size-capped at [MAX_BYTES] (~64 KB). When exceeded, the head is
 *   trimmed to keep the most recent [TRIM_TO_BYTES] (~32 KB).
 * - Concurrent-safe across the foreground service, UI, tile, widget
 *   receiver, and boot receivers. The append + size check + trim is one
 *   critical section protected by [writeLock]; without that, two callers
 *   could observe an oversized file, read it, and write back two
 *   truncated copies that lose each other's interleaved lines. The
 *   intra-process lock is enough today because all callers live in the
 *   same process, but the trim itself rewrites the whole file so
 *   serializing is still cheap.
 * - Never persists PII. Callers should not pass user-entered text
 *   (locations, file URIs) into the message argument.
 * - Like CrashLogger, the log NEVER leaves the device unless the user
 *   manually exports/shares it.
 */
object DiagnosticsLog {

    private const val FILENAME = "diagnostics.log"
    private const val MAX_BYTES = 64L * 1024
    private const val TRIM_TO_BYTES = 32 * 1024
    private const val TAG = "OpenLumen/Diag"

    // Process-wide write lock. Reads are *intentionally* not locked: the file
    // is opened in append mode, so a concurrent read may see a partial trail
    // line but never a corrupted prefix. The dialog renders raw bytes, which
    // is tolerable for a diagnostics surface.
    private val writeLock = Any()

    /** Bounded test-mode override so unit tests can run without a real Context. */
    private val testWriter = AtomicReference<((String) -> Unit)?>(null)

    enum class Level { DEBUG, INFO, WARN, ERROR }

    enum class Category {
        SERVICE,        // service lifecycle (onCreate, startForeground, onDestroy)
        ENGINE,         // engine apply / clear / probe
        SCHEDULE,       // schedule transitions and alarms
        SENSOR,         // light sensor lifecycle
        PREFS,          // preference import / export / migration
        WIDGET,         // widget refresh / click
        TILE,           // QS tile actions
        PROFILE         // preset switches and previous-restore
    }

    fun log(context: Context, level: Level, category: Category, message: String) {
        val line = formatLine(level, category, message)
        testWriter.get()?.let {
            it.invoke(line)
            return
        }
        runCatching {
            val f = File(context.filesDir, FILENAME)
            synchronized(writeLock) {
                f.appendText(line + "\n")
                if (f.length() > MAX_BYTES) trimHeadLocked(f)
            }
        }.onFailure { Log.w(TAG, "log write failed: ${it.message}") }
    }

    fun read(context: Context): String {
        val f = File(context.filesDir, FILENAME)
        // The reader briefly acquires [writeLock] so we never observe a
        // partial trim rewrite. The dialog is opened by user action and
        // isn't hot, so the contention cost is negligible — and without it
        // a "View diagnostics log" tap during heavy service activity could
        // render torn bytes mid-trim.
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

    /** For tests: capture writes without touching the filesystem. */
    internal fun installTestWriter(writer: (String) -> Unit) { testWriter.set(writer) }
    internal fun clearTestWriter() { testWriter.set(null) }

    internal fun formatLine(level: Level, category: Category, message: String): String =
        // Trim message to a sane width so a runaway log can't blow the size cap
        // in one line.
        "${Instant.now()} ${level.name} ${category.name} ${message.take(512)}"

    /**
     * Rewrite [f] to keep at most [TRIM_TO_BYTES] of tail content. Uses
     * RandomAccessFile so we don't allocate the whole-file byte buffer on
     * the heap when the cap is exceeded by a single append.
     *
     * Caller must hold [writeLock].
     */
    private fun trimHeadLocked(f: File) {
        val totalLength = f.length()
        if (totalLength <= TRIM_TO_BYTES) return
        val cutFrom = totalLength - TRIM_TO_BYTES
        // Read the tail into memory, then atomically rename a sibling temp
        // file over the original. File.writeBytes() truncates in place, which
        // is fine but less crash-safe than a rename — pick the simpler form
        // since the worst case is losing the most recent TRIM_TO_BYTES of
        // log on a power loss, which we can survive.
        val tail = ByteArray(TRIM_TO_BYTES)
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(cutFrom)
            raf.readFully(tail)
        }
        f.writeBytes(tail)
    }
}
