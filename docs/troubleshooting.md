# Troubleshooting

> Common failure modes and what to try. Tied to roadmap candidate **C40**.

If your issue isn't here, file one with the in-app driver report from **Driver tab → Share report**.

## "Filter is on but nothing happened"

| Symptom | Likely cause | Try |
|---|---|---|
| Toggle moves to On, no tint, notification appears | Active engine is `Overlay` but `SYSTEM_ALERT_WINDOW` not granted | Home → "Open settings" on the permission card, then toggle the slider in system settings |
| Toggle moves to On briefly, then back to Off | `startForeground()` rejected by the OS (Android 12+) | The QS tile/widget path now opens the app automatically when Android blocks the background start. Use the Home permission card if overlay permission is missing |
| Toggle stays On, notification missing | Notification channel disabled by user | Long-press the app icon → App info → Notifications → enable "Filter status" |
| Tint applies but is invisible | You picked a tint-only preset (Amber/Red/etc) with overlay engine + `dim=0` on Android 12+ | This was a bug fixed in v0.4.0+. Update. The overlay alpha now derives from tint strength too |
| Toggle stays On, controls move, screen doesn't change | Older build saved an unavailable pinned driver, `AlwaysOff` schedule, or `Off` preset | Update to the current build. The app now resets unavailable pinned drivers to Auto and makes the master switch restore a visible preset/schedule. |

## "I am evaluating a PWM-sensitive display setup"

PWM behavior depends on the panel and firmware. OpenLumen provides a comfort-oriented workflow, not a guarantee about flicker or a medical solution:

1. Driver → choose **Overlay** and grant the display-over-other-apps permission.
2. Presets → choose **PWM Comfort**, or use a custom warm tint. Use Home → **Dim** for the amount of overlay adjustment you prefer.
3. Keep hardware brightness at the level that looks right, then adjust the OpenLumen overlay independently. Watch battery and heat when using a higher hardware-brightness level.
4. If comparing OLED Saver / Screen Dimmer or Iris, pause OpenLumen first and test one overlay at a time. Stacking full-screen filters can produce confusing colors, unexpected dimming, or extra power use.

OpenLumen doesn't use an AccessibilityService backend. OLED Saver and Iris have different permissions and rendering paths, and no combination is promised to work identically across devices. See the [health and evidence notes](health-evidence.md) for the comfort-only wording policy.

## "I get a black screen / wrong color and can't recover"

This is the worst case. Try in order:

1. **Toggle the QS tile.** It writes directly to DataStore and the service will clear on the next emission.
2. **Use the notification "Turn off" action** if the notification is visible.
3. **`adb` emergency off**:
   ```bash
   adb shell am broadcast -a com.openlumen.action.TURN_OFF \
       -n com.openlumen/.service.AutomationReceiver
   ```
4. **For SurfaceFlinger / KCAL stuck states**, run the ADB emergency-off command above first. Current builds use it to hard-clear known SurfaceFlinger transaction codes and KCAL sysfs paths.
5. **If a reboot doesn't fix it**, you're on KCAL and the kernel cached the bad values. Boot to recovery if you have it, or use ADB to clear KCAL sysfs. On kernels exposing `/sys/devices/platform/kcal_ctrl.0/kcal`, the current KCAL engine accepts 0 to 255 per-channel scalars, and `255 255 255` is maximum white:
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
| `SecureSettings` | `WRITE_SECURE_SETTINGS` not granted | Copy the `adb pm grant` command from the Driver tab and run it, or use Grant with Shizuku (see below) |
| `SecureSettings` | Android 9 (API 28) or older | The `night_display_*` rows arrive in API 29. Use SurfaceFlinger or Overlay instead |
| `SecureSettings` | Samsung Galaxy, or any phone without Night Light | Samsung ships Eye Comfort Shield instead of Night Light, so the driver has nothing to tint with and Auto uses Overlay. The driver report says `night light: not on this device`. Forcing the driver still works for the colour-vision presets |
| `SecureSettings` | Dim does nothing | Extra Dim needs API 31 and an OEM that ships it, and Android 12+ can hide those settings from apps (both Samsung phones tested do). The driver leaves Extra Dim alone when it can't read it, so use Overlay for dim. The driver report lists which secure rows your device accepted |
| `SurfaceFlinger` | No root | Required. If you have root, see "su keeps prompting" below |
| `KCAL` | Wrong SoC (Tensor / Exynos / Dimensity) | KCAL is Qualcomm-only. Use SurfaceFlinger or Overlay |
| `KCAL` | Stock kernel without KCAL patch | Check with `adb shell ls /sys/devices/platform/kcal_ctrl.0/`. If it's empty, your kernel doesn't have KCAL, so use SurfaceFlinger |
| `Overlay` | `SYSTEM_ALERT_WINDOW` denied | Always available, and the permission rationale card on Home prompts you |

