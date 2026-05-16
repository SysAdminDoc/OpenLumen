# OpenLumen Roadmap

Research version: 2026-05-16 rev 2. Supersedes the original v0.1.0 -> v1.0.0
checklist while preserving shipped history, current design rules, and the first
source-backed roadmap pass from 2026-05-16.

OpenLumen is an offline, GPL-3.0-or-later Android display filter. It should remain
F-Droid-clean, no-INTERNET by default, privacy-literal, and technically honest about
the difference between framebuffer/root/system transforms and overlay fallback.

## State of the Repo

OpenLumen currently ships v0.4.0 with Kotlin 2.1.0, AGP 8.7.3, Jetpack Compose,
minSdk 26, targetSdk 35, Java 17, Hilt, DataStore, kotlinx.serialization, and four
modules: `app`, `core-engine`, `core-schedule`, and `core-prefs`.

What works today:

- Four display engines: AOSP `ColorDisplayManager`, root `SurfaceFlinger`, root KCAL,
  and rootless overlay fallback. The service probes at runtime and serializes engine
  apply/clear calls.
- Compose UI with Home, Schedule, Presets, Driver, and About tabs.
- Named presets, custom RGB, per-channel gamma, intensity, dim, fixed-time schedule,
  solar schedule, ambient-light trigger, Quick Settings tile, boot restore, local
  crash log, and SAF JSON profile export/import.
- AlarmManager-driven schedule transitions replaced the old one-minute ticker.
- Unit tests exist for matrix math and schedule/solar behavior.
- CI builds debug and runs app lint. Release workflow can build/upload an APK and
  SHA256SUMS if signing secrets are present.

What is incomplete:

- No documented real-device validation matrix for Pixel/Tensor, Snapdragon+KCAL,
  Samsung/One UI, non-root overlay, Android TV, or Android 16-preview behavior.
- F-Droid packaging is not present: no `fastlane/metadata`, finalized icon, store
  screenshots, reproducibility notes, or full release checklist.
- No connected-device suite, Compose UI suite, or release-smoke script proves
  permissions, overlay behavior, service lifecycle, boot restore, or no-INTERNET.
- Per-app rules, widgets, Tasker intents, Wear OS, Shizuku, Direct Boot, profile
  migrations, and driver-report UX are not implemented.
- i18n is structurally thin: many user-facing strings remain hard-coded in Compose.

Hard constraints:

- License: GPL-3.0-or-later.
- Android: minSdk 26, targetSdk 35 today; foreground service uses `specialUse`.
- Distribution: F-Droid first, Play optional, no ads, no required account, no
  network permission in the main app.
- UX/aesthetic: Catppuccin Mocha/AMOLED, Compose Material 3, no pill-shaped buttons,
  no marketing copy inside the app.

## Evidence Map

### Direct OSS and near-OSS competitors

| Project | Fit | Stars | Latest visible activity | Maintainer signal | Notable feature signal | Sources |
|---|---:|---:|---|---|---|---|
| Red Moon | Direct OSS baseline | 721 | commit feed 2025-12-08; F-Droid 4.0.0 added 2022-08-23 | README says not actively maintained, PRs accepted | profiles, excluded apps, widget/tile/notification, root beta, translations | S10, S11, S12, S13 |
| Shades | Ancestor/demo | 8 | commit feed 2017-04-29 | inactive | opacity, tint, persistent notification, boot restore | S14 |
| Night-Light | Native-night-mode sample | 11 | commit feed 2016-10-21 | inactive | native Android night mode, blacklist, QS tile, Tasker | S15 |
| DimTV | Android/TV dimmer | 10 | commit feed 2025-02-16 | small active fork | TV UI, notification control, environment adjustment, schedule, color filter | S16 |
| Low Brightness | Modern overlay app | 30 | release page shows 5.1.0 on 2026-01-28 | active small project | Material You, no internet, QS tile, schedules, AccessibilityService overlay | S17 |
| Screen Filter | Old OSS app | 6 | commit feed 2017-08-29 | inactive | color temperature, intensity, opacity, auto on/off, installer/su dialog pause | S18 |
| Eye-Rest | Old OSS app | 14 | commit feed 2019-09-18 | inactive | intensity, color picker, scheduled interval | S19 |
| Pixel Filter | AMOLED-specific dimmer | 62 | archived 2019; F-Droid mirror says abandoned | inactive | pixel-grid dimming, light sensor, pattern shifting, Android 8 overlay limits | S69, S70 |
| Screen Dimming | Recent overlay microproject | 0 | release page shows v1.0 on 2026-02-18 | single maintainer | emergency unlock gesture, haptic feedback, notification intensity controls, language selector | S71 |
| dim_overlay_app | Recent Flutter/Android overlay demo | 1 | 2 commits visible | single maintainer | foreground-service overlay, slider opacity, overlay-permission onboarding | S81 |
| SwingShift | Minimal Kotlin Android sample | 0 | 1 commit visible | single maintainer | Kotlin blue-light filter scaffold based on Night Shift | S82 |
| CF.Lumen / f.lux Android | Root-quality reference | n/a | legacy Android root path | commercial/legacy | system-level/root quality demand, not just overlay | S21, S22, S43 |

### Commercial and platform competitors

| Product | Opportunity signal | Sources |
|---|---|---|
| Twilight | Large Android audience, smooth sun-cycle ramp, Wear OS tile, automation, Hue support, Accessibility overlay, app-specific settings, translations | S20 |
| f.lux | Root-required Android path shows quality demand for system-level transforms; desktop lineage popularized gradual color-temperature schedules | S21, S22 |
| Iris | Paywalled value clusters around PWM-aware dimming, partial-screen filters, presets, automation, color effects, multi-display support | S23 |
| CareUEyes | Commercial pricing confirms blue-light filter + dimmer + break-reminder bundle has value; break reminders are still outside OpenLumen core | S24 |
| Android Night Light | AOSP Night Light requires HWC2 color transform support; system APIs are the best path when available | S25 |
| Android Extra Dim / Night Light complaints | Built-in dimming/filtering is often too weak or inconsistent; users still seek stronger third-party/root options | S41, S44 |

### Adjacent projects worth borrowing from

| Project | Borrowable pattern | Sources |
|---|---|---|
| Redshift | Clear config model and honest FAQ about gamma-ramp limitations | S34 |
| Hyprshade | Shader preset library, on/off/toggle/current commands, scheduled activation, packaging docs | S35 |
| sunsetr | Smooth transitions, geolocation/manual time modes, hot reload, IPC automation, multi-backend strategy | S36 |
| wl-gammarelay-rs | Small DBus control surface for brightness/gamma/temperature and status-bar integrations | S37 |
| wluma | Learns brightness preferences from ambient light plus screen contents | S38 |
| Lunar | App presets, sensor/location adaptive brightness, hardware-first dimming, hotkeys | S39 |
| ScreenDimmer desktop | Hotkeys, multi-screen support, smooth transitions, local settings restore, OSD feedback | S40 |

### Community, policy, and security signals

