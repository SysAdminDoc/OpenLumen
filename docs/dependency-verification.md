# Gradle Dependency Verification

> Tied to roadmap candidate **C48** (Gradle dependency verification).
>
> This document is the **procedure**, not the artifact. The
> `gradle/verification-metadata.xml` file is intentionally not checked
> in yet — see "Why opt-in, not enforced today" below.

Gradle dependency verification ensures every downloaded artifact
(transitive deps, plugins, the wrapper itself) matches a checksum
recorded in `gradle/verification-metadata.xml`. Once enabled, a
tampered or unexpected dependency fails the build before any Kotlin
compiles.

## Why opt-in, not enforced today

OpenLumen has 200+ transitive dependencies across Compose, Hilt,
DataStore, kotlinx-serialization, and the Android Gradle Plugin
itself. Generating a clean lockfile that survives the next routine
Dependabot update is non-trivial:

- Every AGP or Compose BOM bump invalidates dozens of hashes.
- Gradle's metadata generation includes signature checks that fail
  loudly on plugins published without signatures.
- Locking down `androidx.*` artifacts requires regenerating after
  every Compose BOM rev.

Enabling verification globally today would mean every Dependabot PR
fails until a maintainer regenerates the lockfile, which would push
maintainers toward auto-merging the regenerated lockfile alongside
the upgrade. That defeats the point.

The plan is:

1. **Document the procedure** (this file).
2. **Wait for the dependency surface to stabilize** (after the AGP 9
   migration spike, C95).
3. **Generate a verified metadata** and check it in alongside the
   first stable post-AGP-9 release.
4. **Add a CI job** that regenerates and diffs the lockfile to catch
   surprise additions before they ship.

## Generating the verification metadata locally

When you're ready (per step 3 above):

```bash
# 1. Clean checkout. Don't run this against a dirty Gradle cache —
#    cached artifacts may have stale signatures.
git stash
./gradlew --stop
rm -rf ~/.gradle/caches

# 2. Generate the metadata. The 'sha256,pgp' line below records both
#    a SHA-256 hash and the PGP signature where available.
./gradlew --write-verification-metadata sha256,pgp \
    :app:assembleRelease \
    :app:lint \
    :core-engine:test \
    :core-schedule:test \
    :core-prefs:test

# 3. Inspect gradle/verification-metadata.xml. Look for:
#    - components without a sha256 — should be rare; investigate each
#    - components with only-trusted-keys entries — preserve
#    - components flagged as "unverified" — must be triaged before commit

# 4. Run a sanity assemble against the new metadata.
./gradlew :app:assembleDebug
```

The first generation produces ~1000 lines of XML. Subsequent
regenerations should produce diff-shaped output — only the changed
artifacts.

## Updating after a Dependabot PR

Once verification is enabled, every Dependabot PR will fail CI on the
verification check. The fix:

```bash
git fetch origin
git checkout dependabot/<branch>
./gradlew --write-verification-metadata sha256,pgp \
    :app:assembleDebug --refresh-keys
git add gradle/verification-metadata.xml
git commit -m "ci: refresh dependency verification metadata"
git push
```

Then re-request CI. The Dependabot PR auto-merges its branch into
ours when it sees the verification update.

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

## Until this lands

The release procedure already documents a "dependency review" step
that asks maintainers to check Dependabot's open PRs and read
release notes before cutting a tag (see
`docs/release-checklist.md`). Manual review is the interim control.
