# Research - OpenLumen

## Executive Summary

OpenLumen is a GPL-3.0 Android display-filter app with a stronger technical shape than the current open-source Android screen-filter field: it combines a no-INTERNET trust posture, Kotlin/Compose UI, F-Droid-ready metadata, and four runtime-selected display engines (`ColorDisplayManager`, `SurfaceFlinger`, `KCAL`, and `Overlay`). The highest-value direction is still release trust: fill real device evidence, preserve recovery paths, and keep platform dependencies current without breaking the direct-boot/Glance fixes. Top opportunities: preserve the WorkManager lazy-init guard while upgrading the stale `2.7.1` pin, surface exact-alarm degraded scheduling to users, turn driver reports into repeatable device-matrix updates, audit overlay fallback behavior around one-handed mode/IME/system bars/secure surfaces, localize F-Droid metadata for existing app locales, complete existing C01/C36/C83/C140/C194 release gates, and keep Shizuku/per-app work behind the existing C06 privacy review.

## Product Map

- Core workflows: choose a driver, toggle the filter, tune preset/custom RGB/Kelvin/gamma/dim/contrast, schedule by time/solar/alarm/lux, recover with notification/QS/widget/ADB hard-off.
- User personas: privacy-first Android users, rooted power users, no-root ADB/CDM users, PWM-sensitive users, F-Droid reviewers, contributors collecting driver evidence.
- Platforms and distribution: Android 8.0+ (`minSdk 26`, `targetSdk 35`, `compileSdk 37`), GitHub APK today, F-Droid planned, Play optional with `specialUse` evidence.
- Key integrations and data flows: DataStore JSON preferences, narrow Direct Boot mirror, AlarmManager schedule transitions, ambient-light sensor Flow, Glance widgets, QS tile, Tasker/Termux/ADB broadcast surface, local diagnostics/crash logs.

## Competitive Landscape

- Red Moon: established F-Droid baseline with long issue history around backup, Shizuku, green/red filtering, one-handed/nav-bar dimming, and Android TV. OpenLumen should keep harvesting its issue queue but avoid its accessibility-service and maintenance debt.
- Twilight: commercial Android filter with per-app profiles, Wear OS, automation, and smart-light integrations. OpenLumen should learn from per-app and wearable demand while keeping networked smart-light features out of the main app.
- Grayscaler: Shizuku-based per-app grayscale proves demand for foreground-app behavior without root, but its open issues show IME and redraw instability risks when toggling global display state per app.
- ColorBlendr: active Shizuku/root Material You editor shows modern privileged Android UX patterns, OEM variance, secondary-user issues, and active localization operations. OpenLumen should copy the defensive device/OEM reporting discipline, not its theming-only scope.
- LightBulb/f.lux/redshift: desktop references prove value in smooth transitions, manual location, schedule clarity, and honest health language. OpenLumen already covers most parity; adaptive learning remains a later offline-only idea.
- OLED Saver/PWM discussion: validates the PWM-sensitive audience, but OpenLumen must keep wording in comfort/display terms and avoid medical claims.

## Security, Privacy, and Reliability

- Verified strengths: `app/src/main/AndroidManifest.xml` still has no `INTERNET`; `AutomationReceiver` is permission-guarded; `Su.kt` has bounded command/probe timeouts; `PreferencesStore` sanitizes persisted/imported data; Direct Boot mirrors only the active display state, not full preferences.
- WorkManager risk: `gradle/libs.versions.toml` pins `work = "2.7.1"` while `app/src/main/AndroidManifest.xml` and `OpenLumenApp.kt` rely on disabling `WorkManagerInitializer` to avoid the Android 10 Glance/directBootAware crash fixed in issue #5. Any AndroidX/Glance refresh must regression-test that guard.
- Exact-alarm recovery gap: `ScheduleAlarmOrchestrator.kt` falls back to inexact alarms when `canScheduleExactAlarms()` is false or a `SecurityException` fires, and `DriverReport.kt` reports permission state, but Schedule UI does not tell the user that timed transitions may drift.
- Device evidence gap: `.github/ISSUE_TEMPLATE/driver_report.yml` collects structured compatibility reports and `docs/device-matrix.md` defines the matrix, but there is no local ingestion/review helper. C01 remains harder than it needs to be.
- Overlay edge gap: `OverlayEngine.kt` is the universal fallback and explicitly sits above status/nav bars. Red Moon one-handed/nav-bar issues and Grayscaler IME/redraw issues justify a focused overlay viewport/IME/system-bar/secure-surface audit before claiming broad fallback quality.
- Distribution gap: app strings have five non-English locale folders, but `fastlane/metadata/android/` only has `en-US`. F-Droid submission can work with English only, but localized metadata is a low-risk credibility win.

