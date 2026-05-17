# OpenLumen — Project Context

> Canonical consolidated project memory. This file is for any future
> contributor or AI session that needs to come up to speed on OpenLumen
> *fast*. Pair it with [ROADMAP.md](ROADMAP.md) (forward-looking plan)
> and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (deep technical
> reference).
>
> Single-page version of: the README, the CLAUDE.md working notes, the
> architecture doc, the threat model, and the design docs under `docs/`.

## What OpenLumen is

An open-source spiritual successor to Chainfire's CF.Lumen. Brings root-grade
display color shifting and overlay-based fallback to modern Android, with the
AOSP `ColorDisplayManager` path that CF.Lumen never had. Targets the gap left
by CF.Lumen (dormant since December 2020) and Red Moon (unmaintained since
August 2022) on Android 14 / 15 / 17.

- **License**: GPL-3.0-or-later (aligned with Red Moon's lineage).
- **Distribution**: F-Droid first, Play optional. Evidence pack for Play
  exists at [docs/play-fgs-evidence.md](docs/play-fgs-evidence.md).
- **Privacy posture**: **no INTERNET permission**, no telemetry, no
  analytics, no crash reporting, no remote config. Verified in CI on every
  PR ([.github/workflows/ci.yml](.github/workflows/ci.yml) — `permissions-
  audit` job).
- **Aesthetic**: Catppuccin Mocha + AMOLED true-black Compose UI, Material
  3, no pill-shaped buttons (the project ships a small `LumenButton`
  wrapper to suppress Material 3's default CircleShape on `Button` /
  `OutlinedButton` / `TextButton`).
- **Today's tagged release**: v0.4.0. **Today's `main`**: v0.5.0 feature-
  complete, plus 2026-05-17 audit, CI, Android 17 smoke, and service /
  engine correctness updates that ship in v0.5.0 / v0.5.1.

## Stack

| Component | Version | Notes |
|---|---|---|
| Kotlin | 2.1.0 | |
| AGP | 8.7.3 | AGP 9 migration is on the Now list (C95) per rev 3 of ROADMAP. |
| KSP | 2.1.0-1.0.29 | |
| Compose BOM | 2024.12.01 | |
| Compose compiler | 1.5.15 | |
| Material 3 | 1.3.1 | |
| Hilt | 2.53.1 | Hilt Compose artifact migration is on the Now list (C96 → `hilt-lifecycle-viewmodel-compose`). |
| DataStore | 1.1.1 | New `deviceProtectedDataStore()` API unlocks Direct Boot restore (C28). |
| kotlinx.serialization | 1.7.3 | |
| kotlinx.coroutines | 1.9.0 | |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 35 / 35 | Bump to 36 (Android 17) is C103. |
| JVM target | 17 | |
| Test framework | JUnit 4 + Truth | |

Source of truth for versions: [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Module layout

```
OpenLumen/
├── app/             Compose UI, foreground service, QS tile, boot receiver,
│                    widgets, Hilt graph, manifest, resources
├── core-engine/     ColorEngine abstraction + 4 driver impls + DriverProbe +
│                    minimal Su wrapper + presets + Kelvin / matrix math
├── core-schedule/   NOAA solar calculator, ScheduleMode, light-sensor adapter,
│                    OfflineCities (~95 cities, no Play Services)
└── core-prefs/      DataStore-backed Preferences, JSON serialization,
                     versioned migrations, ProfileSnapshot, PresetCycle
```

Why the split (from [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):

- `core-engine` has zero Android UI deps; pulls only `core-ktx`. Driver code
  is JVM-testable.
- `core-schedule` is pure JVM math (NOAA) plus a single Android
  `SensorManager` adapter. NOAA algorithm is unit-tested against NYC,
  Sydney, Quito, Tromsø, and the polar / NYC date-snap regressions added by
  the 2026-05-17 audit.
- `core-prefs` owns the persisted shape — `Preferences`, `MatrixDto`,
  `ScheduleDto`, `ProfileSnapshot`. DTOs are deliberately separate from
  `LumenMatrix` / `ScheduleMode` so engine and schedule code stay
  serialization-annotation-free.

## Display engines (the core of the product)

Four `ColorEngine` implementations under
[core-engine/.../engines/](core-engine/src/main/java/com/openlumen/engine/engines/),
runtime-detected via `DriverProbe.pickBest()`. Ranks pick the highest-
quality available path; overlay is the universal fallback so the user
always gets *something*.

| Engine | Class | Rank | Root? | SoC | Quality |
|---|---|---:|---|---|---|
| `COLOR_DISPLAY_MANAGER` | `ColorDisplayManagerEngine` | 100 | No¹ | Any (AOSP-derived) | Framebuffer |
| `SURFACE_FLINGER` | `SurfaceFlingerEngine` | 90 | Yes | Any (Tensor / Exynos / MediaTek too) | Framebuffer |
| `KCAL` | `KcalEngine` | 70 | Yes | Qualcomm only | Panel driver |
| `OVERLAY` | `OverlayEngine` | 10 | No | Any | Compositor blend |

¹ Some builds require `WRITE_SECURE_SETTINGS` granted via:

```bash
adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS
```

`SurfaceFlingerEngine` probes a per-API ladder of candidate transaction codes
(`1015 → 1023 → 1030 → 1036`) and caches the working one as
`activeTransactionCode`, exposed in the in-app driver report. Failed
apply/clear writes now invalidate the cached code so a later probe can
recover after OTA transaction drift.

`KcalEngine` probes known sysfs roots
(`/sys/devices/platform/kcal_ctrl.0/`, `/sys/module/msm_drm/parameters/`,
`/sys/class/misc/kcal/`) and stores the winner as `activeBasePath`.
KCAL apply/clear shell scripts use `set -e` and clear the cached path on
nonzero exit.

`OverlayEngine` is capped at ~80% alpha by Android 12+ untrusted-touch rules
when used with `FLAG_NOT_TOUCHABLE`. The Driver-tab info card surfaces this.
Overlay view install/update/remove mutations are serialized with an internal
main-thread lock.

## Persistence model

State lives in **one** place: a single JSON blob in DataStore, managed by
[PreferencesStore](core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt).

- UI subscribes via `OpenLumenViewModel.state`.
- `LumenService` subscribes via `observePreferences()`.
- Tile subscribes on every `onStartListening()`.
- Boot receiver writes `enabled=true` and lets the service flow do the rest.

All writers go through `prefs.update { current -> next }` so concurrent
toggles (UI + tile + boot + widget) never race on read-modify-write.

`sanitize()` runs on every read **and** every write — NaN, Inf, out-of-range
imported values, oversized profile lists, invalid preset keys, all get
clamped to defaults. A malicious profile import cannot crash the service.

Schema is versioned (`Preferences.schemaVersion`, current = 1) and
[PreferencesMigrations](core-prefs/src/main/java/com/openlumen/prefs/PreferencesMigrations.kt)
walks pre-`schemaVersion` blobs forward.

## Runtime flow

1. User flips Switch on `HomeScreen` → `OpenLumenViewModel.setEnabled(true)`
   → `prefs.update { it.copy(enabled = true) }`.
2. `PreferencesStore` flow emits the new `Preferences` snapshot.
3. `LumenService.observePreferences()` collects (with `.conflate()`), picks
   an engine via `DriverProbe`, applies the matrix, and schedules the next
   transition alarm via `AlarmManager.setExactAndAllowWhileIdle` (or
   `setAndAllowWhileIdle` fallback).
4. When the alarm fires, `ScheduleAlarmReceiver` sends `ACTION_REEVALUATE`
   back to `LumenService`, which re-derives the matrix and reschedules.
5. Tile / boot receiver / widget receivers also write to DataStore — single
   source of truth.

No background polling. The old 60-second ticker was removed in v0.3.0.

## Permissions

Requested (manifest at [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)):

- `SYSTEM_ALERT_WINDOW` — Overlay engine.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — persistent tint.
- `RECEIVE_BOOT_COMPLETED` — restore filter after reboot.
- `POST_NOTIFICATIONS` (API 33+) — FGS notification visibility.
- `WRITE_SECURE_SETTINGS` — CDM engine (granted only via ADB).
- `SCHEDULE_EXACT_ALARM` — precise schedule transitions.

The manifest also declares
`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="Persistent display color tint for eye comfort and sleep hygiene." />`
on the FGS, which Android 14+ requires.

Deliberately **not** requested:

- `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` (CI enforces).
- `ACCESS_*_LOCATION` (solar mode uses user-entered coordinates only).
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (SAF only).
- `PACKAGE_USAGE_STATS` (rejected — too broad a read surface).
- `BIND_ACCESSIBILITY_SERVICE` (rejected — Android 17 AAPM auto-revokes
  non-disability accessibility apps; OpenLumen does not qualify).
- `QUERY_ALL_PACKAGES`, `BLUETOOTH_*`.

## CI and supply chain

Three workflows under [.github/workflows/](.github/workflows/):

| Workflow | Trigger | Job |
|---|---|---|
| `ci.yml` | push / PR to `main` | `assembleDebug + lint`, module unit tests, **permissions-audit** (manifest grep for INTERNET; `releaseRuntimeClasspath` grep for play-services / firebase / GMS) |
| `release.yml` | `workflow_dispatch` with semver input | keystore decode, `assembleRelease`, release-APK no-INTERNET assertion, SHA-256, **`actions/attest@v4`** |
| `sbom.yml` | weekly Monday 06:00 UTC + every release | SPDX-JSON SBOM via `anchore/sbom-action@v0`, advisory scan via `anchore/scan-action@v7` (severity-cutoff: medium, fail-build: false — triage-then-fix posture) |

Dependabot is configured for Gradle and GitHub Actions
([.github/dependabot.yml](.github/dependabot.yml)). All Actions pinned to
major-version tags with a documented rotation policy in
[docs/sbom-and-advisories.md](docs/sbom-and-advisories.md).

## Key paths

### Engine layer

- [core-engine/.../ColorEngine.kt](core-engine/src/main/java/com/openlumen/engine/ColorEngine.kt) — interface; `EngineKind` enum (rank-ordered).
- [core-engine/.../engines/ColorDisplayManagerEngine.kt](core-engine/src/main/java/com/openlumen/engine/engines/ColorDisplayManagerEngine.kt) — reflection against AOSP `ColorDisplayManager`.
- [core-engine/.../engines/SurfaceFlingerEngine.kt](core-engine/src/main/java/com/openlumen/engine/engines/SurfaceFlingerEngine.kt) — `service call SurfaceFlinger <code>` via `su`; per-API code ladder.
- [core-engine/.../engines/KcalEngine.kt](core-engine/src/main/java/com/openlumen/engine/engines/KcalEngine.kt) — `/sys/devices/platform/kcal_ctrl.0/*` via `su`; multi-base probe.
- [core-engine/.../engines/OverlayEngine.kt](core-engine/src/main/java/com/openlumen/engine/engines/OverlayEngine.kt) — `TYPE_APPLICATION_OVERLAY` rootless fallback.
- [core-engine/.../DriverProbe.kt](core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt) — `pickBest()` / `probeAll()` with rank ordering.
- [core-engine/.../Su.kt](core-engine/src/main/java/com/openlumen/engine/Su.kt) — minimal su wrapper, hardened in the v0.4.0 audit and the 2026-05-17 pass.
- [core-engine/.../Presets.kt](core-engine/src/main/java/com/openlumen/engine/Presets.kt) — 11 named presets.

### Service / UI / data

- [app/.../service/LumenService.kt](app/src/main/java/com/openlumen/service/LumenService.kt) — foreground service (`specialUse`), owns the active engine, mutex-serialized apply, AlarmManager-driven schedule.
- [app/.../service/LumenTileService.kt](app/src/main/java/com/openlumen/service/LumenTileService.kt) — QS tile (subtitle, long-press → app).
- [app/.../service/BootReceiver.kt](app/src/main/java/com/openlumen/service/BootReceiver.kt) — `BOOT_COMPLETED` with 5-minute crash-window panic-reset.
- [app/.../service/ScheduleAlarmReceiver.kt](app/src/main/java/com/openlumen/service/ScheduleAlarmReceiver.kt) — alarm callback that fires `ACTION_REEVALUATE`.
- [app/.../widget/ToggleWidget.kt](app/src/main/java/com/openlumen/widget/ToggleWidget.kt) — 1×1 toggle.
- [app/.../widget/PresetWidget.kt](app/src/main/java/com/openlumen/widget/PresetWidget.kt) — 4×1 favorites.
- [core-prefs/.../PreferencesStore.kt](core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt) — single-JSON-blob DataStore wrapper, sanitize on read/write, import preview.
- [core-schedule/.../SolarCalculator.kt](core-schedule/src/main/java/com/openlumen/schedule/SolarCalculator.kt) — hand-rolled NOAA algorithm with explicit `Polar` enum (polar-day vs polar-night) and date-snapping.

## Build commands

```bash
./gradlew assembleDebug
./gradlew assembleRelease     # needs OPENLUMEN_KEYSTORE env vars
./gradlew :app:lint
./gradlew :app:bundleRelease  # AAB for Play track (optional)

# Module unit tests
./gradlew :app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test
```

Signed release builds require:

```bash
export OPENLUMEN_KEYSTORE=/path/to/release.jks
export OPENLUMEN_KEYSTORE_PASSWORD=...
export OPENLUMEN_KEY_ALIAS=openlumen
export OPENLUMEN_KEY_PASSWORD=...
```

Smoke test on device:

```bash
./gradlew :app:installDebug
adb shell am start -n com.openlumen.debug/com.openlumen.MainActivity
adb logcat -s OpenLumen LumenService
```

For the AOSP `ColorDisplayManager` path, grant secure settings (one-time):

```bash
adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS
```

## Recovery / emergency-off

If a release goes wrong and the overlay or root driver leaves the screen in
a bad state, the canonical escape hatch:

```bash
adb shell am startservice -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.LumenService
```

Full recovery procedures in [docs/root-safety.md](docs/root-safety.md). The
in-app About tab carries the emergency-off ADB command copyable to
clipboard (C13).

The BootReceiver implements a 5-minute crash-window panic reset (C85): if
the crash log was touched within 5 minutes before boot, auto-restore is
suppressed so a stuck-tint state doesn't recur.

## Stable automation surface

Documented at [docs/automation.md](docs/automation.md). `LumenService`
accepts these action strings via `am startservice`:

- `com.openlumen.action.TURN_ON`
- `com.openlumen.action.TURN_OFF`
- `com.openlumen.action.TOGGLE`
- `com.openlumen.action.CYCLE_PRESET`
- `com.openlumen.action.SET_PRESET` (extra: `preset_key`)
- `com.openlumen.action.SET_INTENSITY` (extra: `intensity` 0-100)
- `com.openlumen.action.SET_DIM` (extra: `dim` 0-95)
- `com.openlumen.action.RESTORE_PREVIOUS`
- `com.openlumen.action.REEVALUATE`

Renaming any of these requires a schema-version bump and a deprecation
window.

## Gotchas (the things that bite)

- **CDM engine needs `WRITE_SECURE_SETTINGS`.** Without it,
  `setNightDisplayActivated(true)` silently no-ops. The Driver screen
  surfaces the ADB grant command with copy-to-clipboard.
- **`service call SurfaceFlinger` code drift.** Historically 1015 for
  `setDisplayColorTransform`. We probe `1015 → 1023 → 1030 → 1036` per API
  ladder and cache the winner. If a new Android version drifts again, add
  the code to `SurfaceFlingerEngine.candidatesFor()`.
- **Overlay engine needs a Service context.** `TYPE_APPLICATION_OVERLAY`'s
  window token only works when added from a Service or Activity, never
  Application context. `LumenService.ensureEngine()` calls
  `overlay.installView(this, …)` before first apply, posting to main
  thread when called off-main.
- **Android 14 `specialUse` FGS property is mandatory.** Manifest property
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` is required;
  without it, `startForeground()` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
  throws. Already declared.
- **Android 12+ untrusted-touch alpha cap.** With `FLAG_NOT_TOUCHABLE`,
  overlay alpha is capped at ~0.8 (≤204 in 8-bit ARGB). Hard dim past that
  requires root drivers. The Driver-tab info card surfaces this.
- **Reflection drift on `ColorDisplayManager`.** Future Android versions can
  rename methods; the `runCatching` ladder in `ColorDisplayManagerEngine` is
  defensive on purpose. Partial cached reflection failures now clear all
  cached handles so the next call can re-probe.
- **Solar calculator polar / date corner cases (audited 2026-05-17).**
  `SolarCalculator` returns a `Polar` enum so polar-day and polar-night are
  distinguishable. Sunrise/sunset `ZonedDateTime`s are snapped to the
  requested local date so Western-hemisphere sunsets no longer land on the
  previous day. Caller-supplied `now` is respected (previous bug used
  `LocalDate.now(zoneId)`).
- **Mid-ramp interruptions.** `LumenService` lerps from the actually-
  displayed matrix rather than the previous target. `lastTarget` is now
  separate from `lastApplied`; engine switches reset both. A dedicated
  `rampMutex` serializes cancel/join/launch state, and filter-off clears
  cancel+join any active ramp before clearing the engine.
- **Boot receiver in user-protected storage.** DataStore today lives in
  user-protected storage, so `BootReceiver` does NOT register for
  `LOCKED_BOOT_COMPLETED` — that signal would deadlock `prefs.flow.first()`
  before the keystore is unlocked. C28 (Direct Boot restore) plans the
  move to `deviceProtectedDataStore()` for an `enabled` + `engine`
  minimum-viable restore path.

## Hard constraints (non-negotiable)

- License: GPL-3.0-or-later.
- `minSdk 26`, `targetSdk 35` today; `specialUse` FGS.
- F-Droid first, Play optional. No ads, no required account, no `INTERNET`
  permission in the main app. No Play Services / Firebase / Google APIs.
- No telemetry, no crash reporting, no remote config, no analytics.
- No accessibility service. No `PACKAGE_USAGE_STATS`.
- No medical claims. We say "comfort," "warmer tones in the evening,"
  "reduces blue light in the displayed image." We do not say "improves
  sleep" / "prevents eye damage" / "treats" anything. The 2025/2026
  evidence consensus has shifted further toward "total luminance > spectrum
  for sleep onset"; we lean into the comfort framing.

## Current planning watchpoints (rev 5)

The latest research pass in `ROADMAP.md` rev 5 adds four current
watchpoints future sessions should not miss:

- **Android developer verification (C141)**: because OpenLumen is
  F-Droid/direct-APK oriented, the package name and release signing
  certificate need an Android Developer Console / Play Console
  registration plan before the September 2026 regional enforcement
  window.
- **GitHub Actions Node 24 and action majors (C142)**: shipped
  2026-05-17. Workflows now use `checkout@v6`, `setup-java@v5`,
  `setup-gradle@v6`, `upload-artifact@v7`, `actions/attest@v4`, and
  `scan-action@v7`; the project keeps major tags by default with a
  documented full-SHA exception path.
- **Android 17 smoke expansion (C143)**: shipped 2026-05-17. The
  Android 17 readiness plan and `docs/device-matrix.md` now include
  memory-limiter (`ApplicationExitInfo` / `MemoryLimiter:AnonSwap`) and
  sw600dp resizability/orientation checks in addition to AAPM, FGS, and
  BAL.
- **Service / engine correctness batch (C132-C136)**: shipped
  2026-05-17. The active-filter service and engine layer now cover the
  pass-2 race/stale-cache findings: ramp atomicity, cancel-before-clear,
  CDM partial-cache invalidation, overlay install/apply/clear locking, and
  SF/KCAL failed-write cache invalidation.
- **AndroidX stable refresh (C144)**: after AGP 9 lands, batch the
  stable AndroidX floor refresh rather than mixing broad dependency churn
  into the toolchain migration.

## Where to look for what

| Question | File |
|---|---|
| What is the project? | [README.md](README.md) |
| What's planned next? | [ROADMAP.md](ROADMAP.md) |
| What changed in each release? | [CHANGELOG.md](CHANGELOG.md) |
| How is the code organized? | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| What's the contribution flow? | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Which devices have been smoke-tested? | [docs/device-matrix.md](docs/device-matrix.md) |
| Which engine works on which hardware? | [docs/compatibility-table.md](docs/compatibility-table.md) |
| What can go wrong with root drivers? | [docs/root-safety.md](docs/root-safety.md) |
| What does Tasker/ADB/Termux see? | [docs/automation.md](docs/automation.md) |
| What does OpenLumen claim about sleep? | [docs/health-evidence.md](docs/health-evidence.md) |
| What's the threat model? | [docs/threat-model.md](docs/threat-model.md) |
| What about Play Store FGS evidence? | [docs/play-fgs-evidence.md](docs/play-fgs-evidence.md) |
| How does the SBOM workflow read? | [docs/sbom-and-advisories.md](docs/sbom-and-advisories.md) |
| What deferred items have design sketches? | [docs/deferred-candidates.md](docs/deferred-candidates.md) |
| What's the per-app design analysis? | [docs/overlay-and-per-app-design.md](docs/overlay-and-per-app-design.md) |
| What's queued for Android 17? | [docs/android-17-readiness.md](docs/android-17-readiness.md) (renamed from `api-36-readiness.md` in rev 4) |
| Which external sources do we monitor? | [docs/research-watchlist.md](docs/research-watchlist.md) |

## Session-research artifacts (this session)

The 2026-05-17 walk-away research session left detailed working notes in
[.ai/research/2026-05-17/](.ai/research/2026-05-17/):

- `STATE_OF_REPO.md` — local reconnaissance memo.
- `MEMORY_CONSOLIDATION.md` — inventory of AI memory / instruction files.
- `SOURCE_REGISTER.md` — local and external sources used in this pass.
- `RESEARCH_LOG.md` — search strategies, queries, saturation notes.
- `COMPETITOR_MATRIX.md` — direct + adjacent + commercial competitors.
- `FEATURE_BACKLOG.md` — raw harvested ideas before prioritisation.
- `PRIORITIZATION_MATRIX.md` — scored / tiered candidates.
- `SECURITY_AND_DEPENDENCY_REVIEW.md` — upgrade and hardening opportunities.
- `DATASET_MODEL_INTEGRATION_REVIEW.md` — datasets, integrations, models.
- `CHANGESET_SUMMARY.md` — files created or modified and why.
