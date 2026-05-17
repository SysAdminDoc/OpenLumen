# Reproducible Build Notes

> Tied to roadmap candidate **C37** (Reproducible build notes).

OpenLumen targets reproducible release builds so an F-Droid build server, a
maintainer, and a paranoid auditor can all produce the same APK from the
same tag and get byte-identical output.

This document captures the environment and procedure. If you build a tag
twice and get a different APK, that's a bug — file it.

## Environment

| Tool | Pinned version | Where |
|---|---|---|
| JDK | Temurin 17 | `actions/setup-java@v5 with: distribution: temurin, java-version: "17"` |
| Android SDK | Build-tools 36, Platform 36 | Auto-installed by AGP for `compileSdk = 36` |
| Gradle | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 9.2.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.3.21 | `gradle/libs.versions.toml` |

All version-pinning happens in checked-in files. The Gradle wrapper
distribution SHA is in `gradle/wrapper/gradle-wrapper.properties`; the
Android Gradle Plugin is in `gradle/libs.versions.toml`; everything else
follows from those.

## How to build twice and compare

1. Clean checkout #1:
   ```bash
   git clone https://github.com/SysAdminDoc/OpenLumen.git lumen-a
   cd lumen-a
   git checkout v0.4.0   # or whatever tag you're auditing
   ./gradlew clean :app:assembleRelease
   sha256sum app/build/outputs/apk/release/app-release*.apk
   cd ..
   ```

2. Clean checkout #2 (different directory):
   ```bash
   git clone https://github.com/SysAdminDoc/OpenLumen.git lumen-b
   cd lumen-b
   git checkout v0.4.0
   ./gradlew clean :app:assembleRelease
   sha256sum app/build/outputs/apk/release/app-release*.apk
   ```

3. The two SHA-256 hashes should match. They won't currently match for
   signed builds because signing carries a timestamp — sign separately
   from `assembleRelease` if you want to compare unsigned outputs.

## Sources of non-reproducibility we've eliminated

- **Embedded build timestamps.** AGP no longer writes `Built-Date`
  timestamps into resources or DEX. We don't add any either.
- **AGP VCS metadata in the APK.** Release builds set
  `vcsInfo.include = false`, so AGP does not package
  `META-INF/version-control-info.textproto` with a local Git revision.
  F-Droid documents this file as a reproducible-build comparison pitfall
  when a reference APK was built before the final release tag. OpenLumen
  keeps provenance externally through Git tags, release SHA-256 sums, and
  `actions/attest` instead.
- **Locale-dependent string ordering.** Compose code does not iterate
  resource maps that depend on locale.
- **Random IDs.** No `UUID.randomUUID()` in build-time codegen paths.

## Sources of non-reproducibility worth knowing about

- **JDK minor version drift.** Building with Temurin 17.0.10 vs 17.0.11
  *should* produce identical output but is not formally guaranteed by
  AGP. Pin to a specific minor in `.github/workflows/release.yml` if you
  need byte-level reproducibility across CI runs.
- **`localProperties`.** Don't check in `local.properties`. The SDK path
  it contains is environment-specific.
- **Signing block.** Signed APKs differ from unsigned by the signature
  bytes, which include a timestamp. Compare unsigned APKs (or compare
  `apksigner verify -v --print-certs` outputs separately from the byte
  hash).
- **Building from a dirty tree.** F-Droid's preferred path is still to
  build from a clean, tagged commit. The VCS-info toggle removes one known
  APK payload drift source; it is not a substitute for tagging first and
  building the exact tag.

## CI provenance

`.github/workflows/release.yml` runs
`actions/attest@v4` for each release artifact. This
gives downstream consumers a way to verify "this APK was built by this
GitHub workflow run from this commit", which is a different (and
complementary) trust signal from byte-level reproducibility.

## Reporting a reproducibility failure

Open an issue with:

- The tag you built.
- The SHA-256 of both APKs.
- The `./gradlew --version` output from both environments.
- `java -version` from both environments.
- The `diffoscope` output if you have it (otherwise we'll run it).

Reproducibility is a release-blocking property. We treat regressions
seriously.
