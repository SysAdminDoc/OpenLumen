# Overlay Safety & Per-App Behavior Design Notes

> Tied to roadmap candidates **C10** (overlay blocked-touch troubleshooting),
> **C11** (per-app pause/exclusions), **C12** (auto-pause on installer/
> permission dialogs), **C69** (per-app profiles), and **C90** (emergency
> unlock gesture).
>
> This is a design document, not a feature delivery. The candidates here
> share a hard architectural question — *how does OpenLumen know what app
> the user is currently looking at?* — and there are no good answers
> without a permission cost. This doc captures the analysis so a future
> contributor doesn't re-derive it.

## The shared problem

All five candidates need either:

1. **The foreground package name**, to:
   - Pause the filter when a per-app excluded app is foreground (C11).
   - Switch to a per-app preset when an app comes foreground (C69).
   - Pause the filter when the system installer or a permission dialog is
     foreground (C12).
2. **A way to dismiss the overlay without the screen being usable** (C90).

Each of these has a permission and privacy cost.

## Foreground app detection — the options

### Option A: `UsageStatsManager`

Requires `PACKAGE_USAGE_STATS`, which is a special-access permission. The
user must enable it manually in
**Settings → Apps → Special app access → Usage access**. Granting it gives
OpenLumen the ability to list every app the user has launched and when —
far more data than we need.

Pros:
- The official AOSP-blessed way.
- No accessibility surface.

Cons:
- Sensitive permission with a much wider read surface than we'd use.
- The grant flow is unfamiliar to most users; the dialog text emphasizes
  the broad data access.
- Returned data is rate-limited; querying at 1 Hz can produce stale
  results.
- Some OEMs (notably Xiaomi MIUI) have separate auto-revoke timers that
  silently kill the permission.

### Option B: `AccessibilityService`

Cons compound: accessibility services have full access to UI tree,
clipboard, keystrokes (depending on event filter), and persist after
update across app versions. Google Play scrutinizes accessibility
declarations heavily; an app that uses it for "convenience" not core
accessibility is often rejected.

We've documented this in `docs/threat-model.md` as
*not* OpenLumen's path.

Android 17's Advanced Protection Mode strengthens this decision:
non-accessibility-tool apps can lose Accessibility API access under AAPM,
so using AccessibilityService for per-app convenience would be both a
privacy posture regression and a brittle platform dependency.

### Option C: A separate Shizuku-backed flavor

Shizuku gives the app a privileged channel to the system without root
or ADB grants on every reboot. With Shizuku, `dumpsys activity recents`
or the IActivityManager binder give us the foreground task. This is the
cleanest technical path but adds a dependency on a separate app the user
must install and trust.

Tracked as roadmap candidate C06.

### Option D: AccessibilityService, but read-only and minimally scoped

`AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` with a strict package
filter would be cheap. The service would receive at most one event per
foreground change. The downside is the same as Option B at the policy
review level — Google doesn't distinguish "read-only" accessibility
services from "full" ones during review. Same review risk.

### Option E: Do nothing; users toggle manually

Today's posture. The QS tile, the 1x1 widget, the notification action,
the 4x1 preset widget, the favorites cycle, and the documented ADB
intent are all ways the user can pause the filter on demand. They cover
the C11/C12/C69 use cases without taking a privacy hit, at the cost of a
manual step.

## Decision (current)

**Option E for v0.5.0 → v0.6.0. Spike Option C (Shizuku) before v0.7.0.**

Reasoning:
- The product is otherwise a minimal-permission, no-network, offline
  utility. Adding `PACKAGE_USAGE_STATS` or AccessibilityService would
  change the trust posture in a way that's hard to undo.
- Manual controls already exist for the high-frequency cases (installing
  an app, taking a photo). The "Auto-pause" candidates are convenience,
  not safety.
- Shizuku is opt-in and the user explicitly installs it; the trust
  story is "you already trust Shizuku, so this feature exists for
  you."

## C10 — Overlay blocked-touch troubleshooting

This is the one candidate in this group we can address fully without
foreground-app detection. We've already covered the user-facing
troubleshooting in `docs/troubleshooting.md` under
"The overlay blocks me from tapping an install button."

What we know:

- Android 12+ rejects touches on system installer / permission dialog
  surfaces while an untrusted overlay is showing. This is documented
  platform behavior, not an OpenLumen bug.
