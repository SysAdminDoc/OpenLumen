package com.openlumen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openlumen.R
import com.openlumen.schedule.OfflineCities

/**
 * Manual latitude / longitude entry with offline city picker. Validates that
 * both fields parse to Double and fall within sane ranges before enabling
 * Save. No Google Play Services dep — keeps the F-Droid build clean.
 *
 * Tied to roadmap candidate **C26** (Offline city picker). The picker is a
 * convenience layer over the same lat/lng fields — picking a city fills both
 * fields and leaves the user free to refine, rather than locking them out of
 * manual entry.
 */
@Composable
fun LocationEntryDialog(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lng: Double) -> Unit
) {
    var latText by rememberSaveable {
        mutableStateOf(initialLat?.let { "%.4f".format(it) } ?: "")
    }
    var lngText by rememberSaveable {
        mutableStateOf(initialLng?.let { "%.4f".format(it) } ?: "")
    }
    var query by rememberSaveable { mutableStateOf("") }

    val latVal = latText.toDoubleOrNull()
    val lngVal = lngText.toDoubleOrNull()
    val canSave = latVal != null && lngVal != null &&
        latVal in -90.0..90.0 && lngVal in -180.0..180.0

    // The matched list is bounded to 12 in the picker so the dialog body
    // doesn't grow unboundedly. The user can refine with the query box.
    val matches = remember(query) { OfflineCities.search(query, limit = 12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.location_decimal_degrees_help),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text(stringResource(R.string.location_latitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = latText.isNotEmpty() && (latVal == null || latVal !in -90.0..90.0),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text(stringResource(R.string.location_longitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = lngText.isNotEmpty() && (lngVal == null || lngVal !in -180.0..180.0),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    stringResource(R.string.location_pick_city),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.location_search_cities)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(matches) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    latText = "%.4f".format(city.latitude)
                                    lngText = "%.4f".format(city.longitude)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                city.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            AssistChip(
                                onClick = {
                                    latText = "%.4f".format(city.latitude)
                                    lngText = "%.4f".format(city.longitude)
                                },
                                label = { Text(stringResource(R.string.location_use_city)) },
                                colors = AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            LumenTextButton(
                onClick = {
                    val lat = latVal
                    val lng = lngVal
                    if (lat != null && lng != null && canSave) onSave(lat, lng)
                },
                enabled = canSave
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            LumenTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
