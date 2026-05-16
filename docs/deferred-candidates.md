# Deferred Roadmap Candidates

> Durable analysis of the roadmap candidates that the v0.5.0 work has
> *not* delivered, with the reasoning behind the deferral and the design
> sketch a future contributor would start from.
>
> Companion to [docs/overlay-and-per-app-design.md](overlay-and-per-app-design.md)
> (overlay-touch and per-app candidates) and the
> `Now: v0.5.0` section of [../ROADMAP.md](../ROADMAP.md). Read those
> first; this doc covers everything else.

## Why these are deferred

Each entry below is one of:

1. **Hardware-blocked** — needs a device test pass we couldn't run in
   the development environment.
2. **Design-blocked** — needs a UX or scope decision that's bigger than
   the implementation effort.
3. **Tier-correct** — the roadmap explicitly placed the candidate in
   Later / Under Consideration / Rejected, and shipping it sooner would
   change the product shape away from "minimal-permission display
   filter."

Deferral is *not* abandonment. Each entry has a viable plan; we just
haven't picked it up yet.

## Wear OS — C21 (Next)

**Status**: Next-tier. Will likely land in v0.7.0.

**Design sketch**:

1. New Gradle module `wear/` (Android Wear app target, minSdk 30).
2. Phone-side companion uses the existing `Wearable.MessageClient` (Play
   Services Wearable Data Layer). **This breaks F-Droid cleanness.**
3. **Therefore**: the Wear companion must ship as a *separate APK* and a
   *separate F-Droid package*, not as a flavor of the main app. The
   main `com.openlumen` stays no-INTERNET.
4. Phone-side companion's only network surface is the Wearable Data
   Layer — local Bluetooth, no internet — which is acceptable for the
   companion.
5. Wear tile: single button that issues `ACTION_TOGGLE` to the phone
   service through the data layer.
6. No display tinting on the watch itself — that promise stays unmet
   for hardware reasons (Wear OS displays use different driver paths
   and the value/risk ratio is much worse).

**Why deferred**: the F-Droid posture conversation needs to happen
first. Splitting into a separate package is the only honest path; the
main app stays clean.

## Android TV — C22 (Later)

**Status**: Later-tier.

**Design sketch**:

1. New flavor `tv/` of the main app with `<uses-feature android:name="android.software.leanback" />`.
2. D-pad navigation: Compose Material 3 has leanback-aware focus
   handling but the layouts must be re-tuned for ~10-foot UI.
3. Overlay engine works on TV; CDM works if the TV firmware exposes
   the AOSP path. SF/KCAL are not relevant (Android TV firmware
   typically blocks `su`).
4. Risk: most Android TV devices have heavily customized firmware that
   ignores `ColorDisplayManager`. The Driver tab's per-engine status
   becomes a screenshot worth shipping.

**Why deferred**: niche audience, high test-matrix cost. Revisit when
the phone-form is solid.

## Alarm-based schedule presets — C25 (shipped in v0.5.0)

Shipped. `ScheduleMode.UntilNextAlarm(start, nextAlarmAt)` + the
matching `ScheduleModeDto.UntilNextAlarm` enum entry. `LumenService`
reads `AlarmManager.getNextAlarmClock()` and passes the result into
the pure schedule logic. A 12-hour fallback prevents the filter from
running indefinitely when no alarm is set. Tested in
`core-schedule/.../ScheduleTest.kt`.

## Final icon and adaptive launcher — C35 (Now → deferred to icon design pass)

**Status**: blocks F-Droid metadata completion (C36), but the underlying
work is graphic design, not code.

**Plan**:

1. Pick one of the five prompts in `branding/logo-prompts.md`.
2. Generate adaptive-icon foreground + background SVG/PNG.
3. Replace `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` and
   the legacy `mipmap-*` densities.
4. Drop the final files into
   `fastlane/metadata/android/en-US/images/icon.png` (512×512).

**Why deferred**: hand-drawn or design-tool work, not code. A
contributor with design skill can pick this up independently.

