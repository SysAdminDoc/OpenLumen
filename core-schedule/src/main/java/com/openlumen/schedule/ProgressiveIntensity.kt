package com.openlumen.schedule

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Intensity that keeps deepening across the evening instead of arriving all at
 * once.
 *
 * The app ramps once, at the transition, over a fixed fade. What people ask
 * for is different: warmer at six, close to full at midnight. This is the
 * curve for that, as a pure function of the clock, so the service can ask it
 * for a value whenever it happens to be awake rather than running a ticker.
 */
object ProgressiveIntensity {

    /**
     * How many steps the evening is split into.
     *
     * Each step is one alarm, so this trades smoothness against wake-ups. Six
     * over a typical five-hour evening is a change roughly every fifty
     * minutes, which is well under the rate at which a colour shift is
     * noticeable and nowhere near enough alarms to matter to Doze.
     */
    const val STEPS: Int = 6

    /**
     * The intensity at [now], ramping from [start] to [end].
     *
     * Before the ramp starts this is [startIntensity]; after it ends,
     * [endIntensity]. An [end] at or before [start] is read as crossing
     * midnight and moved to the next day, which is the normal case: the ramp
     * runs from sunset to some hour after it.
     */
    fun at(
        now: ZonedDateTime,
        start: ZonedDateTime,
        end: ZonedDateTime,
        startIntensity: Float,
        endIntensity: Float
    ): Float {
        // Strictly before, not "not after": an end equal to the start is a
        // ramp of no length, which holds the late value. Reading it as a
        // midnight crossing would turn it into a 24-hour ramp, which is not
        // a thing anyone sets deliberately.
        val finish = if (end.isBefore(start)) end.plusDays(1) else end
        val span = Duration.between(start, finish).toMillis()
        if (span <= 0L) return endIntensity

        val elapsed = Duration.between(start, now).toMillis()
        val fraction = (elapsed.toDouble() / span).coerceIn(0.0, 1.0).toFloat()
        return (startIntensity + (endIntensity - startIntensity) * fraction)
            .coerceIn(0f, 1f)
    }

    /**
     * When to wake up next to move the ramp along, or null once it is over.
     *
     * Steps are evenly spaced, so this is the next boundary strictly after
     * [now]. Returning the boundary rather than a fixed interval keeps the
     * wake-ups aligned to the ramp regardless of when the service happens to
     * ask.
     */
    fun nextStepAfter(
        now: ZonedDateTime,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): ZonedDateTime? {
        val finish = if (end.isBefore(start)) end.plusDays(1) else end
        val span = Duration.between(start, finish).toMillis()
        if (span <= 0L) return null

        for (step in 1..STEPS) {
            val boundary = start.plus(Duration.ofMillis(span * step / STEPS))
            if (boundary.isAfter(now)) return boundary
        }
        return null
    }
}
