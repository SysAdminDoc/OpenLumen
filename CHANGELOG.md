# Changelog

## Unreleased

- Reject equal fixed-schedule start and end times in the editor and normalize imported or persisted equal-time schedules to the explicit Off mode.
- Preserve the selected offline city's IANA timezone for solar schedules, while keeping manually entered coordinates on the device timezone.
- Ambient-light activation now uses a visible hysteresis band, preventing lux readings near the threshold from repeatedly restarting filter transitions.
- Foreground notifications now refresh after schedule reevaluation and distinguish active filtering from standby while retaining the next-alarm countdown.
- Notification permission denials now distinguish retryable and permanent states, with an in-app Retry or notification-settings recovery action instead of a silent one-time failure.
- Kelvin control state now follows the current RGB and is explicitly disabled for named presets that are not editable temperature profiles, preventing stale-value jumps.
- Previous-preset restore now records only named presets; custom-to-named changes consistently omit the restore affordance instead of offering a key without its exact RGB snapshot.
- Explicit Off and AlwaysOff selections now remain stable standby states while the service is enabled; only the master-switch turn-on action restores an active schedule/preset.
- Settings and driver-report share actions now resolve external activities defensively and show localized recovery guidance when an OEM or managed profile cannot launch them.
- Location entry now uses an IME-aware bounded scroll region, keeping coordinate fields, city results, validation text, and dialog actions reachable on short viewports.
- Navigation-rail layouts now apply safe top/bottom insets, adapt rail sizing for large font scales, and expose complete destination labels through accessibility semantics.
- Auto driver explanations now use the same shared root → CDM → Overlay resolver as the service, including the no-driver state.
- Driver re-probing is now single-flight across the UI and service, preserves the last known result during refresh, and exposes a localized retry state when probing fails.
- Profile saves now require explicit confirmation before replacing a trimmed, case-insensitive name collision; canceling leaves the original snapshot untouched.
- Screenshot coverage now renders the production navigation root and real screens with deterministic state, including light/dark themes, phone/rail layouts, loading/error/empty states, and an import dialog.
- Adjustable sliders now expose localized control names alongside live value descriptions for TalkBack and keyboard users.
- Exact schedule alarms now target the foreground service directly, while inexact fallback alarms retain bounded blocked-start retries and cancel legacy alarm identities during reconciliation.
- Android 13+ per-app language settings now expose exactly the shipped English, German, Spanish, French, Japanese, and Portuguese locales, with automated string-key parity checks.
- Dependency-update review now reports official release-note endpoints, compatibility risks, required verification commands, checksum impact, unresolved metadata, and explicit intentional holds.
- KCAL emergency recovery documentation now uses the engine's valid 0–255 scalar range, with documentation lint rejecting out-of-range recovery examples.
- PROJECT_CONTEXT.md now reflects the v0.6.6 release metadata, preference schema 2, and nullable Auto driver resolution, with a local consistency gate for future drift.
- Sensor callback backpressure and diagnostics-log locking comments now match the live lossy-buffer and shared-lock implementations, with a concurrency regression test for reader locking.
- Tile and widget foreground-service starts now retain the requested enabled state through a blocked-start recovery handoff, retrying from the visible app after overlay permission is granted.

All notable changes to OpenLumen are documented here.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Solar scheduling now requires a valid location and clearly reports missing location data instead of silently behaving as Always Off.
- Profile import confirmation now applies the exact sanitized preview snapshot instead of rereading mutable external documents.
- Corrupt saved preferences now remain recoverable with bounded export/reset actions instead of being silently overwritten by defaults.
- Direct-Boot state deserialization now rejects oversized device-protected payloads before parsing, preventing an unbounded boot-time allocation.
- Exact-alarm permission grants and revocations now reconcile active schedules through the system broadcast and app-resume fallback without duplicate re-arms.
- Concurrent root availability checks now share one bounded `su id` probe per cache generation, while invalidation during a probe remains effective.
- Timed and solar schedules now re-evaluate after clock, date, timezone, and system next-alarm changes, including when the service process was not already running.
- Cloud backup now requires Android client-side encryption before copying the coordinate-bearing preferences blob; device transfer remains complete.
- Deep and PWM presets now retain their built-in dimming while composing it with the user's global dim control.
- Contrast now shares a documented white-endpoint projection across scalar drivers, while the Driver tab labels their black and midtone limitation.
- Persisted and imported preset references now resolve through the live catalog, so removed keys cannot reappear through cycling or restore actions.
- Ambient-light registration and collection now retry transient failures with bounded backoff and report a stable unavailable state after the retry budget.
- Widget and Quick Settings actions now have bounded preference operations, guaranteed broadcast completion, and retryable refresh behavior when storage is slow or unavailable.
- Solar offset and ambient-light threshold sliders now edit local drafts during a gesture and persist only the final value.
- Schedule alarm reconciliation now caches mode/permission/trigger signatures, avoids redundant AlarmManager work from lux samples, and queues diagnostic file writes off the service path.
- Screen-off now clears only ambient-light-owned tint and immediately reapplies schedule state; screen-on restarts sensor collection and re-evaluates without waiting for a sample.

### Security
- Hardened the exported automation receiver with caller-UID/package allowlisting
  and made the filter-state permission signature-protected; unknown local apps
  can no longer control the filter or observe its state broadcast.
- Release SBOM generation now records SPDX license and source provenance for every resolved dependency and fails unknown or prohibited metadata unless an exact reviewed override exists.
- Signed release validation now fails incomplete OSV responses, missing advisory severity, and unreviewed High/Critical findings; offline advisory mode is restricted to explicit unsigned checks.

### Fixed
- Root command execution now uses hard process-level deadlines and guaranteed
  stream/process cleanup instead of relying only on coroutine cancellation.

## [0.6.6] - 2026-08-03

### Changed
- Synchronized the maintenance-release metadata after the active roadmap
  reached an empty actionable tail; the generated manifest and About tab now
  report v0.6.6 consistently with the README and distribution metadata.

## [0.6.5] - 2026-07-05

### Added
- Expanded Compose Preview Screenshot Testing beyond theme-token fixtures with
  dark-mode tab-level baselines for Home, Schedule, Presets, Driver, and About,
  including the bottom navigation chrome and representative fixed preference
  snapshots.

## [0.6.4] - 2026-07-02

### Changed
- Applied the adaptive navigation scaffold padding to every screen, tightened
  top-level scroll clearance, raised shared button touch targets, and flattened
  widget backplates/swatches to the project radius scale.
- Upgraded dim fine-adjust controls, ADB command displays, warning-card tone,
  and destructive log-clear actions for clearer touch, recovery, and trust
  states.
- Added stronger selected/disabled emphasis to schedule and driver choices,
  framed saved-profile rows and empty states, and surfaced a localized
  no-results state in the offline city picker.
- Replaced pill-shaped framework navigation indicators and switches with
  project-shaped adaptive navigation and binary controls.
- Pinned every in-app dialog to the shared 12dp shape scale so backup,
  diagnostics, schedule, and location modals match the rest of the interface.

## [0.6.3] - 2026-07-02

### Changed
- Release builds now fail fast unless the full `OPENLUMEN_*` signing
  environment is present or `-Popenlumen.allowUnsignedRelease=true` is
  explicitly passed for local/F-Droid reproducibility checks.
- Added `tools/local_release_gate.py` as the workstation release gate for
  strict Gradle verification, lint/tests/screenshot lanes, no-network manifest
  checks, Google/Firebase classpath checks, SBOM/advisory output, SHA-256 sums,
  and APK signature verification.
- Added `tools/dependency_update_review.py` to compare the version catalog
  against stable Maven metadata from Google, Maven Central, and the Gradle
  Plugin Portal without requiring a Gradle Versions plugin.
- Added `tools/health_claim_lint.py` and release-gate integration to reject
  unsupported sleep, eye-strain, medical, and clinical-proof claims across app
  strings, Fastlane metadata, README copy, and local docs.

### Fixed
- KCAL writes now use the standard 0-255 scalar range, including clear and
  emergency-reset paths, so strict kernels no longer reject the prior 256
  value.
- Overlay fallback tinting now computes a complementary SRC_OVER color instead
  of painting scaled RGB directly, so rootless tinting suppresses blue like the
  root engines rather than adding a warm orange layer.
- SurfaceFlinger and KCAL cold probes now use coroutine mutexes so concurrent
  first calls coalesce instead of spawning duplicate `su` probes and duplicate
  Magisk prompts.
- `Su.runCommand` now accepts one explicit raw shell command string instead of
  varargs, making the quoting contract type-level and preventing future callers
  from assuming argv-safe escaping.
- ColorDisplayManager now derives its Night Light temperature by searching the
  existing Kelvin-to-RGB forward model across all RGB channels, so warm presets
  no longer map back to a neutral-ish 4900K range.
- Immediate turn-off now mirrors the disabled direct-boot state before writing
  credential-protected preferences, preventing a crash window where locked boot
  could restore a tint the user just turned off.
- Glance widgets now read preferences through the app singleton via a Hilt entry
  point instead of constructing a fresh `PreferencesStore` for every
  `provideGlance` invocation.
- Emergency display reset now attempts the ColorDisplayManager/Night Light path
  as well as SurfaceFlinger and KCAL, giving CDM users the same hard-reset
  recovery surface.
- Home sliders now use local draft state while dragging and persist on
  `onValueChangeFinished`, cutting DataStore write floods from dim, contrast,
  RGB, Kelvin, and gamma adjustments.
- AutomationReceiver rate-limiting state is now thread-safe: `HashMap` replaced
  with `ConcurrentHashMap` and `Long` counter with `AtomicLong`. Concurrent Binder
  thread delivery could corrupt the HashMap and spin-lock the app process.
- LocationEntryDialog city list uses a composite key (name + coordinates) instead of
  display name alone. Two cities sharing a name (e.g. Springfield) would crash with
  `IllegalArgumentException` from Compose's duplicate-key check.
