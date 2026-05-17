# Changelog

All notable changes to OpenLumen are documented here.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Service smooth-ramp scheduling now has a dedicated ramp mutex and cancels /
  joins in-flight ramps before engine switch or filter-off clear, preventing
  stale transition steps from applying over the latest target.
- ColorDisplayManager, SurfaceFlinger, and KCAL driver caches now invalidate on
  partial reflection failures or failed driver writes so the next probe can
  recover after transient API / OTA / sysfs drift.
- Overlay engine view installation, tint updates, and removal are now serialized
  on the main thread to avoid rapid-toggle races during engine swaps.
- Profile import size validation now caps raw UTF-8 bytes before decoding,
  so multi-byte payloads cannot bypass the intended 64 KiB limit.
- Quick Settings and widget toggle-on paths now classify Android background
  foreground-service start rejections, roll back stale enabled state, and open
  the app when Android 15+ requires a visible overlay before starting.
- Engine switches now reset the service target cache so SurfaceFlinger, KCAL,
  and other engines receive the first matrix emission even when the user did not
  change preset, intensity, or dim values.
- Default preferences now serialize with nullable solar coordinates instead of `NaN`,
  so profile export/import and DataStore writes remain valid JSON.
- Rootless overlay tinting now uses non-zero alpha for color-only presets; previously
  overlay mode was effectively invisible unless the Dim slider was above zero.
- Schedule alarms no longer reschedule into the past when a transition calculation
  returns a stale boundary.
- Until-next-alarm schedules no longer activate before the configured start time
  when the next alarm belongs to the upcoming overnight window.
- Driver availability on the Driver screen now maps DTO names to engine kinds
  correctly instead of silently hiding availability status.
- Kelvin unit tests now avoid JUnit display-name characters that break Kotlin
  test compilation on this toolchain.

### Changed
- Launcher and store artwork now use the final minimal OpenLumen crescent
  mark, with a source SVG under `branding/` and the F-Droid 512x512 icon
  under `fastlane/metadata/android/en-US/images/`.
- Build tooling now uses AGP 9.2.1, Gradle 9.4.1, Kotlin 2.3.21, and
  KSP 2.3.8 with AGP 9's built-in Kotlin support instead of applying the
  separate `org.jetbrains.kotlin.android` plugin.
- Hilt now uses Dagger/Hilt 2.59.2, and Compose `hiltViewModel()` imports
  now come from `androidx.hilt:hilt-lifecycle-viewmodel-compose` rather
  than `hilt-navigation-compose`.
- Release builds now disable AGP's packaged VCS-info metadata
  (`META-INF/version-control-info.textproto`) and document the F-Droid
  reproducibility rationale in `docs/reproducible-build.md`.
- Android 17 readiness docs now record the C111 BAL audit result: there
  are no `IntentSender` / `ActivityOptions` call sites to migrate today.
- Overlay/per-app design notes now explicitly call out Android 17 Advanced
  Protection Mode as another reason not to use AccessibilityService for
  foreground-app convenience features.
- Troubleshooting now documents that a filter paused before reboot remains
  paused after reboot, matching `BootReceiver`'s persisted `enabled` gate.
- Wake/vitals and device-matrix docs now include Android 14-17 boot-restore
  evidence slots for C106 without fabricating pass/fail device rows.
- Driver reports now include an Android 17 Advanced Protection section with
  `enabled`, `disabled`, `n/a`, or bounded `unknown` status, and the app now
  declares `QUERY_ADVANCED_PROTECTION_MODE` for that query path.
- Compose UI no longer depends on deprecated `material-icons-extended`;
  the small nav/favorite icon set is now self-hosted as vector resources.
- GitHub Actions workflows now use current Node-24-capable major tags:
  `checkout@v6`, `setup-java@v5`, `setup-gradle@v6`,
  `upload-artifact@v7`, `actions/attest@v4`, and
  `anchore/scan-action@v7`.
- Android 17 release planning now includes concrete MemoryLimiter /
  `ApplicationExitInfo` and sw600dp/foldable/windowing smoke steps in
  the device validation matrix.
- Removed unused location and `USE_EXACT_ALARM` permissions; added the requested
  `WRITE_SECURE_SETTINGS` declaration so the documented ADB grant can succeed.
- The foreground service subscribes to the light sensor only while the filter and
  ambient-light trigger are both enabled.
- Preset and driver cards are whole-card clickable for consistency with schedule cards.
- Remaining Compose screen and dialog copy now routes through Android string
  resources; preset labels are localized through an app-layer helper used by
  Compose, widgets, and the Quick Settings tile.
