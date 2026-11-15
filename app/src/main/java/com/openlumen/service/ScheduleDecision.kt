package com.openlumen.service

import com.openlumen.diagnostics.MatrixPreview
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.prefs.dayMatrix
import com.openlumen.prefs.effectiveMatrix
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isValidFixedTimeWindow
import com.openlumen.schedule.isValidSolarLocation
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

/**
 * The two decisions the service makes that a user can see: which schedule is
 * running, and whether the filter should be on right now. They lived inside
 * LumenService as private methods, where nothing could reach them, so the
 * rules that keep a corrupt import from crashing the foreground service and
 * keep the light sensor from overriding an explicit off were carried by
 * reading alone.
 */

/**
 * Translate the persisted schedule into the engine's own mode.
 *
 * Every bound here matters. An imported blob can carry an hour of 25, a
 * latitude of NaN or a timezone that does not exist on this device, and the
 * calculator is called from the foreground service, so a throw there takes the
 * filter down rather than showing an error.
 */
fun mapScheduleMode(
    schedule: ScheduleDto,
    nextAlarmAt: ZonedDateTime?
): ScheduleMode = when (schedule.mode) {
    ScheduleModeDto.AlwaysOn -> ScheduleMode.AlwaysOn
    ScheduleModeDto.AlwaysOff -> ScheduleMode.AlwaysOff
    ScheduleModeDto.FixedTime -> ScheduleMode.FixedTime(
        LocalTime.of(schedule.startHour.coerceIn(0, 23), schedule.startMinute.coerceIn(0, 59)),
        LocalTime.of(schedule.endHour.coerceIn(0, 23), schedule.endMinute.coerceIn(0, 59))
    )
    ScheduleModeDto.Solar -> {
        val lat = schedule.latitude
        val lng = schedule.longitude
        if (!isValidSolarLocation(lat, lng)) {
            // No usable location means no sunrise to compute. Standing down is
            // the only safe answer: the calculator would throw on NaN.
            ScheduleMode.AlwaysOff
        } else {
            ScheduleMode.Solar(
                latitude = checkNotNull(lat),
                longitude = checkNotNull(lng),
                locationZoneId = schedule.solarTimezone
                    ?.let { runCatching { ZoneId.of(it) }.getOrNull() },
                sunsetOffsetMin = schedule.sunsetOffsetMin.coerceIn(-180, 180),
                sunriseOffsetMin = schedule.sunriseOffsetMin.coerceIn(-180, 180)
            )
        }
    }
    ScheduleModeDto.UntilNextAlarm -> ScheduleMode.UntilNextAlarm(
        start = LocalTime.of(
            schedule.startHour.coerceIn(0, 23),
            schedule.startMinute.coerceIn(0, 59)
        ),
        nextAlarmAt = nextAlarmAt
    )
}

/** Which filter the screen should carry right now. */
enum class FilterPhase {
    OFF,

    /** The schedule window (or the light sensor): the user's preset. */
    NIGHT,

    /** Outside the window with the day filter on: [com.openlumen.prefs.dayMatrix]. */
    DAY
}

/**
 * [shouldFilterBeActive], plus the day filter for the hours it leaves off.
 *
 * The day filter only fills the gap of a schedule that has one. Always on has
 * no gap, and the Off preset and Always off are the user saying "not now",
 * which covers the day as well. A solar schedule without a usable location
 * maps to [ScheduleMode.AlwaysOff], so it never opens a window and must not be
 * read as "day forever" either.
 */
fun filterPhase(
    prefs: Preferences,
    mode: ScheduleMode,
    scheduleActive: Boolean,
    lightActive: Boolean
): FilterPhase {
    if (shouldFilterBeActive(prefs, scheduleActive, lightActive)) return FilterPhase.NIGHT
    return if (dayFilterCanShow(prefs, mode)) FilterPhase.DAY else FilterPhase.OFF
}

/** Whether [filterPhase] ever answers [FilterPhase.DAY] for these settings. */
private fun dayFilterCanShow(prefs: Preferences, mode: ScheduleMode): Boolean {
    val hasWindow = mode is ScheduleMode.FixedTime ||
        mode is ScheduleMode.Solar ||
        mode is ScheduleMode.UntilNextAlarm
    val explicitStandby = prefs.activePresetKey == Preferences.OFF_PRESET_KEY
    return prefs.schedule.dayFilter && hasWindow && !explicitStandby
}

