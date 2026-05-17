# Second-Pass Findings — 2026-05-17

This file captures the second deep pass on OpenLumen, after the first pass
produced the rev 4 roadmap supplement, canonical `PROJECT_CONTEXT.md`, and
the 10-file research notebook (committed as `e71ee12`).

The second pass had three goals:

1. Do the doc / process follow-ups rev 4 deferred.
2. Run a focused **code-quality review** of the four un-inspected core
   files (LumenService, PreferencesStore, Schedule, SolarCalculator)
   plus the engine layer that the 2026-05-17 in-tree audit didn't fully
   cover.
3. Run a focused **research pass** on three thin areas: F-Droid
   application/submission status, Shizuku integration code shapes, and
   Compose BOM / Material 3 target versions for the AGP 9 migration.

Two background agents (`general-purpose`) ran in parallel; their outputs
are folded below.

## Major findings

### 1. OpenLumen has never been submitted to F-Droid

Cross-checked by the F-Droid research agent (sources S203-S205):

- `https://gitlab.com/fdroid/fdroiddata/-/merge_requests` — zero MRs
  match "openlumen."
- `https://gitlab.com/fdroid/rfp/-/issues` — zero RFP issues mention
  "openlumen" or `SysAdminDoc`.
- `https://search.f-droid.org/?q=openlumen` — *"It looks like F-Droid
  does not have any apps matching your search string 'openlumen'."*

**Implication**: the "F-Droid release packaging" Now item in rev 4 is
actually the *initial* submission. Once C01 (real-device validation) and
C35/C36 (icon, screenshots) land, the maintainer files a single MR to
fdroiddata using the F-Droid Quick Start Guide
(`https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/` —
S206). No pre-existing MR to update.

The submission workflow as of 2026-05-17 is:

1. Fork `gitlab.com/fdroid/fdroiddata`.
2. Create `metadata/com.openlumen.yml` with build recipe.
3. Run `fdroid lint com.openlumen`.
4. Push, open MR labelled "New App."
5. Wait 24-48h post-merge for the repo to surface.

The `fastlane/metadata/android/en-US/` skeleton already exists in the
repo. **Post-pass update**: C35 is now shipped, so `icon.png` exists.
Remaining missing pieces are `phoneScreenshots/*.png` (C36),
`featureGraphic.png` (optional), and the release-bump
`changelogs/<versionCode>.txt`.

### 2. Three HIGH-severity correctness bugs the 2026-05-17 audit didn't catch

The code-review agent surfaced bugs the in-tree audit missed. These are
new roadmap candidates (C132-C140 range) — not features, but correctness
fixes the maintainer should land before any F-Droid submission. Full
analysis per file in the agent output; key findings here.

#### HIGH — `LumenService.applyMatrix` `transitionJob` swap is not atomic

