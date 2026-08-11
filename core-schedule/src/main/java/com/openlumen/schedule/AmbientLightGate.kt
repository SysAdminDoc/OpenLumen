package com.openlumen.schedule

import kotlin.math.max

/**
 * Stateful light-trigger decision with a small exit band to prevent flicker at
 * the user's threshold. A dark-room reading engages below [thresholdLux]; once
 * engaged, the reading must rise above [disengageThreshold] before the gate
 * opens again.
 *
 * The state is deliberately reset for disabled, invalid, or threshold-changed
 * inputs. That prevents a stale light-only activation surviving screen-off,
 * sensor loss, or a newly selected threshold.
 */
class AmbientLightGate {
    private var active = false
    private var lastThresholdLux = Float.NaN

    @Synchronized
    fun update(enabled: Boolean, thresholdLux: Float, lux: Float): Boolean {
        if (!enabled || !lux.isFinite() || lux < 0f || !thresholdLux.isFinite()) {
            reset()
            return false
        }

        val threshold = thresholdLux.coerceAtLeast(0f)
        if (lastThresholdLux != threshold) {
            active = false
            lastThresholdLux = threshold
        }

        active = if (active) {
            lux < disengageThreshold(threshold)
        } else {
            lux < threshold
        }
        return active
    }

    @Synchronized
    fun reset() {
        active = false
        lastThresholdLux = Float.NaN
    }

    companion object {
        /**
         * Ten percent or one lux, whichever is larger, keeps the exit edge
         * meaningfully above the engage edge even for very low thresholds.
         */
        fun disengageThreshold(thresholdLux: Float): Float =
            thresholdLux.coerceAtLeast(0f) + max(thresholdLux * 0.10f, 1f)
    }
}
