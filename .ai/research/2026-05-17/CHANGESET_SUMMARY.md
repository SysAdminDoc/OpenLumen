# Changeset Summary — 2026-05-17 walk-away research session

Every file this session created or modified, plus why. The session ran in
two passes (both 2026-05-17):

- **Pass 1** produced the rev 4 ROADMAP supplement, `PROJECT_CONTEXT.md`,
  and 10 research notebook files (commit `e71ee12`).
- **Pass 2** completed the rev 4 follow-ups, ran a focused code-review
  + F-Droid/Shizuku/Compose research pass, produced 9 new ROADMAP
  candidates (C132-C140) + 27 new sources (S203-S229), and updated
  the ROADMAP to rev 4.1.

See [SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) for the
pass-2 analysis. The pass-1 narrative below is preserved unchanged.

## Files created

### Root-level

| Path | Purpose |
|---|---|
| [PROJECT_CONTEXT.md](../../../PROJECT_CONTEXT.md) | Canonical consolidated project memory — identity, stack, modules, engines, persistence, runtime flow, permissions, CI, key paths, build commands, recovery, gotchas, hard constraints. Tied to the "Consolidate scattered project memory into a canonical project context" deliverable in the session brief. |

### `.ai/research/2026-05-17/`

| Path | Purpose |
|---|---|
| [STATE_OF_REPO.md](STATE_OF_REPO.md) | Local repository reconnaissance memo. Inputs: `git status`, `git log --oneline -30`, `git diff --stat HEAD`, `gradle/libs.versions.toml`, manifests, all 20 `docs/`, the 62-file Kotlin tree, all three CI workflows, Dependabot config, branding/, fastlane/, issue templates. |
| [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) | Inventory of AI memory/instruction files (CLAUDE.md present; AGENTS.md, .cursorrules, GEMINI.md, .windsurfrules, COPILOT_INSTRUCTIONS.md, .claude/** absent). Open-conflicts list: CLAUDE.md status text out of date; `docs/api-36-readiness.md` titled for API 36 not Android 17; `docs/research-watchlist.md` "Last review" date stale; `docs/health-evidence.md` sources predate the 2025/2026 consensus shift; CHANGELOG `[Unreleased]` predates the 2026-05-17 audit; auto-memory git-push restriction inherited from SwiftFloris context. |
| [SOURCE_REGISTER.md](SOURCE_REGISTER.md) | Full source list with S00-S125 preserved from rev 3 and S126-S202 added this session. Topic groupings: Android 17 platform / AGP / Hilt / DataStore / Compose screenshot / F-Droid / sleep / Shizuku / competitor sweep / PWM / OWASP / Glance / Red Moon / NightLight. |
| [RESEARCH_LOG.md](RESEARCH_LOG.md) | Phases, search strategies, agent prompts, saturation tests, triage of new sources against rev 3 (which claims they strengthen, which tier shifts they imply, which new candidates they motivate). |
| [COMPETITOR_MATRIX.md](COMPETITOR_MATRIX.md) | Direct OSS competitors / dormant references / commercial references / adjacent OSS to borrow from / Shizuku-backed peer-architecture exemplars / Wear OS (negative result) / Android TV. Strategic takeaways feed FEATURE_BACKLOG and PRIORITIZATION_MATRIX. |
| [FEATURE_BACKLOG.md](FEATURE_BACKLOG.md) | Raw harvested ideas: four new candidates (C128 FabricatedOverlay engine spike, C129 OLED-aware gamma LUT clamp, C130 AAPM driver-report surface, C131 Eye Dropper integration), proposed tier shifts (C123, C101), doc/process follow-ups, negative candidates. |
| [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md) | Scored deltas vs rev 3. New-candidate scoring table. Now/Next/Later composition for rev 4. "Why this matters now" summary tying the Now-tier to Android 17 stable (June 2026) and AGP 10 opt-out removal (mid 2026). |
| [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md) | Current dependency floor; CVE state (protobuf-java); MASVS-PRIVACY coverage gap; AAPM detection; overlay-attack taxonomy refresh; reflection-ladder defensive posture; Shizuku service-binding session survival; CI permissions-audit completeness; dependency-upgrade plan in four PR-shaped chunks; reproducibility checklist; Dependabot grouping. |
| [DATASET_MODEL_INTEGRATION_REVIEW.md](DATASET_MODEL_INTEGRATION_REVIEW.md) | Deliberately thin — explains why. Datasets: offline city DB, CVD LUTs, sleep evidence base. Models: Tanner Helland Kelvin, NOAA solar, melanopic EDI (out of scope). Integrations: existing intent surface, Shizuku, Wear OS, Eye Dropper, FabricatedOverlay; out-of-scope: Hue/TRÅDFRI, HA via REST/MQTT, local socket. Benchmarks: not benchmarked, qualitative cost in wake-and-vitals doc. |
| [CHANGESET_SUMMARY.md](CHANGESET_SUMMARY.md) | This file. |

## Files modified

| Path | Why |
|---|---|
| [ROADMAP.md](../../../ROADMAP.md) | Promoted rev 3 → rev 4. Added a "What changed in rev 4" section listing four new candidates (C128-C131), two tier shifts (C123, C101), primary-source citation refresh, wider competitor sweep, doc/process follow-ups. Added a rev 3 → rev 4 tier-shift table and a new-candidates table (C128-C131). Added an "External URLs (rev 4 — new, primary-source refresh)" subsection to the Source Appendix listing S126-S202. Extended the Self-Audit with a "Rev 4 additions" block and a companion-artefacts pointer to `.ai/research/2026-05-17/`. Rev 3 content preserved verbatim. |
| [CLAUDE.md](../../../CLAUDE.md) | Inserted a single "Canonical Project Context" section near the top pointing to `PROJECT_CONTEXT.md`, `ROADMAP.md`, and the `.ai/research/2026-05-17/` notebook. Stale "Version history" / "Status" sections preserved with an explicit note that they predate v0.4.0. CLAUDE.md remains git-ignored per its own admonition. |

## Files NOT modified (deliberately)

- All Kotlin source under `app/`, `core-engine/`, `core-prefs/`,
  `core-schedule/`. The walk-away brief is research + memory consolidation
  + roadmap planning; not code changes. The 2026-05-17 in-tree audit pass
  (already done earlier today before this session began) is the code
  change that ships with v0.5.0; this session does not touch the audited
  files.
- All other docs under `docs/`. The follow-ups identified
  (`docs/api-36-readiness.md` rename, `docs/research-watchlist.md` date
  bump, `docs/health-evidence.md` source refresh, `docs/threat-model.md`
  MASVS-PRIVACY extension) are documented in ROADMAP rev 4's "What
  changed in rev 4" list so the maintainer can pick them up explicitly
  in the next commit. We deliberately did not silently edit those docs.
- The `branding/`, `fastlane/`, `gradle/`, `dist/`, `build/`, `.gradle/`,
  `.kotlin/` directories.
- All three `.github/workflows/*.yml` workflows. The proposed CI
  permissions-audit grep expansion is captured in ROADMAP rev 4 and
  `SECURITY_AND_DEPENDENCY_REVIEW.md` for follow-up.
- `CHANGELOG.md`. Rev 3's audit-hardening fold-in is a maintainer
  decision (could go in `[Unreleased]` or in a v0.5.1 hardening cut).
  Documented as a rev 4 follow-up.
- `.gitignore`. `PROJECT_CONTEXT.md` does NOT need to be in `.gitignore`
  (it's a committed artifact); the existing `CLAUDE.md` entry is
  preserved (CLAUDE.md is intentionally git-ignored per its own header).

## Files NOT created

- `CONTINUE_FROM_HERE.md`: not produced because the session reached its
  natural completion (all 9 required artefacts written, plus the canonical
  `PROJECT_CONTEXT.md`, plus the rev 4 roadmap supplement). No hard
  limits hit.

## Reversibility

All file changes are reversible:

- The new `.ai/` directory can be deleted in one command if undesired.
- `PROJECT_CONTEXT.md` is a single new file at the repo root and is the
  cleanest single artefact to remove if needed.
- The `ROADMAP.md` rev 4 changes are additive (rev 3 text preserved
  verbatim); reverting is a one-commit revert.
- The `CLAUDE.md` change inserts one new section near the top; the
  surrounding rev-2-era version-history text is preserved unchanged.

## Counts

| Type | Count |
|---|---:|
| Files created at repo root | 1 (`PROJECT_CONTEXT.md`) |
| Files created under `.ai/research/2026-05-17/` | 10 |
| Files modified | 2 (`ROADMAP.md`, `CLAUDE.md`) |
| New source IDs added to the source register | 77 (S126-S202) |
| New candidate IDs introduced | 4 (C128-C131) |
| Existing-candidate tier shifts | 2 (C123 UC→Next, C101 risk 1→2) |
| Doc/process follow-ups documented for the next commit | 7 |
| Kotlin source files modified | 0 |
| Gradle / manifest / CI files modified | 0 |

## Verification

Quick sanity checks before commit:

1. `ROADMAP.md` rev 3 lines 1-200 (philosophy, what changed in rev 3,
   state of the repo, evidence map) preserved verbatim — confirmed by
   reading lines 12-300 after the rev 4 prepend.
2. `ROADMAP.md` rev 3 candidate inventory (C01-C127) preserved
   verbatim — confirmed; new C128-C131 land in a new sub-table.
3. `ROADMAP.md` rev 3 source appendix S00-S125 preserved verbatim —
   confirmed; new S126-S202 land in a new "External URLs (rev 4 —
   new)" sub-section.
4. `CLAUDE.md` "Stack" / "Build commands" / "Key paths" / "Architecture"
   / "Gotchas" / "Version history" / "Status" sections preserved
   verbatim — confirmed; only the new "Canonical Project Context"
   section is inserted.
5. `PROJECT_CONTEXT.md` does not duplicate ROADMAP content — confirmed
   (it summarises shipping state and gotchas; forward-looking work
   redirects to `ROADMAP.md`).
6. Every `.ai/research/2026-05-17/*.md` file backlinks to its peers
   and to repo-root files using relative `../../../foo` paths.

---

## Pass 2 changeset (2026-05-17 afternoon)

The user re-invoked the walk-away brief; pass 2 closed every doc/process
follow-up rev 4 itemised AND added a focused code-review +
F-Droid/Shizuku/Compose research pass.

### Files created (pass 2)

| Path | Purpose |
|---|---|
| [.ai/research/2026-05-17/SECOND_PASS_FINDINGS.md](SECOND_PASS_FINDINGS.md) | Full second-pass narrative: 3 HIGH-severity code-review bugs the 2026-05-17 audit didn't catch, F-Droid never-submitted finding, concrete Compose BOM / Material 3 / AGP 9 targets, Shizuku code shapes, FabricatedOverlay 12L+ constraint that downgrades C128. |

### Files modified (pass 2)

| Path | Why |
|---|---|
| `ROADMAP.md` | Rev 4 → rev 4.1 supplement. Added 9 new candidates (C132-C140), tier shift for C128 → Later, 27 new sources (S203-S229), and a "What changed in rev 4.1" section. |
| `README.md` | Updated link target for the renamed `docs/api-36-readiness.md` → `docs/android-17-readiness.md`. |
| `CHANGELOG.md` | Folded the 2026-05-17 audit hardening into `[Unreleased]` as a "Hardening" subsection; fixed reference to the renamed doc. |
| `PROJECT_CONTEXT.md` | Fixed reference to the renamed doc. |
| `CLAUDE.md` (git-ignored) | No further changes; pass 1 added the pointer. |
| `docs/research-watchlist.md` | "Last review" date bumped 2026-05-16 → 2026-05-17 with reference to rev 3/4 review effort. |
| `docs/health-evidence.md` | Sources section expanded with S99-S102 (rev 3) and S158-S162 (rev 4) plus a "what changed in 2025/2026 consensus" subsection. |
| `docs/threat-model.md` | Added a MASVS-PRIVACY section to match MASVS v2.1.0. The substance was already covered across Data inventory + Permission inventory; the categorical header was missing. |
| `docs/sbom-and-advisories.md` | "Accepted exposures" table entry added for protobuf-java CVE-2024-7254 with the no-INTERNET rationale. |
| `.github/workflows/ci.yml` | Expanded the `permissions-audit` grep to also block `ACCESS_*_LOCATION`, `READ_PHONE_STATE`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE`. Renamed the step accordingly. |
| `.github/workflows/release.yml` | Matching grep expansion on the release-APK assertion. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Appended a "Second-pass additions (S203-S229)" section with 27 new sources. |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | This file. |

### Files renamed (pass 2)

| From | To | Method |
|---|---|---|
| `docs/api-36-readiness.md` | `docs/android-17-readiness.md` | `git mv`, then body rewrite to align with rev 3's C82 → C103 Android-17 expansion. |

### Counts (pass 2)

| Type | Count |
|---|---:|
| Files created at repo root | 0 |
| Files created under `.ai/research/2026-05-17/` | 1 (`SECOND_PASS_FINDINGS.md`) |
| Files modified | 11 |
| Files renamed | 1 (`api-36-readiness.md` → `android-17-readiness.md`) |
| New source IDs added | 27 (S203-S229) |
| New candidate IDs introduced | 9 (C132-C140) |
| Existing-candidate tier shifts | 1 (C128 UC → Later) |
| Doc/process follow-ups completed (out of rev 4's 7) | 7 of 7 |
| Kotlin source files modified | 0 (code-review findings deferred to next maintainer commit; the bugs are documented in `SECOND_PASS_FINDINGS.md` + ROADMAP rev 4.1 candidates C132-C136) |

### What pass 2 did NOT modify

- Kotlin source. C132-C136 are documented but the actual fixes are
  deferred. The pre-existing 2026-05-17 in-tree audit (15 Kotlin/test/
  strings files) stays unstaged for the maintainer.
- `gradle/libs.versions.toml`. AGP 9 / Compose BOM / Material 3 targets
  are documented as concrete numbers but deferred to C95 / C137.
- `app/build.gradle.kts`. No build-config changes.
- `branding/`, `fastlane/`. No design changes.
- Tests (the JUnit / Truth unit tests in core-engine / core-prefs /
  core-schedule / app). Recommended new tests are documented in
  `SECOND_PASS_FINDINGS.md` but not added.

### Reversibility (pass 2)

All pass-2 changes are reversible:

- The `git mv` rename can be reverted by `git mv docs/android-17-readiness.md
  docs/api-36-readiness.md` (history is preserved).
- Doc edits are localised to single sections of single files.
- CI workflow edits are additive (more strings in a regex); revert by
  removing the new alternation members.
- `ROADMAP.md` rev 4.1 prepends a "What changed in rev 4.1" section and
  appends new candidate rows + new source URLs; rev 4 content is
  preserved.

## Pass 3 changeset (rev 5, 2026-05-17)

Pass 3 resumed after rev 4.1 and added a distribution / platform / CI
refresh.

### Files modified (pass 3)

| File | Why |
|---|---|
| `ROADMAP.md` | Promoted rev 4.1 -> rev 5. Added C141-C144 and summarized Android developer verification, Android 17 memory/resizability, CI action/Node 24, and AndroidX/Hilt/AGP findings. |
| `PROJECT_CONTEXT.md` | Added a short rev 5 context note so future sessions see Android developer verification and CI action rotation as current planning constraints. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S230-S257 and a rev 5 coverage update. |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | Added third-pass queries, saturation notes, and outcomes. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Added raw candidate sketches for C141-C144. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Added rev 5 scoring and tier placement for C141-C144. |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | Added developer-verification, GitHub Actions Node 24 / major rotation, AGP/Hilt coupling, and AndroidX drift analysis. |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | Added current local command context, file counts, and dirty-tree state. |
| `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` | Rechecked requested instruction files and recorded rev 5 conflicts. |
| `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` | Added DimTV / F-Droid Dimmer / general-roundup saturation update. |
| `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` | Added developer-verification and GitHub Actions as platform/distribution integration findings. |
| `docs/v0.5.0-release-readiness.md` | Added soft-gate reminders for C141-C143. |
| `docs/android-17-readiness.md` | Added memory-limiter and sw600dp resizability checks to the Android 17 behavior table and test plan. |
| `docs/sbom-and-advisories.md` | Added Node 24 / action-major rotation note under workflow rotation. |
| `docs/release-checklist.md` | Added CI action review and developer-verification checks. |
| `docs/research-watchlist.md` | Added Android developer verification and GitHub Actions watchpoints. |

### Counts (pass 3)

| Metric | Count |
|---|---:|
| New source IDs introduced | 28 (S230-S257) |
| New candidate IDs introduced | 4 (C141-C144) |
| Kotlin source files modified by pass 3 | 0 |

### What pass 3 did NOT modify

- Kotlin behavior. Existing dirty Kotlin files were preserved and treated
  as pre-existing hardening work.
- `CLAUDE.md` / `AGENTS.md`. `CLAUDE.md` already has the canonical-context
  pointer; `AGENTS.md` is absent.
- Release versionCode / versionName. Rev 5 is planning/research only.

### Reversibility (pass 3)

All rev 5 changes are additive documentation changes. Reverting the pass
is a normal Git revert of the rev 5 documentation commit.

### Verification (pass 3)

The machine did not have `java`, `JAVA_HOME`, or `ANDROID_HOME` available
when verification began. Pass 3 installed user-local prerequisites only:

- Temurin JDK 17.0.19 under `C:\Users\Xray\.codex\jdks\temurin-17`.
- Android command-line SDK under `C:\Users\Xray\.codex\android-sdk`,
  sourced from the official Android Developers SDK download page (S257).
- Android packages: `platform-tools`, `platforms;android-35`, and
  `build-tools;35.0.0`.

Verification commands run from `C:\Users\Xray` with `JAVA_HOME` and
`ANDROID_HOME` scoped to each command:

- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --stacktrace`
  — passed, `BUILD SUCCESSFUL`, 133 actionable tasks.
- `:app:assembleDebug --stacktrace` — passed, `BUILD SUCCESSFUL`, 98
  actionable tasks.
- `:app:lintDebug --stacktrace` — passed on the serial rerun,
  `BUILD SUCCESSFUL`, 142 actionable tasks. The first lint attempt ran in
  parallel with `assembleDebug` and timed out without useful lint output,
  so it was rerun alone.

## Implementation pass 4 (C142, 2026-05-17)

This pass switched from research/planning into roadmap execution and
implemented **C142 — CI action major rotation and SHA-pinning policy**.

### Files modified (pass 4)

| File | Why |
|---|---|
| `.github/workflows/ci.yml` | Rotated checkout/setup-java/setup-gradle to current Node-24-capable majors and clarified the major-tag policy comment. |
| `.github/workflows/release.yml` | Rotated checkout/setup-java/setup-gradle, moved provenance from `actions/attest-build-provenance@v2` to `actions/attest@v4`, and added attestation permissions. |
| `.github/workflows/sbom.yml` | Rotated checkout/setup-java/setup-gradle/upload-artifact/scan-action to current majors while leaving `anchore/sbom-action@v0` on its only major line. |
| `ROADMAP.md` | Marked C142 shipped and recorded the exact workflow major versions. |
| `PROJECT_CONTEXT.md` | Updated the CI/supply-chain snapshot so future sessions see C142 as complete. |
| `CHANGELOG.md` | Added the workflow rotation to `[Unreleased]`. |
| `docs/sbom-and-advisories.md` | Replaced the C142 reminder with the active major-tag policy and full-SHA exception path. |
| `docs/reproducible-build.md` | Updated the JDK action and release provenance references. |
| `docs/dependency-verification.md` | Updated the release provenance control reference. |
| `docs/android-17-readiness.md` | Updated the Android 17 test-plan attestation reference. |
| `docs/v0.5.0-release-readiness.md` | Marked C142 done instead of deferred. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S258-S265 for the upstream tag / metadata checks used during implementation. |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | Logged this implementation pass. |

### Verification (pass 4)

- Upstream tag checks via `git ls-remote` confirmed the selected major
  lines: checkout v6, setup-java v5, setup-gradle v6, upload-artifact v7,
  attest v4, attest-build-provenance v4 wrapper, scan-action v7, and
  sbom-action v0.
- Workflow metadata checks confirmed `actions/setup-java@v5` and
  `anchore/scan-action@v7` are Node-24 lines, `actions/upload-artifact@v7`
  preserves zipped uploads by default, and `actions/attest@v4` accepts the
  existing `subject-path` provenance shape.
- `npx --yes prettier@3.5.3 --check "Z:/repos/OpenLumen/.github/workflows/*.yml"`
  passed; this was the local YAML parser/format validation path.
- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --stacktrace`
  passed (`BUILD SUCCESSFUL`, 133 actionable tasks, configuration cache
  reused).
- `:app:assembleDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 98
  actionable tasks, configuration cache reused).
- `:app:lintDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 142
  actionable tasks, configuration cache reused).

## Implementation pass 5 (C143, 2026-05-17)

This pass implemented **C143 — Android 17 memory/resizability smoke
expansion**. The Android 17 behavior inventory had already been added in
rev 5, but `docs/device-matrix.md` still lacked the matching executable
smoke flow.

### Files modified (pass 5)

| File | Why |
|---|---|
| `docs/device-matrix.md` | Added the Android 17 memory / large-screen add-on with `dumpsys activity exit-info`, `MemoryLimiter:AnonSwap`, sw600dp, foldable, tablet, desktop-windowing, and TV checks. |
| `ROADMAP.md` | Marked C143 shipped and added the implementation-progress note. |
| `PROJECT_CONTEXT.md` | Updated the watchpoint so future sessions see C143 as complete. |
| `CHANGELOG.md` | Added the C143 documentation change under `[Unreleased]`. |
| `docs/v0.5.0-release-readiness.md` | Marked C143 done instead of deferred. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S266 for the AOSP `dumpsys activity exit-info` dump surface. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Marked C143 shipped and expanded sources. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Removed C143 from the outstanding Now list. |
| `.ai/research/2026-05-17/CHANGESET_SUMMARY.md` | Logged this implementation pass. |

### Verification (pass 5)

Docs-only change. Verification should include `git diff --check`; no
Kotlin/Gradle behavior changed in this pass.

## Implementation pass 6 (C132-C136, 2026-05-17)

This pass implemented the rev 4.1 code-review correctness batch after
C142/C143. C141 remains blocked on maintainer identity/account action
outside Git, so the next implementable roadmap work was C132-C136.

### Files modified (pass 6)

| File | Why |
|---|---|
| `app/src/main/java/com/openlumen/service/LumenService.kt` | Added `rampMutex`; serialized transition cancel/join/launch; cancel+join active ramp before clear or engine switch. |
| `core-engine/src/main/java/com/openlumen/engine/engines/ColorDisplayManagerEngine.kt` | Added `clearCache()` and invalidated partial reflection cache failures. |
| `core-engine/src/main/java/com/openlumen/engine/engines/SurfaceFlingerEngine.kt` | Checked apply/clear `Su.runCommand` results and invalidated cached transaction code on nonzero / `not found` output. |
| `core-engine/src/main/java/com/openlumen/engine/engines/KcalEngine.kt` | Made KCAL scripts fail fast with `set -e` and invalidated cached sysfs paths on nonzero shell exit. |
| `core-engine/src/main/java/com/openlumen/engine/engines/OverlayEngine.kt` | Serialized install/apply/clear `View` and `WindowManager` mutation with an internal `viewLock`. |
| `ROADMAP.md` | Marked C132-C136 shipped and added the implementation-progress note. |
| `PROJECT_CONTEXT.md` | Updated durable architecture/gotcha context for ramp, cache, and overlay locking behavior. |
| `CHANGELOG.md` | Added the service/engine correctness fixes under `[Unreleased]`. |
| `docs/ARCHITECTURE.md` | Documented `rampMutex` in the service concurrency model. |
| `docs/v0.5.0-release-readiness.md` | Marked Gate 2 C132-C136 landed and replaced stale Gate 1 commit instructions. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S00c for this local implementation evidence. |

### Verification (pass 6)

- First test run timed out at the shell-tool layer and left stale Gradle /
  transformed-class output on the shared drive. After stopping Gradle and
  forcing a clean rerun, the authoritative verification passed.
- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --rerun-tasks --stacktrace`
  passed (`BUILD SUCCESSFUL`, 133 actionable tasks).
- `:app:assembleDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 98
  actionable tasks).
- `:app:lintDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 142
  actionable tasks).
- `git diff --check` passed with only the repo's CRLF conversion warnings.

## Implementation pass 7 (C130, 2026-05-17)

This pass implemented **C130 — AAPM driver-report surface** after the
C132-C136 correctness batch. It remained locally implementable while C141
and C140 require maintainer/account or release-asset work.

### Files modified (pass 7)

| File | Why |
|---|---|
| `app/src/main/AndroidManifest.xml` | Declared `android.permission.QUERY_ADVANCED_PROTECTION_MODE` for the Android 17 AAPM status query. |
| `app/src/main/java/com/openlumen/diagnostics/DriverReport.kt` | Bumped report format to v2 and added a reflection-gated Advanced Protection section returning `enabled`, `disabled`, `n/a`, or bounded `unknown` status. |
| `ROADMAP.md` | Marked C130 shipped and added the implementation-progress note. |
| `PROJECT_CONTEXT.md` | Added the AAPM permission/report behavior to durable context. |
| `CHANGELOG.md` | Added the driver-report AAPM status change under `[Unreleased]`. |
| `docs/android-17-readiness.md` | Updated the C130 test-plan wording to match the implemented report statuses. |
| `docs/threat-model.md` | Recorded the AAPM permission query in the platform/privacy posture. |
| `docs/v0.5.0-release-readiness.md` | Marked C130 shipped instead of a soft gate. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S267 for the `Context.ADVANCED_PROTECTION_SERVICE` reference. |

### Verification (pass 7)

- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --stacktrace`
  passed (`BUILD SUCCESSFUL`, 133 actionable tasks).
- `:app:assembleDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 98
  actionable tasks).
- `:app:lintDebug --stacktrace` passed (`BUILD SUCCESSFUL`, 142
  actionable tasks). This specifically covered the API-36 permission
  declaration while the repo still compiles against SDK 35.

## Implementation pass 8 (C120, 2026-05-17)

This pass implemented **C120 — VCS info determinism**.

### Files modified (pass 8)

| File | Why |
|---|---|
| `app/build.gradle.kts` | Set `vcsInfo.include = false` for release builds so AGP does not package `META-INF/version-control-info.textproto`. |
| `docs/reproducible-build.md` | Documented the F-Droid comparison risk, the clean-tag build requirement, and the external provenance replacement. |
| `ROADMAP.md` | Marked C120 shipped. |
| `PROJECT_CONTEXT.md` | Added the release VCS-info / provenance rule to durable context. |
| `CHANGELOG.md` | Added the release build reproducibility change under `[Unreleased]`. |
| `docs/v0.5.0-release-readiness.md` | Changed the C120 gate from "apply" to "confirm present." |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S268 for the AGP `VcsInfo` DSL reference. |

### Verification (pass 8)

- `:app:assembleRelease --stacktrace` passed (`BUILD SUCCESSFUL`, 155
  actionable tasks). This verified the AGP 8.7 Kotlin DSL accepts
  `vcsInfo.include = false` and that unsigned release assembly still
  completes.
- `jar tf app/build/outputs/apk/release/app-release-unsigned.apk` showed
  `version-control-info.textproto absent`.

## Implementation pass 9 (C111, 2026-05-17)

This pass completed **C111 — BAL hardening readiness** as a source audit.

### Files modified (pass 9)

| File | Why |
|---|---|
| `ROADMAP.md` | Marked C111 shipped and recorded that no `IntentSender` migration call sites exist. |
| `PROJECT_CONTEXT.md` | Added the BAL audit result to current planning watchpoints. |
| `CHANGELOG.md` | Added the Android 17 BAL audit result under `[Unreleased]`. |
| `docs/android-17-readiness.md` | Replaced the pending C111 migration wording with the concrete audit result and remaining smoke check. |
| `docs/v0.5.0-release-readiness.md` | Marked C111 shipped instead of a soft-gate migration. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S00d for the local audit commands / evidence. |

### Verification (pass 9)

- `rg` found no `IntentSender`, `ActivityOptions`,
  `setPendingIntentBackgroundActivityStartMode`, or
  `MODE_BACKGROUND_ACTIVITY_START_*` call sites in production Kotlin.
- `rg` confirmed existing `PendingIntent` use is direct
  `getActivity`, `getService`, and `getBroadcast`.

## Implementation pass 10 (C116, 2026-05-17)

This pass completed **C116 — don't resume after restart if paused** as a
documentation item.

### Files modified (pass 10)

| File | Why |
|---|---|
| `docs/troubleshooting.md` | Added the explicit paused-before-reboot behavior and how to resume manually. |
| `ROADMAP.md` | Marked C116 shipped. |
| `PROJECT_CONTEXT.md` | Added C116 to current durable watchpoints. |
| `CHANGELOG.md` | Added the troubleshooting documentation change under `[Unreleased]`. |
| `docs/v0.5.0-release-readiness.md` | Marked C116 shipped instead of a soft-gate doc item. |

### Verification (pass 10)

Docs-only change. Local source evidence: `BootReceiver` only starts the
service after boot when `prefs.flow.first().enabled` is true; otherwise it
logs that the filter was disabled and returns.

## Implementation pass 11 (C106, 2026-05-17)

This pass completed **C106 — BOOT_COMPLETED FGS verification rows** as a
documentation/test-plan item without fabricating device results.

### Files modified (pass 11)

| File | Why |
|---|---|
| `docs/wake-and-vitals.md` | Added Android 14/15/16/17 boot-restore validation rows and the exact `dumpsys` capture commands. |
| `docs/device-matrix.md` | Added a boot-restore add-on requiring Android 14+ rows to note `boot restore ?`, `OK`, or `failed`. |
| `docs/android-17-readiness.md` | Pointed Android 17 validation back to the C106 wake/vitals row. |
| `docs/v0.5.0-release-readiness.md` | Marked C106 evidence slots shipped, with real results still under C01. |
| `ROADMAP.md` | Marked C106 shipped as documentation / evidence-slot work. |
| `PROJECT_CONTEXT.md` | Added the C106 evidence-slot state to durable context. |
| `CHANGELOG.md` | Added the C106 documentation change under `[Unreleased]`. |

### Verification (pass 11)

Docs-only change. No device rows were marked passed; all new boot-restore
rows remain pending until a real device or emulator run supplies evidence.

## Implementation pass 12 (C138, 2026-05-17)

This pass completed **C138 — `PreferencesStore` import-size cap
byte-correctness**.

### Files modified (pass 12)

| File | Why |
|---|---|
| `core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt` | Replaced decoded-character import limiting with raw `InputStream` byte limiting before UTF-8 decode. |
| `core-prefs/src/test/java/com/openlumen/prefs/PreferencesImportReadTest.kt` | Added exact-limit and max-plus-one unit coverage for the import byte reader. |
| `ROADMAP.md` | Marked C138 shipped. |
| `PROJECT_CONTEXT.md` | Added the import byte-cap rule to durable persistence context. |
| `CHANGELOG.md` | Added the input-validation fix under `[Unreleased]`. |
| `docs/v0.5.0-release-readiness.md` | Recorded C138 as shipped in the release-readiness checklist. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S00e for local implementation evidence. |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | Logged the local source finding and verification. |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | Added current implementation state for C138. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Marked C138 shipped in the execution update. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Added C138 to the shipped execution table. |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | Recorded the input-validation hardening. |

### Verification (pass 12)

- `:core-prefs:test --no-daemon --rerun-tasks --stacktrace` passed
  after stopping a stale Gradle daemon from an interrupted first run.

## Implementation pass 13 (C137, 2026-05-17)

This pass completed **C137 — `material-icons-extended` deprecation
migration**.

### Files modified (pass 13)

| File | Why |
|---|---|
| `gradle/libs.versions.toml` | Removed the deprecated `compose-material-icons-extended` alias. |
| `app/build.gradle.kts` | Removed the app dependency on `material-icons-extended`. |
| `app/src/main/java/com/openlumen/ui/OpenLumenRoot.kt` | Switched bottom-navigation icons from `Icons.Outlined.*` to local vector resources loaded with `painterResource()`. |
| `app/src/main/java/com/openlumen/ui/screens/PresetsScreen.kt` | Switched favorite / unfavorite icons from `Icons.*` to local vector resources. |
| `app/src/main/res/drawable/ic_nav_*.xml`, `ic_favorite_*.xml` | Added the seven self-hosted vector resources needed by the app UI. |
| `ROADMAP.md` | Marked C137 shipped. |
| `PROJECT_CONTEXT.md` | Recorded the local-vector icon decision in durable stack context. |
| `CHANGELOG.md` | Added the dependency removal under `[Unreleased]`. |
| `docs/v0.5.0-release-readiness.md` | Marked C137 done instead of deferred. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S00f for local implementation evidence. |
| `.ai/research/2026-05-17/RESEARCH_LOG.md` | Logged the call-site inventory and dependency verification. |
| `.ai/research/2026-05-17/STATE_OF_REPO.md` | Added current implementation state for C137. |
| `.ai/research/2026-05-17/FEATURE_BACKLOG.md` | Marked C137 shipped in the execution update. |
| `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` | Added C137 to the shipped execution table. |
| `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` | Recorded the dependency hardening. |

### Verification (pass 13)

- `:app:assembleDebug --no-daemon --stacktrace` passed.
- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --no-daemon --stacktrace`
  passed.
- `:app:lintDebug --no-daemon --stacktrace` passed on a clean rerun
  after the first lint invocation exceeded the shell timeout.
- `:app:dependencies --configuration debugRuntimeClasspath --no-daemon`
  showed no `material-icons-extended` artifact.
- `git diff --check` passed with CRLF conversion warnings only.

## Implementation pass 14 (C105, 2026-05-17)

This pass implemented **C105 — SAW-app FGS-from-background fallback**.

### Files modified (pass 14)

| File | Why |
|---|---|
| `app/src/main/java/com/openlumen/service/LumenServiceStarter.kt` | Added a shared foreground-service start helper that classifies `ForegroundServiceStartNotAllowedException`. |
| `app/src/main/java/com/openlumen/service/LumenTileService.kt` | QS tile toggle-on now rolls back `enabled=false` and opens the app when Android blocks the FGS start. |
| `app/src/main/java/com/openlumen/widget/WidgetActionReceiver.kt` | Added recoverable widget action handling for toggle and preset taps. |
| `app/src/main/java/com/openlumen/widget/ToggleWidget.kt` | Routed widget toggle PendingIntent through the new receiver. |
| `app/src/main/java/com/openlumen/widget/PresetWidget.kt` | Routed preset-chip PendingIntents through the new receiver. |
| `app/src/main/java/com/openlumen/viewmodel/OpenLumenViewModel.kt` | Reused the shared starter helper for in-app starts. |
| `app/src/main/java/com/openlumen/service/BootReceiver.kt` | Reused the shared starter helper for consistent diagnostics. |
| `app/src/main/java/com/openlumen/service/ScheduleAlarmReceiver.kt` | Reused the shared starter helper for schedule reevaluation. |
| `app/src/main/AndroidManifest.xml` | Registered `WidgetActionReceiver`. |
| `ROADMAP.md` | Marked C105 shipped. |
| `PROJECT_CONTEXT.md` | Documented the foreground-start helper in runtime flow context. |
| `CHANGELOG.md` | Added the C105 reliability fix under `[Unreleased]`. |
| `docs/android-17-readiness.md` | Updated the C105 behavior table and smoke plan. |
| `docs/v0.5.0-release-readiness.md` | Marked C105 shipped. |
| `docs/troubleshooting.md` | Updated the blocked-start symptom guidance. |
| `.ai/research/2026-05-17/SOURCE_REGISTER.md` | Added S00g for local implementation evidence. |

### Verification (pass 14)

- `:app:assembleDebug --no-daemon --rerun-tasks --stacktrace` passed
  after stopping a stale Gradle daemon from an interrupted first run.
- `:app:assembleDebug --no-daemon --stacktrace` passed again after the
  final tile compatibility guard.
- `:app:testDebugUnitTest :core-engine:test :core-schedule:test :core-prefs:test --no-daemon --stacktrace`
  passed.
- `:app:lintDebug --no-daemon --stacktrace` passed after adding a
  targeted suppression for the pre-Android-14 TileService compatibility
  branch.
- `git diff --check` passed with CRLF conversion warnings only.
