package com.openlumen.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.BuildConfig
import com.openlumen.CrashLogger
import com.openlumen.R
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun AboutScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val result by vm.exportResult.collectAsState()
    var showCrashLog by rememberSaveable { mutableStateOf(false) }
    var showDiagLog by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var saveProfileName by rememberSaveable { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::beginImportPreview) }

    val pendingImport by vm.pendingImport.collectAsState()
    val currentPrefs by vm.state.collectAsState()

    LaunchedEffect(result) {
        val msg = result ?: return@LaunchedEffect
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        vm.consumeExportResult()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            "${stringResource(R.string.about_version)} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.about_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save or restore your preferences as a JSON file. Useful for moving " +
                        "between devices or sharing presets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenButton(
                    onClick = {
                        exportLauncher.launch("openlumen-profile-${java.time.LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export profile") }
                LumenOutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import profile") }
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text(
                    "OpenLumen keeps a local crash log in app-private storage. It never " +
                        "leaves the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenOutlinedButton(
                    onClick = { showCrashLog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("View crash log") }
                LumenOutlinedButton(
                    onClick = { showDiagLog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_view_diag_log)) }
            }
        }

        // Named profile library (C31). Save the current configuration under a
        // name; load it back later. Loading also records the previous active
        // preset so the C14 restore path round-trips with profile loading.
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.about_profiles_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.about_profiles_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenOutlinedButton(
                    onClick = {
                        saveProfileName = ""
                        showSaveProfileDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_profiles_save)) }

                if (currentPrefs.savedProfiles.isEmpty()) {
                    Text(
                        stringResource(R.string.about_profiles_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    currentPrefs.savedProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            LumenTextButton(onClick = { vm.loadProfile(profile.name) }) {
                                Text(stringResource(R.string.about_profiles_load))
                            }
                            LumenTextButton(onClick = { vm.deleteProfile(profile.name) }) {
                                Text(stringResource(R.string.about_profiles_delete))
                            }
                        }
                    }
                }
            }
        }

        // Emergency-off ADB command (C13). Surfaced in About so the command
        // is discoverable even when the on-screen tint is too strong to read
        // the rest of the UI — users learn it exists, can stash it in a
        // password manager, and can reach it from a paired computer.
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.about_emergency_off_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.about_emergency_off_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val command = emergencyOffCommand(ctx.packageName)
                Text(command, style = MaterialTheme.typography.bodySmall)
                LumenOutlinedButton(
                    onClick = {
                        copyToClipboardAbout(ctx, "OpenLumen emergency off", command)
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.about_emergency_off_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_emergency_off_copy)) }
            }
        }
    }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text(stringResource(R.string.about_profiles_save_title)) },
            text = {
                OutlinedTextField(
                    value = saveProfileName,
                    onValueChange = { saveProfileName = it },
                    label = { Text(stringResource(R.string.about_profiles_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LumenTextButton(
                    onClick = {
                        vm.saveProfileAs(saveProfileName)
                        showSaveProfileDialog = false
                    },
                    enabled = saveProfileName.trim().isNotEmpty()
                ) { Text(stringResource(R.string.about_profiles_save)) }
            },
            dismissButton = {
                LumenTextButton(onClick = { showSaveProfileDialog = false }) {
                    Text(stringResource(R.string.import_preview_cancel))
                }
            }
        )
    }

    if (showDiagLog) {
        val log = remember { DiagnosticsLog.read(ctx) }
        AlertDialog(
            onDismissRequest = { showDiagLog = false },
            title = { Text(stringResource(R.string.about_diag_log_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (log.isBlank()) stringResource(R.string.about_diag_log_empty) else log,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { showDiagLog = false }) { Text("Close") }
            },
            dismissButton = {
                LumenTextButton(onClick = {
                    DiagnosticsLog.clear(ctx)
                    showDiagLog = false
                }) { Text("Clear") }
            }
        )
    }

    if (showCrashLog) {
        val log = remember { CrashLogger.read(ctx) }
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text("Crash log") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (log.isBlank()) "No crashes recorded." else log,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { showCrashLog = false }) { Text("Close") }
            },
            dismissButton = {
                LumenTextButton(onClick = {
                    CrashLogger.clear(ctx)
                    showCrashLog = false
                }) { Text("Clear") }
            }
        )
    }

    // Import preview (C30). The dialog renders a diff of what the imported
    // profile would change vs the user's current preferences. The user must
    // confirm before any DataStore write happens.
    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelPendingImport() },
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val lines = describeDiff(currentPrefs, pending.decoded)
                    if (lines.isEmpty()) {
                        Text(stringResource(R.string.import_preview_unchanged))
                    } else {
                        lines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { vm.confirmPendingImport() }) {
                    Text(stringResource(R.string.import_preview_apply))
                }
            },
            dismissButton = {
                LumenTextButton(onClick = { vm.cancelPendingImport() }) {
                    Text(stringResource(R.string.import_preview_cancel))
                }
            }
        )
    }
}