/**
 * Every matrix these settings put on screen over a day: the preset, and the
 * day filter when the schedule leaves room for it. Auto chooses its driver
 * against all of them (C364), so it does not switch drivers at the evening
 * hand-over, and the screens pass the same list so what they describe is the
 * driver the service runs.
 *
 * The progressive ramp moves the preset's intensity from one end to the other,
 * and warmth moves the same way along it, so both ends stand in for the ramp.
 * The far end only counts in the modes that run the ramp, the ones with an
 * opening moment ([progressiveIntensityAt] answers null for the rest).
 */
fun driverTargets(prefs: Preferences): List<LumenMatrix> = listOfNotNull(
    prefs.effectiveMatrix(),
    prefs.copy(presetIntensity = prefs.schedule.progressiveEndIntensity)
        .effectiveMatrix()
        .takeIf { rampCanRun(prefs) },
    dayFilterTarget(prefs)
)

private fun rampCanRun(prefs: Preferences): Boolean {
    if (!prefs.schedule.progressiveIntensity) return false
    return when (val mode = mapScheduleMode(prefs.schedule, nextAlarmAt = null)) {
        // A window that starts where it ends never opens.
        is ScheduleMode.FixedTime -> isValidFixedTimeWindow(mode.start, mode.end)
        is ScheduleMode.Solar -> true
        else -> false
    }
}

/** The day filter's matrix when these settings ever show it, otherwise null. */
fun dayFilterTarget(prefs: Preferences): LumenMatrix? =
    prefs.dayMatrix().takeIf {
        dayFilterCanShow(prefs, mapScheduleMode(prefs.schedule, nextAlarmAt = null))
    }

/**
 * What the screen carries in [phase]. The service applies this and mirrors it
 * for Direct Boot, so a reboot outside the window restores the day filter and
 * one inside it restores the preset.
 *
 * The night side goes through `MatrixPreview.matrixFor`, which applies the
 * progressive ramp, so the service and the Home tab's readouts cannot disagree
 * about what is on screen (C61's blue-suppression indicator depends on it).
 */
fun phaseMatrix(
    prefs: Preferences,
    phase: FilterPhase,
    now: ZonedDateTime = ZonedDateTime.now()
): LumenMatrix = when (phase) {
    FilterPhase.NIGHT -> MatrixPreview.matrixFor(prefs, now)
    FilterPhase.DAY -> prefs.dayMatrix()
    FilterPhase.OFF -> LumenMatrix.IDENTITY
}

/**
 * Remembers the phase the service last applied, so the hand-over between the
 * day filter and the preset fades like an off to on flip. To the apply gate
 * that hand-over is one active target replacing another, which a slider drag
 * also is, and a slider drag must not fade.
 *
 * The first phase a service instance sees counts as a change. A fresh service
 * has nothing on screen and the gate fades or snaps it in as usual, but one
 * that restored a tint before the first unlock has, and that restored phase
 * can be stale by the time the user unlocks: a reboot at 19:59 restores the
 * day filter, and an unlock at 20:05 has to fade into the preset, not jump.
 */
internal class FilterPhaseTracker {
    private val last = AtomicReference<FilterPhase?>(null)

    fun changedTo(phase: FilterPhase): Boolean = last.getAndSet(phase) != phase
}

/**
 * Whether the filter should be on.
 *
 * The Off preset and the Always off schedule are the user saying "not now".
 * The light sensor is an extra trigger for an ordinary schedule, never a way
 * to overrule either of those: a user who picks Off and walks into a dark room
 * should not have the screen tint itself.
 */
fun shouldFilterBeActive(
    prefs: Preferences,
    scheduleActive: Boolean,
    lightActive: Boolean
): Boolean {
    val explicitStandby = prefs.activePresetKey == Preferences.OFF_PRESET_KEY ||
        prefs.schedule.mode == ScheduleModeDto.AlwaysOff
    return !explicitStandby && (scheduleActive || lightActive)
}
