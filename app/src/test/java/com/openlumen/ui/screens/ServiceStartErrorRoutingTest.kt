package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * C305. A refused service start rolled the toggle back and put its message on
 * the flow only the About tab collects, so the user saw the switch snap back
 * with no explanation and then got a toast later, out of context, the next
 * time they opened About.
 *
 * The routing has no seam a value test can reach: it is a Compose collector,
 * and this module has no Compose test harness. So this asserts the wiring in
 * the source, which is the thing that regressed.
 */
class ServiceStartErrorRoutingTest {

    private fun source(path: String): String {
        val file = File("src/main/java/com/openlumen/$path")
        assertThat(file.exists()).isTrue()
        return file.readText()
    }

    @Test fun `a refused start reports on the screen the user is looking at`() {
        assertThat(source("ui/screens/HomeScreen.kt")).contains("vm.serviceStartError")
        assertThat(source("ui/screens/HomeScreen.kt")).contains("consumeServiceStartError")
    }

    @Test fun `the failure does not go to the export flow`() {
        // exportResult is collected on the About tab only. Routing the start
        // failure back onto it is exactly the defect.
        val viewModel = source("viewmodel/OpenLumenViewModel.kt")
        val setEnabled = viewModel.substringAfter("override fun setEnabled(")
            .substringBefore("override fun selectPreset(")

        assertThat(setEnabled).contains("_serviceStartError.value")
        assertThat(setEnabled).doesNotContain("_exportResult.value")
    }

    @Test fun `the export flow is still what the About tab collects`() {
        // Positive control: the two assertions above have to be about routing,
        // not about exportResult having been removed.
        assertThat(source("ui/screens/AboutScreen.kt")).contains("vm.exportResult")
    }
}

/**
 * C306. The notification permission state was read once per composition entry,
 * so after "Open notification settings", granting and coming back, the card
 * stayed up until something else recreated the screen. OverlayPermissionCard
 * already had the right pattern; Home did not use it.
 *
 * Same reason as above for asserting on the source: the defect is a missing
 * lifecycle observer in a Composable, and this module has no Compose harness.
 */
class NotificationCardRefreshTest {

    private val home = File("src/main/java/com/openlumen/ui/screens/HomeScreen.kt").readText()

    @Test fun `the notification state refreshes on the lifecycle edge`() {
        val block = home.substringAfter("val refreshNotificationPermissionState")
            .substringBefore("val notifLauncher")

        assertThat(block).contains("LifecycleEventObserver")
        assertThat(block).contains("Lifecycle.Event.ON_RESUME")
        assertThat(block).contains("addObserver(observer)")
        assertThat(block).contains("removeObserver(observer)")
    }

    @Test fun `entering composition still reads it once`() {
        // Positive control: the observer must be an addition. Without the
        // immediate read, a rotation or a navigation back waits for the next
        // resume tick before the card is right.
        assertThat(home).contains("LaunchedEffect(Unit) { refreshNotificationPermissionState() }")
    }
}
