# Device Validation Matrix

> Per-device confirmation for each `ColorEngine`. Tied to roadmap candidate **C01** (Real-device driver matrix) and **C44** (Public compatibility table).

This file is a living record. Add a row when a device is exercised against a release build, and link the test artifact (in-app driver report from the Driver tab → "Share report"). Don't edit rows for older releases. Append new ones instead.

## Legend

| Mark | Meaning |
|---|---|
| ✅ | Probe passes, apply works, clear works, no visual artifacts |
| 🟡 | Probe passes but with caveats (see notes) |
| ❌ | Probe fails or apply produces a black screen / stuck tint |
| n/a | Not applicable to this device (e.g. KCAL on a Tensor SoC) |
| ? | Untested |

## Engines × devices

| Device | Android | OEM | Root | CDM | SF | KCAL | Overlay | Release | Notes |
|---|---|---|---|:-:|:-:|:-:|:-:|---|---|
| Samsung Galaxy S22 (SM-S908U1) | 16 (BP2A.250605.031.A3, S908U1UESAGZE3) | Samsung, One UI 8 | no | n/a | n/a | n/a | ✅ | main after 0.8.0 (debug) | No Night Light on this firmware, so the System Night Light driver has nothing to tint with and the probe now turns it down. Overlay keeps the panel at 120 Hz while scrolling (8.33 ms frames with the filter on and off). With gesture navigation the overlay covers the whole 1080x2316 panel, and the strip under the navigation handle takes the same amber as the rest of the screen. Gray out apps works through Shizuku here too: after Shizuku's prompt, Clock went fully gray, and the X gave it its color back. |
| Samsung Galaxy S25 Ultra (SM-S938B) | 17 (CP2A.260605.016, S938BXXSDDZJ1) | Samsung, One UI | no | n/a | n/a | n/a | ✅ | main after 0.8.0 (debug) | Same as the S22. No Night Light, Auto picks Overlay with WRITE_SECURE_SETTINGS granted, and no secure row is written. |
| Pixel 6 | 15 (AP4A.250105.002) | Google | yes (Magisk 28) | ? | ? | n/a | ? | v0.4.0 | smoke pending |
| Pixel 8 | 15 (AP4A.250105.002) | Google | no | ? | n/a | n/a | ? | v0.4.0 | smoke pending |
| Samsung Galaxy S23 | 14 (One UI 6.1) | Samsung | no | ? | n/a | n/a | ? | v0.4.0 | smoke pending |
| OnePlus 9 | 14 | OnePlus | yes, KCAL kernel | ? | ? | ? | ? | v0.4.0 | smoke pending |
| Xiaomi Mi 11 | 14 (HyperOS) | Xiaomi | no | ? | n/a | n/a | ? | v0.4.0 | smoke pending |
| Generic AVD x86_64 | 35 (preview) | AOSP emu | rooted | ? | n/a | n/a | ? | v0.4.0 | emulator only |
| Generic AVD x86_64 | 17 (CE2A.260420.051.A3, userdebug) | AOSP emu | adb root only | ? | ? | n/a | ✅ | main after 0.8.0 (debug) | Only Overlay was run. Gray out apps works through Shizuku 13.6: a picked app goes gray while the rest keep color, the overlay tint still shows on it as shades of amber, it stays gray with the system's color correction on, the level outlives an app restart, and the X gives the app its color back. A reboot clears the levels. With Shizuku started again they came back once OpenLumen was opened, not before. |

Every row here was observed on the device it names. Nothing is filled in from what a driver should do. See `CONTRIBUTING.md` for how to add rows.

## Per-engine smoke test

Run all four engines through this minimum flow against a release build:

1. Cold install. Open the app. Confirm `WRITE_SECURE_SETTINGS` is not granted yet (the `adb` command in the Driver screen should still show).
2. Driver tab → tap each engine in turn → re-probe. Note availability.
3. Pick the highest-ranked available engine. Home → toggle on. Confirm:
   - Notification appears within 1 second.
   - Screen tint matches the selected preset.
   - There is no flicker or noticeable input lag.