- Backup rules now include DataStore preferences while leaving the local crash log
  outside the included backup paths.

### Added
- Compose Preview Screenshot Testing is wired into Gradle and CI with an
  initial textless theme-token fixture plus checked-in debug reference
  images.
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
- Release workflow now generates an `actions/attest` provenance record for each
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
- 1x1 home-screen toggle widget. Tap to toggle (same `ACTION_TOGGLE` path
  the QS tile uses); label below the icon reads On / Off. Stays in sync
  with the in-app toggle via a `ToggleWidget.broadcastRefresh()` nudge
  that the service fires on every prefs emission. The receiver is
  no-op when no widgets are installed.
- 4x1 home-screen preset widget. Renders the first four entries of
  `favoritePresetKeys` as tappable color chips. Tap a chip to
  `SET_PRESET` (immediate, no app launch). If fewer than four favorites
  are marked, unused slots are hidden and a center hint reminds the user
  to mark favorites in the Presets tab. Refreshes via the same
  prefs-emission broadcast pattern as the 1x1 widget but on a separate
  `PRESET_REFRESH` action namespace.
- Accessibility baseline pass: ambient-light, solar-offset, RGB, gamma,
  Kelvin, intensity, dim, and contrast sliders expose TalkBack state
  descriptions.
- Smooth transition engine. New `Preferences.transitionDurationMs` (0
  default; clamped 0..30 min). When non-zero, the foreground service
  interpolates from the last-applied matrix toward the new target over
  the duration on schedule-driven state flips, applying at ~1 Hz with a
  200 ms floor and a 600-step cap. User-driven changes (sliders, preset
  taps) remain instant so the UI never feels laggy. Ramps cancel cleanly
  on the next state change or service shutdown. New radio picker in the
  Schedule tab: Instant / 30 s / 5 min / 15 min / 30 min.
- `LumenMatrix.lerp(target, t)` linearly interpolates all ten fields and
  clamps `t` into 0..1. Unit-tested against the boundary cases (t=0,
  t=1, t=0.5, out-of-range t).
- Previous-preset restore. `Preferences.previousPresetKey` is recorded on
  every preset change; `PresetCycle.restorePrevious(current)` flips back
  and stamps the now-displaced key as the new previous so a double-undo
  round-trips. Surfaced as a Restore affordance at the top of the
  Presets screen when relevant, and as a `RESTORE_PREVIOUS` intent on
  the service for Tasker / ADB users.
- Public-facing compatibility table at `docs/compatibility-table.md`
  summarizing engine support by SoC family, OEM / ROM, and Android
  version. Distinct from the per-test record in
  `docs/device-matrix.md` — that's the testing record, this is the
  user-facing summary.
- Play Store `specialUse` foreground-service evidence pack at
  `docs/play-fgs-evidence.md`: the reasoning, the narrative we'd submit
  to a Play reviewer, and the not-in-Git list of artifacts we'd
  collect if we ever pursue a Play listing. F-Droid remains primary;
  this document lets a maintainer recreate the evidence pack from
  primary sources without re-deriving the rationale.
- SBOM CI workflow at `.github/workflows/sbom.yml`. Generates an
  SPDX-JSON SBOM of the release classpath and runs an Anchore
  advisory scan on every release and weekly Monday 06:00 UTC. Both
  artifacts upload with a 30-day retention. Workflow does not fail
  builds on findings — triage policy in `docs/sbom-and-advisories.md`
  with an "Accepted exposures" register for future entries.
- Gradle dependency-verification procedure at
  `docs/dependency-verification.md`. Documents the regeneration
  workflow, failure modes, and the explicit decision to defer
  enforcement until after the AGP 9 migration spike so the lockfile
  doesn't trample every Dependabot PR.
- Wake / alarm / battery audit at `docs/wake-and-vitals.md`. Inventory
  of what wakes the device (only the schedule alarm and boot
  completion) and what doesn't (light sensor, preference changes, UI
  surface taps, smooth-transition ramp). Includes `adb` commands for
  independent verification.
- Android 16 / API 36 readiness inventory at
  `docs/android-17-readiness.md` (renamed from `docs/api-36-readiness.md`
  in rev 4 of the roadmap). Lists already-handled behavior changes
  and expected upcoming ones with OpenLumen exposure ratings and
  mitigations. Includes a smoke-test plan for the first preview build
  and a migration policy (target-SDK bumps get their own release).
- Schedule screen now surfaces the device timezone label so users know
  which clock fixed-time schedules fire against (e.g.
  `America/New_York`). Prevents the "I set 22:00 but it fires weird"
  support thread after travel.
