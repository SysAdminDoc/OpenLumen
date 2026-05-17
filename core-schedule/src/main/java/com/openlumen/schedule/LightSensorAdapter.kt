package com.openlumen.schedule

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.max

/**
 * Wraps the ambient light sensor as a Flow<Float> of lux readings. Caller decides what
 * to do with low values (e.g. "below 2 lux for 30s → engage Deep Sleep preset").
 *
 * Heavy filtering on the SensorEvent stream — we only emit when the smoothed lux value
 * crosses a 5% delta vs. the last emission to avoid waking the service every half-second.
 *
 * Concurrency: the sensor callback runs on the SensorManager's worker thread;
 * the flow is conflated so a slow collector can never block the callback and
 * the latest reading always wins. We never call `trySend` and silently drop —
 * `BufferOverflow.DROP_OLDEST` makes the buffer behave like the conflation
 * `callbackFlow` semantics for sensors.
 */
class LightSensorAdapter(private val context: Context) {
    fun lux(): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: run { close(); return@callbackFlow }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
            ?: run { close(); return@callbackFlow }

        var ema = -1f
        val alpha = 0.2f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = event.values.firstOrNull() ?: return
                if (!raw.isFinite() || raw < 0f) return
                ema = if (ema < 0f) raw else alpha * raw + (1f - alpha) * ema
                trySend(ema)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            close()
            return@callbackFlow
        }
        // unregisterListener is safe from any thread; the SensorManager
        // internally posts a removal to its own handler.
        awaitClose { sm.unregisterListener(listener) }
    }
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .distinctUntilChanged { a, b ->
            abs(a - b) < max(abs(a) * 0.05f, 0.5f)
        }
}
