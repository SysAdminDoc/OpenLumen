package com.openlumen.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.engines.SecureSettingsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Watches the `Settings.Secure` rows the secure-settings driver currently
 * claims, and lets go of any the user or another app changes underneath it.
 *
 * These rows are not private state. Quick Settings writes them, the system's
 * own sunset schedule writes them, Digital Wellbeing writes them at bedtime,
 * and so does every other app holding `WRITE_SECURE_SETTINGS`. Until now
 * OpenLumen only asked who owned a row at clear time, which is too late: every
 * ramp step and slider move re-writes a tinted row and records the value it
 * just wrote as the one it owns, so the user's change was overwritten and the
 * evidence of it erased in the same breath. The same defect is visible from
 * the other side in DetoxDroid's tracker, which writes the correction rows
 * under the same grant and has eight open reports of grayscale sticking on,
 * refusing to switch off, or surviving a reboot.
 *
 * Registration follows what the engine actually holds rather than whether it is
 * selected: a session that never dims does not own the Extra Dim rows and has
 * no business watching them.
 */
internal class SecureSettingsWatch(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onReleased: suspend (Map<String, String>) -> Unit
) {
    private var observer: ContentObserver? = null

    /** Keys the live observer covers, so an unchanged set does not re-register. */
    private var watched: Set<String> = emptySet()

    /**
     * Bring the registration in line with what [engine] owns right now. Safe to
     * call after every apply; it does nothing when the owned set has not moved.
     */
    fun sync(engine: ColorEngine?) {
        val secure = engine as? SecureSettingsEngine ?: return stop()
        val keys = secure.ownedKeys()
        if (keys.isEmpty()) return stop()
        if (observer != null && keys == watched) return
        stop()
        val handler = Handler(Looper.getMainLooper())
        val created = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Reads a content provider and writes the ownership file, so it
                // cannot run on the callback thread.
                scope.launch(Dispatchers.IO) {
                    val released = secure.releaseForeignRows(context)
                    if (released.isEmpty()) return@launch
                    onReleased(released)
                    sync(secure)
                }
            }
        }
        val cr = context.contentResolver
        keys.forEach { key ->
            runCatching {
                cr.registerContentObserver(Settings.Secure.getUriFor(key), false, created)
            }
        }
        observer = created
        watched = keys
    }

    fun stop() {
        val current = observer ?: return
        runCatching { context.contentResolver.unregisterContentObserver(current) }
        observer = null
        watched = emptySet()
    }

    /** Which rows are being watched, for tests and the driver report. */
    fun watchedKeys(): Set<String> = watched
}
