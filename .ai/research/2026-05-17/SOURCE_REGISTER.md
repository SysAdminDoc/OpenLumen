# Source Register — 2026-05-17

Every local and external source used in this research pass. Inherits the
roadmap's `S00`-`S125` namespace from `ROADMAP.md` rev 3 and adds new
entries through `S281` collected during this session and implementation
passes.

## Local evidence (S00 / S00b-S00m)

- **S00**: Local repo reconnaissance on 2026-05-17: working tree, `git status`,
  `git diff --stat HEAD`, last 30 commits, `gradle/libs.versions.toml`,
  manifests, full Kotlin source tree (62 `.kt` files across 4 modules),
  20 docs under `docs/`, `.github/workflows/*`,
  `fastlane/metadata/android/`, `branding/logo-prompts.md`.
- **S00b**: 2026-05-17 in-tree audit hardening pass — 16 files modified on
  disk pre-commit; ROADMAP rev 3 enumerates the fixes.
- **S00c**: 2026-05-17 roadmap execution pass for C132-C136:
  `LumenService.kt`, `ColorDisplayManagerEngine.kt`,
  `SurfaceFlingerEngine.kt`, `KcalEngine.kt`, and `OverlayEngine.kt`
  were patched and verified with unit tests, debug assemble, lint, and
  `git diff --check`.
- **S00d**: 2026-05-17 BAL hardening audit for C111: `rg` found no
  `IntentSender`, `ActivityOptions`, `setPendingIntentBackgroundActivityStartMode`,
  or `MODE_BACKGROUND_ACTIVITY_START_*` call sites; existing pending
  intents are direct `getActivity`, `getService`, and `getBroadcast`
  paths in `LumenService`, widgets, and schedule alarms.

## Existing roadmap sources (S10 - S125 — preserved verbatim)

See `ROADMAP.md` rev 3 lines 660-788 for the canonical list. Summary:

- S10-S22 / S69-S71 / S81-S82 / S86 / S87 / S103: direct OSS / commercial
  competitors and ancestors.
- S23-S33 / S43-S44: commercial references and platform docs.
- S25-S29 / S65-S68 / S73-S74: Android platform behavior and OWASP
  references.
- S34-S40 / S72 / S104-S106: desktop / Wayland adjacent projects.
- S45-S47 / S99-S102: sleep / circadian-rhythm research base.
- S48-S59 / S109-S111: distribution and dependency metadata.
- S60-S64 / S77 / S108 / S110 / S114 / S122: security and supply-chain.
- S75-S76 / S91-S98 / S118 / S123-S125: AGP / Hilt / Compose / Glance.
- S83-S85 / S88-S90 / S96 / S121: Android 17 + AAPM behavior changes.
- S107 / S80: PWM signals.
- S112-S117 / S119-S120: F-Droid, emulator runner, Wear OS, CVD.

## New sources (this session — 2026-05-17)

Numbered S126 onward to extend `ROADMAP.md` rev 3 without breaking existing
citations. Topic groupings:

### Android 17 platform — release timing, behavior changes, FGS rules, AAPM, BAL

- **S126**: Android 17 Beta 4 announcement (Android Developers Blog,
  2026-04-16). Final beta; platform stability reached; API surface frozen.
  — https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html
- **S127**: Android 17 release notes. Canonical landing page for Android 17
  changes, APIs, and version history.
  — https://developer.android.com/about/versions/17/release-notes
