# OpenLumen Compatibility Table

> Public-facing, user-friendly summary of which engines work on which
> hardware. Tied to roadmap candidate **C44** (Public compatibility
> table). Aggregates and simplifies the per-test rows in
> [docs/device-matrix.md](device-matrix.md).

This is a living document. We add a row when a device's behavior is
confirmed (positive or negative); we don't speculate. An empty row is
better than a wrong row.

## Quick filter

If you're not sure which engine will work on your device, run through
the table below and pick the first row that matches. The Driver tab
inside the app does the same probe automatically — this table just
helps you set expectations before installing.

## By SoC family

| SoC | Secure settings | SF (root) | KCAL | Overlay | Recommended |
|---|:-:|:-:|:-:|:-:|---|
| Google Tensor (Pixel 6/7/8/9) | usually ✅ | ✅ if rooted | — (no KCAL kernel) | ✅ | Secure settings with `WRITE_SECURE_SETTINGS` granted |
| Qualcomm Snapdragon, stock kernel | depends on OEM | ✅ if rooted | — (stock kernels rarely ship KCAL) | ✅ | Secure settings if the ROM honors Night Light; otherwise SF or Overlay |
| Qualcomm Snapdragon, custom kernel with KCAL | depends on OEM | ✅ if rooted | ✅ if rooted | ✅ | KCAL if you want panel-driver quality; SF for the framebuffer path |
| Samsung Exynos | rarely | ✅ if rooted | — | ✅ | SF or Overlay; One UI Night Light behavior is inconsistent |
| MediaTek Dimensity | rarely | ✅ if rooted | — | ✅ | SF or Overlay |
| Generic emulator (AVD) | depends on system image | ✅ if rooted | — | ✅ | Overlay for quick test |

## By OEM / ROM

| OEM | Notes |
|---|---|
| Google (Pixel, stock Android) | Secure settings works reliably with `WRITE_SECURE_SETTINGS` granted. It writes the same `night_display_*` rows the system Settings app writes, so there is nothing to coexist with — it is the same transform. |
| Samsung (One UI) | One UI keeps its own eye-comfort control, which can overwrite the same rows. SF (root) is the most reliable path on Samsung; Overlay is the rootless fallback. |
| OnePlus (OxygenOS) | If you have a KCAL kernel, KCAL is the best quality. SF (root) is the universal alternative. Overlay always works. |
| Xiaomi (HyperOS / MIUI) | Secure settings works on most builds. MIUI's own color-temperature setting can interact with OpenLumen — disable MIUI's "Reading mode" if both fight each other. |
| GrapheneOS / CalyxOS / LineageOS | Secure settings works the same as stock Pixel since these ROMs preserve AOSP color-display behavior. |

## Behavior caveats by Android version

| Android version | What changes for OpenLumen |
|---|---|
| 8.0 (API 26) | Minimum supported version. Overlay-only on most devices because the night-display secure rows (API 29+) and modern SurfaceFlinger codes don't exist yet. |
| 9 (API 28) | Still overlay-only. The `night_display_*` rows arrive in API 29, not 28. |
| 10 (API 29) | Quick Settings tile `subtitle` lands. OpenLumen uses it to show the active preset. |
| 12 (API 31) | Overlay alpha capped at ~80% by untrusted-touch rules; system installer / permission dialogs may block taps while OpenLumen's overlay is on. `SCHEDULE_EXACT_ALARM` becomes a runtime permission. The `reduce_bright_colors_*` rows (Extra Dim) arrive here, so the `SecureSettings` driver can dim below the panel minimum on devices whose OEM ships the feature. |
| 13 (API 33) | `POST_NOTIFICATIONS` becomes a runtime permission. OpenLumen requests it on first launch. |
| 14 (API 34) | Foreground service must declare a `type`; OpenLumen uses `specialUse` with the documented manifest property. |
| 15 (API 35) | Current `targetSdk`. No OpenLumen-specific behavior change observed yet. |

## What "works" means in this table

- **✅** — Probe passes, apply works, clear works, no visual artifacts.
- **depends on OEM** — Behavior varies by ROM; check the in-app driver
  report on your specific device before relying on the engine.
- **—** — Not applicable to this hardware (KCAL on non-Qualcomm, for
  example).

## Adding rows

If your device isn't represented or your experience differs from a
listed row:

1. Run the smoke test in
   [docs/device-matrix.md](device-matrix.md#per-engine-smoke-test).
2. Open a "Driver compatibility report" issue with the in-app driver
   report (Driver tab → Share report).
3. We'll aggregate it into this table and the per-test record in
   `device-matrix.md`.

We deliberately don't fabricate entries to make the table look fuller.
"We don't know yet" is more useful than a guess.
