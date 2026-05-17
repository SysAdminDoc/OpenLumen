# Security and Dependency Review — 2026-05-17

Dependency-upgrade opportunities and security hardening ideas surfaced in
this research pass. Anchors to `gradle/libs.versions.toml` plus the rev 3
roadmap's security candidates (C38, C47-C51, C73-C79, C90, C94, C104,
C111, C121).

## Current dependency floor

From [gradle/libs.versions.toml](../../../gradle/libs.versions.toml) on
`main`:

| Dep | Current | Notes |
|---|---|---|
| AGP | 8.7.3 | C95 (Now): migrate to AGP 9.2.0 / Gradle 9.4.1; AGP 10 mid-2026 removes the AGP 8 opt-out paths (S140-S143, S237-S238). |
| Kotlin | 2.1.0 | Pair the Kotlin/KSP bump with C95; AGP 9.x and newer Hilt lines assume a newer toolchain. |
| KSP | 2.1.0-1.0.29 | Tracks Kotlin today; rev 5 notes KSP's newer lines decouple from old Kotlin-tied versioning, but do this with C95. |
| Compose BOM | 2024.12.01 | Concrete rev 4.1 target: 2026.05.00; rev 5 AndroidX table confirms compose 1.11.1 / material3 1.4.0 stable (S225, S239, S253). |
| Compose compiler | 1.5.15 | Compose Compiler plugin from Kotlin Compose plugin (`kotlin.plugin.compose`); compose compiler version is implied by the plugin. |
| Material 3 | 1.3.1 | C110 surveys Material 3 Expressive components when timing fits. |
| Hilt | 2.53.1 | C124 target is now Hilt 2.59.2, but it is AGP-9-coupled; do not land independently before C95 (S240-S241). |
| Hilt navigation-compose | 1.2.0 | C96: move `hiltViewModel()` to `androidx.hilt:hilt-lifecycle-viewmodel-compose` (1.3.0-stable Sep 2025; S144-S145). |
| DataStore | 1.1.1 | C28 / C102: use stable 1.2.1 / Direct Boot APIs (`deviceProtectedDataStore()`, S252). |
| kotlinx.serialization | 1.7.3 | No upgrade pressure. |
| kotlinx.coroutines | 1.9.0 | No upgrade pressure. |
| AndroidX core-ktx | 1.15.0 | Rev 5 C144 batches stable AndroidX refresh after C95; current stable core is 1.18.0 (S239). |
| JUnit 4 | 4.13.2 | OK; could move to JUnit 5 but not urgent. |
| Truth | 1.4.4 | OK. |

## Active CVEs / advisories

Per [docs/sbom-and-advisories.md](../../../docs/sbom-and-advisories.md) the
SBOM workflow runs `anchore/sbom-action@v0` weekly Mondays plus on every
release, with `fail-build: false` and `severity-cutoff: medium`. The
"Accepted exposures" section now records protobuf-java CVE-2024-7254.

Known surface:

- **protobuf-java CVE-2024-7254** — rev 3 calls this out at S77. Practical
  exposure for an offline display-tint app is zero (we don't deserialize
  attacker-controlled protobuf). Rev 4 should record this in
  `docs/sbom-and-advisories.md` under "Accepted exposures" so the scanner
  noise doesn't re-surface every Monday.

No new CVEs surfaced by this session's research that affect OpenLumen's
current dep set.

## Security ideas surfaced this session

### 1. MASVS v2.1.0 — MASVS-PRIVACY category coverage

**Evidence**: S192 — MASVS v2.1.0 added a MASVS-PRIVACY category. The
existing [docs/threat-model.md](../../../docs/threat-model.md) covers
STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, RESILIENCE but not
PRIVACY by name.

**Action**: extend `docs/threat-model.md` with a MASVS-PRIVACY section.
The substance is already covered (data inventory, permission inventory,
no telemetry, no PII in logs, redacted coordinates in driver report); the
category header is what's missing.

**Effort**: 1. **Risk**: 1. **Tier**: Now (cheap doc update).

### 2. AAPM detection and driver-report transparency

**Evidence**: S134, S135. `AdvancedProtectionManager` API on Android 17+.

**Action**: detect AAPM state and surface it in the driver report (new
candidate C130). Show a Driver-tab info card explaining
*"AAPM auto-revokes Accessibility-based features; OpenLumen does not use
Accessibility, so AAPM has no effect on OpenLumen."*

**Effort**: 1. **Risk**: 1. **Tier**: Now.

### 3. Overlay attack taxonomy refresh

**Evidence**: S188 (MASTG-KNOW-0022), S189 (MASTG-TEST-0035), S190
(MASWE-0056 tapjacking).

**Action**: cross-check `docs/threat-model.md` MASVS-PLATFORM section
against the MASTG-KNOW-0022 / MASTG-TEST-0035 wording. Our existing
mitigation (`TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE` +
`FLAG_NOT_FOCUSABLE`) is already documented; this is purely a citation
refresh.

