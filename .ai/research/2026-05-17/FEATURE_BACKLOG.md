# Feature Backlog — 2026-05-17

Raw harvested ideas before prioritisation. Inherits the full candidate
inventory from `ROADMAP.md` rev 3 (C01-C127 preserved verbatim — see rev 3
text). This file:

1. Adds **new candidates C128-C131** drawn from this session's research.
2. Notes **tier-shift implications** for existing candidates without
   pre-committing the change (the actual tier shifts land in
   `PRIORITIZATION_MATRIX.md` and ROADMAP rev 4).
3. Captures **doc / process follow-ups** that aren't candidates per se
   (file renames, stale headers, MASVS-PRIVACY gap).

The full C01-C127 inventory is NOT duplicated here. See
[../../../ROADMAP.md#candidate-inventory](../../../ROADMAP.md) lines 578-654.

## New candidates from this research pass

### C128 — FabricatedOverlay engine spike

**Category**: engine / platform
**Tier**: Under Consideration (spike → Next on positive outcome)
**I/E/R**: 4 / 4 / 3
**Sources**: S168 (ColorBlendr), S163-S164 (Shizuku ecosystem)

**Description**: Spike a fifth `ColorEngine` implementation that uses the
Android 12+ `FabricatedOverlay` API via Shizuku to mutate
`com.android.systemui` / theme tokens at runtime. Survives reboot via
persistent overlays. Unlike SurfaceFlinger/KCAL, this doesn't need root,
just Shizuku.

**Architecture sketch**:

1. New `EngineKind.FABRICATED_OVERLAY` enum entry, rank between
   SurfaceFlinger (90) and KCAL (70) — call it 85. Requires Shizuku.
2. `FabricatedOverlayEngine` constructs a `FabricatedOverlay` instance,
   registers it via the `IOverlayManager` binder (Shizuku-bound), commits
   the active state.
3. Color matrix → overlay token mapping is non-trivial; we'd be writing to
   `android:color/*` resources that downstream apps already consume. The
   spike must verify this affects the displayed image, not just an
   uninstalled theme.

**Risk**:

- `FabricatedOverlay` is OEM-specific in practice; Samsung One UI may
  ignore overlays in ways stock Android doesn't.
- The token-flipping approach may not actually shift the framebuffer color
  the way the existing engines do — it might only re-theme app surfaces.
  The spike must determine whether this engine is a genuine 5th option or
  a "feature, not engine" entry.
- Shizuku dependency makes this a Next-tier candidate at best, gated on
  C06 (Shizuku backend spike) being feature-complete.

**Decision rule for the spike**: if `FabricatedOverlay` doesn't shift the
framebuffer or compositor blend the way the existing engines do, drop it.
It's an engine candidate, not a feature.

### C129 — OLED-aware gamma LUT clamp

**Category**: engine / image quality
**Tier**: Later
**I/E/R**: 3 / 4 / 3
**Sources**: S174 (cosmos), S100, S160

**Description**: A successor to the shipped scalar AMOLED clamp (C66).
Instead of snapping any per-channel scalar below `0.02` to zero, build a
per-channel 256-entry LUT that scales the bottom of the gamma curve to
prefer fully-off pixels on OLED panels. Preserves more dynamic range in
the mid-tones while still hitting the OLED-true-black benefit.

**Architecture sketch**:

1. `MatrixPreview.kt` gains an `OledLutClamp` precomputation: 256-entry
   per-channel LUT generated lazily and cached.
2. New `Preferences.amoledLutClamp: Boolean` flag (separate from the
   existing scalar `amoledBlackClamp` so users can compare; or wire one
   into the other after a UX pass).
3. Applies only on engines that consume per-channel scalars (CDM /
   Overlay); SurfaceFlinger's 4x5 matrix path doesn't fit a LUT directly,
   so we either skip or downgrade gracefully.

**Risk**:

- Adds CPU cost on slider drags (LUT regenerates on gamma change).
- Has the same "bundled binary vs runtime compute" tradeoff as C63 CVD
  LUT correction.

### C130 — AAPM driver-report surface

**Category**: docs / transparency / security
**Tier**: Now
**I/E/R**: 3 / 1 / 1
**Sources**: S134, S135, S136

**Description**: Detect `AdvancedProtectionManager` state on Android 17+
and surface it in the in-app driver report. Currently the report captures
device build, granted permissions, and every engine's probe result, but
not AAPM state. AAPM users who try a11y-based competitor features and find
they're auto-revoked will see *why* in OpenLumen's report — even though we
don't use a11y anyway.

**Architecture sketch**:

1. `DriverReport.kt` gains an AAPM block (Android 17 check + reflection-
   gated `getSystemService("advanced_protection")`).
2. Report shows `AAPM: on / off / unknown`. Driver tab info card explains
   "AAPM auto-revokes Accessibility-based features; OpenLumen does not
   use Accessibility, so AAPM has no effect on OpenLumen."

**Why now**: rev 3 elevated C79/C80 to Rejected because of AAPM. The
docs/transparency follow-up belongs in the same release that surfaces the
rejection rationale.

### C131 — Eye Dropper integration on Android 17+

**Category**: UX / feature
**Tier**: Later
**I/E/R**: 2 / 2 / 1
**Sources**: S129, S139

**Description**: Custom-RGB picker on Home gains an optional "sample
color" button that, on Android 17+, fires
`android.intent.action.OPEN_EYE_DROPPER` and consumes the returned color.
Lets users grab a target color from anywhere on screen without screen-
capture permission.

**Architecture sketch**:

1. Detect Android 17 via `Build.VERSION.SDK_INT >= 35` (or the actual API
   level once stable lands — depends on the final Android 17 API number;
   likely 36).
2. Add an `Eyedropper` button in the RGB picker. On pre-17 devices, the
   button is hidden.
3. Map returned color to RGB sliders.

**Why Later**: Android 17 device base will be tiny for the first year;
non-essential.

## Tier-shift implications from new evidence (proposed for rev 4)

### C123 — Glance widget rewrite — UC → Next

**Rev 3**: Under Consideration, reason "Glance is alpha."
**New evidence**: S193 — Glance 1.0.0 went stable, 1.1.0 shipped 2024-06-12.
**Proposed action**: move to Next; remove the stability blocker. Pair with
C20 / C19 rewrite once C95 (AGP 9) lands.

### C101 — Compose Preview Screenshot Testing CI — risk 1 → 2

**Rev 3**: Now, I/E/R 4/2/1.
**New evidence**: S148-S149 — tool is still 0.0.1-alphaXX as of Apr 2026.
**Proposed action**: bump risk 1 → 2. Document pin policy: track alpha
version per release, plan to switch to stable when it lands. Roborazzi
(S150) and Paparazzi (S152) remain credible alternatives for JVM
screenshot tests.

### C120 — VCS info determinism — effort already 1

S156 provides a direct disabling recipe. No effort change, but rev 4
should link the F-Droid forum recipe directly in `docs/reproducible-
build.md`.

### Risk reassessment: C111 BAL hardening

Rev 3 placement: Now, I/E/R 3/1/1.
New evidence: S128, S137 confirm the deprecation and the recommended
replacement (`MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`). Rev 3
audit was correct; no change.

## Doc / process follow-ups (not candidates per se)

These are corrective actions to scope, not new features. Folded into
`ROADMAP.md` rev 4's "What changed in rev 4" section.

1. **Rename `docs/api-36-readiness.md` → `docs/android-17-readiness.md`
   and re-title the body** to match rev 3's C82 → C103 expansion.
2. **Bump `docs/research-watchlist.md` "Last review" header to 2026-05-17**;
   credit rev 3 as the review.
3. **Update `docs/health-evidence.md` Sources section** to add S99-S102 +
   S158-S162. The body already disclaims medical claims correctly; only
   the source list needs the refresh. (This is C126's deliverable.)
4. **Extend `docs/threat-model.md` with a MASVS-PRIVACY section** to
   match MASVS v2.1.0 (S192). The data inventory and permission inventory
   sections already cover the substance; the doc just needs the
   categorical header.
5. **Fold the 2026-05-17 audit hardening list into `CHANGELOG.md
   [Unreleased]`** (or cut a v0.5.1 hardening release). Rev 3's
   "Hardening" subsection has the list verbatim.

## Negative candidates (logged, not adopted)

These came up in the research and were considered, then declined.

- **Weather-aware tinting (S175 Solace)**: requires network or weather
  sensor; out of scope for offline-first.
- **REST API + MQTT control surface (S184 TvOverlay)**: requires network.
  *Possibly* viable as a separate companion package for TV form factor,
  but not in the main app. Tracked as an idea under C22 (Android TV
  flavor), not a candidate.
- **Per-website Night Shift disable (S176 Shifty)**: Android analog is
  per-app exclusion, which is already C11 / C69. Skipped as duplicate.
- **OPEN_EYE_DROPPER intent for the entire RGB workflow** (S139): the
  intent returns a single color, not a continuous stream. Not a
  replacement for the existing slider/picker UI, only a complement.
  Captured as C131 instead.
- **Adopting Roborazzi or Paparazzi as the primary screenshot framework**
  instead of Compose Preview Screenshot Testing: keep Compose Preview as
  primary (S148) because it's IDE-native; Roborazzi/Paparazzi can be
  belt-and-braces if the alpha tool burns us. Already C122 in rev 3.

## Backlog discipline notes

- **Don't add candidates because they're plausible.** Every candidate above
  cites ≥2 sources and includes an architecture sketch. Anything I
  couldn't sketch went to "negative candidates" instead of being filed.
- **Don't tier-shift existing candidates without new evidence.** The
  proposed shifts above each cite a specific new source.
- **Don't re-name existing candidates.** C82 / C103 are an example: rev 3
  expanded scope ("API 36" → "Android 17") without renumbering. Future
  passes should keep doing this so commit-message traceability stays
  intact.

## Third-pass harvested candidates (rev 5)

### C141 — Android Developer Console package registration

**Tier**: Now
**Category**: distribution / trust
**Sources**: S230, S231, S232

**Idea**: Register `com.openlumen` and the release signing certificate
through the correct Android developer verification path (Play Console if
the maintainer chooses Play, Android Developer Console if OpenLumen stays
outside Play).

**Why it fits**: OpenLumen is explicitly F-Droid-first and direct-APK
friendly. Android's 2026 verification program applies to certified
Android devices regardless of app source in initial enforcement regions,
so this is not a Play-only concern.

**Implementation sketch**:

1. Decide account ownership (individual vs organization) and record the
   durable owner in maintainer-only release notes, not in Git.
2. Verify identity in Play Console or Android Developer Console.
3. Register `com.openlumen` and prove signing-key ownership with the
   release APK certificate.
4. Add a release-checklist reminder to verify package registration
   before September 2026 regional enforcement.

### C142 — CI action major rotation and SHA-pinning policy

**Status**: Shipped 2026-05-17 after rev 5.
**Tier**: Now
**Category**: supply chain / CI
**Sources**: S242-S251, S258-S265

**Idea**: Rotate GitHub Actions to Node-24-capable current majors and
make an explicit policy decision on major tags vs full SHA pinning with
version comments.

**Why it fits**: OpenLumen's release story depends on CI attestations,
SBOM generation, and permissions audits. GitHub's Node 24 default starts
2026-06-02, and the repo currently runs older majors across CI, release,
and SBOM workflows.

**Implementation sketch**:

1. Update `checkout@v4 -> v6`, `setup-java@v4 -> v5`,
   `setup-gradle@v4 -> v6`, `attest-build-provenance@v2 -> actions/attest@v4`
   or `attest-build-provenance@v4`, `scan-action@v6 -> v7`.
2. Evaluate `upload-artifact@v7` only if non-zipped SBOM artifacts are
   desired; otherwise keep standard zipped behavior.
3. Add a short policy note to `docs/sbom-and-advisories.md`: either
   continue major tags for Dependabot ergonomics or adopt full SHA pins
   with version comments and a rotation checklist.
4. Run `ci.yml`, `release.yml`, and `sbom.yml` once on a branch before
   cutting v0.5.0/v0.6.0.

### C143 — Android 17 memory/resizability smoke expansion

**Status**: Shipped 2026-05-17 after rev 5.
**Tier**: Now
**Category**: mobile / compatibility
**Sources**: S233-S236, S266

**Idea**: Extend C103's Android 17 readiness work to cover the Beta 4
all-app memory limiter and target-37 large-screen resizability behavior.

**Why it fits**: The app is small, but OpenLumen runs a persistent
foreground service and overlay; memory leaks or large-screen Compose
state loss would be release-quality issues. The sw600dp behavior matters
for tablets, foldables, desktop windowing, and Android TV exploration.

**Implementation sketch**:

1. Add `ApplicationExitInfo` / `MemoryLimiter` review to
   `docs/android-17-readiness.md`.
2. Add one tablet/foldable/desktop-windowing row to `docs/device-matrix.md`.
3. Confirm no manifest orientation/resizability assumptions are being
   relied on.
4. Pair with C101 screenshot tests once the screenshot framework lands.

### C144 — AndroidX stable baseline refresh batch

**Tier**: Next
**Category**: upgrade strategy
**Sources**: S237-S241, S252, S253

**Idea**: After the AGP 9 migration, refresh stable AndroidX floors in
one controlled batch: core/activity/lifecycle/navigation/DataStore and
Compose Material 3.

**Why it fits**: The repo intentionally defers broad dependency churn
until the release foundation is stable. The current AndroidX stable
matrix is now far ahead of OpenLumen's floor, and DataStore 1.2.1 is the
stable base for Direct Boot restore.

**Implementation sketch**:

1. Land C95 first: AGP 9.2.0 + Gradle 9.4.1 + compile SDK/toolchain
   validation.
2. Land C96/C124 with the Hilt artifact rename and Hilt 2.59.2.
3. Batch stable AndroidX updates; keep alpha tracks out unless a
   candidate explicitly needs them.
4. Re-run unit tests, lint, permission audit, profile export/import, and
   service smoke.

## Roadmap execution update after rev 5

- **C132-C136 shipped 2026-05-17**. The high/medium-severity pass-2
  service and engine correctness findings are no longer raw backlog:
  ramp scheduling is atomic, filter-off clears cancel active ramps,
  CDM partial reflection caches are invalidated, overlay view mutation is
  locked, and SF/KCAL stale driver caches are invalidated on failed
  writes. Source: S00c.
- **C141 remains outstanding** because it requires maintainer developer
  account / identity work outside Git.
- **C137 is the next small code-level candidate** once maintainer-only
  C141 and release-packaging gates are accounted for; C138 shipped after
  this backlog note was first written.
- **C130 shipped 2026-05-17**. Driver reports now include a reflection-
  gated Android 17 Advanced Protection section and declare
  `QUERY_ADVANCED_PROTECTION_MODE`. Sources: S134, S267.
- **C120 shipped 2026-05-17**. Release builds disable packaged AGP
  VCS-info metadata and the reproducible-build doc now explains the
  F-Droid comparison risk plus the external provenance path. Sources:
  S112, S156, S268.
- **C111 shipped 2026-05-17**. Source audit found no
  `IntentSender` / `ActivityOptions` BAL call sites, so no
  `_ALLOW_IF_VISIBLE` migration is needed until a future feature adds
  one. Source: S00d.
- **C116 shipped 2026-05-17**. Troubleshooting now documents that a
  filter paused before reboot remains paused because `BootReceiver`
  restores only when persisted `enabled = true`.
- **C106 shipped 2026-05-17 as evidence slots**. Wake/vitals now has
  Android 14/15/16/17 pending boot-restore rows and the device matrix has
  a required boot-restore note convention. Real pass/fail data remains
  C01.
- **C138 shipped 2026-05-17**. Profile imports are capped at raw
  `InputStream` bytes before UTF-8 decoding, with exact-limit and
  max-plus-one unit coverage. Source: S00e.
