package com.openlumen

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openlumen.service.BlockedForegroundStartRecovery
import com.openlumen.service.LumenServiceStarter
import com.openlumen.ui.OpenLumenRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var blockedStartPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedStartPending = BlockedForegroundStartRecovery.isPending(intent)
        enableEdgeToEdge()
        setContent { OpenLumenRoot() }
    }

    override fun onResume() {
        super.onResume()
        retryBlockedStartIfReady()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedStartPending = BlockedForegroundStartRecovery.isPending(intent)
        retryBlockedStartIfReady()
    }

    private fun retryBlockedStartIfReady() {
        if (!blockedStartPending || !BlockedForegroundStartRecovery.shouldRetry(
                intent,
                Settings.canDrawOverlays(this)
            )
        ) {
            return
        }
        blockedStartPending = false
        // The user is here, so these preferences are this install's. The
        // automation guard reads this marker to tell a fresh install from
        // a blob restored off another phone.
        com.openlumen.service.AutomationRestoreGuard.claimInstall(this)
        LumenServiceStarter.start(
            this,
            logTag = "OpenLumen/ActivityRecovery",
            exemption = LumenServiceStarter.Exemption.USER_INTERACTION,
            source = "activity"
        )
    }
}
