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
| AGP | 9.2.1 | C95 shipped 2026-05-17; Gradle wrapper is 9.4.1. AGP 10 remains a future watch item (S270). |
| Kotlin | 2.3.21 | AGP 9 built-in Kotlin compiles Android modules; Compose and serialization plugins remain explicit where needed. |
| KSP | 2.3.8 | Matches the post-C95 Kotlin/Hilt train and resolves from Maven Central (S273). |
| Compose BOM | 2026.05.00 | C144 shipped the stable Compose BOM refresh after the AGP 9 train (S225, S253, S281). |
| Compose compiler | Kotlin Compose plugin 2.3.21 | The stale standalone catalog version was removed; compose compiler is implied by the Kotlin Compose plugin. |
| Material 3 | 1.4.0 | C110 surveys Material 3 Expressive components when timing fits; C137 removed the deprecated `material-icons-extended` dependency. |
| Hilt | 2.59.2 | C124 shipped with the AGP 9 train (S274). |
| Hilt lifecycle-viewmodel-compose | 1.3.0 | C96 shipped; `hiltViewModel()` imports now use `androidx.hilt.lifecycle.viewmodel.compose` (S269). |
| Compose screenshot plugin | 0.0.1-alpha14 | C101 shipped an initial `@PreviewTest` fixture and CI validation. |
| DataStore | 1.2.1 | C144 shipped the stable floor; C28/C102 now uses it for the device-protected Direct Boot mirror (S252, S280, S00m). |
| kotlinx.serialization | 1.7.3 | No upgrade pressure. |
| kotlinx.coroutines | 1.9.0 | No upgrade pressure. |
| AndroidX core-ktx | 1.18.0 | C144 shipped the stable AndroidX baseline refresh after C95 (S239, S276). |
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
  (`gradle/actions/setup-gradle@v6` is the current post-C142 baseline).
- Verify the SBOM workflow's `assembleRelease` still produces the
  expected `releaseRuntimeClasspath`.
- Verify `permissions-audit` still passes.
- Verify `actions/attest@v4` still attests the new
  artifact format.

Risk: medium. Documented in rev 3 as I/E/R 4/3/3.

### PR 3 — Material 3 Expressive survey (C110)

C144 already moved the app to Compose BOM 2026.05.00 and Material 3
1.4.0. C110 remains only the separate decision on whether future stable
Material 3 Expressive components are worth adopting for FAB Menu /
ToggleButtons.

### PR 4 — DataStore 1.2 + Direct Boot restore (C28 / C102)

Status: shipped 2026-05-17.

- Uses the already-landed `datastore = "1.2.1"` floor.
- Adds `LockedBootReceiver` (separate from `BootReceiver`) for
  `LOCKED_BOOT_COMPLETED`.
- Mirrors enabled/active flags, selected engine, and last active tint
  matrix into device-protected DataStore.
- Engine availability degrades gracefully: CDM + Overlay work pre-unlock;
  SF + KCAL need `su` so degrade to Overlay until user unlock.

Risk: medium. The privacy-sensitive mitigation is that the direct-boot
mirror excludes schedule coordinates, profile names, saved profiles, and
the full preferences blob. Device pass/fail proof remains C01.

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
2026-06-02 (S242). C142 was implemented on 2026-05-17 and the workflow
baseline is now:

| Workflow dependency | Current | Current upstream signal | Action |
|---|---|---|---|
| `actions/checkout` | v6 | v6.0.2 observed (S258) | Done in C142 |
| `actions/setup-java` | v5 | v5.2.0 observed; Node 24 action line (S245, S259) | Done in C142 |
| `gradle/actions/setup-gradle` | v6 | v6.1.0 observed (S260) | Done in C142 |
| `actions/upload-artifact` | v7 | v7.0.1 observed; zipped uploads still default (S247, S261) | Done in C142 |
| `actions/attest` | v4 | v4.1.0 observed; direct provenance action (S262-S263) | Done in C142 |
| `anchore/sbom-action` | v0 | v0.24.0 current under same major (S250, S265) | Keep v0 but pin/check release notes |
| `anchore/scan-action` | v7 | v7.4.0 observed; Node 24 action line (S251, S264) | Done in C142 |

Policy decision: OpenLumen keeps current major-version tags for
Dependabot ergonomics. GitHub's secure-use docs say full SHA pinning is
the only immutable action reference (S243), so the documented exception
path is to use full SHAs for incident response, high-risk release
hardening, weak maintenance signals, or suspicious tag/release behavior.

### AGP / Gradle / Hilt dependency train

This train is now shipped. The repo uses AGP 9.2.1, Gradle 9.4.1,
Kotlin 2.3.21, KSP 2.3.8, Dagger/Hilt 2.59.2, and AndroidX Hilt
Compose 1.3.0. Dagger/Hilt 2.59.2 is still the right pairing because it
fixes AGP-9-era Hilt transform / incremental-build issues (S274).

Follow-up complete: C48 now checks in Gradle dependency verification
metadata generated after this train and the AndroidX baseline refresh.
Strict verification passed across assemble, lint, screenshot validation,
and unit-test tasks from the local mirror. The metadata includes ignored
PGP keys where public keys could not be downloaded from key servers; new
ignored-key entries should be reviewed during future dependency refreshes.

### AndroidX stable baseline refresh

C144 is now shipped. OpenLumen uses Activity Compose 1.13.0, core-ktx
1.18.0, Lifecycle 2.10.0, Navigation Compose 2.9.8, Compose BOM
2026.05.00, Material 3 1.4.0, and DataStore 1.2.1 (S239, S252-S253,
S275-S281, S00l). This was not a security fire, but it reduced future
forced-upgrade pressure and unlocked the Direct Boot work now recorded in
S00m.

### C123 Glance widget dependency

C123 is now shipped. OpenLumen adds
`androidx.glance:glance-appwidget:1.1.1`, the current stable Glance line
selected instead of the 1.2.0 release candidate (S193-S194). This does
not change the app's no-INTERNET posture or introduce Play Services /
Firebase dependencies. `gradle/verification-metadata.xml` was refreshed
for the new artifacts, and strict verification passed across assemble,
lint, screenshot validation, and unit tests from the local mirror (S00p,
S282).

### C138 import-size hardening

C138 is now shipped. `PreferencesStore` enforces its 64 KiB profile
import limit on raw stream bytes before UTF-8 decoding, closing the
local input-validation bug where multi-byte text could exceed the
intended byte budget while still passing a decoded-character count.
Focused unit coverage lives in `PreferencesImportReadTest`.

### C137 material icon dependency removal

C137 is now shipped. The app no longer declares
`androidx.compose.material:material-icons-extended`; its seven nav /
favorite icons are local vector resources loaded with `painterResource`.
This removes a deprecated Compose artifact from the app dependency graph
without mixing broader Compose BOM / Material 3 churn into the C95 train.

### C105 foreground-service start fallback

C105 is now shipped. The app centralizes foreground-service starts in
`LumenServiceStarter`, classifies Android's
`ForegroundServiceStartNotAllowedException`, and recovers user-initiated
QS/widget toggle-on attempts by rolling back stale state and opening the
app. Boot and schedule broadcasts still only log failures; they are not
user-visible entry points and should not try to launch UI from the
background.
