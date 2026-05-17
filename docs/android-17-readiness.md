# Android 17 Readiness

> Tied to roadmap candidates **C82 / C103** (Android 17 readiness; renamed
> from "Android 16 / API 36 readiness" in `ROADMAP.md` rev 3 because
> Android 17 stable is the realistic next `targetSdk`, not 16). The file
> was renamed in rev 4 to match.
>
> This is a forward-looking inventory, not a migration. We will not bump
> `targetSdk` until Android 17 is in stable release and we have real-device
> validation rows in `docs/device-matrix.md`. Until then, this document is
> a checklist of behaviors we expect to need to handle.

Android 17 Beta 4 shipped 2026-04-16 (S96, S126). Stable lands June 2026
([Android Developers Blog announcement](https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html)).

## What we're already prepared for

These are Android 14 / 15 behaviors that Android 17 keeps:

- **`foregroundServiceType="specialUse"`** with the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property. Already declared
  in [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml);
  documented in [docs/play-fgs-evidence.md](play-fgs-evidence.md).
  Sources: S29, S132.
- **`SCHEDULE_EXACT_ALARM` runtime permission** plus graceful fallback to
  `setAndAllowWhileIdle` when it's not granted. Already implemented in
  `LumenService.rescheduleNextTransition()`. Source: S27.
- **`POST_NOTIFICATIONS` runtime permission** (API 33+). Already requested
  in `MainActivity.onCreate()`. Source: S28.
- **`SYSTEM_ALERT_WINDOW`** opt-in flow. Already covered by
  `OverlayPermissionCard` on the Home tab. Source: S26.

## Android 17 behavior changes we need to track

Severity is OpenLumen's exposure, not Google's general severity. Sources:
S127 (release notes), S128 (behavior changes for apps targeting 17),
S129 (features and APIs), S130 (FGS changes), S131 (FGS background-start
restrictions), S132 (FGS service types), S133 (background audio),
S134 (`AdvancedProtectionManager`), S135 (AAPM landing page),
S137 (BAL restrictions), S138 (AOSP Night Light), S235 (all-app
behavior changes), S236 (large-screen resizability / orientation).

