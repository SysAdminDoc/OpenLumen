package com.openlumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ambient-light-sensor-driven activation card.
 *
 * Behavior: when [enabled] is on and the current ambient lux reading drops below
 * [threshold] for any sample, the filter is activated regardless of the schedule mode.
 * This is an OR condition with the schedule — useful for "always engage in a dark
 * room" workflows where the user doesn't trust their schedule to match indoor light.
 */
@Composable
fun LightSensorCard(
    enabled: Boolean,
    threshold: Float,
    currentLux: Float,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onUseCurrent: () -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Ambient-light trigger", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (currentLux < 0)
                            "Sensor unavailable or no reading yet"
                        else
                            "Now: %.0f lux".format(currentLux),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Text("Threshold: %.0f lux".format(threshold))
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = 0f..200f,
                steps = 39
            )

            LumenOutlinedButton(
                onClick = onUseCurrent,
                enabled = currentLux >= 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calibrate: use current reading as threshold")
            }
        }
    }
}