4. Drag the Intensity slider from 0 to 100. Confirm smooth tracking and no `su` subprocess explosion (`adb shell ps | grep su` shouldn't show many).
5. Toggle off. Confirm:
   - Tint clears within 1 second.
   - Notification disappears.
   - Tile reflects OFF state.
6. Set a fixed-time schedule with end time = now + 2 minutes. Toggle on. Wait 2 minutes. Confirm:
   - Tint clears at the scheduled boundary.
   - Service does not stop (the foreground notification stays).
7. Boot the device. Confirm tint comes back if the schedule was active.
8. Driver tab → "Share report" → paste the report into the device row above (or attach the file to the device's tracking issue).

## Boot-restore add-on (Android 14+ / C106)

For every Android 14, 15, 16, and 17 row, include a boot-restore note in the row's Notes field:

- `boot restore ?` means not yet tested.
- `boot restore OK` means the enabled filter was restored after reboot, and the foreground notification and tint came back after first user unlock.
- `locked boot restore OK` means Direct Boot restore applied the mirrored tint before first user unlock.
- `locked boot restore failed` means Direct Boot restore didn't apply before first unlock. Link the Driver report plus `dumpsys activity services com.openlumen` after unlock.
- `boot restore failed` means the filter didn't restore. Link the Driver report and a `dumpsys activity services com.openlumen` excerpt.

This add-on is the device-matrix side of C106 and C28/C102. It creates the evidence slot but doesn't replace the actual real-device result required by C01.

## Android 17 memory / large-screen add-on

Run this add-on for Android 17+ rows, and for any tablet, foldable, desktop windowing, Android TV, or emulator profile with `sw600dp` or larger width. This is the device-matrix side of roadmap candidate **C143**.

### MemoryLimiter / `ApplicationExitInfo`

1. Start from a clean app install or clear recent exit history in the test notes.
2. Enable the filter with the overlay engine, then run a smooth transition for at least 10 minutes while switching tabs and toggling the notification preset action.
3. Disable the filter, re-enable it, then background and foreground the app three times.
4. Capture recent process-exit history:
   ```bash
   adb shell dumpsys activity exit-info com.openlumen
   adb shell dumpsys activity exit-info com.openlumen.debug
   ```
   Use the `.debug` package for debug builds and the release package for release builds.
5. Mark the row 🟡 and link the dump if any recent entry contains `MemoryLimiter:AnonSwap`, `REASON_LOW_MEMORY`, an ANR trace, or repeated service-process exits during the smoke. Mark ✅ only when no suspicious recent OpenLumen exit is present after the long-running overlay test.

### sw600dp / foldable / desktop-windowing

1. Test at least one wide configuration before any Android 17 target-SDK bump:
   - Android Studio resizable emulator at `sw600dp` or wider.
   - Pixel Tablet / tablet hardware.
   - Foldable unfolded posture.
   - Desktop-windowing mode.
   - Android TV flavor when C22 resumes.
2. Open every tab, rotate or resize the window, then confirm:
   - The bottom navigation and every primary control remain reachable.
   - Slider values and schedule/profile edits survive the configuration change.
   - Overlay tint still covers the full window/display after resize.
   - Dialogs are centered and not clipped.
   - The emergency-off command remains reachable from About.
3. Add the wide-form-factor result as a normal row in the matrix. Put the form factor in Notes, for example `sw600dp emulator; resize/rotate OK`. Don't replace phone rows with emulator-only large-screen coverage.

## SecureSettings specifics

Recorded against the `SecureSettings` driver. Rows captured before 0.8.0 name a `ColorDisplayManager` driver that was removed. It couldn't work on a user install, and the secure-settings writer replaced it.

- API 28+ only. On a device with API 27 or below, this row is always `n/a`.
- Requires `WRITE_SECURE_SETTINGS` granted via:
  ```bash
  adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS
  ```
- Samsung phones ship without Night Light (`config_nightDisplayAvailable` is false on the Galaxy S22 with Android 16 and the S25 Ultra with Android 17, and Eye Comfort Shield replaces it). The rows still accept writes and nothing changes on screen, so the probe checks that config and turns the driver down. Mark the column `n/a` on such a device.
- From Android 12 a user app can't read the Extra Dim rows (`reduce_bright_colors_*`), even with the grant. The read throws a `SecurityException`. The driver checks once and leaves Extra Dim alone where it can't read it, so "secure keys accepted" in the diagnostics report won't list it on those devices.

## SurfaceFlinger specifics

- Requires `su`. Confirm `adb shell which su` returns a path before marking ✅.
- The probe uses the single sourced transaction code 1015. If none work, mark ❌ and capture the device + Android version so we can add new candidate codes.
- First-emission smoke: with the filter already enabled on another engine, switch to SurfaceFlinger without changing preset/intensity/dim. The tint must apply immediately. Record `SF first emission: pass/fail` in the row notes.
- Some Magisk modules block specific `service call` codes, so note this in the row.

## KCAL specifics

- Only relevant on Qualcomm devices with kernels that expose `/sys/devices/platform/kcal_ctrl.0/kcal*`.
- Tensor (Pixel 6/7/8/9) and Exynos devices are always `n/a` here.
- Check the kernel before testing:
  ```bash
  adb shell su -c "ls /sys/devices/platform/kcal_ctrl.0/"
  ```
- First-emission smoke: with the filter already enabled on another engine, switch to KCAL without changing preset/intensity/dim. The tint must apply immediately. Record `KCAL first emission: pass/fail` in the row notes.

## Overlay specifics

- Universal fallback, so it should work on every device.
- Alpha is capped at ~0.8 by Android 12+ untrusted-touch rules. This is documented behavior, not a bug, so mark ✅ unless the overlay completely fails to attach.
- Test that touch passes through to apps underneath (try tapping a Settings toggle while the overlay is active).
- Test the install-flow occlusion case: try to install another APK while the overlay is on. The system installer should refuse the tap (Android 12+ untrusted-touch rule). This is **correct** behavior. The roadmap has C12 to surface a pause path. For now, mark 🟡 in the Overlay column with the note "untrusted-touch blocks installer (expected)".

## Filling rows

When you finish a smoke run, add a row. Include:

- Device name + model identifier.
- Android version + build fingerprint (`adb shell getprop ro.build.fingerprint` is fine).
- OEM software (One UI / OxygenOS / HyperOS / stock / etc).
- Root: `no`, `Magisk X.Y`, `KernelSU`, etc.
- Mark each engine column.
- Release column = the OpenLumen version under test.
- Notes column = anything weird. Keep it short, and put long notes in a linked issue.

## When a row should be removed

We don't remove device rows. We add new ones. Old results stay because:

- Future contributors can see whether a bug has been around for a while or is new in this release.
- F-Droid reviewers and downstream packagers can see the testing surface.
- "We tested this on Pixel 6 once in 2026" is more useful than "we have no data on Pixel 6".

If a row contains a mistake (wrong column marked, wrong device name), fix it in place, but write the fix in the commit body rather than overwriting history silently.
