package com.openlumen

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.openlumen.service.AutomationReceiver
import com.openlumen.service.LumenService
import com.openlumen.widget.WidgetActionReceiver
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C267. The launcher had no long-press shortcuts, so the only documented way
 * out of a stuck filter was reading an adb command off a screen that might be
 * the thing that is broken.
 *
 * These assert the routing, because routing is the whole point: a shortcut
 * that invents its own preference write would drift from what the tile and
 * the notification do, and nothing would notice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShortcutActivityTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun launch(action: String): Intent? {
        Robolectric.buildActivity(
            ShortcutActivity::class.java,
            ShortcutActivity.intentFor(context, action)
        ).setup().get()
        return shadowOf(context).broadcastIntents.lastOrNull()
    }

    @Test fun `toggle goes through the receiver the widgets and tile already use`() {
        val sent = launch(ShortcutActivity.ACTION_TOGGLE)

        assertThat(sent?.action).isEqualTo(WidgetActionReceiver.ACTION_TOGGLE)
        assertThat(sent?.component?.className)
            .isEqualTo(WidgetActionReceiver::class.java.name)
    }

    @Test fun `emergency off goes through the documented escape hatch`() {
        // TURN_OFF is the one automation action that never asks for a token,
        // and the receiver clears the display even when the service will not
        // start. Routing this anywhere else would lose both properties.
        val sent = launch(ShortcutActivity.ACTION_EMERGENCY_OFF)

        assertThat(sent?.action).isEqualTo(LumenService.ACTION_TURN_OFF)
        assertThat(sent?.component?.className)
            .isEqualTo(AutomationReceiver::class.java.name)
    }

    @Test fun `next preset starts the service the notification action starts`() {
        Robolectric.buildActivity(
            ShortcutActivity::class.java,
            ShortcutActivity.intentFor(context, ShortcutActivity.ACTION_NEXT_PRESET)
        ).setup().get()

        val started = shadowOf(context).nextStartedService
        assertThat(started?.action).isEqualTo(LumenService.ACTION_CYCLE_PRESET)
        assertThat(started?.component?.className).isEqualTo(LumenService::class.java.name)
    }

    @Test fun `an action it does not own does nothing`() {
        // Positive control for the three above, and the reason the activity is
        // safe to export: it is reachable by any app, and everything outside
        // its own three actions has to fall through.
        val before = shadowOf(context).broadcastIntents.size

        launch("com.openlumen.shortcut.NOT_A_REAL_ACTION")

        assertThat(shadowOf(context).broadcastIntents).hasSize(before)
        assertThat(shadowOf(context).nextStartedService).isNull()
    }

    @Test fun `the shortcuts resource sends only actions the activity handles`() {
        // A renamed constant would otherwise leave a shortcut in the launcher
        // that opens nothing and reports nothing.
        val xml = File("src/main/res/xml/shortcuts.xml").readText()
        val declared = Regex("""android:action="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

        assertThat(declared).isNotEmpty()
        assertThat(ShortcutActivity.ACTIONS).containsAtLeastElementsIn(declared)
    }
}
