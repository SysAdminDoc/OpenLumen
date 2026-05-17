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
- [ ] Dependency review: `./gradlew dependencyUpdates` or read Dependabot
      open PRs. No transitive advisories newer than 30 days uncategorized.
- [ ] CI action review: no JavaScript action is on an obsolete major for
      the current GitHub runner Node line. See ROADMAP C142.
- [ ] On-device smoke run on the primary device (see `docs/device-matrix.md`
      for the per-engine checklist).

## 2. Cut the release branch

```bash
git checkout main
git pull
git checkout -b release/v$VERSION
```

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] Update `README.md` "Features (vX.Y.Z)" pointer.
- [ ] Move `CHANGELOG.md` `[Unreleased]` into a dated `[X.Y.Z] — YYYY-MM-DD`
      section.
- [ ] Update `ROADMAP.md` if any "Now" items shipped (move to a "Shipped"
      annotation, don't delete).

## 3. Build verification (locally)

- [ ] `./gradlew clean` — confirm a clean slate.
- [ ] `./gradlew assembleDebug` — must succeed.
- [ ] `./gradlew test` — all module tests pass.
- [ ] `./gradlew :app:lint` — no new lint errors at severity `error`.
- [ ] Manual install on at least one device. App opens, the toggle works,
      the schedule fires within 2 minutes when set to a near-future time.

## 4. Permission proof (no-INTERNET assertion)

CI runs this automatically, but verify before tagging:

```bash
./gradlew :app:assembleDebug
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk \
    | grep -i internet
# Expected: no output.
```

- [ ] Confirmed: `INTERNET` is not in the merged manifest.
- [ ] Confirmed: `ACCESS_NETWORK_STATE` is not in the merged manifest.
- [ ] Confirmed: no Play Services dependency in `app/build/dependencies/`.

## 5. Signed release build

```bash
export OPENLUMEN_KEYSTORE=/path/to/release.jks
export OPENLUMEN_KEYSTORE_PASSWORD=...
export OPENLUMEN_KEY_ALIAS=openlumen
export OPENLUMEN_KEY_PASSWORD=...
./gradlew clean :app:assembleRelease
```

- [ ] `app/build/outputs/apk/release/app-release.apk` exists and is signed
      with v1, v2, and v3 signatures (`apksigner verify -v` to confirm).
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
git push origin release/v$VERSION
git push origin v$VERSION
```

- [ ] Open the release PR back to `main`. Maintainer review only; no force
      push to `main`.

## 8. Run the Release workflow

GitHub → Actions → Release → "Run workflow" → enter `$VERSION`.

- [ ] Workflow succeeded.
- [ ] Draft release on the Releases page has the signed APK and
      `SHA256SUMS`.
- [ ] Manually inspect the SHA-256: it must match the one you computed in
      step 6.
- [ ] Edit the draft release notes. Pull from CHANGELOG; do not paste raw
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
adb shell am startservice -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.LumenService
```

Or boot into safe mode and uninstall the affected version.
