package com.openlumen.schedule

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Wraps the ambient light sensor as a Flow<Float> of lux readings. Caller decides what
 * to do with low values (e.g. "below 2 lux for 30s → engage Deep Sleep preset").
 *
 * Heavy filtering on the SensorEvent stream — we only emit when the smoothed lux value
 * crosses a 5% delta vs. the last emission to avoid waking the service every half-second.
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
                ema = if (ema < 0f) raw else alpha * raw + (1f - alpha) * ema
                trySend(ema)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }.distinctUntilChanged { a, b -> Math.abs(a - b) < (a * 0.05f) }
}
