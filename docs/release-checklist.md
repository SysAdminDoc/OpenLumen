# Release Checklist

> Every release goes through this list. If a step doesn't apply, mark it
> N/A in the release issue — don't silently skip.

Tied to roadmap candidate **C45**.

## 1. Pre-flight (a week before tag)

- [ ] No open issues with the `release-blocker` label.
- [ ] Roadmap "Now" tier has no items still claimed for this release that
      aren't merged.
- [ ] CHANGELOG `[Unreleased]` section has at least one entry per merged PR
      that changed user-facing behavior.
- [ ] Dependency review: run `python tools/dependency_update_review.py`
      and inspect the official release-note URL, compatibility-risk note,
      required commands, and verification-metadata impact for every
      `update-available` entry. The command exits 1 if any reference is
      `unresolved`, which means the review could not be trusted rather than
      that an update is waiting. Resolve metadata failures and document any
      exact-version intentional hold in `tools/dependency_update_policy.json`.
      No transitive advisories newer than 30 days uncategorized.
- [ ] Artifact provenance cadence: for every published release retain the
      signed APK, Git tag, SHA-256 sums, SBOM, and advisory report together.
      If a GitHub Actions attestation workflow is enabled, attach its v4
      provenance bundle to the same release; this repository currently has no
      Actions workflow, so the local release gate must not be described as an
      automated attestation.
- [ ] Local release gate review: confirm `tools/local_release_gate.py`
      still covers strict dependency verification, manifest permission
      checks, release classpath checks, SBOM/advisory output, SHA-256 sums,
      signature verification, health-claim lint, tests, screenshot
      validation, and Roborazzi.
- [ ] On-device smoke run on the primary device (see `docs/device-matrix.md`
      for the per-engine checklist).

## 2. Prepare the release on main

```bash
git checkout main
git pull
```

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] Update `README.md` "Features (vX.Y.Z)" pointer.
- [ ] Move `CHANGELOG.md` `[Unreleased]` into a dated `[X.Y.Z] — YYYY-MM-DD`
      section.
- [ ] Delete any shipped items from `ROADMAP.md`; completed work belongs in
      git history and `CHANGELOG.md`.

## 3. Build verification and release gate (locally)

Run the full local release gate before tagging:

```bash
py -3 tools/local_release_gate.py
```

For local reproducibility or F-Droid rebuild checks without signing keys:

```bash
py -3 tools/local_release_gate.py --allow-unsigned-release
```

The gate runs strict dependency verification, debug/release builds, unit
tests, lint, screenshot validation, Roborazzi verification, merged-manifest
permission checks, release-classpath checks, SBOM and advisory output,
SHA-256 sums, and APK signature verification.

It refuses to report success on an empty result. A classpath that resolves
to zero dependencies fails, because that produces an SBOM with no packages
and an advisory scan that checks nothing. Any report it promises but leaves
empty fails too.

On the signed path it also pins the signing certificate. `apksigner`
verifying an APK proves it is signed, not that it is signed with this
project's key, so the gate compares the signer's SHA-256 against
`tools/release-signing-certificate.json`. If that file is absent the gate
fails and prints the observed fingerprint, so recording it is a deliberate
one-time step rather than something that can be skipped by accident.

- [ ] `./gradlew clean` — confirm a clean slate.
- [ ] `py -3 tools/local_release_gate.py` succeeds with signing
      environment set.
- [ ] `./gradlew :app:lint` has no new lint errors at severity `error`.
      Optional: `./gradlew :app:updateLintBaseline` once to capture
      pre-existing warnings into `app/lint-baseline.xml`, then add
      `lint { baseline = file("lint-baseline.xml") }` to
      `app/build.gradle.kts` so future runs surface only new findings.
      Not done yet; tracked as informal maintainer follow-up rather
      than a roadmap candidate.
- [ ] Manual install on at least one device. App opens, the toggle works,
      the schedule fires within 2 minutes when set to a near-future time.

## 4. Permission proof (no-INTERNET assertion)

The local release gate runs this automatically, but this command is a
manual spot check when investigating failures:

