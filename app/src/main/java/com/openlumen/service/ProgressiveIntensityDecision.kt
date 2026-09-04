package com.openlumen.service

import com.openlumen.prefs.Preferences
import com.openlumen.schedule.ProgressiveIntensity
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.activeWindowStart
import java.time.ZonedDateTime

/**
 * The intensity to apply right now, and when to wake up to move it along.
 *
 * The app ramps once, at the transition. What gets asked for is a filter that
 * keeps deepening across the evening, and this is the decision that turns the
 * preference into a number the engine can take.
 */

/**
 * The intensity for [now], or null when the progressive ramp does not apply.
 *
 * Null means "use the preset's own intensity", which is every case where the
 * mode is off, the schedule has no opening moment (always on, always off,
 * until-next-alarm), or the window is not open.
 */
internal fun progressiveIntensityAt(
    prefs: Preferences,
    mode: ScheduleMode,
    now: ZonedDateTime
): Float? {
    if (!prefs.schedule.progressiveIntensity) return null
    val start = activeWindowStart(mode, now) ?: return null

    return ProgressiveIntensity.at(
        now = now,
        start = start,
        end = rampEnd(prefs, start),
        startIntensity = prefs.presetIntensity.coerceIn(0f, 1f),
        endIntensity = prefs.schedule.progressiveEndIntensity.coerceIn(0f, 1f)
    )
}

/**
 * When to wake up next to move the ramp, or null when there is nothing left to
 * move.
 *
 * The steps are alarms rather than a ticker, so a sleeping device is not kept
 * awake for a colour change nobody is looking at.
 */
internal fun nextProgressiveStep(
    prefs: Preferences,
    mode: ScheduleMode,
    now: ZonedDateTime
): ZonedDateTime? {
    if (!prefs.schedule.progressiveIntensity) return null
    val start = activeWindowStart(mode, now) ?: return null

    return ProgressiveIntensity.nextStepAfter(now, start, rampEnd(prefs, start))
}

/**
 * The user's chosen end time, on the calendar day that follows [start].
 *
 * The end is a wall-clock time, usually after midnight, so it is resolved
 * against the day the window opened rather than against today: past midnight
 * "today" is already the next day and the ramp would look finished.
 */
private fun rampEnd(prefs: Preferences, start: ZonedDateTime): ZonedDateTime =
    start
        .withHour(prefs.schedule.progressiveEndHour.coerceIn(0, 23))
        .withMinute(prefs.schedule.progressiveEndMinute.coerceIn(0, 59))
        .withSecond(0)
        .withNano(0)