## "I changed Night Light myself and the filter switched off"

That's deliberate, and the Driver tab says so when it happens.

The rootless driver doesn't paint its own layer. It writes the same system rows the Night Light, Extra Dim and colour-correction switches write, which means those rows have two authors: OpenLumen and you. Quick Settings writes them, the system sunset schedule writes them, Digital Wellbeing writes them at bedtime, and so does any other app you have granted `WRITE_SECURE_SETTINGS`.

When OpenLumen notices that a row it was holding now carries a value it didn't write, it lets that row go and turns the filter off rather than taking it back. The alternative is worse. The filter re-applies on every ramp step and every slider move, so a version that fought for the row would overwrite whatever you had just set, a second or two after you set it.

| What you did | What happens | To get the filter back |
|---|---|---|
| Changed the Night Light slider or switch while a warm preset was running | Filter switches off, your temperature stays | Turn the filter on again, and it takes the row over from where you left it |
| Turned colour correction off while a colour-vision preset was running | Filter switches off, correction stays off | Turn the filter on again |
| Changed Extra Dim while a dimmed preset was running | Filter switches off, your level stays | Turn the filter on again |
| Nothing, and it happened anyway | Another app holding `WRITE_SECURE_SETTINGS` is writing the same rows | Check the diagnostics timeline on the About tab. The entry names the row and the value that arrived |

The root drivers (`SurfaceFlinger`, `KCAL`) don't share their rows with anything, so this doesn't apply to them.

## "Schedule doesn't fire"

| Symptom | Likely cause | Try |
|---|---|---|
| Fixed-time end at 7:00 but tint stays on past 7:00 | `SCHEDULE_EXACT_ALARM` revoked | Settings → Apps → OpenLumen → Alarms & reminders → allow |
| Schedule fires hours late | Doze with `setAndAllowWhileIdle` fallback (no exact-alarm permission) | Grant the exact-alarm permission as above |
| Solar mode shows "Set location" forever | You haven't entered coordinates | Schedule tab → Set location → pick a city, tap "Use my location", or enter decimal lat/lng (no degree symbols) |
| "Use my location" says location is off or couldn't get a location | Location is off in system settings, or the phone has no recent fix | Tap "Open location settings" and turn it on, or pick a city instead. OpenLumen only asks for approximate location |
| A "Timezone changed" notification after travel | The solar location was saved in another time zone | Open OpenLumen and set the location again. With automatic location on, opening the app updates it once you're 15 miles away |
| Solar transitions are wildly wrong | Wrong hemisphere sign in coordinates | Northern hemisphere is positive latitude, and western hemisphere is negative longitude (e.g. NYC = 40.71, -74.00) |
| Tint flips at the wrong minute | Device time is wrong | Settings → System → Date & time → "Set automatically" |

## "su keeps prompting / Magisk denies"

OpenLumen calls `su` for SurfaceFlinger and KCAL engines. If you see a prompt each time the filter applies:

