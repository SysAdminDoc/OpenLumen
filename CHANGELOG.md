# Changelog

All notable changes to OpenLumen are documented here.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Default preferences now serialize with nullable solar coordinates instead of `NaN`,
  so profile export/import and DataStore writes remain valid JSON.
- Rootless overlay tinting now uses non-zero alpha for color-only presets; previously
  overlay mode was effectively invisible unless the Dim slider was above zero.
- Schedule alarms no longer reschedule into the past when a transition calculation
  returns a stale boundary.
- Driver availability on the Driver screen now maps DTO names to engine kinds
  correctly instead of silently hiding availability status.

### Changed
- Removed unused location and `USE_EXACT_ALARM` permissions; added the requested
  `WRITE_SECURE_SETTINGS` declaration so the documented ADB grant can succeed.
- The foreground service subscribes to the light sensor only while the filter and
  ambient-light trigger are both enabled.
- Preset and driver cards are whole-card clickable for consistency with schedule cards.
- Backup rules now include DataStore preferences and exclude the local crash log.

### Added
- `CONTRIBUTING.md`, `docs/ARCHITECTURE.md`, `docs/troubleshooting.md`,
  `docs/device-matrix.md`, `docs/release-checklist.md`,
  `docs/reproducible-build.md`, `docs/root-safety.md`,
  `docs/health-evidence.md`, and `docs/research-watchlist.md` for the v0.5.0
  trust-and-distribution pass.
- F-Droid metadata skeleton at `fastlane/metadata/android/en-US/`.
- GitHub issue templates (bug, driver report, overlay bug, feature request) and
  `dependabot.yml` for weekly Gradle and Actions updates.
- CI now runs `core-engine`, `core-schedule`, and `core-prefs` unit tests on
  every PR, and a `permissions-audit` job that fails the build if the merged
  manifest contains `INTERNET`, `ACCESS_NETWORK_STATE`, or `ACCESS_WIFI_STATE`,
  or if any Play Services / Firebase artifact reaches the release classpath.
- Release workflow now generates an `actions/attest-build-provenance` for each
  release APK.
- In-app driver report on the Driver tab: Copy and Share buttons produce a
  paste-friendly device summary (build, SoC, granted permissions, exact-alarm
  state, every engine's probe result, and the user's current configuration).
  The report intentionally redacts solar coordinates and contains no PII.
- Driver screen now shows `WRITE_SECURE_SETTINGS` grant state and a per-package
  copyable adb command (debug builds get the `.debug`-suffixed variant).
- Overlay engine info card on the Driver tab explains the Android 12+ alpha
  cap and the untrusted-touch behavior on system installer / permission
  dialogs.
- About tab now exposes the emergency-off ADB command, copyable to clipboard,
  so users can stash it before something goes wrong.
- Quick Settings tile subtitle shows the active preset name when the filter
  is on (API 29+), and the tile's long-press destination now opens the app
  directly via the `PREFERENCES_ACTIVITY` manifest meta-data.
- Versioned preference schema: `Preferences.schemaVersion` (current = 1)
  plus a `PreferencesMigrations` runner that walks pre-C29 blobs (no
  `schemaVersion` key on disk) through to the current layout. Migrations
  are pure functions; sanitization runs after.
- Profile import preview: the About tab's Import button now shows a
  field-level diff (preset, engine, schedule mode + times, location,
  intensity, dim, light sensor, favorites) and waits for explicit
  confirmation before writing to DataStore.
- Favorite presets: `Preferences.favoritePresetKeys` with a star-toggle on
  every preset card. Defaults to Night/Amber/Red/Deep. Capped at 8 in
  sanitize. Used by the upcoming notification preset-cycle action (C16)
  and 4x1 widget (C20).
- Foreground notification gets a "Next preset" action that cycles through
  favorites (no-op when favorites is empty; visible regardless to avoid
  notification rebuilds on edit). The cycle logic lives in
  `core-prefs/PresetCycle` so it's unit-testable on the JVM.
