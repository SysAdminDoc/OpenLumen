# Research — OpenLumen

## Executive Summary
OpenLumen is a GPL-3.0 Android display-filter app whose strongest current shape is a privacy-literal, no-INTERNET, F-Droid-oriented tool with multiple runtime display backends: rootless `ColorDisplayManager`, rooted `SurfaceFlinger`, rooted `KCAL`, and overlay fallback. The remaining high-value work is release trust rather than another large feature: enforce signed release artifacts, replace stale GitHub Actions/Dependabot assumptions with local proof, make dependency review executable, and add automated copy guardrails so health/accessibility claims stay evidence-based. Top opportunities: fail unsigned releases by default; build one local release gate for no-INTERNET, dependency verification, SBOM/advisory, screenshots, and signature checks; remove workflow references from release docs; implement the missing dependency-update command path; add health-claim lint; keep Android 17/developer-verification work in the existing blocked/release tracks; avoid AccessibilityService and cloud features.

## Product Map
- Core workflows: choose or tune a tint preset, select the best available engine, schedule activation by fixed time/solar/until-alarm, control via QS/widget/notification/automation, export/import preferences and collect driver reports.
- User personas: privacy-first F-Droid users, rooted display-control power users, no-root ADB/CDM users, PWM-sensitive users needing fine dim control, packagers/reviewers who need reproducible local proof.
- Platforms and distribution: Android 8.0+ (`minSdk 26`, `targetSdk 35`, `compileSdk 37`), Kotlin 2.3.21/AGP 9.2.1/Compose, GitHub/direct APK today, F-Droid planned, Play optional and blocked on account/package registration.
- Key integrations and data flows: DataStore preferences, direct-boot mirror, Glance widgets, local broadcasts with `com.openlumen.permission.AUTOMATION`, no network APIs, Fastlane metadata in `fastlane/metadata/android/`.

## Competitive Landscape
- Red Moon: established F-Droid overlay baseline with schedules, profiles, excluded apps, widgets/tiles, translations, and a long issue history; learn from its profile/exclusion UX and community demand, avoid its unmaintained state and overlay-only ceiling.
- Twilight / CF.lumen / f.lux: commercial/proprietary baselines show users value smooth solar schedules, warm/dim tradeoff controls, root/system-level transforms, and Wear OS; avoid their broad sleep-health marketing and any network/account dependency in the main app.
- Grayscaler and DarQ: demonstrate Shizuku/root paths for per-app behavior and system settings without a blanket AccessibilityService; learn from their per-app and lock-state demand, avoid redraw/freezing and accessibility overreach.
- ColorBlendr / Iconify: system-customization apps show root/Shizuku/FabricatedOverlay patterns, ROM-specific diagnostics, clean uninstall guidance, and active translation/community loops; avoid promising OEM behavior that depends on ROM internals.
- BlueLightFIlter, ScreenColorControl, BrightnessFix: root/kernel display tools prove demand for compositor/KCAL-level output and rollback instructions; avoid making root-only or hardware-specific behavior the default UX.
- Redshift / LightBulb / desktop f.lux: adjacent mature tools show deterministic schedules, CLI/config surfaces, and local-first operation; OpenLumen already matches the local-first constraint and should borrow only release/config discipline.
- F-Droid and Shizuku ecosystems: distribution trust, reproducible builds, metadata hygiene, and Shizuku as a root alternative are table-stakes for this project; avoid docs that describe remote CI controls the repo no longer has.

## Security, Privacy, and Reliability
- Verified: `app/src/main/AndroidManifest.xml` contains no `INTERNET`; special permissions are deliberate (`SYSTEM_ALERT_WINDOW`, `WRITE_SECURE_SETTINGS`, exact alarms, `FOREGROUND_SERVICE_SPECIAL_USE`, Advanced Protection query).
- Verified: ignored local artifacts (`openlumen-release.jks`, `keystore.properties`, `hs_err_pid*.log`, `replay_pid42108.log`) are not tracked, so there is no tracked signing-key incident in the current checkout.
- Risk: `app/build.gradle.kts` only applies `signingConfig` when `OPENLUMEN_KEYSTORE` is present, so `:app:assembleRelease` can silently produce an unsigned release despite `README.md` and `docs/release-checklist.md` requiring signed artifacts.
- Risk: `.github/` contains issue templates but no workflows, while `CONTRIBUTING.md`, `SECURITY.md`, `PROJECT_CONTEXT.md`, `docs/dependency-verification.md`, `docs/sbom-and-advisories.md`, `docs/release-checklist.md`, and `fastlane/README.md` still describe active CI, Dependabot, release, permissions-audit, SBOM, and attestation workflows.
- Risk: `docs/release-checklist.md` references `./gradlew dependencyUpdates`, but the Gradle Versions plugin/task is not configured.
- Missing guardrails: one local release command should prove no-INTERNET/no-GMS, strict dependency verification, lint/tests/screenshots, SBOM/advisory output, signed APK verification, and SHA-256 sums without relying on GitHub Actions.
- Recovery needs: existing emergency-off automation and root-transform clear paths are strong; release verification should keep exercising them through driver-report/device-matrix flows rather than adding another backend.

