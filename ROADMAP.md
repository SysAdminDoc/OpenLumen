# OpenLumen Roadmap

Research version: 2026-05-17 **rev 5**. Rev 5 is the third walk-away
pass on the same day. It preserves rev 4.1 and adds a distribution /
platform / CI refresh from live primary sources: Android developer
verification, Android 17 Beta 4 behavior changes, AGP 9.2 / Gradle
9.4.1 compatibility, Dagger/Hilt 2.59.2 constraints, AndroidX current
stable versions, GitHub Actions Node 24 migration, and current action
major versions.

## Implementation progress after rev 5

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
- **Dependency targets are now more concrete**. AGP 9.2.0 requires
  Gradle 9.4.1 and supports API 36.1; DataStore 1.2.1 is the stable
  Direct Boot floor; AndroidX current stable versions have moved well
  beyond the repo's current floor; Dagger/Hilt 2.59.2 is current but
  its Hilt Gradle plugin now requires AGP 9. This sharpens C95/C96/C124
  rather than adding a separate feature. Sources: S237-S241, S252-S253.
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
| C144 | AndroidX stable baseline refresh batch | upgrade strategy | Next | 3/2/2 | After C95 lands, refresh core/activity/lifecycle/navigation/DataStore as one AndroidX batch and run unit tests, lint, Compose UI smoke, and profile import/export. Keep alpha trains out unless a candidate explicitly needs them. | Current stable AndroidX releases are far ahead of the repo floor; batching avoids mixing dependency churn with AGP 9 toolchain risk. | S237, S238, S239, S252, S253 |

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
  the C95 migration PR: Compose BOM `2024.12.01 → 2026.05.00`,
  Material 3 `1.3.1 → 1.4.0`, do NOT adopt `material3-expressive` yet
  (still alpha). `material-icons-extended` is deprecated — track as
  C137. The Compose migration is one PR, low risk.
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
awaits the device-validation gate. Stack: Kotlin 2.1.0, AGP 8.7.3, JDK 17,
Jetpack Compose, Material 3, Hilt, DataStore, kotlinx.serialization.
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
- Final adaptive icon and store screenshots are still placeholder.
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
- **C48** Gradle dependency verification — procedure documented ([docs/dependency-verification.md](docs/dependency-verification.md)); enforcement deferred to post-AGP 9 to avoid trampling Dependabot PRs
- **C49** Pin GitHub Actions
- **C50** No-INTERNET CI assertion — `permissions-audit` job
- **C51** OWASP MASVS-lite threat model ([docs/threat-model.md](docs/threat-model.md))
- **C52** Local diagnostics bundle — `DiagnosticsLog` ring-buffered event log, tail in driver report
- **C53** Structured log viewer — About → "View diagnostics log"
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
- **C28** Direct Boot restore — design documented; now simpler with the new DataStore APIs (S95)
- **C69** Per-app profiles — same Shizuku blocker
- **C90** Emergency unlock gesture — notification/tile/ADB shipped; touch gesture deferred
- **C95** AGP 9 migration spike — **promoted to Now** in rev 3
- **C96** Hilt Compose artifact migration — **promoted to Now** in rev 3

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
  finalized icon (C35).
- **C55/C56/C57** Accessibility scanner / dynamic font scale / CVD
  contrast audit — still need a real device pass.

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
   - Finalize the adaptive icon (C35), capture phone screenshots into
     `fastlane/metadata/android/en-US/images/phoneScreenshots/` (C36),
     confirm reproducibility on F-Droid's build server (C37), and walk
     the pre-release checklist (C45). The 70% translation floor (S111)
     applies for translated releases but the en-US baseline is enough
     to ship.
   - Impact 5, effort 3, risk 2. Sources: S00, S11, S29, S60, S61, S62,
     S74, S111, S112.

3. **AGP 9 migration (C95, promoted from Next)**
   - AGP 9.0/9.1/9.2 are stable. AGP 10 (mid-2026) removes the AGP 8
     opt-out paths. Migrate now while the spike is small. Coordinated
     with `gradle/libs.versions.toml`, the wrapper, and CI matrix.
     Run the AGP 9 Upgrade Assistant; verify SBOM (C94) still attaches.
   - Impact 4, effort 3, risk 3. Sources: S91, S92, S93.

4. **Hilt Compose artifact rename (C96, promoted from Next)**
   - Move `hiltViewModel()` imports to the new
     `androidx.hilt:hilt-lifecycle-viewmodel-compose` artifact + package.
     `hilt-navigation-compose` keeps the Navigation pieces. Pure
     import + dependency rename; no runtime change.
   - Impact 3, effort 2, risk 1. Sources: S94.

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
    - Compose Preview Screenshot Testing landed as a first-class
      IDE+Gradle feature (S97, S98) — wire it into CI without an
      emulator (effort drops 3→2). Connected-device tests (C84) and
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

