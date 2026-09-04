package com.openlumen.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.BuildConfig
import com.openlumen.CrashLogger
import com.openlumen.R
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.presetDisplayName
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Profiles
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.components.PreferencesPlaceholder
import com.openlumen.ui.components.CommandBlock
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.LumenFilterChip
import com.openlumen.ui.components.LumenSwitch
import com.openlumen.ui.components.LumenTextButton
import com.openlumen.ui.components.labeledSliderSemantics
import com.openlumen.viewmodel.OpenLumenScreenModel
import com.openlumen.viewmodel.OpenLumenViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The ceiling for a scrolling dialog body.
 *
 * A flat 420 dp is taller than a landscape phone's whole window, which pushes
 * the dialog's own buttons off screen with no way to dismiss it. Callers take
 * the smaller of this and a share of the window.
 */
private val DialogLogMaxHeight = 420.dp

/** [DialogLogMaxHeight], or a share of the window when that is smaller. */
@Composable
private fun dialogBodyMaxHeight(): Dp {
    // containerSize, not Configuration.screenHeightDp: the configuration value
    // rounds to whole dp and its inset handling changes with the target SDK,
    // so it does not reliably describe the window this dialog has to fit in.
    // Lint flags that read for exactly this reason.
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(LocalDensity.current) { containerHeight.toDp() }
    return minOf(DialogLogMaxHeight, windowHeight * 0.6f)
}

