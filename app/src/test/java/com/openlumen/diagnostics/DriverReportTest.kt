package com.openlumen.diagnostics

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.shizuku.ShizukuSnapshot
import com.openlumen.shizuku.ShizukuState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `report states how Shizuku stands and how its server was started`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()

        val report = DriverReport.build(
            context,
            Preferences(),
            emptyList(),
            ShizukuSnapshot(ShizukuState.READY, serverUid = 2000, serverVersion = 13)
        )

        assertThat(report).startsWith("OpenLumen driver report v3")
        assertThat(report).contains("Shizuku\n---\nState: running, OpenLumen allowed\nServer uid: 2000 (adb)\nServer API: 13\n")
    }

    @Test
    fun `report says when Shizuku was not checked or not running`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()

        assertThat(DriverReport.build(context, Preferences(), emptyList()))
            .contains("Shizuku\n---\nState: not checked\n")
        assertThat(
            DriverReport.build(
                context,
                Preferences(),
                emptyList(),
                ShizukuSnapshot(ShizukuState.NOT_INSTALLED, serverUid = null, serverVersion = null)
            )
        ).contains("Shizuku\n---\nState: not installed\n\n")
    }
}
