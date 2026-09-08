package com.openlumen.service

import android.content.Context
import android.util.Log
import com.openlumen.prefs.PreferencesStore
import java.io.File

/**
 * Closes the automation surface when the preferences arrived from another
 * device.
 *
 * The token lives in the preferences blob, and that blob is in both the cloud
 * backup and the device-transfer set, so a restored phone came up with the old
 * phone's automation already enabled and the old phone's token still valid.
 * The code and the docs both said the token never leaves the device, and the
 * profile export redacts it for exactly that reason, so this was the one path
 * that contradicted the contract.
 *
 * The marker is the discriminator: a file in the no-backup directory, which is
 * excluded from backup and transfer by construction. Preferences with a token
 * but no marker beside them did not come from this install.
 *
 * A fingerprint or a build id would not do. `Build.FINGERPRINT` changes on
 * every system update, so it would close the surface after an OTA, which is
 * not a restore and not something a user should have to notice.
 */
internal object AutomationRestoreGuard {

    private const val MARKER = "automation-install-marker"
    private const val TAG = "OpenLumen/AutomationRestore"

    private fun marker(context: Context): File = File(context.noBackupFilesDir, MARKER)

    /**
     * Whether the automation surface has to be closed.
     *
     * Only when something is actually open: a token that was never minted, or
     * a surface the user has switched off, has nothing to protect and no
     * reason to interrupt.
     */
    internal fun shouldCloseAutomation(
        markerPresent: Boolean,
        automationEnabled: Boolean,
        token: String
    ): Boolean = !markerPresent && (automationEnabled || token.isNotEmpty())

    /**
     * Mark these preferences as belonging to this install.
     *
     * Called when the app itself is running, which is the only moment we know
     * the user is here rather than a restore having handed us someone else's
     * blob. Claiming is deliberately separate from [reconcile]: doing both in
     * one place meant the first automation broadcast on a fresh install, where
     * the marker does not exist yet either, read as a restore and threw away a
     * token the user had just minted.
     */
    fun claimInstall(context: Context) {
        val file = marker(context)
        if (runCatching { file.exists() }.getOrDefault(true)) return
        runCatching { file.writeText("1") }
            .onFailure { Log.w(TAG, "could not claim the install: ${it.message}") }
    }

    /**
     * Close the automation surface if these preferences were not minted here.
     *
     * Checks the marker; never writes it. Safe to call on every broadcast: it
     * is a file existence check and, once the install is claimed, a no-op.
     */
    suspend fun reconcile(context: Context, prefs: PreferencesStore) {
        val present = runCatching { marker(context).exists() }.getOrDefault(true)
        if (present) return

        var closed = false
        prefs.update { current ->
            if (shouldCloseAutomation(present, current.automationEnabled, current.automationToken)) {
                closed = true
                current.copy(automationEnabled = false, automationToken = "")
            } else {
                current
            }
        }
        if (closed) {
            Log.w(TAG, "preferences arrived from another install; automation closed")
        }
    }
}
