package com.openlumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/**
 * Manual latitude / longitude entry. Validates that both fields parse to Double and
 * fall within sane ranges before enabling Save. No Google Play Services dep — keeps
 * the F-Droid build clean.
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

    val latVal = latText.toDoubleOrNull()
    val lngVal = lngText.toDoubleOrNull()
    val canSave = latVal != null && lngVal != null &&
        latVal in -90.0..90.0 && lngVal in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set location") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Decimal degrees. Latitude must be -90 to 90, longitude -180 to 180.",
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = latText.isNotEmpty() && (latVal == null || latVal !in -90.0..90.0),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = lngText.isNotEmpty() && (lngVal == null || lngVal !in -180.0..180.0),
                    modifier = Modifier.fillMaxWidth()
                )
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
            ) { Text("Save") }
        },
        dismissButton = {
            LumenTextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
