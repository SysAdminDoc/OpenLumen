package com.openlumen.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openlumen.R
import com.openlumen.external.ExternalIntentResult
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.service.ExactAlarmAccess

/**
 * Whether to ask for exact alarms when the app opens.
 *
 * Without them every schedule transition is an inexact alarm, which Android
 * may hold back by up to an hour: the evening preset then stays on well after
 * sunrise while the screen is in use, and the only hint was a card on the
 * Schedule tab. Asked only when a timed schedule is running, and not again in
 * the same session once the user says not now.
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
 * The dialog behind [shouldPromptForExactAlarm]. Re-reads the permission on
 * START and RESUME, so it closes by itself when the user comes back from the
 * settings screen with access granted, and reports that through [onGranted].
 */
@Composable
internal fun ExactAlarmPrompt(
    filterEnabled: Boolean,
    mode: ScheduleModeDto,
    onGranted: () -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canSchedule by remember { mutableStateOf(ExactAlarmAccess.canScheduleExactAlarms(ctx)) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var settingsError by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                val refreshed = ExactAlarmAccess.canScheduleExactAlarms(ctx)
                if (refreshed && !canSchedule) onGranted()
                canSchedule = refreshed
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!shouldPromptForExactAlarm(filterEnabled, mode, canSchedule, dismissed)) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.schedule_exact_alarm_warning_title)) },
        text = {
            Text(
                stringResource(R.string.schedule_exact_alarm_warning_body) +
                    if (settingsError) "\n\n" + stringResource(R.string.external_intent_failed) else ""
            )
        },
        confirmButton = {
            LumenTextButton(onClick = {
                settingsError = ExactAlarmAccess.openExactAlarmSettings(ctx) !=
                    ExternalIntentResult.Launched
            }) { Text(stringResource(R.string.schedule_exact_alarm_warning_action)) }
        },
        dismissButton = {
            LumenTextButton(onClick = { dismissed = true }) {
                Text(stringResource(R.string.exact_alarm_prompt_not_now))
            }
        }
    )
}