- `LumenMatrix.withIntensity` now interpolates dim, bias, and gamma toward identity
  alongside the RGB scalars. Previously intensity at 0% still applied the preset's
  dim factor (e.g. Deep Sleep's 30% dim) even though the color channels were identity.
- Auto engine selection now falls back to CDM (ColorDisplayManager) when no root engine
  is available but CDM is, instead of dropping straight to Overlay. Users who granted
  `WRITE_SECURE_SETTINGS` now get framebuffer-quality output in Auto mode.
- `cachedAutoKind` in LumenService is now invalidated when `engine.apply()` fails,
  so a runtime engine failure triggers a fresh `pickBest()` on the next emission
  instead of silently repeating the failed engine.
- SurfaceFlingerEngine float-to-int conversion uses `Float.floatToRawIntBits()`
  directly instead of allocating a ByteBuffer per float (16 per apply call).
- `Su.runShell` now destroys the subprocess immediately on stdin IOException instead
  of falling through to `waitFor()` and blocking for up to 4 seconds.
- ColorDisplayManager reflected method handles are now `@Volatile`, preventing
  partial-publication races where one thread sees `cdm != null` but
  `setActivated == null`.
- Dark theme now defines `errorContainer` and `onErrorContainer` (Catppuccin Surface0
  / Red). The OverlayPermissionCard and any future error containers no longer fall
  back to Material's default brownish-red that clashes with the Mocha palette.
- Light sensor threshold slider and calibrate button are now disabled when the sensor
  feature is toggled off, preventing confusing interactions with no visible effect.
- Navigation bar icons now carry `contentDescription` matching the label text for
  TalkBack accessibility.
- Schedule screen timezone label no longer caches the zone ID in `remember {}`,
  so a timezone change mid-session is reflected immediately.
- BootReceiver now declares `android:directBootAware="true"` for consistency with
  the Application-level flag, preventing potential `BOOT_COMPLETED` delivery issues
  on FBE devices.
- README roadmap section updated from stale v0.5.0 references to v0.6.0.

### Added
- Local driver-report matrix helper: `tools/driver_report_matrix.py` drafts a
  review-only `docs/device-matrix.md` row from pasted driver reports or GitHub
  issue JSON, with confidence flags and no automatic pass/fail engine marks.
- Debug-only overlay viewport smoke coverage: `tools/overlay_viewport_smoke.ps1`
  captures active Overlay evidence across system bars, one-handed state,
  `FLAG_SECURE`, IME open/close, permission settings, and installer surfaces,
  with an issue-template field for preserving REVIEW lines.
- Fastlane/F-Droid metadata now ships localized title, short description,
  full description, and current changelog copy for Spanish, Portuguese,
  German, French, and Japanese, with metadata length checks covering all
  locales.
- Schedule now warns API 31+ users when Android denies exact alarms for
  timed schedule modes, links to the app's exact-alarm settings, and records
  degraded inexact scheduling in diagnostics.
- First translation pass: Spanish, Portuguese, German, French, and Japanese
  locales with 100% string coverage (216 strings each). Meets the F-Droid
  70% translation threshold for all 5 locales (C175).
- Sleep-countdown notification: when UntilNextAlarm schedule mode is active,
  the persistent notification shows "X h Y m until alarm" (C181).
- Outbound `com.openlumen.event.FILTER_STATE_CHANGED` broadcast with extras
  (`ENABLED`, `ACTIVE_PRESET_KEY`, `INTENSITY`, `DIM`). Tasker / Termux
  "Intent Received" can now react to filter state changes (C176).
- AutomationReceiver rate limiting: intents arriving within 200ms of the
  previous forwarded intent for the same action are dropped. Prevents
  external abuse from thrashing the display engine (C177).
- "PWM Comfort" preset: warm tint (~3800K) with 20% overlay dim, designed
  for OLED users who keep backlight high to avoid PWM flicker (C179).

### Changed
- WorkManager is now pinned to 2.11.2 while preserving the lazy-init
  `Configuration.Provider` setup that avoids the Android 10 direct-boot
  startup crash.
- LumenService subsystem split (C195): the foreground service now delegates
  engine selection/ramping, schedule alarms, light-sensor collection, widget
  refresh diffing, and Direct Boot mirroring to focused collaborators, with
  new JVM coverage for widget diffing and Direct Boot matrix round-tripping.
- Screenshot-test and Roborazzi light-theme references now match the current
  Catppuccin Latte light theme tokens, restoring both visual verification
  lanes to green.
- Backup/restore validation: removed dead `database` include from backup
  rules (no database in app), documented backup scope in XML comments, and
  added restore behavior section to `docs/troubleshooting.md`. Crash logs
  and diagnostics logs are excluded per privacy policy via the include-only
  allowlist model (C184).
- Presets screen uses `ListDetailPaneScaffold` for tablet layout: on
  expanded windows (sw >= 600dp) the preset list and a detail pane with
  color swatch, RGB channel bars, dim level, and CVD indicator render
  side-by-side; on phones the existing single-column layout is preserved
  with navigable detail view on tap (C186).
- Adaptive navigation: replaced `NavigationBar` + `Scaffold` with
  `NavigationSuiteScaffold`. App now renders bottom nav on phones and
  navigation rail on tablets/foldables/Chromebooks automatically (C180).
- Notification permission prompt deferred from app launch to first filter
  enable. Fresh installs no longer see a permission dialog before the user
  has interacted with the filter (C182).
- R8 full mode enabled (`android.enableR8.fullMode=true`); Hilt ProGuard
  rules narrowed from keep-all to generated-components-only (C183).
- Predictive back gesture support: added `enableOnBackInvokedCallback` manifest
  opt-in so Android 14+ shows correct back-preview animations during in-app
  navigation and dialog dismissal (C171).
- Migrated `androidx.core:core-ktx` → `androidx.core:core` 1.19.0 with
  compileSdk bumped to 37. The `core-ktx` artifact is now an empty redirect;
  all Kotlin extensions live in the main `core` artifact (C172).
- AGP 10 readiness audit: confirmed zero deprecated Variant API usage, no
  `enableLegacyVariantApi` flag, and no legacy Gradle properties. Build scripts
  are AGP 10-safe (C174).
- Deep-audit hardening pass (C187-C191):
  - AutomationReceiver guarded with `com.openlumen.permission.AUTOMATION`
    (normal protection level) so only apps that declare the permission can send
    automation intents. Filter-state broadcast also scoped with the same
    permission. ADB and Tasker still work; random apps can no longer toggle
    the filter unprompted (C187).
  - Coordinate display on ScheduleScreen locked to `Locale.ROOT` so French/German
    locales no longer render ambiguous `52,520, 13,405` (C188).
  - LazyColumn `items` calls in PresetsScreen and LocationEntryDialog now pass
    stable `key` lambdas for correct animation and recomposition (C189).
  - AMOLED true-black Switch, light-sensor Switch, and color preview Box now
    carry TalkBack `contentDescription` labels (C190).
  - `LocalLifecycleOwner` import in OverlayPermissionCard migrated from
    deprecated `androidx.compose.ui.platform` to `androidx.lifecycle.compose`
    (C191).
- Migrated all `collectAsState()` calls to `collectAsStateWithLifecycle()`
  across all 5 screens (Home, Schedule, Presets, Driver, About). Flow
  collection now pauses below STARTED, reducing unnecessary recompositions
  and DataStore reads while the UI is in the background (C196).
- AutomationReceiver now forwards only the two documented extras
  (`PRESET_KEY`, `VALUE`) to the service instead of copying the entire inbound
  bundle via `replaceExtras`. The receiver is exported, so the inbound extras
  are untrusted; this bounds the forwarded surface to what the service actually
  consumes (it still validates the values) and avoids relaying arbitrary or
  oversized bundles from a hostile local caller.

### Fixed
- Profile deletion now shows an undo snackbar and restores the deleted profile
  snapshot through the same pure `core-prefs` transform layer used by save/load.
- The exported automation permission label and description are translated in
  all supported locales, and the app name is explicitly non-translatable.
- Portuguese copy now uses proper diacritics across visible strings that lint
  flagged as misspellings.
- Widget picker preview labels now use 11sp centered, ellipsized text; provider
  XML keeps API-31 launcher metadata with lint annotations.
- Diagnostics and city-pick chips use the project shape system instead of the
  default rounded chip shape.
- Overlay permission, notification-channel, and foreground-service start paths
  no longer carry obsolete pre-Marshmallow branches; the app's minSdk is 26.
- Import-preview numeric diffs format with `Locale.ROOT`, avoiding comma-decimal
  drift in non-English locales.
- Light theme is now fully defined. Previously only primary/secondary/tertiary
  were set, leaving every other role on the Material baseline (pastel-purple
  primary with white text, low-contrast secondary text). Added the official
  Catppuccin Latte palette and a complete `lightColorScheme` tuned for WCAG-AA
  contrast, so the app reads as one design system in both light and dark.
- Channel-indicator colors (R/G/B slider tracks and preset channel bars) are
  now a single themed source instead of three different hardcoded hex values
  scattered across HomeScreen and PresetsScreen; they also adapt per theme.
- Backup/restore status messages ("Exported", "Imported", "Export/Import
  failed: …") were hardcoded English; they now use string resources and are
  translated into all 5 supported locales. Failure text also falls back to the
  exception type or a localized "Unknown error" instead of rendering "null".
- Location entry dialog now shows an inline range message ("Enter a value
  between -90 and 90") under a latitude/longitude field when the value is out
  of range, instead of only a silent red error highlight.
- Radio-style selectors (schedule mode, transition duration, driver, preset)
  now use `Modifier.selectable(role = RadioButton)` so TalkBack announces the
  control role and selected/not-selected state as a single node, rather than a
  generic "double-tap to activate" row plus a separate unlabeled radio.
- Solar polar-state detection now classifies the exact geographic poles
  (latitude ±90°) correctly. The zero-denominator path previously returned
  `Polar.NONE` at ±90°; it now resolves to `DAY`/`NIGHT` from the limit of the
  hour-angle equation, and `compute()` guards against a `NaN` hour angle (C192).
- Direct Boot tint mirror now clamps r/g/b channels to `0..1` (matching the
  main preferences store) instead of the looser `0..2`, and gamma to `0.1..5`.
  A corrupt or drifted mirror payload can no longer restore an out-of-range
  channel that the engine and main store would never agree on (C193).
- The master filter switch now repairs inert saved states when turning on:
  `AlwaysOff` schedules become `AlwaysOn`, and the `Off` preset restores the
  previous visible preset or falls back to `Night`. This prevents an installed
  app from showing "Filter is on" while every control appears to do nothing.
- Pinned display drivers that are no longer available now fall back to `Auto`
  instead of silently no-oping. The Driver tab also prevents selecting engines
  whose current probe result is "Not available".
- Auto mode now detects root and prefers the best available root backend
  (`SurfaceFlinger`, then `KCAL`). Non-root devices fall back to Overlay.
- Emergency-off automation now goes through an exported broadcast receiver and
  hard-clears known SurfaceFlinger transaction codes plus KCAL sysfs paths,
  so ADB recovery works even when a fresh service process has no cached engine.
- SurfaceFlinger writes now use the required enable flag before the 16 matrix
  slots, and every off/recovery path sends the real disable transaction
  (`i32 0`) instead of trying to clear by re-applying identity. This fixes the
  blue-screen/stuck-transform failure on rooted devices.
- Preference schema v2 resets upgraded installs that were pinned to
  `SurfaceFlinger` or `KCAL` back to `Auto` once, letting current root
  detection choose the right default instead of preserving stale driver state.
- The `Off` preset is treated as a true identity matrix in preview metrics, so
  Home no longer reports blue or brightness reduction while the active preset
  is Off.
- Fixed static percent strings rendering as `%%` in Home, Driver, and About.

## [0.5.1] — 2026-05-21

Deep-audit hardening pass. No new user-facing features; everything below
is correctness, reliability, performance, or UX polish surfaced by a
principal-engineer-grade review of the v0.5.0 codebase.

### Fixed
- Root engines no longer get stuck "available but silently no-op" after the
  user revokes Magisk root mid-session. `SurfaceFlingerEngine` and
  `KcalEngine` now invalidate the process-wide `Su` availability cache when
  their write fails with the exit codes that indicate `su` itself is gone
  (`127` = not on PATH, `-1` = forcibly destroyed on timeout). Other engine-
  local failures (a single failed write, a permission-denied on a sysfs
  node) still invalidate only the engine's own working state, not su-wide.
- KCAL panels on kernel forks that don't expose `kcal_min` now get an
  app-level safety floor on the per-channel RGB scalars (the same `SAFETY_MIN`
  used by the C166 raise-and-restore path). Without it, an aggressive
  preset could drive a subpixel to zero on those panels and produce
  flicker / a black-frame artifact at the channel boundary. AMOLED-clamp
  users opting into true zero are unaffected — they keep the through-path.
- `OverlayEngine.installView`'s main-thread post no longer relies on a
  bare captured `var` for the result; the value is published through an
  `AtomicBoolean` and we now check `Handler.post`'s return value so a
  Looper-exiting race returns a clean `false` instead of leaking a hang.
- `LumenService.startInForeground` now registers the notification channel
  defensively (idempotent if `OpenLumenApp.onCreate` already registered
  it). Closes a race on the `LOCKED_BOOT_COMPLETED` → service-start path
  where the channel could be missing if direct-boot started us before
  `Application.onCreate` had a chance to set it up.
- `LumenService` listens for `Intent.ACTION_USER_UNLOCKED` at runtime so a
  service started pre-unlock (direct-boot restore) transitions to
  observing credential-protected preferences immediately on unlock,
  instead of waiting for a tile / widget / app interaction to nudge it.

### Changed
- `LightSensorAdapter.lux()` now backs a `shareIn(WhileSubscribed(5s))`
  shared flow instead of returning a fresh `callbackFlow` per collector.
  Both the ViewModel and the foreground service used to collect this
  independently, registering two SensorManager listeners and roughly
  doubling the battery cost of the ambient-light trigger.
- `DriverProbe.probeAll` runs the four engine probes in parallel via
  `async`/`coroutineScope` instead of serializing. CDM is reflection-only
  and fast, but SurfaceFlinger and KCAL both spawn multiple `su`
  subprocesses on first probe; on root devices first-launch is now
  visibly snappier.
- `OfflineCities.search` early-terminates via a sequence + `take(limit)`,
  so a broad query no longer scans the full ~95-row catalog when 12 hits
  would do. Defines clean behavior for `limit <= 0` (empty result).
- Driver tab's "Auto" row now shows which engine Auto would actually pick
  ("Auto picks: X") so the user can see at a glance what they're getting,
  or get a one-line hint when no engine is available yet.
- `MainActivity` notification-permission prompt now records a one-shot
  flag in private SharedPreferences instead of relying on the system to
  silently no-op repeated launches. The prompt still re-fires when the
  system reports `shouldShowRequestPermissionRationale=true`, so a user
  who denied once and changed their mind isn't punished.
- `ScheduleAlarmReceiver` logs the FGS-blocked reason explicitly when a
  schedule fire couldn't restart the service. Diagnostics field reports
  on Android 12+ now have the right breadcrumb when the user is in a
  restrictive app-standby bucket.

### Added
- Unit-test coverage for `Su.resetCacheIfSuLikelyFailed` (boundary
  exit codes: `0`, `1`, `127`, `-1`, `255`) and for `OfflineCities.search`
  edge cases (`limit = 0`, negative limit, broad-query cap, blank query
  with cap).

### Fixed (carried over from 0.5.0 / Unreleased)
- App no longer crashes at launch on Android 10 (and other devices where
  WorkManager's auto-init runs against a directBootAware Application
  context that hasn't settled to credential-protected storage yet).
  Glance pulls WorkManager in transitively; we disable its
  `androidx.startup` auto-initializer and implement
  `Configuration.Provider` on `OpenLumenApp`, letting WorkManager
  lazy-initialize when Glance first enqueues widget work — which only
  happens post-unlock when storage paths are fully resolved. Fixes #5.

## [0.5.0] — 2026-05-17

Reliability, polish, and Direct Boot restore. Rolls up the rev 5
distribution / platform / CI refresh, the 21-fix rev 6 audit pass
(C146-C165 + C170), and the three rev-6 follow-ups that landed in
the continuation passes (C166, C168, C169) plus the small backlog
batch (C114, C53 stretch, C115, C107, C110).

User-visible highlights (also in `fastlane/.../changelogs/6.txt`):

- Direct Boot restore: tint returns on reboot before unlock.
- 4x1 widget highlights the currently-active preset.
- Fine ±0.5% dim nudge buttons (PWM-sensitive users).
- Perceived-brightness reduction indicator alongside blue suppression.
- Diagnostics log filter by level / category.
- Location dialog Save works on comma-decimal locales.

Many under-the-hood reliability, concurrency, and performance fixes
detailed below.

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
- Profile imports now report duplicate saved-profile names that were skipped
  by the existing last-write-wins sanitizer.
- Engine switches now reset the service target cache so SurfaceFlinger, KCAL,
  and other engines receive the first matrix emission even when the user did not
  change preset, intensity, or dim values.
- About and Driver screen clipboard actions now read Compose string resources
  outside click handlers, satisfying the updated Compose lint configuration
  invalidation check.
- Direct Boot restore now uses a device-protected mirror and
  `LOCKED_BOOT_COMPLETED` receiver so the last active tint can be restored
  before the first user unlock without reading credential-protected
  preferences.
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
- Protan, Deutan, and Tritan presets now carry optional 3x3 RGB matrix
  coefficients for matrix-capable engines, while scalar-only engines keep
  channel-scale fallbacks.
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
- AndroidX stable baseline is refreshed to Compose BOM 2026.05.00,
  Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation 2.9.8,
  DataStore 1.2.1, Material 3 1.4.0, and core-ktx 1.18.0; `compileSdk`
  is now 36 while `targetSdk` stays 35 until Android 17 validation.
- Gradle dependency verification is now enforced with checked-in
  `gradle/verification-metadata.xml` generated after the AGP 9 and
  AndroidX refreshes.
- Home-screen widgets now render through Jetpack Glance 1.1.1 while
  preserving the existing toggle / preset broadcast receiver action paths.
- The foreground service is direct-boot aware and falls root-only driver
  choices back to the Overlay engine until the user unlocks.
- Home now shows perceived brightness reduction next to blue-channel
  reduction, using transformed-white relative luminance as a display-output
  metric.
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
- Roborazzi JVM screenshot verification is wired into Gradle and CI with
  two checked-in theme-token PNG baselines.
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

### Hardening (2026-05-17 deep audit pass — second sweep)
Second-sweep correctness, concurrency, performance, and UX fixes from the
2026-05-17 deep audit. On disk on `main`; ships in v0.5.0 or v0.5.1.
- `DirectBootStateStore` sanitizer now clamps the optional 3x3 CVD matrix
  coefficients and `hasColorMatrix` flag mirrored to the device-protected
  payload so a malformed mirror can't reach the engine on Locked Boot
  restore. (The first sweep also briefly replaced the
  `DataStoreFactory.createInDeviceProtectedStorage` call with a manual
  `produceFile` form on the belief that the API didn't exist — that
  was a misread; the API has shipped in `androidx.datastore` since
  1.2.0-alpha01, and the project pins 1.2.1. The original positional
  call site is preserved so existing Direct Boot mirror files keep
  their on-disk path.) Also the serializer now decodes garbage bytes
  into the safe default rather than throwing back into DataStore.
- `OverlayEngine` detects stale `hostView` carry-over after a service-process
  kill (singleton survives the kill while the WindowManager rips the token)
  and reinstalls fresh instead of silently no-op'ing `apply()`. Also caches
  `lastAppliedArgb` so widget-refresh broadcasts that re-emit the same color
  don't trigger redundant repaints.
- `OverlayEngine.apply/clear/isAvailable` now run inline when the caller is
  already on the Main thread, avoiding the deadlock where
  `LumenService.onDestroy`'s `runBlocking { engine.clear() }` would wait the
  full 2 s timeout for a `withContext(Dispatchers.Main)` dispatch into the
  parked Looper.
- `LocationEntryDialog` is now locale-independent: coordinates always
  format/parse against `Locale.ROOT`, but the parse path tolerates a single
  comma as the user's decimal separator. Pre-fix, German / French / Spanish
  locales hit a Catch-22 where the auto-fill wrote `52,5200` and the parser
  rejected it, disabling Save permanently. Parse helper extracted to
  `CoordParsing` for JVM testability.
- `DiagnosticsLog` and `CrashLogger` append + size-check + trim is now one
  synchronized critical section. Without that guard a concurrent trim+append
  race could overwrite the survivor's append with the loser's trim. The
  trim itself now uses `RandomAccessFile.seek+readFully` so it never
  allocates the whole file on the heap. Reads also acquire the lock briefly
  so the in-app log dialog never observes a torn mid-trim file.
- `OpenLumenApp` is now declared `directBootAware="true"` in the manifest and
  swallows OEM `NotificationManager` quirks in early boot.
- `Su.runCommandInternal` caps captured output at 16 KiB so a misbehaving
  `su` writing MBs inside the 4 s timeout can't OOM us. `Su.runShell` drainer
  now discards bytes into a fixed buffer instead of `readText`'s unbounded
  `String` allocation.
- `OverlayPermissionCard` accepts a `requiredByActiveEngine` flag and the
  Home screen passes false when the user pinned a root engine that doesn't
  need overlay — the card was previously a permanent nag for root users.
- `MainActivity.requestNotificationPermissionIfNeeded` migrated from the
  legacy `ActivityCompat.requestPermissions` to
  `ActivityResultContracts.RequestPermission`.
- Notification "Next preset" action now writes a one-shot diagnostic line
  when favorites is empty, so users troubleshooting via About → diagnostics
  log see why the button does nothing.
- `LumenTileService.onCreate` cancels the prior scope's Job before swapping,
  so an OEM that skips `onDestroy` on rebind doesn't leak the previous
  scope's in-flight work.
- `SurfaceFlingerEngine.isAvailable` short-circuits when `workingCode` is
  cached, and `apply()` re-probes once when the cache is empty so a pinned
  engine doesn't silently no-op. Without this, every conflated prefs
  emission for an `Auto`-mode user re-spawned up to 3 `su` subprocesses.
- `KcalEngine.isAvailable` short-circuits when `resolvedPaths` is cached,
  and `apply()` re-probes once when the cache is empty. Same `su`-storm
  performance bug as SurfaceFlinger.
- `LumenService.maybeBroadcastWidgetRefresh` diffs a `WidgetSnapshot`
  (`enabled`, `activePresetKey`, `favoritePresetKeys`) against the last
  broadcast and skips the refresh pair when none of those fields changed.
  Pre-fix, a slider drag flooded both Glance widgets with recompose
  requests for fields they don't render.
- `LumenService.ensureEngine` caches the chosen `EngineKind` for
  `Auto`-mode preferences across emissions, invalidated only when the
  user changes `Preferences.engine`. `pickBest` was being called per
  conflated emission even when the engine couldn't have changed.
- `PreferencesStore.decodeOrDefault` logs once per process when the
  persisted JSON fails to decode (still falls back to defaults), so a
  contributor pulling a driver report has a breadcrumb instead of a
  silent config reset.
- New `CoordParsingTest` covers dot/comma decimals, mixed-separator
  rejection, blank input, non-numeric input, NaN/Inf rejection, and
  `Locale.ROOT` format invariance.
- Extended `DirectBootStateSerializerTest` with regression coverage for
  CVD matrix-coefficient clamping and for garbage-bytes decoding to the
  safe default rather than throwing.

### Continuation batch 3 (post-rev-6 backlog, same day)

Three more backlog items closed in the same session — two docs + a
small test-coverage refactor.

- **C107 docs — FGS job runtime quota policy.** `docs/wake-and-vitals.md`
  now has a 'WorkManager / JobScheduler policy (C107)' section
  documenting the deliberate decision to not use WorkManager today,
  noting that the Android 16+ FGS runtime quotas therefore don't
  apply to OpenLumen, and listing the four constraints any future
  WorkManager integration must satisfy (correct constraints,
  expedited-only-when-justified, stay under the 30s/10min
  expedited budget, surface new wake sources in this audit).
- **C110 review — Material 3 1.5.0 / Expressive components.**
  `docs/deferred-candidates.md` adds a review section that scopes
  the expressive component set against OpenLumen's UI surface:
  `SplitButton` is the clearest fit (Driver tab's Copy/Share
  buttons), `FloatingToolbar` and `ButtonGroup` are deferred, the
  rest are not relevant today. Decision: continue to hold the
  rev-5 "do not adopt yet" position; re-review at
  `material3-expressive 1.5.0-stable`.
- **C53 stretch — refactor: extract `DiagnosticsLog.lineMatches`.**
  The per-line filter logic moved out of the `AboutScreen` dialog
  into a public helper on `DiagnosticsLog` so it has JVM tests
  (five new cases in `DiagnosticsLogFormatTest` covering happy
  path, level-filtered-out, category-filtered-out, blank/malformed
  rejection, and multi-word message preservation through
  `split(' ', limit = 4)`). No behavior change — same filter, just
  now reachable without spinning up a Compose harness.

### Continuation batch 2 (post-rev-6 backlog, same day)

Three small backlog items — two UX + one docs — closed in the same
session as the C166/C168/C169 continuation.

- **C114 — Fine-grain dim precision for PWM-sensitive users.** Inline
  ±0.5% nudge buttons next to the Home tab dim slider. New
  `home_dim_value_precise` string renders the dim value with one
  decimal place so the precision is visible. PWM-sensitive users
  asking for sub-1% landing in the 0-10% region (rev-4 PWM signal
  cluster S80 / S103 / S107) now have a thumb-precision-independent
  path. `DIM_FINE_STEP = 0.005` constant centralizes the step size
  for future tuning.
- **C53 stretch — Diagnostics-log filter by category/level.** The
  About-tab "View diagnostics log" dialog now exposes two FilterChip
  rows — one for the 4 levels (DEBUG/INFO/WARN/ERROR) and one for
  the 8 categories. Default selection is WARN + ERROR (the maintainer
  triage default) with all categories on. Selection persists across
  reopens via `rememberSaveable`. Line count shows "N of M". Pre-fix
  the dialog dumped raw log text; a 32 KiB log was unscrollable
  in practice for triage purposes.
- **C115 docs — Kelvin slider already filters green light.**
  `docs/health-evidence.md` now documents that the existing Kelvin
  control (1000-10 000 K via the Tanner Helland approximation)
  suppresses green output at low Kelvin values (~17/255 at 1500 K)
  and explains why we don't add a dedicated G-channel filter: the
  Kelvin axis is physically grounded, a separate G-suppressor would
  produce color casts users couldn't reason about. Answers Red Moon
  issue #353 (S86) in the canonical health-evidence document
  instead of in a forum reply.

### Continuation (post-rev-6 polish, same day)

Three of the four `Later`-tier follow-ups surfaced in the rev 6 audit
landed on `main` immediately after the rev 6 roadmap entry. Small,
self-contained polish closing gaps the audit pass identified but
didn't fix in the first sweep.

- **C169 — PresetWidget highlights the currently-active favorite.**
  Active chip wrapped in a Catppuccin Surface1 contrast-ring `Box`
  (24 dp outer, 16 dp inner) with the label in `FontWeight.Bold`.
  Inactive chips render with `WidgetColors.MutedText` so the active
  slot reads at a glance without making the widget noisy. Highlight
  is keyed on `enabled && entry.key == activePresetKey` so an "off"
  filter doesn't make any chip look active.
- **C168 — OverlayPermissionCard memoizes `Settings.canDrawOverlays`.**
  `mutableStateOf(...)` cache + `DisposableEffect` on
  `LocalLifecycleOwner` listening for `ON_START` / `ON_RESUME`
  replaces the per-recompose binder roundtrip. `LaunchedEffect(Unit)`
  also re-queries on first composition so a navigation back doesn't
  wait for the next resume tick. Pre-API-23 the cache stays `true`
  and no observer is registered.
- **C166 — KCAL preserves the user's existing `kcal_min`.** Probe
  captures the original value once; `apply` only raises the floor to
  `SAFETY_MIN = 20` when the user's original is lower, and only once
  per probed session; `clear` restores the original. KCAL no longer
  silently overwrites a kernel parameter the user may have tuned
  themselves. Uninstalling OpenLumen now leaves `kcal_min` exactly
  where the user found it.

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

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# OpenLumen Roadmap

Research version: 2026-05-17 **rev 6**. Rev 6 is the fourth walk-away
pass on 2026-05-17, layered on top of the rev 5 distribution / platform
/ CI refresh. It folds in a four-round in-tree audit (post-rev-5,
later the same day) that landed 21 correctness, concurrency,
performance, observability, and UX fixes (C146-C165 + C170), and a
follow-up continuation pass that landed 3 of the 4 surfaced
candidates (C166, C168, C169) on top. C167/C195 (LumenService
subsystem split) later shipped in v0.6.2. Rev 6 also retracts one
round-one audit "finding" that turned out to be wrong (the
`DataStoreFactory.createInDeviceProtectedStorage` API does exist —
see the rev 6 correction note below).

Rev 5 (preserved verbatim below) was the third walk-away pass on the
same day. It preserves rev 4.1 and adds a distribution / platform / CI
refresh from live primary sources: Android developer verification,
Android 17 Beta 4 behavior changes, AGP 9.2 / Gradle 9.4.1
compatibility, Dagger/Hilt 2.59.2 constraints, AndroidX current
stable versions, GitHub Actions Node 24 migration, and current action
major versions.

## Implementation progress after rev 6

All twenty-one C146-C165 + C170 items below landed on `main` after
rev 5 was written, in a four-round audit pass on 2026-05-17. They
ship in v0.5.0 or v0.5.1 alongside the rev-5 hardening batch.

- [x] **C146 — DirectBoot mirror CVD-coefficient sanitize gap** shipped
  2026-05-17. `DirectBootStateStore.sanitizeDirectBootMatrix` now
  clamps the optional 3x3 CVD matrix coefficients and `hasColorMatrix`
  flag mirrored to the device-protected payload so a malformed mirror
  cannot reach the engine on Locked Boot restore. Mirrors the bounds
  applied by `PreferencesStore.sanitizeMatrix`. Sources: S00, S00s.
- [x] **C147 — OverlayEngine stale-hostView after service-process kill**
  shipped 2026-05-17. The engine is `@Singleton`, so the same instance
  survives an FGS process kill while the WindowManager rips the
  underlying window token. `installView`/`apply` now check
  `View.isAttachedToWindow` and reinstall against the fresh service
  token instead of silently writing `setBackgroundColor` to a detached
  view. Also caches `lastAppliedArgb` so widget-refresh broadcasts
  that re-emit the same color don't repaint redundantly. Sources:
  S00, S288.
- [x] **C148 — OverlayEngine main-thread deadlock on shutdown** shipped
  2026-05-17. `LumenService.onDestroy` calls `runBlocking { e.clear() }`
  on Main. `OverlayEngine.clear` was `withContext(Dispatchers.Main)`,
  which would block waiting for a dispatch into the parked Looper and
  only the 2 s `withTimeoutOrNull` escaped the hang. New
  `suspend inline onMain` helper detects "already on Main" via
  `Looper.myLooper() == Looper.getMainLooper()` and runs inline,
  removing the deadlock. The service's `onDestroy` comment now
  explains why the default `runBlocking` dispatcher must be preserved.
  Sources: S00, S289.
- [x] **C149 — LocationEntryDialog locale-decimal Catch-22** shipped
  2026-05-17. `String.format("%.4f", lat)` uses `Locale.getDefault()`,
  so on German / French / Spanish locales the city picker auto-filled
  `52,5200` and `String.toDoubleOrNull()` rejected it — Save stayed
  disabled forever and the user could not proceed. Coordinate
  format/parse is now locked to `Locale.ROOT` and the parse path
  tolerates a single comma as the user's decimal separator. Logic
  extracted to a testable `CoordParsing` object with 7 new JVM tests.
  Sources: S00, S290.
- [x] **C150 / C151 — DiagnosticsLog and CrashLogger append+trim race**
  shipped 2026-05-17. Both writers did `appendText` → `length()` →
  `readBytes` → `writeBytes` with no synchronization. Two concurrent
  callers (service + tile, or two threads in a multi-threaded crash)
  could lose interleaved appends to the loser's trim. Append + size
  check + trim is now one `synchronized(writeLock)` critical section.
  Trim itself was also rewritten to use `RandomAccessFile.seek` +
  `readFully` so it never allocates the whole file on the heap.
  Sources: S00.
- [x] **C152 — DiagnosticsLog/CrashLogger read-during-trim torn read**
  shipped 2026-05-17. Reads briefly acquire the same `writeLock`, so
  the in-app log dialog never observes a partial trim rewrite. The
  contention cost is negligible because reads are user-driven. Sources:
  S00.
- [x] **C153 — Application directBootAware + safe channel registration**
  shipped 2026-05-17. `LumenService` and `LockedBootReceiver` were
  declared `directBootAware="true"` but `OpenLumenApp` was not, which
  the AOSP rule treats as "Application creation may be deferred until
  unlock" — defeating Hilt injection on the locked-boot path.
  `OpenLumenApp` is now declared `directBootAware="true"` and its
  `NotificationManager.createNotificationChannel` call is wrapped in
  `runCatching` so OEM forks that throw in early boot don't crash the
  Application init. Sources: S00, S153.
- [x] **C154 — Su.runCommandInternal output cap** shipped 2026-05-17.
  The capture buffer is now a 16 KiB hard cap with a single warn log
  on truncation. Previously a misbehaving `su` writing MBs of output
  inside the 4 s timeout could OOM the captured `StringBuilder`.
  Sources: S00.
- [x] **C155 — Su.runShell drainer cap** shipped 2026-05-17. The
  drainer thread no longer uses `BufferedReader.readText()` (an
  unbounded `String` allocation) and instead reads into a fixed 4 KiB
  byte buffer that discards output. Callers of `runShell` only care
  about the exit code. Same OOM class as C154 but on the
  pipe-into-`sh` path. Sources: S00.
- [x] **C156 — OverlayPermissionCard relevance-aware visibility**
  shipped 2026-05-17. The card previously nagged every user with
  missing `SYSTEM_ALERT_WINDOW`, including root users who pinned
  `SurfaceFlinger` / `KCAL` and never needed overlay. Now accepts a
  `requiredByActiveEngine` flag and the Home screen passes `false`
  when the user's engine choice doesn't require overlay. Sources: S00.
- [x] **C157 — MainActivity notification-permission prompt cleanup**
  shipped 2026-05-17. Replaced the legacy `ActivityCompat.requestPermissions`
  with `ActivityResultContracts.RequestPermission`. The launcher is
  now a class field so it survives configuration changes and behaves
  correctly on Android 13+'s two-deny-then-permanent rule. Sources:
  S00.
- [x] **C158 — Notification cycle no-op diagnostic breadcrumb** shipped
  2026-05-17. The notification's "Next preset" action stays visible
  even when favorites is empty (rebuilding the notification on every
  favorites edit was the documented trade-off). Now writes a single
  `DiagnosticsLog` line on the empty-favorites no-op path so users
  troubleshooting via About → diagnostics log see the cause instead
  of a silent button. Sources: S00.
- [x] **C159 — LumenTileService scope hygiene across missed onDestroy**
  shipped 2026-05-17. TileService docs are vague about cycle
  guarantees; some OEMs skip `onDestroy` on rebind. `onCreate` now
  cancels the prior scope's Job before swapping, so the previous
  scope's in-flight work doesn't leak. Sources: S00.
- [x] **C160 / C161 — SurfaceFlingerEngine and KcalEngine probe caches**
  shipped 2026-05-17. `isAvailable` now short-circuits when
  `workingCode` / `resolvedPaths` is cached. Pre-fix, an `Auto`-mode
  user dragging a slider triggered up to 9 `su` subprocess spawns per
  conflated emission — `pickBest` → 4 × `isAvailable`, and SF/KCAL
  each re-probed every candidate. Cache invalidation on apply/clear
  failure (rev-5 C136 work) keeps stale state recoverable. Sources:
  S00, S00d.
- [x] **C162 / C163 — SurfaceFlingerEngine and KcalEngine apply()
  re-probe** shipped 2026-05-17. A user who pinned SF/KCAL could
  land in a state where the engine was "available" but every apply
  silently no-op'd because the cache was empty (first call after
  construction or after invalidation). Both `apply` paths now re-probe
  once before logging loudly and giving up. Sources: S00.
- [x] **C164 — LumenService widget-broadcast diff** shipped 2026-05-17.
  `maybeBroadcastWidgetRefresh` diffs a `WidgetSnapshot(enabled,
  activePresetKey, favoritePresetKeys)` against the last broadcast
  and skips the refresh pair when the widget-rendered fields haven't
  changed. Pre-fix, a slider drag flooded both Glance widgets with
  recompose requests for fields they don't render. Sources: S00.
- [x] **C165 — LumenService Auto-mode pickBest result cache** shipped
  2026-05-17. Even with C160/C161 making the leaf probes cheap,
  `pickBest` still walked 4 engines and allocated a sorted list per
  call. `cachedAutoKind` now holds the chosen `EngineKind` across
  emissions, invalidated only when `Preferences.engine` flips between
  Auto and a pinned kind. Sources: S00.
- [x] **C170 — PreferencesStore decode-failure visibility log** shipped
  2026-05-17. `decodeOrDefault` previously reverted to `Preferences()`
  on any throw, making "config reset itself" a black-box failure mode.
  Now logs the exception class + truncated message once per process
  (gated by an `AtomicBoolean` because the DataStore flow can re-read
  the same corrupt blob many times). Still falls back to defaults —
  silent recovery is the right behavior for users — but a contributor
  pulling a driver report finally has a breadcrumb. Sources: S00.

### Continuation pass (post-rev-6, same day)

Three of the four `Later`-tier follow-ups surfaced in the rev 6
audit (C166, C168, C169) landed on `main` immediately after the rev 6
roadmap entry was written. They are small, self-contained, and
unblock no other work — but each closes a real polish gap that
would otherwise rot. C167 (LumenService subsystem split) shipped later
as C195 in v0.6.2.

- [x] **C169 — PresetWidget highlights the currently-active favorite.**
  `PresetSlotUi.isActive = enabled && entry.key == activePresetKey`.
  The active chip is wrapped in a `Surface1` contrast-ring `Box`
  (24 dp outer, 16 dp inner) and the label is rendered in
  `FontWeight.Bold`. Inactive labels render in `WidgetColors.MutedText`
  so the active slot stands out without making the widget noisy.
  New `WidgetColors.ActiveRing` (Catppuccin Surface1 / `0xFF45475A`)
  added to the shared palette. Sources: S00, S118.
- [x] **C168 — OverlayPermissionCard memoizes `Settings.canDrawOverlays`.**
  The card now keeps a `mutableStateOf(...)` cache of the overlay
  permission, populated by a `DisposableEffect` that listens for
  `Lifecycle.Event.ON_START` / `ON_RESUME` on `LocalLifecycleOwner`
  and re-queries only then. A `LaunchedEffect(Unit)` also re-queries
  on first entry so a screen rotation or back-navigation doesn't
  wait for the next resume tick. Per-recompose binder roundtrip
  removed. On pre-Android-6 (API < 23) the cache stays `true` and
  no observer is registered. Sources: S00.
- [x] **C166 — KCAL preserves the user's existing `kcal_min`.**
  Probe now reads `kcal_min` into `Paths.originalMin` via a
  best-effort `cat`. `apply` only raises the floor to
  `SAFETY_MIN = 20` if the user's original was lower, and only once
  per probed session (tracked via a `BoolHolder` latch on the
  `Paths` record). `clear` restores the original value only when
  we actually raised it. KCAL no longer silently mutates a kernel
  parameter the user may have tuned themselves; uninstalling the
  app leaves `kcal_min` exactly where the user found it. Sources:
  S00.

## What changed in rev 6

- **In-tree audit pass (rounds 1-4)** shipped 21 fixes (C146-C165 plus
  C170). Net diff was +696/-132 over 18 files, +2 new files
  (`CoordParsing.kt` and `CoordParsingTest.kt`), +35 lines in the
  extended `DirectBootStateSerializerTest`. CHANGELOG documents
  every line.
- **One rev-6 round-one finding retracted**:
  - The round-one summary claimed
    `DataStoreFactory.createInDeviceProtectedStorage(...)` did not exist
    in any androidx-datastore release and treated this as a
    compile-blocking bug. Cross-checked against the AndroidX DataStore
    release notes (S287, also already cited as S95/S280 in earlier
    revs) and confirmed the API has shipped since
    `androidx.datastore:datastore:1.2.0-alpha01` (2025-03-26). The
    project pins `1.2.1`, so the original call site was correct. The
    code change was reverted; the matrix-sanitize gap fix (C146) and
    the serializer try/catch on garbage bytes are kept because they
    are real, independent improvements. The CHANGELOG entry was
    also corrected.
- **Four new follow-up candidates** that surfaced in the audit but
  weren't fixed (full I/E/R in the rev 6 candidate table below):
  - **C166** — KCAL: preserve user's existing `kcal_min` rather than
    overwriting with the hardcoded literal `20`. Product decision.
  - **C167** — Split `LumenService.kt` into focused subsystems
    (engine lifecycle, schedule alarm, light sensor, widget bridge,
    Direct Boot mirror, ramp). Shipped later as C195 in v0.6.2.
  - **C168** — `OverlayPermissionCard` re-evaluates
    `Settings.canDrawOverlays` per recomposition. Single binder call,
    not measurable today, but cleaner via a lifecycle-bound
    `produceState`.
  - **C169** — `PresetWidget` doesn't highlight the currently-active
    favorite. Pure UX polish.
- **No tier shifts on rev-5 candidates.** The audit pass touched
  implementation, not the forward plan.

### Rev 6 candidate additions

| ID | Candidate | Category | Tier | I/E/R | Concrete action | Sources |
|---|---|---|---|---|---|---|
| C146 | DirectBoot mirror CVD-coefficient sanitize gap | correctness/security | Shipped 2026-05-17 | 3/1/1 | `DirectBootStateStore.sanitizeDirectBootMatrix` clamps `hasColorMatrix` + all 9 CVD coefficients to the same bounds `PreferencesStore.sanitizeMatrix` uses. Serializer also now decodes garbage to safe default. | S00, S00s |
| C147 | OverlayEngine stale-hostView after FGS process kill | reliability | Shipped 2026-05-17 | 4/2/2 | `View.isAttachedToWindow` check on every install/apply; `discardStaleHostLocked` reinstalls fresh; `lastAppliedArgb` cache skips redundant paints. | S00, S288 |
| C148 | OverlayEngine `onDestroy` Main-thread deadlock | reliability | Shipped 2026-05-17 | 4/2/2 | `private suspend inline onMain {}` helper detects current thread is Main via `Looper.myLooper() == Looper.getMainLooper()` and runs inline; `LumenService.onDestroy` keeps default `runBlocking` dispatcher so root engines' `Dispatchers.IO` switch still works. | S00, S289 |
| C149 | LocationEntryDialog locale-decimal Catch-22 | UX / i18n | Shipped 2026-05-17 | 4/2/1 | New `CoordParsing` object: `format(Double)` uses `Locale.ROOT`, `parse(String)` tolerates single comma as decimal separator, rejects mixed `.`/`,` and NaN/Inf. New `CoordParsingTest` covers 7 cases. | S00, S290 |
| C150 | DiagnosticsLog append+trim concurrency | concurrency | Shipped 2026-05-17 | 3/1/1 | Append + size check + trim wrapped in one `synchronized(writeLock)`; trim rewritten with `RandomAccessFile.seek`+`readFully` so it never heap-allocates the whole file. | S00 |
| C151 | CrashLogger append+trim concurrency | concurrency | Shipped 2026-05-17 | 3/1/1 | Same pattern as C150; `install` also double-checks `installed` under `writeLock`. | S00 |
| C152 | Diagnostics/Crash read-during-trim torn read | concurrency | Shipped 2026-05-17 | 2/1/1 | `read()` briefly acquires `writeLock` so the dialog never renders a half-trimmed file. | S00 |
| C153 | Application `directBootAware` + safe channel registration | reliability/manifest | Shipped 2026-05-17 | 3/1/1 | `<application android:directBootAware="true">` so AOSP creates the Hilt Application before the locked-boot service starts; `createNotificationChannel` wrapped in `runCatching` for early-boot OEM quirks. | S00, S153 |
| C154 | `Su.runCommandInternal` output cap | resource safety | Shipped 2026-05-17 | 2/1/1 | Capture cap at 16 KiB; extra bytes drained; one warn log on truncation. | S00 |
| C155 | `Su.runShell` drainer cap | resource safety | Shipped 2026-05-17 | 2/1/1 | Drainer reads into a fixed 4 KiB byte buffer and discards; `BufferedReader.readText()` (unbounded `String` allocation) removed. | S00 |
| C156 | OverlayPermissionCard relevance-aware visibility | UX | Shipped 2026-05-17 | 3/1/1 | New `requiredByActiveEngine` flag; Home passes `false` for pinned root engines so root users no longer see a permanent overlay-permission nag. | S00 |
| C157 | MainActivity notification-permission prompt cleanup | UX | Shipped 2026-05-17 | 2/1/1 | Migrated to `ActivityResultContracts.RequestPermission`; launcher as class field for config-change safety. | S00 |
| C158 | Notification cycle no-op diagnostic breadcrumb | observability/UX | Shipped 2026-05-17 | 2/1/1 | `ACTION_CYCLE_PRESET` writes a single `DiagnosticsLog` line when favorites is empty and the cycle is a no-op. | S00 |
| C159 | LumenTileService scope hygiene | reliability | Shipped 2026-05-17 | 2/1/1 | `onCreate` cancels prior scope's Job before swapping, so a missed-onDestroy OEM doesn't leak the previous scope's in-flight work. | S00 |
| C160 | SurfaceFlingerEngine probe-cache short-circuit | performance | Shipped 2026-05-17 | 4/1/1 | `isAvailable` returns immediately when `workingCode` is cached; `probeLocked` extracted as the slow path. Eliminates up to 3 `su` spawns per conflated Auto-mode emission. | S00, S00d |
| C161 | KcalEngine probe-cache short-circuit | performance | Shipped 2026-05-17 | 4/1/1 | Same pattern as C160 against `resolvedPaths`. Eliminates up to 6 `su` spawns (3 candidate roots × `test -e` calls) per conflated Auto-mode emission. | S00 |
| C162 | SurfaceFlingerEngine apply() re-probe | reliability | Shipped 2026-05-17 | 3/1/1 | `apply` re-probes once when `workingCode` is null instead of silently no-op'ing; logs loudly if no code works. | S00 |
| C163 | KcalEngine apply() re-probe | reliability | Shipped 2026-05-17 | 3/1/1 | Same defense as C162 for KCAL `resolvedPaths`. | S00 |
| C164 | LumenService widget-broadcast diff | performance | Shipped 2026-05-17 | 3/1/1 | `WidgetSnapshot(enabled, activePresetKey, favoritePresetKeys)` diff gates `ToggleWidget.broadcastRefresh` + `PresetWidget.broadcastRefresh`. Slider drags no longer fan out to Glance recomposes. | S00 |
| C165 | LumenService Auto-mode `pickBest` cache | performance | Shipped 2026-05-17 | 3/1/1 | `cachedAutoKind` holds the chosen `EngineKind` across emissions; invalidated only when `Preferences.engine` flips. | S00 |
| C166 | KCAL preserve user's existing `kcal_min` | UX | Shipped 2026-05-17 | 3/2/2 | Probe captures `originalMin` once; `apply` only raises `kcal_min` to the `SAFETY_MIN = 20` floor when the user's value is lower; `clear` restores the original. KCAL engine no longer silently mutates a kernel parameter the user may have tuned themselves. | S00 |
| C167 | LumenService subsystem split | maintainability | Shipped 2026-06-27 as C195 / v0.6.2 | 2/4/2 | Implemented the 5-class service split: `EngineController`, `ScheduleAlarmOrchestrator`, `LightSensorSubscription`, `WidgetBridge`, `DirectBootMirror`. `LumenService.kt` is now the orchestrator, and app unit tests cover widget diffing plus Direct Boot matrix round-tripping. | S00 |
| C168 | OverlayPermissionCard memoize `canDrawOverlays` | performance | Shipped 2026-05-17 | 1/1/1 | `mutableStateOf` cache + `DisposableEffect` on `LocalLifecycleOwner` re-queries only on `ON_START`/`ON_RESUME` and `LaunchedEffect(Unit)` for first entry. Per-recompose binder roundtrip removed. | S00 |
| C169 | PresetWidget highlight active favorite | UX | Shipped 2026-05-17 | 2/2/1 | `PresetSlotUi.isActive = enabled && entry.key == activePresetKey`; active chip wrapped in a Surface1 contrast-ring `Box`, label rendered bold via `FontWeight.Bold`, inactive chips muted. New `WidgetColors.ActiveRing` (Catppuccin Surface1). | S00, S118 |
| C170 | PreferencesStore decode-failure visibility log | observability | Shipped 2026-05-17 | 2/1/1 | `decodeOrDefault` logs the exception class + truncated message once per process via an `AtomicBoolean` latch when persisted JSON fails to decode; still falls back to defaults. | S00 |

### Rev 6 sources

Four new external citations (S287-S290) layered on top of the rev 1-5
source appendix. All are primary developer.android.com / Oracle JDK
references and double-cite already-present rev-5 sources where
applicable.

- **S287**: AndroidX DataStore release notes — confirms
  `DataStoreFactory.createInDeviceProtectedStorage()` shipped in
  `1.2.0-alpha01` (2026-03-26 per the same-day release-note text;
  the public Maven listing is the canonical home) and was preserved
  through `1.2.1`. Used to retract the round-one
  "non-existent API" finding —
  https://developer.android.com/jetpack/androidx/releases/datastore
- **S288**: Android `View.isAttachedToWindow()` reference (added API
  19; safe at minSdk 26 with no shim) — used by C147's stale-view
  reinstall path —
  https://developer.android.com/reference/android/view/View#isAttachedToWindow()
- **S289**: Android `Looper.myLooper()` / `Looper.getMainLooper()`
  reference — canonical "am I on the Main thread" check used by
  C148's `onMain` helper —
  https://developer.android.com/reference/android/os/Looper
- **S290**: Java `Double.parseDouble` / Kotlin `String.toDoubleOrNull`
  contract — only accepts `.` as the decimal separator regardless of
  default locale. Used to justify the C149 `Locale.ROOT` lockdown —
  https://docs.oracle.com/javase/8/docs/api/java/lang/Double.html#parseDouble-java.lang.String-

## Implementation progress after rev 5

- [x] **C35 — Final adaptive icon** shipped on 2026-05-17. The launcher
  background/foreground vectors now use the final minimal crescent mark,
  `branding/openlumen-icon.svg` records the source geometry, and
  `fastlane/metadata/android/en-US/images/icon.png` provides the 512x512
  store icon for F-Droid / Play metadata.
- [x] **C95 / C96 / C101 / C124 — AGP 9, Hilt Compose, screenshot CI
  train** shipped on 2026-05-17. The build now uses AGP 9.2.1,
  Gradle 9.4.1, Kotlin 2.3.21, KSP 2.3.8, Dagger/Hilt 2.59.2,
  `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0`, and
  Compose Preview Screenshot Testing `0.0.1-alpha14`. CI now runs
  `:app:validateDebugScreenshotTest` with an initial textless
  theme-token fixture and checked-in references. Local full validation
  passed from `C:\Users\Xray\OpenLumen-agp9-verify` because the `Z:`
  shared-folder path hit a Windows D8 path limitation under AGP 9.
- [x] **C142 — CI action major rotation and SHA-pinning policy** shipped
  on 2026-05-17. Workflows now use `actions/checkout@v6`,
  `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`,
  `actions/upload-artifact@v7`, `actions/attest@v4`, and
  `anchore/scan-action@v7`. The release workflow now grants
  `id-token: write` and `attestations: write` for provenance. The
  project keeps current major-version tags for Dependabot ergonomics,
  with full-SHA pins reserved for incident response, high-risk release
  hardening, or actions without trustworthy major-maintenance signals.
- [x] **C143 — Android 17 memory/resizability smoke expansion** shipped
  on 2026-05-17. `docs/android-17-readiness.md` covers the Android 17
  behavior changes, and `docs/device-matrix.md` now has a concrete
  add-on smoke flow for `ApplicationExitInfo` / `MemoryLimiter:AnonSwap`
  review plus `sw600dp`, foldable, tablet, desktop-windowing, and TV
  layout checks.
- [x] **C132-C136 — service/engine correctness batch** shipped on
  2026-05-17. `LumenService` now serializes ramp cancellation / launch
  with a dedicated `rampMutex` and cancels+joins an in-flight ramp before
  clearing or switching engines. `ColorDisplayManagerEngine` invalidates
  partial reflection cache failures. `OverlayEngine` serializes view /
  `WindowManager` mutations. SurfaceFlinger and KCAL now invalidate their
  cached driver path/code after failed apply/clear writes.
- [x] **C130 — AAPM driver-report surface** shipped on 2026-05-17.
  The driver report now includes a reflection-gated Android 17 Advanced
  Protection section, declares `QUERY_ADVANCED_PROTECTION_MODE`, and
  reports `enabled`, `disabled`, `n/a`, or a bounded `unknown` reason.
- [x] **C120 — VCS info determinism** shipped on 2026-05-17. Release
  builds now set `vcsInfo.include = false` so AGP does not package
  `META-INF/version-control-info.textproto`; `docs/reproducible-build.md`
  explains the F-Droid comparison risk and the external provenance path.
- [x] **C111 — BAL hardening readiness audit** shipped on 2026-05-17.
  A source audit found no `IntentSender`, `ActivityOptions`, or
  `MODE_BACKGROUND_ACTIVITY_START_*` call sites to migrate; existing
  `PendingIntent` usage is direct activity/service/broadcast routing.
- [x] **C116 — don't resume after restart if paused** shipped on
  2026-05-17. `BootReceiver` already gates restore on persisted
  `enabled = true`; `docs/troubleshooting.md` now documents the paused
  reboot behavior explicitly.
- [x] **C106 — BOOT_COMPLETED FGS verification rows** shipped on
  2026-05-17. `docs/wake-and-vitals.md` now has explicit Android
  14/15/16/17 boot-restore rows, and `docs/device-matrix.md` now requires
  a boot-restore note for every Android 14+ device result. Actual pass/fail
  evidence remains under C01 until tested on hardware/emulators.
- [x] **C144 — AndroidX stable baseline refresh** shipped on
  2026-05-17. OpenLumen now uses Compose BOM 2026.05.00, Activity Compose
  1.13.0, Lifecycle 2.10.0, Navigation 2.9.8, DataStore 1.2.1, Material
  3 1.4.0, and core-ktx 1.18.0 with `compileSdk = 36` and `targetSdk =
  35`.
- [x] **C48 — Gradle dependency verification** shipped on 2026-05-17.
  `gradle/verification-metadata.xml` is now checked in after the AGP 9
  and AndroidX refreshes, and the mirror build passes assemble, lint,
  screenshot validation, and unit tests with
  `--dependency-verification=strict`.
- [x] **C123 — Glance API widget rewrite** shipped on 2026-05-17.
  `ToggleWidget` and `PresetWidget` now render through
  `GlanceAppWidgetReceiver` / `GlanceAppWidget` on
  `androidx.glance:glance-appwidget:1.1.1`, while keeping the existing
  `WidgetActionReceiver` toggle and preset broadcast path. The XML
  AppWidgetProviderInfo layouts remain as launcher picker / initial
  previews only. Strict dependency verification passed from the local
  mirror after refreshing metadata.
- [x] **C122 — Roborazzi gold-image CI** shipped on 2026-05-17.
  The build now uses Roborazzi 1.60.0 plus Robolectric 4.16.1 for a
  JVM/Robolectric theme-token golden lane. CI runs
  `:app:verifyRoborazziDebug`, and two textless PNG baselines live under
  `app/src/test/roborazzi/`.
- [x] **C139 — import duplicate-name UI feedback** shipped on
  2026-05-17. `PreferencesStore.importFrom()` and `previewImport()` now
  return `ImportSummary`, and the import preview / result path lists
  duplicate profile names that were skipped by the existing
  last-write-wins sanitizer.
- [x] **C63 — matrix-capable CVD preset slice** shipped on 2026-05-17.
  `LumenMatrix` now carries optional 3x3 RGB matrix coefficients for
  SurfaceFlinger-class engines; Protan / Deutan / Tritan presets use
  DaltonLens-derived linear-RGB matrices where a matrix engine can consume
  them, while scalar-only engines keep the older channel-scale fallback.
  A true per-pixel LUT / piecewise Brettel tritan pass remains split into
  C145.
- [x] **C28 / C102 — Direct Boot restore** shipped on 2026-05-17. The
  unlocked service mirrors the last active tint matrix and selected engine
  to a device-protected DataStore, `LOCKED_BOOT_COMPLETED` starts a
  direct-boot-aware service path, and root-only engines degrade to the
  rootless Overlay path until the user unlocks.
- [x] **C127 — Perceived-luminance reduction indicator** shipped on
  2026-05-17. The Home tab now pairs blue-channel reduction with a
  relative perceived-brightness reduction metric derived from the effective
  transformed-white luminance.

## What changed in rev 5

- **Android developer verification is now a release-planning item**.
  Starting September 2026, apps in Brazil, Indonesia, Singapore, and
  Thailand must be registered by a verified developer to install on
  certified Android devices, regardless of whether they come from Play
  or outside Play. OpenLumen is F-Droid-first, so this becomes **C141
  - Android Developer Console package registration**. Sources: S230-S232.
- **GitHub Actions needs a Node 24 / action-major rotation before the
  release train hardens**. GitHub says runners begin defaulting
  JavaScript actions to Node 24 on 2026-06-02; OpenLumen still uses
  `actions/checkout@v4`, `actions/setup-java@v4`,
  `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`,
  `actions/attest-build-provenance@v2`, and `anchore/scan-action@v6`
  in workflows. Current upstream majors are checkout v6, setup-java
  v5, setup-gradle v6, attest/attest-build-provenance v4, and
  scan-action v7. This becomes **C142 - CI action major rotation and
  SHA-pinning policy**. Sources: S242-S251.
- **Android 17 Beta 4 adds two test-plan gaps**. The previous C103
  Android 17 readiness work covered AAPM, BAL, FGS, and API naming,
  but not the Beta 4 all-app memory limiter or the target-37
  large-screen orientation/resizability behavior. These become
  **C143 - Android 17 memory/resizability smoke expansion** under the
  existing Android 17 readiness umbrella. Sources: S233-S236.
- **Dependency targets landed in attributable batches**. C95/C96/C124
  shipped the AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.21 / Hilt 2.59.2
  train. C144 then refreshed the stable AndroidX floor to Compose BOM
  2026.05.00, Material 3 1.4.0, Activity Compose 1.13.0, Lifecycle
  2.10.0, Navigation 2.9.8, DataStore 1.2.1, and core-ktx 1.18.0 while
  raising `compileSdk` to 36 and keeping `targetSdk` at 35. Sources:
  S237-S241, S252-S253, S269-S281, S00l.
- **Competitor sweep saturation retested**. No new direct OpenLumen-grade
  framebuffer/root competitor surfaced. DimTV has a fresher Android TV /
  overlay signal than rev 4 recorded, and general Android help content
  still points users back to Red Moon / Twilight / Screen Filter for the
  overlay class. Sources: S254-S256.

### Rev 5 candidate additions

| ID | Candidate | Category | Tier | I/E/R | Concrete action | Why now | Sources |
|---|---|---|---|---|---|---|---|
| C141 | Android Developer Console package registration | distribution / trust | Now | 5/2/2 | Decide Play Console vs Android Developer Console path; verify identity; register `com.openlumen` and release signing certificate before the September 2026 regional enforcement window. Document the account owner and package-registration evidence outside Git. | F-Droid / direct APK users in the first enforcement regions can otherwise hit install blocks even though OpenLumen stays outside Play. | S230, S231, S232 |
| C142 | CI action major rotation and SHA-pinning policy | supply chain / CI | Shipped 2026-05-17 | 4/2/2 | Rotated workflow actions to current Node-24-capable majors; documented major-tag policy with a full-SHA exception path; local validation covered YAML parsing plus debug build/lint/unit tests. | GitHub starts defaulting JavaScript actions to Node 24 on 2026-06-02; GitHub docs still state full SHA is the only immutable action reference. | S242, S243, S244, S245, S246, S247, S248, S249, S250, S251, S258-S265 |
| C143 | Android 17 memory/resizability smoke expansion | mobile / compatibility | Shipped 2026-05-17 | 3/1/1 | Extended `docs/android-17-readiness.md` and the device-matrix smoke flow to cover `ApplicationExitInfo` MemoryLimiter review plus sw600dp / foldable / desktop-windowing layout checks. | Android 17 Beta 4 is the final scheduled beta; these two behaviors were not covered in rev 4.1's C103 notes. | S233, S234, S235, S236, S266 |
| C144 | AndroidX stable baseline refresh batch | upgrade strategy | Shipped 2026-05-17 | 3/2/2 | Refreshed core/activity/lifecycle/navigation/DataStore, Compose BOM, and Material 3 as one stable AndroidX batch; raised `compileSdk` to 36 while leaving `targetSdk` at 35; fixed the new Compose lint findings by hoisting string resources out of click handlers. | Current stable AndroidX releases were far ahead of the repo floor; the batch keeps dependency churn separate from the AGP 9 toolchain migration and gives Direct Boot restore a stable DataStore 1.2.1 floor. | S237, S238, S239, S252, S253, S275-S281, S00l |
| C145 | True CVD LUT / piecewise tritan completion | accessibility / image quality | Later (design-sketched 2026-05-17) | 3/4/3 | `docs/deferred-candidates.md` now contains a 4-step design sketch — Brettel piecewise tritan LUT generation in Kotlin, persistence to `filesDir/cvd-tritan-lut.bin`, `RenderEffect.createColorFilterEffect` application path (API 31+), schema-version invalidation. The reference implementation contract is "round-trip through libDaltonLens.c within 1 LSB per channel". F-Droid reproducibility and runtime-compute cost are the deferral reasons. | S119, S120, S285, S286, S00s |

Research version: 2026-05-17 **rev 4.1**. Rev 4.1 is the second walk-away
pass on the same day. It preserves rev 4 verbatim (which itself
supplements rev 3) and adds nine more candidates (C132-C140) drawn from
a focused **code-quality review** plus a deeper **F-Droid / Shizuku /
Compose-BOM** research pass. Rev 4.1 also folds in 27 new sources
(S203-S229) and one tier shift (C128 → Later because Shizuku-in-ADB
cannot create FabricatedOverlays on Android 12L+).

Rev 4.1 history pointers:

- The first walk-away pass produced rev 4 (this section + the rev 4
  candidate inventory and source appendix below).
- The second walk-away pass produced this rev 4.1 supplement and the
  research notebook entry
  [.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md](.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md).
- The doc/process follow-ups rev 4 itemised are all **done** as of rev
  4.1 — see [.ai/research/2026-05-17/CHANGESET_SUMMARY.md](.ai/research/2026-05-17/CHANGESET_SUMMARY.md).

## What changed in rev 4.1

- **Seven doc/process follow-ups from rev 4 are now done**: the
  `docs/api-36-readiness.md → docs/android-17-readiness.md` rename
  with body retitle; the `docs/research-watchlist.md` "Last review"
  date bump; the `docs/health-evidence.md` Sources refresh
  (S99-S102 + S158-S162); a new MASVS-PRIVACY section in
  `docs/threat-model.md`; the protobuf-java CVE-2024-7254 entry in
  `docs/sbom-and-advisories.md`'s "Accepted exposures"; the
  permissions-audit grep expanded in both `ci.yml` and `release.yml`
  to also block `ACCESS_*_LOCATION`, `READ_PHONE_STATE`,
  `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, and
  `BIND_ACCESSIBILITY_SERVICE`; the 2026-05-17 audit hardening folded
  into `CHANGELOG.md [Unreleased]`.
- **Nine new candidates from second-pass research**:
  - **C132** — `LumenService.applyMatrix` ramp-scheduling atomicity
    fix (HIGH severity; Now).
  - **C133** — `LumenService.clearAndStop` cancel-and-join
    `transitionJob` (HIGH severity; Now).
  - **C134** — `ColorDisplayManagerEngine.load` cache invalidation on
    partial-failure path (HIGH severity; Now).
  - **C135** — `OverlayEngine.installView` thread-safety with
    `apply`/`clear` (HIGH severity; Now).
  - **C136** — Engine apply exit-code checking + cache invalidation on
    SF/KCAL regressions (Med severity; Now).
  - **C137** — `material-icons-extended` deprecation migration
    (shipped 2026-05-17; local vector resources replace the dependency).
  - **C138** — `PreferencesStore` import-size cap byte-correctness
    (Med; shipped 2026-05-17).
  - **C139** — `PreferencesStore` import duplicate-name UI feedback
    via `ImportSummary.droppedDuplicateNames` (Later; UX).
  - **C140** — F-Droid initial submission (Now; new evidence S203-S211
    confirms OpenLumen has *never* been submitted — no MR, no RFP, no
    listing).
- **One tier shift**: C128 (FabricatedOverlay engine spike) moves
  Under Consideration → Later. New evidence S223 confirms Shizuku-in-
  ADB-mode cannot create new FabricatedOverlays on Android 12L+; only
  Shizuku-on-root or Sui can. This invalidates rev 4's framing of
  C128 as a "Shizuku-not-root" 5th engine. C128 either becomes a
  root-tier option or merges into the C06 root-tier spike scope.
- **Concrete AGP 9 + Compose BOM targets identified** (S225-S229) for
  the C95/C144 migration sequence: C95 shipped the AGP 9 train and C144
  shipped Compose BOM `2024.12.01 -> 2026.05.00` plus Material 3
  `1.3.1 -> 1.4.0`. Do NOT adopt `material3-expressive` yet (still
  alpha). `material-icons-extended` was removed under C137.
- **Concrete Shizuku integration code shapes harvested** (S212-S221)
  for the C06 spike: `Shizuku.OnBinderReceivedListenerSticky` +
  `Shizuku.OnBinderDeadListener` for service-restart survival;
  `ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))`
  for `IActivityManager` binding;
  `IActivityTaskManager.registerTaskStackListener` for foreground-
  task detection without `UsageStats` / a11y. Zero CVEs against
  Shizuku as of 2026-05-17 (S224).
- **F-Droid submission status confirmed unsubmitted** — no prior
  fdroiddata MR, no RFP issue, no listing. Submission is a clean
  first-time MR using the F-Droid Quick Start Guide (S206). Captured
  as C140.

Research version: 2026-05-17 rev 4. Supplements rev 3 (also 2026-05-17,
earlier the same day) while preserving its shipped history, candidate IDs
(C01-C127), source IDs (S00-S125), and tier placements. Rev 4 adds four
new candidates (C128-C131), two tier shifts, primary-source citations
(S126-S202), and a list of doc / process follow-ups surfaced by the
walk-away research pass on the afternoon of 2026-05-17. The accompanying
research notebook lives at
[.ai/research/2026-05-17/](.ai/research/2026-05-17/) and the canonical
project memory has been consolidated into
[PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

OpenLumen is an offline, GPL-3.0-or-later Android display filter. It should
remain F-Droid-clean, no-INTERNET by default, privacy-literal, and technically
honest about the difference between framebuffer/root/system transforms and
overlay fallback.

## What changed in rev 4

- **Four new candidates** (full I/E/R + sketch in the New-candidates table
  below):
  - **C128 — FabricatedOverlay engine spike** (Under Consideration).
    Android 12+ Shizuku-only privileged path that survives reboot via
    runtime overlays. Possible fifth `ColorEngine`. Source: S168
    (ColorBlendr).
  - **C129 — OLED-aware gamma LUT clamp** (Later). Successor to C66
    scalar clamp; scale gamma LUT to keep `(0,0,0)` truly off on OLED.
    Source: S174 (cosmos), S160.
  - **C130 — AAPM driver-report surface** (Now). Detect
    `AdvancedProtectionManager` on Android 17+ and surface state in the
    driver report. Pairs with rev 3's C79 / C80 rejection rationale.
    Sources: S134, S135, S136.
  - **C131 — Eye Dropper integration on Android 17+** (Later). Optional
    `OPEN_EYE_DROPPER` intent from the custom-RGB picker.
    Sources: S129, S139.
- **Two tier shifts**:
  - **C123 (Glance widget rewrite)**: Under Consideration → Next.
    Glance is stable since 1.0.0 (1.1.0 in 2024-06-12 per S193); the
    "Glance is alpha" blocker rev 3 cited no longer holds.
  - **C101 (Compose Preview Screenshot Testing CI)** keeps its Now
    placement, but risk bumps 1 → 2 because the tool is still
    `0.0.1-alphaXX` per S148/S149. Plan a version-pin policy.
- **Primary-source citation refresh**. Every Android 17 / AAPM / FGS /
  AGP / Hilt / DataStore / Compose / F-Droid / sleep claim that rev 3
  cited via secondary sources now also cites the primary
  developer.android.com, OWASP, or peer-reviewed source (S126-S165,
  S185-S202). Existing tier placements are unchanged; only the
  evidence base is broadened.
- **Wider competitor sweep** discovered four active 2025-2026 Android
  OSS entrants the roadmap did not previously know about: EcoDimmer
  (S166), Grayscaler (S167), ColorBlendr (S168), Adaptive Theme
  (S169). The Shizuku-using prior art (LSFG-Android S179, DarQ S180)
  is now sourced — feeds into the C06 design notes.
- **Doc / process follow-ups** (not candidates per se, captured here
  rather than buried):
  - Rename `docs/android-17-readiness.md` → `docs/android-17-readiness.md`
    and re-title the body to match rev 3's C82 → C103 expansion.
  - Bump `docs/research-watchlist.md` "Last review" header to
    2026-05-17.
  - Refresh `docs/health-evidence.md` Sources section to add S99-S102 +
    S158-S162 (this is C126's deliverable).
  - Extend `docs/threat-model.md` with a MASVS-PRIVACY section to match
    MASVS v2.1.0 (S192). The substance is covered; the categorical
    header is missing.
  - Fold the 2026-05-17 audit hardening list into `CHANGELOG.md
    [Unreleased]` (or cut a v0.5.1 hardening release).
  - Extend the `permissions-audit` CI grep to also block
    `ACCESS_*_LOCATION`, `READ_PHONE_STATE`, `QUERY_ALL_PACKAGES`,
    `PACKAGE_USAGE_STATS`, and `BIND_ACCESSIBILITY_SERVICE` from the
    merged manifest. Cheap insurance.
  - Record protobuf-java CVE-2024-7254 (S77) in
    `docs/sbom-and-advisories.md` "Accepted exposures" so the scanner
    noise doesn't re-surface every Monday.

## What changed in rev 3

- **Android 17 stable lands June 2026** (Beta 4 shipped 2026-04-16). The
  v0.6.0 release must be Android-17-validated, not just "API 36 ready."
  See S83, S84, S96.
- **Android 17 Advanced Protection Mode auto-revokes the AccessibilityService
  API for any app not flagged `isAccessibilityTool="true"`.** That closes
  the AccessibilityService backend permanently for OpenLumen and elevates
  Shizuku as the only viable per-app path. C79 moves from
  Under Consideration to Rejected; C06 (Shizuku) stays Under Consideration
  but is the only remaining option. Sources S88, S89, S90.
- **Android 15+ restricts `SYSTEM_ALERT_WINDOW` apps from starting a
  foreground service from the background unless the overlay window is
  already visible.** Affects the tile/widget toggle-on flow when the
  service isn't already running. New candidate C105 (Now). Source S85.
- **Android 14+ blocks `BOOT_COMPLETED` receivers from launching certain
  foreground-service types**; `specialUse` is not on the affected list but
  this needs explicit verification. New candidate C106 (Now). Source S85.
- **AGP 9.0 shipped 2026-01, 9.1.0 in 2026-04, 9.2.0 in 2026-04. AGP 10
  closes the opt-out window in mid-2026.** Promotes C95 from Next to Now.
  Sources S91, S92, S93.
- **AndroidX Hilt's `hiltViewModel()` moved from
  `androidx.hilt:hilt-navigation-compose` to
  `androidx.hilt:hilt-lifecycle-viewmodel-compose`** with a deprecation
  notice. Promotes C96 from Next to Now. Source S94.
- **DataStore now ships `createInDeviceProtectedStorage()` and
  `deviceProtectedDataStore()` first-class APIs.** Cuts C28 (Direct Boot
  restore) effort from 4 to 3 and removes the design risk. Stays Next.
  Source S95.
- **Compose Preview Screenshot Testing is now a first-class IDE+Gradle
  feature** (Android Studio Otter 3, AGP 8.5+). Unblocks C83 with no
  emulator dependency. Cuts effort 3→2. Stays Now. Sources S97, S98.
- **Sleep-research consensus has shifted** — the dominant 2025/2026
  evidence says total luminance matters more than blue-light spectrum for
  sleep onset. We already avoid medical claims; we should explicitly note
  the consensus shift and consider surfacing a "perceived luminance
  reduction" indicator alongside the blue-suppression metric.
  Sources S99, S100, S101, S102.
- **In-tree audit hardening pass (2026-05-17)** — corrected the
  Schedule.kt Solar bug (used system clock instead of caller `now`),
  SolarCalculator polar day/night collapse, NYC-sunset date-stamping,
  LumenService mid-ramp lerp-from-stale-target, PreferencesStore nested
  profile-snapshot sanitization, LightSensorAdapter trySend backpressure,
  OverlayEngine cutout coverage and main-thread safety, KcalEngine
  optional-`kcal_min` probe, Su.runShell deadlock guard, and several
  resilience improvements. These are on disk but not yet released;
  they fold into the v0.5.0 changelog or a v0.5.1 hardening cut.

## State of the Repo

OpenLumen currently ships v0.4.0; v0.5.0 is feature-complete on `main` and
awaits the device-validation gate. Stack: Kotlin 2.3.21, AGP 9.2.1,
Gradle 9.4.1, JDK 17, Jetpack Compose, Material 3, Hilt 2.59.2,
DataStore, kotlinx.serialization, and host-side Compose Preview
Screenshot Testing.
minSdk 26, targetSdk 35. Four modules: `app`, `core-engine`, `core-schedule`,
`core-prefs`.

What works today:

- Four display engines: AOSP `ColorDisplayManager`, root `SurfaceFlinger`,
  root KCAL, rootless overlay fallback. Runtime probe + applyMutex-
  serialized engine calls.
- Compose UI with Home, Schedule, Presets, Driver, About tabs.
- Named presets, custom RGB, per-channel gamma, intensity, dim, contrast,
  Kelvin slider, AMOLED true-black clamp, blue-suppression indicator.
- Schedule: fixed-time, NOAA solar, until-next-alarm, always-on/off.
  AlarmManager-driven transitions with smooth ramps.
- Light sensor trigger (OR with schedule) with screen-off invalidation.
- Quick Settings tile (subtitle + long-press preferences), 1x1 toggle
  widget, 4x1 preset widget, foreground notification with cycle/off
  actions.
- SAF JSON export/import with field-level preview diff. Versioned schema
  migrations. Named profile library (cap 32, name ≤48 chars). Previous-
  preset restore. Profile snapshots sanitized.
- Documented intent surface for Tasker/Termux/ADB
  (`docs/automation.md`).
- Local crash log + bounded diagnostics log (no network exfiltration).
- F-Droid metadata skeleton, SBOM/advisory CI workflow, build provenance
  attestations, dependency-verification procedure, threat model.

What is incomplete:

- No real-device validation rows in `docs/device-matrix.md` for Pixel,
  Samsung, Snapdragon+KCAL, non-root overlay, Android TV, or Android 17
  preview behavior.
- Store screenshots are still placeholder; the final adaptive/store icon
  is now present.
- Per-app rules, Shizuku backend, Wear OS companion, Direct Boot restore,
  Android TV flavor, accessibility-scanner pass, and Compose screenshot
  tests are not implemented.
- The hardening fixes from 2026-05-17 (Solar bug, polar handling, mid-
  ramp lerp, nested sanitize, overlay cutout) ship in v0.5.0 or v0.5.1
  but are not yet released.

Hard constraints (unchanged):

- License: GPL-3.0-or-later.
- Android: minSdk 26, targetSdk 35 today; `specialUse` FGS.
- Distribution: F-Droid first, Play optional, no ads, no required
  account, no `INTERNET` permission in the main app.
- UX/aesthetic: Catppuccin Mocha/AMOLED, Compose Material 3, no pill-
  shaped buttons, no marketing copy inside the app.

## Evidence Map

### Direct OSS and near-OSS competitors (rev 3 update)

| Project | Fit | Stars | Latest activity | Maintainer signal | Notable feature signal | Sources |
|---|---:|---:|---|---|---|---|
| Red Moon | Direct OSS baseline | 721 | issue feed through 2026-04-05 (#354 backup request) | "not actively maintained, PRs accepted" — but issue queue still informs roadmap | profiles, excluded apps, widget/tile/notification, root beta, translations; recent open: backup (#354), filter-melanopsin (#353), one-handed dim (#351), don't-resume-after-restart (#349), F-Droid icon (#348), GrapheneOS dropdown miss (#347), Shizuku (#342), previous-profile (#339), contrast (#340) | S10, S11, S12, S13, S86 |
| Twilight (Urbandroid) | Commercial reference | n/a | v14.25 on 2026-02-09 | active commercial | sun-cycle filtering, per-app profiles, Wear OS tile, Chromebook, Philips Hue + IKEA TRÅDFRI smart-light integration, Pro features behind paywall | S20, S87 |
| Shades | Ancestor/demo | 8 | 2017 | inactive | opacity, tint, persistent notification, boot restore | S14 |
| Night-Light | Native sample | 11 | 2016 | inactive | native Night Mode, blacklist, QS tile, Tasker | S15 |
| DimTV | Android TV | 10 | 2025-02-16 | small active fork | TV UI, environment adjustment | S16 |
| Low Brightness | Modern overlay | 30 | v5.1.0 on 2026-01-28 | active small project | Material You, no internet, QS tile, schedules, AccessibilityService overlay | S17 |
| Screen Filter | Old OSS | 6 | 2017 | inactive | color temperature, intensity, opacity, auto on/off | S18 |
| Eye-Rest | Old OSS | 14 | 2019 | inactive | intensity, color picker, scheduled interval | S19 |
| Pixel Filter | AMOLED dim | 62 | archived 2019 | inactive | pixel-grid dimming, light-sensor, pattern shifting | S69, S70 |
| Screen Dimming | Recent micro | 0 | v1.0 on 2026-02-18 | single maintainer | emergency-unlock gesture, language selector | S71 |
| dim_overlay_app | Recent demo | 1 | 2 commits | single maintainer | FGS overlay, slider opacity onboarding | S81 |
| SwingShift | Minimal sample | 0 | 1 commit | single maintainer | Kotlin scaffold based on Night Shift | S82 |
| OLED Saver (Screen Dimmer dev.rewhex) | PWM-sensitive overlay | n/a | active 2026 | active | AccessibilityService overlay, pixel-level dim, PWM-avoidance workflow | S103 |
| CF.Lumen / f.lux Android | Root reference | n/a | legacy | dormant | system-level demand | S21, S22, S43 |

### Commercial / platform competitors (unchanged from rev 2)

| Product | Opportunity signal | Sources |
|---|---|---|
| Twilight | Sun-cycle ramp, Wear OS tile, automation, Hue/IKEA TRÅDFRI, AccessibilityService overlay, per-app, translations | S20, S87 |
| f.lux | Root-required Android path shows quality demand | S21, S22 |
| Iris | PWM-aware dimming, partial-screen filters, presets, automation, color effects, multi-display | S23 |
| CareUEyes | Break reminders + dim + filter bundle (commercial value); not in OpenLumen scope | S24 |
| Android Night Light | AOSP path requires HWC2 color transform support | S25 |
| Android Extra Dim | Built-in dimming often too weak; root/third-party still in demand | S41, S44 |
| Lunar (macOS) | Adaptive brightness from ambient sensors + location, dim-below-0 | S39, S104 |

### Adjacent projects worth borrowing from

| Project | Borrowable pattern | Sources |
|---|---|---|
| Redshift | Config model, honest gamma-ramp FAQ | S34 |
| Hyprshade | Shader presets, schedule, packaging docs | S35, S105 |
| sunsetr | Smooth transitions, location/manual modes, hot reload, IPC | S36, S106 |
| wl-gammarelay-rs | Small DBus control surface | S37 |
| wluma | Ambient + screen-content adaptive brightness | S38 |
| Lunar | App presets, sensor adaptation, hotkeys | S39, S104 |
| ScreenDimmer desktop | Hotkeys, multi-screen, smooth transitions, OSD | S40 |
| Hyprland ecosystem | Awesome lists for blue-light filtering across Wayland | S72, S105 |

### Community, policy, and security signals (rev 3 highlights)

| Source class | Signal | Sources |
|---|---|---|
| Android 17 release timing | Beta 4 on 2026-04-16; stable expected June 2026 | S83, S96 |
| Android 17 AAPM | Auto-revokes accessibility API for apps without `isAccessibilityTool="true"`. Banking trojans up 56% in 2025 — Google now treats accessibility as an attack vector for non-disability tools. | S88, S89, S90 |
| Android 17 BAL hardening | `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated for IntentSender; use `_ALLOW_IF_VISIBLE` | S84 |
| FGS background-start (Android 15+) | SAW apps need a visible overlay to start an FGS from background | S85 |
| BOOT_COMPLETED FGS (Android 14+) | Cannot launch certain FGS types from boot | S85 |
| FGS runtime quotas (Android 16+) | Background jobs from FGS adhere to runtime quotas | S85 |
| AGP migration window | 9.0/9.1/9.2 stable; AGP 10 (mid-2026) removes opt-outs | S91, S92, S93 |
| AndroidX Hilt artifact rename | `hilt-lifecycle-viewmodel-compose` replaces `hilt-navigation-compose` for `hiltViewModel()` | S94 |
| DataStore Direct Boot | First-class `deviceProtectedDataStore()` / `createInDeviceProtectedStorage()` APIs | S95 |
| Compose Preview Screenshot Testing | Built into IDE + AGP; no emulator needed | S97, S98 |
| Sleep evidence | 2025/2026 consensus: total luminance > spectrum for sleep onset; one prominent researcher retracted earlier blue-light claims | S99, S100, S101, S102 |
| PWM sensitivity | AMOLED PWM still drives demand for overlay-at-high-brightness workflows | S80, S103, S107 |
| Overlay attacks | OWASP MASTG v2 overhauling overlay-attack testing | S64, S67, S68, S108 |
| Tooling | CycloneDX Gradle plugin remains best Android SBOM path; Syft+Grype for filesystem scans | S109, S110 |
| F-Droid | 70% translation threshold for release inclusion | S111 |
| F-Droid reproducible builds | APK signature copying after rebuild match; AGP `version-control-info.textproto` is a known non-determinism point | S61, S112 |

### Local evidence

- S00: Local repo reconnaissance on 2026-05-17:
  `README.md`, `CHANGELOG.md`, `ROADMAP.md`, `LICENSE`,
  `.github/workflows/*`, `gradle/libs.versions.toml`, manifests, full
  Kotlin source tree, tests, `docs/**`, and last 30 commits.
- S00b: 2026-05-17 in-tree audit pass — fixed Schedule.kt Solar date bug,
  SolarCalculator polar day/night collapse, NYC sunset date-stamping,
  LumenService mid-ramp lerp from stale target, PreferencesStore nested
  snapshot sanitization, LightSensorAdapter backpressure, OverlayEngine
  cutout coverage and main-thread safety, KcalEngine `kcal_min` optional
  probe, Su.runShell drainer thread, observePreferences resilience,
  ACTION_SET_PRESET validation against the Presets registry, refreshProbes
  invalidates Su cache. Added regression tests for the Solar / polar /
  NYC / Tokyo cases.

## Prioritization Rules

Impact, effort, and risk use 1 (low) → 5 (high). "Parity" means catching up
with common competitor expectations. "Leapfrog" means moving materially
ahead of Android OSS peers without violating the no-network philosophy.

- **Now**: required for a credible v1.0 / F-Droid-ready release, or blocks
  trust.
- **Next**: fits the product but depends on Now hardening.
- **Later**: useful, but bigger surface area, device-specific, or less
  central.
- **Under Consideration**: plausible, but needs a spike because of policy,
  privacy, distribution, or dependency concerns.
- **Rejected**: contradicts repo philosophy, costs too much for value, or
  has weak evidence.

## Progress toward v0.5.0

Shipped on `main` (full list preserved from rev 2):

- **C02** In-app driver report export
- **C03** SurfaceFlinger code registry — per-API ladder, `activeTransactionCode` diagnostic
- **C04** KCAL variant probing — three known sysfs roots, `activeBasePath` diagnostic
- **C05** Root prompt safety and recovery docs ([docs/root-safety.md](docs/root-safety.md))
- **C07** Guided WRITE_SECURE_SETTINGS grant — Driver screen state + copyable adb command
- **C09** Overlay alpha cap explanation — Driver screen info card
- **C13** Emergency off command — About screen ADB command with copy-to-clipboard
- **C14** Previous profile restore — Presets-screen Restore affordance + `ACTION_RESTORE_PREVIOUS` intent
- **C15** Favorite presets — `favoritePresetKeys`, star toggle on Presets screen, cap 8
- **C16** Notification preset cycle — "Next preset" notification action
- **C17** QS tile long-press deep link — `PREFERENCES_ACTIVITY` manifest meta-data
- **C18** QS tile secondary state label — subtitle shows active preset
- **C19** Home-screen 1x1 toggle widget
- **C20** Home-screen 4x1 preset widget
- **C23** Smooth fixed-time transitions — `transitionDurationMs`, ramp coroutine
- **C24** Smooth solar transitions — shared ramp path
- **C25** Alarm-based schedule mode — `ScheduleMode.UntilNextAlarm` + 12h fallback
- **C26** Offline city picker — `OfflineCities` (~95 cities), nearest + search
- **C27** Automatic timezone fallback — Schedule screen shows system zone label
- **C29** Versioned preference migrations — `schemaVersion` + `PreferencesMigrations`
- **C30** Profile import preview — `previewImport(uri)` + import diff dialog
- **C31** Named profile library — `ProfileSnapshot`, `NamedProfile`, About-tab UI, cap 32
- **C32** Red Moon profile import notes ([docs/profile-import-formats.md](docs/profile-import-formats.md))
- **C33** CF.Lumen import notes — manual mapping table
- **C34** F-Droid metadata ([fastlane/metadata/android/](fastlane/metadata/android/))
- **C37** Reproducible build notes ([docs/reproducible-build.md](docs/reproducible-build.md))
- **C38** Artifact attestations — `actions/attest@v4` in release workflow
- **C40** README troubleshooting table ([docs/troubleshooting.md](docs/troubleshooting.md))
- **C41** CONTRIBUTING.md
- **C42** ARCHITECTURE.md ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md))
- **C43** Issue templates
- **C44** Public compatibility table ([docs/compatibility-table.md](docs/compatibility-table.md))
- **C45** Release checklist ([docs/release-checklist.md](docs/release-checklist.md))
- **C46** Dependency update cadence — Dependabot weekly
- **C47** Dependabot/Renovate ([.github/dependabot.yml](.github/dependabot.yml))
- **C48** Gradle dependency verification — `gradle/verification-metadata.xml`
  is checked in and enforced after the AGP 9 / AndroidX baseline refresh;
  [docs/dependency-verification.md](docs/dependency-verification.md)
  documents strict verification and refresh review. Source: S00o.