- The system writes a logcat line like
  `InputDispatcher: Touch blocked by overlay window`. Users with
  `adb logcat` access can confirm.
- A user-facing detection mechanism would require either app-foreground
  detection (back to the permission problem) or interpreting input
  events from our window (which we deliberately do not receive — we set
  `FLAG_NOT_TOUCHABLE`).

What we do:
- Document the behavior (done in `docs/troubleshooting.md`).
- Surface a warning info card on the Driver tab when Overlay is selected
  (done).
- Mention the workaround paths in `docs/overlay-and-per-app-design.md`
  (this file).

## C90 — Emergency unlock gesture

The candidate description: "Add optional multi-tap/long-press corner
gesture plus notification/tile failsafe."

The notification + tile failsafe is already implemented (and the
documented ADB command in `docs/root-safety.md`). The unaddressed half
is the gesture — a screen-side input the user can perform without
reading the screen.

Options:
1. **Volume button sequence.** Requires a `MediaSession` or
   accessibility service. The MediaSession path is OK but it conflicts
   with media playback (the user's headphone-pause buttons would
   trigger our handler).
2. **Touch gesture on the overlay.** Requires `FLAG_NOT_TOUCHABLE = false`
   on the overlay, which would mean the overlay intercepts every tap
   anywhere on the screen — a much worse tapjacking risk than the
   current setup.
3. **A separate dedicated unlock overlay.** A small corner-pinned
   overlay that's touchable, attached only on screen-off or low-vision
   situations. Adds a second overlay window and the same touchability
   concerns.

Option 3 with a small triggerable corner is the cleanest if we ship it.
For now: **deferred.** The notification action + tile + ADB cover the
practical cases. The unlock gesture is a usability nicety with a real
implementation cost.

## C95 / C96 — AGP 9 & Hilt Compose artifact migration

Build-tooling migrations, not user-visible features.

### C95: AGP 9 migration

Status: shipped 2026-05-17.

OpenLumen now uses AGP 9.2.1, Gradle 9.4.1, Kotlin 2.3.21, KSP 2.3.8,
and AGP 9 built-in Kotlin support. The Android modules no longer apply
`org.jetbrains.kotlin.android`; Kotlin source compilation is provided by
AGP, while the app still applies the Compose and serialization Kotlin
plugins where needed.

The `targetSdk` remains 35 until the Android 17 validation gate (C103).
That keeps platform-behavior changes separate from build-tooling churn.

### C96: Hilt Compose artifact migration

AndroidX Hilt moved `hiltViewModel()` from `androidx.hilt:hilt-navigation-compose`
to `androidx.hilt:hilt-lifecycle-viewmodel-compose` and package
`androidx.hilt.lifecycle.viewmodel.compose`.

Status: shipped 2026-05-17. The app now uses Dagger/Hilt 2.59.2 and
`androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0`; the five Compose
screens that request `OpenLumenViewModel` import the new package.

## C28 — Direct Boot restore

Direct Boot (Android 7+) lets apps run in a limited mode after a reboot
but before the user unlocks the device. To support it:

1. Move a minimal subset of `Preferences` (just `enabled` and `engine`
   choice) into device-protected storage via
   `context.createDeviceProtectedStorageContext()`.
2. Register a `LOCKED_BOOT_COMPLETED` receiver that reads the
   device-protected subset.
3. Decide which engines work in Direct Boot — CDM and Overlay should;
   SF and KCAL rely on `su` which won't run until Magisk is unlocked.

Effort: moderate. Risk: medium (storage migration is the most data-loss-
adjacent operation we'd run). Practical user benefit: filter comes back
on a fresh boot before unlock, which matters for users who unlock once
per long stretch.

Plan: ship in v0.7.0 alongside the migration framework changes for the
device-protected DataStore.

## What's already shipped from this group

- **C09** — Overlay alpha cap explanation (v0.5.0).
- **C13** — Emergency off command + About-tab affordance (v0.5.0).
- **C99** — Event-driven ambient sampling — `ACTION_SCREEN_OFF` clears
  the cached lux reading so stale daytime values don't trigger the
  filter at dusk when the device is picked up. (v0.5.0)

## Process note

When any of C11/C12/C28/C69/C90 is ready to ship, **update this
document first.** It is the durable analysis; the code follows.
