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

## Features (v0.4.0)

- Named presets: Night · Amber · Red · Salmon · Sepia · Grayscale · Deep Sleep · Protan · Deutan · Tritan
- Custom R/G/B picker on Home with live color preview
- Per-channel gamma sliders (γR / γG / γB, range 0.5–2.5)
- Intensity slider (0–100% lerp toward identity) + dim slider (0–95%)
- Solar-position schedule (NOAA algorithm, hand-rolled, no external library)
- Sunset / sunrise offset sliders (±180 minutes)
- Fixed-time schedule with Material 3 24-hour time pickers
- Manual location entry (decimal degrees, validated)
- No runtime location permission; solar scheduling uses coordinates the user enters
- Ambient-light-sensor trigger (lux below threshold engages filter; OR with schedule)
- Live lux readout + "calibrate: use current reading" button
- Permission rationale card for SYSTEM_ALERT_WINDOW (overlay driver)
- Quick Settings tile
- Boot persistence
- Foreground service with `specialUse` type (Android 14+ compliant)
- AlarmManager-driven schedule transitions (`setExactAndAllowWhileIdle`, Doze-resilient)
- Profile export / import as JSON via Storage Access Framework
- Local-only crash log (`filesDir/crash.log`, viewable in-app)

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

- [Architecture overview](docs/ARCHITECTURE.md) — modules, runtime flow, engine contract
- [Automation](docs/automation.md) — Tasker / Termux / ADB intent reference
- [Troubleshooting](docs/troubleshooting.md) — common driver and overlay problems
- [Compatibility table](docs/compatibility-table.md) — public summary of which engines work on which hardware
- [Device validation matrix](docs/device-matrix.md) — per-engine smoke flow, current device coverage
- [Root mode safety and recovery](docs/root-safety.md) — what can go wrong with root drivers, and how to recover
- [Release checklist](docs/release-checklist.md) — pre-flight, verification, no-INTERNET assertion
- [Reproducible build notes](docs/reproducible-build.md) — environment pinning, verification procedure
- [Threat model](docs/threat-model.md) — MASVS-lite categories with mitigations
- [SBOM and advisory scan](docs/sbom-and-advisories.md) — CI workflow and triage policy
- [Dependency verification](docs/dependency-verification.md) — Gradle metadata procedure (opt-in)
- [Wake / alarm / battery audit](docs/wake-and-vitals.md) — what wakes the device and why
- [Play FGS evidence pack](docs/play-fgs-evidence.md) — Play `specialUse` justification
- [Profile import lineage formats](docs/profile-import-formats.md) — notes for future Red Moon / CF.Lumen importers
- [Translations and localization](docs/translations.md) — how to contribute a translation
- [Overlay safety and per-app design notes](docs/overlay-and-per-app-design.md) — why per-app behavior is deferred until the trust posture is sorted
- [Deferred roadmap candidates](docs/deferred-candidates.md) — design sketches for Wear OS / Android TV / alarm schedules / contrast / AMOLED / CVD LUT / etc.
- [Health and evidence notes](docs/health-evidence.md) — what we will and will not claim
- [Contributing](CONTRIBUTING.md) — style, tests, driver-work expectations
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

See [ROADMAP.md](ROADMAP.md) for the source-backed release plan. Near-term highlights:

- Device validation matrix and shareable driver reports
- F-Droid metadata, reproducible-build notes, and release checklist
- Overlay-safe pause/emergency-off flows
- Notification, Quick Settings, and widget command surfaces
- Versioned profile migrations and import preview

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

## Acknowledgements

- **Chainfire (Jorrit Jongma)** for the original CF.Lumen and for documenting the
  driver-backend approach so thoroughly that this project could be built without
  the original source.
- **LibreShift/red-moon** for proving open-source overlay shifting works on Android.
