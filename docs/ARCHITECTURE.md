# OpenLumen Architecture

> Snapshot as of v0.4.0. Tracks the actual code in `main`, not aspirational
> structure. Update this doc with the same PR that changes the relevant
> module.

## Modules

OpenLumen is a four-module Gradle project. Each `core-*` module is a plain
Android Library; `app` depends on all three.

```
OpenLumen/
├── app/             Compose UI, foreground service, tile, boot receiver,
│                    Hilt graph, manifest, resources
├── core-engine/     ColorEngine abstraction + 4 driver impls + DriverProbe
├── core-schedule/   NOAA solar calculator, schedule modes, light sensor adapter
└── core-prefs/      DataStore-backed prefs, JSON serialization
```

Why split it this way:

- **`core-engine`** has zero Android-framework UI dependencies. It pulls
  `core-ktx` for `Context` and `Build.VERSION.SDK_INT` and that's it. This
  keeps driver code testable on the JVM and prevents UI accidentally reaching
  into the engine layer.
- **`core-schedule`** is pure JVM math (the NOAA algorithm) plus a single
  Android adapter for `SensorManager`. The math is unit-testable without an
  emulator.
- **`core-prefs`** owns the persisted shape — `Preferences`, `MatrixDto`,
  `ScheduleDto`, etc. The DTOs are deliberately separate from `LumenMatrix`
  / `ScheduleMode` so engine and schedule code stay free of
  `kotlinx.serialization` annotations.

## Single source of truth

Unlocked app state lives in **one** place: the `Preferences` JSON blob in
DataStore, managed by `PreferencesStore`
(`core-prefs/.../PreferencesStore.kt`).

Everything else is a view:

- The Compose UI subscribes to `prefs.flow` via `OpenLumenViewModel.state`.
- The foreground service subscribes to `prefs.flow` via
  `observePreferences()`.
- The tile subscribes to `prefs.flow` on every `onStartListening()`.
- The boot receiver writes `enabled=true` and lets the service flow do the
  rest.

Direct Boot is the only intentional mirror. `LumenService` writes a small
device-protected `DirectBootStateStore` snapshot containing the last active
tint matrix, selected engine, and enabled/active flags. `LockedBootReceiver`
uses that mirror before first unlock and does not read the full preferences
blob until credential-protected storage is available.

Writers all go through `prefs.update { current -> next }` so concurrent
toggles (UI + tile + boot) never race on read-modify-write.

## Runtime flow

1. User flips the Switch on `HomeScreen` →
   `OpenLumenViewModel.setEnabled(true)` →
   `prefs.update { it.withFilterEnabled(true) }`.
2. `PreferencesStore` flow emits the new `Preferences` snapshot.
3. `LumenService.observePreferences()` collects, picks an engine via
   `DriverProbe`, applies the current `LumenMatrix`, and schedules the next
   transition alarm via `AlarmManager`. Auto mode picks the best available
   non-root engine; root engines require an explicit Driver-tab selection.
4. When the alarm fires, `ScheduleAlarmReceiver` sends
   `ACTION_REEVALUATE` back to `LumenService`, which re-derives the matrix
   and reschedules.
5. When the user toggles off, the service clears the engine (`clear()`),
   hard-clears known SurfaceFlinger/KCAL root state, and calls
   `stopSelf()`. External ADB / Tasker commands enter through
   `AutomationReceiver`, not the non-exported service directly.

## Concurrency model

Inside `LumenService`:

- `applyMutex: Mutex` serializes every `engine.apply()` and `engine.clear()`
  call. Two concurrent `su` subprocesses would step on each other, especially
  for KCAL where each call writes multiple sysfs files.
- `rampMutex: Mutex` serializes smooth-transition cancel/join/launch state
  so prefs emissions, light-sensor emissions, engine swaps, and manual off
  actions cannot leave an old ramp coroutine applying over the latest target.
- `latestPrefs: AtomicReference<Preferences?>` and `latestLux: AtomicReference<Float>`
  hold the most recent snapshots. The alarm receiver and the sensor flow read
  these without coupling to the prefs-flow collector coroutine.
- `prefs.flow.conflate()` drops intermediate emissions while a slow `su`
  call is in flight. Drag a slider for two seconds and only the final value
  hits the driver.

## Driver abstraction

`core-engine/src/main/java/com/openlumen/engine/ColorEngine.kt` defines a
four-method contract:

```kotlin
interface ColorEngine {
    val kind: EngineKind
    suspend fun isAvailable(context: Context): Boolean
    suspend fun apply(context: Context, matrix: LumenMatrix)
    suspend fun clear(context: Context)
}
```

`EngineKind` carries a `rank: Int`. `DriverProbe.pickBest()` returns the
highest-ranked engine whose `isAvailable()` returns true. Ranks:

| Engine | Rank | Why |
|---|---:|---|
| `COLOR_DISPLAY_MANAGER` | 100 | AOSP-blessed framebuffer transform; no root, no overlay. Same path Night Light uses. |
| `SURFACE_FLINGER` | 90 | Root-only framebuffer transform via `service call`. Universal across SoCs but needs `su`. |
| `KCAL` | 70 | Panel-driver write. Best dim quality on Snapdragon kernels that expose it, but device-specific. |
| `OVERLAY` | 10 | Universal rootless fallback. Capped at ~80% opacity by Android 12 untrusted-touch rules. |

If `DriverProbe.probeAll()` returns no `available = true`, `pickBest()` falls
back to `OVERLAY` so the user always gets *something*.

### Engine implementations