| Source class | Signal | Sources |
|---|---|---|
| Topic/awesome indexes | The blue-light-filter ecosystem clusters around Hyprshade, sunsetr, wl-gammarelay-rs, Pixel Filter, Redshift-style CLI/config, and small Android overlays rather than large plugin ecosystems | S72 |
| Overlay security | `SYSTEM_ALERT_WINDOW` and Accessibility overlays remain tapjacking-sensitive; OpenLumen needs explicit pause/failsafe behavior and a threat model, not just permission rationales | S26, S32, S67, S68, S73 |
| Platform/distribution policy | Play distribution for `specialUse` foreground services needs a declaration, user-impact narrative, and demo evidence even if F-Droid stays primary | S29, S74 |
| Build/security advisories | Android/Kotlin projects need dependency advisory monitoring, not just version bumps; protobuf CVE scanner noise is a concrete example for Gradle dependency insight and SBOM evidence | S62, S63, S77 |
| Community complaints | Users still ask for smoother dynamic Android filters, stronger root/system-level filtering, OLED-safe black preservation, and PWM-sensitive workflows | S41, S43, S44, S78, S79, S80 |

## Prioritization Rules

Impact, effort, and risk use 1 low -> 5 high. "Parity" means catching up with common
competitor expectations. "Leapfrog" means moving materially ahead of Android OSS peers
without violating the no-network philosophy.

- Now: required for a credible v1.0/F-Droid-ready release or blocks trust.
- Next: fits the product but depends on Now hardening.
- Later: useful, but bigger surface area, device-specific, or less central.
- Under Consideration: plausible, but needs a spike because of policy, privacy,
  distribution, or dependency concerns.
- Rejected: contradicts repo philosophy, costs too much for value, or has weak evidence.

## Progress toward v0.5.0

Completed candidates (commit history is authoritative; this is the human
view). Each entry lists where the work landed.

- **C02** — In-app driver report export → `app/src/main/java/com/openlumen/diagnostics/DriverReport.kt`, Driver screen Copy/Share buttons
- **C15** — Favorite presets → `favoritePresetKeys` in `Preferences`; Presets screen star toggle; consumed by upcoming notification/widget surfaces
- **C16** — Notification preset cycle → "Next preset" action on the foreground notification; uses `PresetCycle.next`
- **C19** — Home-screen 1x1 toggle widget → `ToggleWidget` AppWidgetProvider, refresh broadcast from `LumenService.observePreferences()`
- **C03** — SurfaceFlinger code registry → `SurfaceFlingerEngine.candidatesFor(api)` per-API ladder, `activeTransactionCode` diagnostic, captured in driver report
- **C52** — Local diagnostics bundle → `DiagnosticsLog` ring-buffered event log; tail appended to every driver report
- **C53** — Structured log viewer → About → "View diagnostics log" dialog over the bounded event log
- **C04** — KCAL variant probing → `KcalEngine.CANDIDATE_BASES` with three known sysfs roots, `activeBasePath` diagnostic, captured in driver report
- **C20** — Home-screen 4x1 preset widget → `PresetWidget` renders the first four favorites as tappable swatches; click dispatches `LumenService.ACTION_SET_PRESET`
- **C23** — Smooth fixed-time transitions → `transitionDurationMs` pref + `LumenMatrix.lerp` + `LumenService.applyMatrix(target, durationMs)` ramp coroutine
- **C24** — Smooth solar transitions → same ramp path as C23; UI in Schedule tab covers both modes uniformly
- **C14** — Previous profile restore → `Preferences.previousPresetKey`, `PresetCycle.restorePrevious/setActiveKey`, in-app "Restore" affordance, `ACTION_RESTORE_PREVIOUS` intent
- **C44** — Public compatibility table → [docs/compatibility-table.md](docs/compatibility-table.md)
- **C48** — Gradle dependency verification → [docs/dependency-verification.md](docs/dependency-verification.md) (procedure; opt-in deferred until post-AGP-9)
- **C54** — Wake/alarm/battery audit → [docs/wake-and-vitals.md](docs/wake-and-vitals.md)
- **C93** — Play FGS evidence pack → [docs/play-fgs-evidence.md](docs/play-fgs-evidence.md)
- **C27** — Automatic timezone fallback → Schedule screen shows the system zone label so users know which clock fixed-time schedules fire against
- **C26** — Offline city picker → `core-schedule/OfflineCities` (~95 cities, IANA zones), search + nearest helpers, wired into LocationEntryDialog
- **C31** — Named profile library → `ProfileSnapshot` + `NamedProfile` model, `Profiles.{snapshot,apply,saveCurrentAs,loadByName,delete}` pure transforms, About-screen list UI, sanitized into `Preferences.savedProfiles` (cap 32, name ≤48 chars, last-write-wins on duplicate names)
- **C32** — Red Moon profile import (notes) → [docs/profile-import-formats.md](docs/profile-import-formats.md) (importer not implemented; format and mapping documented)
- **C33** — CF.Lumen import notes → same doc; manual mapping table provided since the source is unavailable
- **C58** — RTL layout support → manifest `supportsRtl=true` already set; layout audit conventions documented in `docs/translations.md`
- **C59** — Weblate/translation workflow → [docs/translations.md](docs/translations.md) (PR-based today; platform deferred until string count grows)
- **C99** — Event-driven ambient sampling → `LumenService` registers an `ACTION_SCREEN_OFF` receiver that invalidates the cached lux reading so stale daytime readings can't trigger the filter at dusk
- **C82** — Android 16/API 36 readiness → [docs/api-36-readiness.md](docs/api-36-readiness.md) inventory of expected behavior changes + test plan + migration policy

Design-doc deliverables (deferred implementations, durable analysis in tree):

