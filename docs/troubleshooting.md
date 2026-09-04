# Troubleshooting

> Common failure modes and what to try. Tied to roadmap candidate **C40**.

If your issue isn't here, file one with the in-app driver report from
**Driver tab → Share report**.

## "Filter is on but nothing happened"

| Symptom | Likely cause | Try |
|---|---|---|
| Toggle moves to On, no tint, notification appears | Active engine is `Overlay` but `SYSTEM_ALERT_WINDOW` not granted | Home → "Open settings" on the permission card; toggle the slider in system settings |
| Toggle moves to On briefly, then back to Off | `startForeground()` rejected by the OS (Android 12+) | The QS tile/widget path now opens the app automatically when Android blocks the background start; use the Home permission card if overlay permission is missing |
| Toggle stays On, notification missing | Notification channel disabled by user | Long-press the app icon → App info → Notifications → enable "Filter status" |
| Tint applies but is invisible | You picked a tint-only preset (Amber/Red/etc) with overlay engine + `dim=0` on Android 12+ | This was a bug fixed in v0.4.0+. Update; the overlay alpha now derives from tint strength too |
| Toggle stays On, controls move, screen does not change | Older build saved an unavailable pinned driver, `AlwaysOff` schedule, or `Off` preset | Update to the current build. The app now resets unavailable pinned drivers to Auto and makes the master switch restore a visible preset/schedule. |

## "I am evaluating a PWM-sensitive display setup"

PWM behavior depends on the panel and firmware. OpenLumen provides a
comfort-oriented workflow, not a guarantee about flicker or a medical
solution:

1. Driver → choose **Overlay** and grant the display-over-other-apps
   permission.
2. Presets → choose **PWM Comfort**, or use a custom warm tint. Use Home →
   **Dim** for the amount of overlay adjustment you prefer.
3. Keep hardware brightness at the level that looks right, then adjust the
   OpenLumen overlay independently. Watch battery and heat when using a
   higher hardware-brightness level.
4. If comparing OLED Saver / Screen Dimmer or Iris, pause OpenLumen first and
   test one overlay at a time. Stacking full-screen filters can produce
   confusing colors, unexpected dimming, or extra power use.

OpenLumen does not use an AccessibilityService backend. OLED Saver and Iris
have different permissions and rendering paths, and no combination is
promised to work identically across devices. See the [health and evidence
notes](health-evidence.md) for the comfort-only wording policy.

## "I get a black screen / wrong color and can't recover"

This is the worst case. Try in order:

1. **Toggle the QS tile.** It writes directly to DataStore and the service
   will clear on the next emission.
2. **Use the notification "Turn off" action** if the notification is
   visible.
3. **`adb` emergency off**:
   ```bash
   adb shell am broadcast -a com.openlumen.action.TURN_OFF \
       -n com.openlumen/.service.AutomationReceiver
   ```
4. **For SurfaceFlinger / KCAL stuck states**, run the ADB emergency-off
   command above first. Current builds use it to hard-clear known
   SurfaceFlinger transaction codes and KCAL sysfs paths.
5. **If a reboot doesn't fix it**, you're on KCAL and the kernel cached the
   bad values. Boot to recovery if you have it, or use ADB to clear KCAL
   sysfs. On kernels exposing `/sys/devices/platform/kcal_ctrl.0/kcal`, the
   current KCAL engine accepts 0–255 per-channel scalars; `255 255 255` is
   maximum white:
   ```bash
   adb shell su -c \
     "echo 255 255 255 > /sys/devices/platform/kcal_ctrl.0/kcal && \
      echo 0 > /sys/devices/platform/kcal_ctrl.0/kcal_enable"
   ```
6. **Last resort**: uninstall.
   ```bash
   adb uninstall com.openlumen
   ```

## "Driver tab says my engine is 'Not available'"