## Store screenshots — C36 (hardware-blocked)

**Status**: blocked on a device or emulator capture pass.

**Plan**:

1. Set up a Pixel 6 / 7 / 8 emulator at 1080×2400.
2. Capture each tab (Home / Schedule / Presets / Driver / About) with
   a representative configuration.
3. Drop them into
   `fastlane/metadata/android/en-US/images/phoneScreenshots/`.

**Why deferred**: no real-device or emulator in the development
environment we worked in.

## Play Store listing — C39 (Under Consideration)

**Status**: optional; F-Droid remains primary.

The full reasoning lives in
[docs/play-fgs-evidence.md](play-fgs-evidence.md). Short version: a
maintainer who wants to ship to Play can recreate the evidence pack
from primary sources; we have not committed maintenance bandwidth to
the dual-channel release process.

## Compose screenshot tests — C83 (hardware-blocked)

**Status**: scaffold-only would be possible without hardware; running
them needs a JVM + Compose test runtime.

**Plan**:

1. Add `androidx.compose.ui:ui-test-junit4` and `compose-bom`'s
   `ui-test-manifest` to the app `androidTestImplementation` block.
2. Add a screenshot test that exercises each tab with a fixed
   `Preferences` snapshot via Hilt test rules.
3. Use Roborazzi or Paparazzi for JVM-host screenshot capture (the
   latter has more mature golden-image tooling).

**Why deferred**: the value comes from running the tests, not from
having them. Without a CI runner that can render Compose layouts,
the tests would never gate anything.

## Connected permission flow tests — C84 (hardware-blocked)

**Status**: emulator-only.

**Plan**:

