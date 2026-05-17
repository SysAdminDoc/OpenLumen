# Memory Consolidation — 2026-05-17

Post-rev memory update: implementation passes later on 2026-05-17 updated
the canonical committed memory through C63's matrix-capable CVD preset
slice. Use `PROJECT_CONTEXT.md`, `ROADMAP.md`, `RESEARCH_LOG.md`, and
`CHANGESET_SUMMARY.md` as the current state; older stale notes below are
historical reconciliation evidence.

Inventory and reconciliation of every AI memory / instruction / project-
context file in the OpenLumen repository, plus the broader memory surface
this session inherited.

## Files inspected

### Tool-specific instruction files (preserved as-is)

| File | Purpose | Status |
|---|---|---|
| [CLAUDE.md](../../../CLAUDE.md) | Working notes for Claude sessions — stack, build commands, key paths, gotchas, version history, status | **Present, partially stale** |
| `AGENTS.md` | Not present | **Absent** |
| `.cursorrules` / `.cursor/rules/**` | Not present | **Absent** |
| `.windsurfrules` | Not present | **Absent** |
| `GEMINI.md` | Not present | **Absent** |
| `COPILOT_INSTRUCTIONS.md` / `.github/copilot-instructions.md` | Not present | **Absent** |
| `.claude/**` | Not present | **Absent** |

`CLAUDE.md` is the only tool-specific instruction file in this repo. It is
explicitly marked at the top:

> Local-only file. NEVER committed (see .gitignore).

It is ignored by Git, which means consolidating it into a committed
`PROJECT_CONTEXT.md` is the correct move — `CLAUDE.md`'s author-intent is to
stay out of the public repo. The committed `PROJECT_CONTEXT.md` becomes the
canonical project memory; `CLAUDE.md` stays as the local working file with a
pointer to it.

### Project-context files (committed)

| File | Purpose | Status |
|---|---|---|
| [README.md](../../../README.md) | User + maintainer entry point | Current. Updated 2026-05-16 (commit `4606b97`). |
| [ROADMAP.md](../../../ROADMAP.md) | Source-backed release plan with candidate inventory | Current as of rev 3, 2026-05-17. |
| [CHANGELOG.md](../../../CHANGELOG.md) | Per-release notes | Current through v0.4.0; `[Unreleased]` section captures most of v0.5.0 but is missing the 2026-05-17 audit hardening additions (those are documented in ROADMAP rev 3 but not yet folded back into CHANGELOG). |
| [CONTRIBUTING.md](../../../CONTRIBUTING.md) | How to contribute | Current. |
| [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) | Module + runtime architecture | Current; snapshot says "as of v0.4.0" and matches `main`. |
| [docs/research-watchlist.md](../../../docs/research-watchlist.md) | Active OSS / policy watchlist | **Stale header**: says "Last review: 2026-05-16" but rev 3 (today) was effectively a watchlist review. |
| [docs/api-36-readiness.md](../../../docs/api-36-readiness.md) | Forward-looking platform-migration inventory | **Stale framing**: titled and written for "Android 16 / API 36"; rev 3 expanded the candidate to Android 17 readiness. Document needs updating to match. |
| [docs/deferred-candidates.md](../../../docs/deferred-candidates.md) | Durable analysis of deferred roadmap items | Current. |
| [docs/overlay-and-per-app-design.md](../../../docs/overlay-and-per-app-design.md) | Design analysis for C10/C11/C12/C69/C90 | Current. |
| [docs/health-evidence.md](../../../docs/health-evidence.md) | What we claim and don't | **Missing 2025/2026 update** — rev 3 acknowledges S99-S102 (consensus shift toward "total luminance > spectrum") but this document has not yet folded the consensus shift into its "Sources" section. |
| [docs/threat-model.md](../../../docs/threat-model.md), [docs/sbom-and-advisories.md](../../../docs/sbom-and-advisories.md), [docs/dependency-verification.md](../../../docs/dependency-verification.md), [docs/wake-and-vitals.md](../../../docs/wake-and-vitals.md), [docs/play-fgs-evidence.md](../../../docs/play-fgs-evidence.md), [docs/reproducible-build.md](../../../docs/reproducible-build.md), [docs/release-checklist.md](../../../docs/release-checklist.md), [docs/troubleshooting.md](../../../docs/troubleshooting.md), [docs/compatibility-table.md](../../../docs/compatibility-table.md), [docs/root-safety.md](../../../docs/root-safety.md), [docs/automation.md](../../../docs/automation.md), [docs/profile-import-formats.md](../../../docs/profile-import-formats.md), [docs/translations.md](../../../docs/translations.md) | Topic-specific | Each is current as of v0.5.0 work; none reviewed for the rev 3 platform updates yet. |
| [docs/device-matrix.md](../../../docs/device-matrix.md) | Per-device confirmation matrix | **Empty by design** — every row says "smoke pending"; no Android 17 row yet. |

