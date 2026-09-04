# OpenLumen

[![Version](https://img.shields.io/badge/version-0.7.1-cba6f7?style=flat-square)](https://github.com/SysAdminDoc/OpenLumen/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0--or--later-a6e3a1?style=flat-square)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/platform-Android%208.0%2B-89b4fa?style=flat-square)](#requirements)
[![Min SDK](https://img.shields.io/badge/minSdk-26-f9e2af?style=flat-square)](app/build.gradle.kts)
[![No INTERNET](https://img.shields.io/badge/INTERNET-not%20requested-94e2d5?style=flat-square)](#privacy)

> **It changes the display, not a layer on top of it.** Three of OpenLumen's
> four drivers work below the app layer: two write the colour transform the
> compositor already applies, and one drives the panel through the kernel. The
> tint reaches the status bar, the notification shade and the lock screen, and
> it never swallows a tap. The overlay driver is the fallback for devices where
> nothing else is reachable, not the design.

If you came here after CF.Lumen, that is probably the sentence you were
looking for. Most of what replaced it paints a coloured window over your
screen, and it shows: the tint stops at the edges of the app, taps land in the
wrong place during installs, and every screenshot comes out orange.

Two things people actually use this for:

**Fixing a screen that is the wrong colour.** Some panels ship with a cast,
usually a green one that reads as a yellow tint on white. Per-channel RGB and
gamma let you pull it back, and the settings persist across reboots, so this
is a calibration you make once rather than a mode you turn on at night.

**Evening colour shift.** The usual thing: warm the screen on a schedule, by
clock time, by sunrise and sunset for your location, or until your next alarm.

There is also a workflow some people want that no Android app really serves:
holding the panel's hardware brightness high while dimming in software, rather
than letting the backlight drop. Every driver here dims in software, and the
root drivers and the system's Extra Dim can go below the panel's own minimum.
Whether that suits you is yours to judge; see
[docs/health-evidence.md](docs/health-evidence.md) for what is and is not
established.

CF.Lumen has been dormant since December 2020. Red Moon, the main open-source
overlay-only competitor, has been unmaintained since August 2022. OpenLumen
exists to fill that gap on Android 14 / 15 with a Compose UI, no telemetry,
and four runtime-selectable display drivers.

---

## Why OpenLumen?

- **Three of four drivers work below the app layer**, so the tint covers the whole screen, passes touches through and leaves screenshots untinted.
- **Per-channel RGB and gamma** for correcting a panel's colour cast, not just warming it.
- **No INTERNET permission, ever.** Fully offline, F-Droid-clean.
- **Catppuccin Mocha + AMOLED true-black** Compose UI. Dark by default.
- **Quick Settings tile** for one-tap toggling. CF.Lumen never shipped a tile.
- **GPL-3.0-or-later**, aligned with Red Moon's lineage.
- **Modern Android targets**: minSdk 26, targetSdk 35 (Android 15).
- **Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · DataStore · kotlinx.serialization.

## Display drivers

OpenLumen ships four `ColorEngine` implementations and probes each at first launch:

| Driver | Root? | SoC | Quality | Notes |
|--------|------|-----|---------|-------|
| `SecureSettings` | No¹ | Any | Framebuffer | Drives system Night Light and Extra Dim through `Settings.Secure`. API 29+. |
| `SurfaceFlinger` | Yes | Any | Framebuffer | `service call SurfaceFlinger` color-transform. Works on Tensor/Exynos/MediaTek too. |
| `KCAL` | Yes | Qualcomm | Panel driver | Requires custom kernel exposing `/sys/devices/platform/kcal_ctrl.0/kcal*`. |
| `Overlay` | No | Any | Compositor blend | Universal fallback. Capped at ~80% opacity by Android 12+ rules. |

**None of these rows has been confirmed on real hardware yet.** They describe
what each driver does by construction, read from the platform sources they
call. [docs/device-matrix.md](docs/device-matrix.md) is where confirmed
results go, and every row in it currently says untested. If you run one, the
Driver tab's Share report produces exactly what that file wants.

¹ Requires granting `WRITE_SECURE_SETTINGS` once:
`adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`

The secure-settings driver writes the same rows `ColorDisplayService` watches, so
the tint is applied by the compositor: it covers the status bar, notification
shade and lock screen, it does not block touches, and screenshots come out
untinted. On devices that ship Extra Dim (Android 12+, and not every OEM does) it
also dims below the panel's minimum backlight without root, which is the one
thing the overlay driver cannot do. Note it carries chromaticity as a colour temperature, so
per-channel gamma and the colour-vision presets still need a root driver.

Releases through 0.7.1 shipped a `ColorDisplayManager` driver here that could not
work on any user install: those APIs need `CONTROL_DISPLAY_COLOR_TRANSFORMS`, which
is `signature|privileged` and cannot be granted with `pm grant`. If you tried the
AOSP driver on an older build and saw nothing happen, that was why.

The app falls back gracefully: Auto prefers the best available root path
(`SurfaceFlinger`, then `KCAL`). Non-root devices use Overlay by default. If
you want a specific driver such as `SecureSettings`, Settings → Driver
lets you pin one; if that pinned driver later probes as unavailable, OpenLumen
resets to Auto instead of leaving the filter enabled with no visible effect.

## Features (v0.7.1)

**Color**

- Named presets: Night · Amber · Red · Salmon · Sepia · Grayscale · Deep Sleep · PWM Comfort · Protan · Deutan · Tritan
- Software dimming on every driver, and below the panel minimum on the root drivers and the system Extra Dim, for holding hardware brightness high and darkening in software instead ([what is and is not established](docs/health-evidence.md))
- Custom R/G/B picker on Home with live color preview
- Kelvin color-temperature slider (1000 to 10 000 K) with Tanner Helland conversion
- Per-channel gamma sliders (γR / γG / γB, range 0.5 to 2.5)
- Intensity slider (0 to 100% lerp toward identity) and dim slider (0 to 95%)
- Contrast multiplier (0.5 to 2.0×)
- AMOLED true-black clamp (opt-in; snaps very dim subpixels to fully off)
- Blue-channel reduction indicator (physical measurement; not a sleep claim)

**Scheduling**

- Solar-position schedule (NOAA algorithm, hand-rolled, no external library)
- Sunset / sunrise offset sliders (±180 minutes)
- Fixed-time schedule with Material 3 24-hour time pickers
- Exact-alarm warning with a settings action when Android downgrades timed transitions to inexact alarms
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
- Token-authenticated Tasker / Termux / ADB intent surface, off by default (`docs/automation.md`)

**Persistence + reliability**

- Foreground service with `specialUse` type (Android 14+ compliant)
- AlarmManager-driven schedule transitions (`setExactAndAllowWhileIdle`, Doze-resilient)
- Boot persistence with crash-window safety net (no auto-restart after a recent crash)
- Profile export / import as JSON via Storage Access Framework, with diff preview
- Named profile library: save current configuration, load it back later
- Portable preset packs with merge preview, custom built-in labels, and alphabetical or recent ordering
- Previous-preset restore (one-tap undo of a preset change)
- Local-only crash log + structured diagnostics log (`filesDir/`, viewable in-app)
- Diagnostics timeline range and text search over the active log filters
- Versioned preference schema with explicit migrations

**Trust + privacy**

- No INTERNET permission, ever (local release checks reject builds that contain one)
- No Play Services / Firebase / Google APIs (local release checks reject builds that pull them in)
- Cloud backup includes solar coordinates only when Android reports client-side encryption; device transfer and explicit profile export remain available without that capability
- No accessibility service, no usage-stats permission, no foreground-app detection
- Permission rationale card for SYSTEM_ALERT_WINDOW (overlay driver)
- In-app driver report (Copy or Share) with zero PII: captures device, build,
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
- For best results: root with Magisk-managed `su`, OR any Android 10+ device with
  `WRITE_SECURE_SETTINGS` granted over ADB (the secure-settings driver rides the
  system's own Night Light transform).

## Build

```bash
git clone https://github.com/SysAdminDoc/OpenLumen.git
cd OpenLumen
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot"
./gradlew assembleRelease
# Fails unless release signing env vars are present.
```

Signed release builds require:

```bash
export OPENLUMEN_KEYSTORE=/path/to/release.jks
export OPENLUMEN_KEYSTORE_PASSWORD=...
export OPENLUMEN_KEY_ALIAS=openlumen
export OPENLUMEN_KEY_PASSWORD=...
./gradlew assembleRelease
```

Unsigned release output is only for local reproducibility or F-Droid rebuild checks:

```bash
./gradlew assembleRelease -Popenlumen.allowUnsignedRelease=true
```

Run the full local release gate before tagging:

```bash
py -3 tools/local_release_gate.py
```

The gate's SPDX SBOM includes a declared/concluded SPDX license and source
provenance for every resolved release dependency. A dependency with missing or
prohibited metadata fails the gate unless its exact coordinate has a reviewed
entry in `tools/sbom-license-overrides.json`.

Review stable version-catalog updates during release planning:

```bash
py -3 tools/dependency_update_review.py
```

Lint user-facing copy for unsupported health claims:

```bash
py -3 tools/health_claim_lint.py
```

For local reproducibility or F-Droid rebuild checks without signing keys:

```bash
py -3 tools/local_release_gate.py --allow-unsigned-release
```

Gate outputs are written to `build/reports/openlumen-release-gate/`:
SBOM JSON, advisory report, release classpath, SHA-256 sums, and signature status.

The signed/release gate queries OSV and fails on incomplete responses, missing
severity metadata, or unreviewed High/Critical advisories. An offline advisory
scaffold is reserved for explicitly unsigned local/F-Droid checks:
`py -3 tools/local_release_gate.py --allow-unsigned-release --advisory-mode offline`.

## Module layout

```
OpenLumen/
├── app/            : Compose UI, foreground service, tile, boot receiver
├── core-engine/    : ColorEngine abstraction + 4 driver impls + DriverProbe
├── core-schedule/  : NOAA solar calculator, schedule modes, light sensor adapter
└── core-prefs/     : DataStore-backed prefs, JSON serialization
```

## Documentation

### For users

- [Troubleshooting](docs/troubleshooting.md): common driver and overlay problems, recovery from stuck states
- [Compatibility table](docs/compatibility-table.md): which engines work on which hardware
- [Root mode safety and recovery](docs/root-safety.md): what can go wrong with root drivers, and how to recover
- [Automation](docs/automation.md): Tasker / Termux / ADB intent reference
- [Health and evidence notes](docs/health-evidence.md): what we will and will not claim

### For contributors

- [Architecture overview](docs/ARCHITECTURE.md): modules, runtime flow, engine contract
- [Contributing](CONTRIBUTING.md): style, tests, driver-work expectations
- [Translations and localization](docs/translations.md): how to contribute a translation
- [Device validation matrix](docs/device-matrix.md): per-engine smoke flow, current device coverage
- Driver report matrix helper: `py -3.12 tools/driver_report_matrix.py report.txt`
- [Profile import lineage formats](docs/profile-import-formats.md): notes for future Red Moon / CF.Lumen importers

### For distributors and packagers

- [Release checklist](docs/release-checklist.md): pre-flight, verification, no-INTERNET assertion
- [Reproducible build notes](docs/reproducible-build.md): environment pinning, verification procedure
- [Play FGS evidence pack](docs/play-fgs-evidence.md): Play `specialUse` justification

### Security and supply chain

- [Threat model](docs/threat-model.md): MASVS-lite categories with mitigations
- [SBOM and advisory scan](docs/sbom-and-advisories.md): local scan and triage policy
- [Dependency verification](docs/dependency-verification.md): Gradle metadata procedure (opt-in)
- [Wake / alarm / battery audit](docs/wake-and-vitals.md): what wakes the device and why

### Roadmap and design

- [Overlay safety and per-app design notes](docs/overlay-and-per-app-design.md): why per-app behavior is deferred until the trust posture is sorted
- [Deferred roadmap candidates](docs/deferred-candidates.md): design sketches for Wear OS / Android TV / etc.
- [Android 17 readiness](docs/android-17-readiness.md): forward-looking Android-version migration notes (renamed from `api-36-readiness.md` in rev 4)
- [Research watchlist](docs/research-watchlist.md): sources we monitor before release planning

## Emergency off

If a release goes wrong and the overlay or root driver leaves your screen
in a bad state, the canonical escape hatch is:

The exported automation receiver requires a token you generate in the app
(About → Automation access) on every command except this one. Turning the
filter off is exempt on purpose: it only ever moves the filter toward off, and
it has to keep working when the screen is too tinted to read a token off.
The receiver's outbound filter-state broadcast is signature-protected and is
not a general-purpose inter-app state channel.

```bash
adb shell am broadcast --include-stopped-packages -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.AutomationReceiver
```

This works whether or not OpenLumen is running. If the app was force-stopped or
killed, `--include-stopped-packages` is what lets the broadcast reach it at all,
and the receiver clears the display itself rather than waiting on a service
Android will not let it start from the background.

See [docs/root-safety.md](docs/root-safety.md) for more recovery paths.

## Roadmap

Planned work is tracked in the repository's issues. Deferred items have design
sketches in
[docs/deferred-candidates.md](docs/deferred-candidates.md) and
[docs/overlay-and-per-app-design.md](docs/overlay-and-per-app-design.md).

Post-v0.7.1 work clusters around:

- A Shizuku-backed privileged path for per-app behavior (C06)
- Wear OS companion as a separate F-Droid package (C21)
- Real-device validation rows in `docs/device-matrix.md` (C01)

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

## Acknowledgements

- **Chainfire (Jorrit Jongma)** for the original CF.Lumen and for documenting the
  driver-backend approach so thoroughly that this project could be built without
  the original source.
- **LibreShift/red-moon** for proving open-source overlay shifting works on Android.
