package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Schedule tab collects lux whether or not the phone has a light sensor.
 * Without one, the adapter's last refused registration used to fail the
 * shared flow inside its own scope, where nothing catches it, and the app
 * crashed about six seconds after the tab opened.
 */
@RunWith(RobolectricTestRunner::class)
class MissingLightSensorTest {

    @Test fun `a phone without a light sensor keeps running while a screen waits for lux`() {
        // Robolectric's SensorManager has no light sensor, like some tablets
        // and TV boxes.
        val adapter = LightSensorAdapter(RuntimeEnvironment.getApplication())
        assertThat(adapter.hasAmbientLightSensor()).isFalse()

        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        val screen = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val readings = CopyOnWriteArrayList<Float>()
            screen.launch { adapter.lux().collect { readings += it } }

            // Registration is retried after 250 ms, 1 s and 5 s; wait out all
            // three and the refusal after them.
            Thread.sleep(7_500)

            assertThat(uncaught).isEmpty()
            assertThat(readings).isEmpty()
            assertThat(adapter.availability.value).isFalse()
        } finally {
            screen.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