- **S128**: Behavior changes — apps targeting Android 17. Covers
  `MessageQueue` rewrite, BAL hardening
  (`MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated → `_ALLOW_IF_VISIBLE`
  recommended), background audio hardening, Certificate Transparency by
  default, `ACCESS_LOCAL_NETWORK`, OTP filtering, CP2 PII restrictions.
  — https://developer.android.com/about/versions/17/behavior-changes-17
- **S129**: Android 17 features and APIs. Lists `AdvancedProtectionManager`,
  Handoff, semantic-color Live Updates, ProfilingManager triggers,
  `JobDebugInfo`, ECH, contact picker.
  — https://developer.android.com/about/versions/17/features
- **S130**: Changes to foreground services. Canonical doc for FGS launch
  rules: `BOOT_COMPLETED` cannot start `dataSync` / `mediaPlayback` /
  `phoneCall` types; WIU permissions blocked from background; SAW apps must
  have a visible overlay window.
  — https://developer.android.com/develop/background-work/services/fgs/changes
- **S131**: FGS background-start restrictions. Confirms `SYSTEM_ALERT_WINDOW`
  exemption only applies when a `TYPE_APPLICATION_OVERLAY` is currently
  visible — directly relevant to OpenLumen's tile/widget toggle-on flow
  (C105 in the rev 3 roadmap).
  — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- **S132**: Foreground service types. Documents `specialUse` and the
  `<property>` manifest declaration with free-form use-case strings
  reviewed in Play Console — confirms our manifest property text is the
  required shape.
  — https://developer.android.com/develop/background-work/services/fgs/service-types
- **S133**: Background audio hardening (Android 17). New WIU requirement
  for any background audio interaction — useful context for the broader
  FGS hardening direction; OpenLumen doesn't touch audio so no direct
  impact.
  — https://developer.android.com/about/versions/17/changes/bg-audio
- **S134**: `AdvancedProtectionManager` API reference. Primary API surface
  for detecting AAPM state and registering callbacks. **Roadmap impact**:
  consider exposing AAPM state in the driver report so users on AAPM-on
  devices see *why* an a11y-using competitor's per-app feature isn't
  available there (C104 / new candidate C130).
  — https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager
- **S135**: AAPM landing page (developer.android.com/privacy-and-security).
  Official user-facing description of AAPM (sideloading block, USB data
  restriction, Play Protect mandatory, **accessibility-API auto-
  revocation for non-`isAccessibilityTool` apps**).
  — https://developer.android.com/privacy-and-security/advanced-protection-mode
- **S136**: AndroidPolice — Android 17 Beta 2 AAPM accessibility auto-
  revocation deep-dive (Feb 2026). Confirms automatic revocation of
  previously-granted accessibility perms for non-accessibility apps when
  AAPM is on.
  — https://www.androidpolice.com/advanced-protection-mode-android-17-beta-accessibility/
- **S137**: Background activity launch restrictions (developer.android.com).
  Covers PendingIntent/IntentSender BAL inheritance, `ActivityOptions`
  modes including `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` and
  `_ALLOW_ALWAYS` introduced in Android 16.
  — https://developer.android.com/guide/components/activities/background-starts
- **S138**: AOSP Night Light implementation guide (source.android.com).
  `ColorDisplayManager` / `ColorDisplayService` remain the core color-
  tint surface; **no new Android 17 Night Light API**. Useful negative
  confirmation that our CDM engine reflection ladder doesn't need an
  Android 17-specific case.
  — https://source.android.com/docs/core/display/night-light
- **S139**: Android 17 Eye Dropper API overview (ProAndroidDev, Mar 2026).
  New system-level color-pick intent (`android.intent.action.OPEN_EYE_DROPPER`)
  that returns a pixel color without screen-capture permission. **Possible
  feature**: OpenLumen's custom-RGB picker on Home could optionally call
  this intent on Android 17 devices to let users sample a target color
  from anything on screen. Tracked as new candidate C131.
  — https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16

### AGP 9 / 10

- **S140**: AGP 9.0.1 release notes (Jan 2026). Built-in Kotlin support,
  new DSL/Variant API stable, removed wearApp embedding and density splits,
  max compileSdk 36.1.
  — https://developer.android.com/build/releases/agp-9-0-0-release-notes
- **S141**: AGP 9.1.1 release notes (Apr 2026). Incremental fixes layered
  on 9.0.
  — https://developer.android.com/build/releases/agp-9-1-0-release-notes
- **S142**: AGP 9.2.0 release notes (Apr 2026). Latest in the 9.x line.
  — https://developer.android.com/build/releases/agp-9-2-0-release-notes
- **S143**: AGP DSL/API migration timeline. Confirms AGP 10 (late 2026)
  deletes legacy Variant/DSL APIs entirely; `android.enableLegacyVariantApi`
  flag will no longer exist. **Reinforces rev 3's promotion of C95 to Now.**
  — https://developer.android.com/build/releases/gradle-plugin-roadmap

### AndroidX Hilt

- **S144**: AndroidX Hilt releases page. Confirms `hiltViewModel()` moved to
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` (package
  `androidx.hilt.lifecycle.viewmodel.compose`) in 1.3.0-alpha02 (2025-07-02);
  stable 1.3.0 in 2025-09.
  — https://developer.android.com/jetpack/androidx/releases/hilt
- **S145**: `hilt-lifecycle-viewmodel-compose` on Maven Central. Direct
  artifact coordinates for the new dependency.
  — https://mvnrepository.com/artifact/androidx.hilt/hilt-lifecycle-viewmodel-compose/

### AndroidX DataStore (Direct Boot)

- **S146**: DataStore releases page. `createInDeviceProtectedStorage()` on
  `DataStoreFactory` and `deviceProtectedDataStore()` delegate landed in
  1.2.0-alpha01; min SDK 24. **Confirms rev 3's effort drop for C28 / C102.**
  — https://developer.android.com/jetpack/androidx/releases/datastore
- **S147**: DataStore architecture guide. General reference for citing
  alongside the Direct Boot APIs.
  — https://developer.android.com/topic/libraries/architecture/datastore

### Compose screenshot testing

- **S148**: Compose Preview Screenshot Testing guide. Current minimum
  AGP 9.0 + plugin `0.0.1-alpha14` + Kotlin 2.2.10 + JDK 17 (as of
  Apr 2026). **Note for C101**: this confirms the rev 3 "now-cheap" claim
  but the plugin is still in alpha, so we should track stable readiness.
  — https://developer.android.com/studio/preview/compose-screenshot-testing
- **S149**: Compose Preview Screenshot Testing release notes. Records
  AGP 9.0 compatibility and JDK 24 support; tool still alpha.
  — https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes
- **S150**: Roborazzi GitHub (takahirom/roborazzi). JVM screenshot lib
  built on Robolectric; AGP 9.0 compatibility added; used by Google's
  Now in Android sample. (Maps to rev 3's S98 / S125 — reaffirmed.)
  — https://github.com/takahirom/roborazzi
- **S151**: Roborazzi releases.
  — https://github.com/takahirom/roborazzi/releases
- **S152**: Paparazzi GitHub (cashapp/paparazzi). Renders Android screens
  on the JVM with no emulator; Java 17+ for recent versions. **Alternative
  to Roborazzi for C122**, with different tradeoffs (more mature golden-
  image tooling, less Robolectric coverage).
  — https://github.com/cashapp/paparazzi
- **S153**: Paparazzi changelog.
  — https://cashapp.github.io/paparazzi/changelog/

### F-Droid

- **S154**: F-Droid Reproducible Builds docs. Primary RB configuration
  guidance for `fdroiddata`.
  — https://f-droid.org/docs/Reproducible_Builds/
- **S155**: "Making reproducible builds visible" (F-Droid blog, 2025-05-21).
  Introduces per-app reproducibility-status indicator on f-droid.org.
  **New roadmap consideration**: track our per-app indicator after first
  F-Droid build.
  — https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html
- **S156**: F-Droid forum — removing `META-INF/version-control-info.textproto`.
  Community thread documenting how AGP 8.3+ bundles VCS metadata that
  breaks reproducibility, with disabling guidance. **Direct fix for C120
  in rev 3.**
  — https://forum.f-droid.org/t/how-can-i-prevent-version-control-info-textproto-from-being-included-in-my-apk/33196
- **S157**: F-Droid Translation and Localization policy. Codifies the
  "70% complete for final release, all translations for alpha" rule
  (rev 3's S111).
  — https://f-droid.org/docs/Translation_and_Localization/

### Sleep / circadian-rhythm (2025-2026 evidence base)

- **S158**: Frontiers in Neurology — "Efficacy of blue-light blocking
  glasses on actigraphic sleep outcomes: systematic review and meta-
  analysis" (2025). Trial evidence remains inconsistent; effect sizes
  small and heterogeneous.
  — https://www.frontiersin.org/journals/neurology/articles/10.3389/fneur.2025.1699303/full
- **S159**: Nature Scientific Reports — "Home lighting, blue-light
  filtering, and their effects on melatonin suppression" (2025). Lamp
  colour temperature and melanopic content drive suppression; intensity
  is a major lever. (Reinforces rev 3's S100.)
  — https://www.nature.com/articles/s41598-025-29882-7
- **S160**: medRxiv — "Effects of Melanopic Equivalent Daylight Illuminance
  on Sleep Regulation and Chronotype-Specific Responses" (Oct 2025).
  Argues melanopic EDI is the right metric (combines intensity and
  spectrum) for predicting sleep regulation. **Roadmap impact**: validates
  C127's "perceived luminance reduction" indicator direction.
  — https://www.medrxiv.org/content/10.1101/2025.10.21.25338466v1.full
- **S161**: Cochrane — Blue-light-filtering spectacles probably make no
  difference to eye strain or sleep. Most-cited authoritative negative
  finding for the popular "blue-blocker" claim; still in 2025 doc set.
  — https://www.cochrane.org/about-us/news/blue-light-filtering-spectacles-probably-make-no-difference-eye-strain-eye-health-or-sleep
- **S162**: SAGE Journals — "Blue-light-filtering spectacle lenses in
  managing vision-related symptoms: an updated review" (2026). 2026
  update confirms little/no clinical benefit on visual fatigue.
  — https://journals.sagepub.com/doi/10.1177/25158414251412798

### Shizuku ecosystem 2026

- **S163**: Shizuku releases (RikkaApps/Shizuku). v13.6.0 (2025-05-25)
  is the current line; adds Android 16 QPR1 support and auto-start over
  trusted Wi-Fi on Android 13+. (Maps to rev 3's S115 — refreshed.)
  — https://github.com/RikkaApps/Shizuku/releases
- **S164**: awesome-shizuku (timschneeb). Curated index of Shizuku-using
  apps. (Maps to rev 3's S116 — refreshed.)
  — https://github.com/timschneeb/awesome-shizuku
- **S165**: AndroidAuthority — "10 awesome Shizuku apps I use to level up
  my Android experience" (2025-2026). Identifies `CurrentActivity` as a
  Shizuku-backed foreground-task monitor — pattern OpenLumen would adopt
  for C06 / C11 / C12 / C69.
  — https://www.androidauthority.com/best-shizuku-apps-android-3659353/

### Competitor sweep (S166-S184)

- **S166**: EcoDimmer (cartman-156). 1★ / v1.0.0 May 2026 / MIT / Kotlin.
  **Privacy-hardened AccessibilityService overlay** — explicitly disables
  `canRetrieveWindowContent` and `flagRequestFilterKeyEvents`. Plus
  accelerometer "Shake to Rescue" panic-disable; hidden launcher icon.
  Borrowable: a11y-service config that draws above status bar without
  claiming a11y read access.
  — https://github.com/cartman-156/EcoDimmer
- **S167**: Grayscaler (C10udburst). 143★ / v1.0 Feb 2025 / GPL-3.0. **Per-
  app grayscale via Shizuku** with the minimal permission triple
  `WRITE_SECURE_SETTINGS + PACKAGE_USAGE_STATS + QUERY_ALL_PACKAGES`
  granted in one Shizuku flow plus an Accessibility Service watching
  foreground transitions. Direct reference for C06/C11/C69.
  — https://github.com/C10udburst/Grayscaler
- **S168**: ColorBlendr (Mahmud0808). 2.1k★ / v2.1.1 Jan 2026 / GPL-3.0.
  Three-tier privilege ladder (Root → Shizuku → Wireless ADB). Uses the
  Android 12+ `FabricatedOverlay` API to mutate Material You tokens at
  runtime without persistent files. **Tracks as new candidate C128** —
  potential fifth engine.
  — https://github.com/Mahmud0808/ColorBlendr
- **S169**: Adaptive Theme (xLexip). 123★ / v2.0.0 Apr 2026 / GPL-3.0.
  Event-driven ambient-light sensor read only on screen-on; offers 4 setup
  paths for `WRITE_SECURE_SETTINGS` (web tool, Shizuku, root, manual ADB).
  Reinforces C99 (screen-off invalidation, shipped) and gives a UX
  template for Driver-tab onboarding.
  — https://github.com/xLexip/Adaptive-Theme
- **S170**: sunsetr (psi4j). 270★ / v0.11.1 Nov 2025 / MIT / Rust [Wayland].
  Named-preset profiles (Reading / Gaming / Sleep) bound to tile/widget;
  Unix-socket IPC; hot config reload; 10k-city interactive picker.
  Borrowable: richer city DB than our ~95.
  — https://github.com/psi4j/sunsetr
- **S171**: hyprsunset (hyprwm). 437★ / v0.3.3 Oct 2025 / BSD-3 / C++
  [Wayland/Hyprland]. Kelvin → 3x3 CTM matrix conversion the same way
  AOSP `ColorDisplayManager` does it.
  — https://github.com/hyprwm/hyprsunset
- **S172**: wl-gammarelay-rs (MaxVerevkin). 176★ / v1.0.1 Mar 2025 /
  GPL-3.0 / Rust [Wayland]. DBus-controlled daemon; analog to our intent
  surface.
  — https://github.com/MaxVerevkin/wl-gammarelay-rs
- **S173**: nerdshade (sstark). 19★ / v1.3.0 Jun 2025 / MIT / Go [Wayland].
  Transition *curve* across separate Kelvin and dim easing windows;
  `acpi_listen` lid-open resume.
  — https://github.com/sstark/nerdshade
- **S174**: cosmos (ext0l on Codeberg). 2★ / v1.0.0 Jun 2024 / last commit
  Feb 2026 / Rust. **OLED-aware brightness emulation** — gamma LUT keeps
  `(0,0,0)` truly off. Tracks as new candidate C129.
  — https://codeberg.org/ext0l/cosmos
- **S175**: Solace (Theodore HQ, macOS). $4.99 one-time / closed-source /
  zero-telemetry. Weather-aware tinting (overcast → warmer). Out-of-scope
  for OpenLumen offline-first but logged.
  — https://www.theodorehq.com/solace/
- **S176**: Shifty (thompsonate, macOS). 1.3k★. **Per-website Night Shift
  disable** via AppleScript browser bridges. Validates per-app/per-
  context exclusion as industry-standard.
  — https://github.com/thompsonate/Shifty
- **S177**: LightBulb v2 (Tyrrrz, Windows). 2.7k★ / v2.7 Mar 2026 / MIT.
  Minimum-API-calls engine throttling. Battery-life parallel.
  — https://github.com/Tyrrrz/LightBulb
- **S178**: Nocturnal (joshjon, macOS). 320★ / archived 2024-08. Below-
  system-minimum dimming via gamma table — same as Red Moon overlay
  engine technique. **Archived after macOS Sonoma broke private gamma
  APIs** — cautionary tale for our `ColorDisplayManagerEngine` reflection
  ladder.
  — https://github.com/joshjon/nocturnal
- **S179**: LSFG-Android (FrankBarretta). 521★ / v0.1.2 May 2026 / custom
  non-commercial / Kotlin+C++. **Cleanest 2026 per-app overlay
  architecture**: AccessibilityService for visible overlay layer,
  `ITaskStackListener` via Shizuku-bound `IActivityManager` for
  foreground detection, **Shizuku off the hot path**. Reference
  architecture for C06.
  — https://github.com/FrankBarretta/LSFG-Android
- **S180**: DarQ (KieronQuinn). 1.6k★ / v2.2.1 Feb 2022. Per-app force-
  dark via Shizuku-elevated `IActivityManager.ITaskStackListener` — "no
  a11y needed" architecture. Last commit 2022 but architecturally sound.
  — https://github.com/KieronQuinn/DarQ
- **S181**: RootlessJamesDSP (timschneeb, audio). Active 2025. Reference
  for Shizuku service-binding patterns / session survival.
  — https://github.com/timschneeb/RootlessJamesDSP
- **S182**: TvOverlay (gugutab). 318★ / v1.0.3 Oct 2023. Android TV overlay
  with **REST API + MQTT + Home Assistant**. Integration story for our
  C22 (TV flavor).
  — https://github.com/gugutab/TvOverlay
- **S183**: GitHub topic — `blue-light-filter`. Live index for ongoing
  discovery.
  — https://github.com/topics/blue-light-filter
- **S184**: GitHub topic — `screen-dimmer`. Live index.
  — https://github.com/topics/screen-dimmer

### PWM signals (2025-2026 secondary)

- **S185**: AndroidCentral — "My phone is making me sick and I'm not alone."
  First-person AMOLED-PWM piece; widely-cited mainstream framing.
  — https://www.androidcentral.com/phones/my-phone-is-making-me-sick-and-im-not-alone
- **S186**: AndroidCentral — "Best phones for PWM/Flicker sensitive people"
  (2026). Continually updated buyer's guide; primary evidence that the
  community signal is live in 2026.
  — https://www.androidcentral.com/phones/best-phones-for-pwm-flicker-sensitive
- **S187**: AndroidCentral — "What is PWM dimming, and what are the
  alternatives?" Reference explainer.
  — https://www.androidcentral.com/phones/what-is-pwm-display-flicker-tips-and-tricks

### OWASP MASVS / MASTG 2025-2026

- **S188**: OWASP MASTG-KNOW-0022 — Overlay Attacks knowledge entry.
  Canonical taxonomy (full vs partial occlusion). Direct reference for
  `docs/threat-model.md` MASVS-PLATFORM section.
  — https://mas.owasp.org/MASTG-KNOW-0022/
- **S189**: OWASP MASTG-TEST-0035 — Testing for Overlay Attacks. Aligned
  to MASVS-PLATFORM.
  — https://mas.owasp.org/MASTG-TEST-0035/
- **S190**: OWASP MASWE-0056 — Tapjacking weakness entry (newer MASWE
  catalogue).
  — https://mas.owasp.org/MASWE-0056/
- **S191**: OWASP MASTG releases. Tracks MASTG v1.6.0 (CycloneDX
  checklists, MASVS color-coding) and subsequent v2 work.
  — https://github.com/OWASP/mastg/releases
- **S192**: OWASP MASVS v2.1.0 release notes. Adds the MASVS-PRIVACY
  category; current baseline. **Roadmap implication**: review
  `docs/threat-model.md` for MASVS-PRIVACY coverage gaps.
  — https://github.com/OWASP/masvs/releases/tag/v2.1.0

### Glance widgets

- **S193**: AndroidX Glance releases page. Glance went stable at 1.0.0;
  1.1.0 stable shipped 2024-06-12 — **confirms current stability (no
  longer alpha).** Lifts the rev 3 blocker on C123 (Glance widget
  rewrite).
  — https://developer.android.com/jetpack/androidx/releases/glance
- **S194**: Jetpack Glance overview (developer.android.com). Canonical doc
  for using Glance composables.
  — https://developer.android.com/develop/ui/compose/glance

### Red Moon / NightLight current activity (refreshed from rev 3)

- **S195**: LibreShift/red-moon repository. Maintenance-mode framing.
  (Refresh of S10.)
  — https://github.com/LibreShift/red-moon
- **S196**: Red Moon issue tracker 2026 sample. Issues observed: #354
  Backup (2026-04-05), #353 Filter green light/melanopsin (2026-03-05),
  #352 Monochromatic icon (2025-12-31), #351 One-handed dimming
  (2025-12-31). (Refresh of S12 / S86.)
  — https://github.com/LibreShift/red-moon/issues
- **S197**: Red Moon issue #281 — maintainer commits to roughly yearly
  releases for translation refreshes; no new features planned.
  — https://github.com/LibreShift/red-moon/issues/281
- **S198**: Twilight on APKPure — current public listing shows v14.21 line
  (latest v14.25 reported 2026-02-09); changelog notes Philips HUE Pro
  bridge support, expressive redesign, new translations, paper-matte
  texture, bigger pause button, target-SDK bump.
  — https://apkpure.com/twilight-blue-light-filter/com.urbandroid.lux
- **S199**: corphish/NightLight — root-required KCAL-based filter
  (Qualcomm display driver); maintenance-mode but actively distributed
  on F-Droid.
  — https://github.com/corphish/NightLight
- **S200**: farmerbb/Night-Light — wrapper that toggles Android Nougat's
  native night-mode flag (no overlay); reference for permission-minimal
  designs.
  — https://github.com/farmerbb/Night-Light
- **S201**: cngu/shades — lightweight Kotlin screen-tint/dimmer; small
  surface area; good reference architecture.
  — https://github.com/cngu/shades

### Eye Dropper (Android 17 system intent)

- **S202**: Android 17 Eye Dropper API overview — refreshed pointer for
  the system-level color-pick intent. (Listed under S139; included again
  here for grouping.)
  — https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16

## Second-pass additions (S203-S229)

These were collected during the 2026-05-17 second pass by background
research agents. See `SECOND_PASS_FINDINGS.md` for the analysis they
informed.

### F-Droid submission status / process

- **S203**: F-Droid `fdroiddata` MR list — zero matches for "openlumen"
  as of 2026-05-17. https://gitlab.com/fdroid/fdroiddata/-/merge_requests
- **S204**: F-Droid RFP issue tracker — zero matches for "openlumen" or
  `SysAdminDoc`. https://gitlab.com/fdroid/rfp/-/issues
- **S205**: f-droid.org app search — "OpenLumen" not listed.
  https://search.f-droid.org/?q=openlumen&lang=en
- **S206**: F-Droid Quick Start Guide for submitting an app — canonical
  workflow as of 2026-05.
  https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- **S207**: F-Droid Translation and Localization — 70%-complete-for-final-
  release rule unchanged in 2026.
  https://f-droid.org/docs/Translation_and_Localization/
- **S208**: AGP 9.2.0 release notes (Apr 2026) — buildserver supports
  JDK 17 minimum / JDK 21 recommended.
  https://developer.android.com/build/releases/agp-9-2-0-release-notes
- **S209**: Google Play target-SDK requirement page — `targetSdk 36`
  effective Aug 2025; F-Droid does not enforce Play requirements but
  the matching AGP 9 / JDK 17+ posture is required.
  https://developer.android.com/google/play/requirements/target-sdk
- **S210**: F-Droid Anti-Features list — `specialUse` FGS is NOT
  flagged as an anti-feature.
  https://f-droid.org/docs/Anti-Features/
- **S211**: F-Droid TWIF April 2026 — most recent metadata-process
  post; no breaking submission-format changes.
  https://f-droid.org/en/2026/04/03/twif.html

### Shizuku integration patterns 2026

- **S212**: RikkaApps/Shizuku-API — canonical Kotlin code shapes for
  permission, binding, and binder-survival listeners.
  https://github.com/RikkaApps/Shizuku-API
- **S213**: RikkaApps/Shizuku — v13.6.0 + companion docs.
  https://github.com/RikkaApps/Shizuku
- **S214**: ShizukuActivityManager — reference for resolving the right
  transaction codes across API levels via `SystemServiceHelper`.
  https://github.com/kzaemrio/ShizukuActivityManager
- **S215**: Android-FPS-Watcher — reference impl for
  `IActivityTaskManager.registerTaskStackListener` via Shizuku-bound
  binder; the foreground-task-detection pattern that avoids
  `UsageStats` and `AccessibilityService`.
  https://github.com/WuDi-ZhanShen/Android-FPS-Watcher
- **S216**: AOSP `ITaskStackListener.aidl` (canonical AIDL).
  https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/ITaskStackListener.aidl
- **S217**: AOSP `IActivityManager.aidl` (mirror, current main branch).
  https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/IActivityManager.aidl
- **S218**: Grayscaler (refresh of S167) — Shizuku for
  `Settings.Secure` writes; AccessibilityService for fg detection
  (counter-example to the Shizuku-only pattern).
  https://github.com/C10udburst/Grayscaler
- **S219**: ColorBlendr (refresh of S168) — production Shizuku →
  `IOverlayManager` binder pattern for Material You overlays on
  Android 12+.
  https://github.com/Mahmud0808/ColorBlendr
- **S220**: LSFG-Android (refresh of S179) — `UserService` AIDL
  pattern for long-lived privileged code; Shizuku used for a UID-
  filtered timing side channel, off the hot path.
  https://github.com/FrankBarretta/LSFG-Android
- **S221**: awesome-shizuku index 2025-2026.
  https://github.com/timschneeb/awesome-shizuku

### FabricatedOverlay 12L+ constraint

- **S222**: AOSP `FabricatedOverlay` API reference.
  https://developer.android.com/reference/android/content/om/FabricatedOverlay
- **S223**: zacharee/FabricateOverlay Kotlin library — README documents
  the Android 12L change that **blocks Shizuku-in-ADB-mode from creating
  FabricatedOverlays**; only Shizuku-on-root or Sui can. **Critical
  finding**: changes the tier calculus on C128.
  https://github.com/zacharee/FabricateOverlay

### Security advisory negative result

- **S224**: GitHub Advisory Database — zero entries for "shizuku" as
  of 2026-05-17.
  https://github.com/advisories?query=shizuku

### Compose BOM / Material 3 / AGP 9 migration targets

- **S225**: Compose BOM mapping — `2026.05.00` → compose 1.11.1 /
  material3 1.4.0.
  https://developer.android.com/develop/ui/compose/bom/bom-mapping
- **S226**: Compose core release notes — 1.11.1 stable.
  https://developer.android.com/jetpack/androidx/releases/compose
- **S227**: Compose Material3 release notes — 1.4.0 stable (May 2026);
  `material3-expressive` still alpha (`1.5.0-alpha19`).
  https://developer.android.com/jetpack/androidx/releases/compose-material3
- **S228**: Android Developers Blog — Jetpack Compose April 2026
  updates.
  https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html
- **S229**: Compose Material Icons package summary — `material-icons-
  extended` deprecated; migrate to Material Symbols / `Icons.AutoMirrored`.
  **New roadmap candidate C137**.
  https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary

## Third-pass additions (S230-S256)

### Android developer verification / off-Play distribution

- **S230**: Android developer verification landing page — starting in
  September 2026, apps in Brazil, Indonesia, Singapore, and Thailand
  must be registered by a verified developer to install on certified
  Android devices; applies outside Play as well.
  https://developer.android.com/developer-verification
- **S231**: Android developer verification guides — explains the Android
  Developer Console path for apps distributed only outside Google Play,
  package-name registration, signing-key proof, and 2027+ global rollout.
  https://developer.android.com/developer-verification/guides
- **S232**: Android developer verification FAQ — confirms non-compliance
  leads to install blocks in applicable regions and notes ADB installs
  remain possible for developers. Feeds C141.
  https://developer.android.com/developer-verification/guides/faq

### Android 17 behavior gaps found in pass 3

- **S233**: Android 17 landing page — Beta 4 available; app developers
  should test compatibility and release updates before stable.
  https://developer.android.com/about/versions/17
- **S234**: Android 17 release notes — Beta 4 released 2026-04-16 and
  introduces app memory limits surfaced through `ApplicationExitInfo`
  descriptions containing `MemoryLimiter`.
  https://developer.android.com/about/versions/17/release-notes
- **S235**: Android 17 behavior changes for all apps — app memory limits,
  `ApplicationExitInfo` diagnostics, per-app keystore limits, and other
  all-app behavior changes.
  https://developer.android.com/about/versions/17/behavior-changes-all
- **S236**: Android 17 restrictions on orientation and resizability are
  ignored — target-37 apps cannot rely on `screenOrientation`,
  `resizeableActivity`, min/max aspect ratio, or requested-orientation
  APIs on sw600dp+ displays. Feeds C143.
  https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored

### Dependency and build-tool targets

- **S237**: Android Gradle plugin 9.2.0 release notes — AGP 9.2 supports
  maximum API level 36.1 and requires Gradle 9.4.1, Build Tools 36.0.0,
  and JDK 17.
  https://developer.android.com/build/releases/gradle-plugin
- **S238**: Gradle 9.4.1 release notes — patch release for Gradle 9.4.0,
  released 2026-03-19, with Java 26 support.
  https://docs.gradle.org/9.4.1/release-notes.html
- **S239**: AndroidX releases overview — current stable snapshot as of
  May 2026: activity 1.13.0, core 1.18.0, lifecycle 2.10.0,
  navigation 2.9.8, compose 1.11.1, material3 1.4.0, DataStore 1.2.1.
  https://developer.android.com/jetpack/androidx/versions
- **S240**: Dagger releases — Dagger/Hilt 2.59.2 current; Hilt's AGP 9
  support replaced the old transform and fixed slow incremental builds.
  Hilt 2.59.1 explicitly raised the Hilt Gradle plugin minimum to AGP 9.
  https://github.com/google/dagger/releases
- **S241**: Hilt Android Maven page — latest stable Hilt Android
  artifacts are 2.59.2, while OpenLumen is on 2.53.1.
  https://mvnrepository.com/artifact/com.google.dagger/hilt-android/versions
- **S252**: DataStore release notes — stable 1.2.1; Direct Boot support
  landed in 1.2.0 with `createInDeviceProtectedStorage()` and
  `deviceProtectedDataStore()`.
  https://developer.android.com/jetpack/androidx/releases/datastore
- **S253**: Compose Material 3 release notes — stable 1.4.0 as of
  2026-05-06; `material3` alpha line continues separately.
  https://developer.android.com/jetpack/androidx/releases/compose-material3

### GitHub Actions / CI supply chain

- **S242**: GitHub Changelog — Node 20 deprecation for GitHub Actions
  runners; runners begin defaulting JavaScript actions to Node 24 on
  2026-06-02. Feeds C142.
  https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/
- **S243**: GitHub Actions secure-use reference — GitHub states full-length
  commit SHA pinning is the only way to use an action as an immutable
  release.
  https://docs.github.com/en/actions/reference/security/secure-use
- **S244**: `actions/checkout` repository — latest documented major is
  v6; checkout v5 improved credential security by storing credentials
  under `$RUNNER_TEMP` instead of directly in `.git/config`.
  https://github.com/actions/checkout
- **S245**: `actions/setup-java` releases — v5.2.0 latest; v5 moved to
  Node 24 and requires a sufficiently recent runner.
  https://github.com/actions/setup-java/releases
- **S246**: `gradle/actions` repository — current setup-gradle usage is
  `gradle/actions/setup-gradle@v6`.
  https://github.com/gradle/actions
- **S247**: GitHub Changelog — `actions/upload-artifact` v7 can upload
  non-zipped artifacts when `archive: false`.
  https://github.blog/changelog/2026-02-26-github-actions-now-supports-uploading-and-downloading-non-zipped-artifacts/
- **S248**: `actions/attest-build-provenance` repository — v4.1.0 latest;
  new implementations should use `actions/attest` directly.
  https://github.com/actions/attest-build-provenance
- **S249**: `actions/attest` repository — generic attestation action,
  v4.1.0 latest.
  https://github.com/actions/attest
- **S250**: `anchore/sbom-action` repository — v0.24.0 latest; still uses
  the v0 major tag but Syft is managed by the action.
  https://github.com/anchore/sbom-action
- **S251**: `anchore/scan-action` repository — latest major is v7, while
  OpenLumen currently uses v6.
  https://github.com/anchore/scan-action

### Competitor / saturation refresh

- **S254**: MarshMeadow/DimTV — Android TV / phone overlay dimmer with
  scheduling, notification controls, F-Droid-oriented positioning, and
  a stated 3.3.1 build 37 current release in the README.
  https://github.com/MarshMeadow/DimTV
- **S255**: F-Droid Dimmer package — old overlay-only dimmer; still useful
  as a store taxonomy / permission-language reference, not a modern
  OpenLumen peer.
  https://f-droid.org/en/packages/giraffine.dimmer/
- **S256**: MakeUseOf blue-light-filter roundup — not roadmap evidence
  by itself, but confirms user-facing comparison language still separates
  built-in Night Light, overlay apps, and root/KCAL paths.
  https://www.makeuseof.com/tag/get-good-nights-sleep-filtering-phones-blue-light/

### Verification bootstrap

- **S257**: Android Developers SDK download page — official source for
  the Windows command-line tools used to install a local Android SDK for
  this verification pass.
  https://developer.android.com/studio

### C142 implementation checks

- **S258**: `actions/checkout` tag check via `git ls-remote` — confirmed
  v6 major tag and v6.0.2 tag were available during C142 implementation.
  https://github.com/actions/checkout
- **S259**: `actions/setup-java` tag check and README — confirmed v5
  major tag / v5.2.0 tag and the v5 Node 24 runner requirement.
  https://github.com/actions/setup-java
- **S260**: `gradle/actions` tag check — confirmed v6 major tag and
  v6.1.0 tag were available for `setup-gradle`.
  https://github.com/gradle/actions
- **S261**: `actions/upload-artifact` tag check and README — confirmed
  v7 major tag / v7.0.1 tag and that zipped uploads remain the default.
  https://github.com/actions/upload-artifact
- **S262**: `actions/attest` tag check and README — confirmed v4 major
  tag / v4.1.0 tag, `subject-path` provenance mode, and required
  attestation permissions.
  https://github.com/actions/attest
- **S263**: `actions/attest-build-provenance` v4 action metadata —
  confirmed v4 is a wrapper around `actions/attest@59d894...` and that
  the direct `actions/attest@v4` migration preserves `subject-path`.
  https://github.com/actions/attest-build-provenance
- **S264**: `anchore/scan-action` tag check and action metadata —
  confirmed v7 major tag / v7.4.0 tag and `runs.using: node24`.
  https://github.com/anchore/scan-action
- **S265**: `anchore/sbom-action` tag check — confirmed the project
  remains on a v0 major line; no major rotation was available for C142.
  https://github.com/anchore/sbom-action
- **S266**: AOSP `AppExitInfoTracker` source — confirms the activity
  manager dump surface is `dumpsys activity exit-info`, used in the C143
  device-matrix MemoryLimiter smoke flow.
  https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/AppExitInfoTracker.java
- **S267**: Android `Context` API reference — confirms
  `ADVANCED_PROTECTION_SERVICE` is the system-service entry point for
  retrieving `AdvancedProtectionManager`, used by the C130 reflection
  implementation while the project remains on compile SDK 35.
  https://developer.android.com/reference/android/content/Context
- **S268**: Android Gradle Plugin 8.7 `VcsInfo` DSL reference —
  confirms `vcsInfo.include` controls whether AGP packages VCS metadata
  in release builds; used for C120.
  https://developer.android.com/reference/tools/gradle-api/8.7/com/android/build/api/dsl/VcsInfo
- **S00e**: Local implementation evidence for C138 —
  `core-prefs/src/main/java/com/openlumen/prefs/PreferencesStore.kt`
  now caps imports with `readImportBytes()` at the raw `InputStream`
  byte layer before UTF-8 decoding; `PreferencesImportReadTest.kt`
  covers exact-limit and max-plus-one behavior.
- **S00f**: Local implementation evidence for C137 —
  `OpenLumenRoot.kt` and `PresetsScreen.kt` now load local vector
  resources via `painterResource`; `gradle/libs.versions.toml` and
  `app/build.gradle.kts` no longer declare `material-icons-extended`.
- **S00g**: Local implementation evidence for C105 —
  `LumenServiceStarter.kt` centralizes foreground-service start attempts
  and classifies `ForegroundServiceStartNotAllowedException`;
  `LumenTileService.kt` and `WidgetActionReceiver.kt` roll back blocked
  toggle-on state and open the app for user-visible recovery.
- **S00h**: Local documentation evidence for C104/C126 status cleanup —
  `docs/overlay-and-per-app-design.md` now names Android 17 AAPM in the
  AccessibilityService rejection rationale, and `docs/health-evidence.md`
  already contains the C126 2025/2026 consensus-shift source refresh.
- **S00i**: Local implementation evidence for C117 —
  `app/src/main/java/com/openlumen/service/ApplyDecisionGate.kt` owns the
  service target cache and resets on engine switch via `LumenService`;
  `app/src/test/java/com/openlumen/service/ApplyDecisionGateTest.kt`
  proves same-matrix first emissions dispatch again after reset.
- **S00j**: Local implementation evidence for C95/C96/C101/C124 —
  `gradle/libs.versions.toml`, Gradle wrapper files, `app/build.gradle.kts`,
  module build files, five Compose screen imports, `.github/workflows/ci.yml`,
  `app/src/screenshotTest/kotlin/`, and
  `app/src/screenshotTestDebug/reference/` implement the AGP 9 / Hilt /
  screenshot CI train. Local full validation passed from
  `C:\Users\Xray\OpenLumen-agp9-verify` because AGP 9 D8 hit a Windows
  shared-folder path limitation under `Z:\repos\OpenLumen`.
- **S00k**: Local implementation evidence for C35 —
  `app/src/main/res/drawable/ic_launcher_background.xml`,
  `app/src/main/res/drawable/ic_launcher_foreground.xml`,
  `branding/openlumen-icon.svg`, and
  `fastlane/metadata/android/en-US/images/icon.png` now contain the final
  minimal crescent adaptive/store icon assets.
- **S00l**: Local implementation evidence for C144 —
  `gradle/libs.versions.toml` now pins Compose BOM 2026.05.00,
  Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation 2.9.8,
  DataStore 1.2.1, Material 3 1.4.0, and core-ktx 1.18.0; app and
  library modules compile with SDK 36 while target SDK remains 35.
  `AboutScreen.kt` and `DriverScreen.kt` were adjusted for the new
  Compose lint rule by reading string resources outside click handlers.
  Full validation passed from `C:\Users\Xray\OpenLumen-agp9-verify`.
- **S00m**: Local implementation evidence for C28/C102 —
  `DirectBootStateStore.kt` adds a typed DataStore in device-protected
  storage, `LockedBootReceiver.kt` handles `LOCKED_BOOT_COMPLETED`, and
  `LumenService.kt` now has a direct-boot restore path that applies the
  mirrored tint matrix without reading credential-protected preferences
  before unlock. Focused verification passed with `:core-prefs:test` and
  `:app:assembleDebug` from `C:\Users\Xray\OpenLumen-agp9-verify`.

### Post-rev-5 build-tool implementation sources

- **S269**: AndroidX Hilt releases page — `hiltViewModel()` moved to
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` / package
  `androidx.hilt.lifecycle.viewmodel.compose` in 1.3.0.
  https://developer.android.com/jetpack/androidx/releases/hilt
- **S270**: AGP built-in Kotlin migration guide — AGP 9.0+ enables built-in
  Kotlin by default and removes the need to apply
  `org.jetbrains.kotlin.android` / `kotlin-android`.
  https://developer.android.com/build/migrate-to-built-in-kotlin
- **S271**: Compose Preview Screenshot Testing guide — full IDE integration
  requires AGP 9.0+, screenshot plugin 0.0.1-alpha13+, Kotlin 2.2.10+,
  and JDK 17; underlying Gradle tasks are documented separately.
  https://developer.android.com/studio/preview/compose-screenshot-testing
- **S272**: Compose Preview Screenshot Testing release notes — alpha14 adds
  AGP 9.0 compatibility and requires `@PreviewTest`.
  https://developer.android.com/studio/preview/compose-screenshot-testing-release-notes
- **S273**: KSP Maven Central metadata — confirms
  `com.google.devtools.ksp` plugin `2.3.8` is available.
  https://repo.maven.apache.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- **S274**: Dagger releases — Dagger/Hilt 2.59.2 fixes AGP-9-era Hilt
  transform and incremental-build issues.
  https://github.com/google/dagger/releases
- **S275**: AndroidX releases overview — current stable matrix for
  Activity, Core, Lifecycle, Navigation, DataStore, Compose, and Material 3.
  https://developer.android.com/jetpack/androidx/versions
- **S276**: AndroidX Core releases — `core-ktx` 1.18.0 stable.
  https://developer.android.com/jetpack/androidx/releases/core
- **S277**: AndroidX Activity releases — Activity / Activity Compose
  1.13.0 stable.
  https://developer.android.com/jetpack/androidx/releases/activity
- **S278**: AndroidX Lifecycle releases — Lifecycle 2.10.0 stable.
  https://developer.android.com/jetpack/androidx/releases/lifecycle
- **S279**: AndroidX Navigation releases — Navigation Compose 2.9.8 stable.
  https://developer.android.com/jetpack/androidx/releases/navigation
- **S280**: AndroidX DataStore releases — DataStore preferences 1.2.1 stable.
  https://developer.android.com/jetpack/androidx/releases/datastore
- **S281**: Compose BOM mapping and Material 3 releases — Compose BOM
  2026.05.00 and Material 3 1.4.0 stable.
  https://developer.android.com/develop/ui/compose/bom/bom-mapping and
  https://developer.android.com/jetpack/androidx/releases/compose-material3

## Source-class coverage check

| Class | Sources | Counts |
|---|---|---:|
| Local evidence | S00, S00b, S00c, S00d, S00e, S00f, S00g, S00h, S00i, S00j, S00k, S00l, S00m | 13 |
| Direct OSS competitors (incl. refreshed) | S10-S19, S69-S71, S81-S82, S86, S103, S166-S169, S179-S180, S195-S197, S199-S201 | 30 |
| Commercial / platform references | S20-S25, S39, S87, S104, S198 | 11 |
| Adjacent (desktop / Wayland) | S34-S40, S72, S104-S106, S170-S178 | 18 |
| Android platform docs | S25-S29, S65-S68, S83-S85, S126-S139, S267 | 25 |
| AAPM / a11y policy | S88-S90, S121, S134-S136, S267 | 8 |
| AGP / Hilt / Compose / Glance | S75-S76, S91-S98, S118, S123-S125, S140-S153, S193-S194, S268-S281 | 41 |
| DataStore | S66, S95, S146-S147, S252, S280 | 6 |
| F-Droid | S60-S61, S111-S112, S154-S157 | 8 |
| Security (OWASP / GHSA / SBOM tooling) | S60-S64, S67-S68, S77, S108-S110, S114, S122, S188-S192 | 19 |
| Sleep / circadian | S45-S47, S99-S102, S158-S162 | 12 |
| PWM | S80, S107, S185-S187 | 5 |
| Wear OS | S117, **(negative result for new entries)** | 1 |
| TV | S16, S182 | 2 |
| CVD | S119-S120 | 2 |
| Color science (Wayland CTM parity) | S171 | 1 |

Saturation: every Now/Next/Later/Under-Consideration item in `ROADMAP.md`
rev 3 has ≥2 sources. New evidence introduces 3 new candidates
(C128 FabricatedOverlay engine, C129 OLED gamma LUT, C130 AAPM driver-
report surface, C131 Eye Dropper picker integration); each cites ≥2
sources.

Rev 5 coverage update: pass 3+implementation added 39 more sources
(S230-S268). The
newest coverage gap closed is **off-Play Android developer verification**,
which was absent from rev 4.1 and now maps to C141. CI/action supply-chain
coverage now includes the Node 24 deadline plus current action majors
(C142). Android 17 readiness now includes memory-limiter and large-screen
resizability behavior (C143). Dependency coverage now includes the AGP
9.2 / Gradle 9.4.1 / Dagger 2.59.2 / AndroidX stable-version matrix
that sharpens C95, C96, C124, and C144. S257 records the official SDK
bootstrap source used to complete local verification after `JAVA_HOME` and
`ANDROID_HOME` were initially missing. S258-S265 record the direct
upstream tag / metadata checks used when implementing C142. S266 records
the AOSP dump surface used for the C143 MemoryLimiter smoke flow. S267
records the Android `Context` service constant used by C130.
S268 records the AGP VCS-info DSL used by C120.
S00e records the local C138 import-byte-limit implementation and focused
unit test.
S00f records the local C137 dependency-removal and vector-resource
implementation evidence.
S00g records the local C105 foreground-service-start fallback evidence.
S00h records the local documentation/status cleanup evidence for C104
and C126.
S00i records the local C117 first-emission dispatch implementation and
JVM regression coverage. S00j records the local C95/C96/C101/C124 AGP 9
/ Hilt / screenshot implementation evidence. S269-S274 refresh the
current external toolchain sources that informed that implementation.
S00k records the local C35 final icon assets. S00l records the C144
stable AndroidX baseline refresh and full local-mirror validation. S00m
records the C28/C102 Direct Boot implementation evidence and focused
verification. S275-S281 capture the AndroidX / Compose release evidence
used for that batch.
