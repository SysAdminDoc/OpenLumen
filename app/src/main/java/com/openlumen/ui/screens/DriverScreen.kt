package com.openlumen.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.openlumen.ui.components.PreferencesPlaceholder
import com.openlumen.ui.components.LumenSwitch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.EngineKind
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.external.ExternalIntentLauncher
import com.openlumen.external.ExternalIntentResult
import com.openlumen.prefs.EngineKindDto
import com.openlumen.shizuku.ShizukuGrantOutcome
import com.openlumen.shizuku.ShizukuState
import com.openlumen.ui.components.CommandBlock
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.viewmodel.OpenLumenScreenModel
import com.openlumen.viewmodel.OpenLumenViewModel
import com.openlumen.viewmodel.ShizukuUiState

@Composable
fun DriverScreen(vm: OpenLumenScreenModel = hiltViewModel<OpenLumenViewModel>()) {
    val ctx = LocalContext.current
    val prefs by vm.state.collectAsStateWithLifecycle()
    // Nothing to draw yet. Without this the first frame after a cold start
    // showed `Preferences()`: the master switch read Off and every slider sat
    // at its default before the screen jumped to the user's values (C329).
    val preferencesLoaded by vm.preferencesLoaded.collectAsStateWithLifecycle()
    if (!preferencesLoaded) {
        PreferencesPlaceholder()
        return
    }

    val probes by vm.probes.collectAsStateWithLifecycle()
    val probesRefreshing by vm.probesRefreshing.collectAsStateWithLifecycle()
    val probeError by vm.probeError.collectAsStateWithLifecycle()
    var shareError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(topLevelScrollPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.driver_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )

        val choices = listOf(
            EngineKindDto.Auto to stringResource(R.string.driver_auto),
            EngineKindDto.ColorDisplayManager to stringResource(R.string.driver_color_display),
            EngineKindDto.SurfaceFlinger to stringResource(R.string.driver_surfaceflinger),
            EngineKindDto.Kcal to stringResource(R.string.driver_kcal),
            EngineKindDto.Overlay to stringResource(R.string.driver_overlay)
        )

        // Resolve which engine the "Auto" choice would pick right now from
        // the same ordered capability resolver used by EngineController.
        val autoResolvedLabelRes: Int? = DriverProbe.bestAvailableKind(probes)
            ?.let(::engineKindLabelRes)

        choices.forEach { (kind, label) ->
            val availability = kind.toEngineKind()
                ?.let { engineKind -> probes.firstOrNull { it.engine.kind == engineKind }?.available }
            // C253: force-pin makes every driver selectable, because the user
            // is overriding a probe verdict they believe is wrong.
            val selectable =
                kind == EngineKindDto.Auto || availability != false || prefs.forcePinnedEngine
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.engine == kind)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = prefs.engine == kind,
                        enabled = selectable,
                        onClick = { vm.setEngine(kind) },
                        role = Role.RadioButton
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        // A row the device cannot use kept full-contrast text
                        // while its radio button went grey, so the only thing
                        // saying "unavailable" was a control most of the row
                        // does not touch. Material's disabled alpha, applied
                        // to the whole row, is what that state looks like
                        // everywhere else in the app.
                        .alpha(if (selectable) 1f else 0.38f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = prefs.engine == kind,
                        onClick = null,
                        enabled = selectable
                    )
                    // Without onClick the radio has no padding of its own, so
                    // its circle touched the first letter of the label.
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectable) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (prefs.engine == kind) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (kind == EngineKindDto.Auto) {
                            // Surface which engine Auto would pick so the
                            // user knows what they're getting; on a device
                            // with no available root engine or overlay
                            // permission, we tell
                            // them how to get the rootless fallback up.
                            val hint = if (autoResolvedLabelRes != null && probes.isNotEmpty()) {
                                stringResource(R.string.driver_auto_resolved, stringResource(autoResolvedLabelRes))
                            } else if (probes.isNotEmpty()) {
                                stringResource(R.string.driver_auto_resolved_none)
                            } else {
                                null
                            }
                            if (hint != null) {
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (availability != null) {
                            Text(
                                if (availability) stringResource(R.string.driver_available)
                                else stringResource(R.string.driver_not_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // C253 / closed issue #16. Root-hiding setups make `su` detection
        // unreliable, so let the user override a probe verdict rather than
        // being silently reverted to Auto. Only meaningful with a driver
        // pinned, so the control is hidden while Auto is selected.
        if (prefs.engine != EngineKindDto.Auto) {
            val forceLabel = stringResource(R.string.driver_force_pinned)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The whole row is the switch, so TalkBack reads the
                    // label and state as one stop.
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = prefs.forcePinnedEngine,
                            role = Role.Switch,
                            onValueChange = { vm.setForcePinnedEngine(it) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            forceLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        LumenSwitch(
                            checked = prefs.forcePinnedEngine,
                            onCheckedChange = null
                        )
                    }
                    Text(
                        stringResource(R.string.driver_force_pinned_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.driver_color_contract),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.driver_aapm_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.driver_aapm_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        probeError?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                // This appears after the user taps refresh. Without a live
                // region a screen reader says nothing at all: the text is
                // simply there the next time you happen to reach it.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        LumenButton(
            onClick = { vm.refreshProbes() },
            enabled = !probesRefreshing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (probesRefreshing) R.string.driver_refreshing else R.string.driver_refresh
                )
            )
        }

        // A system row the driver was holding changed underneath it, so the
        // filter stepped aside and turned itself off (C346). Errors here are
        // the user's own setting winning, not a fault, so this reads as a
        // notice rather than an error surface.
        if (prefs.secureRowsReleased.isNotEmpty()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.driver_secure_rows_released_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    // Resolved before the join, not inside it: stringResource is
                    // itself @Composable and cannot be called from a lambda the
                    // compiler does not see as composable scope.
                    // The one localised list separator in the app; ja uses a
                    // full-width comma.
                    val separator = stringResource(R.string.preset_capability_separator)
                    val rowNames = prefs.secureRowsReleased
                        .map { stringResource(secureRowLabelRes(it)) }
                        .joinToString(separator = separator)
                    Text(
                        stringResource(R.string.driver_secure_rows_released_body, rowNames),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LumenOutlinedButton(onClick = { vm.dismissSecureRowsReleased() }) {
                        Text(stringResource(R.string.driver_secure_rows_released_dismiss))
                    }
                }
            }
        }

        // Overlay alpha cap explainer (C09): visible when the Overlay engine is
        // either currently selected or the only available rootless engine the
        // user is likely to land on.
        if (prefs.engine == EngineKindDto.Overlay || prefs.engine == EngineKindDto.Auto) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.overlay_caveats_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.overlay_alpha_cap_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.overlay_touch_pass_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // WRITE_SECURE_SETTINGS grant status + adb command (C07).
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Read here so a Shizuku grant recomposes this card and the
                // permission check below runs again (C254).
                val shizuku by vm.shizuku.collectAsStateWithLifecycle()
                val granted = hasWriteSecureSettings(ctx)
                val grantedThroughShizuku = stringResource(R.string.driver_shizuku_done)
                // Here rather than in the Shizuku action, which leaves
                // composition the moment the grant lands.
                LaunchedEffect(shizuku.outcome) {
                    if (shizuku.outcome == ShizukuGrantOutcome.Granted) {
                        Toast.makeText(ctx, grantedThroughShizuku, Toast.LENGTH_SHORT).show()
                        vm.consumeShizukuOutcome()
                    }
                }
                Text(
                    stringResource(R.string.driver_grant_secure_settings),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (granted) stringResource(R.string.driver_grant_status_granted)
                    else stringResource(R.string.driver_grant_status_not_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val command = adbGrantCommand(ctx.packageName)
                val clipboardAdbGrant = stringResource(R.string.clipboard_adb_grant)
                val commandCopied = stringResource(R.string.command_copied)
                CommandBlock(text = command)
                LumenOutlinedButton(
                    onClick = {
                        copyToClipboard(
                            ctx,
                            label = clipboardAdbGrant,
                            text = command
                        )
                        Toast.makeText(
                            ctx,
                            commandCopied,
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.driver_copy_command)) }
                if (!granted) {
                    ShizukuGrantAction(shizuku = shizuku, vm = vm)
                }
            }
        }

        AppSaturationCard(vm = vm, levels = prefs.appSaturation)

        Spacer(Modifier.height(4.dp))

        // Driver report (C02). One button copies, one shares — pick whichever
        // fits the reporter's workflow. The report itself is built lazily on
        // click so it always reflects the latest prefs + probe state.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val clipboardDriverReport = stringResource(R.string.clipboard_driver_report)
            val reportCopied = stringResource(R.string.report_copied)
            val driverReportSubject = stringResource(R.string.driver_report_subject)
            val driverShareReport = stringResource(R.string.driver_share_report)
            LumenOutlinedButton(
                onClick = {
                    val report = vm.buildDriverReport()
                    copyToClipboard(
                        ctx,
                        label = clipboardDriverReport,
                        text = report
                    )
                    Toast.makeText(
                        ctx,
                        reportCopied,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.driver_copy_report)) }

            LumenButton(
                onClick = {
                    val report = vm.buildDriverReport()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, driverReportSubject)
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    shareError = ExternalIntentLauncher.share(
                        context = ctx,
                        sendIntent = send,
                        chooserTitle = driverShareReport
                    ) != ExternalIntentResult.Launched
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.driver_share_report)) }
            if (shareError) {
                Text(
                    stringResource(R.string.external_intent_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Shared with PresetsScreen, which needs the same mapping to resolve the
  * capabilities of the driver that will actually run. */
internal fun EngineKindDto.toEngineKind(): EngineKind? = when (this) {
    EngineKindDto.Auto -> null
    EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
    EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
    EngineKindDto.Kcal -> EngineKind.KCAL
    EngineKindDto.Overlay -> EngineKind.OVERLAY
}

/**
 * Map an [EngineKind] to the user-facing label resource used by the Driver
 * rows so the "Auto picks: X" hint reuses the same translations and stays
 * in sync across locales.
 */
private fun engineKindLabelRes(kind: EngineKind): Int = when (kind) {
    EngineKind.COLOR_DISPLAY_MANAGER -> R.string.driver_color_display
    EngineKind.SURFACE_FLINGER -> R.string.driver_surfaceflinger
    EngineKind.KCAL -> R.string.driver_kcal
    EngineKind.OVERLAY -> R.string.driver_overlay
}

private fun hasWriteSecureSettings(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The on-device route to WRITE_SECURE_SETTINGS (C254), under the adb command.
 * Enabled only when Shizuku can act on a tap; otherwise the line under it
 * says what to do in Shizuku first. Shizuku's own prompt is the only
 * question asked, and it is Shizuku's to ask.
 */
@Composable
private fun ShizukuGrantAction(shizuku: ShizukuUiState, vm: OpenLumenScreenModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Installing, starting or allowing happens in Shizuku's own app, so look
    // again whenever this screen comes back to the front.
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshShizuku()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val actionable = shizuku.state == ShizukuState.READY ||
        shizuku.state == ShizukuState.PERMISSION_NEEDED
    LumenOutlinedButton(
        onClick = { vm.grantWithShizuku() },
        enabled = actionable && !shizuku.granting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(
                if (shizuku.granting) R.string.driver_shizuku_granting else R.string.driver_shizuku_grant
            )
        )
    }
    Text(
        stringResource(shizukuStateRes(shizuku.state)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val problem = when (shizuku.outcome) {
        ShizukuGrantOutcome.PermissionRefused -> R.string.driver_shizuku_refused
        is ShizukuGrantOutcome.Failed -> R.string.driver_shizuku_failed
        // NotReady is the state line above; Granted hides this action.
        else -> null
    }
    if (problem != null) {
        Text(
            stringResource(problem),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@StringRes
private fun shizukuStateRes(state: ShizukuState): Int = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.driver_shizuku_not_installed
    ShizukuState.NOT_RUNNING -> R.string.driver_shizuku_not_running
    ShizukuState.OUTDATED -> R.string.driver_shizuku_outdated
    ShizukuState.PERMISSION_NEEDED -> R.string.driver_shizuku_permission_needed
    ShizukuState.PERMISSION_DENIED -> R.string.driver_shizuku_permission_denied
    ShizukuState.READY -> R.string.driver_shizuku_ready
}

/**
 * Human name for a secure row OpenLumen let go of (C346). The key itself is
 * what the diagnostics log records; a user reading the Driver tab needs the
 * name of the setting they just changed. An unknown key falls back to the
 * generic label rather than showing a raw provider key.
 */
@StringRes
private fun secureRowLabelRes(key: String): Int = when (key) {
    SecureSettingsEngine.KEY_NIGHT_ACTIVATED -> R.string.secure_row_night_display
    SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED -> R.string.secure_row_extra_dim
    SecureSettingsEngine.KEY_CORRECTION_MODE -> R.string.secure_row_color_correction
    else -> R.string.secure_row_unknown
}

/**
 * Render the exact adb command the user needs to run. We tie it to the
 * resolved `packageName` so the debug build (`com.openlumen.debug`) shows the
 * right command — copy/paste against the release package fails with "has not
 * requested permission" which is the most-reported support footgun.
 */
private fun adbGrantCommand(packageName: String): String =
    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

private fun copyToClipboard(context: Context, label: String, text: String) {
    // Android 13+ shows its own copy confirmation animation, but the toast
    // from the caller is harmless and the API floor (26) needs the toast to
    // confirm. We don't gate on SDK version here.
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
