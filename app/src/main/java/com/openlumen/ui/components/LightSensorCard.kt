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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlumen.R

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.light_sensor_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (currentLux < 0)
                            stringResource(R.string.light_sensor_unavailable)
                        else
                            stringResource(R.string.light_sensor_now, currentLux.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val sensorLabel = stringResource(R.string.light_sensor_title)
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = sensorLabel }
                )
            }

            val thresholdLux = threshold.toInt()
            val thresholdState = stringResource(R.string.light_sensor_threshold_state, thresholdLux)
            Text(stringResource(R.string.light_sensor_threshold, thresholdLux))
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                valueRange = 0f..200f,
                steps = 39,
                enabled = enabled,
                modifier = Modifier.semantics {
                    stateDescription = thresholdState
                }
            )

            LumenOutlinedButton(
                onClick = onUseCurrent,
                enabled = enabled && currentLux >= 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.light_sensor_calibrate))
            }
        }
    }
}
