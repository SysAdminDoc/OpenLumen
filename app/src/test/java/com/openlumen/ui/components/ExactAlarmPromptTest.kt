package com.openlumen.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import com.openlumen.R
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.ComposeSemantics
import com.openlumen.ui.theme.OpenLumenTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * PR #29. Without exact alarms every schedule flip may run up to an hour
 * late, and the only hint sat on the Schedule tab. Home now says so, as a
 * card rather than a dialog, since nothing is wrong yet.
 */
class ExactAlarmPromptTest {

    @Test fun `asks when a timed schedule runs without exact alarms`() {
        for (mode in listOf(ScheduleModeDto.FixedTime, ScheduleModeDto.Solar, ScheduleModeDto.UntilNextAlarm)) {
            assertThat(shouldPromptForExactAlarm(true, mode, canScheduleExactAlarms = false, dismissedThisSession = false))
                .isTrue()
        }
    }

    @Test fun `does not ask once granted, dismissed, or with nothing to time`() {
        assertThat(shouldPromptForExactAlarm(true, ScheduleModeDto.Solar, canScheduleExactAlarms = true, dismissedThisSession = false))
            .isFalse()
        assertThat(shouldPromptForExactAlarm(true, ScheduleModeDto.Solar, canScheduleExactAlarms = false, dismissedThisSession = true))
            .isFalse()
        assertThat(shouldPromptForExactAlarm(false, ScheduleModeDto.Solar, canScheduleExactAlarms = false, dismissedThisSession = false))
            .isFalse()
        for (mode in listOf(ScheduleModeDto.AlwaysOn, ScheduleModeDto.AlwaysOff)) {
            assertThat(shouldPromptForExactAlarm(true, mode, canScheduleExactAlarms = false, dismissedThisSession = false))
                .isFalse()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExactAlarmPromptCardTest {

    private var granted = 0

    private fun showCard(
        filterEnabled: Boolean = true,
        mode: ScheduleModeDto = ScheduleModeDto.FixedTime
    ): ActivityController<ComponentActivity> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            OpenLumenTheme(darkTheme = true) {
                ExactAlarmPromptCard(filterEnabled = filterEnabled, mode = mode, onGranted = { granted++ })
            }
        }
        ComposeSemantics.settle()
        return controller
    }

    private fun ActivityController<ComponentActivity>.texts() = ComposeSemantics.texts(get())

    private val title get() = org.robolectric.RuntimeEnvironment.getApplication()
        .getString(R.string.schedule_exact_alarm_warning_title)
    private val action get() = org.robolectric.RuntimeEnvironment.getApplication()
        .getString(R.string.schedule_exact_alarm_warning_action)
    private val notNow get() = org.robolectric.RuntimeEnvironment.getApplication()
        .getString(R.string.exact_alarm_prompt_not_now)

    @Test fun `home shows the card while a timed schedule runs without exact alarms`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val texts = showCard().texts()

        assertThat(texts).containsAtLeast(title, action, notNow)
    }

    @Test fun `no card once exact alarms are allowed or with nothing to time`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertThat(showCard().texts()).doesNotContain(title)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertThat(showCard(mode = ScheduleModeDto.AlwaysOn).texts()).doesNotContain(title)
        assertThat(showCard(filterEnabled = false).texts()).doesNotContain(title)
    }

    @Test fun `not now hides the card for the rest of the session`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = showCard()

        ComposeSemantics.click(controller.get(), notNow)
        assertThat(controller.texts()).doesNotContain(title)

        // Leaving for another app and coming back does not bring it back.
        controller.pause().stop().restart().start().resume()
        ComposeSemantics.settle()
        assertThat(controller.texts()).doesNotContain(title)
    }

    @Test fun `coming back with access granted removes the card and reschedules once`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = showCard()
        assertThat(controller.texts()).contains(title)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller.pause().stop().restart().start().resume()
        ComposeSemantics.settle()

        assertThat(controller.texts()).doesNotContain(title)
        assertThat(granted).isEqualTo(1)

        controller.pause().resume()
        ComposeSemantics.settle()
        assertThat(granted).isEqualTo(1)
    }

    @Test fun `the action opens the exact alarm settings for this app`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = showCard()
        val app = controller.get().application
        val settingsActivity = ComponentName("com.android.settings", "com.android.settings.ExactAlarmSettings")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(settingsActivity)
            addIntentFilterForActivity(
                settingsActivity,
                IntentFilter(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                }
            )
        }

        ComposeSemantics.click(controller.get(), action)

        val started = shadowOf(controller.get()).nextStartedActivity
        assertThat(started?.action).isEqualTo(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        assertThat(started?.data.toString()).isEqualTo("package:${app.packageName}")
        // Still shown: the card only goes once access is actually granted.
        assertThat(controller.texts()).contains(title)
    }

    @Test fun `a settings screen that will not open is said on the card`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val controller = showCard()
        val failed = controller.get().getString(R.string.external_intent_failed)

        // Nothing resolves either settings intent here.
        ComposeSemantics.click(controller.get(), action)

        val node = ComposeSemantics.nodes(controller.get())
            .single { failed in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
        assertThat(node.config.getOrNull(SemanticsProperties.LiveRegion)).isEqualTo(LiveRegionMode.Polite)
    }
}