| Behavior | OpenLumen exposure | Mitigation we'd ship | Source |
|---|---|---|---|
| **SAW apps must have a visible overlay window to start an FGS from background** | High — tile/widget toggle-on path | C105 shipped 2026-05-17: `LumenServiceStarter` detects `ForegroundServiceStartNotAllowedException`; QS/widget starts roll back stale state and open the app so the overlay-permission card is visible | S85, S131, S00g |
| **`BOOT_COMPLETED` cannot launch certain FGS types** | Medium — `specialUse` not on the affected list per S130 but needs verification | C106: explicit Android 14/15/16/17 rows in `docs/wake-and-vitals.md` and `docs/device-matrix.md` confirming boot restore still works | S85, S130 |
| **`MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated for IntentSender (use `_ALLOW_IF_VISIBLE`)** | Low today — no `IntentSender` / `ActivityOptions` BAL call sites exist | C111 shipped: source audit found only direct `PendingIntent.getActivity/getService/getBroadcast` usage; no `_ALLOW_IF_VISIBLE` migration needed until an `IntentSender` path is introduced | S84, S128, S137, S00d |
| **Advanced Protection Mode auto-revokes accessibility API for non-`isAccessibilityTool` apps** | Closes C79 / C80 permanently | C104 / C130: document in `docs/threat-model.md` and `docs/overlay-and-per-app-design.md`; surface AAPM state in driver report | S88, S89, S90, S121, S134, S135, S136 |
| **MessageQueue rewrite (apps targeting 17)** | Low — we don't post Messages between threads at scale | None required; C101 now runs host-side Compose screenshot validation in CI, but target-37 behavior still needs device/emulator smoke | S128 |
| **App memory limits / `MemoryLimiter` exit descriptions** | Low-to-medium — OpenLumen is small, but persistent service + overlay leaks would be user-visible | C143: add `ApplicationExitInfo` review to the Android 17 smoke flow; inspect crashes/ANRs for `MemoryLimiter:AnonSwap` after long-running service and overlay tests | S234, S235 |
| **Orientation/resizability/aspect-ratio restrictions ignored on sw600dp+** | Medium — no explicit orientation lock today, but Compose state and bottom-nav layout need tablet/foldable/windowing verification | C143 adds tablet/foldable/desktop-windowing rows to `docs/device-matrix.md`; C101 gives a seed screenshot fixture, while full tab coverage remains C83 | S236 |
| **Background audio hardening** | None — OpenLumen doesn't touch audio | N/A | S133 |
| **Certificate Transparency by default + ECH** | None — we have no INTERNET permission | N/A | S128, S129 |
| **`ACCESS_LOCAL_NETWORK` runtime permission** | None — we don't open local network sockets | N/A | S128 |
| **OTP filtering / CP2 PII restrictions** | None — we don't read SMS or contacts | N/A | S128 |
| **Tightened FGS runtime budgets (Android 16+)** | Medium | Audit the smooth-transition ramp coroutine; document continued reliance on `specialUse` | S85 |
| **`BroadcastReceiver` execution-time tightening (Android 16+)** | Low | Our receivers already finish in <1s; `BootReceiver` uses `goAsync()` with an 8s cap | S85 |
| **`setExactAndAllowWhileIdle` quotas (Android 16+)** | Medium | Schedule fires at most twice in 24h per mode; quota unlikely to bite but verify on first preview | S85 |
| **Overlay alpha cap and untrusted-touch behavior** | Medium | Re-test on Android 17 preview; update threat model if anything moves | S26, S188 |
| **Sensor framework batching changes** | Low | Our light-sensor listener already tolerates batched dispatches | S65 |
| **Manifest-permission visibility lockdown** | Low | We deliberately ask for very few permissions; none flagged for restriction | S85 |
| **`WindowManager` flag deprecations** | Low | We use `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE` + `FLAG_NOT_FOCUSABLE` — all stable since API 26 | S32 |
| **AOSP Night Light surface** | None — `ColorDisplayManager` / `ColorDisplayService` unchanged in Android 17 (S138 negative confirmation). Our reflection ladder doesn't need an Android-17-specific case. | N/A | S138 |
| **`OPEN_EYE_DROPPER` system intent (new in 17)** | Opportunity — C131 Later candidate adds a "sample color" button to the RGB picker | C131 | S129, S139 |

## Test plan for the first stable Android 17 build

When the first stable Android 17 image lands (June 2026):

1. Run the per-engine smoke test from
   [docs/device-matrix.md](device-matrix.md) on a Pixel device running
   stable Android 17. Add the row.
2. Check that the QS tile subtitle still renders (API 29+ `Tile.subtitle`).
3. Verify the SAW-app FGS-from-background fallback (C105) opens the app /
   overlay-permission card from QS tile and widget toggle-on paths when
   no overlay is visible.
4. Verify `BOOT_COMPLETED` still restores the filter and fill the C106
   row in [wake-and-vitals.md](wake-and-vitals.md).
5. Verify notification-tap and widget/tile pending intents still route
   correctly. C111's source audit found no `IntentSender` BAL call sites,
   so this is a smoke check rather than an API migration.
6. Verify the `permissions-audit` CI job still works against the new
   build tools (no merged-manifest changes from AGP 9.x).
7. Confirm `actions/attest@v4` still attests against the
   new artifact format.
8. Re-run the in-app driver report. The AAPM block (C130) should report
   `enabled`, `disabled`, `n/a (API <36)`, or a bounded `unknown` reason.
9. Compare driver-report fingerprint against the most recent stored
   sample. The fingerprint captures the build identifier so version
   comparisons are straightforward.
10. After a long-running overlay + smooth-transition smoke, inspect
    recent `ApplicationExitInfo` entries for `MemoryLimiter:AnonSwap`.
11. Run the app on a sw600dp emulator/tablet/foldable or desktop-windowing
    mode; rotate/resize while each tab is open and confirm state is
    retained and controls remain reachable.

## Migration path

We will:

1. **Not** bump `targetSdk` in the same release as a feature change.
   Target-SDK bumps get their own release so a regression has a clean
   bisection point.
2. **Not** ship a preview-only build to F-Droid. Preview-only behavior
   changes go on a branch.
3. Update [docs/device-matrix.md](device-matrix.md) with at least one
   Android 17 row before the release that bumps `targetSdk` to 36.
4. Keep the `targetSdk = 36` bump separate from the already-landed
   AGP/Hilt train. C95/C96/C124 shipped on 2026-05-17; the remaining
   Android 17 gate is real-device or emulator validation before changing
   `targetSdk`.

## Related roadmap candidates

- **C82 / C103** — Android 17 readiness (this document).
- **C95** — AGP 9 migration spike. Shipped 2026-05-17 on AGP 9.2.1 /
  Gradle 9.4.1 / Kotlin 2.3.21.
- **C96 / C124** — Hilt Compose artifact rename and Hilt 2.56+ bump.
  Shipped 2026-05-17 on Hilt 2.59.2.
  `hiltViewModel()` moved to
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` (S144-S145).
- **C101** — Compose Preview Screenshot Testing CI. Shipped 2026-05-17
  with a textless theme-token fixture; full tab coverage remains C83.
