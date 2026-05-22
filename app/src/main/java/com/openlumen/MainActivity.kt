package com.openlumen

import android.Manifest
import android.content.SharedPreferences
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
    ) { granted ->
        // Record that the user has been asked. We don't gate on the granted
        // value (a permanent deny still flips this); the next launch will
        // skip the prompt and the in-app driver report already surfaces the
        // denied state for users who want to revisit it via system settings.
        notificationPromptPrefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
    }

    private val notificationPromptPrefs: SharedPreferences
        get() = getSharedPreferences(PROMPT_PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent { OpenLumenRoot() }
    }

    /**
     * Ask for POST_NOTIFICATIONS the first time the user opens the app on
     * Android 13+. Subsequent launches do nothing — the foreground service
     * runs without notifications anyway (Android only hides the channel
     * UI), so a permanent deny is recoverable. Spamming the dialog on
     * every launch would just be noise, so we record one-shot via a tiny
     * SharedPreferences flag instead of relying on the previous code's
     * implicit "the system silently no-ops on permanent deny" assumption
     * — which was true but produced an unnecessary launcher dispatch per
     * Activity create.
     *
     * If the system reports the user can be asked again
     * (`shouldShowRequestPermissionRationale`), we re-prompt regardless of
     * our local flag — a "deny once" user changing their mind shouldn't
     * have to dig through settings to grant.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val rationaleAvailable = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.POST_NOTIFICATIONS
        )
        val alreadyAsked = notificationPromptPrefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        if (alreadyAsked && !rationaleAvailable) {
            // We've already asked AND the system says no further prompt
            // will surface — quietly let it go.
            return
        }
        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val PROMPT_PREFS = "openlumen-prompts"
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
    }
}