1. Add `androidx.test.uiautomator` to `androidTestImplementation`.
2. Write tests that exercise `SYSTEM_ALERT_WINDOW`,
   `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and the system
   installer overlay-blocked-touch behavior.
3. Run on a CI runner with an attached emulator
   (`reactivecircus/android-emulator-runner`).

**Why deferred**: same as C83 — needs CI infrastructure that's not
configured.

## SurfaceView / TextureView overlay regression — C91 (hardware-blocked)

**Status**: hardware-only.

**Plan**: smoke test against an app that uses SurfaceView (a video
player, YouTube, a camera viewfinder) on each device in
`docs/device-matrix.md`. Captures whether the overlay alpha is
correctly composited or skipped on the SurfaceView surface.

**Why deferred**: needs real apps and real devices to reproduce.

## One-handed / foldable / split-screen — C92 (hardware-blocked)

**Status**: needs foldable + tablet hardware.

**Plan**: smoke pass against a foldable in folded and unfolded mode,
plus split-screen and pop-up-view modes on tablet form factors.

**Why deferred**: out of reach in this development environment.

## Accessibility scanner / font scale / CVD contrast — C55, C56, C57 (hardware-blocked)

**Status**: Google's Accessibility Scanner app + a real device.

**Plan**:

1. Install Accessibility Scanner on a Pixel.
2. Run it against each of OpenLumen's tabs.
3. Fix any flagged items (label coverage, touch target size,
   contrast).
4. Capture screenshots at 1.0×, 1.3×, 1.5× font scale to verify
   layouts don't break.

**Why deferred**: tooling needs real device.

## Melanopic / circadian estimate UI — C61 (Later)

**Status**: Later-tier.

**Plan**: surface an "estimated melanopic-DER reduction" % alongside
the active preset, derived from the current matrix's blue-channel
suppression.

**Why deferred**: the melanopic estimate is approximate (~10–20%
error) and easy to misread as a health claim. We'd need to land it
alongside more thorough copy in
[docs/health-evidence.md](health-evidence.md) explaining the
limits. Until then, the risk-reward is wrong.

## Color-vision-deficiency LUT correction — C63 (Later)

**Status**: Later-tier.

**Plan**: ship a precomputed LUT (256 entries per channel) for the
common CVD types (deuteranomaly / protanomaly / tritanomaly).

**Why deferred**: requires either a small bundled binary LUT
(non-reproducibility risk) or runtime LUT computation (CPU cost on
slider drags). Needs a careful implementation pass. Existing
CVD-remap presets in `Presets.kt` are a coarser channel-shuffle
approximation that covers the same use case at lower fidelity.

## Contrast control — C64 (shipped in v0.5.0)

Shipped as a Home-tab slider. `Preferences.contrast` (range 0.5..2.0,
default 1.0) feeds through to `LumenService.applyContrast()`. The
overlap with presetIntensity and gamma is real but settled with a
clear UX: contrast operates on top of intensity/gamma rather than
replacing either. Bias takes effect on the SurfaceFlinger engine; the
other engines get the contrast-scaled channel values without bias —
acceptable degradation given they don't model bias at all.

## AMOLED black clamp — C66 (Later)

**Status**: Later-tier; hardware-sensitive.

**Plan**: detect near-black pixels and force them to 0,0,0 to
preserve OLED true-black power savings. Requires the root engines —
the overlay engine can't reach individual pixels.

**Why deferred**: needs framebuffer access; OEM kernels expose
this surface inconsistently. Real value but high engineering cost.

## Content-aware dimming — C67 (Later; privacy-heavy)

**Status**: Later-tier; significant privacy implications.

**Plan**: sample on-screen content to inform dim/intensity adjustments
(like `wluma` on Linux).

**Why deferred**: screen-content access on Android requires either
`MediaProjection` (user-visible recording indicator) or
AccessibilityService. Both are heavy privacy surfaces for what is
ultimately a convenience feature.

## Partial-screen filters — C68 (Under Consideration)

**Status**: Under Consideration; needs a use-case spike.

**Plan**: an overlay limited to a region (top half, single app
window, etc.).

**Why deferred**: the per-app design questions in
`docs/overlay-and-per-app-design.md` apply here; the region-targeting
mechanism would need the same foreground-app or window-bounds
detection. The use case (Iris-style migraine relief) is real but
narrow.

## IPC / local socket automation — C72 (Later)

**Status**: Later-tier.

**Plan**: expose a local Unix socket or content provider that other
on-device apps can dispatch commands to.

**Why deferred**: we already expose `am startservice` intent
commands which work for every automation tool we've seen. Adding a
socket adds attack surface for marginal capability gain.

## Work profile / multi-user behavior — C81 (Later)

**Status**: Later-tier.

**Plan**: test that OpenLumen on a personal profile correctly
ignores the work profile (no foreground-app detection across the
profile boundary), and that the overlay correctly covers the work
profile from the personal-profile service.

**Why deferred**: requires a multi-user device setup; mostly a
validation pass once C11/C12 land.

## System brightness write support — C86 (Under Consideration)

**Status**: Under Consideration.

**Plan**: `WRITE_SETTINGS` to drive the system brightness from
within OpenLumen.

**Why deferred**: confusing UX (users would have a brightness
slider in two places that interact). Probably reject after a UX
pass.

## Pixel-grid AMOLED dimming — C89 (Under Consideration)

**Status**: Under Consideration.

**Plan**: overlay a sparse-grid pattern that turns off N% of pixels
to dim AMOLED panels below the system minimum without changing
color.

**Why deferred**: Pixel Filter (the upstream project) proves the
demand. Android 12+ untrusted-touch and overlay-alpha rules make
the implementation more constrained than the original Android 8 era
this technique was designed for. Burn-in concerns are also real.
Needs a hardware spike before commitment.

## What's not in this document

The candidates that *are* shipped in v0.5.0 are listed in the
"Progress toward v0.5.0" section of
[../ROADMAP.md](../ROADMAP.md). The candidates with their own
dedicated design docs (C11 / C12 / C28 / C69 / C90 / C95 / C96) live
in [docs/overlay-and-per-app-design.md](overlay-and-per-app-design.md).

## Review cadence

This document is reviewed at release planning time. Any candidate
whose status moves to *implementing* gets removed from here and
added to the active Now section of `ROADMAP.md`.
