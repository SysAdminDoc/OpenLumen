# OpenLumen Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Actionable Items

- [ ] **C02** In-app driver report export

- [ ] **C03** SurfaceFlinger code registry — per-API ladder, `activeTransactionCode` diagnostic

- [ ] **C04** KCAL variant probing — three known sysfs roots, `activeBasePath` diagnostic

- [ ] **C05** Root prompt safety and recovery docs ([docs/root-safety.md](docs/root-safety.md))

- [ ] **C07** Guided WRITE_SECURE_SETTINGS grant — Driver screen state + copyable adb command

- [ ] **C09** Overlay alpha cap explanation — Driver screen info card

- [ ] **C13** Emergency off command — About screen ADB command with copy-to-clipboard

- [ ] **C14** Previous profile restore — Presets-screen Restore affordance + `ACTION_RESTORE_PREVIOUS` intent

- [ ] **C15** Favorite presets — `favoritePresetKeys`, star toggle on Presets screen, cap 8

- [ ] **C16** Notification preset cycle — "Next preset" notification action

- [ ] **C17** QS tile long-press deep link — `PREFERENCES_ACTIVITY` manifest meta-data

- [ ] **C18** QS tile secondary state label — subtitle shows active preset

- [ ] **C19** Home-screen 1x1 toggle widget

- [ ] **C20** Home-screen 4x1 preset widget

- [ ] **C23** Smooth fixed-time transitions — `transitionDurationMs`, ramp coroutine

- [ ] **C24** Smooth solar transitions — shared ramp path

- [ ] **C25** Alarm-based schedule mode — `ScheduleMode.UntilNextAlarm` + 12h fallback

- [ ] **C26** Offline city picker — `OfflineCities` (~95 cities), nearest + search

- [ ] **C27** Automatic timezone fallback — Schedule screen shows system zone label

- [ ] **C29** Versioned preference migrations — `schemaVersion` + `PreferencesMigrations`

- [ ] **C30** Profile import preview — `previewImport(uri)` + import diff dialog

- [ ] **C31** Named profile library — `ProfileSnapshot`, `NamedProfile`, About-tab UI, cap 32

- [ ] **C32** Red Moon profile import notes ([docs/profile-import-formats.md](docs/profile-import-formats.md))

- [ ] **C33** CF.Lumen import notes — manual mapping table

- [ ] **C34** F-Droid metadata ([fastlane/metadata/android/](fastlane/metadata/android/))

- [ ] **C37** Reproducible build notes ([docs/reproducible-build.md](docs/reproducible-build.md))

- [ ] **C38** Artifact attestations — `actions/attest@v4` in release workflow

- [ ] **C40** README troubleshooting table ([docs/troubleshooting.md](docs/troubleshooting.md))

- [ ] **C41** CONTRIBUTING.md

- [ ] **C42** ARCHITECTURE.md ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md))

- [ ] **C43** Issue templates

- [ ] **C44** Public compatibility table ([docs/compatibility-table.md](docs/compatibility-table.md))

- [ ] **C45** Release checklist ([docs/release-checklist.md](docs/release-checklist.md))

- [ ] **C46** Dependency update cadence — Dependabot weekly

- [ ] **C47** Dependabot/Renovate ([.github/dependabot.yml](.github/dependabot.yml))

- [ ] **C48** Gradle dependency verification — `gradle/verification-metadata.xml`
  is checked in and enforced after the AGP 9 / AndroidX baseline refresh;
  [docs/dependency-verification.md](docs/dependency-verification.md)
  documents strict verification and refresh review. Source: S00o.

- [ ] **C49** Pin GitHub Actions

- [ ] **C50** No-INTERNET CI assertion — `permissions-audit` job

- [ ] **C51** OWASP MASVS-lite threat model ([docs/threat-model.md](docs/threat-model.md))

- [ ] **C52** Local diagnostics bundle — `DiagnosticsLog` ring-buffered event log, tail in driver report

- [ ] **C54** Wake/alarm/battery audit ([docs/wake-and-vitals.md](docs/wake-and-vitals.md))

- [ ] **C55** Slider TalkBack state descriptions — light/threshold/offsets/RGB/gamma/Kelvin/intensity/dim/contrast

- [ ] **C58** RTL / string-resource baseline

- [ ] **C59** Weblate/translation workflow ([docs/translations.md](docs/translations.md))

- [ ] **C60** Health evidence note ([docs/health-evidence.md](docs/health-evidence.md))

- [ ] **C61** Blue-channel reduction indicator (narrow physical-measurement form of the original melanopic candidate)

- [ ] **C64** Contrast control

- [ ] **C65** Kelvin temperature UI (Tanner Helland approximation)

- [ ] **C66** AMOLED true-black clamp (scalar form)