**Effort**: 1. **Risk**: 1. **Tier**: Next (alongside the MASVS-PRIVACY
doc work).

### 4. Reflection-ladder defensive posture

**Evidence**: S178 (Nocturnal, archived Aug 2024 after macOS Sonoma broke
private gamma APIs).

**Action**: no code change. Reaffirm in `docs/ARCHITECTURE.md` (or
`docs/api-36-readiness.md` rename) that the `ColorDisplayManagerEngine`
reflection ladder *expects* drift and that test cases must include a
"reflection fails entirely" path. The current `runCatching` ladder is
defensive on purpose.

**Effort**: 0 (documentation only). **Tier**: opportunistic.

### 5. Shizuku service-binding session survival

**Evidence**: S181 (RootlessJamesDSP), S179 (LSFG-Android).

**Action**: feeds into C06 (Shizuku backend) design notes — the spike
should explicitly verify behavior on Shizuku service restarts (Shizuku
service can be killed by AAPM, the user, or system memory pressure).
LSFG-Android's "Shizuku off the hot path" pattern is the reference: use
Shizuku for foreground-task detection telemetry only, keep the actual
tint engine on whatever non-Shizuku path is available.

**Effort**: part of C06 spike. **Tier**: tracked under C06 (Next).

### 6. CI permissions-audit completeness

The current `permissions-audit` job in [.github/workflows/ci.yml](../../../.github/workflows/ci.yml)
greps for `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`. Worth
adding to the grep list (defensive):

- `ACCESS_BACKGROUND_LOCATION`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `READ_PHONE_STATE`
- `QUERY_ALL_PACKAGES`
- `PACKAGE_USAGE_STATS`
- `BIND_ACCESSIBILITY_SERVICE`

None of these should ever appear in the merged manifest. CI failing on
their accidental introduction is cheap insurance. **Effort**: 1.
**Risk**: 1. **Tier**: Now (security hygiene).

## Dependency-upgrade plan (rev 4)

Pair upgrades into single PRs to amortize the regression risk:

### PR 1 — Hilt 2.56+ + AndroidX Hilt artifact rename (C124 + C96)

- Bump `hilt = "2.56.x"` (latest stable).
- Bump `hilt-navigation-compose = "1.3.0"` (or latest) and rename to
  `hilt-lifecycle-viewmodel-compose` in the libs catalog.
- Update `hiltViewModel()` import sites in
  [app/.../ui/](../../../app/src/main/java/com/openlumen/ui/) screens.

Risk: low; pure import-rename + version bump. Hilt 2.56 supports KSP at
this version range.

### PR 2 — AGP 9.x migration (C95)

- AGP 9.0 → 9.1 → 9.2; Kotlin 2.1.0 → 2.2.10 (the version Compose Preview
  Screenshot Testing wants per S148).
- Verify all three GitHub Actions workflows still work
  (`gradle/actions/setup-gradle@v4` is already AGP-9-compatible).
- Verify the SBOM workflow's `assembleRelease` still produces the
  expected `releaseRuntimeClasspath`.
- Verify `permissions-audit` still passes.
- Verify `actions/attest-build-provenance@v2` still attests the new
  artifact format.

Risk: medium. Documented in rev 3 as I/E/R 4/3/3.

### PR 3 — Compose BOM bump + Material 3 survey (C110)

After PR 2 lands. Pick a 2025/2026 Compose BOM line, verify Material 3
Expressive components surface, decide whether to adopt FAB Menu /
ToggleButtons. Documented in rev 3 as C110 (Later) — only do this if
PR 2's AGP 9 work doesn't bring it in incidentally.

### PR 4 — DataStore 1.2 + Direct Boot restore (C28 / C102)

- Bump `datastore = "1.2.x"`.
- Build a `LockedBootCompletedReceiver` (separate from `BootReceiver`).
- Mirror `enabled` + active `engine` minima into
  `deviceProtectedDataStore()`.
- Engine availability degrades gracefully: CDM + Overlay work pre-unlock;
  SF + KCAL need `su` so degrade to Overlay until user unlock.

Risk: medium. Documented in rev 3 as I/E/R 3/3/2 (effort dropped from 4
since the DataStore API exists).

## Reproducibility checklist (informed by S154-S156)

For the F-Droid first cut:

1. **AGP `version-control-info.textproto`** is bundled into release APKs
   starting AGP 8.3 (S156). This breaks reproducibility because the
   textproto contains commit SHAs that vary by checkout. Disable
   recommendation:

   ```kotlin
   // app/build.gradle.kts
   androidComponents.onVariants { variant ->
       variant.outputs.forEach { /* see S156 recipe for the proper API */ }
   }
   ```

   The exact API depends on AGP version; the F-Droid forum thread (S156)
   has the working snippet. Tracked as **C120** in rev 3.