- **C49** Pin GitHub Actions
- **C50** No-INTERNET CI assertion — `permissions-audit` job
- **C51** OWASP MASVS-lite threat model ([docs/threat-model.md](docs/threat-model.md))
- **C52** Local diagnostics bundle — `DiagnosticsLog` ring-buffered event log, tail in driver report
- **C53** Structured log viewer — About → "View diagnostics log"; the
  post-v1 filter-by-category/level stretch shipped 2026-05-17 as a new
  `DiagnosticsLogDialog` composable with level and category `FilterChip`
  rows backed by `rememberSaveable` selection state (default: WARN +
  ERROR levels, all categories). Showing-N-of-M counter included.
- **C54** Wake/alarm/battery audit ([docs/wake-and-vitals.md](docs/wake-and-vitals.md))
- **C55** Slider TalkBack state descriptions — light/threshold/offsets/RGB/gamma/Kelvin/intensity/dim/contrast
- **C58** RTL / string-resource baseline
- **C59** Weblate/translation workflow ([docs/translations.md](docs/translations.md))
- **C60** Health evidence note ([docs/health-evidence.md](docs/health-evidence.md))
- **C61** Blue-channel reduction indicator (narrow physical-measurement form of the original melanopic candidate)
- **C64** Contrast control
- **C65** Kelvin temperature UI (Tanner Helland approximation)
- **C66** AMOLED true-black clamp (scalar form)
- **C70** Tasker intents — full automation surface documented
- **C71** Shell/ADB command docs ([docs/automation.md](docs/automation.md))
- **C82** Android 16/API 36 readiness inventory ([docs/android-17-readiness.md](docs/android-17-readiness.md))
- **C85** Local panic reset on boot — 5-minute crash-log window
- **C93** Play FGS evidence pack ([docs/play-fgs-evidence.md](docs/play-fgs-evidence.md))
- **C94** SBOM and advisory scan ([.github/workflows/sbom.yml](.github/workflows/sbom.yml))
- **C97** Awesome/topic-index watchlist ([docs/research-watchlist.md](docs/research-watchlist.md))
- **C98** Dynamic ramp duration presets — Instant/30s/5m/15m/30m
- **C99** Event-driven ambient sampling — `ACTION_SCREEN_OFF` invalidates cached lux
- **C100** Medical/pain-mode disclaimer templates — covered in health-evidence.md

