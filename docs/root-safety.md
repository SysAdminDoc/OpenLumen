# Root Mode Safety and Recovery

> Tied to roadmap candidate **C05** (Root prompt safety and recovery
> docs).

OpenLumen can talk to your display through three privileged paths:

- **`SurfaceFlinger`** — runs `service call SurfaceFlinger <code>` via `su`.
- **`KCAL`** — writes to `/sys/devices/platform/kcal_ctrl.0/*` via `su`.
- **`SecureSettings`** (shown as "System Night Light" in the app) writes the
  `night_display_*` and `reduce_bright_colors_*` rows that `ColorDisplayService`
  watches. Night Light needs Android 10 (API 29), Extra Dim needs Android 12
  (API 31) and only where the OEM ships it. Not root, but privileged in the
  same sense: it needs one ADB grant.

  ```bash
  adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS
  ```

  Releases through 0.7.1 had a `ColorDisplayManager` driver here instead. It
  could never work on a user install, because those setters need
  `CONTROL_DISPLAY_COLOR_TRANSFORMS`, which is `signature|privileged` and
  cannot be granted with `pm grant`. It was removed and replaced with the
  secure-settings writer, which reaches the same transform.

When any of these go wrong, you can end up with a tinted screen that
doesn't clear, a black screen, or kernel-cached panel state that survives
reboots. This page covers prevention and recovery.

Auto mode selects a root engine by default when root is detected, preferring
`SurfaceFlinger` and then `KCAL` by driver rank. Non-root devices use Overlay
by default. If you want to avoid root writes on a rooted phone, pin `Overlay`
from the Driver tab.

## Before you enable a root driver

1. **Make sure you have a working `su` binary.** Open a terminal app and
   run `su` once; confirm Magisk (or your root manager) prompts and you
   can grant access. If the prompt never appears, OpenLumen will get
   `127` (command not found) and silently fall back to a lower-rank
   engine.

2. **Confirm the engine is available before applying.**
   Driver tab → re-probe → confirm a green "Available" badge. If a probe
   says "Not available," current builds disable that row and older saved
   selections reset to Auto.

3. **Test with a mild preset first.** Start with `Amber` or `Night`, not
   `Deep Sleep`. If something goes wrong with a mild tint, you can still
   read the screen well enough to recover.

## What can go wrong

### SurfaceFlinger

- **A rejected transaction.** The colour matrix is `code 1015`, unchanged from
  Android 10 through 16. Earlier releases claimed the code drifted and probed
  1023, 1030 and 1036 as well; none of those was ever a colour-matrix code, and
  1023 is the colour mode. What can actually go wrong is the backdoor refusing
  the call, which needs `HARDWARE_TEST` or uid root. `service call` exits 0
  whatever happens, so the probe reads the reply parcel: `Parcel(Error:` means
  rejected, and `Parcel(NULL)` is the empty reply an accepted call produces.
- **`service call` blocked by Magisk module.** Some hide-detection
  modules drop SurfaceFlinger transactions. You'll see "not found" or
  exit code != 0 in `adb logcat -s OpenLumen/Su`.
- **Display thread livelock.** Very rare. If it happens, the screen
  freezes mid-apply and only a hard reboot recovers.

### KCAL

- **Wrong sysfs path.** Some kernels ship KCAL at
  `/sys/devices/platform/kcal_ctrl.0/` and others at variations like
  `/sys/class/leds/lcd-backlight/kcal*`. OpenLumen only handles the
  former; the latter shows as "Not available".
- **Bad value written.** KCAL writes are persistent until the kernel
  module reloads. Writing `0,0,0` to `kcal` (RGB triplet) gives you a
  black screen until you fix it.
- **Conflict with other KCAL apps.** Don't run two KCAL tools at once.
  The last writer wins, but apply order during boot is non-deterministic.

### SecureSettings

- **Permission silently revoked.** A factory reset or some MDM profiles can
  revoke `WRITE_SECURE_SETTINGS`. The driver re-checks at apply time and
  reports a failure rather than crashing.
- **A device with no Extra Dim.** `reduce_bright_colors_*` only works where
  the OEM ships the feature. The driver probes for it and reports which rows
  the device accepted in the driver report, so Night Light alone and Night
  Light plus Extra Dim are visibly different states.
- **Settings you already had.** The driver records what it found before it
  writes and hands each row back when it stops needing it. If you change Night
  Light or colour correction yourself while the filter runs, it leaves your
  choice alone from then on.

## Recovery, in order of severity

If something goes wrong, try these in order. Each is more disruptive than
the last.

### 1. Use the QS tile to toggle off

