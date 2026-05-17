# OpenLumen

[![Version](https://img.shields.io/badge/version-0.4.0-cba6f7?style=flat-square)](https://github.com/SysAdminDoc/OpenLumen/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0--or--later-a6e3a1?style=flat-square)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/platform-Android%208.0%2B-89b4fa?style=flat-square)](#requirements)
[![Min SDK](https://img.shields.io/badge/minSdk-26-f9e2af?style=flat-square)](app/build.gradle.kts)
[![No INTERNET](https://img.shields.io/badge/INTERNET-not%20requested-94e2d5?style=flat-square)](#privacy)

> **Open-source spiritual successor to Chainfire's CF.Lumen.** Brings root-grade
> display color shifting and overlay-based fallback to modern Android, with the AOSP
> ColorDisplayManager path that CF.Lumen never had.

CF.Lumen has been dormant since December 2020. Red Moon, the main open-source
overlay-only competitor, has been unmaintained since August 2022. OpenLumen exists
to fill that gap on Android 14 / 15 with a clean Compose UI, no telemetry, and four
runtime-selectable display drivers.

---

## Why OpenLumen?

- **Four display drivers, runtime-detected** — uses the highest-quality one the device supports.
- **No INTERNET permission, ever.** Fully offline, F-Droid-clean.
- **Catppuccin Mocha + AMOLED true-black** Compose UI. Dark by default.
- **Quick Settings tile** for one-tap toggling. CF.Lumen never shipped a tile.
- **GPL-3.0-or-later**, aligned with Red Moon's lineage.
- **Modern Android targets** — minSdk 26, targetSdk 35 (Android 15).
- **Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · DataStore · kotlinx.serialization.

## Display drivers

OpenLumen ships four `ColorEngine` implementations and probes each at first launch:

| Driver | Root? | SoC | Quality | Notes |
|--------|------|-----|---------|-------|
| `ColorDisplayManager` | No¹ | Any (AOSP-derived) | Framebuffer | Same path the system Night Light uses. API 28+. |
| `SurfaceFlinger` | Yes | Any | Framebuffer | `service call SurfaceFlinger` color-transform. Works on Tensor/Exynos/MediaTek too. |
| `KCAL` | Yes | Qualcomm | Panel driver | Requires custom kernel exposing `/sys/devices/platform/kcal_ctrl.0/kcal*`. |
| `Overlay` | No | Any | Compositor blend | Universal fallback. Capped at ~80% opacity by Android 12+ rules. |

¹ Some builds require granting `WRITE_SECURE_SETTINGS` via:
`adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`

The app falls back gracefully — if none of the root paths work, you still get the
overlay driver. If you want to pin a specific driver, Settings → Driver lets you.

## Features (v0.5.0 — in development)

**Color**

- Named presets: Night · Amber · Red · Salmon · Sepia · Grayscale · Deep Sleep · Protan · Deutan · Tritan
- Custom R/G/B picker on Home with live color preview
- Kelvin color-temperature slider (1000–10 000 K) with Tanner Helland conversion
- Per-channel gamma sliders (γR / γG / γB, range 0.5–2.5)
- Intensity slider (0–100% lerp toward identity) and dim slider (0–95%)
- Contrast multiplier (0.5–2.0×)
- AMOLED true-black clamp (opt-in; snaps very dim subpixels to fully off)
- Blue-channel reduction indicator (physical measurement; not a sleep claim)

**Scheduling**

- Solar-position schedule (NOAA algorithm, hand-rolled, no external library)
- Sunset / sunrise offset sliders (±180 minutes)
- Fixed-time schedule with Material 3 24-hour time pickers
- "Until my next alarm" mode driven by the system alarm clock
- Manual location entry with bundled offline city picker (~95 cities, no Play Services)
- No runtime location permission; solar scheduling uses coordinates the user enters
- Ambient-light-sensor trigger (lux below threshold engages filter; OR with schedule)
- Live lux readout + "calibrate: use current reading" button
- Smooth fade-in / fade-out transitions (Instant / 30 s / 5 min / 15 min / 30 min)
- Schedule timezone hint so fixed-time windows are unambiguous after travel

**Command surfaces**

- Quick Settings tile (subtitle shows active preset; long-press opens the app)
- 1 × 1 toggle widget
- 4 × 1 favorites widget (tap a chip to switch presets)
- Foreground-service notification with Turn-off and Next-preset actions
- Documented Tasker / Termux / ADB intent surface (`docs/automation.md`)

**Persistence + reliability**

- Foreground service with `specialUse` type (Android 14+ compliant)
- AlarmManager-driven schedule transitions (`setExactAndAllowWhileIdle`, Doze-resilient)
- Boot persistence with crash-window safety net (no auto-restart after a recent crash)
- Profile export / import as JSON via Storage Access Framework, with diff preview
- Named profile library — save current configuration, load it back later
- Previous-preset restore (one-tap undo of a preset change)
- Local-only crash log + structured diagnostics log (`filesDir/`, viewable in-app)
- Versioned preference schema with explicit migrations

**Trust + privacy**

- No INTERNET permission, ever (CI rejects builds that contain one)
- No Play Services / Firebase / Google APIs (CI rejects builds that pull them in)
- No accessibility service, no usage-stats permission, no foreground-app detection
- Permission rationale card for SYSTEM_ALERT_WINDOW (overlay driver)
- In-app driver report (Copy or Share) with zero PII — captures device, build,
  granted permissions, every engine's probe result

## Privacy

OpenLumen requests **zero** network permissions. No telemetry, no crash reporting,
no remote config. Verify with:

```bash
aapt dump permissions OpenLumen-release.apk | grep INTERNET
# (no output)
```

## Requirements

- Android 8.0 (API 26) or higher
- For best results: root with Magisk-managed `su`, OR a Pixel / Android-One device
  whose system Night Light is functional (the AOSP driver piggy-backs on it).

## Build

```bash
git clone https://github.com/SysAdminDoc/OpenLumen.git
cd OpenLumen
./gradlew assembleRelease
```

Signed release builds require:

```bash
export OPENLUMEN_KEYSTORE=/path/to/release.jks
export OPENLUMEN_KEYSTORE_PASSWORD=...
export OPENLUMEN_KEY_ALIAS=openlumen
export OPENLUMEN_KEY_PASSWORD=...
./gradlew assembleRelease
```

## Module layout

```
OpenLumen/
├── app/             — Compose UI, foreground service, tile, boot receiver
├── core-engine/     — ColorEngine abstraction + 4 driver impls + DriverProbe
├── core-schedule/   — NOAA solar calculator, schedule modes, light sensor adapter
└── core-prefs/      — DataStore-backed prefs, JSON serialization
```

## Documentation

### For users

- [Troubleshooting](docs/troubleshooting.md) — common driver and overlay problems, recovery from stuck states
- [Compatibility table](docs/compatibility-table.md) — which engines work on which hardware
- [Root mode safety and recovery](docs/root-safety.md) — what can go wrong with root drivers, and how to recover
- [Automation](docs/automation.md) — Tasker / Termux / ADB intent reference
- [Health and evidence notes](docs/health-evidence.md) — what we will and will not claim

### For contributors

- [Architecture overview](docs/ARCHITECTURE.md) — modules, runtime flow, engine contract
- [Contributing](CONTRIBUTING.md) — style, tests, driver-work expectations
- [Translations and localization](docs/translations.md) — how to contribute a translation
- [Device validation matrix](docs/device-matrix.md) — per-engine smoke flow, current device coverage
- [Profile import lineage formats](docs/profile-import-formats.md) — notes for future Red Moon / CF.Lumen importers

### For distributors and packagers

- [Release checklist](docs/release-checklist.md) — pre-flight, verification, no-INTERNET assertion
- [Reproducible build notes](docs/reproducible-build.md) — environment pinning, verification procedure
- [Play FGS evidence pack](docs/play-fgs-evidence.md) — Play `specialUse` justification

### Security and supply chain

- [Threat model](docs/threat-model.md) — MASVS-lite categories with mitigations
- [SBOM and advisory scan](docs/sbom-and-advisories.md) — CI workflow and triage policy
- [Dependency verification](docs/dependency-verification.md) — Gradle metadata procedure (opt-in)
- [Wake / alarm / battery audit](docs/wake-and-vitals.md) — what wakes the device and why

### Roadmap and design

- [Roadmap](ROADMAP.md) — source-backed release plan with the candidate inventory
- [Overlay safety and per-app design notes](docs/overlay-and-per-app-design.md) — why per-app behavior is deferred until the trust posture is sorted
- [Deferred roadmap candidates](docs/deferred-candidates.md) — design sketches for Wear OS / Android TV / etc.
- [Android 17 readiness](docs/android-17-readiness.md) — forward-looking Android-version migration notes (renamed from `api-36-readiness.md` in rev 4)
- [Research watchlist](docs/research-watchlist.md) — sources we monitor before release planning

## Emergency off

If a release goes wrong and the overlay or root driver leaves your screen
in a bad state, the canonical escape hatch is:

```bash
adb shell am startservice -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.LumenService
```

See [docs/root-safety.md](docs/root-safety.md) for more recovery paths.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the source-backed release plan and the
candidate inventory. The `Now: v0.5.0` section is largely shipped; the
deferred items have design sketches in
[docs/deferred-candidates.md](docs/deferred-candidates.md) and
[docs/overlay-and-per-app-design.md](docs/overlay-and-per-app-design.md).

Post-v0.5.0 work clusters around:

- A Shizuku-backed privileged path for per-app behavior (C06)
- Direct Boot restore (C28)
- AGP 9 + Hilt Compose artifact migration (C95 + C96)
- Wear OS companion as a separate F-Droid package (C21)
- Real-device validation rows in `docs/device-matrix.md` (C01)

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

## Acknowledgements

- **Chainfire (Jorrit Jongma)** for the original CF.Lumen and for documenting the
  driver-backend approach so thoroughly that this project could be built without
  the original source.
- **LibreShift/red-moon** for proving open-source overlay shifting works on Android.