- Documented automation surface: LumenService now accepts
  `TURN_ON` / `TOGGLE` / `CYCLE_PRESET` / `SET_PRESET` / `SET_INTENSITY` /
  `SET_DIM` in addition to the existing `TURN_OFF` and `REEVALUATE`.
  Full ADB / Tasker / Termux command reference at `docs/automation.md`.
  These action strings are part of the stable API; renaming requires a
  schema-version bump and a deprecation period.

### Tests
- Added coverage for finite color-matrix coercion, visible overlay alpha for tint-only
  presets, fixed schedules with identical start/end times, and default preference JSON
  serialization.

## [0.4.0] — 2026-05-16

Deep engineering audit pass. Every major file was reviewed for correctness,
race conditions, error handling, and UX polish; this release rolls up every
fix found.

### Concurrency and lifecycle
- `LumenService.applyMutex` (kotlinx.coroutines.sync.Mutex) now serializes every
  `ColorEngine.apply()` / `clear()` call. Previously concurrent invocations
  (prefs change + alarm fire + light-sensor flip) could spawn overlapping `su`
  subprocesses on the SurfaceFlinger / KCAL paths.
- Prefs flow is `.conflate()`d before collection. Dragging an RGB slider rapidly
  no longer queues dozens of engine applies — only the latest value is taken
  once the current apply releases the mutex.
- `engine`, `lastApplied`, and `lastShouldBeActive` are `@Volatile`. The alarm
  receiver and sensor callback observe these from different threads.
- `LumenService.onDestroy()` runs `engine.clear()` synchronously inside
  `runBlocking { withContext(NonCancellable) { withTimeoutOrNull(2s) {…} } }`.
  Previously we launched a coroutine *after* `lifecycleScope` was about to be
  cancelled, racing teardown with cleanup.
- `LumenTileService` now creates a fresh `CoroutineScope` on every `onCreate()`
  and cancels it on `onDestroy()`. The old service held a module-level scope
  that leaked across rebinds.
- Tile toggle uses `prefs.update { current -> current.copy(enabled = !current.enabled) }`
  — atomic with respect to the stored value, so rapid double-taps cannot land
  in an inconsistent state.

### `su` wrapper hardening (`core-engine/Su.kt`)
- Removed the double-invocation bug in `isAvailable()` (previously spawned
  `su -c id` twice; would prompt Magisk twice on first run).
- `redirectErrorStream(true)` on both `runCommand` and `runShell`. Eliminates
  the classic "pipe buffer full on the un-read stream" deadlock.
- `BufferedReader.use { }` on every stream — no FD leaks on timeout paths.
- `runShell` now has a 4-second wall-clock timeout matching `runCommand`. Old
  implementation could hang forever if `su` prompted interactively.
- `runShell` drains stdout to prevent script-output deadlock on KCAL writes.
- All failure paths log via android.util.Log under `OpenLumen/Su`.

### Engine fixes
- `OverlayEngine.installView()` checks `Settings.canDrawOverlays()` before
  calling `wm.addView()` and catches the exception path. Returns false on
  failure instead of crashing the service.
- `OverlayEngine.clear()` dropped a dead `else` branch that could only fire if
  `hostView != null && hostWm == null` — impossible by code flow.
- `ColorDisplayManagerEngine` tries the `(Context)` constructor first, falls
  back to no-arg. Previous code only tried no-arg, breaking on AOSP builds
  that require the Context overload.
- `ColorDisplayManagerEngine` caches the reflected `Method` handles and the
  manager instance — no more reflection on every apply().

### Boot reliability
- `BootReceiver` no longer registers for `LOCKED_BOOT_COMPLETED`. DataStore
  lives in user-protected storage, so listening for the locked-boot signal
  just deadlocked `prefs.flow.first()` until the system killed our
  `PendingResult`. Direct-boot support is deferred to v0.5+.
- `BootReceiver` wraps the whole body in try/finally and a 8-second timeout
  on the prefs read so a hung DataStore can never leak the PendingResult.

### UI / Compose / accessibility
- The Home tab's top toggle Card is now whole-card-clickable. Previous tap
  target was just the Switch thumb (~48dp wide on a ~340dp card).