- [ ] **C70** Tasker intents — full automation surface documented

- [ ] **C71** Shell/ADB command docs ([docs/automation.md](docs/automation.md))

- [ ] **C82** Android 16/API 36 readiness inventory ([docs/android-17-readiness.md](docs/android-17-readiness.md))

- [ ] **C85** Local panic reset on boot — 5-minute crash-log window

- [ ] **C93** Play FGS evidence pack ([docs/play-fgs-evidence.md](docs/play-fgs-evidence.md))

- [ ] **C94** SBOM and advisory scan ([.github/workflows/sbom.yml](.github/workflows/sbom.yml))

- [ ] **C97** Awesome/topic-index watchlist ([docs/research-watchlist.md](docs/research-watchlist.md))

- [ ] **C98** Dynamic ramp duration presets — Instant/30s/5m/15m/30m

- [ ] **C99** Event-driven ambient sampling — `ACTION_SCREEN_OFF` invalidates cached lux

- [ ] **C100** Medical/pain-mode disclaimer templates — covered in health-evidence.md

Design-doc deliverables (deferred implementations with durable analysis):

- [ ] **C11** Per-app pause/exclusions — deferred behind Shizuku spike (C06)

- [ ] **C12** Secure/install/su dialog auto-pause — same blocker as C11

- [ ] **C69** Per-app profiles — same Shizuku blocker

- [ ] **C01** Real-device validation rows — per-engine smoke flow documented;
  rows pending real hardware.

- [ ] **C36** Store screenshot matrix — layout in place; captures pending
  real device/emulator screenshots.

- [ ] **C55/C56/C57** Accessibility scanner / dynamic font scale / CVD
  contrast audit — still need a real device pass.
  Static 2026-06-15 pass: profile deletion now has undo recovery,
  chip controls use project shapes, widget labels use larger centered
  ellipsized text, and stale light-theme screenshot baselines were
  refreshed. Accessibility Scanner, font-scale screenshots, and CVD
  contrast device evidence remain open.

- [ ] **Device validation and driver report (C01)**
   - Real-device rows in `docs/device-matrix.md`. Include at minimum: a
     Pixel running stable Android 15 and Android 17 preview, a Samsung
     One UI device, a Snapdragon device with a KCAL kernel, and a
     non-root overlay device. The in-app driver report (already shipped)
     is the data-collection mechanism.
   - Impact 5, effort 3, risk 2. Why now: OpenLumen's multi-driver claim
     cannot be trusted without per-device evidence and an easy bug-report
     path. Sources: S00, S10, S11, S25, S26, S48, S86.

- [ ] **F-Droid release packaging (C34, C35, C36, C37, C45)**
   - C35 is shipped. Capture phone screenshots into
      `fastlane/metadata/android/en-US/images/phoneScreenshots/` (C36),
      confirm reproducibility on F-Droid's build server (C37), and walk
     the pre-release checklist (C45). The 70% translation floor (S111)
     applies for translated releases but the en-US baseline is enough
     to ship.
   - Impact 5, effort 3, risk 2. Sources: S00, S11, S29, S60, S61, S62,
     S74, S111, S112.

- [ ] **Android 17 readiness (C82 extension, supersedes API-36-only scope)**
   - Validate on Android 17 Beta 4 (or stable when it lands in June
     2026). Confirm: tile subtitle render, overlay alpha + cutout,
     exact-alarm fallback, `specialUse` FGS subtype declaration, and
     the new BAL hardening (C111). Add an Android 17 row to
     `docs/device-matrix.md`. Bump `targetSdk` in its own release per
     `docs/android-17-readiness.md` policy.
   - Impact 4, effort 3, risk 3. Sources: S83, S84, S96.

- [ ] **SYSTEM_ALERT_WINDOW + FGS-from-background restriction (C105, new)**
   - Android 15+ requires SAW apps to have a visible overlay window
     before starting an FGS from the background. Audit the tile/widget
     toggle-on path: if the service isn't running and overlay isn't
     visible, the FGS launch can be rejected. Add a fallback that opens
     the app to grant the overlay permission, then re-attempts the
     service start.
   - Impact 4, effort 2, risk 2. Sources: S85.

- [ ] **BOOT_COMPLETED FGS verification (C106, new)**
   - Android 14+ blocks `BOOT_COMPLETED` from launching certain FGS
     types. `specialUse` is not on the affected list per current docs,
     but we should add an explicit Android 14/15/16/17 row to the
     wake/vitals audit and the device matrix confirming the boot-
     restore path still works.
   - Impact 3, effort 1, risk 1. Sources: S85.