- **C10** — Overlay blocked-touch troubleshooting → [docs/overlay-and-per-app-design.md](docs/overlay-and-per-app-design.md) + the existing user-facing entry in `docs/troubleshooting.md`
- **C11** — Per-app pause/exclusions → permission/policy analysis in `docs/overlay-and-per-app-design.md` (deferred to v0.6+/Shizuku spike)
- **C12** — Secure/install/su dialog auto-pause → same doc; shares the foreground-app-detection blocker with C11/C69
- **C28** — Direct Boot restore → design path documented (device-protected DataStore subset + `LOCKED_BOOT_COMPLETED` receiver); ship in v0.7.0
- **C69** — Per-app profiles → same blocker as C11; deferred behind the Shizuku spike
- **C90** — Emergency unlock gesture → notification action + tile + ADB command are the shipped failsafes; touch-gesture option requires a second overlay surface (deferred)
- **C95** — AGP 9 migration spike → spike branch process documented; waits for AGP 9 stable
- **C96** — Hilt Compose artifact migration → rides with C95
- **C94** — SBOM and advisory scan → [.github/workflows/sbom.yml](.github/workflows/sbom.yml) + [docs/sbom-and-advisories.md](docs/sbom-and-advisories.md)
- **C98** — Dynamic ramp duration presets → Instant / 30s / 5m / 15m / 30m radio options (Next-tier candidate, landed alongside C23/C24)
- **C51** — OWASP MASVS-lite threat model → [docs/threat-model.md](docs/threat-model.md)
- **C85** — Local panic reset on boot → `BootReceiver` skips auto-restore if `crash.log` was touched within 5 minutes of boot
- **C70** — Tasker intents → documented action surface (ACTION_TURN_ON/OFF/TOGGLE/CYCLE_PRESET/SET_PRESET/SET_INTENSITY/SET_DIM) with EXTRA_PRESET_KEY/EXTRA_VALUE
- **C71** — Shell/ADB command docs → [docs/automation.md](docs/automation.md) covers ADB, Tasker, Termux, Macrodroid, Automate
- **C29** — Versioned preference migrations → `PreferencesMigrations` runner, `schemaVersion` field, on-disk-vs-default detection in `PreferencesStore`
- **C30** — Profile import preview → `previewImport(uri)` + import-preview dialog with field-level diff in About tab
- **C05** — Root prompt safety and recovery docs → [docs/root-safety.md](docs/root-safety.md)
- **C07** — Guided WRITE_SECURE_SETTINGS grant → Driver screen now shows grant state and a copyable per-package adb command
- **C09** — Overlay alpha cap explanation → Driver screen info card when Overlay/Auto selected
- **C13** — Emergency off command → About screen ADB command with copy-to-clipboard
- **C17** — QS tile long-press deep link → `PREFERENCES_ACTIVITY` meta-data on tile service
- **C18** — QS tile secondary state label → tile subtitle now shows active preset name
- **C34** — F-Droid metadata → [fastlane/metadata/android/](fastlane/metadata/android/)
- **C37** — Reproducible build notes → [docs/reproducible-build.md](docs/reproducible-build.md)
- **C38** — Artifact attestations → `actions/attest-build-provenance@v2` in release workflow
- **C40** — README troubleshooting table → [docs/troubleshooting.md](docs/troubleshooting.md)
- **C41** — CONTRIBUTING.md → [CONTRIBUTING.md](CONTRIBUTING.md)
- **C42** — ARCHITECTURE.md → [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- **C43** — Issue templates → [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/)
- **C45** — Release checklist → [docs/release-checklist.md](docs/release-checklist.md)
- **C46** — Dependency update cadence → Dependabot weekly schedule
- **C47** — Dependabot/Renovate → [.github/dependabot.yml](.github/dependabot.yml)
- **C49** — Pin GitHub Actions → version-tag pinning policy documented in workflow files
- **C50** — No-INTERNET CI assertion → `permissions-audit` job in CI
- **C60** — Health evidence note → [docs/health-evidence.md](docs/health-evidence.md)
- **C97** — Awesome/topic-index watchlist → [docs/research-watchlist.md](docs/research-watchlist.md)
- **C100** — Medical/pain-mode disclaimer templates → covered in `docs/health-evidence.md`

Partial:

- **C01** — Device validation and driver report: in-app share UX shipped (see C02);
  per-engine smoke flow documented in `docs/device-matrix.md`; per-device coverage
  rows pending real hardware test runs.
- **C36** — Store screenshot matrix: layout in place under
  `fastlane/metadata/android/en-US/`, captures pending finalized icon (C35).

## Now: v0.5.0, Trustworthy First Public Release

1. Device validation and driver report
   - Add `docs/device-matrix.md`, a Driver screen "share report" dump, and a
     repeatable smoke-test checklist for each engine.
   - Impact 5, effort 3, risk 2. Dependencies: no new dependencies.
   - Why now: OpenLumen's claim is multi-driver quality. It cannot be trusted without
     per-device evidence and an easy bug-report path. Sources: S00, S10, S11, S25,
     S26, S48.

2. F-Droid/release packaging
   - Add `fastlane/metadata/android`, final adaptive icon/store icon, screenshots,
     privacy notes, reproducible-build notes, release checklist, artifact checksum
     verification, and an optional Play FGS evidence pack kept separate from F-Droid.
   - Impact 5, effort 3, risk 2. Dependencies: icon finalization and metadata copy.
   - Why now: the app is explicitly F-Droid-first and no-INTERNET; distribution trust
     is product functionality. Play stays optional, but the `specialUse` service must
     be documented before anyone tries that channel. Sources: S00, S11, S29, S60,
     S61, S62, S74.

3. Overlay-safe interaction model
   - Implement explicit pause paths for app/package exclusions and secure/install
     contexts, document Android 12's 0.8 obscuring-opacity cap, add SurfaceView/View
     overlay regression tests, and add emergency "turn off" paths reachable from
     notification, tile, and a documented gesture.
   - Impact 5, effort 4, risk 3. Dependencies: package visibility/usage stats spike.
   - Why now: overlay apps routinely block sensitive flows; competitors already solve
     or warn about this, and overlay abuse is explicitly called out in Android/OWASP
     security guidance. Sources: S10, S12, S18, S20, S26, S32, S42, S67, S68, S71,
     S73.

4. Command surfaces: notification, tile, widgets
   - Ship long-press QS tile destination, notification preset cycling, 1x1 toggle
     widget, and 4x1 preset widget.
   - Impact 4, effort 3, risk 2. Dependencies: profile/favorites model.
   - Why now: tile/widget/notification control is table-stakes and was already in the
     old roadmap. Sources: S10, S11, S15, S16, S17, S20.

5. Smooth transition engine
   - Add configurable fade-in/fade-out duration for fixed and solar schedules, with no
     background polling between transitions.
   - Impact 4, effort 3, risk 2. Dependencies: schedule alarm refactor and tests.
   - Why now: abrupt changes feel broken; Twilight/f.lux/sunsetr establish smooth ramps
     as a user expectation. Sources: S20, S21, S22, S36.

6. Accessibility and i18n baseline
   - Externalize all strings, add TalkBack labels/state descriptions for every slider
     and card, verify contrast/CVD previews, support RTL, and prepare translation docs.
   - Impact 4, effort 3, risk 1. Dependencies: string audit.
   - Why now: Red Moon's translation history and Android accessibility tooling show this
     belongs before broader distribution. Sources: S10, S11, S31.

7. Migration and backup schema
   - Introduce versioned profile schema, explicit migrations, import preview, named
     profiles, and "previous profile" restore.
   - Impact 4, effort 3, risk 2. Dependencies: Preferences schema versioning.
   - Why now: backup is an open Red Moon request and OpenLumen already imports JSON, so
     schema discipline prevents early data debt. Sources: S12, S30, S66.

8. Test and CI hardening
   - Add engine command unit tests, preferences import fuzz cases, Compose tests,
     connected-device smoke scripts, overlay pass-through/blocked-touch regression
     cases, `:core-*` tests in CI, release dry-run, SBOM/advisory checks, and Gradle
     dependency verification.
   - Impact 5, effort 4, risk 2. Dependencies: emulator/device availability.
   - Why now: a system-level foreground app needs more than unit tests before users
     trust it on real devices. Sources: S00, S61, S63, S64, S67, S68, S73, S77.

9. Security and supply-chain baseline
   - Add Dependabot/Renovate config, GitHub artifact attestations, action pinning
     policy, signing-key handling docs, SBOM/advisory scan, no-INTERNET permission CI
     check, and an OWASP MASVS/MASTG overlay-threat model.
   - Impact 4, effort 3, risk 2. Dependencies: release workflow cleanup.
   - Why now: supply-chain trust is part of F-Droid-style distribution. Sources: S60,
     S61, S62, S63, S64, S67, S68, S77.

10. Responsible health-claims copy
    - Replace strong medical promises with comfort/circadian-language plus a short
      evidence note; never market OpenLumen as treatment for sleep, migraine, PWM
      sensitivity, or eye disease.
    - Impact 3, effort 1, risk 1. Dependencies: README/About copy audit.
    - Why now: community and literature both show demand and skepticism; honest wording
      protects users and maintainers. Sources: S12, S45, S46, S47, S80.

## Next: v0.6.0 -> v0.8.0

1. Per-app profiles
   - Add opt-in per-app preset switching using the least-invasive viable signal. Prefer
     event-driven approaches; avoid one-second foreground polling.
   - Impact 5, effort 5, risk 4. Dependencies: privacy UX, package visibility,
     overlay-safe pause model. Sources: S10, S12, S15, S20, S39.

2. Tasker and intent automation
   - Add documented broadcast/deep-link actions for toggle, off, preset, intensity,
     dim, and schedule refresh.
   - Impact 4, effort 2, risk 2. Dependencies: command parser and permission policy.
     Sources: S15, S20, S35, S37.

3. Optional Shizuku/ADB high-quality backend
   - Spike a Shizuku-backed privileged path for secure settings/system color control
     without root. Keep it optional and outside the core no-dependency path if needed.
   - Impact 5, effort 5, risk 4. Dependencies: policy review and F-Droid flavor
     decision. Sources: S12, S25, S33, S43.

4. Driver compatibility learning
   - Expand SurfaceFlinger transaction probing, KCAL sysfs variant detection, and
     device report templates. Store only local data unless users manually share.
   - Impact 4, effort 4, risk 3. Dependencies: driver report. Sources: S00, S25,
     S43, S48.

5. Preset system v2
   - Favorites, previous profile, Kelvin-facing UI, contrast control, named profile
     library, import/export of preset packs.
   - Impact 4, effort 3, risk 2. Dependencies: migration/profile schema. Sources:
     S10, S12, S13, S20, S23.

6. Wear OS control surface
   - Phone-side companion controls and a Wear tile for toggle/preset only; no watch-side
     display filtering promise.
   - Impact 3, effort 4, risk 3. Dependencies: stable command API. Sources: S20, S49.

7. Direct Boot and resilience
   - Add device-protected minimal state for reboot restore before unlock, emergency
     failsafe docs, and recovery instructions for root modes.
   - Impact 4, effort 4, risk 3. Dependencies: storage migration. Sources: S11, S27,
     S66.

8. Dependency and target upgrade track
   - Move stable dependencies deliberately, then target the next Android SDK after
     real-device validation. Add explicit AGP 9 and Hilt artifact migration spikes,
     but do not chase alpha releases in release branches.
   - Impact 4, effort 3, risk 3. Dependencies: CI matrix. Sources: S49, S50, S51,
     S52, S53, S54, S55, S56, S57, S58, S59, S75, S76.

9. Research watchlist
   - Keep a small `docs/research-watchlist.md` for topic-index harvesting, platform
     policy changes, and competitor issue patterns that should be reviewed before each
     release planning pass.
   - Impact 3, effort 1, risk 1. Dependencies: none.
   - Why next: the ecosystem is fragmented and small; a maintained watchlist prevents
     roadmap drift without adding runtime surface. Sources: S12, S72.

## Later: v0.9.0 -> post-v1.0

- Android TV flavor with D-pad UI, leanback metadata, and remote-safe overlay UX.
  Sources: S16, S20.
- AMOLED-aware black clamp and content-aware dimming. Sources: S20, S23, S38, S39,
  S40, S69, S70.
- One-handed, split-screen, foldable, keyboard, and notification-shade overlay coverage
  after the baseline overlay safety model lands. Sources: S12, S69, S70, S73.
- Color-vision-deficiency LUT correction if implementable without heavy OpenCV
  dependency bloat. Sources: S12, S13, S31, S45.
- Local diagnostics viewer with event timeline, driver probe history, wake/alarm
  history, and exportable text bundle. Sources: S00, S64.
- Multi-user/work-profile behavior and enterprise/admin-device notes. Sources: S64.
- Small preset-pack format, not executable plugins. Sources: S35, S36.
- Play Store listing if it can be done without compromising no-network defaults,
  foreground-service policy clarity, or F-Droid priority. Sources: S20, S29.

## Under Consideration

- Optional location provider flavor using FusedLocationProvider. Fit is medium:
  automatic coordinates are useful, but Play Services is not F-Droid-clean. Sources:
  S11, S20.
- AccessibilityService overlay extension. Fit is medium: it can cover lock screen and
  notification areas, but it is sensitive and must not become a broad data collection
  surface. Sources: S17, S20, S31, S64.
- Reduce Bright Colors / system Extra Dim integration through ADB/root/Shizuku. Fit is
  high if it works, but device/API behavior needs a spike. Sources: S25, S27, S33, S44.
- Partial-screen filters for migraine/reading workflows. Fit is uncertain: evidence and
  implementation complexity are higher than baseline display tinting. Sources: S23, S45,
  S46.
- PWM-sensitive mode guidance. Fit is plausible but risky: users ask for overlay-at-high
  brightness workflows, but the app should not make device-health claims without
  hardware-specific evidence. Sources: S23, S69, S80.

## Rejected

- Network telemetry, remote crash reporting, remote config, or analytics in the main
  app. Contradicts no-INTERNET and user trust. Sources: S00, S17, S60, S64.
- Ads, account login, cloud sync, or paywalled core functionality. Contradicts
  F-Droid-first OSS positioning. Sources: S00, S24.
- Local HTTP/MQTT/Home Assistant control in the main app. Requires INTERNET and expands
  attack surface; consider only as a separate companion later. Sources: S00, S64.
- Philips Hue or smart-light integrations in the main app. Useful commercially, but
  network permissions conflict with OpenLumen's default philosophy. Sources: S20.
- Strong medical efficacy claims. Evidence is mixed and should be phrased as
  comfort/circadian-light control, not treatment. Sources: S45, S46, S47.
- Heavy general plugin ecosystem. The maintenance/security cost is not justified for a
  small privileged display utility. Sources: S35, S36, S64.
- Continuous foreground-app polling every second. Red Moon documents it, but the privacy
  and battery cost is too high unless replaced by opt-in event-driven mechanisms.
  Sources: S10, S64.

## Candidate Inventory

| ID | Candidate | Category | Prev | Tier | I/E/R | Deps / effort sketch | Placement reason | Sources |
|---|---|---|---|---|---|---|---|---|
| C01 | Real-device driver matrix | reliability | table-stakes | Now | 5/3/2 | Manual and scripted tests on Pixel, Samsung, Snapdragon+KCAL, non-root overlay | Validates central multi-driver claim before wider release | S00,S25,S48 |
| C02 | In-app driver report export | observability | rare | Now | 5/2/1 | Serialize probes, API level, permissions, engine result, no PII | Turns user bug reports into actionable driver data | S10,S48,S64 |
| C03 | SurfaceFlinger code registry | platform/OS | rare | Next | 4/4/3 | Expand candidates and log which code works per device | Useful only after reports exist | S00,S25,S48 |
| C04 | KCAL variant probing | platform/OS | rare | Next | 4/4/3 | Probe known sysfs paths and safe write/read checks | High value for root users, device-specific risk | S00,S43 |
| C05 | Root prompt safety and recovery docs | docs/security | table-stakes | Now | 4/1/1 | README and Driver screen recovery steps | Root mode can black-screen or misconfigure devices | S11,S43 |
| C06 | Shizuku privileged backend | platform/OS | rare | Under Consideration | 5/5/4 | Spike optional module/flavor using Shizuku permission model | Could leapfrog OSS peers but policy/distribution risk is real | S12,S25,S33 |
| C07 | Guided WRITE_SECURE_SETTINGS grant | UX/dev-experience | common | Now | 4/2/2 | Copyable command, state detection, warning text | CDM path currently needs clearer onboarding | S00,S25 |
| C08 | System Extra Dim / Reduce Bright Colors spike | platform/OS | rare | Under Consideration | 4/4/3 | Test secure settings across OEMs with ADB/root/Shizuku | Users want deeper-than-minimum dim without overlay artifacts | S27,S33,S44 |
| C09 | Overlay alpha cap explanation | docs/UX | table-stakes | Now | 3/1/1 | Add UI/help text tied to Android 12 cap | Prevents false quality expectations for overlay fallback | S26,S42 |
| C10 | Overlay blocked-touch troubleshooting | reliability | common | Now | 4/2/2 | Detect/log common occlusion failures and link pause UX | Android blocks untrusted touches when obscured | S18,S26,S42 |
| C11 | Per-app pause/exclusions | UX/security | table-stakes | Now | 5/4/3 | Package selection, pause policy, minimal permission strategy | Red Moon/Twilight users expect it and overlays need it | S10,S12,S20 |
| C12 | Secure/install/su dialog auto-pause | security | common | Now | 5/4/3 | Detect installer/permission flows or provide manual fast pause | Prevents blocked install buttons and root prompt issues | S18,S26 |
| C13 | Emergency off command | reliability | table-stakes | Now | 5/2/1 | Notification action, tile action, adb command docs | Root/overlay display apps need a guaranteed escape hatch | S11,S20 |
| C14 | Previous profile restore | UX | common | Next | 4/2/1 | Store previous named profile before temporary changes | Open Red Moon request and useful for automation | S12 |
| C15 | Favorite presets | UX | table-stakes | Now | 4/2/1 | Add favorites list for notification/widget cycling | Needed by command surfaces | S10,S20 |
| C16 | Notification preset cycle | UX | common | Now | 4/2/1 | Add actions and service command handling | Competitors expose quick notification controls | S10,S14,S16 |
| C17 | QS tile long-press deep link | UX | common | Now | 3/1/1 | Override tile long-click metadata where supported | Already in old roadmap and low risk | S00,S15 |
| C18 | QS tile secondary state label | UX/accessibility | common | Now | 3/1/1 | Show active preset/driver in tile state | Improves control at a glance | S20 |
| C19 | Home-screen 1x1 toggle widget | mobile | table-stakes | Now | 4/3/2 | AppWidgetProvider and service command path | Red Moon and Twilight establish widgets as expected | S10,S11,S20 |
| C20 | Home-screen 4x1 preset widget | mobile | common | Now | 4/3/2 | Reuse favorites and command path | High convenience after favorites exist | S10,S11 |
| C21 | Wear OS tile control | mobile | common | Next | 3/4/3 | Companion command surface, phone-side only | Fit is good but depends on stable commands | S20 |
| C22 | Android TV flavor | mobile/distribution | rare | Later | 3/4/3 | TV navigation, manifest, overlay permission UX | Useful niche after phone release hardening | S16,S20 |
| C23 | Smooth fixed-time transitions | UX | table-stakes | Now | 4/3/2 | Schedule ramp state and tests | Abrupt changes lag commercial expectations | S20,S21,S36 |
| C24 | Smooth solar transitions | UX | table-stakes | Now | 4/3/2 | Solar offsets plus ramp duration | Twilight/sunsetr make this expected | S20,S36 |
| C25 | Alarm-based schedule presets | UX | common | Next | 3/2/1 | Use existing AlarmManager machinery | Twilight exposes alarm/custom modes | S20 |
| C26 | Offline city picker | UX/offline | common | Next | 3/3/2 | Bundle compact city coordinate list or generated asset | Improves solar schedule without Play Services | S36 |
| C27 | Automatic timezone fallback | reliability | common | Next | 3/2/1 | Use system zone plus explicit display in UI | Prevents coordinate/time mismatch confusion | S36 |
| C28 | Direct Boot restore | reliability | rare | Next | 4/4/3 | Move minimal enabled state to device-protected storage | Valuable, but bigger storage migration | S00,S27,S66 |
| C29 | Versioned preference migrations | migration | table-stakes | Now | 4/3/2 | Add schema version, migration runner, tests | Existing JSON import makes future drift likely | S00,S30,S66 |
| C30 | Profile import preview | data/UX | common | Now | 4/2/1 | Decode, validate, show diff, then commit | Reduces risk from imported profiles | S30,S64 |
| C31 | Named profile library | data/UX | table-stakes | Next | 4/3/2 | Store multiple profile objects and active key | Needed for widgets and per-app profiles | S10,S20 |
| C32 | Red Moon profile import | migration | rare | Later | 2/4/2 | Map legacy schema if documented enough | Nice migration path, limited source evidence | S10,S11 |
| C33 | CF.Lumen import notes | migration/docs | rare | Later | 2/3/2 | Document manual mapping only unless format found | Helps lineage but not core | S21,S22 |
| C34 | F-Droid metadata | distribution | table-stakes | Now | 5/2/1 | `fastlane/metadata/android` and app metadata | Required by stated distribution target | S00,S60 |
| C35 | Final icon and launcher assets | distribution/UX | table-stakes | Now | 4/2/1 | Finish adaptive icon and store icon | Existing branding checklist is incomplete | S00,S60 |
| C36 | Store screenshot matrix | distribution/docs | table-stakes | Now | 4/2/1 | Capture phone/tablet dark UI screenshots | Needed for F-Droid/Play credibility | S11,S60 |
| C37 | Reproducible build notes | distribution/security | common | Now | 5/3/2 | Document environment, Gradle, signing, checksums | F-Droid trust depends on reproducibility | S60 |
| C38 | Artifact attestations | security/distribution | common | Now | 4/2/2 | Add provenance workflow for release artifacts | Improves binary trust beyond checksums | S63 |
| C39 | Play Store listing | distribution | common | Under Consideration | 2/3/3 | Policy review for specialUse FGS and permissions | Optional; F-Droid remains primary | S20,S29 |
| C40 | README troubleshooting table | docs | table-stakes | Now | 4/2/1 | Add driver/permission/OEM failure matrix | Reduces support load | S10,S26,S27 |
| C41 | CONTRIBUTING.md | docs/dev-experience | table-stakes | Now | 3/2/1 | Document code style, tests, PR expectations | Red Moon shows community docs help maintenance | S10 |
| C42 | ARCHITECTURE.md | docs/dev-experience | common | Now | 4/2/1 | Explain modules, service lifecycle, engine contract | Needed before external contributors touch drivers | S00,S10 |
| C43 | Issue templates | docs/observability | table-stakes | Now | 4/1/1 | Device report, driver bug, overlay bug templates | Converts issues into usable compatibility data | S10,S48 |
| C44 | Public compatibility table | docs/data | common | Next | 4/2/1 | Derive from reports and manual validation | Helps users choose engines | S10,S48 |
| C45 | Release checklist | distribution/reliability | table-stakes | Now | 5/2/1 | Pre-release commands, permission proof, checksum proof | Prevents half-shipped APKs | S00,S60 |
| C46 | Dependency update cadence | upgrade strategy | table-stakes | Now | 4/2/2 | Stable-only policy with monthly review | Current stack is behind many stable lines and AGP/Hilt migrations have breaking-shape risk | S49-S59,S75,S76 |
| C47 | Dependabot/Renovate | security/upgrade | table-stakes | Now | 4/2/2 | Configure Gradle ecosystems and labels | Reduces stale dependency risk | S62,S49-S59 |
| C48 | Gradle dependency verification | security | common | Now | 4/2/2 | Add verification metadata and update procedure | Protects build supply chain | S61,S64 |
| C49 | Pin GitHub Actions | security | common | Now | 4/2/2 | Pin actions to SHAs or document allowed tags | Tightens release workflow trust | S63,S64 |
| C50 | No-INTERNET CI assertion | security/privacy | rare | Now | 5/1/1 | Inspect merged manifest or APK permissions | Enforces core promise automatically | S00,S48 |
| C51 | OWASP MASVS-lite threat model | security | common | Now | 4/3/2 | Map storage, platform interaction, privacy, code quality | Privileged display apps deserve explicit threat boundaries | S64 |
| C52 | Local diagnostics bundle | observability | common | Later | 4/3/2 | Export logs, probe results, alarm state, app version | Useful after driver reporting lands | S00,S65 |
| C53 | Structured log viewer | observability | rare | Later | 3/3/2 | Store bounded local event timeline | Helpful but not release-blocking | S00,S65 |
| C54 | Wake/alarm/battery audit | performance | common | Now | 4/3/2 | Tests and docs against Android vitals categories | Schedule/service app must not harm battery | S65 |
| C55 | Accessibility Scanner pass | accessibility | table-stakes | Now | 4/2/1 | Run scanner and fix labels, touch targets, contrast | Required before public release | S31 |
| C56 | Dynamic font scale support | accessibility | common | Now | 3/2/1 | Verify Compose layouts at high font scales | Dense controls need scaling validation | S31 |
| C57 | CVD contrast preview audit | accessibility | common | Now | 3/2/1 | Use Android Studio UI check and screenshots | Color-heavy app must stay usable | S31 |
| C58 | RTL layout support | i18n | common | Now | 3/2/1 | Externalize strings, test RTL, avoid hard-coded direction | Required for translation readiness | S11,S31 |
| C59 | Weblate/translation workflow | i18n | common | Next | 3/3/2 | Add contribution docs and hosted translation decision | Red Moon gained value from translations | S10,S11 |
| C60 | Health evidence note | docs/licensing | common | Now | 3/1/1 | Add short About/README note and avoid treatment claims | Evidence is mixed; honesty is safer | S45,S46,S47 |
| C61 | Melanopic/circadian estimate UI | data/UX | rare | Later | 2/4/3 | Approximate only; must not overclaim | Interesting but easy to mislead | S23,S45,S46 |
| C62 | Research-based preset labels | UX/docs | common | Next | 3/2/2 | Rename/explain presets with non-medical language | Improves trust without changing drivers | S20,S45-S47 |
| C63 | Color-vision LUT correction | accessibility | rare | Later | 3/5/3 | Precomputed LUT or small matrix set, no heavy OpenCV | Useful but out of v1 core | S13,S31 |
| C64 | Contrast control | UX/accessibility | common | Next | 4/3/2 | Matrix/gamma UI plus tests | Open Red Moon request and useful for readability | S12 |
| C65 | Kelvin temperature UI | UX | table-stakes | Next | 4/3/2 | Convert slider presets to Kelvin-like UX where accurate | Users know color temperature vocabulary | S20,S25,S34 |
| C66 | AMOLED black clamp | performance/UX | rare | Later | 3/4/3 | Root/framebuffer path first; overlay cannot preserve black | Differentiator but hardware-sensitive | S20,S23,S38 |
| C67 | Content-aware dimming | performance/data | rare | Later | 3/5/4 | Screen capture or accessibility data likely sensitive | Borrowable from wluma but risky on Android | S38,S39 |
| C68 | Partial-screen filters | UX/accessibility | rare | Under Consideration | 2/5/3 | Requires overlay/shader-like surface and clear use case | Iris has signal; OpenLumen fit uncertain | S23 |
| C69 | Per-app profiles | integrations/UX | common | Next | 5/5/4 | Requires package/app-state signal and privacy controls | High demand but permission-sensitive | S10,S20,S39 |
| C70 | Tasker intents | integrations | common | Next | 4/2/2 | Broadcast/deep-link command API | Low dependency after command model | S15,S20 |
| C71 | Shell/ADB command docs | dev-experience | common | Next | 3/1/1 | Document `am startservice` commands | Helps power users and testing | S35,S37 |
| C72 | IPC/local socket automation | integrations | rare | Later | 2/4/3 | Android app-local binder/socket only | Borrowable pattern, not urgent | S36,S37 |
| C73 | Local HTTP toggle | integrations | rare | Rejected | 2/3/4 | Requires INTERNET and network hardening | Contradicts default no-INTERNET promise | S00,S64 |
| C74 | MQTT/Home Assistant bridge | integrations | rare | Rejected | 2/4/4 | Requires network and auth | Better as external companion, not main app | S00,S64 |
| C75 | Philips Hue integration | integrations | common commercial | Rejected | 2/4/4 | Requires network/smart-light permissions | Twilight has it, but it violates core scope | S20 |
| C76 | Ads/paywall | licensing | commercial | Rejected | 1/3/4 | Business model change | Contradicts F-Droid-first GPL utility | S00,S24 |
| C77 | Remote telemetry/crash reporting | telemetry | common commercial | Rejected | 2/3/5 | Requires network and privacy policy | Directly conflicts with no-INTERNET | S00,S64 |
| C78 | Cloud sync/accounts | data | common commercial | Rejected | 2/4/5 | Requires backend/auth/network | Not aligned with offline local utility | S00 |
| C79 | AccessibilityService as default backend | platform/OS | common | Under Consideration | 3/4/4 | Deep privacy/policy review | Useful coverage but must remain optional | S17,S20,S31 |
| C80 | UsageStats app-state detection | privacy/integrations | common | Under Consideration | 4/4/4 | Requires opt-in and clear retention policy | Needed for per-app rules if less invasive options fail | S10,S20,S64 |
| C81 | Work profile/multi-user behavior | platform/OS | rare | Later | 2/4/3 | Test profile contexts and app visibility | Important for polish, not v1 blocker | S64 |
| C82 | Android 16/API 36 readiness | upgrade strategy | common | Next | 4/3/3 | Track target SDK, FGS, overlays, exact alarm behavior | OpenLumen targets modern Android and must keep pace | S26,S27,S28,S29 |
| C83 | Compose screenshot tests | testing | common | Now | 4/3/2 | JVM/host screenshots or emulator snapshots | Catches UI regressions in dense controls | S31 |
| C84 | Connected permission flow tests | testing | common | Now | 5/4/2 | Emulator/device tests for overlay, notification, exact alarm | Required for a permission-heavy app | S26,S27,S28 |
| C85 | Local panic reset on boot | reliability | rare | Next | 4/3/2 | Detect repeated crash/failed clear and force identity | Safety net for root/driver mistakes | S11,S43 |
| C86 | System brightness write support | platform/OS | common | Under Consideration | 3/4/3 | WRITE_SETTINGS UX and OEM behavior tests | Useful but can confuse with color transform | S11,S16,S20 |
| C87 | Break reminders | UX | common commercial | Rejected | 1/3/2 | New workflow outside display filtering | CareUEyes value, but not OpenLumen's purpose | S24 |
| C88 | Browser/desktop companion | plugin ecosystem | rare | Rejected | 1/5/4 | Separate app and sync protocol | Dilutes Android display-filter focus | S23,S34 |
| C89 | Pixel-grid AMOLED dimming mode | performance/UX | rare | Under Consideration | 3/5/4 | Overlay bitmap/grid with pattern shifting and OLED warnings | Pixel Filter proves demand, but Android 8+ overlay coverage and burn-in perception make it risky | S69,S70,S80 |
| C90 | Emergency unlock gesture | reliability/security | common | Now | 5/2/2 | Add optional multi-tap/long-press corner gesture plus notification/tile failsafe | A display app needs a non-visual escape path when overlay/root modes go wrong | S11,S71 |
| C91 | SurfaceView/TextureView overlay regression suite | testing/reliability | rare | Now | 4/3/2 | Test overlay view type, alpha, touch pass-through, IME, and blocked-touch logs | Stack Overflow reports SurfaceView-specific breakage under modern untrusted-touch rules | S26,S73 |
| C92 | One-handed/foldable/windowing overlay coverage | mobile/platform | rare | Next | 3/4/3 | Device matrix cases for one-handed mode, split-screen, keyboard, nav shade, foldables | Red Moon and Pixel Filter issues show edge-window modes break dimming guarantees | S12,S69,S70,S73 |
| C93 | Play FGS evidence pack | distribution/docs | common | Now | 3/2/2 | Store declaration text, user-impact proof, screenshots/video checklist outside F-Droid path | Optional Play release will fail without a clear `specialUse` service justification | S29,S74 |
| C94 | SBOM and advisory scan | security/dev-experience | table-stakes | Now | 4/3/2 | Add dependency report, advisory review procedure, and release artifact evidence | CVE scanner noise still requires a documented triage path for Android/Kotlin builds | S62,S63,S77 |
| C95 | AGP 9 migration spike | upgrade strategy | emerging | Next | 3/4/3 | Branch-only spike for built-in Kotlin support and Gradle/API compatibility | AGP 9 changes plugin behavior enough to deserve an isolated migration plan | S75 |
| C96 | Hilt Compose artifact migration | upgrade strategy | emerging | Next | 3/2/2 | Move `hiltViewModel()` imports/artifacts when adopting newer Hilt | AndroidX Hilt moved Compose APIs; defer until dependency track starts | S76 |
| C97 | Awesome/topic-index watchlist | docs/dev-experience | rare | Next | 3/1/1 | Keep a small harvested-source list reviewed before each planning pass | The ecosystem is fragmented across topic indexes and small repos | S72 |
| C98 | Dynamic ramp duration presets | UX/accessibility | common | Next | 4/2/1 | Presets for instant, 5m, 15m, 30m transition windows | Recent community requests still ask for f.lux-like gradual Android transitions | S20,S36,S79 |
| C99 | Event-driven ambient sampling | performance/reliability | rare | Next | 4/3/2 | Sample light sensor on screen-on/config events instead of continuous polling where possible | Keeps adaptive behavior while respecting battery/vitals constraints | S38,S65 |
| C100 | Medical/pain-mode disclaimer templates | docs/licensing | rare | Now | 3/1/1 | Add explicit non-treatment wording for PWM, migraine, sleep, and circadian copy | Community demand is real but claims need strict evidence boundaries | S45,S46,S47,S80 |

## Source Appendix

Local evidence:

- S00: Local repo reconnaissance on 2026-05-16: `README.md`, `CHANGELOG.md`,
  `ROADMAP.md`, `LICENSE`, `.github/workflows/*`, `gradle/libs.versions.toml`,
  manifests, Kotlin source, tests, and last 17 commits.

External URLs:

- S10: Red Moon GitHub, https://github.com/LibreShift/red-moon
- S11: Red Moon F-Droid, https://f-droid.org/en/packages/com.jmstudios.redmoon/
- S12: Red Moon open enhancement issues, https://github.com/LibreShift/red-moon/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement
- S13: Red Moon closed enhancement issues, https://github.com/LibreShift/red-moon/issues?q=is%3Aissue+is%3Aclosed+label%3Aenhancement
- S14: Shades, https://github.com/cngu/shades
- S15: Night-Light, https://github.com/farmerbb/Night-Light
- S16: DimTV, https://github.com/MarshMeadow/DimTV
- S17: Low Brightness, https://github.com/MihaiCristianCondrea/Low-Brightness-for-Android
- S18: Screen Filter, https://github.com/tranleduy2000/screenfilter
- S19: Eye-Rest, https://github.com/Dzhuneyt/android-app-eye-rest-blue-light-filter
- S20: Twilight on Google Play, https://play.google.com/store/apps/details?id=com.urbandroid.lux
- S21: TechCrunch on f.lux Android, https://techcrunch.com/2016/03/15/popular-blue-light-reducing-app-f-lux-arrives-on-android/
- S22: Android Police on f.lux Android beta, https://www.androidpolice.com/2016/02/19/popular-display-tweaking-app-f-lux-is-coming-to-android-available-now-in-beta-root-required/
- S23: Iris product page, https://iristech.co/iris/
- S24: CareUEyes pricing, https://care-eyes.com/buy.html
- S25: AOSP Night Light implementation, https://source.android.com/docs/core/display/night-light
- S26: Android 12 untrusted touch events, https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events
- S27: Android 12 exact alarm behavior, https://developer.android.com/about/versions/12/behavior-changes-12#exact-alarm-permission
- S28: Android notification runtime permission, https://developer.android.com/develop/ui/views/notifications/notification-permission
- S29: Foreground service types, special use, https://developer.android.com/develop/background-work/services/fgs/service-types
- S30: Android Storage Access Framework, https://developer.android.com/training/data-storage/shared/documents-files
- S31: Android accessibility testing, https://developer.android.com/guide/topics/ui/accessibility/testing
- S32: `HIDE_OVERLAY_WINDOWS`, https://developer.android.com/reference/android/Manifest.permission#HIDE_OVERLAY_WINDOWS
- S33: Shizuku setup guide, https://shizuku.rikka.app/guide/setup/
- S34: Redshift, https://github.com/sharpbracket/redshift
- S35: Hyprshade, https://github.com/loqusion/hyprshade
- S36: sunsetr, https://github.com/psi4j/sunsetr
- S37: wl-gammarelay-rs, https://github.com/MaxVerevkin/wl-gammarelay-rs
- S38: wluma, https://github.com/max-baz/wluma
- S39: Lunar, https://github.com/alin23/Lunar
- S40: ScreenDimmer, https://github.com/datbnh/ScreenDimmer
- S41: Reddit screen dimming apps, https://www.reddit.com/r/androidapps/comments/1emudmo
- S42: Reddit overlay apps interfering with touch, https://www.reddit.com/r/lgv20/comments/d0r4kb
- S43: Reddit root vs overlay quality discussion, https://www.reddit.com/r/androidapps/comments/lk8sbv
- S44: Reddit dim actual backlight past limits, https://www.reddit.com/r/AndroidHelp/comments/1jhzmi1
- S45: Blue-light blocking glasses systematic review, https://pmc.ncbi.nlm.nih.gov/articles/PMC12668929/
- S46: Circadian lighting consensus, https://www.frontiersin.org/journals/photonics/articles/10.3389/fphot.2023.1272934
- S47: Blue-light exposure intervention review, https://academic.oup.com/sleepadvances/article/doi/10.1093/sleepadvances/zpaa002/5851240
- S48: OpenLumen GitHub remote, https://github.com/SysAdminDoc/OpenLumen
- S49: AGP Maven metadata, https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- S50: Kotlin Android Gradle plugin metadata, https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml
- S51: Compose BOM metadata, https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- S52: Activity Compose metadata, https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml
- S53: Lifecycle runtime metadata, https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml
- S54: Navigation Compose metadata, https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml
- S55: DataStore preferences metadata, https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml
- S56: Compose Material3 metadata, https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml
- S57: Hilt metadata, https://repo.maven.apache.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml
- S58: kotlinx.serialization JSON metadata, https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml
- S59: kotlinx.coroutines Android metadata, https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml
- S60: F-Droid build metadata reference, https://f-droid.org/docs/Build_Metadata_Reference/
- S61: F-Droid reproducible builds, https://f-droid.org/docs/Reproducible_Builds/
- S62: GitHub Dependabot version updates, https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/about-dependabot-version-updates
- S63: GitHub artifact attestations, https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations/using-artifact-attestations-to-establish-provenance-for-builds
- S64: OWASP MASVS, https://mas.owasp.org/MASVS/
- S65: Android vitals, https://developer.android.com/topic/performance/vitals
- S66: Android DataStore, https://developer.android.com/topic/libraries/architecture/datastore
- S67: OWASP MASTG overlay attacks, https://mas.owasp.org/MASTG/knowledge/android/MASVS-PLATFORM/MASTG-KNOW-0022/
- S68: OWASP MASTG testing for overlay attacks, https://mas.owasp.org/MASTG/tests/android/MASVS-PLATFORM/MASTG-TEST-0035/
- S69: Pixel Filter GitHub, https://github.com/pelya/screen-dimmer-pixel-filter
- S70: Pixel Filter F-Droid mirror, https://jans23.gitlab.io/fdroid-website/en/packages/screen.dimmer.pixelfilter/
- S71: Screen Dimming GitHub, https://github.com/Darexsh/Screen_Dimming
- S72: Ecosyste.ms blue-light-filter topic index, https://repos.ecosyste.ms/topics/blue-light-filter
- S73: Stack Overflow on SYSTEM_ALERT_WINDOW and SurfaceView behavior, https://stackoverflow.com/questions/76411479/android-11-system-alert-window-behaviour-changes-with-surfaceview
- S74: Google Play foreground service requirements, https://support.google.com/googleplay/android-developer/answer/13392821
- S75: Android Gradle Plugin 9 release notes, https://developer.android.com/build/releases/agp-9-0-0-release-notes
- S76: AndroidX Hilt release notes, https://developer.android.com/jetpack/androidx/releases/hilt
- S77: GitHub Advisory Database for CVE-2024-7254/protobuf-java, https://github.com/advisories/GHSA-735f-pc8j-v9w8
- S78: Hacker News f.lux discussion, https://news.ycombinator.com/item?id=30626803
- S79: Reddit dynamic blue-light-filter request, https://www.reddit.com/r/androidapps/comments/1rxmi3v/looking_for_a_dynamic_blue_light_filter/
- S80: Reddit PWM-sensitive overlay discussion, https://www.reddit.com/r/PWM_Sensitive/comments/1obqbsz/does_using_oled_screen_at_100_brightness_with_an/
- S81: dim_overlay_app GitHub, https://github.com/Ayuj-Mondal/dim_overlay_app
- S82: SwingShift GitHub, https://github.com/alexwelsby/swingshift

## Phase 5 Self-Audit

- Traceability: every roadmap candidate has at least one source ID; rejected items
  cite the source class that makes the rejection necessary.
- Tier consistency: Now items are release trust blockers or old-roadmap items with
  strong external evidence; Next items depend on Now foundations; Later items are
  useful but higher effort or narrower; rejected items contradict the core philosophy.
- Required category coverage:
  - Security: C05, C09-C13, C38, C48-C51, C73-C79, C90, C94.
  - Accessibility: C55-C58, C63-C64, C98, C100.
  - i18n/l10n: C58-C59.
  - Observability/telemetry: C02, C43, C52-C54, C77, C94.
  - Testing: C08, C29, C45, C47-C50, C83-C84, C91, C94.
  - Docs: C05, C09, C40-C45, C60, C71, C93, C97, C100.
  - Distribution/packaging: C34-C39, C45, C50, C93-C94.
  - Plugin ecosystem/integrations: C70-C75, C88, C97.
  - Mobile: C19-C22, C81-C82, C89, C92.
  - Offline/resilience: C13, C28-C31, C50, C85, C90, C99.
  - Multi-user/collab: C44, C81.
  - Migration paths: C29-C33.
  - Upgrade strategy: C46-C47, C82, C95-C96.
- Hostile-review fixes applied: no unsourced competitor claims; no fabricated star
  counts where unavailable; health claims are demoted to careful evidence notes;
  networked ideas are rejected or pushed out of core; Shizuku, AccessibilityService,
  pixel-grid dimming, and PWM-sensitive workflows remain under consideration because
  of policy, platform, privacy, or evidence risk.