- `SurfaceFlingerEngine` now picks transaction codes from a per-API
  candidate ladder: `1015 → 1023 → 1030 → 1036` depending on which
  Android version is running. The first code that succeeds for the
  identity matrix is cached and exposed as `activeTransactionCode` so
  the driver report captures exactly which code is in use. Per-API
  list grows-or-stays as Android advances — covered by new unit tests.
- `KcalEngine` now probes a list of known KCAL sysfs roots
  (`/sys/devices/platform/kcal_ctrl.0/`,
  `/sys/module/msm_drm/parameters/`,
  `/sys/class/misc/kcal/`) instead of hardcoding the most-common one.
  The winning base path is exposed as `activeBasePath` and recorded
  in the driver report.
- AMOLED true-black clamp (C66). New opt-in
  `Preferences.amoledBlackClamp` flag plus a matching
  `LumenMatrix.amoledClamp` field. When enabled, `scaledRgb()` snaps
  any channel scalar below `AMOLED_CLAMP_THRESHOLD = 0.02` to zero,
  which on OLED panels turns the matching subpixels fully off in the
  warm/dim end of the tinting range. No-op on LCD. Surfaced as a
  switch on the Home tab. Unit-tested for off-passthrough, on-snap,
  above-threshold preservation, and dim-driven snap.
- Blue-channel reduction indicator on the Home tab (C61). New
  `MatrixPreview.blueSuppression(prefs)` computes `1 - effective_blue`
  from the same matrix path the engine receives, so the indicator
  honors intensity, dim, contrast, gamma, and AMOLED clamp. Phrased
  as a physical measurement ("Blue channel reduced by N%"), not a
  health metric — see `docs/health-evidence.md` for what we will and
  will not claim.
- New `MatrixPreview` utility extracts the
  preference-to-matrix transform out of `LumenService.matrixFor()`
  so the service and UI compute identical effective matrices. The
  service now delegates to `MatrixPreview.matrixFor(prefs)`; future
  preview surfaces (color swatches, melanopic estimates) call the
  same function.
- New schedule mode "Until my next alarm" (C25). On from the
  configured start time until the user's next system alarm clock
  fires. `LumenService.mapMode()` reads `AlarmManager.getNextAlarmClock()`
  at schedule-evaluation time; the pure schedule logic in
  `core-schedule/Schedule.kt` receives the next-alarm time as a
  parameter so it stays Android-framework-free. When no alarm clock
  is set, the mode falls back to a 12-hour window from start so the
  filter doesn't run indefinitely.
