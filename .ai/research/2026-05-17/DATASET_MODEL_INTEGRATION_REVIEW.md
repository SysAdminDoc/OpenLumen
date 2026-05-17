# Dataset / Model / Integration Review — 2026-05-17

This file is thin by design. OpenLumen is an offline, no-INTERNET Android
display-filter utility. It does not ingest datasets, run ML models, or
integrate with external services. The most useful framing is therefore:
**which adjacent datasets / models / integrations *did* this research
surface, and which (if any) could OpenLumen adopt without violating the
no-network philosophy?**

## Datasets surveyed

### Bundled offline city database

**Current state**: `OfflineCities` in [core-schedule/.../OfflineCities.kt](../../../core-schedule/src/main/java/com/openlumen/schedule/OfflineCities.kt)
bundles ~95 major cities with IANA timezones and four-decimal-place
coordinates. Used by the location-entry dialog for solar mode.

**Adjacent reference**: sunsetr (S170) ships a **~10,000-city interactive
picker** for the same use case on Wayland desktops.

**Action**: tracked as a Later candidate. The data exists in many forms
(GeoNames cities1000, OSM extracts), but expanding from ~95 → ~10,000
introduces APK-size and search-UX trade-offs that don't pay off for the
"my city isn't in the list" failure mode. The bundled coordinates picker
in the Schedule tab is the primary fallback; the offline list is for
convenience. **No roadmap shift**.

### CVD color-vision-deficiency LUTs

**Current state**: OpenLumen ships **CVD-remap presets** in
[core-engine/.../Presets.kt](../../../core-engine/src/main/java/com/openlumen/engine/Presets.kt)
(Protan / Deutan / Tritan) as coarse channel-shuffle approximations.

**Adjacent reference**: DaltonLens (S119, S120) is the canonical open
reference for CVD simulation math (Viénot 1999 in linear RGB). The
proper implementation is a precomputed 256-entry LUT per channel per
deficiency type.

**Action**: this is **C63** in rev 3, Next-tier. The DaltonLens math is
fully open; the implementation question is whether to bundle (APK-size
cost, reproducibility cost) or compute at runtime (CPU cost on slider
drags). Rev 4 keeps the candidate at Next.

### Sleep / circadian evidence base

This is the closest thing OpenLumen has to a "dataset" — a curated reading
list of papers and reviews. Updated in `docs/health-evidence.md`
(C60) and the corresponding ROADMAP sources S45-S47, S99-S102, plus this
session's additions S158-S162.

**Action**: refresh `docs/health-evidence.md`'s Sources section with the
five new entries (C126 in rev 3 is the placeholder).

## Models surveyed

### Tanner Helland Kelvin → RGB approximation

**Current state**: [core-engine/.../Kelvin.kt](../../../core-engine/src/main/java/com/openlumen/engine/Kelvin.kt)
implements the Tanner Helland approximation, range 1000-10000K. Used by the
Kelvin slider on Home (C65).

**Adjacent reference**: hyprsunset (S171) uses the same shape of math
(Kelvin → 3x3 CTM matrix) on the Wayland side. Worth a side-by-side
parity check against AOSP `mDisplayWhiteBalanceTintMatrix` for an audit
pass — but no model swap proposed.

**Action**: no change.

### NOAA solar-position algorithm

**Current state**: [core-schedule/.../SolarCalculator.kt](../../../core-schedule/src/main/java/com/openlumen/schedule/SolarCalculator.kt)
hand-rolled NOAA implementation. Returns sunrise / sunset / polar-day /
polar-night. The 2026-05-17 audit fixed polar-day vs polar-night
distinguishability and date-snapping.

**Adjacent reference**: every desktop screen-tint app uses the same
algorithm (Redshift, sunsetr, hyprsunset, wluma). No model alternative
to consider; the math is canonical.

**Action**: no change.

### Future: melanopic / EDI estimate (out of scope)

**Evidence**: S160 — Melanopic EDI is the right metric for sleep
regulation; combines intensity and spectrum.

**Action**: **explicitly out of scope** per
`docs/health-evidence.md`. Rev 3 chose to ship a *physical* "Blue channel
reduced by N%" indicator (C61, shipped) rather than a melanopic estimate
because the dose-response from display tinting is small and individual,
and any in-app number suggesting otherwise would be a health claim
OpenLumen can't support. Rev 4 keeps this stance; C127 in rev 3 adds a
"Perceived luminance reduction by N%" indicator alongside, which is the
correct second physical measurement.