## Reconciliation rules used

1. **Preserve tool-specific instructions.** `CLAUDE.md` is preserved verbatim
   (and stays git-ignored). A brief pointer to `PROJECT_CONTEXT.md` will be
   appended to it.
2. **Promote durable facts to `PROJECT_CONTEXT.md`.** Stack details, module
   layout, key code paths, gotchas, and non-negotiable philosophy markers
   that appear in both `CLAUDE.md` and the docs are consolidated.
3. **Leave roadmap content in `ROADMAP.md`.** The rev 3 roadmap is the canon
   for forward-looking work. `PROJECT_CONTEXT.md` summarises but does not
   duplicate it.
4. **Surface conflicts rather than silently resolving them.** See "Open
   conflicts" below.

## Open conflicts

### 1. `CLAUDE.md` "Status" vs. real state

> **CLAUDE.md** (current text):
> v0.2.0 = UI complete enough to actually test. Not yet smoke-tested on a real
> device. Next step is `./gradlew assembleDebug` + install on a Pixel and
> exercise each driver. See ROADMAP.md for v0.3.0 (AlarmManager fallback,
> profile export/import).

> **Repository on disk**:
> Tagged: v0.4.0. On `main`: v0.5.0 feature-complete with the 2026-05-17 audit
> pass. C25 (alarm-based schedule), C30 (profile export/import) shipped two
> releases ago.

**Resolution**: `CLAUDE.md`'s "Version history" and "Status" sections are
out of date with the actual repo. The "Stack" and "Gotchas" sections remain
accurate. Recommendation: trim the stale sections and add a pointer to
`PROJECT_CONTEXT.md` + `ROADMAP.md` for live status. Done as part of this
consolidation (see "Pointer additions" below). Author-original `CLAUDE.md`
preserved otherwise.

### 2. `docs/api-36-readiness.md` framing vs. rev 3

> **`docs/api-36-readiness.md`** (current text, lines 1-9):
> # Android 16 / API 36 Readiness
> > Tied to roadmap candidate **C82** (Android 16/API 36 readiness).

> **`ROADMAP.md` rev 3** (lines 829-832):
> C82/C103 — the rev 2 candidate name was "API 36 readiness"; rev 3 renames
> the work to "Android 17 readiness" because Android 17 stable is the
> realistic next target SDK, not 16.

**Resolution**: the document title and intro need updating. The CHECKLIST in
the body is still useful as an inheritance pattern but every Android-16-
specific behavior should be cross-checked against the Android 17 behavior-
changes page. Documented as a follow-up in `ROADMAP.md` rev 3.x and
`CONTINUE_FROM_HERE.md` (if produced).

### 3. `docs/research-watchlist.md` "Last review" date

> **`docs/research-watchlist.md`** line 10: `Last review: **2026-05-16**.`