1. Open Magisk → SuperUser → find "OpenLumen" → set "Until Reboot" or "Forever".
2. If OpenLumen isn't in the SuperUser list, the request hasn't fired yet. Toggle the filter on once to trigger it.
3. If you see "denied" without a prompt, go to Magisk → Settings → Superuser and set Auto Response to "Prompt" (some Magisk forks default to "Deny silently").

## "The overlay blocks me from tapping an install button"

Working as intended. Android 12+ refuses touches on system installer screens while an untrusted overlay is showing. Workarounds:

1. **Use the QS tile** to disable the filter, install, then re-enable.
2. **Use a non-overlay driver** if your device supports one (Driver tab → pick CDM or SurfaceFlinger).
3. We track a planned "auto-pause on installer/permission flows" feature (roadmap C12). Until it ships, the tile is the supported workaround.

### Overlay viewport smoke for contributors

When triaging Overlay engine issues, run the local emulator smoke helper against a debug build and attach its summary to the overlay issue template:

```powershell
adb shell dumpsys window windows | grep -A2 OpenLumen
```

The helper pins the debug app to the Overlay driver and Always schedule, then captures `build/overlay-smoke/` evidence for the active overlay window, status/nav/taskbar surfaces, the current one-handed setting, debug-only `FLAG_SECURE`, IME open/close, the overlay permission settings screen, and the package installer intent. PackageInstaller may stay as `REVIEW` on emulators that resolve the intent but don't keep a stable visible installer window. Keep that line in the issue instead of rewriting it.

## "LineageOS reading mode and the filter cancel each other"

Reading mode is a gray matrix the system applies to the whole screen. Whether it combines with a tint depends on where the driver puts its colour.

| Driver | With reading mode on |
|---|---|
| SurfaceFlinger (root) | The driver writes the same matrix reading mode lives in. It draws the gray under the tint itself, so you get a tinted gray, and turning the filter off leaves reading mode as it was. |
| System Night Light | The system draws its gray first and Night Light over it, so the two combine into a tinted gray by themselves. |
| KCAL (root) | KCAL scales the panel after the system's matrix, so the tint lands on top of the gray. |
| Overlay | The overlay is part of the picture the system grays, so the tint goes gray too. Home names the setting. Switch reading mode off and use one of the drivers above for a tint over gray. |

The same applies to colour correction set to Grayscale.

If you want gray with a tint and don't need the system's reading mode for anything else, the Grayscale switch on Home does it from inside OpenLumen. It keeps whichever preset is active and can be flipped from the Grayscale tile in Quick Settings. It works on the SurfaceFlinger and System Night Light drivers.

## "Tile is missing from Quick Settings"

1. Open Quick Settings → edit (pencil icon) → drag "OpenLumen" into the active area.
2. On Samsung One UI, the tile order is locked to its own preferences, so long-press an empty QS slot.
3. On a fresh install, the tile may not appear until the app has been opened once.
4. Grayscale and Preset 1 to 4 are separate tiles in the same list. A preset tile follows that place in your favorites and shows as unavailable while the place is empty.

## "After a crash the filter doesn't come back on reboot"

Working as intended. Tied to roadmap candidate **C85** (boot-panic reset). If OpenLumen's crash log was modified within five minutes before boot, `BootReceiver` treats the shutdown as a panic recovery and forces `enabled = false` instead of restoring the filter. The thinking is that a user who just rebooted after a black-screen episode doesn't want the filter right back on at boot.

To re-enable, open the app once. Manual re-enable always works.

The crash log itself stays in place (About → "View crash log" shows what happened), and you can clear it from the same dialog if you don't want it to suppress auto-restore on the next boot.

## "I paused the filter, rebooted, and it stayed paused"

Working as intended. Tied to roadmap candidate **C116**. OpenLumen only restores the foreground service after reboot when the saved preference has `enabled = true`. If you turned the filter off before restarting, `BootReceiver` leaves it off and doesn't try to infer schedule state on its own.

