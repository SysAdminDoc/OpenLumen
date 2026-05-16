# Root Mode Safety and Recovery

> Tied to roadmap candidate **C05** (Root prompt safety and recovery
> docs).

OpenLumen can talk to your display through three privileged paths:

- **`SurfaceFlinger`** — runs `service call SurfaceFlinger <code>` via `su`.
- **`KCAL`** — writes to `/sys/devices/platform/kcal_ctrl.0/*` via `su`.
- **`ColorDisplayManager`** — uses the AOSP private API, gated on
  `WRITE_SECURE_SETTINGS` granted by ADB. Not technically "root" but
  privileged in the same sense.

When any of these go wrong, you can end up with a tinted screen that
doesn't clear, a black screen, or kernel-cached panel state that survives
reboots. This page covers prevention and recovery.

## Before you enable a root driver

1. **Make sure you have a working `su` binary.** Open a terminal app and
   run `su` once; confirm Magisk (or your root manager) prompts and you
   can grant access. If the prompt never appears, OpenLumen will get
   `127` (command not found) and silently fall back to a lower-rank
   engine.

2. **Confirm the engine is available before applying.**
   Driver tab → re-probe → confirm a green "Available" badge. If a probe
   says "Not available," do not pin that engine.

3. **Test with a mild preset first.** Start with `Amber` or `Night`, not
   `Deep Sleep`. If something goes wrong with a mild tint, you can still
   read the screen well enough to recover.

## What can go wrong

### SurfaceFlinger

- **Wrong transaction code.** SurfaceFlinger's `setDisplayColorTransform`
  used `code 1015` for years but drifted to `1023` / `1030` on some
  Android versions. The probe tries known candidates; if a new Android
  version ships a new code, the apply silently no-ops or returns an
  error.
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

### ColorDisplayManager

- **Reflection drift.** Future Android versions may rename internal
  methods. The engine catches that and reports "Not available" rather
  than crashing.
- **Permission silently revoked.** A factory reset or some MDM profiles
  can revoke `WRITE_SECURE_SETTINGS`. The engine re-checks at apply
  time and bails — no crash, just no effect.

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
adb shell am startservice -a com.openlumen.action.TURN_OFF \
    -n com.openlumen/.service.LumenService
```

This routes through the same `ACTION_TURN_OFF` handler the notification
uses. The service writes `enabled=false`, the engine clears, and the
service stops.

### 4. Reboot

For SurfaceFlinger problems, a reboot resets the framebuffer color
transform to identity. The boot receiver may re-apply the filter on
restart if it was enabled — toggle off in the app within the first 5
seconds after reaching the home screen if that happens.

### 5. KCAL hard reset

KCAL sysfs values persist until the kernel module reloads. To reset:

```bash
adb shell su -c \
  "echo 256 256 256 > /sys/devices/platform/kcal_ctrl.0/kcal && \
   echo 0 > /sys/devices/platform/kcal_ctrl.0/kcal_enable"
```

`256 256 256` is identity. `kcal_enable=0` disables the panel-driver
adjustment entirely.

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
- **The service synchronously clears on `onDestroy()`.** When the
  service is killed (system, ADB, user), it blocks for up to 2 seconds
  trying to send the identity matrix.
- **The service rolls back `enabled=true`** if `startForeground()`
  fails. You don't end up with a permanent "supposed to be on but isn't"
  state.
- **Default preferences are conservative.** First launch is `enabled =
  false`, `schedule.mode = AlwaysOff`. The app never auto-enables on
  install.

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
