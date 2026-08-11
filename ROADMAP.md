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

- [ ] P2 — C226 — Kelvin slider is visually stale and can jump when custom/preset color changes
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:120,122-129,397-420 and app/src/main/java/com/openlumen/ui/OpenLumenViewModel.kt:149-175
  Problem: The Kelvin slider is initialized/displayed from a fixed KELVIN_DEFAULT and is not reverse-derived when RGB, custom matrix, or a preset changes. The next Kelvin drag therefore starts from an unrelated 6500 K value and can jump the color unexpectedly, while the RGB controls and status represent a different current matrix.
  Evidence: The Home effects synchronize RGB/gamma but intentionally omit Kelvin derivation; the Kelvin label reads the fixed state. The ViewModel persists canonical RGB for custom changes, so selecting a warm preset or editing RGB does not update the Kelvin slider state.
  Fix: Store a canonical color-temperature state when applicable and derive/display an approximate Kelvin value for compatible matrices, or clearly scope the Kelvin control to an explicit mode and reset/disable it when the matrix is not temperature-derived. Ensure the first drag uses the visible current value.
  Acceptance: After selecting a preset, editing RGB, navigating away/back, and recomposing, the Kelvin control either reflects the current temperature-derived color or is clearly inactive; moving it never jumps from a stale hidden default.
  Confidence: Verified
  Effort: M

- [ ] P2 — C227 — Previous-preset restore cannot represent a previous custom snapshot
  Category: ux
  Where: app/src/main/java/com/openlumen/service/PresetCycle.kt:54-64 and app/src/main/java/com/openlumen/ui/presets/PresetsScreen.kt:123-126
  Problem: Cycling records "custom" as previousPresetKey, but the Presets screen renders a restore affordance only when Presets.byKey(previousKey) returns a built-in preset. After changing from custom to a named preset, the previous custom state is hidden from the UI even though notification restore can still attempt it, producing inconsistent recovery behavior.
  Evidence: restorePrevious accepts arbitrary keys, while the screen's conditional card requires a catalog lookup. Custom matrices are stored separately in preferences and are not represented as a catalog entry or snapshot in the restore UI.
  Fix: Represent custom as a first-class restore target with the exact prior RGB/gamma/dim snapshot, or explicitly exclude custom from previous-state recording and make notification/UI behavior consistent. Add custom-to-named-to-restore tests across Home, notification, and widget paths.
  Acceptance: A custom-to-named transition either shows and restores the exact prior custom matrix or consistently communicates that it cannot be restored; no surface offers a restore action that another surface silently omits.
  Confidence: Verified
  Effort: M

- [ ] P2 — C228 — Normalization silently overrides the explicit Off preset and AlwaysOff schedule
  Category: correctness
  Where: core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt:221-242 and app/src/main/java/com/openlumen/service/LumenService.kt:403-409; user choices in app/src/main/java/com/openlumen/ui/presets/PresetsScreen.kt and ScheduleScreen.kt:100-105
  Problem: Whenever enabled preferences are emitted, normalizedEnabledFilterState changes active preset "off" to the previous/default preset and changes AlwaysOff to AlwaysOn. The UI explicitly offers an Off preset and an AlwaysOff schedule, so selecting either is immediately undone without explanation; the user cannot intentionally leave the service running but filtering disabled through those controls.
  Evidence: handlePreferenceEmission writes the normalized state and returns before applying it. The same screen surfaces expose the values that normalization rejects, and no confirmation/helper/status explains the override.
  Fix: Define Off/AlwaysOff semantics explicitly: permit them as valid standby states, or move normalization to the narrow action that explicitly means "turn on" and show a clear confirmation when it replaces Off. Keep persisted state, notification copy, and schedule UI aligned.
  Acceptance: Selecting Off or AlwaysOff produces the documented stable state and survives the next preference emission; any intentional auto-normalization is visibly explained and covered by service/UI tests.
  Confidence: Verified
  Effort: M