2. **APK signature copy after rebuild match** is supported on F-Droid's
   build server (S154). Plan: F-Droid maintainer builds unsigned, hashes
   match, server copies our v2+v3 signature blob across.

3. **Per-app reproducibility indicator** (S155) — once we ship to F-Droid,
   watch f-droid.org for the per-app reproducibility status; if it shows
   non-reproducible, file an issue with the discrepancy and triage.

## Dependabot grouping

Current `.github/dependabot.yml` config: weekly Gradle + GitHub Actions
PRs. Once Gradle dependency verification (C48 procedure) is enforced
(post-AGP 9), grouping needs review so the lockfile regenerates cleanly
on each Dependabot batch. Documented in
[docs/dependency-verification.md](../../../docs/dependency-verification.md).

## Triage of new sources for security relevance

| Source | Relevance | Action |
|---|---|---|
| S134 / S135 (AAPM) | High | C130 (Now) |
| S136 (AAPM Beta 2 piece) | Med | Already cited |
| S188-S192 (OWASP MASTG/MASVS) | High | Doc refresh, MASVS-PRIVACY section |
| S178 (Nocturnal archived) | Low | Reflection-ladder note in ARCHITECTURE.md |
| S163-S165 (Shizuku) | Med | Feeds into C06 design |
| S140-S143 (AGP) | High | Confirms C95 Now placement |
| S144-S145 (Hilt) | High | Confirms C96 / C124 Now placement |
| S146-S147 (DataStore Direct Boot) | High | Confirms C28 effort drop |
| S154-S157 (F-Droid policy) | Med | Reinforces existing candidates |
| S155 (RB visibility) | Med | New process item — watch indicator post-launch |
| S156 (textproto disable) | High | Direct fix for C120 |
| S158-S162 (sleep evidence) | Low security, high docs | C126 sources |
| S193-S194 (Glance stable) | Low security, high upgrade | C123 tier promotion |

## Rev 5 security / dependency update

### Android developer verification is a distribution-security gate

Android's 2026 developer verification program applies to apps installed
on certified Android devices in the first enforcement regions, even when
the app is distributed outside Play (S230-S232). For OpenLumen, this is
not a telemetry or Play Services issue; it is a package-ownership and
signing-certificate registration task. Track as **C141 (Now)** and keep
identity documents / account recovery material out of Git.

### GitHub Actions Node 24 and action-major rotation

GitHub's Node 20 deprecation starts affecting JavaScript actions on
2026-06-02 (S242). Current workflow majors in this repo are now behind
upstream on the core release path:

| Workflow dependency | Current | Current upstream signal | Action |
|---|---|---|---|
| `actions/checkout` | v4 | v6 current (S244) | Rotate in C142 |
| `actions/setup-java` | v4 | v5.2.0 current; Node 24 action line (S245) | Rotate in C142 |
| `gradle/actions/setup-gradle` | v4 | docs show v6 (S246) | Rotate in C142 |
| `actions/upload-artifact` | v4 | v7 adds optional unzipped artifacts (S247) | Consider only if useful |
| `actions/attest-build-provenance` | v2 | v4.1.0 current; new work should consider `actions/attest` (S248-S249) | Rotate in C142 |
| `anchore/sbom-action` | v0 | v0.24.0 current under same major (S250) | Keep v0 but pin/check release notes |
| `anchore/scan-action` | v6 | v7 current (S251) | Rotate in C142 |

Policy decision: `ci.yml` currently documents major-version tags for
Dependabot ergonomics. GitHub's secure-use docs say full SHA pinning is
the only immutable action reference (S243). C142 should either preserve
the major-tag policy explicitly or switch to full SHAs with version
comments and a rotation checklist.

### AGP / Gradle / Hilt dependency train

AGP 9.2.0 requires Gradle 9.4.1 and Build Tools 36.0.0 (S237). Dagger /
Hilt 2.59.2 is current, but the Hilt Gradle plugin line now assumes AGP
9, and 2.59.2 specifically fixes AGP-9-era Hilt transform / incremental
build issues (S240-S241). That changes the rev 4 guidance:

- Do **not** treat Hilt 2.56+ as a standalone low-risk pre-AGP-9 bump.
- Land C95 first or in the same branch: AGP 9.2.0, Gradle 9.4.1,
  SDK/build-tools update, CI validation.
- Then land C96/C124: AndroidX Hilt artifact rename and Hilt 2.59.2.

### AndroidX stable baseline drift

AndroidX current stable versions as of the rev 5 pass are materially
newer than OpenLumen's floor: activity 1.13.0, core 1.18.0, lifecycle
2.10.0, navigation 2.9.8, compose 1.11.1, material3 1.4.0, and
DataStore 1.2.1 (S239, S252-S253). Track as **C144 (Next)** after the
toolchain migration; this is not a security fire, but it reduces
future forced-upgrade pressure and unlocks Direct Boot restore work on
stable DataStore APIs.
