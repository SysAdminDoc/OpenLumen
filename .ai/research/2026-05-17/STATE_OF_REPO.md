# State of the Repo — 2026-05-17

Local reconnaissance memo for OpenLumen. Inputs: working tree on `main`, last
30 commits (`git log --oneline -30`), `git status`, `git diff --stat HEAD`,
all root-level docs, `gradle/libs.versions.toml`, manifests, `docs/**`, and a
sweep of the four Kotlin module trees.

## Identity

- **Name**: OpenLumen
- **Tagline**: open-source spiritual successor to Chainfire's CF.Lumen.
- **License**: GPL-3.0-or-later. (`LICENSE`)
- **Repository**: `Z:\repos\OpenLumen`, remote
  `https://github.com/SysAdminDoc/OpenLumen` ([README.md:120](../../../README.md#L120)).
- **Status today**: shipping `v0.4.0` as the latest tagged release; `v0.5.0`
  is feature-complete on `main` and awaiting a device-validation gate.
- **App package**: `com.openlumen` (`.debug` suffix for debug builds).

## Stack and module layout

Sourced from [gradle/libs.versions.toml](../../../gradle/libs.versions.toml) and
[app/build.gradle.kts](../../../app/build.gradle.kts).

| Component | Version |
|---|---|
| Kotlin | 2.3.21 |
| AGP | 9.2.1 |
| KSP | 2.3.8 |
| Compose BOM | 2026.05.00 |
| Compose compiler | Kotlin Compose plugin 2.3.21 |
| Material 3 | 1.4.0 |
| Activity Compose | 1.13.0 |
| Lifecycle (runtime/viewmodel/service) | 2.10.0 |
| Navigation Compose | 2.9.8 |
| Hilt | 2.59.2 |
| AndroidX Hilt lifecycle-viewmodel-compose | 1.3.0 |
| Compose screenshot plugin | 0.0.1-alpha14 |
| DataStore (preferences) | 1.2.1 |
| kotlinx.serialization JSON | 1.7.3 |
| kotlinx.coroutines | 1.9.0 |
| AndroidX core-ktx | 1.18.0 |
| JUnit 4 | 4.13.2 |
| Truth | 1.4.4 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 36 |
| JVM target | 17 |
| `versionCode` / `versionName` | 5 / `0.4.0` |

Gradle layout: four modules.

```
OpenLumen/
├── app/             Compose UI, FGS, tile, boot receiver, widgets, Hilt graph
├── core-engine/     ColorEngine abstraction + 4 driver impls + DriverProbe + Su
├── core-schedule/   NOAA solar calculator, ScheduleMode, light-sensor adapter,
│                    OfflineCities (~95 cities)
└── core-prefs/      DataStore-backed Preferences, JSON serialization, migrations,
                     ProfileSnapshot, PresetCycle
```

Source code surface (Kotlin LOC, main+test):

| Module | Main LOC | Test LOC | Notable files |
|---|---:|---:|---|
| `app` | ~3.5k | ~0.2k | `service/LumenService.kt` (645), `diagnostics/DriverReport.kt`, 5 screens, 2 widgets |
| `core-engine` | ~1.0k | ~0.5k | 4 engines, `Su.kt`, `Kelvin.kt`, `ColorMatrix.kt`, `Presets.kt` |
| `core-prefs` | ~0.7k | ~0.4k | `PreferencesStore.kt` (258), migrations, profiles, cycle |
| `core-schedule` | ~0.7k | ~0.3k | `Schedule.kt`, `SolarCalculator.kt`, `OfflineCities.kt`, `LightSensorAdapter.kt` |

## Permissions (`app/src/main/AndroidManifest.xml`)

Requested:

- `SYSTEM_ALERT_WINDOW` — Overlay engine.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — persistent tint.
- `RECEIVE_BOOT_COMPLETED` — restore after reboot.
- `POST_NOTIFICATIONS` — API 33+ FGS notification visibility.
- `WRITE_SECURE_SETTINGS` (ADB-granted) — CDM engine.
- `SCHEDULE_EXACT_ALARM` — precise schedule transitions.

Manifest declares the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` subtype string
required by Android 14+. The comment *"Deliberately no INTERNET permission"*
is preserved at [AndroidManifest.xml:15-18](../../../app/src/main/AndroidManifest.xml#L15-L18).

Components declared:

- `MainActivity` (single launcher).
- `service/LumenService` (`exported=false`, `foregroundServiceType=specialUse`).
- `service/LumenTileService` (QS tile; `PREFERENCES_ACTIVITY` meta-data for
  long-press).
- `service/BootReceiver` (BOOT_COMPLETED).
- `service/ScheduleAlarmReceiver` (alarm callback).
- `widget/ToggleWidget` (1×1) and `widget/PresetWidget` (4×1), each with a
  namespaced `REFRESH` / `PRESET_REFRESH` broadcast action.

## CI and supply chain

Three workflows under [.github/workflows/](../../../.github/workflows/):

- **[ci.yml](../../../.github/workflows/ci.yml)** — `assembleDebug + lint`,
  `testDebugUnitTest` plus each `core-*` `test`, and a
  `permissions-audit` job that:
  1. Builds the debug APK.
  2. Runs `aapt2 dump permissions` against the merged manifest and fails the
     build on `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`.
  3. Greps the `releaseRuntimeClasspath` for `play-services`, `firebase`, or
     `com.google.android.gms` and fails if any appear.
- **[release.yml](../../../.github/workflows/release.yml)** — keystore decode
  from `secrets.KEYSTORE_BASE64`, `assembleRelease`, no-INTERNET assertion on
  the release APK, SHA-256 sums, and
  `actions/attest-build-provenance@v2` for SLSA provenance.
- **[sbom.yml](../../../.github/workflows/sbom.yml)** — SPDX-JSON SBOM via
  `anchore/sbom-action@v0` and an advisory scan via `anchore/scan-action@v6`.
  Runs weekly Monday 06:00 UTC plus every release. `fail-build: false`
  intentionally (triage-then-fix posture documented in
  [docs/sbom-and-advisories.md](../../../docs/sbom-and-advisories.md)).

Dependabot is configured for Gradle and GitHub Actions
([.github/dependabot.yml](../../../.github/dependabot.yml)). All Actions
pinned to major-version tags with a documented rotation policy.

Issue templates: bug, driver_report, overlay_bug, feature_request.

## Git state (snapshot 2026-05-17)

```
$ git status
On branch main
Your branch is ahead of 'origin/main' by 22 commits.

Changes not staged for commit:
  ROADMAP.md                                                   (rev 2 → rev 3)
  app/src/main/java/com/openlumen/diagnostics/DriverReport.kt  (audit)
  app/src/main/java/com/openlumen/service/LumenService.kt      (audit)
  app/src/main/java/com/openlumen/service/LumenTileService.kt  (audit)
  app/src/main/java/com/openlumen/ui/screens/AboutScreen.kt    (audit)
  app/src/main/java/com/openlumen/viewmodel/OpenLumenViewModel.kt (audit)
  app/src/main/res/values/strings.xml                          (audit)
  core-engine/src/main/java/com/openlumen/engine/Su.kt         (audit)
  core-engine/src/main/java/com/openlumen/engine/engines/KcalEngine.kt
  core-engine/src/main/java/com/openlumen/engine/engines/OverlayEngine.kt
  core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt
  core-schedule/src/main/java/com/openlumen/schedule/LightSensorAdapter.kt
  core-schedule/src/main/java/com/openlumen/schedule/Schedule.kt
  core-schedule/src/main/java/com/openlumen/schedule/SolarCalculator.kt
  core-schedule/src/test/java/com/openlumen/schedule/ScheduleTest.kt
  core-schedule/src/test/java/com/openlumen/schedule/SolarCalculatorTest.kt
```

16 files modified, all part of the 2026-05-17 in-tree audit pass documented
verbatim in `ROADMAP.md` under "What changed in rev 3" and "Hardening
(post-rev-2 audit) — landed on `main`". `git diff --stat` shows
`ROADMAP.md` is the biggest delta (1349 lines changed in-place — a rev-2 →
rev-3 rewrite that preserved candidate IDs and source IDs).

The 22-commits-ahead-of-origin state is consistent with this project's auto-
memory note that pushes to `SysAdminDoc/OpenLumen` from this VM 403; commits
are accumulating locally for the user to push from elsewhere.

Recent commit topics (last 22, newest first):

```
6245fa4 feat: externalize UI strings and accessibility labels
4606b97 docs: README polish — v0.5.0 feature list + audience-organized doc index
273f5e1 feat: AMOLED clamp + blue-suppression indicator + matrix preview (C61/C66)
4ba1fb9 feat: alarm-based schedule mode + contrast control (C25/C64)
b207075 docs: consolidate deferred-candidate analysis (closes the v0.5.0 roadmap pass)
1cdf224 feat: Kelvin color-temperature input (C65)
104de80 feat+docs: screen-off lux invalidation + overlay/per-app design (C10/C11/C12/C28/C69/C90/C95/C96/C99)
31e30e6 feat: named profile library (C31)
50ea466 feat+docs: offline city picker, translation workflow, legacy import notes (C26/C32/C33/C58/C59)
a9e283a feat: local diagnostics log + in-app viewer (C52/C53)
9e995a6 feat: SF code registry + KCAL variant probing (C03/C04)
339d89d docs+feat: API 36 readiness inventory, schedule timezone label (C27/C82)
232c210 ci+docs: SBOM workflow, dependency-verification procedure, vitals audit (C48/C54/C94)
c311d6e feat+docs: previous-preset restore, compatibility table, Play FGS evidence (C14/C44/C93)
817e879 feat: smooth transition engine (C23/C24/C98)
8c8a9e7 feat: 4x1 preset widget (C20)
f6c3296 feat: 1x1 toggle widget, threat model, boot-panic reset (C19/C51/C85)
823d008 feat: notification preset cycle + Tasker intent surface (C16/C70/C71)
2485d4f feat: versioned schema, import preview, favorites (C15/C29/C30)
bc42293 feat: driver report, tile subtitle/long-press, emergency-off, overlay caveats (C02/C07/C09/C13/C17/C18)
ab21564 docs+ci: v0.5.0 trust-and-distribution foundation (C05/C34/C37/C38/C40-C50/C60/C97/C100)
f1c55c8 Post-v0.4.0 hardening pass: nullable solar coords, overlay alpha fix, safer alarms
```

Every feature commit references a roadmap candidate ID. That traceability is
preserved.

## Current implementation state after C144

C144 shipped the stable AndroidX refresh on 2026-05-17. The repo now
compiles against SDK 36 for current AndroidX artifacts, but still targets
SDK 35 until C103 Android 17 device validation is complete. Full Gradle
validation passed from `C:\Users\Xray\OpenLumen-agp9-verify`; direct
builds from `Z:\repos\OpenLumen` remain affected by the known AGP 9 D8
Windows shared-folder path limitation.

C36 store screenshots remain blocked in this environment: the local SDK
has build-tools/platform packages but no emulator binary, no AVD, and no
system image. A real device or provisioned emulator is still required.

## Architecture (verified against source)

Architecture as documented in [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md)
matches the source on `main`. Spot-checks performed:

- [core-engine/.../ColorEngine.kt:32-37](../../../core-engine/src/main/java/com/openlumen/engine/ColorEngine.kt#L32-L37) confirms the 4-engine `EngineKind` enum with ranks 100 / 90 / 70 / 10 in the documented order.
- [app/.../AndroidManifest.xml:46-52](../../../app/src/main/AndroidManifest.xml#L46-L52) confirms `foregroundServiceType="specialUse"` plus the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value
  *"Persistent display color tint for eye comfort and sleep hygiene."*
- [.github/workflows/ci.yml:75](../../../.github/workflows/ci.yml#L75) confirms the manifest network-permission grep.
- [app/build.gradle.kts:32-34](../../../app/build.gradle.kts#L32-L34) confirms APK signing schemes v1+v2+v3.

## Tests

JVM unit tests (run on every PR, all green per CI):

- `core-engine`: `LumenMatrixTest`, `KelvinTest`, `AmoledClampTest`,
  `SurfaceFlingerEngineTest`.
- `core-prefs`: `PreferencesMigrationsTest`, `PreferencesSerializationTest`,
  `PresetCycleTest`, `ProfilesTest`.
- `core-schedule`: `ScheduleTest`, `SolarCalculatorTest`, `OfflineCitiesTest`.
- `app`: `diagnostics/DiagnosticsLogFormatTest`.

The `ScheduleTest` / `SolarCalculatorTest` files are part of the 16 modified
files — the 2026-05-17 audit added regression cases for the Solar / polar /
NYC / Tokyo bugs the audit pass found.

No connected-device tests. Compose Preview Screenshot Testing is not wired in
yet (tracked as C83 / C101).

## Documentation surface

Twenty docs under [docs/](../../../docs/):

- User-facing: `troubleshooting.md`, `compatibility-table.md`,
  `root-safety.md`, `automation.md`, `health-evidence.md`.
- Contributor: `ARCHITECTURE.md`, `translations.md`, `device-matrix.md`,
  `profile-import-formats.md`.
- Distribution: `release-checklist.md`, `reproducible-build.md`,
  `play-fgs-evidence.md`.
- Security: `threat-model.md`, `sbom-and-advisories.md`,
  `dependency-verification.md`, `wake-and-vitals.md`.
- Roadmap and design: `overlay-and-per-app-design.md`,
  `deferred-candidates.md`, `api-36-readiness.md`, `research-watchlist.md`.

Plus root-level `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `ROADMAP.md`,
`LICENSE`, and the `branding/logo-prompts.md` design brief. `fastlane/`
contains the F-Droid metadata skeleton at
`fastlane/metadata/android/en-US/`.

## Known gaps (what is NOT done)

Pulled from `ROADMAP.md` rev 3 "What is incomplete" plus my own diff against
source:

1. **No real-device validation rows** in `docs/device-matrix.md`. Every row
   says "smoke pending" with `?` in every engine column. This is the single
   biggest gate to a v0.5.0 → F-Droid cut.
2. **Store screenshots** are still placeholder. C35 final adaptive/store
   icon is shipped; C36 screenshot capture remains.
3. **Android 17 validation row** is missing from `docs/device-matrix.md` and
   `docs/api-36-readiness.md` is still titled and framed for "Android 16 /
   API 36" — out of date with rev 3 which expanded C82 to Android 17.
4. **Per-app rules, Shizuku backend, Wear OS companion, Direct Boot restore,
   Android TV flavor, accessibility-scanner pass, Compose screenshot tests**
   — all designed, none implemented.
5. **Audit hardening from 2026-05-17 ships in v0.5.0 / v0.5.1** but the
   CHANGELOG.md `[Unreleased]` section was last updated before the audit
   pass — it does not yet enumerate the audit fixes. (The rev 3 `ROADMAP.md`
   does.)
6. **CLAUDE.md is stale**: it lists `v0.2.0` as the most recent version
   under "Version history" and "Status" says *"v0.2.0 = UI complete enough
   to actually test. Not yet smoke-tested on a real device."* The repo is on
   `v0.4.0` shipped with `v0.5.0` feature-complete on `main`. The "Stack"
   section is current; the "Version history" / "Status" sections are not.
7. **research-watchlist.md** says `Last review: 2026-05-16`. Rev 3 (today,
   2026-05-17) effectively *was* a watchlist review pass — that header
   should advance to 2026-05-17.

## Philosophy markers (preserved)

These are non-negotiables baked into the repo:

- GPL-3.0-or-later.
- minSdk 26 / targetSdk 35 (today); `specialUse` FGS.
- F-Droid first; Play optional (evidence pack ready at
  `docs/play-fgs-evidence.md`). No ads, no required account, no `INTERNET`
  permission in the main app.
- Catppuccin Mocha / AMOLED true-black Compose UI. Material 3. No pill-
  shaped buttons (enforced via `LumenButton` wrappers per
  [CHANGELOG.md:370-381](../../../CHANGELOG.md#L370)).
- No telemetry, no crash reporting, no remote config, no analytics.
- No accessibility service. No `PACKAGE_USAGE_STATS`. (Both rejected
  permanently in rev 3 after Android 17 AAPM.)

## Rev 5 local-state update (third pass)

Snapshot command context:

- Commands had to run from `C:\Users\Xray` because launching directly in
  the VMware/shared-folder `Z:\Downloads\MavenRepo` cwd failed with
  Windows error 267. All repo paths still targeted `Z:\repos\OpenLumen`.
- `git rev-parse --short HEAD`: `1238907`.
- `git status --short --branch`: `main...origin/main [ahead 24]` with
  dirty working-tree changes.

Inventory deltas from this pass:

- Tool-instruction files: `CLAUDE.md` exists; `AGENTS.md`, `.claude/**`,
  `.claude-instructions`, `.cursor/**`, `.cursorrules`, `.windsurfrules`,
  `GEMINI.md`, `COPILOT_INSTRUCTIONS.md`, and
  `.github/copilot-instructions.md` are absent.
- Production Kotlin files: 49 across `app`, `core-engine`, `core-prefs`,
  and `core-schedule`.
- Kotlin test files: 12.
- `@Test` count: 90.
- Tracked file count: 157.

Dirty working tree at rev 5 start:

- 15 modified Kotlin / resource files from the 2026-05-17 hardening pass:
  `DriverReport.kt`, `LumenService.kt`, `LumenTileService.kt`,
  `AboutScreen.kt`, `OpenLumenViewModel.kt`, `strings.xml`, `Su.kt`,
  `KcalEngine.kt`, `OverlayEngine.kt`, `PreferencesStore.kt`,
  `LightSensorAdapter.kt`, `Schedule.kt`, `SolarCalculator.kt`,
  `ScheduleTest.kt`, and `SolarCalculatorTest.kt`.
- 4 untracked release/distribution docs/files: `SECURITY.md`,
  `docs/fdroid-recipe-draft.md`, `docs/v0.5.0-release-readiness.md`,
  and `fastlane/metadata/android/en-US/changelogs/5.txt`.

Interpretation: the dirty Kotlin state appears to be the hardening work
already described in `CHANGELOG.md` and rev 4.1, not a rev 5 research
edit. Rev 5 documentation treats it as existing local state and does not
revert it.

## Rev 5 verification update

Initial verification blockers:

- `gradlew.bat` could not run because `JAVA_HOME` was unset and `java` was
  not on PATH.
- After adding a local Java 17 runtime, Gradle configured far enough to
  report that no Android SDK was configured.

Resolution:

- Installed a user-local Temurin JDK 17.0.19 at
  `C:\Users\Xray\.codex\jdks\temurin-17`.
- Installed a user-local Android command-line SDK at
  `C:\Users\Xray\.codex\android-sdk` with Android 35 platform/build tools.
- Did not alter system PATH or repo-local `local.properties`; `JAVA_HOME`,
  `ANDROID_HOME`, and `ANDROID_SDK_ROOT` were set only for verification
  commands.

Passing checks:

- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --stacktrace`
  passed (`BUILD SUCCESSFUL`, 133 actionable tasks).
- `:app:assembleDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 98
  actionable tasks).
- `:app:lintDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 142 actionable
  tasks). The serial rerun is the authoritative lint result; the earlier
  parallel attempt timed out while another Gradle build was running.

## C142 implementation state

After commit `d8d6d9e`, the next implementable Now-tier roadmap item was
C142. The implementation pass updated CI/release/SBOM workflows to current
Node-24-capable action majors and marked C142 shipped in `ROADMAP.md`.

Current workflow action baseline:

- `actions/checkout@v6`
- `actions/setup-java@v5`
- `gradle/actions/setup-gradle@v6`
- `actions/upload-artifact@v7`
- `actions/attest@v4`
- `anchore/sbom-action@v0`
- `anchore/scan-action@v7`

Verification after the C142 edit:

- Workflow YAML parsed under `prettier@3.5.3 --check`.
- Unit tests passed:
  `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test`.
- `:app:assembleDebug` passed.
- `:app:lintDebug` passed.

## C143 implementation state

C143 was implemented after C142. The repo now has a concrete
`docs/device-matrix.md` add-on for Android 17+ and wide-form-factor
validation:

- `dumpsys activity exit-info com.openlumen` / `.debug` capture after
  long-running overlay and transition smoke.
- Explicit `MemoryLimiter:AnonSwap`, `REASON_LOW_MEMORY`, ANR, and
  repeated service-exit triage instructions.
- sw600dp, tablet, foldable, desktop-windowing, and Android TV layout
  checks for navigation reachability, retained state, overlay coverage,
  dialog clipping, and emergency-off reachability.

No device result rows were fabricated; the new content is a smoke-flow
extension only.

## C132-C136 implementation state

The pass-2 correctness batch is now implemented locally after C143:

- `LumenService` has a dedicated `rampMutex` for transition cancel/join/
  launch state. `clearAndStop()` and engine switches cancel+join any
  active ramp before clearing or swapping engines.
- `ColorDisplayManagerEngine` clears all cached reflection handles on
  partial cache-hit failure or reflection failure.
- `SurfaceFlingerEngine` checks apply/clear `Su.runCommand` results and
  invalidates the cached transaction code on nonzero or `not found`
  output.
- `KcalEngine` uses `set -e` in KCAL shell scripts and clears cached
  sysfs paths on nonzero shell exit.
- `OverlayEngine` serializes install/apply/clear view and window-manager
  mutation with an internal lock on the main thread.

Verification:

- Unit test suite passed with `--rerun-tasks` after clearing stale
  shared-drive build output from an interrupted first run.
- `:app:assembleDebug` passed.
- `:app:lintDebug` passed.
- `git diff --check` passed with CRLF warnings only.

## C130 implementation state

C130 is now implemented locally:

- Manifest declares `android.permission.QUERY_ADVANCED_PROTECTION_MODE`
  with an `UnknownPermission` lint suppression so compile SDK 35 can keep
  building until the Android 17 toolchain migration.
- `DriverReport` format is now v2 and includes an "Advanced Protection"
  section.
- The report uses reflection to retrieve `Context.ADVANCED_PROTECTION_SERVICE`
  and call `isAdvancedProtectionEnabled()` only on API 36+; older APIs
  report `n/a (API <36)`.
- Security failures or OEM/API drift report a bounded `unknown` reason
  instead of throwing while generating a report.

Verification is tracked in `CHANGESET_SUMMARY.md` pass 7.

## C120 implementation state

C120 is now implemented locally:

- Release builds set `vcsInfo.include = false` in `app/build.gradle.kts`.
- `docs/reproducible-build.md` explains why the embedded
  `META-INF/version-control-info.textproto` can break F-Droid reference
  APK comparisons and why clean tagged builds remain required.
- External provenance remains Git tags, published SHA-256 sums, and
  `actions/attest`.

Verification is tracked in `CHANGESET_SUMMARY.md` pass 8.

## C111 implementation state

C111 is now complete as a source audit:

- No `IntentSender`, `ActivityOptions`,
  `setPendingIntentBackgroundActivityStartMode`, or
  `MODE_BACKGROUND_ACTIVITY_START_*` call sites exist in production
  Kotlin.
- Existing `PendingIntent` usage is direct `getActivity`, `getService`,
  and `getBroadcast` routing in the service, widgets, and schedule alarm
  path.
- Android 17 readiness docs now keep only the remaining smoke check for
  notification/widget/tile routing.

## C116 implementation state

C116 is now complete as documentation:

- `docs/troubleshooting.md` explicitly tells users that turning the filter
  off before reboot leaves it off after reboot.
- The documented behavior matches `BootReceiver`: it reads persisted
  preferences and starts `LumenService` only when `enabled = true`.

## C106 implementation state

C106 is now complete as a documentation/test-plan item:

- `docs/wake-and-vitals.md` contains Android 14/15/16/17 boot-restore
  validation rows with pending evidence.
- `docs/device-matrix.md` requires a boot-restore note on Android 14+
  device rows.
- No real device rows were marked passed. Actual pass/fail evidence
  remains part of C01.

## C95 / C96 / C101 / C124 implementation state

This batch is now implemented locally:

- `gradle/libs.versions.toml` now pins AGP 9.2.1, Kotlin 2.3.21,
  KSP 2.3.8, Dagger/Hilt 2.59.2, AndroidX Hilt Compose 1.3.0, and
  Compose screenshot plugin 0.0.1-alpha14.
- Android modules use AGP 9 built-in Kotlin; the removed
  `org.jetbrains.kotlin.android` plugin is the expected AGP 9 shape.
- Compose `hiltViewModel()` imports moved to
  `androidx.hilt.lifecycle.viewmodel.compose`.
- `.github/workflows/ci.yml` now runs
  `:app:validateDebugScreenshotTest`.
- The initial screenshot fixture lives under
  `app/src/screenshotTest/kotlin/` with references in
  `app/src/screenshotTestDebug/reference/`.
- A stale `core-engine` `consumer-rules.pro` declaration was removed
  because no such rule file existed and AGP 9 validates that input.

Verification is tracked in `CHANGESET_SUMMARY.md` pass 17.

## C35 implementation state

C35 is now implemented locally:

- `ic_launcher_background.xml` and `ic_launcher_foreground.xml` use the
  final minimal crescent mark.
- `branding/openlumen-icon.svg` captures the source geometry and colors.
- `fastlane/metadata/android/en-US/images/icon.png` is present as the
  512x512 store icon.

Verification is tracked in `CHANGESET_SUMMARY.md` pass 18.

## C138 implementation state

C138 is now implemented locally:

- `PreferencesStore` no longer decodes import data through a `Reader`
  before applying the limit.
- `readImportBytes()` enforces `MAX_IMPORT_FILE_BYTES` at the raw stream
  boundary and reads only one probe byte beyond the cap before failing.
- `PreferencesImportReadTest` covers exact-limit acceptance and
  max-plus-one rejection before decode.

## C137 implementation state

C137 is now implemented locally:

- `material-icons-extended` is removed from `gradle/libs.versions.toml`
  and `app/build.gradle.kts`.
- `OpenLumenRoot` and `PresetsScreen` use local vector drawables through
  `painterResource()`.
- `:app:dependencies --configuration debugRuntimeClasspath` shows no
  `material-icons-extended` artifact.

## C105 implementation state

C105 is now implemented locally:

- `LumenServiceStarter` wraps `startForegroundService` / `startService`
  and identifies Android foreground-start restriction failures.
- QS tile toggle-on rolls back `enabled=false` and opens the app when the
  service start is blocked.
- Widget toggle / preset taps now route through `WidgetActionReceiver`,
  so blocked starts can be handled instead of failing as direct service
  PendingIntents.

## C104/C126 documentation state

C104 and C126 are now marked shipped:

- C104: AAPM accessibility auto-revocation is documented in
  `docs/threat-model.md`, `docs/android-17-readiness.md`, and
  `docs/overlay-and-per-app-design.md`.
- C126: `docs/health-evidence.md` contains the 2025/2026 consensus-shift
  note and S99-S102 / S158-S162 source refresh.

## C117 implementation state

C117 is now implemented locally:

- `ApplyDecisionGate` owns the service target-cache decision and is reset
  whenever `LumenService` switches engines.
- JVM tests prove a duplicate target is suppressed for the same engine but
  dispatches again after reset, covering the first-emission regression
  class without requiring root hardware in CI.
- `docs/device-matrix.md` now asks rooted SurfaceFlinger and KCAL smoke
  testers to record first-emission pass/fail evidence during C01 device
  validation.
