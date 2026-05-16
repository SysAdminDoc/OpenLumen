package com.openlumen.engine.engines

import android.app.Service
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
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
 */
class OverlayEngine : ColorEngine {
    override val kind = EngineKind.OVERLAY

    @Volatile private var hostView: View? = null
    @Volatile private var hostWm: WindowManager? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
    }

    /**
     * Must be called from a Service or Activity context that holds a window token capable
     * of TYPE_APPLICATION_OVERLAY. The app's LumenService handles this on apply().
     */
    fun installView(serviceContext: Context, initial: LumenMatrix) {
        if (hostView != null) return
        val view = View(serviceContext).apply {
            setBackgroundColor(initial.toOverlayArgb())
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
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.TOP }
        val wm = serviceContext.getSystemService(Service.WINDOW_SERVICE) as WindowManager
        wm.addView(view, lp)
        hostView = view
        hostWm = wm
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.Main) {
        val v = hostView ?: run {
            installView(context, matrix)
            return@withContext
        }
        v.setBackgroundColor(matrix.toOverlayArgb())
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.Main) {
        val v = hostView
        val wm = hostWm
        if (v != null && wm != null) {
            runCatching { wm.removeViewImmediate(v) }
        } else {
            hostView?.setBackgroundColor(Color.TRANSPARENT)
        }
        hostView = null
        hostWm = null
    }
}