## Architecture Assessment

- The four-module split remains sound: `core-engine` for display backends, `core-schedule` for NOAA/sensor schedule logic, `core-prefs` for sanitized persistence, and `app` for service/UI/surfaces.
- The v0.6.2 service split removed the old `LumenService.kt` concentration risk by moving engine, schedule, light-sensor, widget, and Direct Boot work into focused collaborators.
- Refactor candidates: WorkManager/Glance dependency handling in `app/build.gradle.kts`, `gradle/libs.versions.toml`, `OpenLumenApp.kt`, and the manifest startup provider; exact-alarm UX across `ScheduleAlarmOrchestrator.kt`, `ScheduleScreen.kt`, and `DriverReport.kt`; overlay fallback tests around `OverlayEngine.kt` and troubleshooting docs.
- Test gaps: source-marker scan found no active TODO/FIXME/HACK stubs; unit coverage is good for math/prefs/engine decisions, but C83 real-screen screenshots, C01 hardware rows, and an Android 10 Glance/WorkManager direct-boot regression remain the high-value validation gaps.
- Documentation gaps: most major docs exist, but current research should be compact in this file; old historical research remains too large in `ROADMAP.md` and should not be expanded except through append-only incomplete items.

## Rejected Ideas

- Network telemetry, remote crash reporting, cloud sync, smart-light control in the main app - contradicts no-INTERNET and F-Droid-first posture; Twilight shows demand but not fit.
- AccessibilityService as a foreground-app backend - Red Moon/OLED Saver use it, but Android Advanced Protection and privacy review make Shizuku/root the better path.
- UsageStatsManager foreground-app detection - reads broad app-launch history for a convenience feature; keep rejected in favor of C06 Shizuku.
- Main-app plugin ecosystem - privileged display control plus plugins raises maintenance and trust cost with little fit.
- FabricatedOverlay as a rootless Shizuku display engine - existing research notes the Android 12L+ shell-user constraint; keep it tied to root/privileged exploration, not a no-root promise.
- Medical sleep/eye-health claims - 2025/2026 evidence is mixed and luminance-heavy; keep comfort and display-output language.

## Sources

Competitors:
- https://github.com/LibreShift/red-moon
- https://github.com/LibreShift/red-moon/issues/354
- https://github.com/LibreShift/red-moon/issues/353
- https://github.com/LibreShift/red-moon/issues/351
- https://github.com/LibreShift/red-moon/issues/342
- https://github.com/LibreShift/red-moon/issues/244
- https://github.com/C10udburst/Grayscaler
- https://github.com/C10udburst/Grayscaler/issues/8
- https://github.com/C10udburst/Grayscaler/issues/4
- https://github.com/C10udburst/Grayscaler/issues/2
- https://github.com/Mahmud0808/ColorBlendr
- https://github.com/Mahmud0808/ColorBlendr/issues/292
- https://github.com/Mahmud0808/ColorBlendr/issues/287
- https://github.com/KieronQuinn/DarQ
- https://github.com/Tyrrrz/LightBulb
- https://f-droid.org/en/packages/com.flux/
- https://play.google.com/store/apps/details?id=com.urbandroid.lux

Platform and dependencies:
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/12/behavior-changes-12#exact-alarm-permission
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/glance
- https://developer.android.com/jetpack/androidx/versions
- https://kotlinlang.org/docs/whatsnew24.html
- https://github.com/google/ksp/issues/2965

Distribution, security, and evidence:
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Translation_and_Localization/
- https://mas.owasp.org/MASTG-KNOW-0022/
- https://mas.owasp.org/MASWE-0056/

## Open Questions

- Which Android 10 device/emulator path best reproduces the Glance/WorkManager direct-boot crash fixed in issue #5 for future dependency upgrades?
- Does Google developer verification enforcement after September 30, 2026 affect F-Droid/direct-APK installs on certified devices in the first enforcement regions?
- Which maintainer-owned devices can provide the first trustworthy CDM, SurfaceFlinger, KCAL, and Overlay rows for C01?
