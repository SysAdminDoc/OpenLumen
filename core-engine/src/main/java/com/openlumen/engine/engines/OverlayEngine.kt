package com.openlumen.engine.engines

import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal rootless fallback. Adds a `TYPE_APPLICATION_OVERLAY` View that paints a
 * single tinted color over every other window. Cannot reach below the system minimum
 * brightness in framebuffer terms — the overlay is layered above status bar / nav bar
 * but compositor still drives the panel at the user's brightness slider value.
 *
 * Android 12+ untrusted-touch rule caps overlay alpha at ~0.8 when FLAG_NOT_TOUCHABLE
 * is set. We respect that cap; users wanting harder dim need root.
 *
 * The overlay View itself is owned and added/removed by the foreground service that
 * holds the engine — it must call [installView] from the service context (token).
 *
 * **Lifetime gotcha.** Hilt provides the engine as a singleton, so the same instance
 * survives a foreground-service process kill / restart. On a kill the system rips
 * the window token but [hostView] / [hostWm] stay non-null in the singleton. We
 * detect this on every install/apply by checking [View.isAttachedToWindow]; a
 * detached view triggers a clean re-install rather than a silent no-op.
 */
class OverlayEngine : ColorEngine {
    override val kind = EngineKind.OVERLAY

    private val tag = "OpenLumen/Overlay"

    // All mutation is on Dispatchers.Main from a single Service — @Volatile is defensive
    // for the rare case `apply()` is called from a worker thread by accident.
    @Volatile private var hostView: View? = null
    @Volatile private var hostWm: WindowManager? = null
    @Volatile private var lastAppliedArgb: Int = 0
    private val viewLock = Any()

    override suspend fun isAvailable(context: Context): Boolean = onMain {
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
    }

    /**
     * Must be called from a Service or Activity context that holds a window token capable
     * of TYPE_APPLICATION_OVERLAY. Returns false if the overlay permission isn't granted
     * or if `addView` throws (e.g. token revoked). Idempotent and safe to call after a
     * service-process restart: a stale, detached `hostView` from the previous service
     * incarnation is dropped first so the new service's window token is the one
     * registered with WindowManager.
     *
     * WindowManager mutations must occur on the main thread. We post the
     * work onto the main looper synchronously rather than asserting the
     * caller's thread, so service code that ends up here from a
     * non-Main coroutine still installs cleanly.
     */
    fun installView(serviceContext: Context, initial: LumenMatrix): Boolean {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(serviceContext)) {
            Log.w(tag, "installView: SYSTEM_ALERT_WINDOW not granted; overlay engine disabled")
            return false
        }
        return runOnMain { synchronized(viewLock) { installViewLocked(serviceContext, initial) } }
    }

    /** True if the cached host view is still attached to a window manager. */
    private fun isHostAttached(): Boolean {
        val v = hostView ?: return false
        return v.isAttachedToWindow
    }

    private fun discardStaleHostLocked() {
        val v = hostView ?: return
        val wm = hostWm
        if (wm != null) {
            // Best-effort: the underlying window token is likely already
            // gone if we're here, so a throw is expected. Swallow it.
            runCatching { wm.removeViewImmediate(v) }
        }
        hostView = null
        hostWm = null
        lastAppliedArgb = 0
    }

    private fun installViewLocked(serviceContext: Context, initial: LumenMatrix): Boolean {
        if (hostView != null) {
            if (isHostAttached()) return true
            // Carry-over from a previous service incarnation; reinstall fresh.
            Log.d(tag, "installView: discarding stale detached view from previous session")
            discardStaleHostLocked()
        }
        val initialArgb = initial.toOverlayArgb()
        val view = View(serviceContext).apply {
            setBackgroundColor(initialArgb)
            isFocusable = false
            isClickable = false
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            // Cover the display cutout so the tint visibly matches the
            // rest of the screen on notch/punch-hole devices (API 28+).
            // Older APIs don't have cutouts to worry about.
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        val wm = serviceContext.getSystemService(Service.WINDOW_SERVICE) as? WindowManager
            ?: run {
                Log.e(tag, "installViewLocked: WINDOW_SERVICE unavailable")
                return false
            }
        return try {
            wm.addView(view, lp)
            hostView = view
            hostWm = wm
            lastAppliedArgb = initialArgb
            true
        } catch (t: Throwable) {
            Log.e(tag, "wm.addView failed: ${t.message}", t)
            false
        }
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): Unit = onMain {
        val argb = matrix.toOverlayArgb()
        val installed = synchronized(viewLock) {
            val v = hostView
            if (v != null && isHostAttached()) {
                if (argb != lastAppliedArgb) {
                    v.setBackgroundColor(argb)
                    lastAppliedArgb = argb
                }
                true
            } else {
                // Either we never installed, or the previous service's view
                // got torn down with the process. Already on Main, so go
                // through the locked installer directly.
                if (v != null) discardStaleHostLocked()
                installViewLocked(context, matrix)
            }
        }
        if (!installed) {
            Log.w(tag, "apply: installView failed; tint will not be visible")
        }
    }

    override suspend fun clear(context: Context): Unit = onMain {
        synchronized(viewLock) {
            val v = hostView
            val wm = hostWm
            if (v != null && wm != null) {
                runCatching { wm.removeViewImmediate(v) }
                    .onFailure { Log.w(tag, "removeViewImmediate failed: ${it.message}") }
            }
            hostView = null
            hostWm = null
            lastAppliedArgb = 0
        }
    }

    /**
     * If we're already on the Android main thread, run [block] inline so
     * a caller that wrapped us in `runBlocking` on Main doesn't deadlock
     * waiting for the Main dispatcher to drain a parked Looper. The
     * `LumenService.onDestroy` shutdown path is the canonical example —
     * `runBlocking { engine.clear(this) }` from Service.onDestroy() would
     * otherwise wait the full timeout because the Main Looper that owns
     * the dispatcher is parked inside runBlocking.
     */
    private suspend inline fun <T> onMain(crossinline block: () -> T): T =
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            withContext(Dispatchers.Main) { block() }
        }

    /**
     * Run [block] on the main looper and block the calling thread until it
     * completes. We deliberately don't use a coroutine `withContext` here
     * because `installView` is callable from non-suspend code (the service
     * call site is inside `applyMutex.withLock`, which doesn't switch
     * dispatchers).
     */
    private inline fun runOnMain(crossinline block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val handler = Handler(Looper.getMainLooper())
        val lock = java.util.concurrent.CountDownLatch(1)
        var result = false
        handler.post {
            try { result = block() } finally { lock.countDown() }
        }
        // Bounded wait so a wedged main thread can't pin the caller forever.
        return if (lock.await(2, java.util.concurrent.TimeUnit.SECONDS)) result
               else { Log.w(tag, "installView: timed out waiting for main thread"); false }
    }
}