- Intensity and Dim sliders now expose `Modifier.semantics { stateDescription = "N percent" }`
  so TalkBack reads "75 percent" instead of "0.75" or just "slider".
- Bottom-nav icons now carry `contentDescription = labelRes`. Was null, which
  would have read just "Home button" without context if a future label change
  broke the visible text rendering.
- `AlertDialog`-driven flags (`showStartPicker`, `showEndPicker`,
  `showLocationDialog`, `showCrashLog`) are now `rememberSaveable` so a rotation
  or process death survives the dialog state.
- `AboutScreen.LaunchedEffect(result)` no longer fires its body twice (once
  for the new value, once for the cleared null). Uses `return@LaunchedEffect`
  early-out.

### Defensive input handling
- `PreferencesStore.importFrom()` reads up to 64 KB, decodes, then **sanitizes**
  every numeric field (R/G/B/dim/gamma/lat/lng/offsets/hour/minute) into its
  valid range. Out-of-range latitudes become `NaN` (= AlwaysOff). Importing
  a malicious profile cannot crash the service.
- Import preserves the user's current `enabled` state — replacing settings
  must not silently toggle the filter on/off.
- `LumenService.mapMode()` clamps `startHour/startMinute/endHour/endMinute`
  before constructing `LocalTime`, so corrupted prefs never throw inside the
  foreground service.

### Diagnostics
- Added `core-engine/Log.kt` (`EngineLog`) — thin android.util.Log wrapper that
  enforces the 23-char tag length cap.
- Every catch/fallback path in the service + engines now logs under tags like
  `OpenLumen/LumenSvc`, `OpenLumen/Overlay`, `OpenLumen/Su`, `OpenLumen/CDM`,
  `OpenLumen/BootRecv`, `OpenLumen/Tile`.

### Tests
- Added `core-engine/src/test/java/.../LumenMatrixTest.kt` covering identity,
  dim coercion, gamma math, and SurfaceFlinger matrix layout.
- Added `core-schedule/src/test/java/.../SolarCalculatorTest.kt` cross-checking
  NOAA sunrise/sunset for New York, Sydney, Quito, Tromsø.
- Added `core-schedule/src/test/java/.../ScheduleTest.kt` covering AlwaysOn/Off,
  FixedTime midnight wrap, edge boundaries, and `nextTransition` correctness.
- JUnit 4 + Truth wired in via `gradle/libs.versions.toml`. Run with
  `./gradlew :core-engine:test :core-schedule:test`.

## [0.3.1] — 2026-05-16

### Fixed
- Material 3 `Button`/`OutlinedButton`/`TextButton` default to a fully-rounded
  pill (CircleShape). Replaced every call site with project-local
  `LumenButton`/`LumenOutlinedButton`/`LumenTextButton` wrappers in
  `ui/components/LumenButton.kt` that pin the shape to
  `MaterialTheme.shapes.medium` (10dp). No more pill backdrops in the UI.
- Signing config now explicitly enables v1 + v2 + v3 schemes (was v2-only).
  Improves install compatibility on Android 8.0 (API 26) devices and supports
  future key rotation via APK Signature Scheme v3.

## [0.3.0] — 2026-05-16

### Added
- `Schedule.nextTransition()` — pure function that returns the next moment the
  active state would flip for a given `ScheduleMode`. Returns null for
  `AlwaysOn`/`AlwaysOff`.
- `ScheduleAlarmReceiver` — fires the `ACTION_REEVALUATE` intent at the
  scheduled transition time, nudging the foreground service to re-apply.
- AlarmManager-driven schedule: `LumenService` now reschedules
  `setExactAndAllowWhileIdle` after every re-evaluation. Survives Doze; falls
  back to `setAndAllowWhileIdle` if `SCHEDULE_EXACT_ALARM` is denied or the OEM
  throws a SecurityException.
- Profile export / import via Storage Access Framework
  (`ActivityResultContracts.CreateDocument` + `OpenDocument`). Default filename
  uses today's date. JSON is pretty-printed.
- `CrashLogger` — local-only uncaught-exception handler that appends a
  timestamped stack trace to `filesDir/crash.log`. Auto-trims to ~32 KB once it
  exceeds 64 KB. About screen gains a "View crash log" dialog with Clear/Close.