@Composable
fun AboutScreen(
    vm: OpenLumenScreenModel = hiltViewModel<OpenLumenViewModel>(),
    enableSystemActions: Boolean = true
) {
    val ctx = LocalContext.current
    val result by vm.exportResult.collectAsStateWithLifecycle()
    var showCrashLog by rememberSaveable { mutableStateOf(false) }
    var showDiagLog by rememberSaveable { mutableStateOf(false) }
    var showRecoveryReset by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showReplaceProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameProfileDialog by rememberSaveable { mutableStateOf(false) }
    var saveProfileName by rememberSaveable { mutableStateOf("") }
    var pendingReplaceProfileName by rememberSaveable { mutableStateOf("") }
    var pendingRenameProfileName by rememberSaveable { mutableStateOf("") }
    var renameProfileName by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportLauncher = if (enableSystemActions) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }
    else null

    val importLauncher = if (enableSystemActions) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::beginImportPreview) }
    else null
    val presetPackExportLauncher = if (enableSystemActions) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportPresetPack) }
    else null
    val presetPackImportLauncher = if (enableSystemActions) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::beginPresetPackPreview) }
    else null
    val recoveryExportLauncher = if (enableSystemActions) rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportCorruptPreferencesTo) }
    else null

    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    val pendingPresetPack by vm.pendingPresetPack.collectAsStateWithLifecycle()
    val preferenceRecovery by vm.preferenceRecovery.collectAsStateWithLifecycle()
    val currentPrefs by vm.state.collectAsStateWithLifecycle()
    // Nothing to draw yet. Without this the first frame after a cold start
    // showed `Preferences()`: the automation surface read as closed and the
    // saved profiles read as none before the screen jumped (C329).
    val preferencesLoaded by vm.preferencesLoaded.collectAsStateWithLifecycle()
    if (!preferencesLoaded) {
        PreferencesPlaceholder()
        return
    }

    val profileDeletedMessage = stringResource(R.string.about_profiles_deleted)
    val undoActionLabel = stringResource(R.string.action_undo)
    val crashLogClearedMessage = stringResource(R.string.about_crash_log_cleared)
    val diagLogClearedMessage = stringResource(R.string.about_diag_log_cleared)
    val tokenRegeneratedMessage = stringResource(R.string.about_automation_token_regenerated)
    val automationEnableLabel = stringResource(R.string.about_automation_enable)
    val automationTokenPending = stringResource(R.string.about_automation_token_pending)
    val automationTokenCopied = stringResource(R.string.about_automation_token_copied)
    val clipboardAutomationToken = stringResource(R.string.clipboard_automation_token)

    fun submitProfileSave(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        if (Profiles.findByName(currentPrefs, cleanName) != null) {
            pendingReplaceProfileName = cleanName
            showSaveProfileDialog = false
            showReplaceProfileDialog = true
        } else {
            vm.saveProfileAs(cleanName)
            showSaveProfileDialog = false
        }
    }

    fun submitProfileRename(name: String) {
        val oldName = pendingRenameProfileName
        val cleanName = name.trim()
        if (oldName.isBlank() || cleanName.isBlank()) return
        if (profileRenameCollides(currentPrefs, oldName, cleanName)) return
        vm.renameProfile(oldName, cleanName)
        showRenameProfileDialog = false
        pendingRenameProfileName = ""
    }

    LaunchedEffect(result) {
        val msg = result ?: return@LaunchedEffect
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        vm.consumeExportResult()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(topLevelScrollPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                // TalkBack's heading navigation is how you skip a long scroll
                // screen. There was not one heading anywhere in the app, so it
                // had nothing to jump between.
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME),
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
                            exportLauncher?.launch("openlumen-profile-${java.time.LocalDate.now()}.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_export_profile)) }
                    LumenOutlinedButton(
                        onClick = { importLauncher?.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_import_profile)) }
                    LumenOutlinedButton(
                        onClick = {
                            presetPackExportLauncher?.launch(
                                "openlumen-preset-pack-${java.time.LocalDate.now()}.json"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_export_preset_pack)) }
                    LumenOutlinedButton(
                        onClick = {
                            presetPackImportLauncher?.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_import_preset_pack)) }
                    preferenceRecovery?.let { recovery ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.backup_recovery_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    stringResource(
                                        R.string.backup_recovery_body,
                                        recovery.rawCharacterCount,
                                        recovery.quarantinedCharacterCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                LumenButton(
                                    onClick = {
                                        recoveryExportLauncher?.launch("openlumen-corrupt-preferences.json")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.backup_recovery_export)) }
                                LumenOutlinedButton(
                                    onClick = { showRecoveryReset = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.backup_recovery_reset_action)) }
                            }
                        }
                    }
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
                            pendingReplaceProfileName = ""
                            showSaveProfileDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_profiles_save)) }

                    if (currentPrefs.savedProfiles.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                stringResource(R.string.about_profiles_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        currentPrefs.savedProfiles.forEach { profile ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        profile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Three weighted buttons in one row leaves
                                    // each a third of a narrow phone, so Rename
                                    // and Delete ellipsised into nothing. They
                                    // wrap now instead of shrinking.
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LumenTextButton(
                                            onClick = { vm.loadProfile(profile.name) }
                                        ) {
                                            Text(
                                                stringResource(R.string.about_profiles_load),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        LumenTextButton(
                                            onClick = {
                                                pendingRenameProfileName = profile.name
                                                renameProfileName = profile.name
                                                showRenameProfileDialog = true
                                            }
                                        ) {
                                            Text(
                                                stringResource(R.string.about_profiles_rename),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        LumenTextButton(
                                            onClick = {
                                                vm.deleteProfile(profile.name)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = profileDeletedMessage,
                                                        actionLabel = undoActionLabel,
                                                        withDismissAction = true
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        vm.restoreDeletedProfile(profile)
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                stringResource(R.string.about_profiles_delete),
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Automation surface (C250). Off on a fresh install: a broadcast
            // receiver cannot identify its sender, so the only workable gate
            // across the supported API range is a secret the user hands to
            // their own scripts.
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.about_automation_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.about_automation_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.about_automation_enable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LumenSwitch(
                            checked = currentPrefs.automationEnabled,
                            onCheckedChange = { vm.setAutomationEnabled(it) },
                            modifier = Modifier.semantics {
                                contentDescription = automationEnableLabel
                            }
                        )
                    }
                    if (currentPrefs.automationEnabled) {
                        val token = currentPrefs.automationToken
                        Text(
                            stringResource(R.string.about_automation_token_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        CommandBlock(text = token.ifEmpty { automationTokenPending })
                        Text(
                            stringResource(R.string.about_automation_token_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LumenOutlinedButton(
                                onClick = {
                                    copyToClipboardAbout(ctx, clipboardAutomationToken, token)
                                    Toast.makeText(ctx, automationTokenCopied, Toast.LENGTH_SHORT).show()
                                },
                                enabled = token.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.about_automation_copy_token)) }
                            LumenOutlinedButton(
                                onClick = {
                                    vm.regenerateAutomationToken()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = tokenRegeneratedMessage,
                                            withDismissAction = true
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.about_automation_regenerate)) }
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
                    CommandBlock(text = command)
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
    }

    if (showSaveProfileDialog) {
        val cleanProfileName = saveProfileName.trim()
        val maxProfileNameLength = Preferences.MAX_PROFILE_NAME_LENGTH
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.about_profiles_save_title)) },
            text = {
                OutlinedTextField(
                    value = saveProfileName,
                    onValueChange = { saveProfileName = it.take(maxProfileNameLength) },
                    label = { Text(stringResource(R.string.about_profiles_name_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.about_profiles_name_count,
                                saveProfileName.length,
                                maxProfileNameLength
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            submitProfileSave(cleanProfileName)
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LumenTextButton(
                    onClick = {
                        submitProfileSave(cleanProfileName)
                    },
                    enabled = cleanProfileName.isNotEmpty()
                ) { Text(stringResource(R.string.about_profiles_save)) }
            },
            dismissButton = {
                LumenTextButton(onClick = { showSaveProfileDialog = false }) {
                    Text(stringResource(R.string.import_preview_cancel))
                }
            }
        )
    }

    if (showRenameProfileDialog) {
        val cleanProfileName = renameProfileName.trim()
        val maxProfileNameLength = Preferences.MAX_PROFILE_NAME_LENGTH
        // The Save button used to stay enabled on a name that is already
        // taken, and the tap was swallowed: the dialog just sat there.
        val renameCollides = profileRenameCollides(
            currentPrefs,
            pendingRenameProfileName,
            cleanProfileName
        )
        AlertDialog(
            onDismissRequest = {
                showRenameProfileDialog = false
                pendingRenameProfileName = ""
            },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.about_profiles_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameProfileName,
                    onValueChange = { renameProfileName = it.take(maxProfileNameLength) },
                    label = { Text(stringResource(R.string.about_profiles_name_label)) },
                    isError = renameCollides,
                    supportingText = {
                        Text(
                            if (renameCollides) {
                                stringResource(R.string.about_profiles_name_taken)
                            } else {
                                stringResource(
                                    R.string.about_profiles_name_count,
                                    renameProfileName.length,
                                    maxProfileNameLength
                                )
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { submitProfileRename(cleanProfileName) }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LumenTextButton(
                    onClick = { submitProfileRename(cleanProfileName) },
                    enabled = cleanProfileName.isNotEmpty() && !renameCollides
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                LumenTextButton(
                    onClick = {
                        showRenameProfileDialog = false
                        pendingRenameProfileName = ""
                    }
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showReplaceProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showReplaceProfileDialog = false
                pendingReplaceProfileName = ""
            },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.about_profiles_replace_title)) },
            text = { Text(stringResource(R.string.about_profiles_replace_body)) },
            confirmButton = {
                LumenTextButton(
                    onClick = {
                        vm.saveProfileAs(
                            pendingReplaceProfileName,
                            replaceExisting = true
                        )
                        showReplaceProfileDialog = false
                        pendingReplaceProfileName = ""
                    }
                ) { Text(stringResource(R.string.about_profiles_replace)) }
            },
            dismissButton = {
                LumenTextButton(
                    onClick = {
                        showReplaceProfileDialog = false
                        pendingReplaceProfileName = ""
                    }
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDiagLog) {
        DiagnosticsLogDialog(
            ctx = ctx,
            onDismiss = { showDiagLog = false },
            onCleared = { snapshot ->
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = diagLogClearedMessage,
                        actionLabel = undoActionLabel,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        DiagnosticsLog.restore(ctx, snapshot)
                    }
                }
            }
        )
    }

    if (showCrashLog) {
        val log = CrashLogger.read(ctx)
        AlertDialog(
            onDismissRequest = { showCrashLog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.about_crash_log_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogBodyMaxHeight())
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (log.isBlank()) stringResource(R.string.about_crash_log_empty) else log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (log.isBlank()) null else FontFamily.Monospace,
                        softWrap = log.isBlank(),
                        modifier = if (log.isBlank()) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(rememberScrollState())
                        }
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
                    val snapshot = CrashLogger.read(ctx)
                    CrashLogger.clear(ctx)
                    showCrashLog = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = crashLogClearedMessage,
                            actionLabel = undoActionLabel,
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            CrashLogger.restore(ctx, snapshot)
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.action_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    pendingPresetPack?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelPendingPresetPack() },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.preset_pack_preview_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogBodyMaxHeight())
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.preset_pack_preview_summary,
                            pluralStringResource(
                                R.plurals.preset_pack_preview_added,
                                pending.summary.importedProfileNames.size,
                                pending.summary.importedProfileNames.size
                            ),
                            pluralStringResource(
                                R.plurals.preset_pack_preview_replaced,
                                pending.summary.replacedProfileNames.size,
                                pending.summary.replacedProfileNames.size
                            )
                        )
                    )
                    if (pending.summary.replacedProfileNames.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.preset_pack_preview_replaced,
                                pending.summary.replacedProfileNames.joinToString(", ")
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        stringResource(R.string.preset_pack_preview_safe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                LumenTextButton(onClick = { vm.confirmPendingPresetPack() }) {
                    Text(stringResource(R.string.import_preview_apply))
                }
            },
            dismissButton = {
                LumenTextButton(onClick = { vm.cancelPendingPresetPack() }) {
                    Text(stringResource(R.string.import_preview_cancel))
                }
            }
        )
    }

    // Import preview (C30). The dialog renders a diff of what the imported
    // profile would change vs the user's current preferences. The user must
    // confirm before any DataStore write happens.
    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.cancelPendingImport() },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = dialogBodyMaxHeight())
                        .verticalScroll(rememberScrollState())
                ) {
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

    if (preferenceRecovery != null && showRecoveryReset) {
        AlertDialog(
            onDismissRequest = { showRecoveryReset = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.backup_recovery_reset_title)) },
            text = { Text(stringResource(R.string.backup_recovery_reset_body)) },
            confirmButton = {
                LumenTextButton(
                    onClick = {
                        showRecoveryReset = false
                        vm.resetCorruptPreferences()
                    }
                ) {
                    Text(
                        stringResource(R.string.backup_recovery_reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                LumenTextButton(onClick = { showRecoveryReset = false }) {
                    Text(stringResource(R.string.action_cancel))
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
        presetDisplayName(
            context,
            current.activePresetKey,
            override = current.presetNameOverrides[current.activePresetKey]
        ),
        presetDisplayName(
            context,
            next.activePresetKey,
            override = next.presetNameOverrides[next.activePresetKey]
        )
    )
    diff(R.string.diff_engine, engineLabel(context, current.engine), engineLabel(context, next.engine))
    diff(
        R.string.diff_schedule_mode,
        scheduleModeLabel(context, current.schedule.mode),
        scheduleModeLabel(context, next.schedule.mode)
    )
    diff(
        R.string.diff_schedule_start,
        String.format(Locale.ROOT, "%02d:%02d", current.schedule.startHour, current.schedule.startMinute),
        String.format(Locale.ROOT, "%02d:%02d", next.schedule.startHour, next.schedule.startMinute)
    )
    diff(
        R.string.diff_schedule_end,
        String.format(Locale.ROOT, "%02d:%02d", current.schedule.endHour, current.schedule.endMinute),
        String.format(Locale.ROOT, "%02d:%02d", next.schedule.endHour, next.schedule.endMinute)
    )
    val unset = context.getString(R.string.value_unset)
    val currentCoords = current.schedule.latitude?.let {
        String.format(Locale.ROOT, "%.2f,%.2f", it, current.schedule.longitude ?: 0.0)
    } ?: unset
    val nextCoords = next.schedule.latitude?.let {
        String.format(Locale.ROOT, "%.2f,%.2f", it, next.schedule.longitude ?: 0.0)
    } ?: unset
    diff(R.string.diff_location, currentCoords, nextCoords)
    diff(R.string.diff_intensity, String.format(Locale.ROOT, "%.2f", current.presetIntensity), String.format(Locale.ROOT, "%.2f", next.presetIntensity))
    diff(R.string.diff_dim, String.format(Locale.ROOT, "%.2f", current.dim), String.format(Locale.ROOT, "%.2f", next.dim))
    diff(R.string.diff_contrast, String.format(Locale.ROOT, "%.2f", current.contrast), String.format(Locale.ROOT, "%.2f", next.contrast))
    diff(
        R.string.diff_amoled_clamp,
        enabledLabel(context, current.amoledBlackClamp),
        enabledLabel(context, next.amoledBlackClamp)
    )
    diff(R.string.diff_light_sensor, enabledLabel(context, current.lightSensorEnabled), enabledLabel(context, next.lightSensorEnabled))
    if (current.lightSensorEnabled || next.lightSensorEnabled) {
        diff(
            R.string.diff_light_sensor_threshold,
            String.format(Locale.ROOT, "%d", current.lightSensorLuxThreshold.toInt()),
            String.format(Locale.ROOT, "%d", next.lightSensorLuxThreshold.toInt())
        )
    }
    diff(
        R.string.diff_sunset_offset,
        String.format(Locale.ROOT, "%d", current.schedule.sunsetOffsetMin),
        String.format(Locale.ROOT, "%d", next.schedule.sunsetOffsetMin)
    )
    diff(
        R.string.diff_sunrise_offset,
        String.format(Locale.ROOT, "%d", current.schedule.sunriseOffsetMin),
        String.format(Locale.ROOT, "%d", next.schedule.sunriseOffsetMin)
    )
    val noneLabel = context.getString(R.string.value_unset)
    diff(
        R.string.diff_favorites,
        current.favoritePresetKeys.joinToString(",") {
            presetDisplayName(context, it, override = current.presetNameOverrides[it])
        }.ifEmpty { noneLabel },
        next.favoritePresetKeys.joinToString(",") {
            presetDisplayName(context, it, override = next.presetNameOverrides[it])
        }.ifEmpty { noneLabel }
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
// Matches LumenService.ACTION_TURN_OFF through AutomationReceiver, which
// re-enters the non-exported service under OpenLumen's UID and hard-clears
// root display backends even if the foreground UI is completely obscured.
private fun emergencyOffCommand(packageName: String): String =
    // --include-stopped-packages: a force-stopped or killed package receives no
    // broadcast without it, and "the app is not running" is the state this
    // command exists for (C296).
    "adb shell am broadcast --include-stopped-packages -a com.openlumen.action.TURN_OFF " +
        "-n $packageName/com.openlumen.service.AutomationReceiver"

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
 *
 * Level chips default to WARN + ERROR (the maintainer-triage default);
 * category chips default to all-on. The selections persist across
 * configuration changes (rotation) inside an open dialog via
 * `rememberSaveable`, but reset to the defaults whenever the dialog
 * is closed and reopened — the dialog composable enters a fresh
 * remember scope each open and we deliberately don't hoist the
 * selection state into the ViewModel because the diagnostics view
 * is a one-shot triage surface, not part of the persistent app
 * state model.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiagnosticsLogDialog(
    ctx: Context,
    onDismiss: () -> Unit,
    onCleared: (snapshot: String) -> Unit
) {
    val diagLogClipboardLabel = stringResource(R.string.about_diag_log_title)
    val diagLogCopiedMessage = stringResource(R.string.about_diag_log_copied)
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var timelineStartFraction by rememberSaveable { mutableFloatStateOf(0f) }
    var timelineEndFraction by rememberSaveable { mutableFloatStateOf(1f) }
    val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())

    val timelineBounds = remember(rawLines, selectedLevels, selectedCategories) {
        DiagnosticsLog.timelineBounds(rawLines, selectedLevels, selectedCategories)
    }
    LaunchedEffect(timelineBounds) {
        timelineStartFraction = 0f
        timelineEndFraction = 1f
    }
    val timelineFrom = timelineBounds?.let {
        timelineInstantAt(it, timelineStartFraction)
    }
    val timelineThrough = timelineBounds?.let {
        timelineInstantAt(it, timelineEndFraction)
    }
    val baseFilteredLines = remember(rawLines, selectedLevels, selectedCategories) {
        rawLines.filter { line ->
            DiagnosticsLog.lineMatches(line, selectedLevels, selectedCategories)
        }
    }
    val filteredLines = remember(
        rawLines,
        selectedLevels,
        selectedCategories,
        searchQuery,
        timelineFrom,
        timelineThrough
    ) {
        DiagnosticsLog.filterLines(
            lines = rawLines,
            levels = selectedLevels,
            categories = selectedCategories,
            query = searchQuery,
            from = timelineFrom,
            through = timelineThrough
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.about_diag_log_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = dialogBodyMaxHeight())
                    .verticalScroll(rememberScrollState())
            ) {
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
                            LumenFilterChip(
                                selected = lvl.name in selectedLevels,
                                onClick = {
                                    selectedLevels = if (lvl.name in selectedLevels) selectedLevels - lvl.name
                                                     else selectedLevels + lvl.name
                                },
                                label = { Text(stringResource(diagLevelLabel(lvl))) }
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
                            LumenFilterChip(
                                selected = cat.name in selectedCategories,
                                onClick = {
                                    selectedCategories = if (cat.name in selectedCategories) selectedCategories - cat.name
                                                         else selectedCategories + cat.name
                                },
                                label = { Text(stringResource(diagCategoryLabel(cat))) }
                            )
                        }
                    }
                    timelineBounds?.let { bounds ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.about_diag_log_timeline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RangeSlider(
                            value = timelineStartFraction..timelineEndFraction,
                            onValueChange = { range ->
                                timelineStartFraction = range.start
                                timelineEndFraction = range.endInclusive
                            },
                            valueRange = 0f..1f,
                            steps = 0,
                            // Without this the control has no name at all and
                            // TalkBack announces two unlabelled adjustable
                            // handles, which is unusable: there is no way to
                            // tell which end you are dragging.
                            modifier = Modifier.labeledSliderSemantics(
                                name = stringResource(R.string.about_diag_log_timeline),
                                valueDescription = stringResource(
                                    R.string.about_diag_log_timeline_state,
                                    formatLogInstant(bounds.earliest, timelineStartFraction, bounds, locale),
                                    formatLogInstant(bounds.latest, timelineEndFraction, bounds, locale)
                                )
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatLogInstant(bounds.earliest, 0f, bounds, locale),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatLogInstant(bounds.latest, 1f, bounds, locale),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.about_diag_log_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(
                            R.plurals.about_diag_log_count,
                            baseFilteredLines.size,
                            filteredLines.size,
                            baseFilteredLines.size
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
                        // Selectable and copyable. The only other way to get
                        // this text off the device was the 3 KB tail the driver
                        // report carries, and a user reporting an issue needs
                        // the lines they are looking at, not the last few.
                        SelectionContainer {
                            Text(
                                filteredLines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                softWrap = false,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LumenTextButton(
                            onClick = {
                                copyToClipboardAbout(
                                    ctx,
                                    diagLogClipboardLabel,
                                    filteredLines.joinToString("\n")
                                )
                                Toast.makeText(ctx, diagLogCopiedMessage, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(stringResource(R.string.about_diag_log_copy))
                        }
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
                onCleared(rawLog)
            }) {
                Text(
                    stringResource(R.string.action_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

private fun timelineInstantAt(
    bounds: DiagnosticsLog.TimelineBounds,
    fraction: Float
): java.time.Instant {
    val start = bounds.earliest.toEpochMilli()
    val end = bounds.latest.toEpochMilli()
    val clamped = fraction.coerceIn(0f, 1f)
    val offset = ((end - start).toDouble() * clamped).toLong()
    return java.time.Instant.ofEpochMilli(start + offset)
}

/**
 * Whether renaming [oldName] to [newName] would land on a profile that already
 * exists. Renaming a profile to its own name in a different case is not a
 * collision: that is how a user fixes the capitalisation of a name they own.
 */
internal fun profileRenameCollides(
    current: Preferences,
    oldName: String,
    newName: String
): Boolean {
    val existing = Profiles.findByName(current, newName.trim()) ?: return false
    return !existing.name.equals(oldName, ignoreCase = true)
}

/**
 * The diagnostics filter chips used to render the enum constant name, so they
 * read DEBUG and PREFS in every language, and could not be translated at all.
 * The `when` is exhaustive on purpose: a new level or category is then a
 * compile error rather than a chip that quietly says its enum name again.
 */
@StringRes
private fun diagLevelLabel(level: DiagnosticsLog.Level): Int = when (level) {
    DiagnosticsLog.Level.DEBUG -> R.string.diag_level_debug
    DiagnosticsLog.Level.INFO -> R.string.diag_level_info
    DiagnosticsLog.Level.WARN -> R.string.diag_level_warn
    DiagnosticsLog.Level.ERROR -> R.string.diag_level_error
}

@StringRes
private fun diagCategoryLabel(category: DiagnosticsLog.Category): Int = when (category) {
    DiagnosticsLog.Category.SERVICE -> R.string.diag_category_service
    DiagnosticsLog.Category.ENGINE -> R.string.diag_category_engine
    DiagnosticsLog.Category.SCHEDULE -> R.string.diag_category_schedule
    DiagnosticsLog.Category.SENSOR -> R.string.diag_category_sensor
    DiagnosticsLog.Category.PREFS -> R.string.diag_category_prefs
    DiagnosticsLog.Category.WIDGET -> R.string.diag_category_widget
    DiagnosticsLog.Category.TILE -> R.string.diag_category_tile
    DiagnosticsLog.Category.PROFILE -> R.string.diag_category_profile
}

/**
 * A diagnostics-log timestamp as a person reads it.
 *
 * The timeline used to print Instant.toString(), so the log filter offered
 * "2026-09-04T14:22:31.918Z" as its user-facing bounds: the wrong timezone,
 * and a precision nobody needs.
 *
 * [fraction] positions the value inside [bounds], so the same function serves
 * the two fixed end labels and the two moving handles.
 */
internal fun formatLogInstant(
    fallback: Instant,
    fraction: Float,
    bounds: DiagnosticsLog.TimelineBounds,
    locale: Locale
): String {
    val span = bounds.latest.toEpochMilli() - bounds.earliest.toEpochMilli()
    val at = if (span <= 0L) {
        fallback
    } else {
        Instant.ofEpochMilli(
            bounds.earliest.toEpochMilli() + (span * fraction.coerceIn(0f, 1f)).toLong()
        )
    }
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(at)
}
