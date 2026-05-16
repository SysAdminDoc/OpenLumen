# Wake, Alarm, and Vitals Audit

> Tied to roadmap candidate **C54** (Wake/alarm/battery audit).

OpenLumen is a long-running foreground service. Android's vitals
framework treats long-running services as high-suspicion by default,
so we document here exactly what wakes the device, when, and why —
both for our own discipline and so a paranoid user (or F-Droid
reviewer) can audit it.

## What never wakes the device

- **The schedule's `nextTransition` clock.** Even though we use
  `AlarmManager.setExactAndAllowWhileIdle()`, the transitions fire at
  most twice a day (sunset + sunrise, or two fixed-time boundaries).
  Wake count is bounded by the schedule mode, not by app behavior.
- **The light sensor.** When the ambient-light trigger is enabled, the
  service registers a `SensorManager.SENSOR_DELAY_NORMAL` listener
  (~5 Hz) — but only while both `enabled` AND `lightSensorEnabled`
  are true. The listener runs in the app's process; it does not hold
  a separate wake lock and the sensor framework batches readings.
- **Preference changes.** `PreferencesStore.flow` is collected in the
  service via a normal coroutine; there's no scheduler involved. A
  preference write fires an emission immediately, not via an alarm.
- **The QS tile / widget / notification button.** All of these route
  through `Intent` start-service calls. They don't hold wake locks.
- **The crash logger.** Writes to local file on the uncaught-exception
  thread. No alarms, no wake locks.

## What does (legitimately) wake the device

- **Schedule transitions** via
  `AlarmManager.setExactAndAllowWhileIdle()`. One alarm per pending
  transition, replaced each time the transition is recomputed. A
  fixed-time schedule fires twice in 24h; a solar schedule fires
  twice in 24h (sunset + sunrise). An `AlwaysOn` / `AlwaysOff`
  schedule fires zero times.
- **Boot completion** wakes the boot receiver, which decides whether
  to start the service. Single fire per device boot.
- **Smooth-transition ramps** while the device is awake. The ramp
  runs on `lifecycleScope`, which is the service's own scope. It does
  not register an alarm and does not hold a wake lock.

## Why we use `setExactAndAllowWhileIdle` (not just `set`)

A schedule that flips the filter at 22:00 should flip at 22:00, not
"sometime in the next Doze maintenance window". Color tinting for
sleep hygiene loses value if the transition slips 30 minutes.

But: `SCHEDULE_EXACT_ALARM` was demoted to a runtime permission in
Android 12. We handle the demotion gracefully:

```kotlin
try {
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(RTC_WAKEUP, triggerMs, pi)
    } else {
        am.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerMs, pi)
    }
} catch (e: SecurityException) {
    // Some OEMs revoke the exact-alarm grant after install.
    am.setAndAllowWhileIdle(RTC_WAKEUP, triggerMs, pi)
}
```

The inexact path can slip up to the Doze maintenance window (varies
by Android version; typically tens of minutes). That's documented as
expected behavior in `docs/troubleshooting.md`.

## Battery cost

Categorized by Android vitals' own framing:

| Vital | OpenLumen contribution | Why |
|---|---|---|
| Background wake-ups | Bounded by schedule (0–2/day) | Only schedule alarms, no polling |
| Wake locks | None held directly | The foreground notification keeps the service alive, not a wake lock |
| Mobile network use | Zero | No INTERNET permission |
| Stuck wake-locks | None held | See above |
| Frozen frames / slow rendering | Compose UI only | No custom drawing, no SurfaceView animation |
| ANRs | Service callbacks have bounded execution time | `applyMutex.withLock {}` and `withTimeoutOrNull(2000)` on engine clear |

We do not collect actual battery telemetry — there is no INTERNET
permission. If a contributor reports drain, the right path is:

1. Open Settings → Battery → see the OpenLumen attribution.
2. Compare across a session with and without the filter enabled.
3. Capture `adb shell dumpsys batterystats com.openlumen` if you
   have ADB available.
4. File an issue with the device fingerprint and the dumpsys excerpt.

## How to audit independently

```bash
# What alarms does OpenLumen own?
adb shell dumpsys alarm | grep -A 10 com.openlumen

# Sensors registered?
adb shell dumpsys sensorservice | grep -B 2 -A 10 OpenLumen

# Foreground service state?
adb shell dumpsys activity services com.openlumen

# Wake locks held?
adb shell dumpsys power | grep -A 3 PARTIAL_WAKE_LOCK
```

You should see:
- At most one pending alarm (the schedule's next transition).
- A registered light sensor listener if and only if the ambient
  trigger is enabled.
- A single `LumenService` entry running in the foreground.
- Zero `PARTIAL_WAKE_LOCK` entries attributed to `com.openlumen`.

If you see anything different, it's a bug. File it.

## Future work

- The ramp coroutine currently runs at a 1-second cadence by default.
  For long ramps (15+ min) we could reduce to 5-second cadence
  without visible jerk. Tracked under C99 (event-driven ambient
  sampling) on the roadmap.
- Direct Boot (C28) would add a `LOCKED_BOOT_COMPLETED` wake fire,
  but only for users with the filter enabled — and even then, only
  once per boot. Doesn't change the steady-state vitals story.