- [ ] **Activity Background Start (BAL) hardening readiness (C111, new)**
   - Android 17 deprecates `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`
     for `IntentSender` in favor of
     `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`. Audit the
     PendingIntent / notification-tap and tile long-press paths.
   - Impact 3, effort 1, risk 1. Sources: S84.

- [ ] **Overlay-safe interaction model (C10, C11, C12, C90, C91)**
   - C10/C90 shipped via troubleshooting + notification/tile/ADB
     emergency-off. The remaining work for Now is publishing the design
     decision in `docs/overlay-and-per-app-design.md` (done) and
     ensuring the per-app candidates (C11/C12/C69) are clearly
     blocked-by-Shizuku in the public docs so users understand why
     "auto-pause on installer" is not on the v1 list. The C91
     SurfaceView regression test belongs here once we have a device.
   - Impact 5, effort 3, risk 3. Sources: S10, S12, S18, S20, S26,
     S32, S42, S67, S68, S71, S73, S88, S89, S108.

- [ ] **Test and CI hardening (C83, C84, C91, C94)**
    - C101 shipped the first Compose Preview Screenshot Testing fixture
      and CI job. C83 remains the broader screen-coverage expansion.
      Connected-device tests (C84) and
      SurfaceView regression (C91) still need emulator infrastructure;
      schedule once `reactivecircus/android-emulator-runner`
      [S113] is wired in. SBOM/advisory scan (C94) already runs weekly
      and on release.
    - Impact 5, effort 3, risk 2. Sources: S97, S98, S113, S26, S27, S28.

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

- [ ] **Shizuku-backed privileged backend (C06, also unblocks C11, C12, C69)**
   - Optional flavor (or first-class detection at runtime), wired
     through a new `ShizukuEngine` that uses `dumpsys activity recents` /
     IActivityManager binder access for foreground-app detection. Lets
     us ship per-app pause (C11), installer auto-pause (C12), and per-app
     profiles (C69) without `PACKAGE_USAGE_STATS` or AccessibilityService
     (which Android 17 AAPM auto-revokes — S88, S89, S90). Document the
     Shizuku install path; don't bundle the library, just probe at
     runtime.
   - Impact 5, effort 5, risk 4. Sources: S12, S25, S33, S43, S115, S116.

- [ ] **Wear OS companion (C21)** — separate F-Droid package (`com.openlumen.wear`)
   that uses the Wearable Data Layer. Phone-side keeps the no-INTERNET
   posture. Wear tile = single Toggle button. ProtoLayout for
   responsive tile rendering (S117). No display tinting on the watch
   itself.

- [ ] **Driver compatibility learning (continued)** — extend
   `SurfaceFlinger.candidatesFor()` and `Kcal.CANDIDATE_BASES` as device
   reports arrive. Maintain `docs/device-matrix.md` per release.

- [ ] **Preset system v2 polish** — preset-pack export/import (the JSON
   format is already extensible); user-renameable presets; sort presets
   alphabetically or by recency.