Design-doc deliverables (deferred implementations with durable analysis):

- **C10** Overlay blocked-touch troubleshooting — [docs/overlay-and-per-app-design.md](docs/overlay-and-per-app-design.md) + `docs/troubleshooting.md`
- **C11** Per-app pause/exclusions — deferred behind Shizuku spike (C06)
- **C12** Secure/install/su dialog auto-pause — same blocker as C11
- **C28** Direct Boot restore — shipped 2026-05-17 with a device-protected
  mirror and `LOCKED_BOOT_COMPLETED` restore path.
- **C69** Per-app profiles — same Shizuku blocker
- **C90** Emergency unlock gesture — notification/tile/ADB shipped; touch gesture deferred
- **C95** AGP 9 migration spike — **promoted to Now** in rev 3; shipped 2026-05-17
- **C96** Hilt Compose artifact migration — **promoted to Now** in rev 3; shipped 2026-05-17

Hardening fixes on `main` (post-rev-2, 2026-05-17 audit pass; not yet
in a released APK):

- Schedule.kt Solar mode now honors the caller's `now` (was using
  `LocalDate.now(zoneId)`). SolarCalculator returns a `Polar` enum so
  polar-day and polar-night are distinguishable. Sunrise/sunset
  ZonedDateTimes are snapped to the requested local date so Western
  hemisphere sunsets no longer land on the previous day.
- LumenService mid-ramp interruption now lerps from the actually-
  displayed matrix rather than the previous target. `lastTarget` is
  separate from `lastApplied`. Cancel-and-join replaces bare cancel.
  Engine switches reset both fields.
- PreferencesStore sanitizes nested profile-snapshot matrices, schedule
  fields, lux thresholds, intensity, dim, contrast, transition, favorites,
  and preset keys. `previousPresetKey` is sanitized.
- LightSensorAdapter buffers with `DROP_OLDEST` so sensor callbacks
  cannot lose readings to backpressure; rejects non-finite/negative raw
  samples.
- OverlayEngine adds `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` (API 28+) and
  posts `installView` to the main thread when called off-Main.
- KcalEngine probes `kcal_min` separately and only writes to it when
  present.
- Su.runShell drains stdout on a daemon thread to avoid script-output
  deadlocks.
- LumenService.observePreferences wraps each emission in try/catch
  (re-throws CancellationException) with diagnostic logging.
- LumenService.ACTION_SET_PRESET validates the key against
  `Presets.byKey(...)` (plus `"custom"`).
- LumenTileService.refreshTile wraps `updateTile()` in try/catch.
- OpenLumenViewModel.refreshProbes invalidates `Su.cachedAvailable`.
- AboutScreen.describeDiff now surfaces changes to contrast,
  AMOLED clamp, lux threshold, and sunset/sunrise offsets.

These ship in the v0.5.0 changelog cut.

Partial (per rev 2, still partial in rev 3):

- **C01** Real-device validation rows — per-engine smoke flow documented;
  rows pending real hardware.
- **C36** Store screenshot matrix — layout in place; captures pending
  real device/emulator screenshots.