/**
 * Human-readable summary of the differences between two [com.openlumen.prefs.Preferences]
 * snapshots. Used by the import preview dialog (C30). Deliberately terse — the
 * dialog body shouldn't scroll for typical profile imports.
 */
private fun describeDiff(
    current: com.openlumen.prefs.Preferences,
    next: com.openlumen.prefs.Preferences
): List<String> {
    val out = mutableListOf<String>()
    fun <T> diff(label: String, a: T, b: T) {
        if (a != b) out += "$label: $a → $b"
    }
    diff("Active preset", current.activePresetKey, next.activePresetKey)
    diff("Engine", current.engine.name, next.engine.name)
    diff("Schedule mode", current.schedule.mode.name, next.schedule.mode.name)
    diff(
        "Schedule start",
        "%02d:%02d".format(current.schedule.startHour, current.schedule.startMinute),
        "%02d:%02d".format(next.schedule.startHour, next.schedule.startMinute)
    )
    diff(
        "Schedule end",
        "%02d:%02d".format(current.schedule.endHour, current.schedule.endMinute),
        "%02d:%02d".format(next.schedule.endHour, next.schedule.endMinute)
    )
    val currentCoords = current.schedule.latitude?.let { "%.2f,%.2f".format(it, current.schedule.longitude ?: 0.0) } ?: "unset"
    val nextCoords = next.schedule.latitude?.let { "%.2f,%.2f".format(it, next.schedule.longitude ?: 0.0) } ?: "unset"
    diff("Location", currentCoords, nextCoords)
    diff("Intensity", "%.2f".format(current.presetIntensity), "%.2f".format(next.presetIntensity))
    diff("Dim", "%.2f".format(current.dim), "%.2f".format(next.dim))
    diff("Light sensor", current.lightSensorEnabled, next.lightSensorEnabled)
    diff(
        "Favorites",
        current.favoritePresetKeys.joinToString(","),
        next.favoritePresetKeys.joinToString(",")
    )
    diff(
        "Transition",
        formatDuration(current.transitionDurationMs),
        formatDuration(next.transitionDurationMs)
    )
    return out
}

private fun formatDuration(ms: Long): String = when {
    ms <= 0 -> "instant"
    ms < 60_000 -> "${ms / 1000}s"
    else -> "${ms / 60_000}m"
}

// Built around the runtime package name so the debug build prints the
// `.debug`-suffixed package — same convention as the Driver-screen ADB grant.
// Matches LumenService.ACTION_TURN_OFF, which writes enabled=false and stops
// the service even if the foreground UI is completely obscured.
private fun emergencyOffCommand(packageName: String): String =
    "adb shell am startservice -a com.openlumen.action.TURN_OFF " +
        "-n $packageName/com.openlumen.service.LumenService"

private fun copyToClipboardAbout(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