12. **Sleep-evidence consensus update (C100 extension, C127 new)**
    - The 2025/2026 sleep-science consensus has shifted: total
      luminance matters more than spectrum for sleep onset, and one
      prominent researcher publicly retracted earlier blue-light
      advocacy. Update `docs/health-evidence.md` with a one-paragraph
      "what changed since rev 2" note and reinforce the Home tab's
      "comfort, not treatment" copy. New candidate C127: surface a
      "perceived luminance reduction" indicator alongside the
      existing blue-suppression metric on the Home tab.
    - Impact 3, effort 1, risk 1. Sources: S45, S46, S47, S99, S100,
      S101, S102, S158, S159, S160, S161, S162.

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

1. **Direct Boot restore (C28)** — minimal `enabled`/`engine` state in
   `deviceProtectedDataStore()` + `LOCKED_BOOT_COMPLETED` receiver.
   Engine availability: CDM + Overlay work pre-unlock; SF + KCAL require
   `su` so degrade gracefully. Effort 3 (was 4) because the new DataStore
   APIs (S95) remove the storage-migration risk. Sources: S00, S27, S66,
   S95.

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

4. **Glance API widget rewrite (C123, promoted UC → Next in rev 4)** —
   `ToggleWidget` and `PresetWidget` use raw `RemoteViews`. Glance gives
   us Compose-style widget code with `@PreviewTest` support and built-in
   responsive sizing. Rev 3 placed this Under Consideration because
   Glance was alpha; rev 4 confirms Glance is stable since 1.0.0 (1.1.0
   shipped 2024-06-12), so the stability blocker is gone. Pair the
   rewrite with the AGP 9 / Kotlin bump (C95).
   Impact 3 (cleanup), effort 3, risk 2. Sources: S118, S193, S194.

5. **CVD LUT correction (C63)** — bundle precomputed 256-entry per-channel
   LUTs for deuteranomaly/protanomaly/tritanomaly. Reference DaltonLens
   for canonical math (Viénot 1999 in linear RGB). The shipped CVD-remap
   presets in `Presets.kt` are the coarser baseline. Sources: S13, S31,
   S119, S120.

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
  shipped as C53; the post-v1 stretch is filter-by-category/level.
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
- **Glance API widget rewrite (C123)** — currently RemoteViews works.
  Glance is cleaner but adds dependency surface. Decision after Glance
  hits stable (currently 1.0 alpha).

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
| C139 | `PreferencesStore` import duplicate-name UI feedback | UX | new | Later | 2/2/1 | Change `importFrom` / `previewImport` to return `Result<ImportSummary>` where `ImportSummary` includes `droppedDuplicateNames: List<String>`; surface in the import dialog | Med (UX) severity: silent profile-name dedupe on import surprises users who imported a backup containing two profiles with the same name | S00, `PreferencesStore.kt:221-234` |
| C140 | F-Droid initial submission (fdroiddata MR) | distribution | new | Now | 5/2/2 | Fork `gitlab.com/fdroid/fdroiddata`, create `metadata/com.openlumen.yml`, run `fdroid lint`, open MR labelled "New App". Allow 24-48h post-merge | OpenLumen has never been submitted (S203-S205 negative results across MR / RFP / app-search). Direct MR using the F-Droid Quick Start Guide (S206). Gated on C01 (real-device validation rows) and C35 (final adaptive icon) / C36 (screenshots) | S203, S204, S205, S206, S207, S210, S211 |

### New candidates (rev 3)