- [ ] P2 — C229 — External settings/share intents can fail without a safe user-facing result
  Category: reliability
  Where: app/src/main/java/com/openlumen/ui/settings/OverlayPermissionCard.kt:98-103, app/src/main/java/com/openlumen/ui/settings/ExactAlarmAccess.kt:27-40, and app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:261-274
  Problem: Several flows call startActivity/chooser directly. Overlay permission has no resolver or catch; Exact Alarm catches only the primary activity failure and silently swallows fallback failure; Driver export/share has no ActivityNotFoundException/SecurityException handling. OEMs, managed profiles, missing browsers/share targets, or restricted settings can therefore crash the click or make it appear to do nothing.
  Evidence: The cited call sites perform direct launches and do not share a safe launcher/result message. The Exact Alarm fallback catch has no user feedback, and the share chooser is invoked without checking whether it can resolve.
  Fix: Introduce one small UI-safe external-intent helper that checks resolveActivity, catches ActivityNotFoundException and SecurityException, and reports an actionable inline/snackbar error. Use it for settings and share flows, preserving the best available fallback.
  Acceptance: Tests with no resolver and with a throwing activity prove no crash, no silent failure, and a localized recovery message; normal devices still open the intended destination.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C230 — Location-entry dialog can push its actions below the keyboard/short viewport
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/schedule/LocationEntryDialog.kt:84-215
  Problem: The dialog body is an unbounded Column containing help text, a LazyColumn capped at 200 dp, three inputs, validation/error content, and actions. It has no IME-aware scroll container or bounded body layout. On a short phone viewport, especially with the keyboard open and a 12-city result list, the Save/Cancel actions can be obscured or unreachable.
  Evidence: The screenshot suite does not exercise this dialog, and the implementation has no verticalScroll, constrained dialog body, or keyboard inset handling around the stacked content. The result list plus fields can exceed the available window height.
  Fix: Use an IME-aware bounded dialog layout with a scrollable content region and fixed/visible actions, preserving focus and error visibility. Test long results, validation errors, keyboard-open, and 360×640-style constraints in both themes.
  Acceptance: Every field, result, validation message, Save, and Cancel remains reachable by keyboard and touch in a short viewport; opening/closing the IME does not lose the selected city or move actions off-screen.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C231 — Navigation-rail content does not receive system-bar insets
  Category: visual
  Where: app/src/main/java/com/openlumen/ui/OpenLumenRoot.kt:98-115,156-167 and app/src/main/java/com/openlumen/ui/ScreenPadding.kt:9-19
  Problem: In rail mode the rail itself applies statusBarsPadding/navigationBarsPadding, but the sibling OpenLumenNavHost content only receives weight/fill/background and screen-level fixed padding. Because MainActivity enables edge-to-edge, top-level content can draw under the status bar and bottom content under the navigation area on wide/tablet layouts.
  Evidence: The rail branch places the NavHost directly beside the padded rail; topLevelScrollPadding is a fixed 16/24 dp spacing, not a system-inset consumption. The bottom-navigation branch handles insets differently, so the issue is specific to the secondary layout.
  Fix: Apply the same safe-content inset policy to the rail NavHost (or consume insets once in a shared root container) and verify nested dialogs/popovers still use correct window insets. Do not double-apply rail insets.
  Acceptance: Rail layouts in portrait/landscape and both themes keep headers and final scroll content below/above system bars at 100% and large fonts, with screenshot tests covering the inset contract.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — C232 — Fixed navigation-rail width and one-line labels clip localized/large-font text
  Category: a11y
  Where: app/src/main/java/com/openlumen/ui/OpenLumenRoot.kt:156-179,226-233 and localized labels in app/src/main/res/values-es/strings.xml:4-8
  Problem: The rail is fixed at 104 dp and each item's label is maxLines=1 with no overflow/tooltip strategy. Long localized labels such as Spanish "Ajustes predefinidos," and 200% font scaling, can be clipped or ellipsized without an accessible full-name alternative, reducing discoverability and violating the intended readable navigation contract.
  Evidence: Width and item height are constants; the label has a one-line constraint and no measured adaptive width or content description. The supported Spanish strings already exceed the short English labels.
  Fix: Make the rail width/label layout adaptive or provide intentionally short localized labels plus an accessible tooltip/content description containing the full name. Verify selected/unselected state remains understandable at large font scales.
  Acceptance: Supported locales and at least 200% font scale display or expose the full name for every rail item without overlap/clipping, and Compose semantics tests assert the complete accessible labels.
  Confidence: Likely
  Effort: M

- [ ] P2 — C233 — Driver screen's Auto explanation disagrees with the service resolver
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/driver/DriverScreen.kt:70-79 and core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt:47-55
  Problem: The Driver screen describes Auto as root first, then Overlay, omitting CDM. The actual resolver selects root, then CDM, then Overlay. On a device with CDM available and no root, the service uses CDM while the UI tells the user Auto will use Overlay, making driver behavior and troubleshooting misleading.
  Evidence: The UI label has a separate root/overlay conditional; pickBestFrom includes COLOR_DISPLAY_MANAGER before the overlay fallback. No shared resolver/description is used.
  Fix: Derive the Auto explanation from the same ordered resolver/capability list used by the service, including the no-driver case, and keep it updated after reprobe.
  Acceptance: For every availability combination, the screen's Auto explanation names the same selected driver and fallback order that the service will use; tests exercise root-only, CDM-only, overlay-only, and none.
  Confidence: Verified
  Effort: S

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