- Contrast slider on the Home tab (C64). New `Preferences.contrast`
  (range 0.5..2.0, default 1.0). Applied in
  `LumenService.matrixFor()` as a per-channel scale plus a centering
  bias on the matrix's bias fields — keeps mid-gray fixed while
  expanding or compressing the response range. Bias only takes effect
  on the SurfaceFlinger engine (which consumes the matrix's 4th row);
  the other engines still get the contrast-scaled channel values, an
  acceptable degradation.
- Kelvin color-temperature slider on the Home tab. Internally maps
  to RGB via the Tanner Helland approximation
  (`core-engine/Kelvin.kt`) and writes through `setCustomKelvin` so the
  canonical persisted state stays the RGB triplet on `customMatrix`.
  Range 1000–10 000 K, default 3200 K. Unit-tested for neutral-white
  near 6500 K, warm = red-saturated, cool = blue-saturated, and
  bounds clamping.
- LumenService now registers a runtime receiver for
  `ACTION_SCREEN_OFF` and invalidates the cached lux reading on each
  fire. Implicit-broadcast exempt from Android 8+ background limits;
  manifest-registered receivers don't get screen-off on modern
  Android, so the runtime registration is required. The OS already
  pauses the sensor when the screen is off; this change makes sure
  the next `applyIfShouldBeActive` doesn't act on a stale daytime
  reading. (C99)
- New `docs/overlay-and-per-app-design.md`: durable analysis of the
  C10 / C11 / C12 / C28 / C69 / C90 / C95 / C96 design space. The
  shared blocker for the per-app candidates (C11 / C12 / C69) is
  foreground-app detection, which would require
  `PACKAGE_USAGE_STATS`, an AccessibilityService, or a Shizuku
  backend — all three of which change OpenLumen's trust posture. The
  doc records the decision to defer pending the Shizuku spike (C06)
  and captures the implementation plans for C28, C90, C95, and C96.
- Named profile library. `Preferences.savedProfiles` holds up to 32
  `NamedProfile`s; each is a `(name, ProfileSnapshot)` pair where the
  snapshot covers preset, custom RGB matrix, intensity, dim,
  schedule, engine, light-sensor settings, favorites, and transition
  duration. Saving captures the current configuration; loading
  applies it while preserving runtime state (enabled, schemaVersion,
  the saved-profile library itself, firstRunComplete) and stamping
  the previous active preset so C14 restore round-trips through
  profile loads. Pure transforms in `core-prefs/Profiles.kt` are
  unit-tested separately from the UI. About tab gets a Profiles card
  with Save / Load / Delete affordances.
- Offline city picker in the Location entry dialog. `OfflineCities` in
  `core-schedule` bundles ~95 major cities with IANA timezones and
  coordinates accurate to four decimal places. Search is
  case-insensitive substring on `"City, Country"`; `nearest(lat, lng)`
  returns the closest bundled city for a given coordinate. The picker
  fills the lat/lng fields but doesn't lock out manual entry. No
  network dependency, no Play Services dependency — all bundled.
- Local diagnostics log at `filesDir/diagnostics.log`. Bounded
  (~64 KB cap, trimmed to ~32 KB), append-only, grep-friendly text
  format `<instant> <LEVEL> <CATEGORY> <message>`. The
  foreground service writes lifecycle and schedule-reschedule events.
  Tail of the log is included in every driver report
  (last ~3 KB). About → "View diagnostics log" opens an in-app
  dialog with Clear; the log never leaves the device unless the user
  shares it manually. The app module now runs its own
  `testDebugUnitTest` in CI; format-level tests on `DiagnosticsLog`
  ride alongside.
- OWASP-MASVS-lite threat model at `docs/threat-model.md` covering storage,
  crypto, auth, network, platform-interaction, and code-quality risks with
  specific mitigations. Includes data and permission inventories and a
  review-cadence policy.
- Boot-panic reset: `BootReceiver` now suppresses auto-restore if the
  crash log was touched within 5 minutes before boot. Lets users escape
  a stuck-tint state by rebooting without OpenLumen putting them right
  back in it. The crash log itself stays in place; clearing it from
  About → View crash log restores normal auto-restore behavior.

### Tests
- Added coverage for finite color-matrix coercion, visible overlay alpha for tint-only
  presets, fixed schedules with identical start/end times, and default preference JSON
  serialization.

### Hardening (2026-05-17 in-tree audit pass)
Correctness fixes from the 2026-05-17 audit pass (see ROADMAP.md rev 3 / rev 4
"Hardening (post-rev-2 audit)"). On disk on `main`; ships in v0.5.0 or a v0.5.1
hardening cut.
- `Schedule.kt` Solar mode now honors the caller's `now` (was using
  `LocalDate.now(zoneId)`, which made the schedule logic non-pure).
- `SolarCalculator.kt` returns a `Polar` enum so polar-day and polar-night
  are distinguishable. Sunrise/sunset `ZonedDateTime`s are snapped to the
  requested local date so Western-hemisphere sunsets no longer land on
  the previous day.
- `LumenService` mid-ramp interruption now lerps from the actually-
  displayed matrix rather than the previous target. `lastTarget` is now
  separate from `lastApplied`; cancel-and-join replaces bare cancel;
  engine switches reset both fields.
- `PreferencesStore` sanitizes nested profile-snapshot matrices, schedule
  fields, lux thresholds, intensity, dim, contrast, transition, favorites,
  and preset keys. `previousPresetKey` is sanitized.
- `LightSensorAdapter` buffers with `DROP_OLDEST` so sensor callbacks
  cannot lose readings to backpressure; rejects non-finite / negative raw
  samples.
- `OverlayEngine` adds `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (API 28+)
  and posts `installView` to the main thread when called off-Main.
- `KcalEngine` probes `kcal_min` separately and only writes to it when
  present.
- `Su.runShell` drains stdout on a daemon thread to avoid script-output
  deadlocks.
- `LumenService.observePreferences` wraps each emission in try/catch
  (re-throws `CancellationException`) with diagnostic logging.
- `LumenService.ACTION_SET_PRESET` validates the key against
  `Presets.byKey(...)` (plus `"custom"`).
- `LumenTileService.refreshTile` wraps `updateTile()` in try/catch.
- `OpenLumenViewModel.refreshProbes` invalidates `Su.cachedAvailable`.
- `AboutScreen.describeDiff` now surfaces changes to contrast,
  AMOLED clamp, lux threshold, and sunset/sunrise offsets.
- Regression tests added for Solar caller-`now`, polar-day vs polar-night,
  NYC sunset date-stamping, and Tokyo timezone behavior.

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