- About screen is now scrollable; gains "Backup" and "Diagnostics" cards.

### Changed
- `LumenService` 60-second ticker has been removed. Schedule transitions are
  driven by the AlarmManager broadcast, light-sensor changes by the existing
  Flow collector. Net effect: zero background work between transitions.
- Manifest declares `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` permissions and
  the `ScheduleAlarmReceiver`.
- `PreferencesStore` Json now uses `prettyPrint = true` so exported files are
  human-readable.

### Privacy
- Crash log is **local-only** — the app still has no `INTERNET` permission. No
  upload, no telemetry. Users can share manually if they choose.

## [0.2.0] — 2026-05-16

### Added
- Custom RGB color picker on the Home screen with three labeled sliders
  (R / G / B), each with a colored swatch and a live numeric value, plus a
  combined preview swatch.
- Per-channel gamma sliders (γR / γG / γB, range 0.5–2.5). `LumenMatrix.scaledRgb()`
  now folds gamma into the math: `effective = pow(scale * (1 - dim), 1 / gamma)`.
- Intensity slider (0–100%) that lerps the active preset toward identity, so the
  user can fade the filter without re-selecting presets.
- Material 3 24-hour `TimePickerDialog` for fixed-time schedule's start/end.
- Manual decimal-degrees location entry dialog (no Play Services dep) with
  lat/lng range validation.
- Sunset and sunrise offset sliders (±180 minutes, 5-minute step) for the
  solar schedule mode.
- Ambient-light-sensor activation: switch + threshold slider (0–200 lux) +
  live lux readout + calibration button. Activation logic is now an OR
  between schedule-active and `lux < threshold`.
- `OverlayPermissionCard` on Home — when `SYSTEM_ALERT_WINDOW` is not granted,
  surfaces a rationale + button that opens `MANAGE_OVERLAY_PERMISSION` for the
  package. Self-hides once granted.
- Gradle 8.11.1 wrapper (jar + properties + `gradlew` + `gradlew.bat`) so the
  project builds without a system Gradle install.

### Changed
- `LumenService.matrixFor()` now always applies user gamma onto the chosen matrix
  (preset OR custom). Gamma is a global "tone" knob independent of preset.
- `ScheduleScreen` mode cards are now whole-card clickable (not just the radio
  button). Whole screen is vertically scrollable.

### Fixed
- `LumenService` broken `currentPrefs()` pattern that called `collectLatest`
  inside a suspend function and never returned. Replaced with an
  `AtomicReference<Preferences?>` written by the single long-lived collector;
  ticker reads the snapshot.
- Activation/decision logic no longer triggers spurious engine re-applies when
  the schedule state hasn't changed and the matrix is equal (proper `==`
  comparison on the data class).

## [0.1.0] — 2026-05-16

Initial scaffold release.

### Added
- Four `ColorEngine` implementations: `ColorDisplayManagerEngine`,
  `SurfaceFlingerEngine`, `KcalEngine`, `OverlayEngine`.
- Runtime `DriverProbe` that picks the highest-rank available engine, with a
  user override in Settings → Driver.
- 11 named presets (Night / Amber / Red / Salmon / Sepia / Grayscale / Deep Sleep /
  Protan / Deutan / Tritan / Off).
- NOAA solar-position calculator (hand-rolled, no external library) for
  sunset-to-sunrise scheduling.
- Fixed-time schedule mode with midnight wrap.
- Ambient-light sensor adapter with EMA smoothing.
- Foreground service with `specialUse` foregroundServiceType (Android 14+ compliant).
- Quick Settings tile for one-tap toggle.
- Boot receiver — restores filter on `BOOT_COMPLETED`.
- DataStore-backed preferences with JSON whole-blob serialization.
- Compose UI with five tabs (Home / Schedule / Presets / Driver / About).
- Catppuccin Mocha theme + AMOLED true-black surface.

### Privacy
- No `INTERNET` permission requested. App is fully offline.
- No analytics, no crash reporting, no telemetry.
