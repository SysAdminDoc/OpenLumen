package com.openlumen.service

import com.openlumen.schedule.LightSensorAdapter
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the ambient light sensor collection job and latest-lux cache.
 */
internal class LightSensorSubscription(
    private val lightSensor: LightSensorAdapter,
    private val scope: CoroutineScope,
    private val onLuxChanged: suspend (Float) -> Unit
) {
    private var job: Job? = null
    private val latestLux = AtomicReference(-1f)

    fun update(enabled: Boolean) {
        if (!enabled) {
            cancel()
            invalidate()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            lightSensor.lux().collect { lux ->
                latestLux.set(lux)
                onLuxChanged(lux)
            }
        }
    }

    fun currentLuxOrNegative(): Float = latestLux.get()

    fun invalidate() {
        latestLux.set(-1f)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
