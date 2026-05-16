# Changelog

All notable changes to OpenLumen are documented here.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