- [ ] **Connected permission / overlay tests (C84, C91)** — emulator CI via
   `reactivecircus/android-emulator-runner` covers
   `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
   blocked-touch behavior, and SurfaceView pass-through.

- [ ] **Research watchlist maintenance** — review `docs/research-watchlist.md`
   each release planning pass; add Android 17 behavior tracker, AAPM
   updates, AGP 10 timeline (mid-2026 opt-out removal).

- [ ] **Android TV flavor (C22)** — leanback metadata, D-pad navigation,
  acknowledging that many TV firmwares ignore `ColorDisplayManager`.

- [ ] **AMOLED-aware content-aware dimming (C67)** — privacy-heavy; requires
  `MediaProjection` or accessibility access. After Android 17 AAPM
  (S88) the accessibility door is closed for us; `MediaProjection`
  shows a recording indicator that's terrible UX for an always-on
  filter. Likely stays Later indefinitely.

- [ ] **Partial-screen filters (C68)** — same per-app blocker as C11.

- [ ] **Pixel-grid AMOLED dimming (C89)** — Pixel Filter's idea; risky
  given Android 12+ untrusted-touch and overlay-alpha rules. Burn-in
  perception concern.

- [ ] **PWM-sensitive workflow guidance** — document the OLED Saver (S103)
  / Iris approach without claiming health benefits.

- [ ] **Multi-user / work-profile behavior (C81)** — polish after C11/C12.

- [ ] **Local diagnostics viewer with timeline filtering** — already
  shipped as C53; the filter-by-category/level stretch also shipped
  2026-05-17 (see C53 entry in Progress). Remaining stretch: timeline
  scrubbing (jump to range), text search within filtered subset.

- [ ] **Optional Play Store listing (C39)** — `specialUse` evidence pack
  is ready (C93); we just have not committed maintenance bandwidth.
  See [docs/play-fgs-evidence.md](docs/play-fgs-evidence.md).

- [ ] **System brightness write support (C86)** — confusing UX (two
  brightness sliders); probably reject.

- [ ] P2 — C221 — Ambient threshold has no hysteresis or dwell protection
  Category: reliability
  Where: app/src/main/java/com/openlumen/service/LumenService.kt:483-489 and app/src/main/java/com/openlumen/sensor/LightSensorAdapter.kt:55-77
  Problem: The active decision uses a single lux < threshold comparison. EMA smoothing and a 5% distinct filter reduce noise but do not prevent repeated enter/exit transitions when lux hovers around the threshold, causing ramp cancellation/restart, alarm churn, and visible flicker.
  Evidence: There are no separate engage/disengage thresholds, consecutive-sample counters, or minimum dwell time in the service or sensor adapter. Every distinct reading is passed to the same direct comparison and can change shouldBeActive.
  Fix: Add configurable or fixed hysteresis (for example, enter below the threshold and exit above a higher threshold) and/or require a bounded dwell/consecutive-sample period. Make the chosen behavior visible in helper text and cover it with a noisy-boundary test.
  Acceptance: A lux sequence oscillating around the threshold does not repeatedly toggle the engine; crossing the exit threshold or completing the dwell changes state promptly and exactly once.
  Confidence: Likely
  Effort: M

- [ ] P2 — C222 — Notification countdown becomes stale after an alarm or reevaluation
  Category: ux
  Where: app/src/main/java/com/openlumen/service/LumenService.kt:321-368,423-425, ScheduleAlarmReceiver.kt, and app/src/main/res/values/strings.xml:207-212
  Problem: The "until alarm" countdown is computed only during preference emissions through updateNotificationSubtitle. ACTION_REEVALUATE and ScheduleAlarmReceiver do not update it, and there is no chronometer/timer. The notification can continue showing a future countdown after the alarm fired or show a past value until an unrelated preference change.
  Evidence: The countdown fields are read from preferences and updated in the emission handler; the receiver's transition path starts/re-evaluates service work without a notification refresh. No scheduled countdown update exists between emissions.
  Fix: Refresh the subtitle whenever a transition/reevaluation changes the next alarm, or use a platform chronometer/strictly bounded periodic update for a live countdown. Clear the countdown when no future alarm exists.
  Acceptance: Delivering the scheduled alarm or an explicit reevaluation updates/removes the countdown immediately; notification text never reports a stale/past next alarm after the service has recomputed the schedule.
  Confidence: Verified
  Effort: M

- [ ] P2 — C223 — Foreground notification copy claims "active" while the service is only on standby
  Category: ux
  Where: app/src/main/res/values/strings.xml:207-212 and app/src/main/java/com/openlumen/service/LumenService.kt:289-302,321-368
  Problem: The foreground notification title is always "OpenLumen active" whenever the enabled service is running, even when a fixed/solar schedule is outside its interval or an ambient threshold is not met. The subtitle only adds the next alarm and does not state that the filter is currently inactive.
  Evidence: startInForeground uses the same active title for every p.enabled service state; the service distinguishes shouldBeActive internally but does not map it to notification title/status copy. Users therefore cannot tell "service running" from "filter currently applied."
  Fix: Provide separate service-running/standby/actively-filtering copy and update the notification on active-state transitions, retaining the next-transition information. Use calm, unambiguous terminology consistent with Home status text.
  Acceptance: An enabled but out-of-window service notification explicitly says standby/not filtering, while an applied matrix says filtering active; transitions update without restarting the service.
  Confidence: Likely
  Effort: S

- [ ] P2 — C224 — Notification permission denial is treated as permanent and cannot be retried in-app
  Category: ux
  Where: app/src/main/java/com/openlumen/ui/HomeScreen.kt:83-109, requestNotifIfNeeded, and CHANGELOG.md:332-336
  Problem: The permission result stores notification_permission_asked=true for every denial, and future requests are blocked by that flag. The code does not check shouldShowRequestPermissionRationale or offer a notification-settings route, so a first temporary denial prevents the app from explaining/re-requesting the permission later. The changelog says the prompt should re-fire when rationale is true, but the current implementation does not do so.
  Evidence: The result callback writes the asked flag unconditionally; the launch guard checks only API level and the stored flag. No rationale branch or permanent-denial recovery action is present in the traced Home flow.
  Fix: Track first request, temporary denial, and permanent denial separately; show a rationale with Retry when Android permits another request and a Settings action when it does not. Keep notification-dependent behavior clearly marked when permission is absent.
  Acceptance: A first denial can be retried after the rationale is shown, a permanent denial opens the app notification settings, and tests cover both result states without repeatedly nagging the user.
  Confidence: Verified
  Effort: M

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
