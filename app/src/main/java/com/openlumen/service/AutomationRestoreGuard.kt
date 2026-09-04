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
     * Run once per process, early. Clears the token and switches the surface
     * off when the preferences look restored, then claims the install so this
     * never fires again on this device.
     */
    suspend fun reconcile(context: Context, prefs: PreferencesStore) {
        val file = marker(context)
        val present = runCatching { file.exists() }.getOrDefault(true)

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

        if (!present) {
            runCatching { file.writeText("1") }
                .onFailure { Log.w(TAG, "could not claim the install: ${it.message}") }
        }
    }
}
