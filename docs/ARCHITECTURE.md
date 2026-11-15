# OpenLumen Architecture

> Snapshot as of v0.7.1. It tracks the actual code in `main`, not an aspirational structure. Update this doc in the same PR that changes the relevant module.

## Modules

OpenLumen is a four-module Gradle project. Each `core-*` module is a plain Android Library, and `app` depends on all three.

```
OpenLumen/
├── app/             Compose UI, foreground service, tile, boot receiver,
│                    Hilt graph, manifest, resources
├── core-engine/     ColorEngine abstraction + 4 driver impls + DriverProbe
├── core-schedule/   NOAA solar calculator, schedule modes, light sensor adapter
└── core-prefs/      DataStore-backed prefs, JSON serialization
```

Why split it this way:

- **`core-engine`** has zero Android-framework UI dependencies. It pulls `core-ktx` for `Context` and `Build.VERSION.SDK_INT` and that's it. That keeps driver code testable on the JVM and stops the UI from reaching into the engine layer by accident.
- **`core-schedule`** is pure JVM math (the NOAA algorithm) plus a single Android adapter for `SensorManager`. The math is unit-testable without an emulator.
- **`core-prefs`** owns the persisted shape (`Preferences`, `MatrixDto`, `ScheduleDto` and so on). The DTOs are deliberately separate from `LumenMatrix` / `ScheduleMode` so engine and schedule code stay free of `kotlinx.serialization` annotations.

## Single source of truth

Unlocked app state lives in **one** place, the `Preferences` JSON blob in DataStore, managed by `PreferencesStore` (`core-prefs/.../PreferencesStore.kt`).

Everything else is a view:

- The Compose UI subscribes to `prefs.flow` via `OpenLumenViewModel.state`.
- The foreground service subscribes to `prefs.flow` via `observePreferences()`.
- The tile subscribes to `prefs.flow` on every `onStartListening()`.
- The boot receiver writes `enabled=true` and lets the service flow do the rest.

Direct Boot is the only intentional mirror. `DirectBootMirror` writes a small device-protected `DirectBootStateStore` snapshot containing the last active tint matrix, selected engine, and enabled/active flags. `LockedBootReceiver` uses that mirror before first unlock and does not read the full preferences blob until credential-protected storage is available.

Writers all go through `prefs.update { current -> next }` so concurrent toggles (UI + tile + boot) never race on read-modify-write.

## Runtime flow

1. User flips the Switch on `HomeScreen` → `OpenLumenViewModel.setEnabled(true)` → `prefs.update { it.withFilterEnabled(true) }`.
2. `PreferencesStore` flow emits the new `Preferences` snapshot.
3. `LumenService.observePreferences()` collects, asks `EngineController` to pick/apply the current `LumenMatrix`, and asks `ScheduleAlarmOrchestrator` to schedule the next transition alarm via `AlarmManager`. Auto prefers any available root driver, then the secure-settings driver, then Overlay.
4. When the alarm fires, `ScheduleAlarmReceiver` sends `ACTION_REEVALUATE` back to `LumenService`, which re-derives the matrix and reschedules.
5. When the user toggles off, the service clears the engine (`clear()`), hard-clears known SurfaceFlinger/KCAL root state, and calls `stopSelf()`. External ADB / Tasker commands enter through `AutomationReceiver`, not the non-exported service directly.

## Concurrency model

Inside `LumenService` and its service-package collaborators:

- `EngineController.applyMutex` serializes every `engine.apply()` and `engine.clear()` call. Two concurrent `su` subprocesses would step on each other, especially for KCAL where each call writes multiple sysfs files.
- `EngineController.rampMutex` serializes smooth-transition cancel/join/launch state so prefs emissions, light-sensor emissions, engine swaps, and manual off actions cannot leave an old ramp coroutine applying over the latest target.
- `LumenService.latestPrefs` and `LightSensorSubscription.latestLux` hold the most recent snapshots. The alarm receiver and sensor flow read these without coupling to the prefs-flow collector coroutine.
- `prefs.flow.conflate()` drops intermediate emissions while a slow `su` call is in flight. Drag a slider for two seconds and only the final value hits the driver.

## Driver abstraction

`core-engine/src/main/java/com/openlumen/engine/ColorEngine.kt` defines a four-method contract:

```kotlin
interface ColorEngine {
    val kind: EngineKind
    suspend fun isAvailable(context: Context): Boolean
    suspend fun apply(context: Context, matrix: LumenMatrix)
    suspend fun clear(context: Context)
}
```

`DriverProbe.bestAvailableKind()` resolves Auto in three steps, and the order is not the rank order:

1. Any available driver whose `EngineKind.requiresRoot` is true, in list order, which is `SURFACE_FLINGER` then `KCAL`.
2. Otherwise `COLOR_DISPLAY_MANAGER`, the secure-settings driver, if it's available.
3. Otherwise `OVERLAY`.

If none of the three is available it returns null, and the service and the Driver tab both report that no driver is available rather than silently selecting one that can't work. It doesn't fall back to `OVERLAY` for its own sake, because an overlay that can't be installed isn't a filter.

`EngineKind` still carries a `rank: Int`. The rank orders the Driver tab's list and breaks ties inside step 1, but step 2 doesn't key off it, which is why the ranks below don't read as the resolution order:

| Engine | Rank | Why |
|---|---:|---|
| `COLOR_DISPLAY_MANAGER` | 100 | System Night Light and Extra Dim through `Settings.Secure`. No root, no overlay, and the tint covers the whole screen. Ranks first because it is the best result a device without root can get. |
| `SURFACE_FLINGER` | 90 | Root-only framebuffer transform via `service call`. Universal across SoCs but needs `su`. Preferred over the row above when root is present, because it takes the whole matrix rather than a colour temperature. |
| `KCAL` | 70 | Panel-driver write. Best dim quality on Snapdragon kernels that expose it, but device-specific. |
| `OVERLAY` | 10 | Universal rootless fallback. Capped at ~80% opacity by Android 12 untrusted-touch rules. |

A pinned driver is honoured over all of this, and a pin whose probe reports unavailable falls back to the Auto order above unless the user has also turned on the force-pin switch, which exists because `su` detection is unreliable on root-hiding setups.

### Engine implementations

- **`SecureSettingsEngine`** calls the public `Settings.Secure.putInt` against the `night_display_*` and `reduce_bright_colors_*` rows `ColorDisplayService` observes. It replaced a `ColorDisplayManager` reflection engine that couldn't work on a user install, because those setters need `CONTROL_DISPLAY_COLOR_TRANSFORMS` and `pm grant` can't grant it. It's cached after the first successful load and gated on `WRITE_SECURE_SETTINGS` being granted. While it holds a row, `SecureSettingsWatch` keeps a `ContentObserver` on it. When a value OpenLumen didn't write turns up, the row is released, the new value is logged, the filter turns off and the Driver screen shows a notice. That way neither the next ramp step nor the next clear writes over a setting the user chose.
- **`SurfaceFlingerEngine`** runs `service call SurfaceFlinger <code>` via `su`. The colour matrix is transaction 1015, unchanged from `android-10.0.0_r1` through `android-16.0.0_r1`. The probe issues the disable transaction and reads the reply parcel, because `service call` exits 0 whether or not the service accepted it.
- **`KcalEngine`** writes RGB values and an enable flag to `/sys/devices/platform/kcal_ctrl.0/*` sysfs nodes via `su`. It needs a Qualcomm device with a kernel that exposes KCAL.
- **`OverlayEngine`** is a `TYPE_APPLICATION_OVERLAY` window with a tinted full-screen `View`. Touches pass through because of `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`. Alpha comes from both dim and tint strength, so color-only presets are visible at `dim=0`.

### Adding a new engine

1. Add an enum entry to `EngineKind` with a sensible `rank`.
2. Implement `ColorEngine` in `core-engine/src/main/java/com/openlumen/engine/engines/`.
3. Register it in `DriverProbe.Companion.defaultEngines()`.
4. Add an `EngineKindDto` enum entry in `core-prefs/.../Preferences.kt`.
5. Wire the new DTO value through `EngineController.ensureEngineFor()` and `DriverScreen.toEngineKind()`.
6. Add at least a "doesn't throw on unsupported hardware" unit test.

## Schedule abstraction

`core-schedule/src/main/java/com/openlumen/schedule/Schedule.kt` defines a sealed `ScheduleMode`:

- `AlwaysOn` keeps the filter on permanently, with no transitions.
- `AlwaysOff` keeps the filter off, with no transitions. This is the "disabled" form of the schedule, which isn't the same as the global enable flag.
- `FixedTime(start, end)` is a local-time window.
- `Solar(lat, lng, sunsetOffsetMin, sunriseOffsetMin)` uses NOAA-computed dusk and dawn boundaries.

