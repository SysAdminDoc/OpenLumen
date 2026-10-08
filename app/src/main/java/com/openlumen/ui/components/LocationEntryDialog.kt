package com.openlumen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.openlumen.R
import com.openlumen.external.ExternalIntentLauncher
import com.openlumen.location.DeviceLocation
import kotlinx.coroutines.launch
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
 *
 * **Locale safety.** Decimal coordinates always render and parse against
 * [Locale.ROOT] (i.e. dot-separated). Without this, German / French / Spanish
 * locales hit a Catch-22: `"%.4f".format(...)` writes `52,5200` but
 * `String.toDoubleOrNull()` only accepts `.`, so the city picker fills the
 * field with a value the validator can't read and Save stays disabled. The
 * manual-input path additionally tolerates a single `,` as the user's decimal
 * separator and normalizes it to `.` before parsing.
 *
 * A bundled city also supplies its IANA timezone for solar scheduling. Manual
 * coordinate edits clear that association and fall back to the device zone.
 */
@Composable
fun LocationEntryDialog(
    initialLat: Double?,
    initialLng: Double?,
    initialTimezone: String? = null,
    initialAutoLocation: Boolean = false,
    lastAutoFixAtMs: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (lat: Double, lng: Double, solarTimezone: String?, autoLocation: Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoLocation by rememberSaveable { mutableStateOf(initialAutoLocation) }
    var locating by remember { mutableStateOf(false) }
    var locateStatus by rememberSaveable { mutableStateOf(LocateStatus.NONE) }
    var latText by rememberSaveable {
        mutableStateOf(initialLat?.let { formatCoord(it) } ?: "")
    }
    var lngText by rememberSaveable {
        mutableStateOf(initialLng?.let { formatCoord(it) } ?: "")
    }
    var selectedTimezone by rememberSaveable { mutableStateOf(initialTimezone) }
    var query by rememberSaveable { mutableStateOf("") }

    val latVal = parseCoord(latText)
    val lngVal = parseCoord(lngText)
    val latInRange = latVal != null && latVal in -90.0..90.0
    val lngInRange = lngVal != null && lngVal in -180.0..180.0
    // Only flag a field as in-error once the user has typed something; an
    // empty field is "incomplete", not "wrong", so it stays neutral.
    val latError = latText.isNotEmpty() && !latInRange
    val lngError = lngText.isNotEmpty() && !lngInRange
    val canSave = latInRange && lngInRange

    // The matched list is bounded to 12 in the picker so the dialog body
    // doesn't grow unboundedly. The user can refine with the query box.
    val matches = remember(query) { OfflineCities.search(query, limit = 12) }

    val locate: () -> Unit = {
        if (!DeviceLocation.isEnabled(context)) {
            locateStatus = LocateStatus.LOCATION_OFF
        } else {
            locating = true
            scope.launch {
                val fix = DeviceLocation.fix(context)
                locating = false
                if (fix == null) {
                    locateStatus = LocateStatus.FAILED
                } else {
                    latText = formatCoord(fix.latitude)
                    lngText = formatCoord(fix.longitude)
                    // The device zone is the right one for where the user is.
                    selectedTimezone = null
                    autoLocation = true
                    locateStatus = LocateStatus.FOUND
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locate() else locateStatus = LocateStatus.DENIED
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.location_title)) },
        text = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                LumenOutlinedButton(
                    onClick = {
                        if (DeviceLocation.hasPermission(context)) {
                            locate()
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (locating) R.string.location_locating else R.string.location_use_mine
                        )
                    )
                }
                locateStatus.messageRes()?.let { res ->
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (locateStatus == LocateStatus.LOCATION_OFF) {
                    LumenTextButton(onClick = {
                        ExternalIntentLauncher.launch(
                            context,
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text(stringResource(R.string.location_open_settings)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.location_auto_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.location_auto_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (autoLocation && lastAutoFixAtMs > 0L) {
                            Text(
                                stringResource(
                                    R.string.location_auto_last,
                                    DateUtils.getRelativeTimeSpanString(
                                        lastAutoFixAtMs,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    )
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LumenSwitch(
                        checked = autoLocation,
                        onCheckedChange = { on ->
                            if (!on) {
                                autoLocation = false
                            } else if (DeviceLocation.hasPermission(context)) {
                                locate()
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }
                    )
                }
                Text(
                    stringResource(R.string.location_decimal_degrees_help),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.location_timezone_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        selectedTimezone = null
                        autoLocation = false
                    },
                    label = { Text(stringResource(R.string.location_latitude)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true,
                    isError = latError,
                    supportingText = if (latError) {
                        { Text(stringResource(R.string.location_latitude_range)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it
                        selectedTimezone = null
                        autoLocation = false
                    },
                    label = { Text(stringResource(R.string.location_longitude)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val lat = latVal
                            val lng = lngVal
                            if (lat != null && lng != null && canSave) {
                                onSave(lat, lng, selectedTimezone, autoLocation)
                            }
                        }
                    ),
                    singleLine = true,
                    isError = lngError,
                    supportingText = if (lngError) {
                        { Text(stringResource(R.string.location_longitude_range)) }
                    } else null,
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (matches.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.location_no_city_matches),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            )
                        }
                    }
                    items(matches, key = { "${it.displayName}|${it.latitude},${it.longitude}" }) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) {
                                    latText = formatCoord(city.latitude)
                                    lngText = formatCoord(city.longitude)
                                    selectedTimezone = city.timezone
                                    autoLocation = false
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                city.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.location_use_city),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
                selectedTimezone?.let { timezone ->
                    Text(
                        stringResource(R.string.location_timezone_selected, timezone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
        },
        confirmButton = {
            LumenTextButton(
                onClick = {
                    val lat = latVal
                    val lng = lngVal
                    if (lat != null && lng != null && canSave) {
                        onSave(lat, lng, selectedTimezone, autoLocation)
                    }
                },
                enabled = canSave
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            LumenTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// Locale-tolerant parse/format lives in `CoordParsing` so it's unit-testable
// without spinning up a Composable harness.
private fun formatCoord(value: Double): String = CoordParsing.format(value)
private fun parseCoord(raw: String): Double? = CoordParsing.parse(raw)

/** The outcome of the last "Use my location" tap, shown under the button. */
private enum class LocateStatus {
    NONE, FOUND, LOCATION_OFF, DENIED, FAILED;

    fun messageRes(): Int? = when (this) {
        NONE -> null
        FOUND -> R.string.location_found
        LOCATION_OFF -> R.string.location_off
        DENIED -> R.string.location_denied
        FAILED -> R.string.location_failed
    }
}