```bash
./gradlew :app:assembleDebug
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk \
    | grep -i internet
# Expected: no output.
```

- [ ] Confirmed: `INTERNET` is not in the merged manifest.
- [ ] Confirmed: `ACCESS_NETWORK_STATE` is not in the merged manifest.
- [ ] Confirmed: no Play Services, Firebase, or GMS dependency in
      `build/reports/openlumen-release-gate/releaseRuntimeClasspath.txt`.

## 5. Signed release build

```bash
export OPENLUMEN_KEYSTORE=/path/to/release.jks
export OPENLUMEN_KEYSTORE_PASSWORD=...
export OPENLUMEN_KEY_ALIAS=openlumen
export OPENLUMEN_KEY_PASSWORD=...
py -3 tools/local_release_gate.py
```

- [ ] `app/build/outputs/apk/release/app-release.apk` exists and is signed
      with v1, v2, and v3 signatures (`apksigner verify -v` to confirm).
- [ ] Release builds without the full `OPENLUMEN_*` signing environment fail
      unless `-Popenlumen.allowUnsignedRelease=true` is passed explicitly.
      Use that override only for local reproducibility or F-Droid rebuild
      checks, never for a published release artifact.
- [ ] APK size delta vs previous release is justifiable (a sudden +1 MB
      means a dependency landed; investigate before shipping).

## 6. Reproducibility checks

See `docs/reproducible-build.md` for the full procedure. Quick version:

- [ ] Build twice in clean checkouts, identical environment. SHA-256 of the
      APK matches.
- [ ] Recorded build environment: JDK version, AGP version, Gradle version,
      OS, locale.
- [ ] If reproducibility broke, do **not** ship until the cause is
      identified (timestamps in resources, non-deterministic codegen, etc).

## 7. Tag and push

```bash
git commit -am "release: v$VERSION"
git tag -a v$VERSION -m "OpenLumen v$VERSION"
git push origin main
git push origin v$VERSION
```

- [ ] Confirm GitHub serves the pushed tag and README content.

## 8. Create the GitHub release locally

Use the locally built signed APK and release-gate reports:

```bash
gh release create v$VERSION \
    app/build/outputs/apk/release/app-release.apk \
    build/reports/openlumen-release-gate/SHA256SUMS \
    build/reports/openlumen-release-gate/sbom.spdx.json \
    build/reports/openlumen-release-gate/advisory-report.json \
    --title "OpenLumen v$VERSION" \
    --notes-file /tmp/openlumen-release-notes.md
```

- [ ] Release on the Releases page has the signed APK, `SHA256SUMS`, SBOM,
      and advisory report.
- [ ] Manually inspect the SHA-256: it must match the one you computed in
      step 3.
- [ ] Release notes are pulled from CHANGELOG; do not paste raw
      diff stats.

## 9. Publish

- [ ] Flip the GitHub release from draft to published.
- [ ] (Optional) Push to F-Droid if metadata is ready. See
      `fastlane/metadata/android/`.
- [ ] If distributing outside Play after September 2026 enforcement begins,
      confirm `com.openlumen` is registered through the Android developer
      verification path. See ROADMAP C141.

## 10. Post-release

- [ ] `CHANGELOG.md`: open a new `[Unreleased]` section for the next cycle.
- [ ] `ROADMAP.md`: re-evaluate "Now" for the next release.
- [ ] Close release-blocker label.
- [ ] Watch the issue tracker for 48 hours for regressions.

## Rollback plan

If a release introduces a black-screen / stuck-tint / drained-battery
regression:

1. Mark the GitHub release as "Pre-release" (hides it from F-Droid index
   updates).
2. Open a release-blocker issue describing the symptom and affected devices.
3. Cut a `vX.Y.Z+1` hotfix from the previous tag, cherry-pick the fix only,
   and run this checklist again.
4. Never delete a published tag — it breaks F-Droid's reproducibility audit.

## Emergency off (for users hit by a bad release)

Document in release notes when a bad release is identified:

```bash
adb shell am broadcast -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.AutomationReceiver
```

Or boot into safe mode and uninstall the affected version.