- **C104** — Document AAPM accessibility revocation. Shipped as part of
  the rev 3 / rev 4 docs pass.
- **C105** — SAW-app FGS-from-background fallback. Shipped
  2026-05-17 for QS tile and widget user actions.
- **C106** — `BOOT_COMPLETED` FGS verification. Shipped 2026-05-17 as
  explicit wake/vitals + device-matrix evidence slots; real pass/fail
  results still live under C01.
- **C111** — BAL hardening readiness. Shipped 2026-05-17 as a source
  audit; no `IntentSender` / `ActivityOptions` call sites exist today.
- **C130** — AAPM driver-report surface (rev 4 new). Shipped
  2026-05-17.
- **C131** — Eye Dropper integration on Android 17+ (rev 4 new).
- **C143** — Android 17 memory/resizability smoke expansion (rev 5 new).

## Sources

- Android 17 release notes — https://developer.android.com/about/versions/17/release-notes (S127)
- Behavior changes for apps targeting Android 17 — https://developer.android.com/about/versions/17/behavior-changes-17 (S128)
- Android 17 features and APIs — https://developer.android.com/about/versions/17/features (S129)
- Changes to foreground services — https://developer.android.com/develop/background-work/services/fgs/changes (S130)
- FGS background-start restrictions — https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start (S131)
- Foreground service types — https://developer.android.com/develop/background-work/services/fgs/service-types (S132)
- `AdvancedProtectionManager` reference — https://developer.android.com/reference/android/security/advancedprotection/AdvancedProtectionManager (S134)
- Advanced Protection Mode landing page — https://developer.android.com/privacy-and-security/advanced-protection-mode (S135)
- Android 17 Eye Dropper API — https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16 (S139)
- Android 17 behavior changes for all apps — https://developer.android.com/about/versions/17/behavior-changes-all (S235)
- Android 17 orientation/resizability restrictions ignored — https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored (S236)
- AGP 9.x release notes — https://developer.android.com/build/releases/agp-9-0-0-release-notes (S140)
- AGP roadmap (AGP 10 timeline) — https://developer.android.com/build/releases/gradle-plugin-roadmap (S143)
- AndroidX Hilt releases — https://developer.android.com/jetpack/androidx/releases/hilt (S144)
- Local research watchlist: [docs/research-watchlist.md](research-watchlist.md).
