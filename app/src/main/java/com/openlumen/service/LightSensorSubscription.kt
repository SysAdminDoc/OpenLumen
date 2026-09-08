package com.openlumen.service

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Owns the ambient light sensor collection job and latest-lux cache.
 */
internal class LightSensorSubscription(
    private val luxFlow: () -> Flow<Float>,
    private val scope: CoroutineScope,
    private val onLuxChanged: suspend (Float) -> Unit,
    private val onUnavailable: suspend () -> Unit = {},
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
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
            var failures = 0
            while (isActive) {
                try {
                    luxFlow().collect { lux ->
                        failures = 0
                        latestLux.set(lux)
                        onLuxChanged(lux)
                    }
                    if (!isActive) break
                    failures += 1
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    if (!isActive) break
                    failures += 1
                }

                if (!isActive) break
                if (failures > MAX_RETRIES) {
                    onUnavailable()
                    break
                }
                retryDelay(retryDelayMs(failures))
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

    /** Restart collection after the platform pauses sensor delivery at screen-off. */
    fun restart(enabled: Boolean) {
        cancel()
        if (enabled) {
            update(true)
        } else {
            invalidate()
        }
    }

    private fun retryDelayMs(attempt: Int): Long = when (attempt) {
        1 -> 250L
        2 -> 1_000L
        else -> 5_000L
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
