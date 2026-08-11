# OpenLumen Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Actionable Items

- [ ] **SYSTEM_ALERT_WINDOW + FGS-from-background restriction (C105, new)**
   - Android 15+ requires SAW apps to have a visible overlay window
     before starting an FGS from the background. Audit the tile/widget
     toggle-on path: if the service isn't running and overlay isn't
     visible, the FGS launch can be rejected. Add a fallback that opens
     the app to grant the overlay permission, then re-attempts the
     service start.
   - Impact 4, effort 2, risk 2. Sources: S85.

- [ ] **Security and supply-chain baseline (C38, C47-C51, C94)**
    - Already shipped; C142 refreshed the Actions baseline to
      Node-24-capable current majors and moved release provenance to
      `actions/attest@v4`. Keep the artifact attestation cadence visible
      in the release checklist. Document the protobuf-java CVE-2024-7254
      triage state in `docs/sbom-and-advisories.md` since the scanner
      will keep surfacing it.
    - Impact 4, effort 2, risk 2. Sources: S60, S61, S62, S63, S64,
      S67, S68, S77, S108, S110, S114.

- [ ] **AAPM driver-report surface (C130, new in rev 4)**
    - Reflection-gated query for `AdvancedProtectionManager` state on
      Android 17+; surface in the in-app driver report. Driver-tab info
      card explains *"AAPM auto-revokes Accessibility-based features;
      OpenLumen does not use Accessibility, so AAPM has no effect on
      OpenLumen."* Pairs with the C79/C80 rejection rationale shipped
      in rev 3 — users who try a11y-based competitor features and find
      they're auto-revoked see the receipt in our report.
    - Impact 3, effort 1, risk 1. Sources: S134, S135, S136.

- [ ] **Preset system v2 polish** — preset-pack export/import (the JSON
  format is already extensible); user-renameable presets; sort presets
  alphabetically or by recency.

- [ ] **PWM-sensitive workflow guidance** — document the OLED Saver (S103)
  / Iris approach without claiming health benefits.

- [ ] **Local diagnostics viewer with timeline filtering** — already
  shipped as C53; the filter-by-category/level stretch also shipped
  2026-05-17 (see C53 entry in Progress). Remaining stretch: timeline
  scrubbing (jump to range), text search within filtered subset.

- [ ] P2 — C234 — Reprobe can run concurrently and overwrite newer driver results
  Category: reliability
  Where: app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:155-157 and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:219-231
  Problem: The Reprobe control remains enabled while refreshProbes() launches a new coroutine, clears the global root cache, and starts probes. Rapid taps or a simultaneous service probe can create overlapping root commands and non-deterministic _probes/preference updates; an older completion can overwrite a newer result.
  Evidence: There is no in-flight flag, mutex, cancellation/latest-generation check, or disabled button around the refresh coroutine. The function can be called repeatedly from the UI and touches process-wide probe state.
  Fix: Make refresh single-flight/latest-wins, disable the action while running, and coordinate global cache invalidation with service probing. Preserve the last known result while showing progress and surface a bounded failure.
  Acceptance: Rapid repeated taps and concurrent service/UI refreshes execute at most one probe generation, the final displayed result is from the latest generation, and no stale completion changes the selected driver afterward.
  Confidence: Likely
  Effort: M

- [ ] P2 — C235 — Saving a profile with an existing name silently replaces the old snapshot
  Category: ux
  Where: core-prefs/src/main/java/com/openlumen/prefs/Profiles.kt:62-77, app/src/main/java/com/openlumen/ui/settings/AboutScreen.kt:315-363, and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:87-90
  Problem: Profile storage trims names and replaces an existing profile with the same name in place. The save dialog has no collision warning, explicit Replace confirmation, versioning, or undo. A user trying to create a new profile can silently destroy a prior configuration.
  Evidence: Profiles.upsert filters the same-name entry before appending the new snapshot; the dialog only validates the name/content and immediately calls save. No recovery action is presented.
  Fix: Detect name collisions in the dialog and require an explicit Replace confirmation, or auto-generate a distinct name and offer rename/undo. Keep the operation atomic and test whitespace/case normalization policy.
  Acceptance: Saving an existing name cannot overwrite without an explicit confirmation; cancel preserves the old snapshot, and confirmed replacement is reflected consistently in Home/profile lists with a test.
  Confidence: Likely
  Effort: M