| Engine | Most common reason | Fix |
|---|---|---|
| `SecureSettings` | `WRITE_SECURE_SETTINGS` not granted | Copy the `adb pm grant` command from the Driver tab and run it |
| `SecureSettings` | Android 9 (API 28) or older | The `night_display_*` rows arrive in API 29. Use SurfaceFlinger or Overlay instead |
| `SecureSettings` | Dim does nothing | Extra Dim needs API 31 and an OEM that ships it. The driver report lists which secure rows your device accepted |
| `SurfaceFlinger` | No root | Required. If you have root, see "su keeps prompting" below |
| `KCAL` | Wrong SoC (Tensor / Exynos / Dimensity) | KCAL is Qualcomm-only. Use SurfaceFlinger or Overlay |
| `KCAL` | Stock kernel without KCAL patch | Check: `adb shell ls /sys/devices/platform/kcal_ctrl.0/`. If empty, your kernel doesn't have KCAL — use SurfaceFlinger |
| `Overlay` | `SYSTEM_ALERT_WINDOW` denied | Always-available; permission rationale card on Home prompts you |

## "Schedule doesn't fire"

| Symptom | Likely cause | Try |
|---|---|---|
| Fixed-time end at 7:00 but tint stays on past 7:00 | `SCHEDULE_EXACT_ALARM` revoked | Settings → Apps → OpenLumen → Alarms & reminders → allow |
| Schedule fires hours late | Doze with `setAndAllowWhileIdle` fallback (no exact-alarm permission) | Grant the exact-alarm permission as above |
| Solar mode shows "Set location" forever | You haven't entered coordinates | Schedule tab → Set location → enter decimal lat/lng (no degree symbols) |
| Solar transitions are wildly wrong | Wrong hemisphere sign in coordinates | Northern hemisphere is positive latitude; western hemisphere is negative longitude (e.g. NYC = 40.71, -74.00) |
| Tint flips at the wrong minute | Device time is wrong | Settings → System → Date & time → "Set automatically" |

## "su keeps prompting / Magisk denies"

OpenLumen calls `su` for SurfaceFlinger and KCAL engines. If you see a
prompt each time the filter applies:

1. Open Magisk → SuperUser → find "OpenLumen" → set "Until Reboot" or
   "Forever".
2. If OpenLumen isn't in the SuperUser list, the request hasn't fired yet.
   Toggle the filter on once to trigger it.
3. If you see "denied" without a prompt: Magisk → Settings →
   Superuser → Auto Response = "Prompt" (some Magisk forks default to
   "Deny silently").

## "The overlay blocks me from tapping an install button"

Working as intended. Android 12+ refuses touches on system installer
screens while an untrusted overlay is showing. Workarounds:

1. **Use the QS tile** to disable the filter, install, then re-enable.
2. **Use a non-overlay driver** if your device supports one (Driver tab →
   pick CDM or SurfaceFlinger).
3. We track a planned "auto-pause on installer/permission flows" feature
   (roadmap C12). Until it ships, the tile is the supported workaround.

### Overlay viewport smoke for contributors

When triaging Overlay engine issues, run the local emulator smoke helper
against a debug build and attach its summary to the overlay issue template:

```powershell
rtk powershell -NoProfile -ExecutionPolicy Bypass -File tools\overlay_viewport_smoke.ps1 -Serial emulator-5554 -Package com.openlumen.debug
```

The helper pins the debug app to the Overlay driver and Always schedule,
then captures `build/overlay-smoke/` evidence for the active overlay window,
status/nav/taskbar surfaces, the current one-handed setting,
debug-only `FLAG_SECURE`, IME open/close, the overlay permission settings
screen, and the package installer intent. PackageInstaller may stay as
`REVIEW` on emulators that resolve the intent but do not keep a stable visible
installer window; keep that line in the issue instead of rewriting it.

## "Tile is missing from Quick Settings"

1. Open Quick Settings → edit (pencil icon) → drag "OpenLumen" into the
   active area.
2. On Samsung One UI, the tile order is locked to its own preferences —
   long-press an empty QS slot.
3. On a fresh install, the tile may not appear until the app has been
   opened once.

## "After a crash the filter doesn't come back on reboot"

