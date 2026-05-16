package com.openlumen.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.engine.EngineKind
import com.openlumen.prefs.EngineKindDto
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun DriverScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs by vm.state.collectAsState()
    val probes by vm.probes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.driver_title), style = MaterialTheme.typography.titleMedium)

        val choices = listOf(
            EngineKindDto.Auto to stringResource(R.string.driver_auto),
            EngineKindDto.ColorDisplayManager to stringResource(R.string.driver_color_display),
            EngineKindDto.SurfaceFlinger to stringResource(R.string.driver_surfaceflinger),
            EngineKindDto.Kcal to stringResource(R.string.driver_kcal),
            EngineKindDto.Overlay to stringResource(R.string.driver_overlay)
        )

        choices.forEach { (kind, label) ->
            val availability = kind.toEngineKind()
                ?.let { engineKind -> probes.firstOrNull { it.engine.kind == engineKind }?.available }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.engine == kind)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable { vm.setEngine(kind) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = prefs.engine == kind,
                        onClick = { vm.setEngine(kind) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (kind != EngineKindDto.Auto && availability != null) {
                            Text(
                                if (availability) stringResource(R.string.driver_available)
                                else stringResource(R.string.driver_not_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LumenButton(onClick = { vm.refreshProbes() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.driver_refresh))
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
                val granted = hasWriteSecureSettings(ctx)
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
                Text(
                    command,
                    style = MaterialTheme.typography.bodySmall
                )
                LumenOutlinedButton(
                    onClick = {
                        copyToClipboard(
                            ctx,
                            label = ctx.getString(R.string.clipboard_adb_grant),
                            text = command
                        )
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.command_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.driver_copy_command)) }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Driver report (C02). One button copies, one shares — pick whichever
        // fits the reporter's workflow. The report itself is built lazily on
        // click so it always reflects the latest prefs + probe state.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LumenOutlinedButton(
                onClick = {
                    val report = vm.buildDriverReport()
                    copyToClipboard(
                        ctx,
                        label = ctx.getString(R.string.clipboard_driver_report),
                        text = report
                    )
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.report_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.driver_copy_report)) }

            LumenButton(
                onClick = {
                    val report = vm.buildDriverReport()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.driver_report_subject))
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    ctx.startActivity(
                        Intent.createChooser(send, ctx.getString(R.string.driver_share_report))
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.driver_share_report)) }
        }
    }
}

private fun EngineKindDto.toEngineKind(): EngineKind? = when (this) {
    EngineKindDto.Auto -> null
    EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
    EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
    EngineKindDto.Kcal -> EngineKind.KCAL
    EngineKindDto.Overlay -> EngineKind.OVERLAY
}

private fun hasWriteSecureSettings(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED

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
