package com.openlumen.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openlumen.R
import com.openlumen.external.ExternalIntentResult
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.isValidFixedTimeWindow
import com.openlumen.schedule.isValidSolarLocation
import com.openlumen.service.ExactAlarmAccess
import com.openlumen.ui.components.PreferencesPlaceholder
import com.openlumen.ui.components.LightSensorCard
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LocationEntryDialog
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.LumenSwitch
import com.openlumen.ui.components.TimePickerDialog
import com.openlumen.ui.components.labeledSliderSemantics
import com.openlumen.ui.components.lumenSliderColors
import com.openlumen.viewmodel.OpenLumenViewModel
import com.openlumen.viewmodel.OpenLumenScreenModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun ScheduleScreen(
    vm: OpenLumenScreenModel = hiltViewModel<OpenLumenViewModel>(),
    enableSystemActions: Boolean = true
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs by vm.state.collectAsStateWithLifecycle()
    // Nothing to draw yet. Without this the first frame after a cold start
    // showed `Preferences()`: the master switch read Off and every slider sat
    // at its default before the screen jumped to the user's values (C329).
    val preferencesLoaded by vm.preferencesLoaded.collectAsStateWithLifecycle()
    if (!preferencesLoaded) {
        PreferencesPlaceholder()
        return
    }

    val lux by vm.lux.collectAsStateWithLifecycle()
    val lightSensorAvailable by vm.lightSensorAvailable.collectAsStateWithLifecycle()

    var canScheduleExactAlarms by remember(ctx, enableSystemActions) {
        mutableStateOf(
            !enableSystemActions || ExactAlarmAccess.canScheduleExactAlarms(ctx)
        )
    }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }
    // Set when the dialog was opened from the Solar row with no location yet:
    // saving it should also select the mode the user tapped.
    var selectSolarAfterLocation by rememberSaveable { mutableStateOf(false) }
    var showProgressiveEndPicker by rememberSaveable { mutableStateOf(false) }
    var equalFixedTimesError by rememberSaveable { mutableStateOf(false) }
    var exactAlarmSettingsError by rememberSaveable { mutableStateOf(false) }
    var sunsetOffsetDraft by remember {
        mutableFloatStateOf(prefs.schedule.sunsetOffsetMin.toFloat())
    }
    var sunriseOffsetDraft by remember {
        mutableFloatStateOf(prefs.schedule.sunriseOffsetMin.toFloat())
    }

    LaunchedEffect(prefs.schedule.sunsetOffsetMin) {
        sunsetOffsetDraft = prefs.schedule.sunsetOffsetMin.toFloat()
    }
    LaunchedEffect(prefs.schedule.sunriseOffsetMin) {
        sunriseOffsetDraft = prefs.schedule.sunriseOffsetMin.toFloat()
    }
    LaunchedEffect(prefs.schedule.mode) {
        equalFixedTimesError = false
    }

    if (enableSystemActions) DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                val refreshed = ExactAlarmAccess.canScheduleExactAlarms(ctx)
                if (refreshed != canScheduleExactAlarms) {
                    canScheduleExactAlarms = refreshed
                    vm.reconcileExactAlarmPermission()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (enableSystemActions) LaunchedEffect(ctx) {
        val refreshed = ExactAlarmAccess.canScheduleExactAlarms(ctx)
        if (refreshed != canScheduleExactAlarms) {
            canScheduleExactAlarms = refreshed
            vm.reconcileExactAlarmPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(topLevelScrollPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.schedule_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        // Fixed-time schedules follow the device zone. A city-selected solar
        // schedule keeps the city's IANA zone so its wall-clock sunrise and
        // sunset remain stable when the device travels.
        // Compose's own Locale.current rather than LocalConfiguration: the
        // configuration read is not observable, so a locale change would not
        // recompose the times.
        val locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
        val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
        val solarTimezone = prefs.schedule.solarTimezone
        val shownZone = solarTimezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?.takeIf { prefs.schedule.mode == ScheduleModeDto.Solar }
        val deviceZone = ZoneId.systemDefault()
        val zone = shownZone ?: deviceZone
        // A preview renders the zone name for a fixed summer day. The real
        // date picks daylight or standard time, so the screenshot references
        // flipped twice a year with nothing on the screen having changed.
        val now = if (LocalInspectionMode.current) PREVIEW_INSTANT else Instant.now()
        val zoneName = zoneDisplayName(zone, locale, now)
        Text(
            if (shownZone != null) {
                stringResource(R.string.schedule_solar_timezone, zoneName)
            } else {
                stringResource(R.string.schedule_timezone, zoneName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            zone.id,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val modes = listOf(
            ScheduleModeDto.AlwaysOff to stringResource(R.string.schedule_off),
            ScheduleModeDto.AlwaysOn  to stringResource(R.string.schedule_always),
            ScheduleModeDto.FixedTime to stringResource(R.string.schedule_fixed),
            ScheduleModeDto.Solar     to stringResource(R.string.schedule_solar),
            ScheduleModeDto.UntilNextAlarm to stringResource(R.string.schedule_until_next_alarm)
        )
        val solarLocationValid = isValidSolarLocation(
            prefs.schedule.latitude,
            prefs.schedule.longitude
        )
        modes.forEach { (mode, label) ->
            val solarUnavailable = mode == ScheduleModeDto.Solar && !solarLocationValid
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (solarUnavailable && prefs.schedule.mode == mode) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else if (prefs.schedule.mode == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = prefs.schedule.mode == mode,
                        onClick = {
                            when (scheduleModeRowAction(mode, solarLocationValid)) {
                                ScheduleModeRowAction.RequestLocation -> {
                                    selectSolarAfterLocation = true
                                    showLocationDialog = true
                                }
                                ScheduleModeRowAction.SelectMode -> vm.setScheduleMode(mode)
                            }
                        },
                        role = Role.RadioButton
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // onClick = null: the Card's selectable() is the single
                    // accessibility node, so TalkBack announces the label plus
                    // "selected/not selected" and the RadioButton role once.
                    RadioButton(
                        selected = prefs.schedule.mode == mode,
                        onClick = null,
                        enabled = !solarUnavailable
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (solarUnavailable) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (prefs.schedule.mode == mode) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (solarUnavailable) {
                            Text(
                                stringResource(R.string.schedule_solar_location_tap_to_set),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        if (shouldWarnAboutSolarLocation(prefs.schedule.mode, solarLocationValid)) {
            SolarLocationWarningCard(onSetLocation = { showLocationDialog = true })
        }

        if (
            ExactAlarmAccess.scheduleModeNeedsExactAlarm(prefs.schedule.mode) &&
            !canScheduleExactAlarms
        ) {
            ExactAlarmWarningCard(
                onOpenSettings = {
                    exactAlarmSettingsError = ExactAlarmAccess.openExactAlarmSettings(ctx) !=
                        ExternalIntentResult.Launched
                    canScheduleExactAlarms = ExactAlarmAccess.canScheduleExactAlarms(ctx)
                },
                settingsError = exactAlarmSettingsError
            )
        }

        if (prefs.schedule.mode == ScheduleModeDto.FixedTime) {
            Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenOutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.schedule_time_value,
                                stringResource(R.string.schedule_start),
                                formatScheduleTime(
                                    prefs.schedule.startHour,
                                    prefs.schedule.startMinute,
                                    use24Hour,
                                    locale
                                )
                            )
                        )
                    }
                    LumenOutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.schedule_time_value,
                                stringResource(R.string.schedule_end),
                                formatScheduleTime(
                                    prefs.schedule.endHour,
                                    prefs.schedule.endMinute,
                                    use24Hour,
                                    locale
                                )
                            )
                        )
                    }
                    if (equalFixedTimesError) {
                        Text(
                            stringResource(R.string.schedule_equal_times_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (prefs.schedule.mode == ScheduleModeDto.Solar) {
            Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenOutlinedButton(
                        onClick = { showLocationDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val lat = prefs.schedule.latitude
                        val lng = prefs.schedule.longitude
                        Text(
                            if (lat == null || lng == null)
                                stringResource(R.string.schedule_set_location)
                            else
                                stringResource(
                                    if (prefs.schedule.autoLocation) {
                                        R.string.schedule_location_value_auto
                                    } else {
                                        R.string.schedule_location_value
                                    },
                                    String.format(Locale.ROOT, "%.3f, %.3f", lat, lng)
                                )
                        )
                    }

                    val sunsetOffsetLabel = stringResource(
                        R.string.schedule_sunset_offset,
                        sunsetOffsetDraft.roundToInt()
                    )
                    Text(
                        sunsetOffsetLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = sunsetOffsetDraft,
                        onValueChange = { sunsetOffsetDraft = it },
                        onValueChangeFinished = {
                            vm.setScheduleOffsets(
                                sunsetOffsetDraft.roundToInt(),
                                prefs.schedule.sunriseOffsetMin
                            )
                        },
                        valueRange = -180f..180f,
                        steps = 71,
                        modifier = Modifier.labeledSliderSemantics(
                            name = stringResource(R.string.schedule_sunset_offset_name),
                            valueDescription = sunsetOffsetLabel
                        ),
                        colors = lumenSliderColors()
                    )

                    val sunriseOffsetLabel = stringResource(
                        R.string.schedule_sunrise_offset,
                        sunriseOffsetDraft.roundToInt()
                    )
                    Text(
                        sunriseOffsetLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = sunriseOffsetDraft,
                        onValueChange = { sunriseOffsetDraft = it },
                        onValueChangeFinished = {
                            vm.setScheduleOffsets(
                                prefs.schedule.sunsetOffsetMin,
                                sunriseOffsetDraft.roundToInt()
                            )
                        },
                        valueRange = -180f..180f,
                        steps = 71,
                        modifier = Modifier.labeledSliderSemantics(
                            name = stringResource(R.string.schedule_sunrise_offset_name),
                            valueDescription = sunriseOffsetLabel
                        ),
                        colors = lumenSliderColors()
                    )
                }
            }
        }

        if (prefs.schedule.mode == ScheduleModeDto.UntilNextAlarm) {
            Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenOutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.schedule_time_value,
                                stringResource(R.string.schedule_start),
                                formatScheduleTime(
                                    prefs.schedule.startHour,
                                    prefs.schedule.startMinute,
                                    use24Hour,
                                    locale
                                )
                            )
                        )
                    }
                    Text(
                        stringResource(R.string.schedule_until_alarm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // C269. A single fade at the transition is not what people ask for:
        // they want the filter to keep deepening as the evening goes on.
        // Offered only for the two modes that have an opening moment to
        // measure from.
        if (
            prefs.schedule.mode == ScheduleModeDto.Solar ||
            prefs.schedule.mode == ScheduleModeDto.FixedTime
        ) {
            var progressiveEndIntensity by remember(prefs.schedule.progressiveEndIntensity) {
                mutableFloatStateOf(prefs.schedule.progressiveEndIntensity)
            }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.schedule_progressive_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.schedule_progressive_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LumenSwitch(
                            checked = prefs.schedule.progressiveIntensity,
                            onCheckedChange = { on ->
                                vm.setProgressiveIntensity(
                                    enabled = on,
                                    endHour = prefs.schedule.progressiveEndHour,
                                    endMinute = prefs.schedule.progressiveEndMinute,
                                    endIntensity = prefs.schedule.progressiveEndIntensity
                                )
                            }
                        )
                    }
                    if (prefs.schedule.progressiveIntensity) {
                        Spacer(Modifier.height(8.dp))
                        LumenOutlinedButton(
                            onClick = { showProgressiveEndPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    R.string.schedule_progressive_end,
                                    formatScheduleTime(
                                        prefs.schedule.progressiveEndHour,
                                        prefs.schedule.progressiveEndMinute,
                                        use24Hour,
                                        locale
                                    )
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.schedule_progressive_end_intensity,
                                (progressiveEndIntensity * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = progressiveEndIntensity,
                            onValueChange = { progressiveEndIntensity = it },
                            onValueChangeFinished = {
                                vm.setProgressiveIntensity(
                                    enabled = true,
                                    endHour = prefs.schedule.progressiveEndHour,
                                    endMinute = prefs.schedule.progressiveEndMinute,
                                    endIntensity = progressiveEndIntensity
                                )
                            },
                            modifier = Modifier.labeledSliderSemantics(
                                name = stringResource(R.string.schedule_progressive_end_intensity_name),
                                valueDescription = stringResource(
                                    R.string.schedule_progressive_end_intensity,
                                    (progressiveEndIntensity * 100).roundToInt()
                                )
                            ),
                            colors = lumenSliderColors()
                        )
                    }
                }
            }
        }

        LightSensorCard(
            enabled = prefs.lightSensorEnabled,
            threshold = prefs.lightSensorLuxThreshold,
            currentLux = lux,
            available = lightSensorAvailable,
            onToggle = { vm.setLightSensor(it, prefs.lightSensorLuxThreshold) },
            onThresholdChange = { vm.setLightSensor(prefs.lightSensorEnabled, it) },
            onUseCurrent = { if (lux >= 0) vm.setLightSensor(prefs.lightSensorEnabled, lux) }
        )

        // Smooth-transition duration (C23/C24). Visible regardless of mode
        // because both fixed-time and solar modes use the same ramp path —
        // the duration is per-app, not per-mode.
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.transition_duration_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.transition_duration_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Stays on Custom for the rest of this visit when the user
                // picked it, so dragging the slider onto a preset value (both
                // ends included) does not collapse it under their finger.
                var customChosen by rememberSaveable { mutableStateOf(false) }
                val customSelected =
                    isCustomTransitionSelected(prefs.transitionDurationMs, customChosen)
                @Composable
                fun TransitionOption(selected: Boolean, label: String, onClick: () -> Unit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = onClick,
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                TRANSITION_PRESET_DURATIONS_MS.forEach { durationMs ->
                    TransitionOption(
                        selected = isTransitionPresetSelected(
                            durationMs,
                            prefs.transitionDurationMs,
                            customChosen
                        ),
                        label = stringResource(transitionPresetLabelRes(durationMs)),
                        onClick = {
                            customChosen = false
                            vm.setTransitionDuration(durationMs)
                        }
                    )
                }
                TransitionOption(
                    selected = customSelected,
                    label = stringResource(R.string.transition_custom),
                    onClick = {
                        if (!customSelected) {
                            customChosen = true
                            vm.setTransitionDuration(CUSTOM_TRANSITION_DEFAULT_MS)
                        }
                    }
                )
                if (customSelected) {
                    var customMinutes by remember(prefs.transitionDurationMs) {
                        mutableFloatStateOf(
                            customTransitionMinutes(prefs.transitionDurationMs).toFloat()
                        )
                    }
                    val customLabel = stringResource(
                        R.string.transition_custom_value,
                        customMinutes.roundToInt()
                    )
                    Text(customLabel, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.roundToInt().toFloat() },
                        onValueChangeFinished = {
                            vm.setTransitionDuration(customMinutes.roundToInt() * 60_000L)
                        },
                        valueRange = 1f..CUSTOM_TRANSITION_MAX_MIN.toFloat(),
                        steps = CUSTOM_TRANSITION_SLIDER_STEPS,
                        modifier = Modifier.labeledSliderSemantics(
                            name = stringResource(R.string.transition_custom_name),
                            valueDescription = customLabel
                        ),
                        colors = lumenSliderColors()
                    )
                }
            }
        }
    }

    if (showProgressiveEndPicker) {
        TimePickerDialog(
            title = stringResource(R.string.schedule_progressive_end_title),
            initialHour = prefs.schedule.progressiveEndHour,
            initialMinute = prefs.schedule.progressiveEndMinute,
            onDismiss = { showProgressiveEndPicker = false },
            onConfirm = { h, m ->
                vm.setProgressiveIntensity(
                    enabled = true,
                    endHour = h,
                    endMinute = m,
                    endIntensity = prefs.schedule.progressiveEndIntensity
                )
                showProgressiveEndPicker = false
            }
        )
    }

    if (showStartPicker) {
        TimePickerDialog(
            title = stringResource(R.string.schedule_start),
            initialHour = prefs.schedule.startHour,
            initialMinute = prefs.schedule.startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { h, m ->
                val valid = prefs.schedule.mode != ScheduleModeDto.FixedTime ||
                    isValidFixedTimeWindow(
                        LocalTime.of(h, m),
                        LocalTime.of(prefs.schedule.endHour, prefs.schedule.endMinute)
                    )
                if (valid) {
                    vm.setScheduleTimes(h, m, prefs.schedule.endHour, prefs.schedule.endMinute)
                    equalFixedTimesError = false
                } else {
                    equalFixedTimesError = true
                }
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            title = stringResource(R.string.schedule_end),
            initialHour = prefs.schedule.endHour,
            initialMinute = prefs.schedule.endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { h, m ->
                val valid = isValidFixedTimeWindow(
                    LocalTime.of(prefs.schedule.startHour, prefs.schedule.startMinute),
                    LocalTime.of(h, m)
                )
                if (valid) {
                    vm.setScheduleTimes(prefs.schedule.startHour, prefs.schedule.startMinute, h, m)
                    equalFixedTimesError = false
                } else {
                    equalFixedTimesError = true
                }
                showEndPicker = false
            }
        )
    }
    if (showLocationDialog) {
        LocationEntryDialog(
            initialLat = prefs.schedule.latitude,
            initialLng = prefs.schedule.longitude,
            initialTimezone = prefs.schedule.solarTimezone,
            initialAutoLocation = prefs.schedule.autoLocation,
            lastAutoFixAtMs = prefs.schedule.locationFixAtMs,
            onDismiss = {
                showLocationDialog = false
                selectSolarAfterLocation = false
            },
            onSave = { lat, lng, solarTimezone, autoLocation ->
                vm.saveLocation(
                    lat = lat,
                    lng = lng,
                    solarTimezone = solarTimezone,
                    autoLocation = autoLocation,
                    selectSolar = selectSolarAfterLocation
                )
                showLocationDialog = false
                selectSolarAfterLocation = false
            }
        )
    }
}

@Composable
private fun ExactAlarmWarningCard(
    onOpenSettings: () -> Unit,
    settingsError: Boolean
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.schedule_exact_alarm_warning_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(R.string.schedule_exact_alarm_warning_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (settingsError) {
                Text(
                    stringResource(R.string.external_intent_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            LumenButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.schedule_exact_alarm_warning_action))
            }
        }
    }
}

@Composable
private fun SolarLocationWarningCard(onSetLocation: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.schedule_solar_location_required_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(R.string.schedule_solar_location_required_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            LumenButton(onClick = onSetLocation) {
                Text(stringResource(R.string.schedule_set_location))
            }
        }
    }
}

/**
 * Whether the Schedule tab shows the "needs a location" card.
 *
 * Only the solar mode needs a location, so only the solar mode warns about not
 * having one. This used to key off the location alone, so a fresh install
 * opened the Schedule tab on a warning about a mode the user had not picked.
 * The Solar row carries its own inline hint, so discovery does not depend on
 * this card.
 */
internal fun shouldWarnAboutSolarLocation(
    mode: ScheduleModeDto,
    locationValid: Boolean
): Boolean = mode == ScheduleModeDto.Solar && !locationValid

/** What tapping a schedule mode row does. */
internal enum class ScheduleModeRowAction { SelectMode, RequestLocation }

/**
 * The Solar row stays tappable without a location. The only location button
 * sits under the solar mode, so disabling the row left a fresh install no way
 * to enter the first location. The tap opens the location dialog instead, and
 * saving there selects the solar mode in the same write.
 */
internal fun scheduleModeRowAction(
    mode: ScheduleModeDto,
    solarLocationValid: Boolean
): ScheduleModeRowAction =
    if (mode == ScheduleModeDto.Solar && !solarLocationValid) {
        ScheduleModeRowAction.RequestLocation
    } else {
        ScheduleModeRowAction.SelectMode
    }

/**
 * A schedule time as this device would write it.
 *
 * The tab printed `21:30` to everyone and the picker was hard-wired to a
 * 24-hour dial, so a user whose device is set to 12-hour time saw a clock they
 * do not use, in a picker that would not let them enter one.
 * [android.text.format.DateFormat.getBestDateTimePattern] gives the locale's
 * own arrangement of the hour and minute, so this is not "HH:mm or h:mm a"
 * with a language guess bolted on.
 */
internal fun formatScheduleTime(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    locale: Locale
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm")
    return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(DateTimeFormatter.ofPattern(pattern, locale))
}

private val PREVIEW_INSTANT: Instant = Instant.parse("2026-07-01T12:00:00Z")

/**
 * A timezone as a person would name it, e.g. "Eastern Daylight Time" rather
 * than "America/New_York". The id is still shown, underneath, because it is
 * what the user picked and what a support conversation needs.
 *
 * [now] decides whether the daylight or standard name applies, so this is
 * correct across a DST boundary instead of being right for half the year.
 */
internal fun zoneDisplayName(zone: ZoneId, locale: Locale, now: Instant): String {
    val timeZone = TimeZone.getTimeZone(zone)
    return timeZone.getDisplayName(timeZone.inDaylightTime(Date.from(now)), TimeZone.LONG, locale)
}

/** The transition rows above Custom, in the order shown. */
internal val TRANSITION_PRESET_DURATIONS_MS: List<Long> = listOf(
    0L,
    30_000L,
    60_000L,
    3L * 60_000L,
    5L * 60_000L,
    15L * 60_000L,
    30L * 60_000L
)

internal fun transitionPresetLabelRes(durationMs: Long): Int = when (durationMs) {
    0L -> R.string.transition_instant
    30_000L -> R.string.transition_30s
    60_000L -> R.string.transition_1m
    3L * 60_000L -> R.string.transition_3m
    5L * 60_000L -> R.string.transition_5m
    15L * 60_000L -> R.string.transition_15m
    30L * 60_000L -> R.string.transition_30m
    else -> throw IllegalArgumentException("not a transition preset: $durationMs")
}

/**
 * Custom is whatever the presets are not, so a stored value that matches none
 * of them (an import, a hand-edited profile) still selects a row instead of
 * leaving the group empty. [customChosen] keeps it selected after the user
 * picked it and slid onto a preset value.
 */
internal fun isCustomTransitionSelected(storedMs: Long, customChosen: Boolean): Boolean =
    customChosen || storedMs !in TRANSITION_PRESET_DURATIONS_MS

internal fun isTransitionPresetSelected(
    presetMs: Long,
    storedMs: Long,
    customChosen: Boolean
): Boolean = !isCustomTransitionSelected(storedMs, customChosen) && presetMs == storedMs

/** Where Custom starts when picked from a preset: a value no preset holds. */
internal const val CUSTOM_TRANSITION_DEFAULT_MS = 10L * 60_000L

/** The custom slider's top, in whole minutes: the stored maximum. */
internal const val CUSTOM_TRANSITION_MAX_MIN =
    (com.openlumen.prefs.Preferences.TRANSITION_MAX_MS / 60_000L).toInt()

/** Ticks between the ends, so every whole minute from 1 to the top is a stop. */
internal const val CUSTOM_TRANSITION_SLIDER_STEPS = CUSTOM_TRANSITION_MAX_MIN - 2

/** The slider stop for a stored duration: the nearest whole minute it allows. */
internal fun customTransitionMinutes(storedMs: Long): Int =
    (storedMs / 60_000.0).roundToInt().coerceIn(1, CUSTOM_TRANSITION_MAX_MIN)
