package com.openlumen.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
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
 * - Concurrent-safe enough for the foreground service + UI to share.
 *   We don't hold a Mutex — append() is single-flush per call and the
 *   underlying File.appendText is atomic at the FS layer for short
 *   writes.
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
            f.appendText(line + "\n")
            if (f.length() > MAX_BYTES) trimHead(f)
        }.onFailure { Log.w(TAG, "log write failed: ${it.message}") }
    }

    fun read(context: Context): String {
        val f = File(context.filesDir, FILENAME)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(context: Context): Boolean {
        val f = File(context.filesDir, FILENAME)
        return !f.exists() || f.delete()
    }

    /** For tests: capture writes without touching the filesystem. */
    internal fun installTestWriter(writer: (String) -> Unit) { testWriter.set(writer) }
    internal fun clearTestWriter() { testWriter.set(null) }

    internal fun formatLine(level: Level, category: Category, message: String): String =
        // Trim message to a sane width so a runaway log can't blow the size cap
        // in one line.
        "${Instant.now()} ${level.name} ${category.name} ${message.take(512)}"

    private fun trimHead(f: File) {
        val raw = f.readBytes()
        val keep = if (raw.size <= TRIM_TO_BYTES) raw
                   else raw.copyOfRange(raw.size - TRIM_TO_BYTES, raw.size)
        f.writeBytes(keep)
    }
}
