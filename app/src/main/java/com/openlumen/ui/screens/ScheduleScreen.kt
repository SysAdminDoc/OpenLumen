package com.openlumen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openlumen.R
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.viewmodel.OpenLumenViewModel

@Composable
fun ScheduleScreen(vm: OpenLumenViewModel = hiltViewModel()) {
    val prefs by vm.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleMedium)

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
                modifier = Modifier.fillMaxWidth()
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
            FixedTimeBlock(
                startH = prefs.schedule.startHour,
                startM = prefs.schedule.startMinute,
                endH = prefs.schedule.endHour,
                endM = prefs.schedule.endMinute,
                onChange = vm::setScheduleTimes
            )
        }

        if (prefs.schedule.mode == ScheduleModeDto.Solar) {
            SolarBlock(
                lat = prefs.schedule.latitude,
                lng = prefs.schedule.longitude,
                onChange = vm::setLocation
            )
        }
    }
}

@Composable
private fun FixedTimeBlock(
    startH: Int, startM: Int,
    endH: Int, endM: Int,
    onChange: (Int, Int, Int, Int) -> Unit
) {
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("$startH:%02d → $endH:%02d".format(startM, endM))
            Text(
                "Time pickers in next iteration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SolarBlock(lat: Double, lng: Double, onChange: (Double, Double) -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(if (lat.isNaN()) "No location set" else "%.3f, %.3f".format(lat, lng))
            Text(
                "Location picker in next iteration (manual coords + FusedLocation)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
