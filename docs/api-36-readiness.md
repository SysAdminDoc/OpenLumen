# Android 16 / API 36 Readiness

> Tied to roadmap candidate **C82** (Android 16/API 36 readiness).
>
> This is a forward-looking inventory, not a migration. We will not bump
> `targetSdk` until Android 16 is in stable release and we have real-device
> validation rows in `docs/device-matrix.md`. Until then, this document is
> a checklist of behaviors we expect to need to handle.

## What we're already prepared for

These are Android 14 / 15 behaviors that Android 16 is expected to keep:

- **`foregroundServiceType="specialUse"`** with the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property. Already
  declared; documented in `docs/play-fgs-evidence.md`.
- **`SCHEDULE_EXACT_ALARM` runtime permission** plus graceful fallback
  to `setAndAllowWhileIdle` when it's not granted. Already implemented in
  `LumenService.rescheduleNextTransition()`.
- **`POST_NOTIFICATIONS` runtime permission** (API 33+). Already requested
  in `MainActivity.onCreate()`.
- **`SYSTEM_ALERT_WINDOW`** opt-in flow. Already covered by
  `OverlayPermissionCard` on the Home tab.

## Expected Android 16 behavior changes we need to track

These are behaviors announced by Google during the API 36 preview cycle.
Severity is OpenLumen's exposure, not Google's general severity.

| Behavior | OpenLumen exposure | Mitigation we'd ship |
|---|---|---|
| Tightened foreground-service runtime budgets | Medium | Audit any newly long-running scope in `applyMatrix` ramp coroutine; document continued reliance on `specialUse` |
| `BroadcastReceiver` execution-time tightening | Low | Our receivers already finish in <1s; `BootReceiver` uses `goAsync()` with an 8s cap |
| Stricter `setExactAndAllowWhileIdle` quotas | Medium | Schedule fires at most twice in 24h per mode; quota is unlikely to bite, but verify on first preview |
| Overlay alpha cap changes | Medium | Re-test the touch-pass-through and tapjacking-protection behavior on the preview; update `docs/threat-model.md` if anything moves |
| Sensor framework batching changes | Low | Our light-sensor listener already tolerates batched dispatches |
| Manifest-permission visibility lockdown | Low | We deliberately ask for very few permissions; nothing we use has been flagged for restriction |
| `WindowManager` flag deprecations | Low | We use `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_TOUCHABLE` + `FLAG_NOT_FOCUSABLE` — all stable since API 26 |

## Test plan for the first preview build

When the first stable Android 16 image lands:

1. Run the per-engine smoke test from `docs/device-matrix.md` on a Pixel
   device with the preview image.
2. Check that the QS tile subtitle still renders (we use the API 29+
   `Tile.subtitle` API).
3. Verify the `permissions-audit` CI job still works against the new
   build tools (no merged-manifest changes from AGP).
4. Confirm `actions/attest-build-provenance@v2` still attests against
   the new artifact format.
5. Re-run the in-app driver report and compare with the most recent
   stored sample (the driver report's fingerprint section captures the
   build identifier so version comparisons are straightforward).

## Migration path

We will:

1. **Not** bump `targetSdk` in the same release as a feature change.
   Target-SDK bumps get their own release so a regression has a clean
   bisection point.
2. **Not** ship a preview-only build to F-Droid. Preview-only behavior
   changes go on a branch.
3. Update `docs/device-matrix.md` with at least one Android 16 row
   before the release that bumps `targetSdk`.

## Related roadmap candidates

- **C95** — AGP 9 migration spike. Android 16 + AGP 9 is the realistic
  combined target.
- **C96** — Hilt Compose artifact migration. AndroidX Hilt's Compose
  APIs moved namespaces; that migration should land in the same cycle
  as the AGP 9 spike.

## Source

- [Android Developers — Android 16 behavior
  changes](https://developer.android.com/about/versions/16/behavior-changes-all)
  (link will populate as the preview matures)
- [AGP release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- Local research watchlist: see `docs/research-watchlist.md`
