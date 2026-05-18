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

## Final icon and adaptive launcher — C35 (shipped)

**Status**: shipped 2026-05-17. The final minimal crescent direction from
`branding/logo-prompts.md` is now represented by:

- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `branding/openlumen-icon.svg`
- `fastlane/metadata/android/en-US/images/icon.png`

The app relies on adaptive-icon XML/vector resources because `minSdk` is
26. The remaining release-artwork blocker is C36 store screenshots.

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

## Compose screenshot tests — C83 (partially unblocked)

**Status**: C101 and C122 shipped the CI/runtime foundation on
2026-05-17. Compose Preview Screenshot Testing `0.0.1-alpha14` covers an
initial textless theme-token `@PreviewTest` with checked-in debug
references; Roborazzi 1.60.0 covers the same theme-token surface through
Robolectric with checked-in PNG baselines and `:app:verifyRoborazziDebug`
in CI. C83 is now the broader screen-coverage expansion, not the
framework bootstrap.

**Plan**:

1. Add preview fixtures for each tab (Home / Schedule / Presets /
   Driver / About) with fixed, representative `Preferences` snapshots.
2. Keep fixtures text-light or locale-stable unless the test is
   explicitly validating copy/layout.
3. Add dark/light variants for the public app chrome and tablet-ish
   widths once the first tab fixtures are stable.
4. Use Roborazzi for JVM goldens that benefit from Robolectric device
   qualifiers, and keep Compose Preview Screenshot Testing for static
   preview coverage.

**Why deferred**: the CI gate now exists, but full tab coverage needs a
deliberate fixture design so goldens do not churn on localization,
fonts, or unrelated content changes.

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

## Melanopic / circadian estimate UI — C61 (shipped as blue-channel reduction)

Shipped as a "Blue channel reduced by N%" indicator on the Home tab,
computed by `MatrixPreview.blueSuppression(prefs)`. This is the
narrow, defensible form of the candidate — a *physical*
measurement of the output (1 - effective blue scalar), not a
melanopic / circadian / sleep claim. The richer melanopic-DER
estimate the candidate originally described is still deferred per
the rationale in `docs/health-evidence.md`: the dose-response from
display tinting is small and highly individual, and any in-app
number that suggests otherwise would be a health claim we can't
support.

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

## AMOLED black clamp — C66 (shipped in v0.5.0, scalar-level form)

Shipped as an opt-in `Preferences.amoledBlackClamp` flag and a
`LumenMatrix.amoledClamp` field. When the flag is set,
`LumenMatrix.scaledRgb()` snaps any channel scalar below
`AMOLED_CLAMP_THRESHOLD = 0.02` to zero. On OLED panels this turns the
matching subpixels fully off in the warm/dim end of the tinting
range; on LCD panels it's a no-op (the backlight stays lit regardless).

The shipped form is the scalar-level clamp, not the per-pixel
framebuffer transform the candidate originally described. Per-pixel
true-black would require shader access (SurfaceFlinger does not
expose pixel-level transforms through its color-matrix path) and is
explicitly out of scope. The scalar clamp delivers the practical OLED
savings without the kernel-level surface dependency.

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

## Material 3 1.5.0 / Expressive components — C110 (Later)

**Status**: Later-tier (review-only, not committing).

**Context**: Compose Material 3 `material3-expressive` shipped its
first stable building blocks alongside Material 3 1.4.0 — but the
expressive overload set itself remains alpha-only per the rev-5
source register (S227). Rev 5 explicitly held the line:
"Do NOT adopt `material3-expressive` yet (still alpha)."

**Review (this revision)**: the components that would be useful
for OpenLumen are:

- `FloatingToolbar` — could host the Home tab's Filter toggle as
  a floating control. Risk: floating UI competes with the existing
  toggle Card; would need a UX decision.
- `ButtonGroup` / connected button rows — could replace the
  schedule-mode RadioButton list with a more compact selector.
  Risk: introduces a new UX pattern when the existing list works.
- `WideNavigationRail` — only relevant if we ever ship a tablet
  layout (out of scope today).
- `SplitButton` — could combine the Driver tab's "Copy report" +
  "Share report" buttons. Risk: low; this is the cleanest fit.
- `LoadingIndicator` (the contained variant) — replaces the
  generic spinner pattern. We don't have any loading spinners
  today (every screen reads from `StateFlow` and renders
  synchronously), so no fit.

**Decision (2026-05-17)**: continue to hold. None of the
expressive components are stable enough to justify the API
churn risk before v0.5.0 ships. Re-review when
`material3-expressive` reaches `1.5.0-stable` or when the
candidate list above grows past two useful components.

**Implementation sketch** (when we lift the hold):

1. Add `material3-expressive` to `libs.versions.toml` against
   whichever stable release lands first.
2. Apply behind a `BuildConfig.USE_M3_EXPRESSIVE` flag for one
   release so we can roll back if the visual change is
   unwelcome.
3. Migrate `SplitButton` first (lowest risk, clearest win) on
   the Driver tab; defer `FloatingToolbar` / `ButtonGroup`.

**Why deferred**: alpha API risk vs. minimal user-visible win
for a single-developer project that prizes API stability over
aesthetic refresh cadence.

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
