package com.openlumen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openlumen.R
import com.openlumen.external.ExternalIntentResult
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.service.ExactAlarmAccess

/**
 * Whether Home should ask for exact alarms.
 *
 * Without them every schedule transition is an inexact alarm, which Android
 * may hold back by up to an hour: the evening preset then stays on well after
 * sunrise while the screen is in use, and the only hint was a card on the
 * Schedule tab. Shown only while a timed schedule is running, and not again
 * in the same session once the user says not now.
 */
internal fun shouldPromptForExactAlarm(
    filterEnabled: Boolean,
    mode: ScheduleModeDto,
    canScheduleExactAlarms: Boolean,
    dismissedThisSession: Boolean
): Boolean = filterEnabled &&
    ExactAlarmAccess.scheduleModeNeedsExactAlarm(mode) &&
    !canScheduleExactAlarms &&
    !dismissedThisSession

/**
 * The Home card behind [shouldPromptForExactAlarm]. It sits with
 * [OverlayPermissionCard] and says the same thing as the Schedule tab's
 * warning. Access is read again on START and RESUME, so the card goes away
 * by itself when the user comes back from the settings screen with it
 * granted; [onGranted] is called once on that edge so the next transition
 * can be rescheduled as an exact alarm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExactAlarmPromptCard(
    filterEnabled: Boolean,
    mode: ScheduleModeDto,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var canSchedule by remember { mutableStateOf(ExactAlarmAccess.canScheduleExactAlarms(ctx)) }
    // Saveable, so it holds across tab switches and rotation for as long as
    // this activity's session lasts.
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var settingsError by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        val refreshed = ExactAlarmAccess.canScheduleExactAlarms(ctx)
        if (refreshed && !canSchedule) currentOnGranted()
        canSchedule = refreshed
    }
    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { refresh() }

    if (!shouldPromptForExactAlarm(filterEnabled, mode, canSchedule, dismissed)) return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.schedule_exact_alarm_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                stringResource(R.string.schedule_exact_alarm_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (settingsError) {
                // Appears after a tap, so it is announced rather than left
                // for TalkBack to find.
                Text(
                    stringResource(R.string.external_intent_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LumenButton(onClick = {
                    settingsError = ExactAlarmAccess.openExactAlarmSettings(ctx) !=
                        ExternalIntentResult.Launched
                }) {
                    Text(stringResource(R.string.schedule_exact_alarm_warning_action))
                }
                LumenTextButton(onClick = { dismissed = true }) {
                    Text(stringResource(R.string.exact_alarm_prompt_not_now))
                }
            }
        }
    }
}
