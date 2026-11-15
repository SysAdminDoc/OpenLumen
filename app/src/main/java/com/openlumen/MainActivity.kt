package com.openlumen

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.openlumen.location.AutoLocationRefresher
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.BlockedForegroundStartRecovery
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import com.openlumen.ui.OpenLumenRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var autoLocation: AutoLocationRefresher
    @Inject lateinit var prefs: PreferencesStore

    private var blockedStartPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedStartPending = BlockedForegroundStartRecovery.isPending(intent)
        enableEdgeToEdge()
        setContent { OpenLumenRoot() }
    }

    override fun onResume() {
        super.onResume()
        // A tile or widget start that was refused hands its retry to this
        // activity, and that retry waits for overlay access. Starting the
        // service from here as well would race it.
        if (blockedStartPending) retryBlockedStartIfReady() else restartFilterLeftOn()
        lifecycleScope.launch { autoLocation.refresh(applicationContext) }
    }

    /**
     * A foreground start refused in the background keeps the switch on and
     * leaves the retry to an alarm, which Android 12+ only lets through when
     * it is exact. Opening the app is the other way back, and the one that
     * always works: the activity is visible, so the start is allowed. Without
     * this the switch read on while nothing was running.
     */
    private fun restartFilterLeftOn() {
        if (LumenService.inForeground) return
        lifecycleScope.launch {
            val enabled = runCatching { prefs.flow.first().enabled }.getOrDefault(false)
            if (!enabled || LumenService.inForeground) return@launch
            LumenServiceStarter.start(
                this@MainActivity,
                logTag = "OpenLumen/ActivityRecovery",
                exemption = LumenServiceStarter.Exemption.USER_INTERACTION,
                source = "app-entry"
            )
        }
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
