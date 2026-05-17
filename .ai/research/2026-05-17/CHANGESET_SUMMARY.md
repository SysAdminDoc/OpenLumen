# Changeset Summary — 2026-05-17 walk-away research session

Every file this session created or modified, plus why.

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