## Architecture Assessment
- Module boundaries are healthy: Android UI/service code stays in `app`, pure display math and engines stay in `core-engine`, preferences/migrations in `core-prefs`, and solar/offline schedule logic in `core-schedule`.
- Refactor candidates: release logic belongs in `tools/` or Gradle tasks instead of being split across stale docs; release signing should be an explicit Gradle contract in `app/build.gradle.kts`; advisory/SBOM generation should be local and reproducible.
- Test gaps: no test or release task asserts unsigned releases fail; no executable dependency-update review path exists; health-copy policy is documented in `docs/health-evidence.md` but not machine-checked across strings, Fastlane metadata, README, and docs.
- Documentation gaps: living docs still conflict on whether GitHub Actions/Dependabot exist; update docs to the local-build policy before the next release loop.
- Category coverage: security/reliability (release signing, workflow drift), accessibility/health (copy lint), i18n (Fastlane translations now exist; lint must scan locales), observability (driver report remains the right path), testing (local release gate), docs/distribution (F-Droid/reproducible build proof), offline/resilience (no network), upgrade strategy (dependency review command), plugin ecosystem/mobile/multi-user (covered by existing roadmap/blocked items, no duplicate additions).

## Rejected Ideas
- Add telemetry, remote crash reporting, smart-light sync, or cloud backup: conflicts with the no-INTERNET and F-Droid-first posture; commercial tools prove demand but not fit.
- Make AccessibilityService the default per-app backend: Android Advanced Protection and OWASP overlay/tapjacking guidance make Shizuku/root the better power-user path.
- Add Play-only release or GitHub Actions as the source of truth: current repo policy and checkout state require local builds, not remote CI.
- Add broad plugin ecosystem before Shizuku/per-app foundations: existing roadmap already covers Shizuku/FabricatedOverlay/Eye Dropper concepts; adding another ecosystem item would duplicate and dilute.
- Market OpenLumen as a sleep, migraine, PWM, or eye-strain treatment: Cochrane and current blue-light evidence do not support medical claims; frame as display comfort and user-controlled tint only.
- Add root-only hardware dimming as a default path: kernel/sysfs display controls are device-specific and riskier than OpenLumen's current ranked fallback model.

## Sources
Direct OSS and adjacent tools:
- https://github.com/LibreShift/red-moon
- https://github.com/LibreShift/red-moon/issues/150
- https://github.com/C10udburst/Grayscaler
- https://github.com/C10udburst/Grayscaler/issues
- https://github.com/KieronQuinn/DarQ
- https://github.com/Mahmud0808/ColorBlendr
- https://github.com/NoneBaiano/BlueLightFIlter
- https://github.com/SmartPack/ScreenColorControl
- https://github.com/rikkaapps/shizuku
- https://github.com/awesome-android-root/awesome-android-root
- https://github.com/timschneeb/changelog-awesome-shizuku

Commercial and community:
- https://play.google.com/store/apps/details?id=com.urbandroid.lux
- https://twilight.urbandroid.org/doc/
- https://play.google.com/store/apps/details?id=eu.chainfire.lumen
- https://justgetflux.com/
- https://www.reddit.com/r/Android/comments/4bcjcx/red_moon_an_open_source_alternative_of_twilight/
- https://www.reddit.com/r/androiddev/comments/p1qy40/can_i_globally_filter_the_display_content_to_only/
- https://www.reddit.com/r/PWM_Sensitive/comments/1azhpiw/does_anyone_use_oled_saver_app_on_android/

Android, F-Droid, security, dependencies, evidence:
- https://developer.android.com/about/versions/12/behavior-changes-all
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/developer-verification
- https://developer.android.com/privacy-and-security/risks/tapjacking
- https://developer.android.com/studio/publish/app-signing
- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://developer.android.com/jetpack/androidx/versions/all-channel
- https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/
- https://www.cochrane.org/about-us/news/blue-light-filtering-spectacles-probably-make-no-difference-eye-strain-eye-health-or-sleep

## Open Questions
None for implementation prioritization. External account ownership still blocks Android developer/package registration and belongs in `Roadmap_Blocked.md`.