- **`ColorDisplayManagerEngine`** — reflection against the hidden
  `android.hardware.display.ColorDisplayManager` AOSP API. Cached after first
  successful load. Gated on `WRITE_SECURE_SETTINGS` being granted.
- **`SurfaceFlingerEngine`** — `service call SurfaceFlinger <code>` via `su`.
  Probes a list of candidate transaction codes (1015, 1023, 1030, 1036) with
  the disable transaction and caches the working one for the device.
- **`KcalEngine`** — writes RGB values + enable flag to
  `/sys/devices/platform/kcal_ctrl.0/*` sysfs nodes via `su`. Requires a
  Qualcomm device with a kernel that exposes KCAL.
- **`OverlayEngine`** — `TYPE_APPLICATION_OVERLAY` window with a tinted
  full-screen `View`. Touch-pass-through via `FLAG_NOT_TOUCHABLE` and
  `FLAG_NOT_FOCUSABLE`. Alpha derived from both dim and tint strength so
  color-only presets are visible at `dim=0`.

### Adding a new engine

1. Add an enum entry to `EngineKind` with a sensible `rank`.
2. Implement `ColorEngine` in `core-engine/src/main/java/com/openlumen/engine/engines/`.
3. Register it in `DriverProbe.Companion.defaultEngines()`.
4. Add an `EngineKindDto` enum entry in `core-prefs/.../Preferences.kt`.
5. Wire the new DTO value through `LumenService.ensureEngine()` and
   `DriverScreen.toEngineKind()`.
6. Add at least an "doesn't throw on unsupported hardware" unit test.

## Schedule abstraction

`core-schedule/src/main/java/com/openlumen/schedule/Schedule.kt` defines a
sealed `ScheduleMode`:

- `AlwaysOn` — filter on permanently, no transitions
- `AlwaysOff` — filter off, no transitions (this is the "disabled" form of
  the schedule, not the same as the global enable flag)
- `FixedTime(start, end)` — local-time window
- `Solar(lat, lng, sunsetOffsetMin, sunriseOffsetMin)` — NOAA-computed dusk
  and dawn boundaries

Two pure functions are the public surface:

- `isActive(mode, now, zone): Boolean` — is the filter supposed to be on
  right now?
- `nextTransition(mode, now, zone): ZonedDateTime?` — when's the next
  state-flip? Returns `null` for `AlwaysOn` / `AlwaysOff` / degenerate fixed
  windows.

`LumenService` uses both: `isActive` for the current apply decision and
`nextTransition` to set the next `AlarmManager` alarm.

## Persistence

`PreferencesStore` is a thin wrapper around DataStore's `preferencesDataStore`,
backed by a **single string key** holding a JSON-serialized `Preferences`
object. `DirectBootStateStore` is a separate typed DataStore in device-
protected storage and is not a second source of truth; it is a boot-time
cache derived from service emissions. Why single-key:

- One read, one decode per emission. No N+1 across dozens of typed keys.
- Profile export/import is "dump the string, restore the string."
- Schema evolution is centralized in one place (see C29 on the roadmap).

The store always runs values through `sanitize()` on read **and** write so
NaN / Inf / out-of-range values from a corrupted import never reach the GPU
path or the AlarmManager.

## UI structure

- `MainActivity` — single Compose `Activity`, edge-to-edge, requests
  POST_NOTIFICATIONS on API 33+ at first launch.
- `OpenLumenRoot.kt` — bottom-nav scaffold with 5 destinations.
- `screens/HomeScreen.kt` — main toggle, intensity, dim, RGB picker.
- `screens/ScheduleScreen.kt` — mode radio + fixed/solar inputs.
- `screens/PresetsScreen.kt` — preset list with color chips.
- `screens/DriverScreen.kt` — engine picker with availability badges.
- `screens/AboutScreen.kt` — version, license, backup, crash log.
- `viewmodel/OpenLumenViewModel.kt` — the single `AndroidViewModel`. Holds
  the `state: StateFlow<Preferences>` and exposes mutation functions that
  all funnel into `prefs.update`.

## What the service does NOT do

- **No background polling.** The old 60-second ticker was replaced with
  `AlarmManager` + a `BroadcastReceiver` in v0.3.0. The light sensor uses
  `SensorManager.registerListener` (event-driven, also no polling).
- **No location permission.** Solar mode uses user-entered coordinates only.
  FusedLocationProvider would pull in Play Services and break F-Droid.
- **No network.** The manifest deliberately omits `android.permission.INTERNET`
  and CI enforces it.
- **No accessibility service.** Considered (see C79) but not implemented;
  too sensitive a surface for the current threat model.

## Gotchas worth knowing before you touch this

- **Reflection drift.** `ColorDisplayManagerEngine` reflects against a hidden
  AOSP API. Future Android versions can rename methods; the `runCatching`
  ladder is defensive on purpose.
- **`service call SurfaceFlinger` code drift.** Historically `1015` for
  `setDisplayColorTransform`. We probe `1015`, `1023`, `1030`, `1036`. If a new
  Android version drifts again, add it to `SurfaceFlingerEngine.candidates`.
- **Overlay window token.** `TYPE_APPLICATION_OVERLAY` needs a Service or
  Activity context; `Application` context throws. `LumenService.ensureEngine()`
  calls `overlay.installView(this, …)` before the first apply.
- **Android 14 `specialUse` FGS property.** The manifest must declare
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` or `startForeground()` with
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` throws.
- **Android 12+ overlay alpha cap.** With `FLAG_NOT_TOUCHABLE`, overlay alpha
  is capped at ~0.8 (≤204 in 8-bit ARGB). Hard dim past that needs a root
  driver.
