# Changelog

All notable changes to OpenLumen are documented here.
The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Release builds now fail fast unless the full `OPENLUMEN_*` signing
  environment is present or `-Popenlumen.allowUnsignedRelease=true` is
  explicitly passed for local/F-Droid reproducibility checks.
- Added `tools/local_release_gate.py` as the workstation release gate for
  strict Gradle verification, lint/tests/screenshot lanes, no-network manifest
  checks, Google/Firebase classpath checks, SBOM/advisory output, SHA-256 sums,
  and APK signature verification.

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
