package com.openlumen.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openlumen.R
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.service.driverTargets
import com.openlumen.external.ExternalIntentLauncher
import com.openlumen.external.ExternalIntentResult
import com.openlumen.service.SystemColorConflict
import com.openlumen.service.SystemColorSettingsWatcher
import com.openlumen.service.SystemColorState
import com.openlumen.service.readSystemColorState
import com.openlumen.service.settingsAction
import com.openlumen.service.systemColorConflicts

/**
 * Names the system colour features that overwrite the SurfaceFlinger driver's
 * matrix, each with a button to the screen that turns it off. Under the overlay
 * driver it names the system grayscale modes instead, which take the colour out
 * of the overlay's tint along with everything else (#32).
 *
 * The service already puts the filter back after every change, but while one
 * of these is on the user sees a flicker on each change and the system feature
 * does nothing visible, so the lasting fix is switching it off. The rows are
 * read on START and RESUME only: coming back from system settings is the
 * moment they change, and a recomposition must not cost a Binder call.
 */
@Composable
internal fun SystemColorConflictCard(
    engine: EngineKind?,
    modifier: Modifier = Modifier
) {
    if (engine != EngineKind.SURFACE_FLINGER && engine != EngineKind.OVERLAY) return
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember(engine) { mutableStateOf(readSystemColorState(ctx.contentResolver)) }
    val conflicts = systemColorConflicts(engine, state)
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                state = readSystemColorState(ctx.contentResolver)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (conflicts.isEmpty()) return
    // Night Light off now but on a schedule is a warning about later, not
    // something happening on screen; saying "overriding" for it read as
    // wrong to anyone looking at Night Light switched off.
    val onlyScheduled = conflicts == setOf(SystemColorConflict.NIGHT_LIGHT_SCHEDULED)
    val overlay = engine == EngineKind.OVERLAY
    var settingsError by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(
                    when {
                        overlay -> R.string.system_conflict_overlay_gray_title
                        onlyScheduled -> R.string.system_conflict_scheduled_title
                        else -> R.string.system_conflict_title
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(
                    when {
                        overlay -> R.string.system_conflict_overlay_gray_body
                        onlyScheduled -> R.string.system_conflict_scheduled_body
                        else -> R.string.system_conflict_body
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            for (conflict in SystemColorConflict.entries.filter { it in conflicts }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        conflict.label(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    LumenTextButton(onClick = {
                        val intent = Intent(conflict.settingsAction())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        settingsError = ExternalIntentLauncher.launch(ctx, intent) !=
                            ExternalIntentResult.Launched
                    }) {
                        Text(stringResource(R.string.system_conflict_open_settings))
                    }
                }
            }
            if (settingsError) {
                Text(
                    stringResource(R.string.external_intent_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun SystemColorConflict.label(state: SystemColorState): String = when (this) {
    SystemColorConflict.NIGHT_LIGHT_ON -> stringResource(R.string.system_conflict_night_light_on)
    SystemColorConflict.NIGHT_LIGHT_SCHEDULED -> when (state.nightLightAutoMode) {
        SystemColorSettingsWatcher.NIGHT_AUTO_MODE_TWILIGHT ->
            stringResource(R.string.system_conflict_night_light_scheduled_sunset)
        SystemColorSettingsWatcher.NIGHT_AUTO_MODE_CUSTOM -> {
            val startMs = state.nightLightCustomStartMs ?: DEFAULT_NIGHT_LIGHT_START_MS
            val time = java.time.LocalTime.ofSecondOfDay(
                (startMs / 1000).coerceIn(0L, 86_399L)
            )
            val formatted = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                .format(java.util.Date.from(time.atDate(java.time.LocalDate.now())
                    .atZone(java.time.ZoneId.systemDefault()).toInstant()))
            stringResource(R.string.system_conflict_night_light_scheduled_at, formatted)
        }
        else -> stringResource(R.string.system_conflict_night_light_scheduled)
    }
    SystemColorConflict.EXTRA_DIM_ON -> stringResource(R.string.system_conflict_extra_dim_on)
    SystemColorConflict.COLOR_INVERSION_ON -> stringResource(R.string.system_conflict_inversion_on)
    SystemColorConflict.READING_MODE_ON -> stringResource(R.string.system_conflict_reading_mode_on)
    SystemColorConflict.GRAYSCALE_CORRECTION_ON ->
        stringResource(R.string.system_conflict_grayscale_correction_on)
}

/**
 * AOSP's `config_defaultNightDisplayCustomStartTime` (22:00), which the row
 * falls back to when the user never moved the start.
 */
private const val DEFAULT_NIGHT_LIGHT_START_MS = 22L * 60 * 60 * 1000

/**
 * The driver the service will be running, as far as the UI can tell: a pin it
 * honours, otherwise Auto's choice from the probes. Mirrors
 * `EngineController.resolveDesiredEngineKind`; null until the probes are in.
 * [targets] is `driverTargets` for the settings in question, the list the
 * service resolves Auto against.
 */
internal fun expectedEngineKind(
    selected: EngineKindDto,
    forcePinned: Boolean,
    probes: List<DriverProbe.Probe>,
    targets: List<LumenMatrix>
): EngineKind? {
    val pinned = when (selected) {
        EngineKindDto.Auto -> null
        EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
        EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
        EngineKindDto.Kcal -> EngineKind.KCAL
        EngineKindDto.Overlay -> EngineKind.OVERLAY
    }
    if (pinned != null) {
        val available = probes.any { it.engine.kind == pinned && it.available }
        if (DriverProbe.honourPinnedEngine(forcePinned, available)) return pinned
    }
    return DriverProbe.bestAvailableKind(probes, targets)
}

/**
 * What the system's Night Light shows in place of [matrix]'s warmth when
 * [prefs] run on the System Night Light driver (C364). Null when it shows as
 * asked, when another driver runs, or before the probes are in.
 */
internal fun nightLightClampFor(
    prefs: Preferences,
    probes: List<DriverProbe.Probe>,
    matrix: LumenMatrix
): SecureSettingsEngine.NightLightClamp? {
    val kind = expectedEngineKind(
        prefs.engine,
        prefs.forcePinnedEngine,
        probes,
        driverTargets(prefs)
    )
    if (kind != EngineKind.COLOR_DISPLAY_MANAGER) return null
    val engine = probes.firstOrNull { it.engine.kind == kind }?.engine as? SecureSettingsEngine
    return engine?.nightLightClamp(matrix)
}
