package com.openlumen.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openlumen.R
import com.openlumen.prefs.Preferences
import com.openlumen.shizuku.AppSaturationOutcome
import com.openlumen.shizuku.ShizukuState
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.labeledSliderSemantics
import com.openlumen.ui.components.lumenSliderColors
import com.openlumen.viewmodel.OpenLumenScreenModel
import kotlin.math.roundToInt

/**
 * Per-app saturation (C287): the apps the user picked, each with its own
 * level, drawn by the system for that app alone while the rest of the screen
 * keeps its color. It runs through Shizuku, so the card says where Shizuku
 * stands whenever it cannot act.
 *
 * Apps come from the system's own app picker, so OpenLumen never lists what
 * is installed. A new app starts fully gray, which is what most people add
 * one for.
 */
@Composable
internal fun AppSaturationCard(vm: OpenLumenScreenModel, levels: Map<String, Int>) {
    val ctx = LocalContext.current
    val ui by vm.appSaturation.collectAsStateWithLifecycle()
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Starting or allowing Shizuku happens in Shizuku's own app.
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshShizuku()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currentLevels by rememberUpdatedState(levels)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.component?.packageName
        if (result.resultCode == Activity.RESULT_OK && picked != null) {
            // Picking an app already on the list keeps its level.
            vm.setAppSaturation(picked, currentLevels[picked] ?: 0)
        }
    }
    val pickerMissing = stringResource(R.string.app_saturation_picker_missing)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.app_saturation_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.app_saturation_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!ui.supported) {
                Text(
                    stringResource(R.string.app_saturation_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            appSaturationShizukuRes(shizuku.state)?.let { line ->
                Text(
                    stringResource(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val actionable = shizuku.state == ShizukuState.READY ||
                shizuku.state == ShizukuState.PERMISSION_NEEDED
            // With Shizuku gone, nothing can set a level again; a row can
            // still come off the list (the view model explains why).
            val removable = actionable || shizuku.state == ShizukuState.NOT_INSTALLED
            levels.entries
                .map { (packageName, level) -> Triple(packageName, appLabel(ctx, packageName), level) }
                .sortedBy { (_, label, _) -> label.lowercase() }
                .forEach { (packageName, label, level) ->
                    AppSaturationRow(
                        label = label,
                        level = level,
                        adjustable = actionable && !ui.busy,
                        removable = removable && !ui.busy,
                        onLevel = { vm.setAppSaturation(packageName, it) },
                        onRemove = { vm.removeAppSaturation(packageName) }
                    )
                }
            if (levels.size >= Preferences.APP_SATURATION_MAX_APPS) {
                Text(
                    stringResource(R.string.app_saturation_full, Preferences.APP_SATURATION_MAX_APPS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LumenOutlinedButton(
                    onClick = {
                        try {
                            picker.launch(appPickerIntent())
                        } catch (missing: ActivityNotFoundException) {
                            Toast.makeText(ctx, pickerMissing, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = actionable && !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (ui.busy) R.string.driver_shizuku_granting else R.string.app_saturation_add
                        )
                    )
                }
            }
            // NotReady is the Shizuku line above.
            if (ui.problem is AppSaturationOutcome.Failed) {
                Text(
                    stringResource(R.string.app_saturation_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

@Composable
private fun AppSaturationRow(
    label: String,
    level: Int,
    adjustable: Boolean,
    removable: Boolean,
    onLevel: (Int) -> Unit,
    onRemove: () -> Unit
) {
    // Keyed on the saved level so a change the system refused springs back.
    var draft by remember(level) { mutableFloatStateOf(level.toFloat()) }
    val shown = draft.roundToInt()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.home_percent_value, shown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRemove, enabled = removable) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.app_saturation_remove_named, label)
                )
            }
        }
        Slider(
            value = draft,
            onValueChange = { draft = it.coerceIn(0f, Preferences.APP_SATURATION_FULL.toFloat()) },
            onValueChangeFinished = { if (draft.roundToInt() != level) onLevel(draft.roundToInt()) },
            valueRange = 0f..Preferences.APP_SATURATION_FULL.toFloat(),
            steps = 9,
            enabled = adjustable,
            modifier = Modifier.labeledSliderSemantics(
                name = stringResource(R.string.app_saturation_level_named, label),
                valueDescription = stringResource(R.string.home_percent_state, shown)
            ),
            colors = lumenSliderColors()
        )
    }
}

/**
 * The system's app picker over every launcher app. It hands back the picked
 * activity's component; the package is all this needs.
 */
private fun appPickerIntent(): Intent =
    Intent(Intent.ACTION_PICK_ACTIVITY)
        .putExtra(
            Intent.EXTRA_INTENT,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )

/**
 * The app's name, or its package name when it has gone or is hidden. The
 * manifest's launcher query makes launcher apps visible for this lookup.
 */
private fun appLabel(context: Context, packageName: String): String =
    try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (missing: PackageManager.NameNotFoundException) {
        packageName
    }

@StringRes
private fun appSaturationShizukuRes(state: ShizukuState): Int? = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.app_saturation_shizuku_not_installed
    ShizukuState.NOT_RUNNING -> R.string.app_saturation_shizuku_not_running
    ShizukuState.OUTDATED -> R.string.app_saturation_shizuku_outdated
    ShizukuState.PERMISSION_NEEDED -> R.string.app_saturation_shizuku_permission_needed
    ShizukuState.PERMISSION_DENIED -> R.string.app_saturation_shizuku_permission_denied
    ShizukuState.READY -> null
}