The tile reads/writes DataStore directly; it doesn't depend on the
service being healthy. If the tile responds, this is the fastest fix.

### 2. Use the notification "Turn off" action

The foreground notification has a "Turn off" action that sends
`ACTION_TURN_OFF` to the service. If the notification is visible, tap
it.

### 3. ADB emergency off

If both the tile and the notification are unreachable (e.g. the overlay
is opaque enough that you can't read the screen):

```bash
adb shell am broadcast -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.AutomationReceiver
```

This routes through the exported automation receiver into the same
`ACTION_TURN_OFF` handler the notification uses. The service writes
`enabled=false`, clears the active engine, hard-clears known
SurfaceFlinger/KCAL root state, and stops.

### 4. Reboot

For SurfaceFlinger problems, a reboot resets the framebuffer color
transform to identity. The boot receiver may re-apply the filter on
restart if it was enabled — toggle off in the app within the first 5
seconds after reaching the home screen if that happens.

### 5. KCAL hard reset

KCAL sysfs values persist until the kernel module reloads. To reset:

On kernels exposing `/sys/devices/platform/kcal_ctrl.0/kcal`, the current
KCAL engine accepts 0–255 per-channel scalars; `255 255 255` is maximum
white:

```bash
adb shell su -c \
  "echo 255 255 255 > /sys/devices/platform/kcal_ctrl.0/kcal && \
   echo 0 > /sys/devices/platform/kcal_ctrl.0/kcal_enable"
```

`kcal_enable=0` disables the panel-driver adjustment entirely.

### 6. Boot to safe mode

If you can't reach the home screen because the overlay is fully opaque
or the screen is unreadable:

- **Pixel / stock Android**: power off, then hold power → long-press
  "Power off" → "Reboot to safe mode".
- **Samsung**: power off, then power on. When the Samsung logo appears,
  press and hold Volume Down until the lock screen appears.
- **OnePlus**: power off, then power on while holding Volume Down.

Safe mode disables third-party apps. The OpenLumen service won't start;
its overlay won't show; you can uninstall it from Settings → Apps.

### 7. Uninstall

```bash
adb uninstall com.openlumen
```

Or via the Play / F-Droid client if ADB isn't an option.

## What OpenLumen does to limit damage

These are guarantees the code is currently expected to maintain. If you
find a case that violates one of these, it's a bug — file it.

- **No engine `apply()` runs without first being able to `clear()`.**
  Each engine's `clear()` is exercised in the probe path or at install.
- **Every engine switch resets the target cache.** The next preference
  emission dispatches a fresh matrix to the newly selected engine even if
  the user did not change intensity, dim, or preset values. JVM coverage
  for `ApplyDecisionGate` protects the source-level behavior; rooted
  device rows still need to record SF/KCAL smoke results.
- **The service synchronously clears on `onDestroy()`.** When the
  service is killed (system, ADB, user), it blocks for up to 2 seconds
  trying to send root display disable transactions.
- **The service rolls back `enabled=true`** if `startForeground()`
  fails. You don't end up with a permanent "supposed to be on but isn't"
  state.
- **Default preferences are conservative.** First launch is `enabled =
  false`, `schedule.mode = AlwaysOff`. The app never auto-enables on
  install.
- **A raised `kcal_min` is always recoverable.** On kernels whose
  `kcal_min` sits below the safety floor, OpenLumen raises it while the
  filter is on and puts it back on clear. Your original value is written
  to `filesDir/kcal-min-restore` at the moment the raise is issued, not
  after the whole write succeeds, so a partial failure or a process kill
  between the two cannot strand the node. The next apply or clear reads
  that record back and restores from it.

  If you ever need to check or undo it by hand:

  ```bash
  adb shell su -c "cat /sys/devices/platform/kcal_ctrl.0/kcal_min"
  adb shell run-as com.openlumen cat files/kcal-min-restore   # "<original> true"
  adb shell su -c "echo '<original>' > /sys/devices/platform/kcal_ctrl.0/kcal_min"
  ```

  `run-as` only works on a debuggable build; on a release build read the
  value from the driver report instead. A stale record is harmless: it is
  deleted as soon as a restore succeeds.

## Reporting a stuck-state bug

Include:

1. Driver tab → Share report (gives device, OS version, granted
   permissions, every engine's probe result).
2. Last 200 lines of `adb logcat -s OpenLumen LumenService Su`
   (no PII; OpenLumen does not log usernames, locations, or app
   contents).
3. What you were doing when it stuck: which engine, which preset, what
   intensity / dim values, whether the schedule had just fired.

We can't reproduce stuck-state bugs without the engine identity and the
device fingerprint. A "my screen turned red" issue without these will
get a "please add the driver report" comment and not much else.