To resume after reboot, turn the filter back on from the app, Quick Settings tile, widget, or the documented ADB command.

## "Battery drain"

OpenLumen doesn't poll. The service is a foreground service (which keeps it alive), but it doesn't run a timer. Specifically:

- The schedule uses `AlarmManager` (one wake every transition, typically twice a day).
- The light sensor uses `SensorManager` with `SENSOR_DELAY_NORMAL` (~5 Hz) and only when the ambient trigger is enabled.
- The engine `apply()` runs only on a state change.

If you see drain, it's almost certainly the screen-on time from longer display use, not OpenLumen's CPU. Verify with Android battery stats. The "Wakelock" attributable to OpenLumen should be 1% or less over a day. If it isn't, file a bug with the device, OS version, and the schedule mode you're using.

## "Profile import says 'invalid JSON'"

The export format is `{"enabled": …, "activePresetKey": …, "schedule": …}`. Common breakage:

- Edited with a Windows editor that inserted BOM. Re-save as UTF-8 without BOM.
- Concatenated multiple profiles. Each export is exactly one JSON object, so pick one.
- Old export with `"latitude": NaN`. OpenLumen v0.4.0+ accepts these on read for backwards compatibility, but JSON validators don't. If your validator complains, replace `NaN` with `null` and re-import.

## "ADB grant of `WRITE_SECURE_SETTINGS` says 'has not requested permission'"

That message means you're running it against a different package. Verify:

```bash
adb shell pm list packages | grep openlumen
```

You should see `package:com.openlumen` (release) or `package:com.openlumen.debug` (debug build). Grant the permission against whichever package you actually installed:

```bash
adb shell pm grant com.openlumen.debug \
    android.permission.WRITE_SECURE_SETTINGS
```

## Granting `WRITE_SECURE_SETTINGS` without a computer

If you have [Shizuku](https://github.com/RikkaApps/Shizuku), the Driver tab can run the grant for you. Start Shizuku (wireless debugging on Android 11 and later, or root), then open OpenLumen's Driver tab and tap **Grant with Shizuku**. Shizuku asks once whether OpenLumen may use it. Allow that and the grant runs on the phone, after which OpenLumen re-checks its drivers.

The button only shows while the permission is missing. It stays greyed out with a short note when Shizuku isn't installed, isn't running, or has OpenLumen blocked. In that last case, open Shizuku and allow OpenLumen from its app list. The driver report's Shizuku section says which of these you're in and whether the server runs as adb (uid 2000) or root (uid 0).

The grant survives reboots, so you can uninstall Shizuku afterwards. OpenLumen only uses it for this one command.

## Backup and restore

OpenLumen participates in Android Auto Backup and device-to-device transfer. A restore (or new-device setup from a backup) produces a working app with the user's last filter, schedule, and profile settings intact.

**What is backed up:**

- Filter preferences (active preset, intensity, dim, contrast, gamma, etc.)
- Schedule settings (mode, times, offsets, transition duration)
- Approximate city coordinates for solar schedule (user-entered, not GPS)
- Favorite presets and saved profiles
- Notification prompt one-shot flags

**What isn't backed up (excluded per privacy policy):**

- Crash log (`crash.log`), device-local only
- Diagnostics event log (`diagnostics.log`), device-local only

After a restore, the filter starts in the `enabled` state it was saved in. If the restored preferences reference a driver that isn't available on the new device (e.g., KCAL on a non-Qualcomm SoC), Auto mode re-probes and picks the best available engine. The overlay permission and `WRITE_SECURE_SETTINGS` grant must be re-established on the new device.

To manually export/import settings across devices without cloud backup, use About → Export profile / Import profile. The JSON export includes all preferences and saved profiles. Crash and diagnostic logs are never included in exports.

## Reporting a bug

Use the templates in `.github/ISSUE_TEMPLATE/`. The driver report (Driver tab → Share report) is the single most useful artifact you can include. It captures device, OS version, granted permissions, and every engine's probe result in one paste.