[`app/.../service/LumenService.kt:411-432`](../../../app/src/main/java/com/openlumen/service/LumenService.kt#L411). `@Volatile` gives visibility, not atomicity. Two
concurrent callers (prefs collector + sensor flow) can interleave the
`prior.cancel(); prior.join(); transitionJob = launch{}` sequence and
produce a zombie ramp + lost ramp. Fix: wrap the ramp-scheduling block
in a dedicated `rampMutex.withLock { … }` (separate from `applyMutex`
to avoid blocking apply during cancel-and-join).

#### HIGH — `LumenService.clearAndStop` doesn't cancel the active ramp

[`app/.../service/LumenService.kt:312-318`](../../../app/src/main/java/com/openlumen/service/LumenService.kt#L312). When the user toggles off
mid-ramp, the still-running `transitionJob` keeps calling `applyOnce`
on top of the just-cleared engine until `lifecycleScope` is torn down.
The screen flickers back to tinted briefly. Fix: cancel and join
`transitionJob` before calling `engine?.clear()` in `clearAndStop`.

#### HIGH — `ColorDisplayManagerEngine.load` cache poisoning on partial failure

[`core-engine/.../ColorDisplayManagerEngine.kt:86-107`](../../../core-engine/src/main/java/com/openlumen/engine/engines/ColorDisplayManagerEngine.kt#L86). On the cache-
hit path: if `cdm != null` but reflective lookup of `setActivated`
fails, the function returns `null` but does not invalidate `cdm`. A
transient class-load failure on first call cached a stale `cdm`
instance that gets re-checked forever, dooming the CDM engine path for
the lifetime of the process. Fix: when returning `null` after a cache-
hit check, also `cdm = null`.

#### HIGH — `OverlayEngine.installView` bypasses `applyMutex`

[`core-engine/.../engines/OverlayEngine.kt:108-130`](../../../core-engine/src/main/java/com/openlumen/engine/engines/OverlayEngine.kt#L108). `installView()` (public,
non-suspend) and `apply()` (suspend, under `applyMutex`) can race during
an engine swap. Rapid toggling between Auto-CDM and Auto-Overlay can
interleave `installView` with `clear`, causing the volatile `hostView`
read to race against `removeViewImmediate`. Fix: either route
`installView` through `applyMutex`, or add a private internal
`installMutex` to the engine.

### 3. Medium-severity issues

| File | Issue | Severity | Lines |
|---|---|---|---|
| `PreferencesStore.kt` | `MAX_IMPORT_BYTES` counts UTF-16 chars not bytes — 64K-char ASCII fits, but high-BMP padding can push actual byte count to ~128 KB | Med | [:113](../../../core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt#L113) |
| `PreferencesStore.kt` | `allowSpecialFloatingPointValues = true` is shared between read and write Json; NaN/Inf can leak into exported profiles for any future field that sanitize doesn't cover | Med | [:37](../../../core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt#L37) |
| `PreferencesStore.kt` | `sanitizeProfiles` silently dedupes profile names on import with no user feedback | Med (UX) | [:221](../../../core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt#L221) |
| `LumenService.kt` | Light-sensor listener may not unregister cleanly on `onDestroy` if cancel-without-join races teardown | Med | [:583-610](../../../app/src/main/java/com/openlumen/service/LumenService.kt#L583) |
| `Schedule.kt` | `isActiveUntilAlarm` has a missing unit test for "alarm before today's start, now between alarm and start" case | Med | [:85-96](../../../core-schedule/src/main/java/com/openlumen/schedule/Schedule.kt#L85) |
| `SurfaceFlingerEngine.kt` | `apply` ignores `Su.runCommand` exit code; OTA-driven SF code drift produces silent failure (UI says "filter on," screen doesn't change) | Med | [:62-65](../../../core-engine/src/main/java/com/openlumen/engine/engines/SurfaceFlingerEngine.kt#L62) |
| `SurfaceFlingerEngine.kt` | `isAvailable` probe with IDENTITY matrix can be a false positive — some builds accept the call but dispatch to an unrelated transaction | Med | [:50](../../../core-engine/src/main/java/com/openlumen/engine/engines/SurfaceFlingerEngine.kt#L50) |
| `KcalEngine.kt` | `apply` and `clear` drop `Su.runShell` exit codes; non-zero (su revoked, sysfs gone) silently no-ops | Med | [:80-86](../../../core-engine/src/main/java/com/openlumen/engine/engines/KcalEngine.kt#L80) |
| `Su.kt` | `OutputStreamWriter` FD leak window on timeout if coroutine cancellation aborts the `use` block before its finally | Med | [:81-90](../../../core-engine/src/main/java/com/openlumen/engine/engines/Su.kt#L81) |
| `ColorDisplayManagerEngine.kt` | If `setTemperature` is null, `apply` silently activates Night Display with stale OS-managed value — user's matrix is ignored | Med | [:60-61](../../../core-engine/src/main/java/com/openlumen/engine/engines/ColorDisplayManagerEngine.kt#L60) |

### 4. Low-severity hardening / cleanup opportunities

(Not enumerated individually — see code-review agent transcript for the
file-by-file list. Highlights: `Su.id` stdout `contains("uid=0")` matches
false positives; per-call `ByteBuffer.allocate(4)` in SF apply is
wasteful — use `floatToRawIntBits`; KCAL `*256f` clamps to 256 which
some kernels reject; SolarCalculator `(ut * 3600).toLong()` rounds toward
zero instead of nearest.)

### 5. Shizuku integration code shapes (now sourced)

The F-Droid/Shizuku research agent retrieved concrete Kotlin patterns
for C06 (Shizuku backend). Key takeaways for the spike:

- **Canonical availability + permission flow** uses
  `Shizuku.pingBinder()` + `Shizuku.isPreV11()` +
  `Shizuku.checkSelfPermission()` (S212, S213).
- **Survive Shizuku service restarts** by binding to
  `OnBinderReceivedListenerSticky` + `OnBinderDeadListener` rather than
  a stale binder reference (S212).
- **System service binding** uses
  `IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity")))`
  — every transact() flows through the Shizuku server with shell UID.
- **Foreground-task detection** uses
  `IActivityTaskManager.registerTaskStackListener(...)` (S215, S217)
  rather than `UsageStats` or `AccessibilityService`. Reference impl
  in `WuDi-ZhanShen/Android-FPS-Watcher`.
- **No GHSA entries against Shizuku** as of 2026-05-17 (S224 — zero
  results on GitHub Advisory).
- **FabricatedOverlay constraint we missed**: per the
  `zacharee/FabricateOverlay` README (S223), *"Android 12L has patched
  the ability to create fabricated overlays as the shell user. Only
  system apps and root can create fabricated overlays now."*
  **Implication for C128 (rev 4 new candidate)**: Shizuku-in-ADB-mode
  cannot create new FabricatedOverlays on 12L+; only Shizuku-on-root
  or Sui can. This makes C128 a root-tier engine, not a
  Shizuku-not-root one. **Tier downgrade**: from Under Consideration
  to Later, or merge into the C06 spike scope.

### 6. Concrete AGP 9 + Compose BOM migration targets

| Artifact | Current (`libs.versions.toml`) | Target (rev 4 follow-up) | Source |
|---|---|---|---|
| Compose BOM | `2024.12.01` | `2026.05.00` | S225 |
| Material 3 | `1.3.1` | `1.4.0` (no breaking changes vs 1.3.1) | S227 |
| Material 3 Expressive | not present | **alpha only** (`1.5.0-alpha19`); do NOT adopt yet | S227 |
| Compose core | implicit via BOM | `1.11.1` | S226 |
| AGP | `8.7.3` | `9.2.0` (Apr 2026 release) | S208 |
| JDK target | 17 | keep 17 minimum; consider 21 (AGP 9 recommended) | S208 |
| `material-icons-extended` | present | **deprecated**; migrate to Material Symbols / vector drawables | S229 |

`material-icons-extended` deprecation is **new news to rev 4** and
implies a small follow-up: the dozen icons OpenLumen uses need to
migrate to `Icons.AutoMirrored.Filled.*` or self-hosted vectors. Tracked
as new candidate C137.

## New candidates from second pass (proposed for ROADMAP)

| ID | Candidate | Tier | I/E/R | Why now |
|---|---|---|---:|---|
| C132 | `LumenService.applyMatrix` ramp-scheduling atomicity fix | Now | 4/2/2 | HIGH-severity race condition surfaced in second-pass code review; must land before F-Droid submission. |
| C133 | `LumenService.clearAndStop` cancel-and-join `transitionJob` | Now | 4/1/1 | HIGH-severity user-visible flicker; trivial fix. |
| C134 | `ColorDisplayManagerEngine.load` cache invalidation on partial-failure path | Now | 4/1/1 | HIGH-severity dooms CDM engine for process lifetime on transient reflective failure. |
| C135 | `OverlayEngine.installView` thread-safety with `apply`/`clear` | Now | 3/2/2 | HIGH-severity race during engine swap with overlay engine path. |
| C136 | `SurfaceFlingerEngine`/`KcalEngine` exit-code checking + cache invalidation on regression | Now | 4/2/1 | Med-severity silent-failure surface; user sees "filter on" but no tint after an OTA. |
| C137 | `material-icons-extended` deprecation migration | Next | 2/2/1 | New evidence (S229) — migrate to Material Symbols / vector drawables. |
| C138 | `PreferencesStore` import-size cap byte-correctness | Next | 3/1/1 | Med-severity input-validation bug; trivial to fix at InputStream level. |
| C139 | `PreferencesStore` import duplicate-name UI feedback (`droppedDuplicateNames` in `ImportSummary`) | Shipped 2026-05-17 | 2/2/1 | Med (UX) — silent dedupe surprised users; S00r records implementation. |
| C140 | F-Droid initial submission (RFP optional, direct MR to fdroiddata) | Now | 5/2/2 | First-time submission, no prior MR; gates the entire F-Droid distribution. Sources: S203-S211. |

## Tier shifts for existing candidates (second pass)

| ID | Candidate | Current Tier | Proposed Tier | Reason |
|---|---|---|---|---|
| C128 | FabricatedOverlay engine spike | Under Consideration (rev 4) | Later (or merge into C06 root-tier scope) | New evidence (S223): Shizuku-in-ADB cannot create FabricatedOverlays on Android 12L+. The "Shizuku-not-root" framing in rev 4 was wrong. |

## Sources added (S203-S229)

Folded into `SOURCE_REGISTER.md`.

- **F-Droid submission status / process**: S203-S211 (10 entries)
- **Shizuku integration patterns**: S212-S221 (10 entries)
- **FabricatedOverlay 12L+ constraint**: S222-S223 (2 entries)
- **Shizuku security advisories (negative result)**: S224 (1 entry)
- **Compose BOM / Material 3 / AGP 9 / icons deprecation**: S225-S229
  (5 entries)

Total new source IDs this pass: 27.

## Doc / process follow-ups completed in this pass (vs. listed in rev 4)

| Follow-up | Status |
|---|---|
| Rename `docs/api-36-readiness.md` → `docs/android-17-readiness.md` + retitle body | **Done** |
| Bump `docs/research-watchlist.md` "Last review" header to 2026-05-17 | **Done** |
| Refresh `docs/health-evidence.md` Sources section (add S99-S102 + S158-S162) | **Done** |
| Extend `docs/threat-model.md` with MASVS-PRIVACY section | **Done** |
| Record protobuf-java CVE-2024-7254 in `docs/sbom-and-advisories.md` "Accepted exposures" | **Done** |
| Expand `permissions-audit` CI grep to include location / phone / usage / a11y | **Done** (both `ci.yml` and `release.yml`) |
| Fold 2026-05-17 audit hardening into `CHANGELOG.md [Unreleased]` | **Done** |

All seven follow-ups from rev 4's "What changed in rev 4" list are done.

## What's still open for a third pass / future session

1. **Implement the C132-C136 correctness fixes.** Code review is done;
   actual Kotlin edits are the next maintainer commit. The fixes are
   small (each is ~5-30 lines of Kotlin).
2. **C140 — file the F-Droid MR.** Blocked on C01 (real-device
   validation rows) and C36 (screenshots). C35 is now shipped.
3. **C137 — `material-icons-extended` migration.** Single PR; survey
   the dozen call sites and replace.
4. **Re-run code review after the C132-C136 fixes land**, to make sure
   the fixes don't introduce new race conditions.

## Files modified in this second pass

Root-level:

- `README.md` — updated link target for renamed `docs/android-17-
  readiness.md`.
- `CHANGELOG.md` — folded 2026-05-17 audit hardening into `[Unreleased]`;
  fixed reference to the renamed doc.
- `PROJECT_CONTEXT.md` — fixed reference to the renamed doc.
- `ROADMAP.md` — TBD in the rev 4.1 update that lands with this commit
  (new candidates C132-C140, tier shift for C128).

`docs/`:

- `docs/android-17-readiness.md` — created by `git mv` + body rewrite.
- `docs/research-watchlist.md` — header date bumped.
- `docs/health-evidence.md` — Sources section expanded.
- `docs/threat-model.md` — MASVS-PRIVACY section added.
- `docs/sbom-and-advisories.md` — "Accepted exposures" table entry
  added for protobuf-java CVE.

`.github/workflows/`:

- `.github/workflows/ci.yml` — expanded `permissions-audit` grep.
- `.github/workflows/release.yml` — matching grep expansion.

`.ai/research/2026-05-17/`:

- `SOURCE_REGISTER.md` — extended with S203-S229.
- `SECOND_PASS_FINDINGS.md` (this file).
- `CHANGESET_SUMMARY.md` — updated for second pass.

`docs/api-36-readiness.md` — **renamed** via `git mv` to
`docs/android-17-readiness.md` (history preserved).

## What's NOT modified

- Kotlin source. Code-review findings are documented but the actual
  fixes are deferred to the next maintainer commit. The pre-existing
  unstaged audit hardening (15 Kotlin/test/strings files) remains
  unstaged.
- `gradle/libs.versions.toml`. The Compose BOM / AGP 9 / Material 3
  bumps are documented as concrete targets in `SECURITY_AND_DEPENDENCY_REVIEW.md`
  but are deferred to the C95 migration PR.
- `app/build.gradle.kts`. No build-config changes.