| ID | Candidate | Category | Prev | Tier | I/E/R | Deps / effort sketch | Placement reason | Sources |
|---|---|---|---|---|---|---|---|---|
| C101 | Compose Preview Screenshot Testing CI wiring | testing | emerging | Now | 4/2/1 | AGP 8.5+ feature; wire into CI without emulator | Unblocks C83 efficiently | S97, S98 |
| C102 | DataStore Direct Boot APIs adoption | reliability/migration | emerging | Next | 4/3/2 | `deviceProtectedDataStore()` + `LOCKED_BOOT_COMPLETED` receiver | Drops C28 effort and risk | S95 |
| C103 | Android 17 stable validation | platform/OS | table-stakes | Now | 4/3/2 | Per-engine smoke on Pixel running Android 17 stable | Stable lands June 2026 | S83, S84, S96 |
| C104 | Document AAPM accessibility revocation | docs/security | rare | Shipped 2026-05-17 | 3/1/1 | `docs/threat-model.md`, `docs/android-17-readiness.md`, and `docs/overlay-and-per-app-design.md` now call out why AAPM reinforces rejecting AccessibilityService for foreground-app convenience | Reinforces C79 rejection and Shizuku as only path | S88, S89, S90, S00h |
| C105 | SAW-app FGS-from-background fallback | reliability/UX | rare | Shipped 2026-05-17 | 4/2/2 | Added `LumenServiceStarter` classification for `ForegroundServiceStartNotAllowedException`; QS/widget user actions roll back stale enabled state and open the app when Android blocks a background FGS start | Android 15+ tightens the rules for SAW apps without a visible overlay window | S85, S131, S00g |
| C106 | BOOT_COMPLETED FGS verification | reliability | rare | Shipped 2026-05-17 | 3/1/1 | Added Android 14/15/16/17 boot-restore rows to wake/vitals audit and boot-restore notes to the device-matrix flow; real pass/fail evidence remains C01 | Ensures boot restore still works as the API tightens | S85 |
| C107 | FGS job runtime quota audit | performance | rare | Later | 2/2/2 | We don't use Jobs today; document policy if we ever add WorkManager | Forward guard | S85 |
| C108 | (folded into C96) | — | — | — | — | — | — | — |
| C109 | (folded into C95) | — | — | — | — | — | — | — |
| C110 | Material 3 1.5.0 / Expressive components review | UX | emerging | Later | 2/2/1 | Survey expressive components; pick high-value ones (FAB Menu, ToggleButtons) | Optional polish, not table-stakes | S123, S124 |
| C111 | BAL hardening readiness | platform/OS | rare | Shipped 2026-05-17 | 3/1/1 | Audited for `IntentSender`, `ActivityOptions`, and `MODE_BACKGROUND_ACTIVITY_START_*`; no migration call sites exist today | Android 17 deprecation | S84, S128, S137, S00d |
| C112 | (n/a — no network, unaffected by CT/ECH) | — | — | — | — | — | — | — |
| C113 | (n/a — same) | — | — | — | — | — | — | — |
| C114 | Fine-grain dim precision for PWM users | UX | rare | Later | 3/2/2 | Sub-1% slider step in the low-dim region | Community signal (S80, S107) | S80, S103, S107 |
| C115 | "Filter green light too" (Red Moon #353) | UX | rare | Under Consideration | 2/2/2 | Already partly satisfied by low-Kelvin slider; document explicitly | Adjacent to existing Kelvin path | S86 |
| C116 | "Don't resume after restart if paused" docs | docs | rare | Shipped 2026-05-17 | 2/1/1 | `BootReceiver` already restores only when persisted `enabled = true`; documented the paused reboot behavior in troubleshooting | Red Moon users currently lack this | S86, `BootReceiver.kt`, `docs/troubleshooting.md` |
| C117 | Root-mode apply-on-first-emission verification | reliability | rare | Now | 3/1/1 | Verify SF/KCAL receive their first matrix without requiring a value change | Red Moon has a known bug here; we should not | S86 |
| C118 | GrapheneOS / lockdown-ROM overlay coverage | platform/OS | rare | Next | 3/3/3 | Test overlay z-order against system shade on GrapheneOS | Red Moon issue #347 indicates real OEM-divergence risk | S86 |
| C119 | (folded into C35) | — | — | — | — | — | — | — |
| C120 | VCS info determinism in reproducibility doc | distribution/docs | rare | Shipped 2026-05-17 | 2/1/1 | Release builds disable `vcsInfo.include`; `docs/reproducible-build.md` documents the AGP `version-control-info.textproto` handling and external provenance path | Known F-Droid reproducibility friction | S112, S156, S268 |
| C121 | Tink + Proto DataStore replacement of EncryptedSharedPreferences (if we ever encrypt) | security | rare | Under Consideration | 2/3/2 | Document the modern path in `docs/threat-model.md`; not adopting now | EncryptedSharedPreferences deprecated; future-proof note | S122 |
| C122 | Roborazzi gold-image CI | testing | rare | Next | 3/3/2 | Roborazzi gives JVM screenshot testing alongside Compose Preview Screenshot Testing | Belt-and-braces snapshot coverage | S97, S125 |
| C123 | Glance API widget rewrite | mobile | emerging | Under Consideration | 3/3/2 | Replace RemoteViews with Glance once 1.0 stable | Cleaner widget code, `@PreviewTest` support | S118 |
| C124 | Hilt 2.56+ minimum | upgrade strategy | emerging | Now | 3/1/1 | Bump in `gradle/libs.versions.toml` with KSP support | Pairs with C96 | S94 |
| C125 | Twilight 14.25 feature scan | research | emerging | Later | 2/1/1 | Periodic check of Twilight's per-app/Wear/Chromebook frontier | Trend signal, not parity goal | S87 |
| C126 | Stronger sleep-evidence disclaimer | docs/licensing | rare | Shipped 2026-05-17 | 3/1/1 | `docs/health-evidence.md` now has the 2025/2026 consensus-shift note plus S99-S102 and S158-S162 source refresh | Consensus shift demands explicit acknowledgement | S99-S102, S158-S162 |
| C127 | Perceived-luminance reduction indicator | UX/data | rare | Next | 3/2/1 | Surface "Perceived brightness reduced by N%" alongside blue-suppression on Home | Aligns the UI metric with current sleep-evidence consensus | S99-S102 |

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
