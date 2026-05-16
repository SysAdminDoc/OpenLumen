package com.openlumen.engine

import android.util.Log

/**
 * Thin android.util.Log wrapper. Every class that wants diagnostics declares a tag like
 * `"OpenLumen/LumenService"` and uses these helpers. Centralizing here lets us flip a
 * single boolean to silence all logging in a future "release-stripped" build, and keeps
 * the tag length under android.util.Log's 23-char limit by construction.
 *
 * No INTERNET permission is requested anywhere in this app, so these logs live and die
 * inside the device — they never reach a backend.
 */
internal object EngineLog {
    private const val ROOT = "OpenLumen"

    /** Build a tag like "OpenLumen/Foo" — caller passes just "Foo". */
    fun tag(suffix: String): String {
        val raw = "$ROOT/$suffix"
        return if (raw.length <= 23) raw else raw.substring(0, 23)
    }

    inline fun d(tag: String, msg: () -> String) {
        if (Log.isLoggable(tag, Log.DEBUG)) Log.d(tag, msg())
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }
}
