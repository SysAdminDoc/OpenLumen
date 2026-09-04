package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * C272. Android 15 narrowed the SYSTEM_ALERT_WINDOW exemption: an app
 * targeting SDK 35 relying on it needs a visible overlay window at the moment
 * of the start. This app does not rely on it, because startInForeground runs
 * in onCreate before any overlay is installed, but nothing said so and nothing
 * recorded which exemption a given start was actually leaning on.
 */
class ServiceStartExemptionTest {

    private fun source(path: String) = File("src/main/java/com/openlumen/$path").readText()

    @Test fun `every service start names the exemption it relies on`() {
        // An unnamed start defaults to NONE, which is the safe reading but
        // tells a diagnostics reader nothing. These are the paths that have a
        // real exemption, so they have to say which.
        val callers = mapOf(
            "MainActivity.kt" to "USER_INTERACTION",
            "service/LumenTileService.kt" to "USER_INTERACTION",
            "service/BootReceiver.kt" to "BOOT",
            "service/LockedBootReceiver.kt" to "BOOT",
            "service/ScheduleAlarmReceiver.kt" to "EXACT_ALARM",
            "service/ScheduleClockChangeReceiver.kt" to "SYSTEM_BROADCAST",
            "service/ExactAlarmPermissionReceiver.kt" to "SYSTEM_BROADCAST"
        )

        for ((path, exemption) in callers) {
            assertThat(source(path)).contains("Exemption.$exemption")
        }
    }

    @Test fun `the automation surface claims no exemption`() {
        // It is driven by other apps and by adb, from the background, and
        // nothing exempts that. Claiming otherwise here would make the
        // diagnostics log lie about why a start was refused.
        assertThat(source("service/AutomationReceiver.kt")).contains("Exemption.NONE")
    }

    @Test fun `the service goes foreground before any overlay exists`() {
        // This is what makes the SAW exemption irrelevant to this app, and it
        // is the thing that would quietly stop being true if someone moved
        // the engine setup earlier.
        val service = source("service/LumenService.kt")
        val onCreate = service.substringAfter("override fun onCreate()")
            .substringBefore("private fun registerScreenStateReceiver")
        val foregroundAt = onCreate.indexOf("startInForeground()")
        val observeAt = onCreate.indexOf("ensurePreferencesObserved()")

        assertThat(foregroundAt).isGreaterThan(0)
        assertThat(observeAt).isGreaterThan(0)
        assertThat(foregroundAt).isLessThan(observeAt)
        // The overlay window is installed by EngineController, which the
        // preference observation reaches, so nothing here can have put one on
        // screen yet.
        assertThat(service).doesNotContain("installView")
    }
}
