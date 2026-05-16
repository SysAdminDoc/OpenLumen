package com.openlumen.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.components.LightSensorCard
import com.openlumen.ui.components.LocationEntryDialog
import com.openlumen.ui.components.LumenOutlinedButton
import com.openlumen.ui.components.TimePickerDialog
import com.openlumen.viewmodel.OpenLumenViewModel
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ScheduleScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()
    val lux by vm.lux.collectAsState()

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleMedium)
        // Timezone hint (C27). Fixed-time schedules fire against the device's
        // current zone, not UTC and not the location entered for solar mode.
        // Showing it explicitly prevents the "I set 22:00 but it fires at
        // weird-looking time after travel" support thread.
        val zoneLabel = remember { ZoneId.systemDefault().id }
        Text(
            stringResource(R.string.schedule_timezone, zoneLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val modes = listOf(
            ScheduleModeDto.AlwaysOff to stringResource(R.string.schedule_off),
            ScheduleModeDto.AlwaysOn  to stringResource(R.string.schedule_always),
            ScheduleModeDto.FixedTime to stringResource(R.string.schedule_fixed),
            ScheduleModeDto.Solar     to stringResource(R.string.schedule_solar)
        )
        modes.forEach { (mode, label) ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.schedule.mode == mode)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable { vm.setScheduleMode(mode) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = prefs.schedule.mode == mode,
                        onClick = { vm.setScheduleMode(mode) }
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
                            "${stringResource(R.string.schedule_start)}: " +
                                "%02d:%02d".format(prefs.schedule.startHour, prefs.schedule.startMinute)
                        )
                    }
                    LumenOutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${stringResource(R.string.schedule_end)}: " +
                                "%02d:%02d".format(prefs.schedule.endHour, prefs.schedule.endMinute)
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
                                "Set location"
                            else
                                "%.3f, %.3f".format(lat, lng)
                        )
                    }

                    Text(
                        "Sunset offset: ${prefs.schedule.sunsetOffsetMin}m",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = prefs.schedule.sunsetOffsetMin.toFloat(),
                        onValueChange = { v ->
                            vm.setScheduleOffsets(v.roundToInt(), prefs.schedule.sunriseOffsetMin)
                        },
                        valueRange = -180f..180f,
                        steps = 71
                    )

                    Text(
                        "Sunrise offset: ${prefs.schedule.sunriseOffsetMin}m",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = prefs.schedule.sunriseOffsetMin.toFloat(),
                        onValueChange = { v ->
                            vm.setScheduleOffsets(prefs.schedule.sunsetOffsetMin, v.roundToInt())
                        },
                        valueRange = -180f..180f,
                        steps = 71
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
                            .clickable { vm.setTransitionDuration(durationMs) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = prefs.transitionDurationMs == durationMs,
                            onClick = { vm.setTransitionDuration(durationMs) }
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
