# Automation: Tasker, Termux, and ADB

> Documented control surface for power users, scripts, and Tasker macros.
> Tied to roadmap candidates **C70** (Tasker intents) and **C71** (Shell/ADB
> command docs).

OpenLumen's foreground service accepts a small set of intent actions. These
are stable across releases — they are the same actions the in-app UI uses,
so changing them is a breaking change. We bump
`Preferences.CURRENT_SCHEMA_VERSION` and document the move if any of them
have to rename in the future.

There is no over-network API. All commands run on the device, either
through ADB or a local intent broadcaster (Tasker, Macrodroid, Termux,
Automate).

## Action reference

All commands target the foreground service via
`am startservice -a <ACTION> -n com.openlumen/.service.LumenService`.

Debug builds use `com.openlumen.debug` for the package. The examples below
use the release package — adjust if you're scripting against a debug
install.

### Turn off

```bash
adb shell am startservice \
  -a com.openlumen.action.TURN_OFF \
  -n com.openlumen/.service.LumenService
```

Sets `enabled = false` and stops the service. The same intent the
notification's "Turn off" button uses. This is the canonical
emergency-off path.

### Turn on

```bash
adb shell am startservice \
  -a com.openlumen.action.TURN_ON \
  -n com.openlumen/.service.LumenService
```

Sets `enabled = true`. The service auto-starts on the next preference
emission.

### Toggle

```bash
adb shell am startservice \
  -a com.openlumen.action.TOGGLE \
  -n com.openlumen/.service.LumenService
```

Flips `enabled`. The QS tile uses this internally.

### Restore the previous preset

```bash
adb shell am startservice \
  -a com.openlumen.action.RESTORE_PREVIOUS \
  -n com.openlumen/.service.LumenService
```

Flips `activePresetKey` back to whatever it was before the last
preset change, and records the now-displaced key as the new previous.
No-op if no previous preset is recorded (fresh install, or every change
so far has been an undo). Tied to roadmap candidate C14.

### Cycle to the next favorite preset

```bash
adb shell am startservice \
  -a com.openlumen.action.CYCLE_PRESET \
  -n com.openlumen/.service.LumenService
```

Advances `activePresetKey` to the next entry in
`favoritePresetKeys`. No-op if the favorites list is empty. The
notification's "Next preset" action uses this.

### Set a specific preset

```bash
adb shell am startservice \
  -a com.openlumen.action.SET_PRESET \
  --es com.openlumen.extra.PRESET_KEY night \
  -n com.openlumen/.service.LumenService
```

Valid preset keys: `off`, `night`, `amber`, `red`, `salmon`, `sepia`,
`gray`, `deep`, `protan`, `deutan`, `tritan`. Unknown keys are ignored
silently (they fail the sanitize check on the next write).

### Set intensity

```bash
adb shell am startservice \
  -a com.openlumen.action.SET_INTENSITY \
  --ef com.openlumen.extra.VALUE 0.75 \
  -n com.openlumen/.service.LumenService
```

`VALUE` is a float in `0.0..1.0`. Anything out of range is clamped.
`NaN` is rejected.

### Set dim

```bash
adb shell am startservice \
  -a com.openlumen.action.SET_DIM \
  --ef com.openlumen.extra.VALUE 0.50 \
  -n com.openlumen/.service.LumenService
```

`VALUE` is a float in `0.0..0.95`. Same clamping rules as intensity.

## Tasker recipes

Tasker exposes "Send Intent" with these fields:

- **Action**: the action string above (e.g. `com.openlumen.action.SET_PRESET`)
- **Target**: `Service`
- **Package**: `com.openlumen`
- **Class**: `com.openlumen.service.LumenService`
- **Extras**: key/value pairs for SET_PRESET, SET_INTENSITY, SET_DIM

Example — "Set Night at sunset":

```
Task: Lumen Night
  A1: Send Intent
      Action: com.openlumen.action.SET_PRESET
      Cat: Default
      Package: com.openlumen
      Class: com.openlumen.service.LumenService
      Extra: com.openlumen.extra.PRESET_KEY:night
      Target: Service
  A2: Send Intent
      Action: com.openlumen.action.TURN_ON
      ...
```

Tasker doesn't need root to send these intents. The service is exported as
internal-only and accessed through the `am`-equivalent mechanism Tasker uses.

## Termux

Install the `termux-api` plugin and use:

```bash
am startservice -a com.openlumen.action.TOGGLE \
  -n com.openlumen/.service.LumenService
```

Same syntax, runs from the device without ADB.

## Macrodroid / Automate

Both expose generic "Send Intent" actions. Use the same target package and
class as above. Macrodroid sometimes calls intent extras "EXTRA" while
Automate calls them "Extras"; the keys (`com.openlumen.extra.PRESET_KEY`,
`com.openlumen.extra.VALUE`) are identical.

## Verifying a command worked

```bash
adb logcat -s OpenLumen/LumenSvc:V
```

You'll see a line on every prefs emission. If you see the line but no
visual change, the engine accepted the matrix but the display path may be
silent — see `docs/troubleshooting.md`.

## What's not exposed

These are deliberately not in the command surface:

- **Schedule mode changes**. Schedules are configuration, not commands.
  Edit them through the UI and they're persisted; automation can rely on
  them firing.
- **Engine selection**. Forcing a specific driver from a script is
  asking for trouble — use the Driver tab to pick once.
- **Profile import**. Profile imports go through the storage access
  framework and a preview dialog. Automating around the dialog defeats
  the point.
- **Permission grants**. ADB grants (`pm grant`) belong in a separate
  command, not in OpenLumen's intent surface.

## Stability promise

Action and extra strings in this document are part of the app's stable
API. Renaming or removing one is a breaking change and requires:

- A new `Preferences.CURRENT_SCHEMA_VERSION`.
- A note in the CHANGELOG under "Breaking".
- A deprecation period of at least one minor release where the old
  action still works alongside the new one.

If you find a command in the codebase that isn't documented here, treat
it as private and unstable.