> **Rev 3 of `ROADMAP.md`** was authored 2026-05-17 and visibly walked every
> watchlist source (every entry has a 2026-04/05 dated "Last useful signal"
> column update in rev 3's text).

**Resolution**: bump the header to 2026-05-17 and credit rev 3 as the
review. Low-stakes textual fix.

### 4. `docs/health-evidence.md` "Sources" predates the 2025/2026 consensus shift

> **`docs/health-evidence.md`** cites S45 / S46 / S47 only — the three
> sources known in rev 2.

> **`ROADMAP.md` rev 3** adds S99-S102 (HN discussion, Scientific Reports
> 2026, PubMed 2025, Sleep 2024) supporting "total luminance > spectrum for
> sleep onset," and C126 promotes a documentation update.

**Resolution**: C126 in rev 3 is the placeholder. The follow-up edit to
`docs/health-evidence.md` is a one-paragraph "what changed in 2025/2026"
note plus four new sources in the Sources section.

### 5. CHANGELOG `[Unreleased]` vs. rev 3 audit hardening

The CHANGELOG `[Unreleased]` section captures most v0.5.0 work but
*pre-dates* the rev 3 audit pass on 2026-05-17. The audit fixes listed at
`ROADMAP.md` rev 3 lines 288-321 (Schedule.kt Solar bug, polar handling,
LumenService mid-ramp lerp, etc.) are on disk in the modified `.kt` files
but not yet rolled into the CHANGELOG. Resolution: at the v0.5.0 cut, the
maintainer folds the audit lines into a "Hardening" subsection of
`[Unreleased]`, or cuts a v0.5.1 with the hardening fixes alone.

### 6. Auto-memory git-push assumption

User auto-memory (`swiftfloris-git-auth.md`) records that
`git push` to `SysAdminDoc/SwiftFloris` fails 403 from this VM. The repo
state here (22 commits ahead of `origin/main` on `SysAdminDoc/OpenLumen`)
strongly implies the same constraint applies to OpenLumen. **Action**: do
not attempt `git push`; commit locally and let the user push from a machine
with credentials. This consolidation respects that — every artifact below is
written to the working tree only.

## What was promoted to PROJECT_CONTEXT.md

Anything reasonably-stable about the project that a future AI session would
want without re-reading 20+ documents:

- Project identity (name, license, distribution posture, philosophy).
- Stack and module layout (from `gradle/libs.versions.toml` and
  `app/build.gradle.kts`).
- Engine taxonomy and rank order.
- Single-source-of-truth persistence model.
- Permission inventory (asked / not asked).
- CI structure (three workflows + their gates).
- Hard constraints (no INTERNET; no AccessibilityService; no UsageStats;
  GPL-3.0; F-Droid first).
- Recovery / emergency-off commands.
- A pointer map to ROADMAP, CHANGELOG, design docs.

## What deliberately stayed out of PROJECT_CONTEXT.md

- Speculative roadmap items, candidate IDs, tier shifts (live in
  `ROADMAP.md`).
- Per-release CHANGELOG content.
- Detailed per-engine implementation walkthroughs (live in
  `docs/ARCHITECTURE.md`).
- Threat-model verticals (live in `docs/threat-model.md`).

## Pointer additions

Where it's clearly safe and low-risk, the consolidation adds one-line
pointers to `PROJECT_CONTEXT.md`. Specifically:

- `CLAUDE.md` — appended a "Canonical project context" section at the top of
  the file, just below the "Local-only file" admonition.

`AGENTS.md` was not present, so no pointer was added there.

## Rev 5 reconciliation update

The third pass rechecked the requested tool-memory file set:

| File / pattern | Status |
|---|---|
| `AGENTS.md` | absent |
| `CLAUDE.md` | present; preserved as the local Claude scratchpad / instructions file |
| `.claude/**` | absent |
| `.claude-instructions` | absent |
| `.cursor/**`, `.cursorrules` | absent |
| `.windsurfrules` | absent |
| `GEMINI.md` | absent |
| `COPILOT_INSTRUCTIONS.md` | absent |
| `.github/copilot-instructions.md` | absent |

New durable facts promoted or cross-linked in rev 5:

- Android developer verification is a distribution concern for OpenLumen,
  not a Play-only concern, because the project is F-Droid/direct-APK
  oriented and Android's 2026 enforcement applies to certified devices
  in initial regions regardless of app source. Promoted to C141 and
  summarized in `PROJECT_CONTEXT.md`.
- CI action major versions and Node 24 readiness are now time-bound by
  GitHub's 2026-06-02 runner change. Promoted to C142.
- The existing Android 17 readiness memory was incomplete: it covered
  AAPM / FGS / BAL but not the Beta 4 memory limiter or sw600dp
  resizability behavior. Promoted to C143.

Open conflicts after rev 5:

1. **Release-readiness doc vs. dirty working tree**:
   `docs/v0.5.0-release-readiness.md` says the 15-file hardening pass is
   present but not yet committed. That was still true at the start of
   rev 5. Some dirty Kotlin changes overlap rev 4.1's C132/C133/C135
   concerns, but C134 is not visibly implemented in the sampled diff.
   Treat these as working-tree implementation state until tests and a
   commit confirm them.
2. **Major-tag action policy vs. GitHub SHA-pinning guidance**:
   `ci.yml` documents a major-version tag policy for Dependabot
   ergonomics. GitHub's secure-use reference says full SHA pinning is the
   only immutable action reference. Rev 5 does not silently change the
   policy; C142 requires an explicit maintainer decision.
