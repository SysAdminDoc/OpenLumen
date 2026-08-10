# Research — OpenLumen
Date: 2026-08-10 — replaces all prior research.

## Executive Summary

OpenLumen is a GPL-3.0-or-later Android display-filter app with an unusually clear privacy boundary: no `INTERNET`, no telemetry, no Play Services, four ranked display backends, offline solar scheduling, direct-boot recovery, and local diagnostics. The codebase is structurally healthy, but the highest-value next work is trust and lifecycle closure around schedules, backup data, release provenance, and localization—not adding cloud features or a second privileged-control framework. Existing C206-C242 findings should land first; the new research priorities are C243-C249 below.

- C243: re-arm schedules when Android exact-alarm permission is granted or revoked.
- C244: protect user-entered solar coordinates in system cloud backup while retaining device transfer.
- C245/C246: make release dependency licenses and advisory triage machine-verifiable rather than report-only.
- C247: remove the alarm receiver-to-foreground-service process-death gap.
- C248: expose the existing translations through Android 13+ per-app language settings.
- C249: make dependency-update review produce official release-note and compatibility evidence.
- Complete the existing production-screen screenshot, slider semantics, rail, dialog, and manual device-validation work (C230-C237, C242) before treating the UI as release-complete.

The product should intentionally remain a local-first, capability-disclosing tool. AOSP's compositor-backed Night Light model validates the system-level direction, while Red Moon, Twilight, Night Filter, and Shizuku show the strongest parity opportunities: reliable schedule semantics, explicit preview/commit behavior, portable profiles, and a documented privileged path. Per-app control, Wear OS, Android TV, multi-user, and true content-aware dimming remain existing roadmap tracks or deliberate rejects, not new research additions.

## Product Map

### Core workflows

- Select a built-in or custom RGB/Kelvin/gamma/intensity/dim/contrast configuration, preview the matrix, and enable the filter.
- Choose the most capable available backend: AOSP `ColorDisplayManager`, rooted `SurfaceFlinger`, rooted KCAL, or rootless overlay fallback.
- Schedule fixed-time, solar, or “until next alarm” transitions, optionally OR-ed with a light-sensor threshold; store solar coordinates locally through the offline city picker.
- Control state from the QS tile, Glance widgets, foreground notification, authenticated ADB/Tasker/Termux intents, or the app.
- Export/import JSON profiles, save named profiles, inspect driver reports, view local diagnostics, and recover from a bad tint with the emergency-off path.

### Personas

- Privacy-first F-Droid users who will reject accounts, network permissions, analytics, and opaque binaries.
- Rooted display power users who need reliable compositor/KCAL behavior, rollback, and device-specific diagnostics.
- No-root ADB/secure-settings users who want the strongest system transform available without an accessibility service.
- Overlay-only users who need clear capability limits, safe permission recovery, and usable dimming below the system minimum.
- Maintainers, F-Droid reviewers, and release consumers who need reproducible, signed, license-traceable artifacts.

### Platforms and distribution

- Android API 26+; `minSdk 26`, `targetSdk 35`, `compileSdk 37`, Java 17, Kotlin 2.3.21, AGP 9.2.1, Compose/Material 3, Hilt, DataStore, Glance, and WorkManager.
- Main app package `com.openlumen`; direct APK/GitHub distribution is available, F-Droid submission and Android developer/package registration remain externally gated in `Roadmap_Blocked.md`.
- The repository contains localized Android resources for English, German, Spanish, French, Japanese, and Portuguese, plus Fastlane metadata, but does not currently opt into Android's generated per-app language configuration.

### Integrations and data flows

- `PreferencesStore` persists a versioned JSON blob in credential-protected DataStore; `DirectBootStateStore` mirrors only the minimum active matrix in device-protected DataStore.
- `LumenService` owns the engine, notification, alarm orchestration, sensor subscription, widget refresh, and direct-boot reconciliation through separated collaborators.
- `ScheduleAlarmOrchestrator` currently schedules a `PendingIntent.getBroadcast` to `ScheduleAlarmReceiver`, which then starts `LumenService`.
- `AutomationReceiver` is exported but filters callers by UID, shell/root identity, and a documented package allowlist; filter-state broadcasts use the app's signature permission.
- Backup rules include the entire `file/datastore/` tree for both cloud backup and device transfer, which includes user-entered solar coordinates by design.

## Competitive Landscape

