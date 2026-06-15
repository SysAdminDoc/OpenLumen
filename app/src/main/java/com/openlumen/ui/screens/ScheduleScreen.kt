package com.openlumen.ui.screens

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.components.LightSensorCard
import com.openlumen.ui.components.LocationEntryDialog
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.TimePickerDialog
import com.openlumen.viewmodel.OpenLumenViewModel
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ScheduleScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsStateWithLifecycle()
    val lux by vm.lux.collectAsStateWithLifecycle()

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(topLevelScrollPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleMedium)
        // Timezone hint (C27). Fixed-time schedules fire against the device's
        // current zone, not UTC and not the location entered for solar mode.
        // Showing it explicitly prevents the "I set 22:00 but it fires at
        // weird-looking time after travel" support thread.
        val zoneLabel = ZoneId.systemDefault().id
        Text(
            stringResource(R.string.schedule_timezone, zoneLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val modes = listOf(
            ScheduleModeDto.AlwaysOff to stringResource(R.string.schedule_off),
            ScheduleModeDto.AlwaysOn  to stringResource(R.string.schedule_always),
            ScheduleModeDto.FixedTime to stringResource(R.string.schedule_fixed),
            ScheduleModeDto.Solar     to stringResource(R.string.schedule_solar),
            ScheduleModeDto.UntilNextAlarm to stringResource(R.string.schedule_until_next_alarm)
        )
        modes.forEach { (mode, label) ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.schedule.mode == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = prefs.schedule.mode == mode,
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
                        onClick = null
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
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
                                prefs.schedule.startHour,
                                prefs.schedule.startMinute
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
                                prefs.schedule.endHour,
                                prefs.schedule.endMinute
                            )
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
                                String.format(Locale.ROOT, "%.3f, %.3f", lat, lng)
                        )
                    }

                    val sunsetOffsetLabel = stringResource(
                        R.string.schedule_sunset_offset,
                        prefs.schedule.sunsetOffsetMin
                    )
                    Text(
                        sunsetOffsetLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = prefs.schedule.sunsetOffsetMin.toFloat(),
                        onValueChange = { v ->
                            vm.setScheduleOffsets(v.roundToInt(), prefs.schedule.sunriseOffsetMin)
                        },
                        valueRange = -180f..180f,
                        steps = 71,
                        modifier = Modifier.semantics {
                            stateDescription = sunsetOffsetLabel
                        }
                    )

                    val sunriseOffsetLabel = stringResource(
                        R.string.schedule_sunrise_offset,
                        prefs.schedule.sunriseOffsetMin
                    )
                    Text(
                        sunriseOffsetLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = prefs.schedule.sunriseOffsetMin.toFloat(),
                        onValueChange = { v ->
                            vm.setScheduleOffsets(prefs.schedule.sunsetOffsetMin, v.roundToInt())
                        },
                        valueRange = -180f..180f,
                        steps = 71,
                        modifier = Modifier.semantics {
                            stateDescription = sunriseOffsetLabel
                        }
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
                                prefs.schedule.startHour,
                                prefs.schedule.startMinute
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
                        Text(label, style = MaterialTheme.typography.bodyMedium)
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
                vm.setScheduleTimes(h, m, prefs.schedule.endHour, prefs.schedule.endMinute)
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
                vm.setScheduleTimes(prefs.schedule.startHour, prefs.schedule.startMinute, h, m)
                showEndPicker = false
            }
        )
    }
    if (showLocationDialog) {
        LocationEntryDialog(
            initialLat = prefs.schedule.latitude,
            initialLng = prefs.schedule.longitude,
            onDismiss = { showLocationDialog = false },
            onSave = { lat, lng ->
                vm.setLocation(lat, lng)
                showLocationDialog = false
            }
        )
    }
}
