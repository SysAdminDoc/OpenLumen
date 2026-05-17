# Gradle Dependency Verification

> Tied to roadmap candidate **C48** (Gradle dependency verification).
>
> Status: **enforced**. `gradle/verification-metadata.xml` is checked in
> after the AGP 9 migration and AndroidX baseline refresh, and the
> current Gradle suite passes with `--dependency-verification=strict`.

Gradle dependency verification ensures every downloaded artifact
(transitive deps, plugins, the wrapper itself) matches a checksum
recorded in `gradle/verification-metadata.xml`. A
tampered or unexpected dependency fails the build before any Kotlin
compiles.

## Current policy

- `gradle/verification-metadata.xml` is source-controlled and should be
  reviewed like source code.
- The file records SHA-256 checksums and PGP signature metadata where
  Gradle could retrieve it.
- The initial enforced metadata was generated after C95 (AGP 9) and
  C144 (AndroidX stable baseline refresh) so routine toolchain churn did
  not immediately invalidate the lockfile.
- The metadata currently includes ignored PGP keys whose public keys
  could not be downloaded from any key server during generation. Keep
  those entries only when the artifact checksum is expected and the
  dependency/source is otherwise known.
- CI and release maintainers should keep verification in strict mode.

## Refreshing the verification metadata

Use a clean checkout and review the XML diff before committing:

```bash
# 1. Clean checkout. Don't run this against a dirty Gradle cache.
git stash
./gradlew --stop
rm -rf ~/.gradle/caches

# 2. Generate metadata for the release and validation surfaces.
./gradlew --write-verification-metadata sha256,pgp \
    :app:assembleDebug \
    :app:assembleRelease \
    :app:lintDebug \
    :app:validateDebugScreenshotTest \
    :app:verifyRoborazziDebug \
    :app:testDebugUnitTest \
    :core-engine:test \
    :core-schedule:test \
    :core-prefs:test

# 3. Prove the refreshed metadata is usable in strict mode.
./gradlew --dependency-verification=strict \
    :app:assembleDebug \
    :app:lintDebug \
    :app:validateDebugScreenshotTest \
    :app:verifyRoborazziDebug \
    :app:testDebugUnitTest \
    :core-engine:test \
    :core-schedule:test \
    :core-prefs:test
```

The first generation produces ~1000 lines of XML. Subsequent
regenerations should produce diff-shaped output — only the changed
artifacts.

Review `gradle/verification-metadata.xml` for:

- components without a SHA-256 checksum — investigate each one;
- new ignored PGP keys — verify why Gradle could not fetch the key;
- unexpected new groups or artifacts — compare to the dependency PR;
- any entry marked unverified — triage before commit.

## Updating after a Dependabot PR

Dependency PRs that add or upgrade artifacts will usually need a metadata
refresh:

```bash
git fetch origin
git checkout dependabot/<branch>
./gradlew --write-verification-metadata sha256,pgp \
    :app:assembleDebug \
    :app:lintDebug \
    :app:testDebugUnitTest \
    --refresh-keys
./gradlew --dependency-verification=strict \
    :app:assembleDebug \
    :app:lintDebug \
    :app:testDebugUnitTest
git add gradle/verification-metadata.xml
git commit -m "ci: refresh dependency verification metadata"
git push
```

Then re-request CI. Do not auto-merge a dependency bump just because the
metadata can be regenerated; still read the release notes and inspect the
new artifacts.

## Failure modes and what they mean

| Symptom | Cause | Fix |
|---|---|---|
| `Dependency verification failed for org.foo:bar:1.2.3` | Hash in metadata doesn't match the resolved artifact | Regenerate metadata; investigate why the artifact changed without a version bump |
| `No checksum found for org.foo:bar:1.2.3` | Artifact was added (often transitively) without metadata | Regenerate or add an explicit `<trusted-artifacts>` entry |
| Signature verification failed for AGP plugin | AGP or its sub-plugins changed their signing key | Compare the new key fingerprint to the published one on `developer.android.com`; update `<pgp-keys>` block if legitimate |
| Gradle wrapper itself fails | Wrapper distribution hash changed | Check `gradle/wrapper/gradle-wrapper.properties` against `services.gradle.org/distributions/` — never silently bump |

## Related controls already in place

- The CI `permissions-audit` job (in `.github/workflows/ci.yml`)
  rejects `INTERNET`, `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE`
  in the merged manifest, plus any `play-services`, `firebase`, or
  `com.google.android.gms` artifact in the release classpath. That's
  category guard, not artifact integrity, so it complements rather
  than replaces dependency verification.
- The `Release` workflow runs
  `actions/attest@v4` for each release APK, which is build-side
  provenance (separate from this artifact-side verification).
- The SBOM workflow (`.github/workflows/sbom.yml`) produces a
  SPDX-format dependency manifest on every release and weekly schedule,
  and runs an advisory scan against it.

## Manual review still required

The release procedure already documents a "dependency review" step
that asks maintainers to check Dependabot's open PRs and read
release notes before cutting a tag (see
`docs/release-checklist.md`). Dependency verification proves artifact
identity; it does not prove that a legitimate new version is safe.
