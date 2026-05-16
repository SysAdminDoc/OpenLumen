# OpenLumen

[![Version](https://img.shields.io/badge/version-0.2.0-cba6f7?style=flat-square)](https://github.com/SysAdminDoc/OpenLumen/releases)
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

## Features (v0.2.0)

- Named presets: Night · Amber · Red · Salmon · Sepia · Grayscale · Deep Sleep · Protan · Deutan · Tritan
- Custom R/G/B picker on Home with live color preview
- Per-channel gamma sliders (γR / γG / γB, range 0.5–2.5)
- Intensity slider (0–100% lerp toward identity) + dim slider (0–95%)
- Solar-position schedule (NOAA algorithm, hand-rolled, no external library)
- Sunset / sunrise offset sliders (±180 minutes)
- Fixed-time schedule with Material 3 24-hour time pickers
- Manual location entry (decimal degrees, validated)
- Ambient-light-sensor trigger (lux below threshold engages filter; OR with schedule)
- Live lux readout + "calibrate: use current reading" button
- Permission rationale card for SYSTEM_ALERT_WINDOW (overlay driver)
- Quick Settings tile
- Boot persistence
- Foreground service with `specialUse` type (Android 14+ compliant)

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

## Roadmap

See [ROADMAP.md](ROADMAP.md) for v0.2.0 → v1.0.0 plans. Highlights:

- Time-picker dialog for fixed-time schedule (currently text-only)
- Location picker (manual coords + FusedLocation opt-in)
- Custom RGB color picker on Home screen
- Home-screen widget
- Wear OS companion (deferred to post-v1.0)

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

## Acknowledgements

- **Chainfire (Jorrit Jongma)** for the original CF.Lumen and for documenting the
  driver-backend approach so thoroughly that this project could be built without
  the original source.
- **LibreShift/red-moon** for proving open-source overlay shifting works on Android.
