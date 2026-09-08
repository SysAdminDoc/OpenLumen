package com.openlumen.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openlumen.R
import com.openlumen.schedule.AmbientLightGate

/**
 * Ambient-light-sensor-driven activation card.
 *
 * Behavior: when [enabled] is on and the current ambient lux reading drops below
 * [threshold], the filter is activated. It remains active until lux rises above a
 * small hysteresis band, regardless of the schedule mode. This is an OR condition
 * with the schedule — useful for "always engage in a dark room" workflows where
 * the user doesn't trust their schedule to match indoor light.
 */
@Composable
fun LightSensorCard(
    enabled: Boolean,
    threshold: Float,
    currentLux: Float,
    available: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onUseCurrent: () -> Unit
) {
    val ctx = LocalContext.current
    // Read here rather than in the click lambda: a Context captured in a lambda
    // does not re-read its resources when the configuration changes, so the
    // message would keep the language the card was first composed in.
    val thresholdSetMessage =
        stringResource(R.string.light_sensor_threshold_set, currentLux.roundToInt())
    var thresholdDraft by remember { mutableFloatStateOf(threshold) }
    LaunchedEffect(threshold) {
        thresholdDraft = threshold
    }

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
                        if (!available)
                            stringResource(R.string.light_sensor_no_hardware)
                        else if (currentLux < 0)
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
                LumenSwitch(
                    checked = enabled && available,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = sensorLabel },
                    enabled = available
                )
            }

            val thresholdLux = thresholdDraft.toInt()
            val thresholdState = stringResource(R.string.light_sensor_threshold_state, thresholdLux)
            val disengageLux = AmbientLightGate.disengageThreshold(thresholdDraft).toInt()
            Text(
                stringResource(R.string.light_sensor_threshold, thresholdLux),
                color = if (enabled && available) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Slider(
                value = thresholdDraft,
                onValueChange = { thresholdDraft = it },
                onValueChangeFinished = { onThresholdChange(thresholdDraft) },
                valueRange = 0f..200f,
                steps = 39,
                enabled = enabled && available,
                modifier = Modifier.labeledSliderSemantics(
                    name = stringResource(R.string.light_sensor_threshold_name),
                    valueDescription = thresholdState
                ),
                colors = lumenSliderColors()
            )
            Text(
                stringResource(R.string.light_sensor_hysteresis, thresholdLux, disengageLux),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LumenOutlinedButton(
                onClick = {
                    onUseCurrent()
                    // The button reads "Calibrate" and the slider it moves is
                    // above the fold on a short screen, so without this the tap
                    // looked like it had done nothing. It also gives TalkBack
                    // the new value, which the slider alone does not announce.
                    Toast.makeText(ctx, thresholdSetMessage, Toast.LENGTH_SHORT).show()
                },
                enabled = enabled && available && currentLux >= 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.light_sensor_calibrate))
            }
        }
    }
}
