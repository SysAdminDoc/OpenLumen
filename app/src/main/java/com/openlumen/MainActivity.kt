package com.openlumen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openlumen.ui.OpenLumenRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* outcome is read at next launch via checkSelfPermission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent { OpenLumenRoot() }
    }

    /**
     * Ask for POST_NOTIFICATIONS the first time the user opens the app on
     * Android 13+, but stop asking once the system reports "user permanently
     * denied". The foreground service still runs without notifications —
     * Android only hides the channel UI — so a permanent deny is recoverable,
     * not catastrophic. Spamming the dialog on every launch would just be
     * noise.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        // If shouldShowRequestPermissionRationale returns false AND the
        // permission is denied, the user has chosen "Don't allow" twice
        // (or selected "Don't ask again"). Honor that choice. The Driver
        // screen's diagnostic report already surfaces the denied state so
        // a user who changes their mind can find the system settings path.
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            // First request still goes through — shouldShowRequestPermissionRationale
            // returns false the very first time too. We let the launcher itself
            // be the gate: on second-permanent-deny the system silently no-ops.
            // No state to track here; the launcher is idempotent.
        }
        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
