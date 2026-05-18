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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import FilterChip
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.BuildConfig
import com.openlumen.CrashLogger
import com.openlumen.R
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.presetDisplayName
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleModeDto
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
                Text(stringResource(R.string.about_backup_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_backup_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenButton(
                    onClick = {
                        exportLauncher.launch("openlumen-profile-${java.time.LocalDate.now()}.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_export_profile)) }
                LumenOutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_import_profile)) }
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_diagnostics_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_diagnostics_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LumenOutlinedButton(
                    onClick = { showCrashLog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_view_crash_log)) }
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
                val clipboardEmergencyOff = stringResource(R.string.clipboard_emergency_off)
                val emergencyOffCopied = stringResource(R.string.about_emergency_off_copied)
                Text(command, style = MaterialTheme.typography.bodySmall)
                LumenOutlinedButton(
                    onClick = {
                        copyToClipboardAbout(ctx, clipboardEmergencyOff, command)
                        Toast.makeText(
                            ctx,
                            emergencyOffCopied,
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
        DiagnosticsLogDialog(
            ctx = ctx,
            onDismiss = { showDiagLog = false }
        )
    }

    if (showCrashLog) {
        val log = remember { CrashLogger.read(ctx) }
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            title = { Text(stringResource(R.string.about_crash_log_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        if (log.isBlank()) stringResource(R.string.about_crash_log_empty) else log,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { showCrashLog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                LumenTextButton(onClick = {
                    CrashLogger.clear(ctx)
                    showCrashLog = false
                }) { Text(stringResource(R.string.action_clear)) }
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
                    if (pending.summary.droppedDuplicateNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.import_preview_duplicate_profiles,
                                pending.summary.droppedDuplicateNames.joinToString(", ")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val lines = describeDiff(ctx, currentPrefs, pending.decoded)
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
    context: Context,
    current: Preferences,
    next: Preferences
): List<String> {
    val out = mutableListOf<String>()
    fun diff(labelRes: Int, a: String, b: String) {
        if (a != b) {
            out += context.getString(R.string.diff_line, context.getString(labelRes), a, b)
        }
    }
    diff(
        R.string.diff_active_preset,
        presetDisplayName(context, current.activePresetKey),
        presetDisplayName(context, next.activePresetKey)
    )
    diff(R.string.diff_engine, engineLabel(context, current.engine), engineLabel(context, next.engine))
    diff(
        R.string.diff_schedule_mode,
        scheduleModeLabel(context, current.schedule.mode),
        scheduleModeLabel(context, next.schedule.mode)
    )
    diff(
        R.string.diff_schedule_start,
        "%02d:%02d".format(current.schedule.startHour, current.schedule.startMinute),
        "%02d:%02d".format(next.schedule.startHour, next.schedule.startMinute)
    )
    diff(
        R.string.diff_schedule_end,
        "%02d:%02d".format(current.schedule.endHour, current.schedule.endMinute),
        "%02d:%02d".format(next.schedule.endHour, next.schedule.endMinute)
    )
    val unset = context.getString(R.string.value_unset)
    val currentCoords = current.schedule.latitude?.let {
        "%.2f,%.2f".format(it, current.schedule.longitude ?: 0.0)
    } ?: unset
    val nextCoords = next.schedule.latitude?.let {
        "%.2f,%.2f".format(it, next.schedule.longitude ?: 0.0)
    } ?: unset
    diff(R.string.diff_location, currentCoords, nextCoords)
    diff(R.string.diff_intensity, "%.2f".format(current.presetIntensity), "%.2f".format(next.presetIntensity))
    diff(R.string.diff_dim, "%.2f".format(current.dim), "%.2f".format(next.dim))
    diff(R.string.diff_contrast, "%.2f".format(current.contrast), "%.2f".format(next.contrast))
    diff(
        R.string.diff_amoled_clamp,
        enabledLabel(context, current.amoledBlackClamp),
        enabledLabel(context, next.amoledBlackClamp)
    )
    diff(R.string.diff_light_sensor, enabledLabel(context, current.lightSensorEnabled), enabledLabel(context, next.lightSensorEnabled))
    if (current.lightSensorEnabled || next.lightSensorEnabled) {
        diff(
            R.string.diff_light_sensor_threshold,
            "%d".format(current.lightSensorLuxThreshold.toInt()),
            "%d".format(next.lightSensorLuxThreshold.toInt())
        )
    }
    diff(
        R.string.diff_sunset_offset,
        "%d".format(current.schedule.sunsetOffsetMin),
        "%d".format(next.schedule.sunsetOffsetMin)
    )
    diff(
        R.string.diff_sunrise_offset,
        "%d".format(current.schedule.sunriseOffsetMin),
        "%d".format(next.schedule.sunriseOffsetMin)
    )
    diff(
        R.string.diff_favorites,
        current.favoritePresetKeys.joinToString(",") { presetDisplayName(context, it) },
        next.favoritePresetKeys.joinToString(",") { presetDisplayName(context, it) }
    )
    diff(
        R.string.diff_transition,
        formatDuration(context, current.transitionDurationMs),
        formatDuration(context, next.transitionDurationMs)
    )
    return out
}

private fun engineLabel(context: Context, engine: EngineKindDto): String = when (engine) {
    EngineKindDto.Auto -> context.getString(R.string.driver_auto)
    EngineKindDto.ColorDisplayManager -> context.getString(R.string.driver_color_display)
    EngineKindDto.SurfaceFlinger -> context.getString(R.string.driver_surfaceflinger)
    EngineKindDto.Kcal -> context.getString(R.string.driver_kcal)
    EngineKindDto.Overlay -> context.getString(R.string.driver_overlay)
}

private fun scheduleModeLabel(context: Context, mode: ScheduleModeDto): String = when (mode) {
    ScheduleModeDto.AlwaysOff -> context.getString(R.string.schedule_off)
    ScheduleModeDto.AlwaysOn -> context.getString(R.string.schedule_always)
    ScheduleModeDto.FixedTime -> context.getString(R.string.schedule_fixed)
    ScheduleModeDto.Solar -> context.getString(R.string.schedule_solar)
    ScheduleModeDto.UntilNextAlarm -> context.getString(R.string.schedule_until_next_alarm)
}

private fun enabledLabel(context: Context, enabled: Boolean): String =
    context.getString(if (enabled) R.string.value_enabled else R.string.value_disabled)

private fun formatDuration(context: Context, ms: Long): String = when {
    ms <= 0 -> context.getString(R.string.duration_instant)
    ms < 60_000 -> context.getString(R.string.duration_seconds_short, ms / 1000)
    else -> context.getString(R.string.duration_minutes_short, ms / 60_000)
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

/**
 * Diagnostics-log dialog with level + category filter chips
 * (roadmap **C53 stretch**). The underlying log format is
 * `<instant> LEVEL CATEGORY <message>` so we can filter by checking
 * the second and third whitespace-separated tokens of each line.
 * Level chips default to WARN + ERROR (the maintainer-triage default);
 * category chips default to all-on. The chip rows persist across
 * dialog reopens via `rememberSaveable` — a user troubleshooting a
 * specific subsystem doesn't have to re-select on each open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsLogDialog(
    ctx: Context,
    onDismiss: () -> Unit
) {
    val rawLog = remember { DiagnosticsLog.read(ctx) }
    val rawLines = remember(rawLog) {
        if (rawLog.isBlank()) emptyList() else rawLog.lineSequence().filter { it.isNotBlank() }.toList()
    }

    // Default: ERROR + WARN visible (the high-signal triage view). Users can
    // multi-select to add INFO / DEBUG.
    var selectedLevels by rememberSaveable {
        mutableStateOf(setOf("WARN", "ERROR"))
    }
    var selectedCategories by rememberSaveable {
        mutableStateOf(com.openlumen.diagnostics.DiagnosticsLog.Category.values().map { it.name }.toSet())
    }

    val filteredLines = remember(rawLines, selectedLevels, selectedCategories) {
        if (rawLines.isEmpty()) emptyList()
        else rawLines.filter { line ->
            val tokens = line.split(' ', limit = 4)
            val level = tokens.getOrNull(1) ?: return@filter false
            val category = tokens.getOrNull(2) ?: return@filter false
            level in selectedLevels && category in selectedCategories
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_diag_log_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (rawLines.isEmpty()) {
                    Text(
                        stringResource(R.string.about_diag_log_empty),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        stringResource(R.string.about_diag_log_filter_level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.openlumen.diagnostics.DiagnosticsLog.Level.values().forEach { lvl ->
                            FilterChip(
                                selected = lvl.name in selectedLevels,
                                onClick = {
                                    selectedLevels = if (lvl.name in selectedLevels) selectedLevels - lvl.name
                                                     else selectedLevels + lvl.name
                                },
                                label = { Text(lvl.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_diag_log_filter_category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.openlumen.diagnostics.DiagnosticsLog.Category.values().forEach { cat ->
                            FilterChip(
                                selected = cat.name in selectedCategories,
                                onClick = {
                                    selectedCategories = if (cat.name in selectedCategories) selectedCategories - cat.name
                                                         else selectedCategories + cat.name
                                },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.about_diag_log_count,
                            filteredLines.size,
                            rawLines.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    if (filteredLines.isEmpty()) {
                        Text(
                            stringResource(R.string.about_diag_log_no_matches),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            filteredLines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            LumenTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            LumenTextButton(onClick = {
                DiagnosticsLog.clear(ctx)
                onDismiss()
            }) { Text(stringResource(R.string.action_clear)) }
        }
    )
}