Two pure functions are the public surface:

- `isActive(mode, now, zone): Boolean` answers whether the filter is supposed to be on right now.
- `nextTransition(mode, now, zone): ZonedDateTime?` says when the next state flip is. It returns `null` for `AlwaysOn` / `AlwaysOff` / degenerate fixed windows.

`LumenService` uses `isActive` for the current apply decision. `ScheduleAlarmOrchestrator` uses `nextTransition` to set the next `AlarmManager` alarm.

## Persistence

`PreferencesStore` is a thin wrapper around DataStore's `preferencesDataStore`, backed by a **single string key** holding a JSON-serialized `Preferences` object. `DirectBootStateStore` is a separate typed DataStore in device-protected storage. It isn't a second source of truth, just a boot-time cache derived from service emissions. Why a single key:

- One read, one decode per emission. No N+1 across dozens of typed keys.
- Profile export/import is "dump the string, restore the string."
- Schema evolution is centralized in one place (see C29 on the roadmap).

The store always runs values through `sanitize()` on read **and** write so NaN / Inf / out-of-range values from a corrupted import never reach the GPU path or the AlarmManager.

## UI structure

- `MainActivity` is the single Compose `Activity`. It's edge-to-edge and requests POST_NOTIFICATIONS on API 33+ at first launch.
- `OpenLumenRoot.kt` is the bottom-nav scaffold with 5 destinations.
- `screens/HomeScreen.kt` (main toggle, intensity, dim, RGB picker)
- `screens/ScheduleScreen.kt` (mode radio plus fixed/solar inputs)
- `screens/PresetsScreen.kt` (preset list with color chips)
- `screens/DriverScreen.kt` (engine picker with availability badges)
- `screens/AboutScreen.kt` (version, license, backup, crash log)
- `viewmodel/OpenLumenViewModel.kt` is the single `AndroidViewModel`. It holds the `state: StateFlow<Preferences>` and exposes mutation functions that all funnel into `prefs.update`.

## What the service does NOT do

- **No background polling.** The old 60-second ticker was replaced with `AlarmManager` + a `BroadcastReceiver` in v0.3.0. The light sensor uses `SensorManager.registerListener` (event-driven, also no polling).
- **No background location.** The only location permission is `ACCESS_COARSE_LOCATION`, and it's used while the app is open. That's when the user taps "Use my location", and on app open if they turned on automatic location for the solar schedule (`AutoLocationRefresher`, at most every 15 minutes). `DeviceLocation` asks the platform `LocationManager` for a cached fix under 30 minutes old, else one `LocationManagerCompat.getCurrentLocation` call with a 30 second timeout. FusedLocationProvider would pull in Play Services and break F-Droid. Fine and background location are stripped from the merged manifest. A time zone change while the app is closed only posts a notification (`ScheduleClockChangeReceiver`). It never reads location.
- **No network.** The manifest deliberately omits `android.permission.INTERNET` and CI enforces it.
- **No accessibility service.** It was considered (see C79) but not implemented, since it's too sensitive a surface for the current threat model.

## Gotchas worth knowing before you touch this

- **Reflection drift.** This no longer applies, because the reflection engine it described was removed. It stays here so a reader of an older build knows why. The former text follows.

  `ColorDisplayManagerEngine` reflected against a hidden AOSP API. Future Android versions can rename methods, and the `runCatching` ladder is defensive on purpose.
- **A rejected `service call SurfaceFlinger` transaction.** The colour matrix is 1015 and hasn't moved. Earlier releases also probed 1023, 1030 and 1036. None was ever verified against AOSP, and 1023 is the colour mode. Add a code to `SurfaceFlingerEngine.CANDIDATE_CODES` only with a citation to the `onTransact` case that implements it.
- **Overlay window token.** `TYPE_APPLICATION_OVERLAY` needs a Service or Activity context, and an `Application` context throws. `EngineController` calls `overlay.installView(serviceContext, …)` before the first apply.
- **Android 14 `specialUse` FGS property.** The manifest must declare `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` or `startForeground()` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` throws.
- **Android 12+ overlay alpha cap.** With `FLAG_NOT_TOUCHABLE`, overlay alpha is capped at ~0.8 (≤204 in 8-bit ARGB). Hard dim past that needs a root driver.
