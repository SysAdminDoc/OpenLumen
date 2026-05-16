# Device Validation Matrix

> Per-device confirmation for each `ColorEngine`. Tied to roadmap candidate
> **C01** (Real-device driver matrix) and **C44** (Public compatibility
> table).

This file is a living record. Add a row when a device is exercised against a
release build, and link the test artifact (in-app driver report from the
Driver tab → "Share report"). Do not edit rows for older releases — append
new ones.

## Legend

| Mark | Meaning |
|---|---|
| ✅ | Probe passes, apply works, clear works, no visual artifacts |
| 🟡 | Probe passes but with caveats — see notes |
| ❌ | Probe fails or apply produces a black screen / stuck tint |
| — | Not applicable to this device (e.g. KCAL on a Tensor SoC) |
| ? | Untested |

## Engines × devices

| Device | Android | OEM | Root | CDM | SF | KCAL | Overlay | Release | Notes |
|---|---|---|---|:-:|:-:|:-:|:-:|---|---|
| Pixel 6 | 15 (AP4A.250105.002) | Google | yes (Magisk 28) | ? | ? | — | ? | v0.4.0 | smoke pending |
| Pixel 8 | 15 (AP4A.250105.002) | Google | no | ? | — | — | ? | v0.4.0 | smoke pending |
| Samsung Galaxy S23 | 14 (One UI 6.1) | Samsung | no | ? | — | — | ? | v0.4.0 | smoke pending |
| OnePlus 9 | 14 | OnePlus | yes, KCAL kernel | ? | ? | ? | ? | v0.4.0 | smoke pending |
| Xiaomi Mi 11 | 14 (HyperOS) | Xiaomi | no | ? | — | — | ? | v0.4.0 | smoke pending |
| Generic AVD x86_64 | 35 (preview) | AOSP emu | rooted | ? | — | — | ? | v0.4.0 | emulator only |

This grid starts empty by design. We've intentionally avoided fabricating
results — see `CONTRIBUTING.md` for how to add rows.

## Per-engine smoke test

Run all four engines through this minimum flow against a release build:

1. Cold install. Open the app. Confirm `WRITE_SECURE_SETTINGS` is not
   granted yet (the `adb` command in the Driver screen should still show).
2. Driver tab → tap each engine in turn → re-probe. Note availability.
3. Pick the highest-ranked available engine. Home → toggle on. Confirm:
   - Notification appears within 1 second.
   - Screen tint matches the selected preset.
   - There is no flicker or noticeable input lag.
4. Drag the Intensity slider from 0 to 100. Confirm smooth tracking, no
   `su` subprocess explosion (`adb shell ps | grep su` should not show
   many).
5. Toggle off. Confirm:
   - Tint clears within 1 second.
   - Notification disappears.
   - Tile reflects OFF state.
6. Set a fixed-time schedule with end time = now + 2 minutes. Toggle on.
   Wait 2 minutes. Confirm:
   - Tint clears at the scheduled boundary.
   - Service does not stop (the foreground notification stays).
7. Boot the device. Confirm tint comes back if the schedule was active.
8. Driver tab → "Share report" → paste the report into the device row above
   (or attach the file to the device's tracking issue).

## CDM (`ColorDisplayManager`) specifics

- API 28+ only. On a device with API 27 or below, this row is always `—`.
- Requires `WRITE_SECURE_SETTINGS` granted via:
  ```bash
  adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS
  ```
- On some Samsung firmwares, the grant succeeds but the reflected method
  silently no-ops. Mark such cases 🟡 and capture the device + firmware
  build number.

## SurfaceFlinger specifics

- Requires `su`. Confirm `adb shell which su` returns a path before
  marking ✅.
- The probe iterates candidate transaction codes (1015, 1023, 1030). If
  none work, mark ❌ and capture the device + Android version so we can
  add new candidate codes.
- Some Magisk modules block specific `service call` codes — note this in
  the row.

## KCAL specifics

- Only relevant on Qualcomm devices with kernels that expose
  `/sys/devices/platform/kcal_ctrl.0/kcal*`.
- Tensor (Pixel 6/7/8/9) and Exynos devices are always `—` here.
- Check the kernel before testing:
  ```bash
  adb shell su -c "ls /sys/devices/platform/kcal_ctrl.0/"
  ```

## Overlay specifics

- Universal fallback; should work on every device.
- Alpha is capped at ~0.8 by Android 12+ untrusted-touch rules. This is
  documented behavior, not a bug — mark ✅ unless the overlay completely
  fails to attach.
- Test that touch passes through to apps underneath (try tapping a Settings
  toggle while the overlay is active).
- Test the install-flow occlusion case: try to install another APK while
  the overlay is on. The system installer should refuse the tap (Android
  12+ untrusted-touch rule). This is **correct** behavior — the roadmap
  has C12 to surface a pause path; for now, mark 🟡 in the Overlay column
  with note "untrusted-touch blocks installer (expected)".

## Filling rows

When you finish a smoke run, add a row. Include:

- Device name + model identifier.
- Android version + build fingerprint (`adb shell getprop ro.build.fingerprint`
  is fine).
- OEM software (One UI / OxygenOS / HyperOS / stock / etc).
- Root: `no`, `Magisk X.Y`, `KernelSU`, etc.
- Mark each engine column.
- Release column = the OpenLumen version under test.
- Notes column = anything weird. Keep it short; long notes go in a linked
  issue.

## When a row should be removed

We don't remove device rows. We add new ones. Old results stay because:

- Future contributors can see whether a bug has been around for a while or
  is new in this release.
- F-Droid reviewers and downstream packagers can see the testing surface.
- "We tested this on Pixel 6 once in 2026" is more useful than "we have no
  data on Pixel 6".

If a row contains a mistake (wrong column marked, wrong device name), fix
it in place — but write the fix in the commit body, not by overwriting
history silently.
