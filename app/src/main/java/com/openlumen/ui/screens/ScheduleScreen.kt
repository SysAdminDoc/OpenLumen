package com.openlumen.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
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
import com.openlumen.ui.components.LightSensorCard
import com.openlumen.ui.components.LumenButton
import com.openlumen.ui.components.LocationEntryDialog
import com.openlumen.ui.components.LumenOutlinedButton
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
        val zoneName = zoneDisplayName(zone, locale, Instant.now())
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
                        enabled = !solarUnavailable,
                        onClick = { vm.setScheduleMode(mode) },
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
                                stringResource(R.string.schedule_solar_location_required_short),
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
                                    R.string.schedule_location_value,
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
                val options = listOf(
                    0L to stringResource(R.string.transition_instant),
                    30_000L to stringResource(R.string.transition_30s),
                    5L * 60_000L to stringResource(R.string.transition_5m),
                    15L * 60_000L to stringResource(R.string.transition_15m),
                    30L * 60_000L to stringResource(R.string.transition_30m)
                )
                options.forEach { (durationMs, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = prefs.transitionDurationMs == durationMs,
                                onClick = { vm.setTransitionDuration(durationMs) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = prefs.transitionDurationMs == durationMs,
                            onClick = null
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (prefs.transitionDurationMs == durationMs) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
            }
        }
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
            onDismiss = { showLocationDialog = false },
            onSave = { lat, lng, solarTimezone ->
                vm.setLocation(lat, lng, solarTimezone)
                showLocationDialog = false
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