Working as intended. Tied to roadmap candidate **C85** (boot-panic reset).
If OpenLumen's crash log was modified within five minutes before boot,
`BootReceiver` treats the shutdown as a panic recovery and forces
`enabled = false` instead of restoring the filter. The thinking: a user
who just rebooted after a black-screen episode doesn't want the filter
right back on at boot.

To re-enable, open the app once. Manual re-enable always works.

The crash log itself stays in place — About → "View crash log" shows
what happened — and you can clear it from the same dialog if you don't
want it to suppress auto-restore on the next boot.

## "I paused the filter, rebooted, and it stayed paused"

Working as intended. Tied to roadmap candidate **C116**. OpenLumen only
restores the foreground service after reboot when the saved preference
has `enabled = true`. If you turned the filter off before restarting,
`BootReceiver` leaves it off and does not try to infer schedule state on
its own.

To resume after reboot, turn the filter back on from the app, Quick
Settings tile, widget, or the documented ADB command.

## "Battery drain"

OpenLumen does not poll. The service is a foreground service (which keeps
it alive), but it doesn't run a timer. Specifically:

- The schedule uses `AlarmManager` (one wake every transition, typically
  twice a day).
- The light sensor uses `SensorManager` with `SENSOR_DELAY_NORMAL` (~5 Hz)
  and only when the ambient trigger is enabled.
- The engine `apply()` runs only on a state change.

If you see drain, it's almost certainly the screen-on time from longer
display use, not OpenLumen's CPU. Verify with Android battery stats: the
"Wakelock" attributable to OpenLumen should be ≤1% over a day. If it isn't,
file a bug with the device, OS version, and the schedule mode you're using.

## "Profile import says 'invalid JSON'"

The export format is `{"enabled": …, "activePresetKey": …, "schedule": …}`.
Common breakage:

- Edited with a Windows editor that inserted BOM. Re-save as UTF-8 without
  BOM.
- Concatenated multiple profiles. Each export is exactly one JSON object —
  pick one.
- Old export with `"latitude": NaN`. OpenLumen v0.4.0+ accepts these on
  read for backwards compatibility, but JSON validators don't. If your
  validator complains, replace `NaN` with `null` and re-import.

## "ADB grant of `WRITE_SECURE_SETTINGS` says 'has not requested permission'"

That message means you're running it against a different package. Verify:

```bash
adb shell pm list packages | grep openlumen
```

You should see `package:com.openlumen` (release) or
`package:com.openlumen.debug` (debug build). Grant the permission against
whichever package you actually installed:

```bash
adb shell pm grant com.openlumen.debug \
    android.permission.WRITE_SECURE_SETTINGS
```

## Backup and restore

OpenLumen participates in Android Auto Backup and device-to-device transfer.
A restore (or new-device setup from a backup) produces a working app with
the user's last filter, schedule, and profile settings intact.

**What is backed up:**

- Filter preferences (active preset, intensity, dim, contrast, gamma, etc.)
- Schedule settings (mode, times, offsets, transition duration)
- Approximate city coordinates for solar schedule (user-entered, not GPS)
- Favorite presets and saved profiles
- Notification prompt one-shot flags

**What is NOT backed up (excluded per privacy policy):**

- Crash log (`crash.log`) — device-local only
- Diagnostics event log (`diagnostics.log`) — device-local only

After a restore, the filter starts in the `enabled` state it was saved in.
If the restored preferences reference a driver that isn't available on the
new device (e.g., KCAL on a non-Qualcomm SoC), Auto mode re-probes and
picks the best available engine. The overlay permission and
`WRITE_SECURE_SETTINGS` grant must be re-established on the new device.

To manually export/import settings across devices without cloud backup,
use About → Export profile / Import profile. The JSON export includes all
preferences and saved profiles. Crash and diagnostic logs are never
included in exports.

## Reporting a bug

Use the templates in `.github/ISSUE_TEMPLATE/`. The driver report
(Driver tab → Share report) is the single most useful artifact you can
include — it captures device, OS version, granted permissions, and every
engine's probe result in one paste.