- **C55/C56/C57** Accessibility scanner / dynamic font scale / CVD
  contrast audit — still need a real device pass.
  Static 2026-06-15 pass: profile deletion now has undo recovery,
  chip controls use project shapes, widget labels use larger centered
  ellipsized text, and stale light-theme screenshot baselines were
  refreshed. Accessibility Scanner, font-scale screenshots, and CVD
  contrast device evidence remain open.

## Now: v0.5.0/v0.6.0, F-Droid-ready public release

1. **Device validation and driver report (C01)**
   - Real-device rows in `docs/device-matrix.md`. Include at minimum: a
     Pixel running stable Android 15 and Android 17 preview, a Samsung
     One UI device, a Snapdragon device with a KCAL kernel, and a
     non-root overlay device. The in-app driver report (already shipped)
     is the data-collection mechanism.
   - Impact 5, effort 3, risk 2. Why now: OpenLumen's multi-driver claim
     cannot be trusted without per-device evidence and an easy bug-report
     path. Sources: S00, S10, S11, S25, S26, S48, S86.

2. **F-Droid release packaging (C34, C35, C36, C37, C45)**
   - C35 is shipped. Capture phone screenshots into
      `fastlane/metadata/android/en-US/images/phoneScreenshots/` (C36),
      confirm reproducibility on F-Droid's build server (C37), and walk
     the pre-release checklist (C45). The 70% translation floor (S111)
     applies for translated releases but the en-US baseline is enough
     to ship.
   - Impact 5, effort 3, risk 2. Sources: S00, S11, S29, S60, S61, S62,
     S74, S111, S112.

3. **AGP 9 migration (C95, promoted from Next) — shipped 2026-05-17**
   - Migrated to AGP 9.2.1, Gradle 9.4.1, Kotlin 2.3.21, KSP 2.3.8,
     and AGP 9 built-in Kotlin support. The target SDK remains 35 until
     C103 Android 17 device validation.
   - Impact 4, effort 3, risk 3. Sources: S91, S92, S93, S269-S274.

4. **Hilt Compose artifact rename (C96, promoted from Next) — shipped
   2026-05-17**
   - `hiltViewModel()` imports now use
     `androidx.hilt:hilt-lifecycle-viewmodel-compose` and package
     `androidx.hilt.lifecycle.viewmodel.compose`; Dagger/Hilt is
     2.59.2.
   - Impact 3, effort 2, risk 1. Sources: S94, S240, S269.

5. **Android 17 readiness (C82 extension, supersedes API-36-only scope)**
   - Validate on Android 17 Beta 4 (or stable when it lands in June
     2026). Confirm: tile subtitle render, overlay alpha + cutout,
     exact-alarm fallback, `specialUse` FGS subtype declaration, and
     the new BAL hardening (C111). Add an Android 17 row to
     `docs/device-matrix.md`. Bump `targetSdk` in its own release per
     `docs/android-17-readiness.md` policy.
   - Impact 4, effort 3, risk 3. Sources: S83, S84, S96.

6. **SYSTEM_ALERT_WINDOW + FGS-from-background restriction (C105, new)**
   - Android 15+ requires SAW apps to have a visible overlay window
     before starting an FGS from the background. Audit the tile/widget
     toggle-on path: if the service isn't running and overlay isn't
     visible, the FGS launch can be rejected. Add a fallback that opens
     the app to grant the overlay permission, then re-attempts the
     service start.
   - Impact 4, effort 2, risk 2. Sources: S85.

7. **BOOT_COMPLETED FGS verification (C106, new)**
   - Android 14+ blocks `BOOT_COMPLETED` from launching certain FGS
     types. `specialUse` is not on the affected list per current docs,
     but we should add an explicit Android 14/15/16/17 row to the
     wake/vitals audit and the device matrix confirming the boot-
     restore path still works.
   - Impact 3, effort 1, risk 1. Sources: S85.

8. **Activity Background Start (BAL) hardening readiness (C111, new)**
   - Android 17 deprecates `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`
     for `IntentSender` in favor of
     `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`. Audit the
     PendingIntent / notification-tap and tile long-press paths.
   - Impact 3, effort 1, risk 1. Sources: S84.

9. **Overlay-safe interaction model (C10, C11, C12, C90, C91)**
   - C10/C90 shipped via troubleshooting + notification/tile/ADB
     emergency-off. The remaining work for Now is publishing the design
     decision in `docs/overlay-and-per-app-design.md` (done) and
     ensuring the per-app candidates (C11/C12/C69) are clearly
     blocked-by-Shizuku in the public docs so users understand why
     "auto-pause on installer" is not on the v1 list. The C91
     SurfaceView regression test belongs here once we have a device.
   - Impact 5, effort 3, risk 3. Sources: S10, S12, S18, S20, S26,
     S32, S42, S67, S68, S71, S73, S88, S89, S108.

10. **Test and CI hardening (C83, C84, C91, C94)**
    - C101 shipped the first Compose Preview Screenshot Testing fixture
      and CI job. C83 remains the broader screen-coverage expansion.
      Connected-device tests (C84) and
      SurfaceView regression (C91) still need emulator infrastructure;
      schedule once `reactivecircus/android-emulator-runner`
      [S113] is wired in. SBOM/advisory scan (C94) already runs weekly
      and on release.
    - Impact 5, effort 3, risk 2. Sources: S97, S98, S113, S26, S27, S28.

11. **Security and supply-chain baseline (C38, C47-C51, C94)**
    - Already shipped; C142 refreshed the Actions baseline to
      Node-24-capable current majors and moved release provenance to
      `actions/attest@v4`. Keep the artifact attestation cadence visible
      in the release checklist. Document the protobuf-java CVE-2024-7254
      triage state in `docs/sbom-and-advisories.md` since the scanner
      will keep surfacing it.
    - Impact 4, effort 2, risk 2. Sources: S60, S61, S62, S63, S64,
      S67, S68, S77, S108, S110, S114.

12. **Sleep-evidence consensus update (C100 extension, C127 new) —
    shipped 2026-05-17**
    - The 2025/2026 sleep-science consensus has shifted: total
      luminance matters more than spectrum for sleep onset, and one
      prominent researcher publicly retracted earlier blue-light
      advocacy. Update `docs/health-evidence.md` with a one-paragraph
      "what changed since rev 2" note and reinforce the Home tab's
      "comfort, not treatment" copy. C127 now surfaces a
      "perceived brightness reduced" indicator alongside the existing
      blue-suppression metric on the Home tab.
    - Impact 3, effort 1, risk 1. Sources: S45, S46, S47, S99, S100,
      S101, S102, S158, S159, S160, S161, S162, S00n.

13. **AAPM driver-report surface (C130, new in rev 4)**
    - Reflection-gated query for `AdvancedProtectionManager` state on
      Android 17+; surface in the in-app driver report. Driver-tab info
      card explains *"AAPM auto-revokes Accessibility-based features;
      OpenLumen does not use Accessibility, so AAPM has no effect on
      OpenLumen."* Pairs with the C79/C80 rejection rationale shipped
      in rev 3 — users who try a11y-based competitor features and find
      they're auto-revoked see the receipt in our report.
    - Impact 3, effort 1, risk 1. Sources: S134, S135, S136.

## Next: v0.7.0 → v0.8.0

1. **Direct Boot restore (C28 / C102) — shipped 2026-05-17**
   Device-protected mirror plus `LOCKED_BOOT_COMPLETED` restore path now
   exists. Remaining evidence is hardware/emulator validation under C01,
   not implementation work. Sources: S00, S27, S66, S95, S280, S00m.

2. **Shizuku-backed privileged backend (C06, also unblocks C11, C12, C69)**
   - Optional flavor (or first-class detection at runtime), wired
     through a new `ShizukuEngine` that uses `dumpsys activity recents` /
     IActivityManager binder access for foreground-app detection. Lets
     us ship per-app pause (C11), installer auto-pause (C12), and per-app
     profiles (C69) without `PACKAGE_USAGE_STATS` or AccessibilityService
     (which Android 17 AAPM auto-revokes — S88, S89, S90). Document the
     Shizuku install path; don't bundle the library, just probe at
     runtime.
   - Impact 5, effort 5, risk 4. Sources: S12, S25, S33, S43, S115, S116.

3. **Wear OS companion (C21)** — separate F-Droid package (`com.openlumen.wear`)
   that uses the Wearable Data Layer. Phone-side keeps the no-INTERNET
   posture. Wear tile = single Toggle button. ProtoLayout for
   responsive tile rendering (S117). No display tinting on the watch
   itself.

4. **Glance API widget rewrite (C123, promoted UC → Next in rev 4) —
   shipped 2026-05-17.** `ToggleWidget` and `PresetWidget` now use
   Glance runtime rendering and keep the existing `WidgetActionReceiver`
   action path. The old XML layouts remain as initial / picker previews,
   not the active runtime UI. Sources: S118, S193, S194, S00p.

5. **CVD matrix / LUT correction (C63 → C145 split)** — C63 shipped the
   matrix-capable slice: `LumenMatrix` has optional 3x3 RGB coefficients,
   SurfaceFlinger receives those off-diagonal terms, and Protan / Deutan /
   Tritan presets use DaltonLens-derived matrices with scalar fallbacks for
   engines that cannot consume a full matrix. The true per-pixel LUT /
   piecewise Brettel tritan completion is now C145. Sources: S13, S31,
   S119, S120, S285, S286, S00s.

6. **Driver compatibility learning (continued)** — extend
   `SurfaceFlinger.candidatesFor()` and `Kcal.CANDIDATE_BASES` as device
   reports arrive. Maintain `docs/device-matrix.md` per release.

7. **Preset system v2 polish** — preset-pack export/import (the JSON
   format is already extensible); user-renameable presets; sort presets
   alphabetically or by recency.