- [ ] P2 — C236 — Screenshot tests exercise fixtures instead of production screens
  Category: testing
  Where: app/src/screenshotTest/kotlin/com/openlumen/screenshot/ScreenCoverageScreenshotTest.kt:69-112,125-144 and app/src/screenshotTest/kotlin/com/openlumen/screenshot/ThemeTokenScreenshotTest.kt:28-44
  Problem: The seven screen screenshots render HomeTabFixture, ScheduleTabFixture, and similar synthetic fixtures rather than HomeScreen, ScheduleScreen, OpenLumenRoot, or the real navigation/inset/dialog composition. The token test covers only isolated token fixtures, with full screen screenshots effectively dark-only at one 393×852 size. Passing baselines therefore cannot catch production wiring, light-theme, rail, dialog, loading/error/empty, keyboard, or inset regressions.
  Evidence: Test source imports/renders fixture composables and hardcodes OpenLumenTheme(darkTheme = true) for the screen coverage cases; only the token fixture is rendered once per theme. No production ViewModel/fake-repository harness is used.
  Fix: Build a deterministic production-composable harness with fake preferences/engine/service state, then cover real Home, Schedule, Presets, Driver, Settings, dialogs, and state variants in light/dark themes, phone/rail widths, and large text. Keep fixture tests only for isolated component contracts.
  Acceptance: A deliberate change to production screen layout/theme/insets causes the relevant screenshot test to fail; baselines include both themes, rail and phone layouts, and representative loading/error/empty/dialog states.
  Confidence: Verified
  Effort: L

- [ ] P2 — C237 — Slider controls lack an explicit accessible name
  Category: a11y
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:200-208,225-233,285-298,409-420,481-490,519-528, ScheduleScreen.kt:213-243, and LightSensorCard.kt:86-95
  Problem: Custom Gamma/RGB/schedule/light sliders expose a value/state description but do not attach an explicit control name/content description or label semantics to the adjustable node. A visible sibling Text is not guaranteed to become the TalkBack label for a custom semantic control, so users can encounter anonymous adjustable controls whose values are not associated with Gamma, red/green/blue, schedule offset, or lux threshold.
  Evidence: The cited slider call sites set stateDescription/visual labels but contain no contentDescription, semantics label, or merged labeled-control pattern. C190 improved labels/state descriptions but did not add names to these nodes.
  Fix: Give every adjustable node a stable localized accessible name and value/state description, using a shared labeled-slider helper/semantics pattern so the visible and spoken labels stay in sync. Add Compose semantics tests for all custom slider variants.
  Acceptance: Semantics output for each slider contains its control name and current value, keyboard/TalkBack adjustment announces both, and names remain correct after recomposition and localization.
  Confidence: Likely
  Effort: M

- [ ] P3 — C238 — Emergency KCAL recovery documentation uses an invalid scalar
  Category: docs
  Where: docs/troubleshooting.md:31-41 and docs/root-safety.md:114-125; range enforcement in core-engine/src/main/java/com/openlumen/engine/KcalEngine.kt:262-268
  Problem: The emergency recovery examples tell users to write 256 256 256, but the current KCAL engine clamps/validates scalar values to a maximum of 255. On strict kernels the documented recovery command can be rejected, leaving a user with the very failure path the documentation is supposed to recover from.
  Evidence: Both docs contain the literal 256 recovery command; KcalEngine.MAX_SCALAR is 255 and the command builder enforces that range. The changelog also states the standardized range is 0–255.
  Fix: Change the recovery examples to valid 0–255 values (use 255 for maximum white), state the expected kernel path, and add a docs/health check that rejects recovery examples outside the engine's documented range.
  Acceptance: Copying the documented emergency command produces a valid KCAL command on the strict 0–255 path, and documentation lint/test fails if an out-of-range recovery scalar is reintroduced.
  Confidence: Verified
  Effort: S

- [ ] P3 — C240 — PROJECT_CONTEXT.md contains stale release, schema, and Auto-driver contracts
  Category: docs
  Where: PROJECT_CONTEXT.md:30-33,85-96,160-162, README.md/CHANGELOG.md current release metadata, core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt:164-169, and core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt:47-55
  Problem: The context document says the main release is v0.6.5 and tagged release v0.4.0, describes current schema as 1, and says Auto falls back to Overlay so it always has a driver. The repository is at v0.6.6, schema 2, and the current resolver can select CDM or reach an unavailable-overlay/no-driver case. Agents and maintainers using this document will make incorrect compatibility and recovery decisions.
  Evidence: The cited context claims conflict with the current build/changelog metadata, CURRENT_SCHEMA_VERSION, and resolver code. No generated/currentness check covers these assertions.
  Fix: Update the canonical context to current release/schema/resolver behavior, label historical claims with their version, and add a lightweight consistency check for version/schema/driver-contract statements that are intended to be current.
  Acceptance: A fresh contributor following PROJECT_CONTEXT.md receives the current release/schema/Auto behavior, and the consistency check fails when those canonical values drift.
  Confidence: Verified
  Effort: S

- [ ] P3 — C241 — Inline comments contradict live sensor and diagnostics locking behavior
  Category: maintainability
  Where: app/src/main/java/com/openlumen/sensor/LightSensorAdapter.kt:29-33,62 and app/src/main/java/com/openlumen/diagnostics/DiagnosticsLog.kt:40-43,77-85
  Problem: LightSensorAdapter says it never calls trySend, but the implementation does call trySend for readings. DiagnosticsLog says reads are intentionally not locked, while read() synchronizes on the same writeLock. These contradictions make concurrency intent and future cleanup decisions unreliable.
  Evidence: The comment and live statements are adjacent in the cited functions; the code is not a dead or test-only branch. The mismatch is visible by reading the current implementation without inference about runtime behavior.
  Fix: Rewrite both comments to state the actual channel/backpressure and locking guarantees, or change the implementation if the documented guarantee is the intended contract. Add a focused concurrency/comment contract test where practical.
  Acceptance: Comments describe the current operations accurately, and code review/tests document whether sensor sends are lossy and whether diagnostics reads share the writer lock.
  Confidence: Verified
  Effort: S

