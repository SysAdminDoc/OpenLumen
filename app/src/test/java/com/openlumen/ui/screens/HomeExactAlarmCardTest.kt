package com.openlumen.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.service.LumenService
import com.openlumen.ui.ComposeSemantics
import com.openlumen.widget.widgetPreferencesStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowAlarmManager

/**
 * PR #29, through the real activity: a timed schedule running without exact
 * alarms is said on Home, where the user lands, not only on the Schedule tab.
 */
@RunWith(RobolectricTestRunner::class)
class HomeExactAlarmCardTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val title get() = app.getString(R.string.schedule_exact_alarm_warning_title)

    @Before fun setUp() {
        LumenService.resetForegroundStateForTest()
    }

    private fun homeTexts(activity: ComponentActivity, expect: String?, timeoutMs: Long = 10_000): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var texts = emptyList<String>()
        while (System.currentTimeMillis() < deadline) {
            ComposeSemantics.settle()
            texts = ComposeSemantics.texts(activity)
            // Home draws a placeholder until preferences load.
            val loaded = texts.any { it == app.getString(R.string.home_filter_on) || it == app.getString(R.string.home_filter_off) }
            if (loaded && (expect == null || expect in texts)) return texts
            Thread.sleep(20)
        }
        return texts
    }

    @Test fun `home asks for exact alarms while a timed schedule runs without them`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        runBlocking {
            prefs.update { it.copy(enabled = true, schedule = it.schedule.copy(mode = ScheduleModeDto.FixedTime)) }
        }

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertThat(homeTexts(activity, expect = title)).contains(title)
    }

    @Test fun `home says nothing about exact alarms once they are allowed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        runBlocking {
            prefs.update { it.copy(enabled = true, schedule = it.schedule.copy(mode = ScheduleModeDto.FixedTime)) }
        }

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertThat(homeTexts(activity, expect = null)).doesNotContain(title)
    }
}