8. **Connected permission / overlay tests (C84, C91)** — emulator CI via
   `reactivecircus/android-emulator-runner` covers
   `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
   blocked-touch behavior, and SurfaceView pass-through.

9. **Research watchlist maintenance** — review `docs/research-watchlist.md`
   each release planning pass; add Android 17 behavior tracker, AAPM
   updates, AGP 10 timeline (mid-2026 opt-out removal).

## Later: v0.9.0 → post-v1.0

- **Android TV flavor (C22)** — leanback metadata, D-pad navigation,
  acknowledging that many TV firmwares ignore `ColorDisplayManager`.
- **AMOLED-aware content-aware dimming (C67)** — privacy-heavy; requires
  `MediaProjection` or accessibility access. After Android 17 AAPM
  (S88) the accessibility door is closed for us; `MediaProjection`
  shows a recording indicator that's terrible UX for an always-on
  filter. Likely stays Later indefinitely.
- **Partial-screen filters (C68)** — same per-app blocker as C11.
- **Pixel-grid AMOLED dimming (C89)** — Pixel Filter's idea; risky
  given Android 12+ untrusted-touch and overlay-alpha rules. Burn-in
  perception concern.
- **PWM-sensitive workflow guidance** — document the OLED Saver (S103)
  / Iris approach without claiming health benefits.
- **Multi-user / work-profile behavior (C81)** — polish after C11/C12.
- **Local diagnostics viewer with timeline filtering** — already
  shipped as C53; the filter-by-category/level stretch also shipped
  2026-05-17 (see C53 entry in Progress). Remaining stretch: timeline
  scrubbing (jump to range), text search within filtered subset.
- **Optional Play Store listing (C39)** — `specialUse` evidence pack
  is ready (C93); we just have not committed maintenance bandwidth.
  See [docs/play-fgs-evidence.md](docs/play-fgs-evidence.md).
- **System brightness write support (C86)** — confusing UX (two
  brightness sliders); probably reject.

## Under Consideration

- **Optional FusedLocationProvider flavor** — automatic coordinates
  are useful; Play Services is not F-Droid-clean. Could ship as a
  separate non-F-Droid build.
- **Shizuku backend (C06)** — the only remaining viable per-app path
  (see above). The decision to spike is on; the decision to ship is
  Next-tier conditional on the spike outcome.
- **EncryptedSharedPreferences successor (C121, new)** — we don't
  currently encrypt prefs; if we add an "encrypted profile bundle"
  export feature, the modern path is DataStore + Tink (S122). Decision
  deferred; document in `docs/threat-model.md`.
- **Reduce Bright Colors / system Extra Dim integration (C08)** —
  ADB/root/Shizuku spike needed for cross-OEM behavior.
- **PWM-sensitive overlay-at-high-brightness mode** — community demand
  is real (S80, S103, S107) but we should not make device-health
  claims. A documented "for PWM-sensitive users" preset bundle is
  feasible without medical wording.
## Research-Driven Additions

Research version: 2026-06-12 **rev 7**. Supplements rev 6 with a fresh
external research pass covering competitor landscape, Android 17 stable
readiness, Kotlin 2.4, Wear OS 7, community demand signals, and
dependency updates. New source IDs start at S291.

### P3 — Future platform and ecosystem

No active rev-7 engineering items remain. Blocked/non-repo items live in
`Roadmap_Blocked.md`.

### Rev 7 sources

- **S291**: AndroidX Core releases — `core-ktx` merged into `core` at 1.19.0 — https://developer.android.com/jetpack/androidx/releases/core
- **S292**: Kotlin 2.4.0 release notes — K1 removal, context parameters stable, Compose compiler flag deprecations — https://kotlinlang.org/docs/whatsnew24.html
- **S293**: KSP issue #2965 — Kotlin 2.4 upgrade tracking — https://github.com/google/ksp/issues/2965
- **S294**: AGP migration roadmap — AGP 10 legacy API deletion timeline — https://developer.android.com/build/releases/gradle-plugin-roadmap
- **S295**: awesome-shizuku — OpenLumen not listed — https://github.com/timschneeb/awesome-shizuku
- **S296**: awesome-android-kotlin-apps — curated Kotlin/Compose app list — https://github.com/androiddevnotes/awesome-android-kotlin-apps
- **S297**: AlternativeTo CF.lumen — only Red Moon as OSS Android option — https://alternativeto.net/software/cf-lumen
- **S298**: iPhone 17 Pro PWM toggle — validates PWM sensitivity as mainstream concern — https://www.notebookcheck.net/Apple-iPhone-17-Pro-without-OLED-flickering-PWM-can-be-turned-off.1110809.0.html
- **S299**: Android adaptive navigation guide — NavigationSuiteScaffold, WindowSizeClass, ListDetailPaneScaffold — https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- **S300**: f.lux features — backward alarm clock, movie mode, darkroom mode — https://justgetflux.com/
- **S301**: Wear OS 7 Wear Widgets — RemoteCompose replaces ProtoLayout Tiles — https://developer.android.com/training/wearables/widgets
- **S302**: Horologist DataLayer helpers — phone-watch sync abstraction — https://google.github.io/horologist/datalayer-helpers-guide/

## Rejected

- Network telemetry, remote crash reporting, remote config, analytics
  in the main app. Contradicts no-INTERNET and user trust.
  Sources: S00, S17, S60, S64.
- Ads, account login, cloud sync, paywalled core functionality.
  Contradicts F-Droid-first OSS positioning. Sources: S00, S24.
- Local HTTP / MQTT / Home Assistant control in the main app. Requires
  INTERNET, expands attack surface. Consider only as a separate
  companion package, not the main app. Sources: S00, S64.
- Philips Hue / IKEA TRÅDFRI / smart-light integrations in the main
  app. Useful commercially (Twilight has them); network permission
  conflicts with the default philosophy. Sources: S20, S87.
- Strong medical efficacy claims. The 2025/2026 evidence consensus
  shifted further toward "total luminance matters more than spectrum"
  (S99-S102); strict comfort/circadian-language is the only honest
  framing.
- General plugin ecosystem. Maintenance + security cost not justified
  for a small privileged display utility. Sources: S35, S36, S64.
- Continuous foreground-app polling every second. Red Moon documents
  it; privacy + battery cost is too high. Sources: S10, S64.
- **AccessibilityService as default backend (C79, moved from Under
  Consideration in rev 2 to Rejected in rev 3)** — Android 17 Advanced
  Protection Mode auto-revokes accessibility access for any app not
  flagged `isAccessibilityTool="true"`, and Google reviewer scrutiny
  on non-disability accessibility apps has tightened. OpenLumen does
  not qualify for the exemption. Sources: S88, S89, S90, S121.
- **UsageStatsManager-based foreground app detection (C80, moved from
  Under Consideration to Rejected)** — the trust posture cost
  (`PACKAGE_USAGE_STATS` is a sensitive special-access permission with
  full per-app launch history) is too high for the convenience benefit.
  Shizuku is the better path for the same outcome.
- **Browser/desktop companion (C88)** — dilutes Android focus.

## Candidate Inventory

The full 100-row inventory from rev 2 is preserved verbatim below with
**rev 3 deltas marked in the "Tier" column** (entries with a strikethrough
or "→" indicate a tier shift). New candidates start at C101.

### Tier-shift summary (rev 2 → rev 3)

| ID | Candidate | rev 2 Tier | rev 3 Tier | Reason |
|---|---|---|---|---|
| C79 | AccessibilityService as default backend | Under Consideration | Rejected | Android 17 AAPM auto-revokes; S88, S89, S90 |
| C80 | UsageStats app-state detection | Under Consideration | Rejected | Sensitive permission with much wider read surface than Shizuku |
| C82 | Android 16/API 36 readiness | Next | Now (expanded to Android 17) | Beta 4 shipped 2026-04-16; stable June 2026; S83, S96 |
| C83 | Compose screenshot tests | Now (effort 3) | Now (effort 2) | Compose Preview Screenshot Testing built-in (S97, S98) |
| C95 | AGP 9 migration spike | Next | Now | 9.0/9.1/9.2 stable; AGP 10 closes opt-out mid-2026 (S91-S93) |
| C96 | Hilt Compose artifact migration | Next | Now | New artifact live; deprecation notice on old (S94) |
| C28 | Direct Boot restore | Next (effort 4) | Next (effort 3) | DataStore APIs landed (S95) |

### Tier-shift summary (rev 3 → rev 4)

| ID | Candidate | rev 3 Tier | rev 4 Tier | Reason |
|---|---|---|---|---|
| C123 | Glance widget rewrite | Under Consideration | Next | Glance is stable since 1.0.0; 1.1.0 shipped 2024-06-12; removes the "alpha" blocker rev 3 cited. (S193, S194) |
| C101 | Compose Preview Screenshot Testing CI | Now (risk 1) | Now (risk 2) | Tool still `0.0.1-alphaXX` as of Apr 2026; bump risk and document pin policy. (S148, S149) |

### New candidates (rev 4)

| ID | Candidate | Category | Prev | Tier | I/E/R | Deps / effort sketch | Placement reason | Sources |
|---|---|---|---|---|---|---|---|---|
| C128 | FabricatedOverlay engine spike | engine/platform | emerging | ~~Under Consideration~~ → Later (rev 4.1) | 4/4/3 | Android 12+ `FabricatedOverlay` API via Shizuku-bound `IOverlayManager`; spike must verify framebuffer impact vs theme-only effect | **Rev 4.1**: tier downgraded — Shizuku-in-ADB cannot create FabricatedOverlays on Android 12L+ (S223). Becomes a root-tier candidate, not Shizuku-not-root. Merge into C06 root-tier scope. | S168, S163, S164, S222, S223 |
| C129 | OLED-aware gamma LUT clamp | engine/image quality | emerging | Later | 3/4/3 | Successor to C66 scalar clamp; per-channel 256-entry LUT to keep `(0,0,0)` truly off across the bottom of the dim range | Same bundled-LUT-vs-runtime-compute tradeoff as C63 | S174, S100, S160 |
| C130 | AAPM driver-report surface | docs/transparency/security | rare | Shipped 2026-05-17 | 3/1/1 | Reflection-gated `AdvancedProtectionManager` query in `DriverReport.kt`; driver report explains AAPM has no effect on OpenLumen | Pairs with rev 3's C79 / C80 rejection rationale; cheap transparency win. Shipped after rev 5. | S134, S135, S136, S267 |
| C131 | Eye Dropper integration on Android 17+ | UX/feature | emerging | Later | 2/2/1 | Custom-RGB picker on Home gains an optional "sample color" button that fires `OPEN_EYE_DROPPER` and consumes the returned color; hidden on pre-17 devices | Optional UX affordance; Android 17 device base is tiny in year one | S129, S139 |

### New candidates (rev 4.1 — second-pass code review + F-Droid + Compose)

| ID | Candidate | Category | Prev | Tier | I/E/R | Deps / effort sketch | Placement reason | Sources |
|---|---|---|---|---|---|---|---|---|
| C132 | `LumenService.applyMatrix` ramp-scheduling atomicity fix | correctness/concurrency | new | Shipped 2026-05-17 | 4/2/2 | Added a dedicated `rampMutex` around transition cancel/join, `lastApplied` read, and new ramp launch | HIGH severity race condition; two concurrent callers (prefs collector + sensor flow) could interleave the read-modify-write and produce zombie ramps. Shipped after rev 5. | S00 (code review), `LumenService.kt` |
| C133 | `LumenService.clearAndStop` cancel-and-join `transitionJob` | correctness | new | Shipped 2026-05-17 | 4/1/1 | `clearAndStop()` now cancels and joins the active transition before `engine?.clear()` | HIGH severity user-visible flicker: toggling off mid-ramp could leave the ramp applying steps over the cleared engine. Shipped after rev 5. | S00, `LumenService.kt` |
| C134 | `ColorDisplayManagerEngine.load` cache invalidation on partial-failure path | correctness/reliability | new | Shipped 2026-05-17 | 4/1/1 | Added `clearCache()` and call it on partial cache-hit failure and reflection failure | HIGH severity: a transient class-load failure on first call could doom the CDM engine for the lifetime of the process. Shipped after rev 5. | S00, `ColorDisplayManagerEngine.kt` |
| C135 | `OverlayEngine.installView` thread-safety with `apply`/`clear` | correctness/concurrency | new | Shipped 2026-05-17 | 3/2/2 | Added an internal `viewLock` around overlay install/apply/clear `View` and `WindowManager` mutations | HIGH severity race during engine swap with rapid toggling between Auto-CDM and Auto-Overlay. Shipped after rev 5. | S00, `OverlayEngine.kt` |
| C136 | Engine `apply` exit-code checking + cache invalidation on driver regression | reliability | new | Shipped 2026-05-17 | 4/2/1 | SurfaceFlinger invalidates `workingCode` on nonzero / "not found" apply-clear results; KCAL shell writes now `set -e` and invalidate `resolvedPaths` on nonzero exit | Med severity silent-failure surface after OTA / driver removal. Shipped after rev 5. | S00, `SurfaceFlingerEngine.kt`, `KcalEngine.kt` |
| C137 | `material-icons-extended` deprecation migration | UX/upgrade strategy | new | Shipped 2026-05-17 | 2/2/1 | Replaced the seven Compose Material icon call sites with local vector resources and removed `compose-material-icons-extended` from the version catalog / app dependencies | The artifact is deprecated as of late-2025/2026 (S229); self-hosting the tiny icon set avoids deprecation churn without waiting for the C95/C110 Compose train | S229, S00f |
| C138 | `PreferencesStore` import-size cap byte-correctness | input validation | new | Shipped 2026-05-17 | 3/1/1 | Added `readImportBytes()` to read at most `MAX_IMPORT_FILE_BYTES + 1` bytes at the `InputStream` level; reject if length exceeds the cap before decoding to chars | Med severity: `sb.length > MAX_IMPORT_BYTES` compared UTF-16 char count to a byte budget; high-BMP payloads could exceed the intended raw-byte cap | S00, S00e, `PreferencesStore.kt` |
| C139 | `PreferencesStore` import duplicate-name UI feedback | UX | new | Shipped 2026-05-17 | 2/2/1 | `importFrom` / `previewImport` now return `Result<ImportSummary>` with `droppedDuplicateNames`; the import dialog and result message surface duplicate profile names skipped by the last-write-wins sanitizer | Med (UX) severity: silent profile-name dedupe on import surprised users who imported a backup containing two profiles with the same name | S00, S00r |
| C140 | F-Droid initial submission (fdroiddata MR) | distribution | new | Now | 5/2/2 | Fork `gitlab.com/fdroid/fdroiddata`, create `metadata/com.openlumen.yml`, run `fdroid lint`, open MR labelled "New App". Allow 24-48h post-merge | OpenLumen has never been submitted (S203-S205 negative results across MR / RFP / app-search). Direct MR using the F-Droid Quick Start Guide (S206). Gated on C01 (real-device validation rows) and C36 (screenshots); C35 is now shipped. | S203, S204, S205, S206, S207, S210, S211, S00k |

### New candidates (rev 3)

| ID | Candidate | Category | Prev | Tier | I/E/R | Deps / effort sketch | Placement reason | Sources |
|---|---|---|---|---|---|---|---|---|
| C101 | Compose Preview Screenshot Testing CI wiring | testing | emerging | Shipped 2026-05-17 | 4/2/1 | Added `com.android.compose.screenshot` `0.0.1-alpha14`, an initial textless theme-token `@PreviewTest`, checked-in debug references, and a CI `validateDebugScreenshotTest` job | Unblocks C83 expansion efficiently | S97, S98, S148, S149, S269-S274 |
| C102 | DataStore Direct Boot APIs adoption | reliability/migration | emerging | Shipped 2026-05-17 | 4/3/2 | Added a typed device-protected DataStore mirror, `LOCKED_BOOT_COMPLETED` receiver, direct-boot-aware service path, and root-engine degradation to Overlay before unlock | Drops C28 effort and risk; remaining proof is device-matrix validation under C01 | S95, S280, S00m |
| C103 | Android 17 stable validation | platform/OS | table-stakes | Now | 4/3/2 | Per-engine smoke on Pixel running Android 17 stable | Stable lands June 2026 | S83, S84, S96 |
| C104 | Document AAPM accessibility revocation | docs/security | rare | Shipped 2026-05-17 | 3/1/1 | `docs/threat-model.md`, `docs/android-17-readiness.md`, and `docs/overlay-and-per-app-design.md` now call out why AAPM reinforces rejecting AccessibilityService for foreground-app convenience | Reinforces C79 rejection and Shizuku as only path | S88, S89, S90, S00h |
| C105 | SAW-app FGS-from-background fallback | reliability/UX | rare | Shipped 2026-05-17 | 4/2/2 | Added `LumenServiceStarter` classification for `ForegroundServiceStartNotAllowedException`; QS/widget user actions roll back stale enabled state and open the app when Android blocks a background FGS start | Android 15+ tightens the rules for SAW apps without a visible overlay window | S85, S131, S00g |
| C106 | BOOT_COMPLETED FGS verification | reliability | rare | Shipped 2026-05-17 | 3/1/1 | Added Android 14/15/16/17 boot-restore rows to wake/vitals audit and boot-restore notes to the device-matrix flow; real pass/fail evidence remains C01 | Ensures boot restore still works as the API tightens | S85 |
| C107 | FGS job runtime quota audit | performance | rare | Shipped 2026-05-17 (docs) | 2/2/2 | `docs/wake-and-vitals.md` now has a 'WorkManager / JobScheduler policy (C107)' section documenting: no WorkManager today; FGS runtime quotas (Android 16+) do not currently apply; the four constraints any future WorkManager integration must satisfy; expedited-work quota guidance. | S85 |
| C108 | (folded into C96) | — | — | — | — | — | — | — |
| C109 | (folded into C95) | — | — | — | — | — | — | — |
| C110 | Material 3 1.5.0 / Expressive components review | UX | emerging | Reviewed 2026-05-17 (hold) | 2/2/1 | `docs/deferred-candidates.md` now contains a 'Material 3 1.5.0 / Expressive components — C110' section reviewing the candidate set (`SplitButton` is the clearest fit for Driver tab; `FloatingToolbar`, `ButtonGroup` deferred). Decision: continue to hold; re-review when `material3-expressive` reaches `1.5.0-stable` or candidate list grows past two useful components. | S123, S124, S227 |
| C111 | BAL hardening readiness | platform/OS | rare | Shipped 2026-05-17 | 3/1/1 | Audited for `IntentSender`, `ActivityOptions`, and `MODE_BACKGROUND_ACTIVITY_START_*`; no migration call sites exist today | Android 17 deprecation | S84, S128, S137, S00d |
| C112 | (n/a — no network, unaffected by CT/ECH) | — | — | — | — | — | — | — |
| C113 | (n/a — same) | — | — | — | — | — | — | — |
| C114 | Fine-grain dim precision for PWM users | UX | rare | Shipped 2026-05-17 | 3/2/2 | Inline ±0.5% nudge buttons next to the Home tab dim slider; new `home_dim_value_precise` string shows one-decimal percentage; `DIM_FINE_STEP` constant centralizes the step. PWM-sensitive users can now land at half-percent values in the 0-10% region without fighting slider-thumb precision. | S80, S103, S107 |
| C115 | "Filter green light too" (Red Moon #353) | UX | rare | Shipped 2026-05-17 (docs) | 2/2/2 | `docs/health-evidence.md` now documents that the existing Kelvin slider already suppresses green at low temperatures (~17/255 at 1500 K) and explains why we don't add a separate G-channel control — the Kelvin axis is the physically-grounded one. | S86 |
| C116 | "Don't resume after restart if paused" docs | docs | rare | Shipped 2026-05-17 | 2/1/1 | `BootReceiver` already restores only when persisted `enabled = true`; documented the paused reboot behavior in troubleshooting | Red Moon users currently lack this | S86, `BootReceiver.kt`, `docs/troubleshooting.md` |
| C117 | Root-mode apply-on-first-emission verification | reliability | rare | Shipped 2026-05-17 | 3/1/1 | `ApplyDecisionGate` resets on engine switch and has JVM coverage proving the same target dispatches again after reset; device matrix now calls out SF/KCAL first-emission smoke evidence | Red Moon has a known bug here; we should not | S86, S00i |
| C118 | GrapheneOS / lockdown-ROM overlay coverage | platform/OS | rare | Next | 3/3/3 | Test overlay z-order against system shade on GrapheneOS | Red Moon issue #347 indicates real OEM-divergence risk | S86 |
| C119 | (folded into C35) | — | — | — | — | — | — | — |
| C120 | VCS info determinism in reproducibility doc | distribution/docs | rare | Shipped 2026-05-17 | 2/1/1 | Release builds disable `vcsInfo.include`; `docs/reproducible-build.md` documents the AGP `version-control-info.textproto` handling and external provenance path | Known F-Droid reproducibility friction | S112, S156, S268 |
| C121 | Tink + Proto DataStore replacement of EncryptedSharedPreferences (if we ever encrypt) | security | rare | Documented 2026-05-17 (hold) | 2/3/2 | `docs/threat-model.md` MASVS-CRYPTO section now documents the deprecation of `EncryptedSharedPreferences`, the four-step Tink + Keystore + Proto DataStore migration path, and the explicit "defer until a field actually needs at-rest encryption" decision. Future contributors won't reach for the deprecated API. | S122 |
| C122 | Roborazzi gold-image CI | testing | rare | Shipped 2026-05-17 | 3/3/2 | Added Roborazzi 1.60.0 / Robolectric 4.16.1, a textless theme-token JVM screenshot test, checked-in PNG baselines, and CI `:app:verifyRoborazziDebug` | Belt-and-braces snapshot coverage alongside Compose Preview Screenshot Testing | S97, S98, S150, S151, S00q |
| C123 | Glance API widget rewrite | mobile | emerging | Shipped 2026-05-17 | 3/3/2 | Replaced runtime RemoteViews providers with Glance receivers on `androidx.glance:glance-appwidget:1.1.1`; kept XML layouts as launcher previews and preserved the existing widget broadcast actions | Cleaner widget code on the stable Glance line; strict dependency verification refreshed and passed | S118, S193, S194, S00p |
| C124 | Hilt 2.56+ minimum | upgrade strategy | emerging | Shipped 2026-05-17 | 3/1/1 | Bumped Dagger/Hilt to 2.59.2 with KSP 2.3.8 as part of the AGP 9 train | Pairs with C96 | S94, S240, S241, S269 |
| C125 | Twilight 14.25 feature scan | research | emerging | Later | 2/1/1 | Periodic check of Twilight's per-app/Wear/Chromebook frontier | Trend signal, not parity goal | S87 |
| C126 | Stronger sleep-evidence disclaimer | docs/licensing | rare | Shipped 2026-05-17 | 3/1/1 | `docs/health-evidence.md` now has the 2025/2026 consensus-shift note plus S99-S102 and S158-S162 source refresh | Consensus shift demands explicit acknowledgement | S99-S102, S158-S162 |
| C127 | Perceived-luminance reduction indicator | UX/data | rare | Shipped 2026-05-17 | 3/2/1 | Added `MatrixPreview.perceivedLuminanceReduction()` and Home-tab copy for "Perceived brightness reduced by N%" alongside blue suppression | Aligns the UI metric with current sleep-evidence consensus | S99-S102, S00n |

### Hardening (post-rev-2 audit) — landed on `main`

These are not new roadmap candidates; they are correctness fixes from the
2026-05-17 audit pass that ship with v0.5.0. Listed here so the
"Progress" section above stays focused on candidate IDs:

- Schedule.kt Solar mode honors caller `now` (was `LocalDate.now(zoneId)`)
- SolarCalculator `Polar` enum + date-snap fix for non-UTC zones
- LumenService mid-ramp lerp from displayed value (not stale target)
- PreferencesStore nested profile-snapshot sanitization
- LightSensorAdapter `DROP_OLDEST` buffer + raw-sample validation
- OverlayEngine cutout coverage + main-thread `installView`
- KcalEngine optional `kcal_min` probe
- Su.runShell drainer thread to prevent script-output deadlock
- LumenService.observePreferences resilience (per-emission try/catch)
- LumenService.ACTION_SET_PRESET registry validation
- LumenTileService.refreshTile try/catch around `updateTile()`
- OpenLumenViewModel.refreshProbes invalidates `Su.cachedAvailable`
- AboutScreen.describeDiff covers contrast/AMOLED/lux/sunset/sunrise

### Inventory carryover (rev 2 verbatim)

The 100 candidates introduced in rev 2 stay in scope. Their full I/E/R,
deps sketch, and placement reasons are documented in the rev 2 history
preserved in `docs/research-watchlist.md` and in the design docs under
`docs/`. Only the candidates whose tier shifted in rev 3 are re-listed
above; the others continue with their rev 2 placement.

## Source Appendix

### Local evidence

- **S00**: Local repo reconnaissance on 2026-05-17: `README.md`,
  `CHANGELOG.md`, prior `ROADMAP.md` (rev 2), `LICENSE`,
  `.github/workflows/*`, `gradle/libs.versions.toml`, manifests, full
  Kotlin source tree, tests, `docs/**`, last 30 commits.
- **S00b**: 2026-05-17 in-tree audit hardening pass — see "Hardening"
  section above.
- **S00o**: 2026-05-17 C48 dependency-verification implementation —
  `gradle/verification-metadata.xml` was generated after C95/C144 and
  strict verification passed from `C:\Users\Xray\OpenLumen-agp9-verify`
  across `:app:assembleDebug`, `:app:lintDebug`,
  `:app:validateDebugScreenshotTest`, `:app:testDebugUnitTest`,
  `:core-engine:test`, `:core-schedule:test`, and `:core-prefs:test`.
- **S00p**: 2026-05-17 C123 Glance widget rewrite — `ToggleWidget` and
  `PresetWidget` now use `GlanceAppWidgetReceiver` / `GlanceAppWidget`
  with `androidx.glance:glance-appwidget:1.1.1`; runtime widget actions
  still route through `WidgetActionReceiver`, dependency metadata was
  refreshed, and strict verification passed from the local mirror. Local
  emulator provisioning for widget screenshots remained blocked because
  x86_64 Android emulator images require hardware acceleration on this
  host.
- **S00q**: 2026-05-17 C122 Roborazzi implementation —
  `ThemeTokenRoborazziTest.kt` records light/dark theme-token PNG
  baselines under `app/src/test/roborazzi/`; CI now runs
  `:app:verifyRoborazziDebug`; `gradle/verification-metadata.xml` was
  refreshed for Roborazzi/Robolectric dependencies; strict verification
  passed from `C:\Users\Xray\OpenLumen-agp9-verify`.
- **S00r**: 2026-05-17 C139 import duplicate-name feedback —
  `PreferencesStore.importFrom()` and `previewImport()` return
  `ImportSummary`; duplicate profile names dropped by the sanitizer are
  reported in `AboutScreen`'s import preview and in the post-import result
  message; `ProfilesTest` covers duplicate-name detection.
- **S00s**: 2026-05-17 C63 matrix-capable CVD preset slice —
  `LumenMatrix` carries optional 3x3 RGB coefficients, SurfaceFlinger
  applies column-major off-diagonal terms, Protan/Deutan/Tritan presets include
  DaltonLens-derived matrices plus scalar fallbacks, `MatrixDto` preserves
  the fields for import/direct-boot mirrors, and focused strict Gradle
  plus full strict Gradle verification passed from
  `C:\Users\Xray\OpenLumen-agp9-verify`.

### External URLs (rev 2 — preserved)

- **S10**: Red Moon GitHub — https://github.com/LibreShift/red-moon
- **S11**: Red Moon F-Droid — https://f-droid.org/en/packages/com.jmstudios.redmoon/
- **S12**: Red Moon open enhancement issues — https://github.com/LibreShift/red-moon/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement
- **S13**: Red Moon closed enhancement issues — https://github.com/LibreShift/red-moon/issues?q=is%3Aissue+is%3Aclosed+label%3Aenhancement
- **S14**: Shades — https://github.com/cngu/shades
- **S15**: Night-Light — https://github.com/farmerbb/Night-Light
- **S16**: DimTV — https://github.com/MarshMeadow/DimTV
- **S17**: Low Brightness — https://github.com/MihaiCristianCondrea/Low-Brightness-for-Android
- **S18**: Screen Filter — https://github.com/tranleduy2000/screenfilter
- **S19**: Eye-Rest — https://github.com/Dzhuneyt/android-app-eye-rest-blue-light-filter
- **S20**: Twilight on Google Play — https://play.google.com/store/apps/details?id=com.urbandroid.lux
- **S21**: TechCrunch on f.lux Android — https://techcrunch.com/2016/03/15/popular-blue-light-reducing-app-f-lux-arrives-on-android/
- **S22**: Android Police on f.lux Android beta — https://www.androidpolice.com/2016/02/19/popular-display-tweaking-app-f-lux-is-coming-to-android-available-now-in-beta-root-required/
- **S23**: Iris — https://iristech.co/iris/
- **S24**: CareUEyes pricing — https://care-eyes.com/buy.html
- **S25**: AOSP Night Light implementation — https://source.android.com/docs/core/display/night-light
- **S26**: Android 12 untrusted touch events — https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
- **S27**: Android 12 exact alarm behavior — https://developer.android.com/about/versions/12/behavior-changes-12#exact-alarm-permission
- **S28**: Android notification runtime permission — https://developer.android.com/develop/ui/views/notifications/notification-permission
- **S29**: Foreground service types, special use — https://developer.android.com/develop/background-work/services/fgs/service-types
- **S30**: Android Storage Access Framework — https://developer.android.com/training/data-storage/shared/documents-files
- **S31**: Android accessibility testing — https://developer.android.com/guide/topics/ui/accessibility/testing
- **S32**: `HIDE_OVERLAY_WINDOWS` — https://developer.android.com/reference/android/Manifest.permission#HIDE_OVERLAY_WINDOWS
- **S33**: Shizuku setup guide — https://shizuku.rikka.app/guide/setup/
- **S34**: Redshift — https://github.com/sharpbracket/redshift
- **S35**: Hyprshade — https://github.com/loqusion/hyprshade
- **S36**: sunsetr — https://github.com/psi4j/sunsetr
- **S37**: wl-gammarelay-rs — https://github.com/MaxVerevkin/wl-gammarelay-rs
- **S38**: wluma — https://github.com/max-baz/wluma
- **S39**: Lunar — https://github.com/alin23/Lunar
- **S40**: ScreenDimmer — https://github.com/datbnh/ScreenDimmer
- **S41**: Reddit screen dimming apps — https://www.reddit.com/r/androidapps/comments/1emudmo
- **S42**: Reddit overlay apps interfering with touch — https://www.reddit.com/r/lgv20/comments/d0r4kb
- **S43**: Reddit root vs overlay quality — https://www.reddit.com/r/androidapps/comments/lk8sbv
- **S44**: Reddit dim past limits — https://www.reddit.com/r/AndroidHelp/comments/1jhzmi1
- **S45**: Blue-light blocking glasses systematic review — https://pmc.ncbi.nlm.nih.gov/articles/PMC12668929/
- **S46**: Circadian lighting consensus — https://www.frontiersin.org/journals/photonics/articles/10.3389/fphot.2023.1272934
- **S47**: Blue-light exposure intervention review — https://academic.oup.com/sleepadvances/article/doi/10.1093/sleepadvances/zpaa002/5851240
- **S48**: OpenLumen GitHub remote — https://github.com/SysAdminDoc/OpenLumen
- **S49**: AGP Maven metadata — https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- **S50**: Kotlin Android Gradle plugin metadata — https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml
- **S51**: Compose BOM metadata — https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- **S52**: Activity Compose metadata — https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml
- **S53**: Lifecycle runtime metadata — https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml
- **S54**: Navigation Compose metadata — https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml
- **S55**: DataStore preferences metadata — https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml
- **S56**: Compose Material3 metadata — https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml
- **S57**: Hilt metadata — https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml
- **S58**: kotlinx.serialization JSON metadata — https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml
- **S59**: kotlinx.coroutines Android metadata — https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml
- **S60**: F-Droid build metadata reference — https://f-droid.org/docs/Build_Metadata_Reference/
- **S61**: F-Droid reproducible builds — https://f-droid.org/docs/Reproducible_Builds/
- **S62**: GitHub Dependabot version updates — https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/about-dependabot-version-updates
- **S63**: GitHub artifact attestations — https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations/using-artifact-attestations-to-establish-provenance-for-builds
- **S64**: OWASP MASVS — https://mas.owasp.org/MASVS/
- **S65**: Android vitals — https://developer.android.com/topic/performance/vitals
- **S66**: Android DataStore — https://developer.android.com/topic/libraries/architecture/datastore
- **S67**: OWASP MASTG overlay attacks — https://mas.owasp.org/MASTG/knowledge/android/MASVS-PLATFORM/MASTG-KNOW-0022/
- **S68**: OWASP MASTG testing for overlay attacks — https://mas.owasp.org/MASTG/tests/android/MASVS-PLATFORM/MASTG-TEST-0035/
- **S69**: Pixel Filter GitHub — https://github.com/pelya/screen-dimmer-pixel-filter
- **S70**: Pixel Filter F-Droid mirror — https://jans23.gitlab.io/fdroid-website/en/packages/screen.dimmer.pixelfilter/
- **S71**: Screen Dimming GitHub — https://github.com/Darexsh/Screen_Dimming
- **S72**: Ecosyste.ms blue-light-filter topic index — https://repos.ecosyste.ms/topics/blue-light-filter
- **S73**: Stack Overflow on SYSTEM_ALERT_WINDOW + SurfaceView — https://stackoverflow.com/questions/76411479/android-11-system-alert-window-behaviour-changes-with-surfaceview
- **S74**: Google Play foreground service requirements — https://support.google.com/googleplay/android-developer/answer/13392821
- **S75**: AGP 9 release notes — https://developer.android.com/build/releases/agp-9-0-0-release-notes
- **S76**: AndroidX Hilt release notes — https://developer.android.com/jetpack/androidx/releases/hilt
- **S77**: GHSA for protobuf-java CVE-2024-7254 — https://github.com/advisories/GHSA-735f-pc8j-v9w8
- **S78**: HN f.lux discussion — https://news.ycombinator.com/item?id=30626803
- **S79**: Reddit dynamic blue-light filter request — https://www.reddit.com/r/androidapps/comments/1rxmi3v/looking_for_a_dynamic_blue_light_filter/
- **S80**: Reddit PWM-sensitive overlay discussion — https://www.reddit.com/r/PWM_Sensitive/comments/1obqbsz/does_using_oled_screen_at_100_brightness_with_an/
- **S81**: dim_overlay_app GitHub — https://github.com/Ayuj-Mondal/dim_overlay_app
- **S82**: SwingShift GitHub — https://github.com/alexwelsby/swingshift

### External URLs (rev 3 — new)

- **S83**: Android 17 release notes — https://developer.android.com/about/versions/17/release-notes
- **S84**: Android 17 behavior changes for apps targeting 17 — https://developer.android.com/about/versions/17/behavior-changes-17
- **S85**: Changes to foreground services (Android 14/15/16) — https://developer.android.com/develop/background-work/services/fgs/changes
- **S86**: Red Moon issue queue 2026 snapshot (issues #339, #340, #342, #343, #346, #347, #348, #349, #351, #352, #353, #354) — https://github.com/LibreShift/red-moon/issues
- **S87**: Twilight v14.25 Feb 2026 changelog (Urbandroid Play page + APKMirror release) — https://play.google.com/store/apps/details?id=com.urbandroid.lux ; https://www.apkmirror.com/apk/urbandroid/twilight/
- **S88**: Android 17 Beta 2 — Advanced Protection Mode blocks accessibility API abuse — https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/
- **S89**: The Hacker News — Android 17 blocks non-accessibility apps from accessibility API — https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html
- **S90**: Help Net Security — Google limits Android accessibility API to curb malware abuse — https://www.helpnetsecurity.com/2026/03/19/google-android-accessibility-api-restrictions/
- **S91**: AGP 9.0.1 release notes (January 2026) — https://developer.android.com/build/releases/agp-9-0-0-release-notes
- **S92**: AGP 9.1.0/9.1.1 release notes (April 2026) — https://developer.android.com/build/releases/agp-9-1-0-release-notes
- **S93**: AGP 9.2.0 release notes (April 2026) — https://developer.android.com/build/releases/agp-9-2-0-release-notes
- **S94**: AndroidX Hilt release notes — Compose `hiltViewModel()` artifact move to `hilt-lifecycle-viewmodel-compose` — https://developer.android.com/jetpack/androidx/releases/hilt
- **S95**: DataStore release notes — `createInDeviceProtectedStorage()` and `deviceProtectedDataStore()` — https://developer.android.com/jetpack/androidx/releases/datastore
- **S96**: Android Developers Blog — The Fourth Beta of Android 17 — https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html
- **S97**: Compose Preview Screenshot Testing — https://developer.android.com/studio/preview/compose-screenshot-testing
- **S98**: Roborazzi GitHub — https://github.com/takahirom/roborazzi
- **S99**: HN — Blue light filters don't work, total luminance is better — https://news.ycombinator.com/item?id=47091606
- **S100**: 2026 Scientific Reports — Home lighting, blue-light filtering, melatonin suppression — https://www.nature.com/articles/s41598-025-29882-7
- **S101**: 2025 PubMed — Optimizing blue-blocking glasses for sleep and circadian health — https://pubmed.ncbi.nlm.nih.gov/40728371/
- **S102**: 2024 Sleep — Melanopic irradiance defines display-light impact on sleep latency — https://pubmed.ncbi.nlm.nih.gov/36854795/
- **S103**: OLED Saver / Screen Dimmer (dev.rewhex) Play listing — https://play.google.com/store/apps/details?id=dev.rewhex.screendimmer
- **S104**: Lunar fyi (macOS adaptive brightness) — https://lunar.fyi/
- **S105**: Hyprshade README — https://github.com/loqusion/hyprshade/blob/main/README.md
- **S106**: Sunsetr troubleshooting guide — https://psi4j.github.io/sunsetr/troubleshooting.html
- **S107**: Android Central — What is PWM dimming, alternatives — https://www.androidcentral.com/phones/what-is-pwm-display-flicker-tips-and-tricks
- **S108**: OWASP MASTG v2 overlay-attack overhaul (issue tracker note) — https://github.com/OWASP/masvs/issues/263
- **S109**: CycloneDX Gradle plugin — https://github.com/CycloneDX/cyclonedx-gradle-plugin
- **S110**: Anchore Syft (SBOM generator) — https://github.com/anchore/syft
- **S111**: F-Droid translation/localization (70% threshold) — https://f-droid.org/docs/Translation_and_Localization/
- **S112**: F-Droid reproducible builds + AGP `version-control-info.textproto` — https://f-droid.org/docs/Reproducible_Builds/
- **S113**: ReactiveCircus android-emulator-runner — https://github.com/ReactiveCircus/android-emulator-runner
- **S114**: Anchore Grype (vulnerability scanner) — https://github.com/anchore/grype
- **S115**: RikkaApps Shizuku — https://github.com/RikkaApps/Shizuku
- **S116**: awesome-shizuku list — https://github.com/timschneeb/awesome-shizuku
- **S117**: Wear OS Tiles documentation — https://developer.android.com/training/wearables/tiles
- **S118**: Jetpack Glance — https://developer.android.com/develop/ui/compose/glance
- **S119**: DaltonLens CVD simulation review — https://daltonlens.org/opensource-cvd-simulation/
- **S120**: DaltonLens SVG filters for CVD simulation — https://daltonlens.org/cvd-simulation-svg-filters/
- **S285**: DaltonLens `libDaltonLens.c` precomputed Viénot / Brettel matrices — https://raw.githubusercontent.com/DaltonLens/libDaltonLens/master/libDaltonLens.c
- **S286**: AOSP `SurfaceFlinger.cpp` column-major color-transform transaction handling — https://android.googlesource.com/platform/frameworks/native/+/d40036791bd882431bafb7e5d3401a1661c6e459/services/surfaceflinger/SurfaceFlinger.cpp
- **S121**: SecurityAffairs — AAPM in Android 17 prevents accessibility misuse — https://securityaffairs.com/189497/security/advanced-protection-mode-in-android-17-prevents-apps-from-misusing-accessibility-services.html
- **S122**: EncryptedSharedPreferences deprecation + Tink/Proto DataStore migration — https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a
- **S123**: Compose Material 3 release notes — https://developer.android.com/jetpack/androidx/releases/compose-material3
- **S124**: Material 3 Expressive in Compose — https://medium.com/@expertappdevs/android-ui-redesign-with-jetpack-compose-material-3-expressive-0c52e85e16af
- **S125**: ComposablePreviewScanner — https://github.com/sergio-sastre/ComposablePreviewScanner

### External URLs (rev 4 — new, primary-source refresh)

Source list grouped by topic. Full triage in
[.ai/research/2026-05-17/SOURCE_REGISTER.md](.ai/research/2026-05-17/SOURCE_REGISTER.md).

Android 17 platform (release timing, behavior changes, FGS, AAPM, BAL):

- **S126**: Android 17 Beta 4 announcement (Android Developers Blog,
  2026-04-16) — https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html
- **S127**: Android 17 release notes — https://developer.android.com/about/versions/17/release-notes
- **S128**: Behavior changes — apps targeting Android 17 — https://developer.android.com/about/versions/17/behavior-changes-17
- **S129**: Android 17 features and APIs — https://developer.android.com/about/versions/17/features
- **S130**: Changes to foreground services — https://developer.android.com/develop/background-work/services/fgs/changes
- **S131**: FGS background-start restrictions — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- **S132**: Foreground service types — https://developer.android.com/develop/background-work/services/fgs/service-types
- **S133**: Background audio hardening (Android 17) — https://developer.android.com/about/versions/17/changes/bg-audio
- **S134**: `AdvancedProtectionManager` API reference — https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager
- **S135**: Advanced Protection Mode landing page — https://developer.android.com/privacy-and-security/advanced-protection-mode
- **S136**: AndroidPolice — Android 17 Beta 2 AAPM accessibility auto-revocation deep-dive — https://www.androidpolice.com/advanced-protection-mode-android-17-beta-accessibility/
- **S137**: Background activity launch restrictions — https://developer.android.com/guide/components/activities/background-starts
- **S138**: AOSP Night Light implementation guide — https://source.android.com/docs/core/display/night-light
- **S139**: Android 17 Eye Dropper API overview — https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16

AGP 9 / 10:

- **S140**: AGP 9.0.1 release notes (Jan 2026) — https://developer.android.com/build/releases/agp-9-0-0-release-notes
- **S141**: AGP 9.1.1 release notes (Apr 2026) — https://developer.android.com/build/releases/agp-9-1-0-release-notes
- **S142**: AGP 9.2.0 release notes (Apr 2026) — https://developer.android.com/build/releases/agp-9-2-0-release-notes
- **S143**: AGP DSL/API migration timeline — https://developer.android.com/build/releases/gradle-plugin-roadmap

AndroidX Hilt artifact move:

- **S144**: AndroidX Hilt releases page — https://developer.android.com/jetpack/androidx/releases/hilt
- **S145**: `hilt-lifecycle-viewmodel-compose` on Maven Central — https://mvnrepository.com/artifact/androidx.hilt/hilt-lifecycle-viewmodel-compose/

AndroidX DataStore Direct Boot APIs:

- **S146**: DataStore releases page — https://developer.android.com/jetpack/androidx/releases/datastore
- **S147**: DataStore architecture guide — https://developer.android.com/topic/libraries/architecture/datastore

Compose screenshot testing:

- **S148**: Compose Preview Screenshot Testing guide — https://developer.android.com/studio/preview/compose-screenshot-testing
- **S149**: Compose Preview Screenshot Testing release notes — https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes
- **S150**: Roborazzi GitHub — https://github.com/takahirom/roborazzi
- **S151**: Roborazzi releases — https://github.com/takahirom/roborazzi/releases
- **S152**: Paparazzi GitHub — https://github.com/cashapp/paparazzi
- **S153**: Paparazzi changelog — https://cashapp.github.io/paparazzi/changelog/

F-Droid:

- **S154**: F-Droid Reproducible Builds docs — https://f-droid.org/docs/Reproducible_Builds/
- **S155**: "Making reproducible builds visible" (F-Droid blog, 2025-05-21) — https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html
- **S156**: F-Droid forum — removing `META-INF/version-control-info.textproto` — https://forum.f-droid.org/t/how-can-i-prevent-version-control-info-textproto-from-being-included-in-my-apk/33196
- **S157**: F-Droid Translation and Localization policy — https://f-droid.org/docs/Translation_and_Localization/

Sleep / circadian (2025-2026 broadening):

- **S158**: Frontiers in Neurology — Efficacy of blue-light blocking glasses on actigraphic sleep outcomes: systematic review and meta-analysis (2025) — https://www.frontiersin.org/journals/neurology/articles/10.3389/fneur.2025.1699303/full
- **S159**: Nature Scientific Reports — Home lighting, blue-light filtering, melatonin suppression (2025) — https://www.nature.com/articles/s41598-025-29882-7
- **S160**: medRxiv — Melanopic Equivalent Daylight Illuminance and sleep regulation (Oct 2025) — https://www.medrxiv.org/content/10.1101/2025.10.21.25338466v1.full
- **S161**: Cochrane — Blue-light-filtering spectacles probably make no difference to eye strain or sleep — https://www.cochrane.org/about-us/news/blue-light-filtering-spectacles-probably-make-no-difference-eye-strain-eye-health-or-sleep
- **S162**: SAGE Journals — Blue-light-filtering spectacle lenses: updated review (2026) — https://journals.sagepub.com/doi/10.1177/25158414251412798

Shizuku ecosystem 2026 (refreshed):

- **S163**: Shizuku releases — https://github.com/RikkaApps/Shizuku/releases
- **S164**: awesome-shizuku — https://github.com/timschneeb/awesome-shizuku
- **S165**: AndroidAuthority — "10 awesome Shizuku apps" — https://www.androidauthority.com/best-shizuku-apps-android-3659353/

Competitor sweep (new 2025-2026 entrants and cross-platform inspirations):

- **S166**: EcoDimmer — https://github.com/cartman-156/EcoDimmer
- **S167**: Grayscaler — https://github.com/C10udburst/Grayscaler
- **S168**: ColorBlendr — https://github.com/Mahmud0808/ColorBlendr
- **S169**: Adaptive Theme — https://github.com/xLexip/Adaptive-Theme
- **S170**: sunsetr — https://github.com/psi4j/sunsetr
- **S171**: hyprsunset — https://github.com/hyprwm/hyprsunset
- **S172**: wl-gammarelay-rs — https://github.com/MaxVerevkin/wl-gammarelay-rs
- **S173**: nerdshade — https://github.com/sstark/nerdshade
- **S174**: cosmos (Codeberg) — https://codeberg.org/ext0l/cosmos
- **S175**: Solace (macOS) — https://www.theodorehq.com/solace/
- **S176**: Shifty (macOS) — https://github.com/thompsonate/Shifty
- **S177**: LightBulb v2 (Windows) — https://github.com/Tyrrrz/LightBulb
- **S178**: Nocturnal (macOS, archived) — https://github.com/joshjon/nocturnal
- **S179**: LSFG-Android — https://github.com/FrankBarretta/LSFG-Android
- **S180**: DarQ — https://github.com/KieronQuinn/DarQ
- **S181**: RootlessJamesDSP — https://github.com/timschneeb/RootlessJamesDSP
- **S182**: TvOverlay — https://github.com/gugutab/TvOverlay
- **S183**: GitHub topic — blue-light-filter — https://github.com/topics/blue-light-filter
- **S184**: GitHub topic — screen-dimmer — https://github.com/topics/screen-dimmer

PWM (2025-2026 secondary):

- **S185**: AndroidCentral — "My phone is making me sick" (PWM) — https://www.androidcentral.com/phones/my-phone-is-making-me-sick-and-im-not-alone
- **S186**: AndroidCentral — Best phones for PWM/Flicker sensitive (2026) — https://www.androidcentral.com/phones/best-phones-for-pwm-flicker-sensitive
- **S187**: AndroidCentral — What is PWM dimming, and what are the alternatives? — https://www.androidcentral.com/phones/what-is-pwm-display-flicker-tips-and-tricks

OWASP MASVS / MASTG 2025-2026:

- **S188**: OWASP MASTG-KNOW-0022 — Overlay Attacks — https://mas.owasp.org/MASTG-KNOW-0022/
- **S189**: OWASP MASTG-TEST-0035 — Testing for Overlay Attacks — https://mas.owasp.org/MASTG-TEST-0035/
- **S190**: OWASP MASWE-0056 — Tapjacking — https://mas.owasp.org/MASWE-0056/
- **S191**: OWASP MASTG releases — https://github.com/OWASP/mastg/releases
- **S192**: OWASP MASVS v2.1.0 release notes — https://github.com/OWASP/masvs/releases/tag/v2.1.0

Glance widgets (stable since 1.0.0):

- **S193**: AndroidX Glance releases page — https://developer.android.com/jetpack/androidx/releases/glance
- **S194**: Jetpack Glance overview — https://developer.android.com/develop/ui/compose/glance

Red Moon / NightLight current activity (refreshed):

- **S195**: LibreShift/red-moon repository — https://github.com/LibreShift/red-moon
- **S196**: Red Moon issue tracker 2026 sample — https://github.com/LibreShift/red-moon/issues
- **S197**: Red Moon issue #281 — maintenance posture — https://github.com/LibreShift/red-moon/issues/281
- **S198**: Twilight on APKPure (2026 changelog) — https://apkpure.com/twilight-blue-light-filter/com.urbandroid.lux
- **S199**: corphish/NightLight — https://github.com/corphish/NightLight
- **S200**: farmerbb/Night-Light — https://github.com/farmerbb/Night-Light
- **S201**: cngu/shades — https://github.com/cngu/shades
- **S202**: Android 17 Eye Dropper API (refreshed pointer) — https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16

### External URLs (rev 4.1 — second-pass research)

Twenty-seven new sources from the F-Droid submission status agent, the
Shizuku integration patterns agent, and the Compose / Material 3 /
AGP 9 migration target agent. Full triage in
[.ai/research/2026-05-17/SOURCE_REGISTER.md](.ai/research/2026-05-17/SOURCE_REGISTER.md)
and analysis in
[.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md](.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md).

F-Droid submission status / process:

- **S203**: F-Droid `fdroiddata` MRs (zero matches for "openlumen") — https://gitlab.com/fdroid/fdroiddata/-/merge_requests
- **S204**: F-Droid RFP issues (zero matches) — https://gitlab.com/fdroid/rfp/-/issues
- **S205**: f-droid.org app search — https://search.f-droid.org/?q=openlumen&lang=en
- **S206**: F-Droid Quick Start Guide for new apps — https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- **S207**: F-Droid Translation and Localization (70% rule) — https://f-droid.org/docs/Translation_and_Localization/
- **S208**: AGP 9.2.0 release notes (April 2026) — https://developer.android.com/build/releases/agp-9-2-0-release-notes
- **S209**: Google Play target-SDK requirements — https://developer.android.com/google/play/requirements/target-sdk
- **S210**: F-Droid Anti-Features list — https://f-droid.org/docs/Anti-Features/
- **S211**: F-Droid TWIF April 2026 — https://f-droid.org/en/2026/04/03/twif.html

Shizuku integration patterns 2026:

- **S212**: RikkaApps/Shizuku-API — https://github.com/RikkaApps/Shizuku-API
- **S213**: RikkaApps/Shizuku — https://github.com/RikkaApps/Shizuku
- **S214**: ShizukuActivityManager (transaction-code lookup pattern) — https://github.com/kzaemrio/ShizukuActivityManager
- **S215**: Android-FPS-Watcher (ITaskStackListener pattern) — https://github.com/WuDi-ZhanShen/Android-FPS-Watcher
- **S216**: AOSP `ITaskStackListener.aidl` — https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/ITaskStackListener.aidl
- **S217**: AOSP `IActivityManager.aidl` (mirror) — https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/IActivityManager.aidl
- **S218**: Grayscaler (refresh of S167) — https://github.com/C10udburst/Grayscaler
- **S219**: ColorBlendr (refresh of S168) — https://github.com/Mahmud0808/ColorBlendr
- **S220**: LSFG-Android (refresh of S179) — https://github.com/FrankBarretta/LSFG-Android
- **S221**: awesome-shizuku — https://github.com/timschneeb/awesome-shizuku

FabricatedOverlay 12L+ constraint:

- **S222**: AOSP `FabricatedOverlay` API reference — https://developer.android.com/reference/android/content/om/FabricatedOverlay
- **S223**: zacharee/FabricateOverlay (documents the 12L+ shell-user block) — https://github.com/zacharee/FabricateOverlay

Shizuku security advisories (negative result):

- **S224**: GitHub Advisory Database — zero entries for "shizuku" as of 2026-05-17 — https://github.com/advisories?query=shizuku

Compose BOM / Material 3 / AGP 9 migration targets:

- **S225**: Compose BOM mapping (`2026.05.00`) — https://developer.android.com/develop/ui/compose/bom/bom-mapping
- **S226**: Compose core releases (1.11.1) — https://developer.android.com/jetpack/androidx/releases/compose
- **S227**: Compose Material3 releases (1.4.0; `material3-expressive` alpha-only) — https://developer.android.com/jetpack/androidx/releases/compose-material3
- **S228**: Jetpack Compose April 2026 updates blog — https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html
- **S229**: Compose Material Icons package summary (`material-icons-extended` deprecated) — https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary

### External URLs (post-rev-5 implementation refresh)

- **S269**: AndroidX Hilt releases page — `hiltViewModel()` moved to
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` / package
  `androidx.hilt.lifecycle.viewmodel.compose` in 1.3.0 —
  https://developer.android.com/jetpack/androidx/releases/hilt
- **S270**: AGP built-in Kotlin migration guide — AGP 9.0+ enables built-in
  Kotlin by default and removes the need to apply
  `org.jetbrains.kotlin.android` / `kotlin-android` —
  https://developer.android.com/build/migrate-to-built-in-kotlin
- **S271**: Compose Preview Screenshot Testing guide — full IDE integration
  requires AGP 9.0+, screenshot plugin 0.0.1-alpha13+, Kotlin 2.2.10+,
  and JDK 17; underlying Gradle tasks are also documented —
  https://developer.android.com/studio/preview/compose-screenshot-testing
- **S272**: Compose Preview Screenshot Testing release notes — alpha14 adds
  AGP 9.0 compatibility and requires previews to be annotated
  `@PreviewTest` —
  https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes
- **S273**: KSP Maven Central metadata — confirms
  `com.google.devtools.ksp` plugin `2.3.8` is available —
  https://repo.maven.apache.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- **S274**: Dagger releases — Dagger/Hilt 2.59.2 fixes AGP-9-era Hilt
  transform and incremental-build issues —
  https://github.com/google/dagger/releases
- **S275**: AndroidX releases overview — current stable matrix for
  Activity, Core, Lifecycle, Navigation, DataStore, Compose, and
  Material 3 —
  https://developer.android.com/jetpack/androidx/versions
- **S276**: AndroidX Core releases — `core-ktx` 1.18.0 stable —
  https://developer.android.com/jetpack/androidx/releases/core
- **S277**: AndroidX Activity releases — Activity / Activity Compose
  1.13.0 stable —
  https://developer.android.com/jetpack/androidx/releases/activity
- **S278**: AndroidX Lifecycle releases — Lifecycle 2.10.0 stable —
  https://developer.android.com/jetpack/androidx/releases/lifecycle
- **S279**: AndroidX Navigation releases — Navigation Compose 2.9.8
  stable —
  https://developer.android.com/jetpack/androidx/releases/navigation
- **S280**: AndroidX DataStore releases — DataStore preferences 1.2.1
  stable and Direct Boot helper APIs available in the 1.2 line —
  https://developer.android.com/jetpack/androidx/releases/datastore
- **S281**: Compose BOM mapping and Material 3 releases — Compose BOM
  2026.05.00 maps to Compose 1.11.1-era artifacts; Material 3 1.4.0 is
  stable while Material 3 Expressive remains alpha-only —
  https://developer.android.com/develop/ui/compose/bom/bom-mapping and
  https://developer.android.com/jetpack/androidx/releases/compose-material3

## Phase 5 Self-Audit

- **Traceability**: every Now/Next/Later/Under-Consideration/Rejected item
  cites at least one source ID. Hardening items in
  "Hardening (post-rev-2 audit)" trace to S00b (the local audit pass);
  candidates traceable to commit history (S00).
- **Tier consistency**: Now items are release-trust blockers or have new
  external evidence (Android 17 stable, AGP 9 / AGP 10 deadline, Hilt
  artifact rename). Next items depend on Now foundations or external
  ecosystem maturity (Shizuku spike, Glance stable). Later items are
  niche or have privacy/effort blockers. Under-Consideration items have
  policy / dependency / evidence risk. Rejected items contradict the
  core philosophy or have been definitively closed off by external policy
  (Android 17 AAPM for C79).
- **Required category coverage** (after rev 3):
  - Security: C05, C09-C13, C38, C48-C51, C73-C79, C90, C94, C104, C111, C121.
  - Accessibility: C55-C58, C63-C64, C98, C100, C104, C127.
  - i18n/l10n: C58-C59, C111 (Translation).
  - Observability/telemetry: C02, C43, C52-C54, C77 (rejected),
    C94, C116.
  - Testing: C29, C45, C47-C50, C83-C84, C91, C94, C101, C122.
  - Docs: C05, C09, C40-C45, C60, C71, C93, C97, C100, C104, C116,
    C120, C126.
  - Distribution/packaging: C34-C39, C45, C50, C93-C94, C120.
  - Plugin ecosystem / integrations: C06, C70-C75 (last three rejected),
    C88 (rejected), C97, C123.
  - Mobile: C19-C22, C81-C82, C89, C92, C103, C118, C123.
  - Offline/resilience: C13, C28-C31, C50, C85, C90, C99, C102, C105, C106.
  - Multi-user/collab: C44, C81.
  - Migration paths: C29-C33, C95, C96, C102, C121.
  - Upgrade strategy: C46-C47, C82, C95-C96, C103, C108-C109, C124.
- **Hostile-review fixes applied in rev 3**:
  - C79 (AccessibilityService) moved from Under Consideration to Rejected
    because Android 17 AAPM auto-revokes the permission — a reviewer
    would have flagged "Under Consideration" as naive given the new
    policy. C80 (UsageStats) moved with the same logic: Shizuku is the
    right path.
  - C95/C96 moved from Next to Now because deferring them past AGP 10
    (mid-2026) would force a panic-migration. A reviewer would have
    called Next placement complacent.
  - C82/C103 — the rev 2 candidate name was "API 36 readiness"; rev 3
    renames the work to "Android 17 readiness" because Android 17 stable
    is the realistic next target SDK, not 16.
  - The 2025/2026 sleep-evidence shift is acknowledged explicitly
    (C126, C127) so a reviewer can't argue we are clinging to outdated
    science.
  - Audit hardening from 2026-05-17 is listed (with file/area pointers)
    rather than buried — a reviewer who knows the audit happened
    expects it to be traceable.
- **No duplicate items across tiers**, no silently resurrected rejects,
  no items contradicting the no-INTERNET / GPL-3.0 / F-Droid-first
  philosophy without an explicit Under Consideration label.
- **Confirmed**: `ROADMAP.md` is written to the repo root.
- **Rev 4 additions**:
  - C128 (FabricatedOverlay), C129 (OLED gamma LUT), C130 (AAPM
    driver-report surface), C131 (Eye Dropper integration) each cite
    ≥2 sources; none contradicts the no-INTERNET / GPL-3.0 / F-Droid-
    first posture; C128 is gated on the existing C06 Shizuku spike.
  - The "Required category coverage" list above stays valid; rev 4
    additions slot into Security (C130), Docs (C130), Plugin ecosystem
    (C128, C131), Mobile (C128, C131), Migration paths (C123).
  - Tier shifts in rev 4 (C123 UC → Next; C101 risk 1 → 2) each cite
    a primary source.
  - Two doc-rename / process follow-ups in "What changed in rev 4"
    track explicit text artefacts (`docs/android-17-readiness.md` rename,
    `docs/research-watchlist.md` header bump). Both are non-code,
    reversible, and explicitly listed so a future review can audit
    completion.
- **Companion artefacts produced in the 2026-05-17 walk-away research
  pass** (under
  [.ai/research/2026-05-17/](.ai/research/2026-05-17/)):
  `STATE_OF_REPO.md`, `MEMORY_CONSOLIDATION.md`, `SOURCE_REGISTER.md`,
  `RESEARCH_LOG.md`, `COMPETITOR_MATRIX.md`, `FEATURE_BACKLOG.md`,
  `PRIORITIZATION_MATRIX.md`, `SECURITY_AND_DEPENDENCY_REVIEW.md`,
  `DATASET_MODEL_INTEGRATION_REVIEW.md`, `CHANGESET_SUMMARY.md`.
  Canonical consolidated project memory lives at
  [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

### Phase 5 self-audit (rev 6 additions)

- **Traceability**: every C146-C169 entry cites at least one source
  (S00 for in-tree audit evidence, plus S00d / S00s / S118 / S153 /
  S287-S290 where the candidate borrows external primary references).
  S287-S290 are new in rev 6 and trace to AndroidX DataStore release
  notes, Android `View` reference, Android `Looper` reference, and
  the Java `Double.parseDouble` contract.
- **Tier consistency**: C146-C165 all land as `Shipped 2026-05-17`
  because the audit pass merged them in the same session. C166-C169
  are tiered `Later` because each has a small impact, a clear-but-
  non-urgent path, or a product question that hasn't been answered.
- **Required category coverage (rev 6 deltas)**:
  - Concurrency: C148, C150, C151, C152, C159.
  - Reliability: C147, C153, C159, C162, C163.
  - Performance: C160, C161, C164, C165.
  - UX / i18n: C149, C156, C157, C158, C169.
  - Resource safety: C154, C155.
  - Maintainability: C167 (later shipped as C195 / v0.6.2).
- **Hostile-review fixes applied in rev 6**:
  - The round-one "non-existent API" finding was actively retracted
    and the code reverted. A reviewer would have caught the false
    claim by reading the AndroidX release notes; we caught it in the
    rev 6 validation pass before it shipped to anyone.
  - C148 is documented as a *latent* deadlock (the 2 s timeout
    masked it in production), so the fix is justified even though no
    user-facing incident existed. A reviewer would otherwise call the
    fix speculative.
  - C147 explicitly explains why singleton lifetime + service-process
    kill is the failure mode, not "a Compose preview bug" — so a
    reviewer asks "does this actually happen" and the answer is "yes,
    every process restart with overlay engine."
- **No duplicate items across tiers** (C166-C169 are new IDs; no
  overlap with C01-C145). No silently resurrected rejects. No
  contradictions with the no-INTERNET / GPL-3.0 / F-Droid-first
  philosophy.
- **Confirmed**: `ROADMAP.md` and `CHANGELOG.md` both updated; the
  audit's CHANGELOG entry was corrected to remove the false
  "non-existent API" claim.

## Audit-surfaced items (2026-06-15)

Items found during a deep engineering audit but not fixed in the audit
pass — either because they require device testing, are design decisions,
or have larger blast radius.

## Research-Driven Additions

Research date: 2026-06-28. This appends only net-new incomplete items.
C83 shipped in v0.6.5 with tab-level Compose Preview Screenshot baselines.
Blocked device, account, emulator-infrastructure, and external-repo items live
in `Roadmap_Blocked.md` so this active section stays actionable-only.

### P1 - Reliability and release trust

### P2 - UX and platform edge cases

## Research-Driven Additions

### P1 - Release trust and local verification

### P2 - Maintainability and copy guardrails

## Audit Findings — 2026-08-10

- [ ] P2 — C220 — Ambient controls remain enabled on devices with no light sensor
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/schedule/LightSensorCard.kt:58-95 and light-sensor capability plumbing in app/src/main/java/com/openlumen/sensor/LightSensorAdapter.kt
  Problem: When currentLux < 0, the card changes only the displayed lux text to “unavailable.” The enable switch and threshold slider remain interactive/persistable, even though no TYPE_LIGHT sensor can ever satisfy the setting. Users can turn on a feature that silently never triggers.
  Evidence: The card’s enabled/slider modifiers depend on the preference toggle, not on sensor availability; the unavailable sentinel is used only for text. The adapter returns no usable sensor in this state, so the setting has no reachable active path.
  Fix: Expose sensor capability separately from current lux, disable or hide the switch/slider when unsupported, and provide a concise explanation/recovery path. Prevent persistence of an enabled ambient mode that cannot operate, or clearly mark it as waiting for hardware.
  Acceptance: With no light sensor, controls are visibly disabled with an accessible explanation and cannot create a misleading active configuration; with a sensor, controls work and availability changes are reflected without restarting the screen.
  Confidence: Verified
  Effort: M

- [ ] P2 — C221 — Ambient threshold has no hysteresis or dwell protection
  Category: reliability
  Where: app/src/main/java/com/openlumen/service/LumenService.kt:483-489 and app/src/main/java/com/openlumen/sensor/LightSensorAdapter.kt:55-77
  Problem: The active decision uses a single lux < threshold comparison. EMA smoothing and a 5% distinct filter reduce noise but do not prevent repeated enter/exit transitions when lux hovers around the threshold, causing ramp cancellation/restart, alarm churn, and visible flicker.
  Evidence: There are no separate engage/disengage thresholds, consecutive-sample counters, or minimum dwell time in the service or sensor adapter. Every distinct reading is passed to the same direct comparison and can change shouldBeActive.
  Fix: Add configurable or fixed hysteresis (for example, enter below the threshold and exit above a higher threshold) and/or require a bounded dwell/consecutive-sample period. Make the chosen behavior visible in helper text and cover it with a noisy-boundary test.
  Acceptance: A lux sequence oscillating around the threshold does not repeatedly toggle the engine; crossing the exit threshold or completing the dwell changes state promptly and exactly once.
  Confidence: Likely
  Effort: M

- [ ] P2 — C222 — Notification countdown becomes stale after an alarm or reevaluation
  Category: ux
  Where: app/src/main/java/com/openlumen/service/LumenService.kt:321-368,423-425, ScheduleAlarmReceiver.kt, and app/src/main/res/values/strings.xml:207-212
  Problem: The “until alarm” countdown is computed only during preference emissions through updateNotificationSubtitle. ACTION_REEVALUATE and ScheduleAlarmReceiver do not update it, and there is no chronometer/timer. The notification can continue showing a future countdown after the alarm fired or show a past value until an unrelated preference change.
  Evidence: The countdown fields are read from preferences and updated in the emission handler; the receiver’s transition path starts/re-evaluates service work without a notification refresh. No scheduled countdown update exists between emissions.
  Fix: Refresh the subtitle whenever a transition/reevaluation changes the next alarm, or use a platform chronometer/strictly bounded periodic update for a live countdown. Clear the countdown when no future alarm exists.
  Acceptance: Delivering the scheduled alarm or an explicit reevaluation updates/removes the countdown immediately; notification text never reports a stale/past next alarm after the service has recomputed the schedule.
  Confidence: Verified
  Effort: M

- [ ] P2 — C223 — Foreground notification copy claims “active” while the service is only on standby
  Category: ux
  Where: app/src/main/res/values/strings.xml:207-212 and app/src/main/java/com/openlumen/service/LumenService.kt:289-302,321-368
  Problem: The foreground notification title is always “OpenLumen active” whenever the enabled service is running, even when a fixed/solar schedule is outside its interval or an ambient threshold is not met. The subtitle only adds the next alarm and does not state that the filter is currently inactive.
  Evidence: startInForeground uses the same active title for every p.enabled service state; the service distinguishes shouldBeActive internally but does not map it to notification title/status copy. Users therefore cannot tell “service running” from “filter currently applied.”
  Fix: Provide separate service-running/standby/actively-filtering copy and update the notification on active-state transitions, retaining the next-transition information. Use calm, unambiguous terminology consistent with Home status text.
  Acceptance: An enabled but out-of-window service notification explicitly says standby/not filtering, while an applied matrix says filtering active; transitions update without restarting the service.
  Confidence: Likely
  Effort: S

- [ ] P2 — C224 — Notification permission denial is treated as permanent and cannot be retried in-app
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:83-109, requestNotifIfNeeded, and CHANGELOG.md:332-336
  Problem: The permission result stores notification_permission_asked=true for every denial, and future requests are blocked by that flag. The code does not check shouldShowRequestPermissionRationale or offer a notification-settings route, so a first temporary denial prevents the app from explaining/re-requesting the permission later. The changelog says the prompt should re-fire when rationale is true, but the current implementation does not do so.
  Evidence: The result callback writes the asked flag unconditionally; the launch guard checks only API level and the stored flag. No rationale branch or permanent-denial recovery action is present in the traced Home flow.
  Fix: Track first request, temporary denial, and permanent denial separately; show a rationale with Retry when Android permits another request and a Settings action when it does not. Keep notification-dependent behavior clearly marked when permission is absent.
  Acceptance: A first denial can be retried after the rationale is shown, a permanent denial opens the app notification settings, and tests cover both result states without repeatedly nagging the user.
  Confidence: Verified
  Effort: M

- [ ] P2 — C225 — FGS-start-blocked intents are ignored by MainActivity
  Category: ux
  Where: app/src/main/java/com/openlumen/service/LumenServiceStarter.kt:39-43, app/src/main/java/com/openlumen/MainActivity.kt:11-16, app/src/main/java/com/openlumen/widget/WidgetActionReceiver.kt:49-55,76-83, and app/src/main/java/com/openlumen/widget/LumenTileService.kt:120-138
  Problem: Widget/tile actions open the activity with ACTION_START_BLOCKED and a reason extra when the foreground service cannot start, but MainActivity only sets content. It does not inspect the initial intent or handle onNewIntent, so both a newly launched and an existing activity show normal Home with no explanation, retry, or settings route.
  Evidence: The starter constructs the action/reason intent and callers invoke openApp after a blocked result; MainActivity has no action branch or onNewIntent implementation. The SINGLE_TOP flag makes the ignored onNewIntent path especially reachable when Home is already open.
  Fix: Handle the action in both initial and new intents, consume it once, and show an accessible, actionable banner/dialog with the blocked reason plus Retry and relevant settings/help. Do not rely on a transient toast that disappears before the user can recover.
  Acceptance: Triggering a blocked widget/tile action with the app closed or open presents the same clear recovery UI, and Retry/settings actions invoke the documented path exactly once.
  Confidence: Verified
  Effort: M

- [ ] P2 — C226 — Kelvin slider is visually stale and can jump when custom/preset color changes
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:120,122-129,397-420 and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:149-175
  Problem: The Kelvin slider is initialized/displayed from a fixed KELVIN_DEFAULT and is not reverse-derived when RGB, custom matrix, or a preset changes. The next Kelvin drag therefore starts from an unrelated 6500 K value and can jump the color unexpectedly, while the RGB controls and status represent a different current matrix.
  Evidence: The Home effects synchronize RGB/gamma but intentionally omit Kelvin derivation; the Kelvin label reads the fixed state. The ViewModel persists canonical RGB for custom changes, so selecting a warm preset or editing RGB does not update the Kelvin slider state.
  Fix: Store a canonical color-temperature state when applicable and derive/display an approximate Kelvin value for compatible matrices, or clearly scope the Kelvin control to an explicit mode and reset/disable it when the matrix is not temperature-derived. Ensure the first drag uses the visible current value.
  Acceptance: After selecting a preset, editing RGB, navigating away/back, and recomposing, the Kelvin control either reflects the current temperature-derived color or is clearly inactive; moving it never jumps from a stale hidden default.
  Confidence: Verified
  Effort: M

- [ ] P2 — C227 — Previous-preset restore cannot represent a previous custom snapshot
  Category: ux
  Where: app/src/main/java/com/openlumen/service/PresetCycle.kt:54-64 and app/src/main/java/com/openlumen/ui/presets/PresetsScreen.kt:123-126
  Problem: Cycling records "custom" as previousPresetKey, but the Presets screen renders a restore affordance only when Presets.byKey(previousKey) returns a built-in preset. After changing from custom to a named preset, the previous custom state is hidden from the UI even though notification restore can still attempt it, producing inconsistent recovery behavior.
  Evidence: restorePrevious accepts arbitrary keys, while the screen’s conditional card requires a catalog lookup. Custom matrices are stored separately in preferences and are not represented as a catalog entry or snapshot in the restore UI.
  Fix: Represent custom as a first-class restore target with the exact prior RGB/gamma/dim snapshot, or explicitly exclude custom from previous-state recording and make notification/UI behavior consistent. Add custom-to-named-to-restore tests across Home, notification, and widget paths.
  Acceptance: A custom-to-named transition either shows and restores the exact prior custom matrix or consistently communicates that it cannot be restored; no surface offers a restore action that another surface silently omits.
  Confidence: Verified
  Effort: M

- [ ] P2 — C228 — Normalization silently overrides the explicit Off preset and AlwaysOff schedule
  Category: correctness
  Where: core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt:221-242 and app/src/main/java/com/openlumen/service/LumenService.kt:403-409; user choices in app/src/main/java/com/openlumen/ui/presets/PresetsScreen.kt and ScheduleScreen.kt:100-105
  Problem: Whenever enabled preferences are emitted, normalizedEnabledFilterState changes active preset "off" to the previous/default preset and changes AlwaysOff to AlwaysOn. The UI explicitly offers an Off preset and an AlwaysOff schedule, so selecting either is immediately undone without explanation; the user cannot intentionally leave the service running but filtering disabled through those controls.
  Evidence: handlePreferenceEmission writes the normalized state and returns before applying it. The same screen surfaces expose the values that normalization rejects, and no confirmation/helper/status explains the override.
  Fix: Define Off/AlwaysOff semantics explicitly: permit them as valid standby states, or move normalization to the narrow action that explicitly means “turn on” and show a clear confirmation when it replaces Off. Keep persisted state, notification copy, and schedule UI aligned.
  Acceptance: Selecting Off or AlwaysOff produces the documented stable state and survives the next preference emission; any intentional auto-normalization is visibly explained and covered by service/UI tests.
  Confidence: Verified
  Effort: M

- [ ] P2 — C229 — External settings/share intents can fail without a safe user-facing result
  Category: reliability
  Where: app/src/main/java/com/openlumen/ui/settings/OverlayPermissionCard.kt:98-103, app/src/main/java/com/openlumen/ui/settings/ExactAlarmAccess.kt:27-40, and app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:261-274
  Problem: Several flows call startActivity/chooser directly. Overlay permission has no resolver or catch; Exact Alarm catches only the primary activity failure and silently swallows fallback failure; Driver export/share has no ActivityNotFoundException/SecurityException handling. OEMs, managed profiles, missing browsers/share targets, or restricted settings can therefore crash the click or make it appear to do nothing.
  Evidence: The cited call sites perform direct launches and do not share a safe launcher/result message. The Exact Alarm fallback catch has no user feedback, and the share chooser is invoked without checking whether it can resolve.
  Fix: Introduce one small UI-safe external-intent helper that checks resolveActivity, catches ActivityNotFoundException and SecurityException, and reports an actionable inline/snackbar error. Use it for settings and share flows, preserving the best available fallback.
  Acceptance: Tests with no resolver and with a throwing activity prove no crash, no silent failure, and a localized recovery message; normal devices still open the intended destination.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C230 — Location-entry dialog can push its actions below the keyboard/short viewport
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/schedule/LocationEntryDialog.kt:84-215
  Problem: The dialog body is an unbounded Column containing help text, a LazyColumn capped at 200 dp, three inputs, validation/error content, and actions. It has no IME-aware scroll container or bounded body layout. On a short phone viewport, especially with the keyboard open and a 12-city result list, the Save/Cancel actions can be obscured or unreachable.
  Evidence: The screenshot suite does not exercise this dialog, and the implementation has no verticalScroll, constrained dialog body, or keyboard inset handling around the stacked content. The result list plus fields can exceed the available window height.
  Fix: Use an IME-aware bounded dialog layout with a scrollable content region and fixed/visible actions, preserving focus and error visibility. Test long results, validation errors, keyboard-open, and 360×640-style constraints in both themes.
  Acceptance: Every field, result, validation message, Save, and Cancel remains reachable by keyboard and touch in a short viewport; opening/closing the IME does not lose the selected city or move actions off-screen.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C231 — Navigation-rail content does not receive system-bar insets
  Category: visual
  Where: app/src/main/java/com/openlumen/ui/OpenLumenRoot.kt:98-115,156-167 and app/src/main/java/com/openlumen/ui/ScreenPadding.kt:9-19
  Problem: In rail mode the rail itself applies statusBarsPadding/navigationBarsPadding, but the sibling OpenLumenNavHost content only receives weight/fill/background and screen-level fixed padding. Because MainActivity enables edge-to-edge, top-level content can draw under the status bar and bottom content under the navigation area on wide/tablet layouts.
  Evidence: The rail branch places the NavHost directly beside the padded rail; topLevelScrollPadding is a fixed 16/24 dp spacing, not a system-inset consumption. The bottom-navigation branch handles insets differently, so the issue is specific to the secondary layout.
  Fix: Apply the same safe-content inset policy to the rail NavHost (or consume insets once in a shared root container) and verify nested dialogs/popovers still use correct window insets. Do not double-apply rail insets.
  Acceptance: Rail layouts in portrait/landscape and both themes keep headers and final scroll content below/above system bars at 100% and large fonts, with screenshot tests covering the inset contract.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C232 — Fixed navigation-rail width and one-line labels clip localized/large-font text
  Category: a11y
  Where: app/src/main/java/com/openlumen/ui/OpenLumenRoot.kt:156-179,226-233 and localized labels in app/src/main/res/values-es/strings.xml:4-8
  Problem: The rail is fixed at 104 dp and each item’s label is maxLines=1 with no overflow/tooltip strategy. Long localized labels such as Spanish “Ajustes predefinidos,” and 200% font scaling, can be clipped or ellipsized without an accessible full-name alternative, reducing discoverability and violating the intended readable navigation contract.
  Evidence: Width and item height are constants; the label has a one-line constraint and no measured adaptive width or content description. The supported Spanish strings already exceed the short English labels.
  Fix: Make the rail width/label layout adaptive or provide intentionally short localized labels plus an accessible tooltip/content description containing the full name. Verify selected/unselected state remains understandable at large font scales.
  Acceptance: Supported locales and at least 200% font scale display or expose the full name for every rail item without overlap/clipping, and Compose semantics tests assert the complete accessible labels.
  Confidence: Likely
  Effort: M

- [ ] P2 — C233 — Driver screen’s Auto explanation disagrees with the service resolver
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:70-79 and core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt:47-55
  Problem: The Driver screen describes Auto as root first, then Overlay, omitting CDM. The actual resolver selects root, then CDM, then Overlay. On a device with CDM available and no root, the service uses CDM while the UI tells the user Auto will use Overlay, making driver behavior and troubleshooting misleading.
  Evidence: The UI label has a separate root/overlay conditional; pickBestFrom includes COLOR_DISPLAY_MANAGER before the overlay fallback. No shared resolver/description is used.
  Fix: Derive the Auto explanation from the same ordered resolver/capability list used by the service, including the no-driver case, and keep it updated after reprobe.
  Acceptance: For every availability combination, the screen’s Auto explanation names the same selected driver and fallback order that the service will use; tests exercise root-only, CDM-only, overlay-only, and none.
  Confidence: Verified
  Effort: S

- [ ] P2 — C234 — Reprobe can run concurrently and overwrite newer driver results
  Category: reliability
  Where: app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:155-157 and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:219-231
  Problem: The Reprobe control remains enabled while refreshProbes() launches a new coroutine, clears the global root cache, and starts probes. Rapid taps or a simultaneous service probe can create overlapping root commands and non-deterministic _probes/preference updates; an older completion can overwrite a newer result.
  Evidence: There is no in-flight flag, mutex, cancellation/latest-generation check, or disabled button around the refresh coroutine. The function can be called repeatedly from the UI and touches process-wide probe state.
  Fix: Make refresh single-flight/latest-wins, disable the action while running, and coordinate global cache invalidation with service probing. Preserve the last known result while showing progress and surface a bounded failure.
  Acceptance: Rapid repeated taps and concurrent service/UI refreshes execute at most one probe generation, the final displayed result is from the latest generation, and no stale completion changes the selected driver afterward.
  Confidence: Likely
  Effort: M

- [ ] P2 — C235 — Saving a profile with an existing name silently replaces the old snapshot
  Category: ux
  Where: core-prefs/src/main/java/com/openlumen/prefs/Profiles.kt:62-77, app/src/main/java/com/openlumen/ui/settings/AboutScreen.kt:315-363, and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:87-90
  Problem: Profile storage trims names and replaces an existing profile with the same name in place. The save dialog has no collision warning, explicit Replace confirmation, versioning, or undo. A user trying to create a new profile can silently destroy a prior configuration.
  Evidence: Profiles.upsert filters the same-name entry before appending the new snapshot; the dialog only validates the name/content and immediately calls save. No recovery action is presented.
  Fix: Detect name collisions in the dialog and require an explicit Replace confirmation, or auto-generate a distinct name and offer rename/undo. Keep the operation atomic and test whitespace/case normalization policy.
  Acceptance: Saving an existing name cannot overwrite without an explicit confirmation; cancel preserves the old snapshot, and confirmed replacement is reflected consistently in Home/profile lists with a test.
  Confidence: Likely
  Effort: M

- [ ] P2 — C236 — Screenshot tests exercise fixtures instead of production screens
  Category: testing
  Where: app/src/screenshotTest/kotlin/com/openlumen/screenshot/ScreenCoverageScreenshotTest.kt:69-112,125-144 and app/src/screenshotTest/kotlin/com/openlumen/screenshot/ThemeTokenScreenshotTest.kt:28-44
  Problem: The seven screen screenshots render HomeTabFixture, ScheduleTabFixture, and similar synthetic fixtures rather than HomeScreen, ScheduleScreen, OpenLumenRoot, or the real navigation/inset/dialog composition. The token test covers only isolated token fixtures, with full screen screenshots effectively dark-only at one 393×852 size. Passing baselines therefore cannot catch production wiring, light-theme, rail, dialog, loading/error/empty, keyboard, or inset regressions.
  Evidence: Test source imports/renders fixture composables and hardcodes OpenLumenTheme(darkTheme = true) for the screen coverage cases; only the token fixture is rendered once per theme. No production ViewModel/fake-repository harness is used.
  Fix: Build a deterministic production-composable harness with fake preferences/engine/service state, then cover real Home, Schedule, Presets, Driver, Settings, dialogs, and state variants in light/dark themes, phone/rail widths, and large text. Keep fixture tests only for isolated component contracts.
  Acceptance: A deliberate change to production screen layout/theme/insets causes the relevant screenshot test to fail; baselines include both themes, rail and phone layouts, and representative loading/error/empty/dialog states.
  Confidence: Verified
  Effort: L

- [ ] P2 — C237 — Slider controls lack an explicit accessible name
  Category: a11y
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:200-208,225-233,285-298,409-420,481-490,519-528, ScheduleScreen.kt:213-243, and LightSensorCard.kt:86-95
  Problem: Custom Gamma/RGB/schedule/light sliders expose a value/state description but do not attach an explicit control name/content description or label semantics to the adjustable node. A visible sibling Text is not guaranteed to become the TalkBack label for a custom semantic control, so users can encounter anonymous adjustable controls whose values are not associated with Gamma, red/green/blue, schedule offset, or lux threshold.
  Evidence: The cited slider call sites set stateDescription/visual labels but contain no contentDescription, semantics label, or merged labeled-control pattern. C190 improved labels/state descriptions but did not add names to these nodes.
  Fix: Give every adjustable node a stable localized accessible name and value/state description, using a shared labeled-slider helper/semantics pattern so the visible and spoken labels stay in sync. Add Compose semantics tests for all custom slider variants.
  Acceptance: Semantics output for each slider contains its control name and current value, keyboard/TalkBack adjustment announces both, and names remain correct after recomposition and localization.
  Confidence: Likely
  Effort: M

- [ ] P3 — C238 — Emergency KCAL recovery documentation uses an invalid scalar
  Category: docs
  Where: docs/troubleshooting.md:31-41 and docs/root-safety.md:114-125; range enforcement in core-engine/src/main/java/com/openlumen/engine/KcalEngine.kt:262-268
  Problem: The emergency recovery examples tell users to write 256 256 256, but the current KCAL engine clamps/validates scalar values to a maximum of 255. On strict kernels the documented recovery command can be rejected, leaving a user with the very failure path the documentation is supposed to recover from.
  Evidence: Both docs contain the literal 256 recovery command; KcalEngine.MAX_SCALAR is 255 and the command builder enforces that range. The changelog also states the standardized range is 0–255.
  Fix: Change the recovery examples to valid 0–255 values (use 255 for maximum white), state the expected kernel path, and add a docs/health check that rejects recovery examples outside the engine’s documented range.
  Acceptance: Copying the documented emergency command produces a valid KCAL command on the strict 0–255 path, and documentation lint/test fails if an out-of-range recovery scalar is reintroduced.
  Confidence: Verified
  Effort: S

- [ ] P3 — C239 — Boot receiver documentation claims Direct Boot is unsupported after it shipped
  Category: maintainability
  Where: app/src/main/java/com/openlumen/BootReceiver.kt:23-27, app/src/main/AndroidManifest.xml:103-110, and core-prefs/src/main/java/com/openlumen/prefs/DirectBootStateStore.kt
  Problem: The BootReceiver KDoc still says LOCKED_BOOT_COMPLETED/Direct Boot is not implemented and references an old tracked issue, while the manifest contains LockedBootReceiver and the device-protected state path is active. This stale contract can cause maintainers to remove or bypass the two-phase boot behavior during future changes.
  Evidence: The comment directly contradicts the current manifest and DirectBootStateStore implementation. The normal boot receiver and locked-boot receiver are both registered, so the stale text is reachable guidance for anyone modifying boot behavior.
  Fix: Rewrite the KDoc to describe the current locked-boot mirror/start behavior, unlocked reconciliation, and failure limitations; remove the obsolete issue reference and keep the comment synchronized with the manifest.
  Acceptance: Source documentation accurately describes both boot receivers and the direct-boot data source, with a review/health check preventing the obsolete “not implemented” claim from returning.
  Confidence: Verified
  Effort: S

- [ ] P3 — C240 — PROJECT_CONTEXT.md contains stale release, schema, and Auto-driver contracts
  Category: docs
  Where: PROJECT_CONTEXT.md:30-33,85-96,160-162, README.md/CHANGELOG.md current release metadata, core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt:164-169, and core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt:47-55
  Problem: The context document says the main release is v0.6.5 and tagged release v0.4.0, describes current schema as 1, and says Auto falls back to Overlay so it always has a driver. The repository is at v0.6.6, schema 2, and the current resolver can select CDM or reach an unavailable-overlay/no-driver case. Agents and maintainers using this document will make incorrect compatibility and recovery decisions.
  Evidence: The cited context claims conflict with the current build/changelog metadata, CURRENT_SCHEMA_VERSION, and resolver code. No generated/currentness check covers these assertions.
  Fix: Update the canonical context to current release/schema/resolver behavior, label historical claims with their version, and add a lightweight consistency check for version/schema/driver-contract statements that are intended to be current.
  Acceptance: A fresh contributor following PROJECT_CONTEXT.md receives the current release/schema/Auto behavior, and the consistency check fails when those canonical values drift.
  Confidence: Verified
  Effort: S

- [ ] P3 — C241 — Inline comments contradict live sensor and diagnostics locking behavior
  Category: maintainability
  Where: app/src/main/java/com/openlumen/sensor/LightSensorAdapter.kt:29-33,62 and app/src/main/java/com/openlumen/diagnostics/DiagnosticsLog.kt:40-43,77-85
  Problem: LightSensorAdapter says it never calls trySend, but the implementation does call trySend for readings. DiagnosticsLog says reads are intentionally not locked, while read() synchronizes on the same writeLock. These contradictions make concurrency intent and future cleanup decisions unreliable.
  Evidence: The comment and live statements are adjacent in the cited functions; the code is not a dead or test-only branch. The mismatch is visible by reading the current implementation without inference about runtime behavior.
  Fix: Rewrite both comments to state the actual channel/backpressure and locking guarantees, or change the implementation if the documented guarantee is the intended contract. Add a focused concurrency/comment contract test where practical.
  Acceptance: Comments describe the current operations accurately, and code review/tests document whether sensor sends are lossy and whether diagnostics reads share the writer lock.
  Confidence: Verified
  Effort: S

## Research-Driven Additions

- [ ] P2 — C247 — Remove the schedule alarm receiver-to-service process-death gap
  Why: A scheduled transition currently depends on a `BroadcastReceiver` process handoff before the foreground service can re-evaluate the schedule. If the process dies during that narrow jump, the transition can be missed even though the alarm fired; the recovery retry budget only addresses a foreground-start rejection, not a killed handoff.
  Evidence: `app/src/main/java/com/openlumen/service/ScheduleAlarmOrchestrator.kt:112-119` creates `PendingIntent.getBroadcast` for `ScheduleAlarmReceiver`; `ScheduleAlarmReceiver.kt:16-22` then calls `LumenServiceStarter.start()`. The Android DeskClock rationale quoted in the alarm-delivery discussion recommends targeting the service directly because an out-of-memory kill can thwart a receiver-to-service jump: https://stackoverflow.com/questions/71657416/can-we-startforegroundservie-from-an-exact-alarms-receiver-in-the-background. Android's FGS background-start restrictions remain the platform constraint to validate: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start.
  Touches: `ScheduleAlarmOrchestrator.kt`, `ScheduleAlarmReceiver.kt`, `LumenService.kt`, manifest declarations, alarm tests, and the Android 15/17 device-matrix schedule rows.
  Acceptance: The primary scheduled transition uses an explicit `PendingIntent.getForegroundService`/service-targeted path or a documented durable idempotent handoff that survives process death; a kill immediately before the trigger still produces one re-evaluation and next-alarm schedule; blocked FGS starts retain bounded retries and no duplicate transitions; API 31, 35, and 37 validation records the result.
  Complexity: M

- [ ] P2 — C248 — Expose supported translations through Android per-app language settings
  Why: OpenLumen already ships six localized resource sets, but Android 13 users cannot select the app language from the system's App Languages surface unless the app opts into generated or manual locale configuration. This makes the existing C58/C59 translation investment harder to discover and control for multilingual users; it is separate from C232's rail clipping problem.
  Evidence: `app/src/main/res/` contains `values`, `values-de`, `values-es`, `values-fr`, `values-ja`, and `values-pt`; `app/build.gradle.kts:86-147` has no `androidResources { generateLocaleConfig = true }` or default-locale configuration, and `AndroidManifest.xml` has no `android:localeConfig`. Android recommends generated locale configuration via AGP 8.1+ and describes the system settings behavior here: https://developer.android.com/guide/topics/resources/app-languages.
  Touches: `app/build.gradle.kts`, `app/src/main/res/resources.properties` or the generated-locale configuration, the manifest if manual configuration is chosen, translation documentation, and locale-completeness tests.
  Acceptance: On API 33+, Settings > App Languages lists OpenLumen with exactly the supported English/German/Spanish/French/Japanese/Portuguese locales; changing the system selection updates the app after recreation and persists through backup/restore; base and translated string key sets are checked for drift; unsupported library locales are not advertised.
  Complexity: S

- [ ] P2 — C249 — Make dependency-update review produce release-note and compatibility evidence
  Why: A stable version number alone does not say whether an AndroidX/AGP/Hilt/Kotlin upgrade changes APIs, target-SDK behavior, build tooling, or license/provenance. The current helper detects Maven metadata candidates, leaving the release checklist's most important compatibility step as an undocumented manual search; this extends the shipped C124/C144 version baseline without proposing another blind dependency bump.
  Evidence: `tools/dependency_update_review.py:1-270` queries repository `maven-metadata.xml`, filters unstable versions, and reports current/latest coordinates, but emits no official release-note URL, API/behavior-change summary, dependency-verification diff, or module-specific test recommendation. `docs/release-checklist.md` separately instructs maintainers to read release notes. Official release streams are available for AndroidX, AGP, Kotlin, and Dagger: https://developer.android.com/jetpack/androidx/versions, https://developer.android.com/build/releases/agp-9-2-0-release-notes, https://kotlinlang.org/docs/releases.html, https://github.com/google/dagger/releases.
  Touches: `tools/dependency_update_review.py`, `tools/test_dependency_update_review.py`, `docs/dependency-verification.md`, `docs/release-checklist.md`, and a small checked-in mapping for official release-note endpoints where Maven metadata has no changelog link.
  Acceptance: The console/JSON report distinguishes update, unresolved, and intentionally held versions; each update has an official release-note URL, affected module/plugin, compatibility-risk note, required Gradle/test commands, and verification-metadata impact; a metadata/network failure is not reported as “current”; fixture tests cover stable updates, pre-release-only versions, missing metadata, and release-note mapping.
  Complexity: M
```

</details>
