package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriverReportTest {

    @Test
    fun `report exposes advanced protection status and impact contract`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()

        val report = DriverReport.build(context, Preferences(), emptyList())

        assertThat(report).contains("Advanced Protection")
        assertThat(report).contains("State: n/a (API <36)")
        assertThat(report).contains("no AccessibilityService or UsageStats backend is used")
        assertThat(report).contains("QUERY_ADVANCED_PROTECTION_MODE: n/a (API <36)")
    }
}