## Integrations surveyed

### Existing integration surface

- **Tasker / Termux / ADB intent surface** (C70, C71, shipped — see
  [docs/automation.md](../../../docs/automation.md)). Stable API: every
  action string requires a schema-version bump to rename.
- **Quick Settings tile** + **1×1 toggle widget** + **4×1 preset
  widget** — first-party Android integration surfaces. Already shipped.

### Considered but out of scope

- **Philips Hue / IKEA TRÅDFRI** (S20, S87 — Twilight's commercial
  integration story). Requires `INTERNET` and is explicitly rejected in
  rev 3.
- **Home Assistant via REST / MQTT** (S184 — TvOverlay). Requires
  `INTERNET`; out of scope for main app. *Possibly* viable as a
  separate companion app for the TV form factor (C22 territory). Tracked
  as an idea, not a candidate.
- **Local Unix socket / IPC** (C72 — Later). Already declined: the
  existing intent surface covers every automation tool we've seen;
  adding a socket adds attack surface for marginal capability gain.
- **Shizuku** (C06 — Next): the one privileged-channel integration
  that's still on the table, because Shizuku is opt-in and the user
  explicitly trusts it. See `docs/overlay-and-per-app-design.md` for
  the design analysis.
- **Wear OS Data Layer** (C21 — Next): would require a *separate*
  companion APK (with Bluetooth-only Wearable Data Layer, NOT internet),
  not a flavor of the main app. The main `com.openlumen` package stays
  no-INTERNET.

### Eye Dropper system intent (Android 17+)

**Evidence**: S139, S202.

**Action**: new candidate **C131 — Eye Dropper integration on Android
17+**. Optional "sample color" button in the RGB picker fires
`android.intent.action.OPEN_EYE_DROPPER` and consumes the returned
color. Tier: **Later** (Android 17 device base is tiny in year one).

### FabricatedOverlay system API (Android 12+)

**Evidence**: S168.

**Action**: new candidate **C128 — FabricatedOverlay engine spike**.
Shizuku-bound `IOverlayManager` access. Tier: **Under Consideration**;
spike must confirm whether the overlay actually shifts the framebuffer
or merely re-themes app surfaces. Gated on C06 (Shizuku backend) spike
outcome.

## Benchmarks

OpenLumen does not benchmark. The relevant cost-of-engine measurements
are documented qualitatively in `docs/wake-and-vitals.md`:

- AlarmManager wakes the device at most twice per 24h per schedule mode.
- Light-sensor listener is event-driven, not polled.
- Prefs flow is `.conflate()`d so slider drags don't fan out into many
  `su` calls.
- Smooth transitions cap apply rate at ~1 Hz with a 200 ms floor and a
  600-step cap.

No proposed benchmark workstream. The roadmap's testing emphasis is on
**correctness** (C83 Compose screenshot, C84 connected permission, C91
SurfaceView regression) rather than throughput, which is appropriate for
a foreground-display utility.

## Why this file is thin

OpenLumen is a display utility, not a data app. The only "data" it ships
is a 95-city offline CSV and a handful of color-matrix presets. The only
"model" is the Tanner Helland Kelvin approximation and the NOAA solar
algorithm. The only "integrations" are first-party Android (intent
surface, tile, widgets, FGS) and a possibly-future Shizuku channel. The
philosophy ("offline-only, no Play Services, no network") deliberately
collapses this category.

The most useful artefact this category produces is the **reading list**
in `docs/health-evidence.md` and the **competitor-borrowable patterns**
captured in [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md).

## Rev 5 integration update

Two integration-adjacent findings were added in the third pass:

### Android developer verification

**Evidence**: S230-S232.

This is not an app runtime integration, but it is a platform-distribution
registration workflow. OpenLumen's package name and signing certificate
need to be registered through the Android developer verification path if
the maintainer wants F-Droid/direct-APK users on certified devices in
the first enforcement regions to avoid install blocks. Tracked as
**C141**.

### GitHub Actions supply-chain integration

**Evidence**: S242-S251.

OpenLumen's release process already depends on GitHub Actions for
debug/release builds, permission audits, SBOM generation, advisory scans,
hashing, and attestations. The Node 24 migration and current action
majors make this a live integration-maintenance item, not just a generic
dependency bump. Tracked as **C142**.

No new ML/data/model workstream was found. The rev 5 additions reinforce
the conclusion that OpenLumen's "integration" surface is mostly platform
and distribution infrastructure, not web APIs or external datasets.
