package com.openlumen

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.location.AutoLocationRefresher
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.PreferencesStore
import com.openlumen.service.AutomationRestoreGuard
import com.openlumen.service.BlockedForegroundStartRecovery
import com.openlumen.service.BlockedStartNotice
import com.openlumen.service.LumenService
import com.openlumen.service.LumenServiceStarter
import com.openlumen.ui.OpenLumenRoot
import com.openlumen.ui.components.BlockedStartUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var autoLocation: AutoLocationRefresher
    @Inject lateinit var prefs: PreferencesStore

    /**
     * A tile or widget start Android refused in the background, handed here
     * so the visible activity can finish it (C225). It is read off the intent
     * once and then lives here and in the saved state: a rotation, or a
     * relaunch from Recents, hands the same intent back, and acting on it
     * again would start the service twice or start a filter the user has
     * turned off since.
     */
    private var blockedStart by mutableStateOf<BlockedStartNotice?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedStart = if (savedInstanceState != null) {
            BlockedStartNotice.restore(savedInstanceState)
        } else {
            BlockedStartNotice.from(intent)?.also(::logHandover)
        }
        enableEdgeToEdge()
        setContent {
            OpenLumenRoot(
                blockedStart = blockedStart?.let { BlockedStartUi(it, ::retryBlockedStart) }
            )
        }
        // Turning the filter off answers the notice: there is nothing left
        // to start.
        lifecycleScope.launch {
            prefs.flow.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                if (!enabled && blockedStart?.visible == true) blockedStart = null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        blockedStart?.save(outState)
    }

    override fun onResume() {
        super.onResume()
        // A refused tile or widget start is finished from here, which also
        // brings the filter back. Starting it again for the switch being on
        // would race that start.
        if (blockedStart != null) startBlockedIfReady() else restartFilterLeftOn()
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
            if (!enabled || LumenService.inForeground || blockedStart != null) return@launch
            LumenServiceStarter.start(
                this@MainActivity,
                logTag = "OpenLumen/ActivityRecovery",
                exemption = LumenServiceStarter.Exemption.USER_INTERACTION,
                source = "app-entry"
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The app was already open: SINGLE_TOP brings the tile's or widget's
        // handover here instead of to a new activity.
        val notice = BlockedStartNotice.from(intent) ?: return
        logHandover(notice)
        blockedStart = notice
        // onResume follows when the activity was paused for this; when it
        // is already resumed nothing else would pick the notice up.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startBlockedIfReady()
    }

    /**
     * Starts the service for a handed-over start as soon as it can work:
     * once, and only while the switch is still on. A driver that needs the
     * overlay waits on the permission and says so on Home; a start the
     * system refuses again waits for Retry.
     */
    private fun startBlockedIfReady() {
        val pending = blockedStart ?: return
        if (pending.phase == BlockedStartNotice.Phase.StartFailed) return
        lifecycleScope.launch {
            val current = runCatching { prefs.flow.first() }.getOrNull()
            // Re-read after the suspension: a second resume, or the Retry
            // button, may have handled this notice meanwhile.
            val notice = blockedStart
            if (notice == null || notice.id != pending.id ||
                notice.phase == BlockedStartNotice.Phase.StartFailed
            ) {
                return@launch
            }
            if (current?.enabled == false) {
                blockedStart = null
                return@launch
            }
            val overlayNeeded = BlockedForegroundStartRecovery.overlayNeeded(current?.engine ?: EngineKindDto.Auto)
            if (!BlockedForegroundStartRecovery.canStartNow(overlayNeeded, Settings.canDrawOverlays(this@MainActivity))) {
                blockedStart = notice.copy(phase = BlockedStartNotice.Phase.NeedsOverlay)
                return@launch
            }
            startFromActivity(notice, source = "activity")
        }
    }

    /** The notice's Retry: one start, whatever the overlay state. */
    private fun retryBlockedStart() {
        val notice = blockedStart ?: return
        startFromActivity(notice, source = "retry")
    }

    private fun startFromActivity(notice: BlockedStartNotice, source: String) {
        // The user is here, so these preferences are this install's. The
        // automation guard reads this marker to tell a fresh install from
        // a blob restored off another phone.
        AutomationRestoreGuard.claimInstall(this)
        val result = LumenServiceStarter.start(
            this,
            logTag = "OpenLumen/ActivityRecovery",
            exemption = LumenServiceStarter.Exemption.USER_INTERACTION,
            source = source
        )
        blockedStart = if (result.started) null else notice.copy(phase = BlockedStartNotice.Phase.StartFailed)
    }

    private fun logHandover(notice: BlockedStartNotice) {
        DiagnosticsLog.log(
            this,
            DiagnosticsLog.Level.INFO,
            DiagnosticsLog.Category.SERVICE,
            "refused service start handed to the app from ${notice.reason ?: "unknown"}"
        )
    }
}