- [ ] P2 — C247 — Remove the schedule alarm receiver-to-service process-death gap
  Why: A scheduled transition currently depends on a `BroadcastReceiver` process handoff before the foreground service can re-evaluate the schedule. If the process dies during that narrow jump, the transition can be missed even though the alarm fired; the recovery retry budget only addresses a foreground-start rejection, not a killed handoff.
  Evidence: `app/src/main/java/com/openlumen/service/ScheduleAlarmOrchestrator.kt:112-119` creates `PendingIntent.getBroadcast` for `ScheduleAlarmReceiver`; `ScheduleAlarmReceiver.kt:16-22` then calls `LumenServiceStarter.start()`. The Android DeskClock rationale quoted in the alarm-delivery discussion recommends targeting the service directly because an out-of-memory kill can thwart a receiver-to-service jump: https://stackoverflow.com/questions/71657416/can-we-startforegroundservie-from-an-exact-alarms-receiver-in-the-background. Android's FGS background-start restrictions remain the platform constraint to validate: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start.
  Touches: `ScheduleAlarmOrchestrator.kt`, `ScheduleAlarmReceiver.kt`, `LumenService.kt`, manifest declarations, alarm tests, and the Android 15/17 device-matrix schedule rows.
  Acceptance: The primary scheduled transition uses an explicit `PendingIntent.getForegroundService`/service-targeted path or a documented durable idempotent handoff that survives process death; a kill immediately before the trigger still produces one re-evaluation and next-alarm schedule; blocked FGS starts retain bounded retries and no duplicate transitions; API 31, 35, and 37 validation records the result.
  Complexity: M

- [ ] P2 — C248 — Expose supported translations through Android per-app language settings
  Why: OpenLumen already ships six localized resource sets, but Android 13 users cannot select the app language from the system's App Languages surface unless the app opts into generated or manual locale configuration. This makes the existing C58/C59 translation investment harder to discover and control for multilingual users; it is separate from C232's rail clipping problem.
  Evidence: `app/src/main/res/` contains `values`, `values-de`, `values-es`, `values-fr`, `values-ja`, and `values-pt`; `app/build.gradle.kts:86-147` has no `androidResources { generateLocaleConfig = true }` or default-locale configuration, and `AndroidManifest.xml` has no `android:localeConfig`. Android recommends generated locale configuration via AGP 8.1+ and describes the system settings behavior here: https://developer.android.com/guide/topics/resources/app-languages.
  Touches: `app/build.gradle.kts`, `app/src/main/res/resources.properties` or the generated-locale configuration, the manifest if manual configuration is chosen, translation documentation, and locale-completeness tests.
  Acceptance: On API 33+, Settings > App Languages lists OpenLumen with exactly the supported English/German/Spanish/French/Japanese/Portuguese locales; changing the system selection updates the app after recreation and persists through backup/restore; base and translated string key sets are checked for drift; unsupported library locales are not advertised.
  Complexity: S

- [ ] P2 — C249 — Make dependency-update review produce release-note and compatibility evidence
  Why: A stable version number alone does not say whether an AndroidX/AGP/Hilt/Kotlin upgrade changes APIs, target-SDK behavior, build tooling, or license/provenance. The current helper detects Maven metadata candidates, leaving the release checklist's most important compatibility step as an undocumented manual search; this extends the shipped C124/C144 version baseline without proposing another blind dependency bump.
  Evidence: `tools/dependency_update_review.py:1-270` queries repository `maven-metadata.xml`, filters unstable versions, and reports current/latest coordinates, but emits no official release-note URL, API/behavior-change summary, dependency-verification diff, or module-specific test recommendation. `docs/release-checklist.md` separately instructs maintainers to read release notes. Official release streams are available for AndroidX, AGP, Kotlin, and Dagger: https://developer.android.com/jetpack/androidx/versions, https://developer.android.com/build/releases/agp-9-2-0-release-notes, https://kotlinlang.org/docs/releases.html, https://github.com/google/dagger/releases.
  Touches: `tools/dependency_update_review.py`, `tools/test_dependency_update_review.py`, `docs/dependency-verification.md`, `docs/release-checklist.md`, and a small checked-in mapping for official release-note endpoints where Maven metadata has no changelog link.
  Acceptance: The console/JSON report distinguishes update, unresolved, and intentionally held versions; each update has an official release-note URL, affected module/plugin, compatibility-risk note, required Gradle/test commands, and verification-metadata impact; a metadata/network failure is not reported as "current"; fixture tests cover stable updates, pre-release-only versions, missing metadata, and release-note mapping.
  Complexity: M
