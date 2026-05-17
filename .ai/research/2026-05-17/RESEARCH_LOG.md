# Research Log — 2026-05-17

This pass extends `ROADMAP.md` rev 3 (also dated 2026-05-17), preserving its
candidate IDs (C01-C127) and source IDs (S00-S125) and adding S126-S202 from
two parallel research agents. The goal was not to re-do rev 3 — rev 3 is
authoritative — but to broaden the evidence base, find unknown competitors,
and identify any roadmap gaps rev 3 missed.

## Phases

| Phase | Inputs | Outputs |
|---|---|---|
| 1. Local repo reconnaissance | `git status`, `git log --oneline -30`, `git diff --stat HEAD`, `gradle/libs.versions.toml`, manifests, every doc under `docs/`, every Kotlin source file | `STATE_OF_REPO.md` |
| 2. Memory inventory | Search for AGENTS.md / .cursorrules / GEMINI.md / .claude/** / .github/copilot-instructions.md / CLAUDE.md, plus all committed docs | `MEMORY_CONSOLIDATION.md` |
| 3. External research (parallel) | Two background agents — one for Android-platform-evolution sources, one for the wider competitor sweep | Source bundles folded into `SOURCE_REGISTER.md` |
| 4. Cross-checking | Each new source triaged against rev 3 — does it strengthen an existing claim, change a tier, or imply a new candidate? | `COMPETITOR_MATRIX.md`, `FEATURE_BACKLOG.md`, `PRIORITIZATION_MATRIX.md`, draft additions for ROADMAP.md rev 4 |
| 5. Self-audit | Are all required artifacts present? Does every Now/Next/Later/UC/Rejected item still cite a source? Are new candidates traceable? | `CHANGESET_SUMMARY.md`, rev 4 audit notes |

## Background agents launched

Two `general-purpose` agents ran in parallel (one prompt each):

### Agent 1 — Android 17 + FGS + AAPM source harvest

Brief: collect primary-source URLs (developer.android.com, GitHub, F-Droid,
OWASP, NIH) for 14 topics (Android 17 stable + Beta 4, AGP 9/10, Hilt, DataStore,
Compose screenshot testing, F-Droid policy, sleep/circadian, Shizuku, Red Moon,
Twilight, active OSS entrants, PWM signals, OWASP MASVS v2 / MASTG, Glance
stable).

Returned 58 URLs (S126-S183 in agent's numbering; renumbered to S126-S165 +
S185-S202 in `SOURCE_REGISTER.md` to avoid collision with Agent 2).

### Agent 2 — Competitor & adjacent project sweep

Brief: find Android OSS display-tint apps OpenLumen doesn't already know
about, plus cross-platform inspirations (Wayland / macOS / Windows 2024-2026),
Android Shizuku ecosystem exemplars for foreground-task detection, Wear OS
tint apps, Android TV tint apps. Skip the 17 already-known competitors.

Returned 19 substantive entries + 1 negative finding (Wear OS open category)
as S166-S184.

## Saturation tests

Searches that returned no new useful signal (saturation evidence):

- **Wear OS tint/dim apps**: Agent 2 explicitly returned a negative finding —
  no qualifying open-source Wear OS tint app pushed after 2024-12-31. This is
  an opportunity, not a gap.
- **Reddit r/PWM_Sensitive direct content**: Agent 1 could not fetch the
  subreddit; substituted three Android Central pieces. Treat r/PWM_Sensitive
  as un-mined for future passes if subreddit fetches become possible.
- **Twilight official changelog**: only APKPure has 2026 version data
  surfaced; the official twilight.urbandroid.org/doc pages did not return
  changelog content. Logged as a known limitation.
- **Android 17 Night Light API additions**: there are none — S138 (AOSP
  Night Light implementation guide) was the explicit negative confirmation.
  This means our `ColorDisplayManagerEngine` reflection ladder does NOT
  need an Android-17-specific case beyond what rev 3 already plans.
- **F-Droid reproducible-build per-app indicator** (S155): we now know the
  indicator exists but does not impose any per-app action items beyond what
  C37 / C45 / C120 already cover.
- **New 2026 OSS entrants in the niche**: thin — Agent 2 returned 4
  substantive new entrants (EcoDimmer, Grayscaler, ColorBlendr, Adaptive
  Theme). Agent 1 separately re-confirmed the major dormant references
  (corphish/NightLight, farmerbb/Night-Light, shades) are still
  distributed but not actively developed. The category is consolidating
  toward a small number of mature entries plus a long tail of
  abandoned/demo projects — exactly the gap OpenLumen is positioning to
  fill.

Saturation confidence is high for: Android 17 platform changes, AGP/Hilt/
DataStore release notes, F-Droid policy, the OSS-Android-tint competitor
set, Wayland-ecosystem inspirations, and OWASP MASVS/MASTG status.

Saturation confidence is medium for: PWM community signal (subreddit was
unreachable), Wear OS color/tint apps (negative finding may simply mean no
one's tried).

Saturation confidence is low (would benefit from another pass) for:
- Quantitative usage data on Red Moon installs in 2026 (no fresh F-Droid
  metrics surfaced).
- Whether any of the four new Android entrants have already been picked up
  by F-Droid distribution.
- Exact behavior of Android 17 BAL `_ALLOW_IF_VISIBLE` against our
  PendingIntent / tile / widget paths (needs device testing, not desk
  research).

## Triage of new sources against rev 3

For each meaningful new finding I asked: does this *strengthen* an existing
rev 3 claim, *change a tier*, or *imply a new candidate*?

### Strengthens existing rev 3 claims (no roadmap change)

- **S126-S132, S135-S138**: every Android 17 / FGS / AAPM citation in rev 3
  is now backed by primary `developer.android.com` URLs in addition to
  the secondary sources rev 3 was using (S88-S90 were Android Authority /
  Hacker News / Help Net Security).
- **S140-S143**: AGP 9.0 / 9.1 / 9.2 release notes confirmed and the
  AGP 10 opt-out-removal timeline is now sourced (S143).
- **S144-S145**: Hilt artifact rename confirmed against the official
  AndroidX Hilt releases page.
- **S146-S147**: DataStore Direct Boot APIs confirmed against the
  official AndroidX DataStore releases page.
- **S148-S153**: Compose Preview Screenshot Testing alpha status and
  Roborazzi / Paparazzi options re-sourced.
- **S154-S157**: F-Droid RB + 70% translation policy primary-sourced.
- **S158-S162**: 2025/2026 sleep-evidence base broadened — five new
  primary sources, all in line with rev 3's stance.
- **S163-S165**: Shizuku ecosystem refreshed (v13.6.0 line, awesome-list
  live).
- **S188-S192**: OWASP MASTG / MASVS now sourced directly. **Note**:
  MASVS v2.1.0 added a MASVS-PRIVACY category; `docs/threat-model.md`
  currently covers MASVS-STORAGE / CRYPTO / AUTH / NETWORK / PLATFORM /
  CODE / RESILIENCE but not PRIVACY by name. Folded into rev 4 as a
  documentation refresh task.
- **S193-S194**: Glance is stable (1.0+) and shipped 1.1.0 in mid-2024.
  This **changes the C123 risk** from "Glance is alpha" to "Glance is
  stable, the only blocker is dep-surface review."

### Implies new candidates (roadmap additions in rev 4)

- **C128 — FabricatedOverlay engine spike** (sources S168). Android 12+
  Shizuku-only privileged path that survives reboot via runtime overlays.
  Could become OpenLumen's fifth engine for the "Shizuku-but-not-root"
  use case. **Tier**: Next / Under Consideration; spike-first.
- **C129 — OLED-aware gamma LUT clamp** (sources S174, S100). Beyond
  the scalar AMOLED clamp (C66, shipped), scale gamma to keep `(0,0,0)`
  truly off across the whole low-dim range. **Tier**: Later (CPU cost /
  shader access constraints, similar to C63 CVD LUT).
- **C130 — AAPM driver-report surface** (sources S134, S135). Detect
  `AdvancedProtectionManager` state and include it in the driver report
  so AAPM users understand why certain competitor features (a11y-based
  per-app) aren't possible on their device. **Tier**: Now (effort 1,
  high-value transparency).
- **C131 — Eye Dropper integration on Android 17+** (source S139, S202).
  Optional: custom-RGB picker on Home calls
  `android.intent.action.OPEN_EYE_DROPPER` on supported devices to let
  the user sample a color from anywhere on screen. **Tier**: Later
  (depends on Android 17 device base reaching minimum coverage).

### Changes a tier or risk score

- **C123 (Glance widget rewrite)**: rev 3 placed this Under Consideration
  with the reason "Glance is alpha." S193 confirms Glance is stable
  (1.0+ since mid-2024, 1.1.0 since 2024-06-12). **Rev 4 tier change**:
  Under Consideration → Next; remove the stability blocker from the
  rationale.
- **C101 (Compose Preview Screenshot Testing in CI)**: S148 / S149 confirm
  the tool is still alpha (0.0.1-alphaXX). **Rev 4 risk score**: bump
  risk from 1 → 2 because alpha tooling can rev breaking changes. Keep
  the Now placement; document the version pin policy.
- **C120 (VCS info determinism)**: S156 provides a direct disabling
  recipe via the F-Droid forum thread. **Rev 4 effort score**: drop
  effort 1 → 1 (already minimal) but link the recipe directly.
- **C28 / C102 (Direct Boot restore)**: S146 reconfirms the DataStore
  Direct Boot APIs are real and at minSdk 24. Rev 3 already dropped
  effort 4→3 because of this; rev 4 simply re-affirms.

### No-action signals

- **S139 (Eye Dropper)**: filed as C131 (Later) — not pressing.
- **S175 Solace (weather-aware tinting)**: out of scope for OpenLumen
  offline-first; no candidate.
- **S178 Nocturnal archived**: cautionary tale logged; no candidate but
  reinforces our defensive reflection ladder in `ColorDisplayManagerEngine`.
- **S185-S187 (PWM AndroidCentral pieces)**: reinforces existing C114
  / Under-Consideration PWM-sensitive preset; no tier change.

## Conflicts surfaced for resolution by future passes

1. **`docs/api-36-readiness.md` is titled and framed for "Android 16 /
   API 36"** but rev 3 of `ROADMAP.md` already promoted C82 / C103 to
   "Android 17 readiness." `MEMORY_CONSOLIDATION.md` flagged this. ROADMAP
   rev 4 adds the doc-rename as an explicit follow-up.

2. **`CLAUDE.md` "Status" section is stale** (says v0.2.0 not yet smoke-
   tested). The CLAUDE.md fix is part of this session's deliverable — we
   're appending a "Canonical project context" pointer to PROJECT_CONTEXT.md
   without touching the version-history / status text. The maintainer
   should decide whether to trim the version-history section in CLAUDE.md
   (it's a local-only file).

3. **`docs/research-watchlist.md` "Last review: 2026-05-16"**. ROADMAP rev
   3 was effectively a watchlist review; the header should advance to
   2026-05-17. Folded into rev 4 follow-ups.

4. **`docs/health-evidence.md` sources predate S99-S102 / S158-S162**.
   C126 in rev 3 is the placeholder. Rev 4 keeps C126 Now and links the
   new sources.

5. **`docs/threat-model.md` does not have a MASVS-PRIVACY section**
   (S192). Rev 4 adds a follow-up to extend the doc.

## Methodology notes for future passes

- **Don't re-do rev 3 — extend it.** Rev 3 was authored hours before this
  session; it walked the same watchlist this session would have walked.
  The right move was to *broaden* the evidence base and *fill gaps*, not
  rewrite from scratch.
- **Parallel agents save context.** Two background `general-purpose`
  agents (one Android-platform, one competitor-sweep) ran concurrently
  while I wrote the local-evidence files. Total wall time was ~5 minutes
  for both agents; main-context cost was the prompts plus the digested
  summaries.
- **Renumber sources defensively.** Two agents both picked S166-S184 ranges
  organically. I renumbered Agent 1's overflow to S185-S202 in the source
  register to keep the IDs collision-free. For future passes, give each
  agent an explicit non-overlapping range in its prompt.
- **Saturation is per-topic, not per-pass.** Wear OS tint apps saturate at
  zero; Android 17 platform docs saturate fast with primary sources;
  Reddit / community PWM signal didn't saturate (subreddit unreachable).
  Track saturation per topic.

## Files produced this session

Under `.ai/research/2026-05-17/`:

- `STATE_OF_REPO.md`
- `MEMORY_CONSOLIDATION.md`
- `SOURCE_REGISTER.md`
- `RESEARCH_LOG.md` (this file)
- `COMPETITOR_MATRIX.md`
- `FEATURE_BACKLOG.md`
- `PRIORITIZATION_MATRIX.md`
- `SECURITY_AND_DEPENDENCY_REVIEW.md`
- `DATASET_MODEL_INTEGRATION_REVIEW.md`
- `CHANGESET_SUMMARY.md`

At repository root:

- `PROJECT_CONTEXT.md` (new, canonical project memory)
- `ROADMAP.md` (rev 3 → rev 4 update; preserves all rev 3 content)
- `CLAUDE.md` (single pointer added near the top; rest preserved)

## Third-pass research update (rev 5)

This pass resumed the same 2026-05-17 notebook after rev 4.1. I did
not relaunch background agents; the live queries were narrow enough to
run directly.

### Queries / source classes covered

- Android developer verification:
  - `site:developer.android.com developer verification Android apps September 2026`
  - `site:developer.android.com developer verification guides FAQ`
- Android 17 behavior gaps:
  - `site:developer.android.com/about/versions/17 behavior changes all apps MemoryLimiter`
  - `site:developer.android.com/about/versions/17 resizability orientation sw600`
- Build / dependency refresh:
  - `site:developer.android.com Android Gradle Plugin 9.2.0 release notes`
  - `site:gradle.org Gradle 9.4.1 release notes`
  - `site:developer.android.com/jetpack/androidx/versions lifecycle navigation activity core May 2026`
  - `github google dagger releases 2.59.2 hilt AGP 9`
- CI / supply chain:
  - `GitHub Actions Node 20 deprecation June 2 2026 official changelog`
  - `GitHub Actions security hardening pin actions full-length commit SHA`
  - `actions/checkout v6`, `actions/setup-java v5`, `gradle/actions setup-gradle v6`
  - `actions/attest-build-provenance v4`, `anchore/scan-action v7`
- Competitor saturation:
  - `android screen dimmer open source Shizuku 2026 GitHub`
  - `"screen dimmer" "F-Droid" Android 2026 overlay`
  - `"OpenLumen" "F-Droid" "com.openlumen"`
- Verification bootstrap:
  - `Android SDK command line tools Windows latest download commandlinetools-win`

### Saturation notes

- **Developer verification** was the only truly new strategic source
  class. It was absent from rev 4.1 and changes distribution planning
  because OpenLumen is explicitly F-Droid/direct-APK oriented.
- **Android 17 behavior** was not exhausted in rev 4.1. AAPM / FGS /
  BAL had strong coverage, but memory-limiter and sw600dp resizability
  checks were missing from the test plan.
- **CI supply chain** now has a time-bound trigger: GitHub's Node 24
  default on 2026-06-02. The repo's existing "major tag" policy is
  documented, but GitHub's secure-use docs still prefer full SHA pinning
  for immutable references, so this became a concrete policy decision
  rather than a generic security admonition.
- **Competitors** did not yield a new direct framebuffer/root Android
  peer. DimTV's current README is fresher than rev 4.1's Android TV
  note, but it remains overlay/system-settings oriented rather than an
  OpenLumen-equivalent multi-engine app.

### Rev 5 outcomes

- Added source IDs **S230-S257** to `SOURCE_REGISTER.md`.
- Added roadmap candidates **C141-C144**:
  - C141 Android Developer Console package registration.
  - C142 CI action major rotation and SHA-pinning policy.
  - C143 Android 17 memory/resizability smoke expansion.
  - C144 AndroidX stable baseline refresh batch.
- Sharpened existing C95/C96/C124 dependency strategy: Hilt 2.59.2 is
  current, but the Hilt Gradle plugin now requires AGP 9; therefore the
  Hilt bump should travel with the AGP 9 train, not as an isolated
  pre-AGP-9 dependency bump.
- Installed user-local JDK/Android SDK prerequisites and completed unit
  tests, `assembleDebug`, and `lintDebug`; exact commands and outcomes are
  recorded in `STATE_OF_REPO.md` and `CHANGESET_SUMMARY.md`.

## Implementation update (C142)

After the rev 5 checkpoint, the next implementable Now-tier roadmap item
was **C142**. C141 requires maintainer account / identity work outside
Git, while C143's documentation pieces were already added in rev 5.

Implementation evidence gathered:

- `git ls-remote --tags` against `actions/checkout`,
  `actions/setup-java`, `gradle/actions`, `actions/upload-artifact`,
  `actions/attest`, `actions/attest-build-provenance`,
  `anchore/scan-action`, and `anchore/sbom-action`.
- Raw upstream action metadata / READMEs for `setup-java@v5`,
  `upload-artifact@v7`, `actions/attest@v4`,
  `actions/attest-build-provenance@v4`, and `anchore/scan-action@v7`.

Outcome:

- Workflows now use checkout v6, setup-java v5, setup-gradle v6,
  upload-artifact v7, actions/attest v4, and scan-action v7.
- `anchore/sbom-action` remains on v0 because that project still has no
  non-zero major line.
- The release workflow has explicit `id-token: write` and
  `attestations: write` permissions for provenance.
- The documented policy keeps major-version tags by default and reserves
  full-SHA pins for incident response, high-risk release hardening, weak
  maintenance signals, or suspicious tag/release behavior.

## Implementation update (C143)

After C142, C141 remained blocked on maintainer identity/account action,
so the next implementable roadmap item was **C143**. Rev 5 had already
expanded `docs/android-17-readiness.md`; this implementation added the
missing device-matrix smoke flow.

Outcome:

- Added an Android 17 memory / large-screen add-on to
  `docs/device-matrix.md`.
- The smoke flow now captures `dumpsys activity exit-info` for release
  and debug package names and tells testers how to triage
  `MemoryLimiter:AnonSwap`, `REASON_LOW_MEMORY`, ANR, and repeated
  service exits.
- The wide-form-factor flow now covers sw600dp emulator, tablet,
  foldable, desktop-windowing, and Android TV candidates without
  fabricating any device-result rows.
- ROADMAP / PROJECT_CONTEXT / FEATURE_BACKLOG / PRIORITIZATION_MATRIX
  now mark C143 shipped.

## Implementation update (C132-C136)

C141 still requires maintainer account / identity action outside Git, so
the next implementable roadmap batch after C143 was the rev 4.1 pass-2
correctness set:

- C132/C133: service ramp atomicity and cancel-before-clear.
- C134: CDM partial reflection cache invalidation.
- C135: overlay install/apply/clear mutation locking.
- C136: SurfaceFlinger / KCAL failed-write cache invalidation.

No new external research was needed for this pass; the evidence source is
local code review (S00/S00c). Verification covered unit tests with
`--rerun-tasks`, debug assemble, lint, and `git diff --check`.