- **Red Moon** — The strongest direct F-Droid baseline: schedules, custom profiles, intensity/dim controls, notification/tile/widget controls, translations, and overlay safety behavior. Learn from its profile/exclusion workflow and community-driven compatibility reports; avoid inheriting an “unmaintained but still recommended” maintenance posture. [Red Moon](https://github.com/LibreShift/red-moon)

- **Grayscaler and DarQ** — Both demonstrate that per-app behavior can be approached through Shizuku/root rather than making `AccessibilityService` the default. Learn from explicit per-app scope and privileged setup; avoid foreground-app polling, redraw conflicts, and a permission model that users cannot understand. [Grayscaler](https://github.com/C10udburst/Grayscaler), [DarQ](https://github.com/KieronQuinn/DarQ)

- **ScreenColorControl and ColorBlendr** — Root/KCAL profile portability and ROM customization are useful adjacent patterns. Learn from importable profiles, capability reporting, translation/community infrastructure, and explicit root boundaries; avoid coupling the core product to archived kernel-specific assumptions or broad theme-modification scope. [ScreenColorControl](https://github.com/SmartPack/ScreenColorControl), [ColorBlendr](https://github.com/Mahmud0808/ColorBlendr)

- **Shizuku** — Its own documentation explains why repeated `su` process creation and text parsing are slow and unreliable, then replaces them with a Binder-backed privileged API. Treat it as the only credible future per-app/privileged extension point; keep it optional, capability-checked, and outside the default no-extra-service path. [Shizuku](https://github.com/RikkaApps/Shizuku)

- **Night Filter** — A commercial product with a live preview, RGB/HSV controls, named presets, widgets/shortcuts, schedules, and an explicit “Save & enable” commit boundary. Learn from its one-screen progressive disclosure and transactional confidence; do not copy its proprietary distribution or assume a cloud/paywall model fits OpenLumen. [Night Filter](https://nightfilter.app/)

- **Twilight and f.lux** — They establish the expectation that solar schedules, gradual transitions, alarm-based timing, automation, and device companions are coherent product concepts rather than isolated toggles. Learn from setup explanation and recovery-oriented automation; avoid medical certainty, network smart-light integrations, and accessibility-based control as a default. [Twilight documentation](https://twilight.urbandroid.org/doc/), [f.lux](https://justgetflux.com/)

- **AOSP Night Light** — Android's reference implementation uses `ColorDisplayManager`/`COLOR_DISPLAY_SERVICE`, HWC color transforms, custom or sunset schedules, intensity, and a manual toggle that still respects automatic rules. Use it as the semantic contract for system-level behavior and driver disclosure; do not claim identical results on OEMs whose HWC or private APIs differ. [AOSP Night Light](https://source.android.com/docs/core/display/night-light)

## Security, Privacy, and Reliability

- **[Verified] Network and privilege posture is strong.** `app/src/main/AndroidManifest.xml` removes network permissions and deliberately declares only SAW, FGS special-use, exact alarms, secure settings, boot, notifications, and the Android 17 protection-mode query. `CONTRIBUTING.md`, `SECURITY.md`, and `PROJECT_CONTEXT.md` consistently prohibit telemetry, cloud APIs, and an accessibility-service backend.

- **[Likely, actionable] Exact-alarm permission changes can orphan a schedule.** `ScheduleAlarmOrchestrator.kt` checks `canScheduleExactAlarms()` only when some other service event calls `rescheduleNextTransition`; `ScheduleScreen.kt` refreshes the displayed permission state on lifecycle events but does not cause the service to re-arm. Android documents that exact alarms are deleted when permission is revoked and specifically requires reacting to `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` after a grant. This is C243.

- **[Verified, actionable] Approximate solar coordinates are included in cloud backup without an encryption requirement.** Both `app/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml` include `file/datastore/`, and their comments explicitly say latitude/longitude are intentionally included. Android's backup guidance says sensitive data should be excluded or require client-side encryption. This is C244; manual SAF export can remain unchanged.

- **[Verified, actionable] The release SBOM is not license-complete.** `tools/local_release_gate.py:216-244` emits package name, version, and Maven PURL, but no `licenseDeclared`, `licenseConcluded`, source provenance, or reviewed exception. The gate rejects network permissions and Google/Firebase coordinates, but it cannot prove that every transitive artifact satisfies F-Droid's FLOSS/license policy. This is C245.

- **[Verified, actionable] Advisory output is review-only.** `build_advisory_report()` writes vulnerabilities and a partial/ok status but never fails the gate for a high or critical advisory; `docs/sbom-and-advisories.md` explicitly lists fail-on-high/critical as future work. Keep the current accepted protobuf exposure documented, but add a versioned, expiring allowlist and a release-mode failure policy. This is C246.

- **[Likely, actionable] The schedule alarm has a process-death window.** `ScheduleAlarmOrchestrator.schedulePendingIntent()` targets `ScheduleAlarmReceiver`, and the receiver then calls `startForegroundService`. Android background-start rules are already handled for blocked starts, but the alarm-to-receiver-to-service jump can still lose the transition if the process is killed in the handoff; the Android DeskClock rationale quoted in the community evidence recommends targeting the service directly when possible. This is C247 and requires device validation before changing the entrypoint.

- **Recovery is otherwise well designed.** `BootReceiver` has a crash-window panic reset, `DirectBootStateStore` has bounded/sanitized decoding, root engines have hard-clear paths, and About exposes an emergency-off command. The remaining recovery gap is preserving schedule correctness when platform permissions, process state, or backup/restore state change outside the app.

## Architecture Assessment

- **Boundaries are healthy.** Pure matrix/driver logic is in `core-engine`, solar and city logic in `core-schedule`, persistence/migration in `core-prefs`, and Android lifecycle orchestration in `app`. The recent service split (`EngineController`, `ScheduleAlarmOrchestrator`, `LightSensorSubscription`, `WidgetBridge`, `DirectBootMirror`) is the correct base for further work.

- **Schedule platform events need one coordinator.** Clock/timezone/date handling is already an active C208 finding; C243 adds exact-alarm permission events. Route both through one idempotent schedule-runtime coordinator so alarms, service state, notification copy, and UI degradation cannot drift across separate receivers and lifecycle callbacks.

- **Release metadata needs to become an executable contract.** The local gate already runs strict Gradle verification, builds, lint, tests, screenshots, SBOM/advisory generation, hash output, and APK signature checks. C245/C246 should extend that contract rather than recreate CI, with deterministic license/advisory reports attached by the existing release checklist.

- **Testing is broad in pure modules but thin at Android boundaries.** `core-engine`, `core-schedule`, and `core-prefs` have focused JVM coverage; app tests cover service helpers and token screenshots. `app/src/screenshotTest/.../ScreenCoverageScreenshotTest.kt` renders fixtures instead of production screens, and no `app/src/androidTest` tree exists. Keep C236/C237/C242 as the production-screen, semantics, and manual-device work; use the current Compose Accessibility Test Framework guidance when that lane is implemented.

- **Localization is translated but not platform-integrated.** The six resource sets and Fastlane metadata are a strong base. `app/build.gradle.kts` has no `androidResources { generateLocaleConfig = true }`, no `resources.properties`, and the manifest has no `android:localeConfig`; C248 is a small, independently testable upgrade.

- **Category decisions:** security/reliability are addressed by C243-C247 and C206-C225; accessibility/visual quality by C230-C237 and C242; i18n by C232, C248, and existing translation work; observability by the current local diagnostics/driver-report system; testing by C236/C242 plus release-gate tests; distribution by C245/C246 and existing C140/C141; offline/resilience by the no-network posture and C243/C244; migration/upgrade by C244/C248/C249 and existing C29/C102/C144. Plugin ecosystem, mobile companions, and multi-user behavior remain existing C06/C21/C22/C81 tracks or are rejected below.

## Rejected Ideas

- **Telemetry, remote crash reporting, cloud sync, or smart-light control in the main app:** contradicts the no-`INTERNET`, F-Droid-first contract; commercial demand does not override the repository's stated privacy boundary. Sources: `README.md`, `CONTRIBUTING.md`, [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), [Twilight documentation](https://twilight.urbandroid.org/doc/).

- **AccessibilityService as the default per-app backend:** competitors show demand, but OpenLumen's threat model and Android protection direction make an optional Shizuku/root path safer and more honest. Existing C79/C104 already record the rejection; do not create a duplicate. Sources: `SECURITY.md`, `PROJECT_CONTEXT.md`, [Shizuku](https://github.com/RikkaApps/Shizuku).

- **Content-aware or pixel-level dimming in the default app:** academic work shows technical promise, but it requires MediaProjection/accessibility or device-specific rendering, adds privacy and battery costs, and conflicts with the no-capture posture. Existing C67/C68/C89/C145 cover the deferred/rejected variants. Source: [SmartNight](https://arxiv.org/abs/1905.08367).

- **Broad plugin/extension marketplace:** there is no safe extension boundary today, and the current product's value comes from a small auditable engine set. Establish the Shizuku boundary and stable automation contract first; do not add a second ecosystem item.

- **Play-only or cloud-hosted release infrastructure:** the current local release gate is the intended source of truth, while F-Droid requires source-build transparency and FLOSS tooling. Improve local proof instead of restoring an undocumented remote dependency. Sources: `tools/local_release_gate.py`, [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/).

- **Medical, sleep, migraine, or eye-treatment claims:** the repository's own health-evidence policy correctly limits copy to comfort and physical display metrics. Do not turn competitor marketing or small studies into product claims.

## Sources

### OSS competitors and adjacent tools

- https://github.com/LibreShift/red-moon
- https://github.com/C10udburst/Grayscaler
- https://github.com/KieronQuinn/DarQ
- https://github.com/Mahmud0808/ColorBlendr
- https://github.com/SmartPack/ScreenColorControl
- https://github.com/NoneBaiano/BlueLightFIlter
- https://github.com/RikkaApps/Shizuku
- https://github.com/RikkaApps/Shizuku-API/blob/master/rish/README.md
- https://github.com/RRethy/Quasar
- https://github.com/jiesou/Android-Screener
- https://github.com/jqssun/android-display-extend
- https://github.com/jqssun/android-display-mirror
- https://github.com/aka-munan/keysync

### Awesome lists and ecosystem indexes

- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root
- https://github.com/offa/android-foss
- https://github.com/lukeslp/awesome-accessibility

### Commercial and closed-source references

- https://nightfilter.app/
- https://twilight.urbandroid.org/
- https://twilight.urbandroid.org/doc/
- https://play.google.com/store/apps/details?id=com.urbandroid.lux
- https://play.google.com/store/apps/details?id=eu.chainfire.lumen
- https://justgetflux.com/

### Platform, standards, and dependency releases

- https://source.android.com/docs/core/display/night-light
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/reference/android/app/AlarmManager
- https://developer.android.com/privacy-and-security/direct-boot
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/guide/topics/resources/app-languages
- https://developer.android.com/reference/android/hardware/display/DisplayManager
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/privacy-and-security/risks/pending-intent
- https://developer.android.com/privacy-and-security/risks/intent-redirection
- https://developer.android.com/about/versions/14/behavior-changes-14
- https://developer.android.com/about/versions/12/behavior-changes-12
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/glance
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://kotlinlang.org/docs/releases.html
- https://github.com/google/dagger/releases
- https://kotlinlang.org/docs/coroutines-guide.html

### Distribution, provenance, and security advisories

- https://f-droid.org/docs/Inclusion_Policy/
- https://spdx.dev/specifications/
- https://osv.dev/docs/
- https://github.com/advisories
- https://google.github.io/osv-scanner/

### Academic and engineering research

- https://arxiv.org/abs/2607.04120
- https://arxiv.org/abs/2205.13945
- https://arxiv.org/abs/1905.08367
- https://arxiv.org/abs/2308.09029
- https://arxiv.org/abs/2003.00380

### Community signal

- https://www.reddit.com/r/androidapps/comments/e0u5i2/blue_screen_filter/
- https://www.reddit.com/r/androidapps/comments/qy5o8b/
- https://www.reddit.com/r/androidapps/comments/1rxmi3v/
- https://news.ycombinator.com/item?id=41616023
- https://stackoverflow.com/questions/71657416/can-we-startforegroundservie-from-an-exact-alarms-receiver-in-the-background

## Open Questions

- Product/security owner must choose whether the default for cloud backup is “encrypted-only” (recommended by C244) or “exclude solar coordinates entirely”; the implementation can preserve device-to-device transfer either way.
- Real Android 17 stable, OEM overlay, exact-alarm, boot-restore, and release-distribution evidence remains gated by the existing C01/C84/C103/C140/C141/C194 rows in `Roadmap_Blocked.md`.
- The scope and release packaging of Shizuku, Wear OS, Android TV, and multi-user support remain existing roadmap decisions; this research found no evidence that they should bypass their current gates.
