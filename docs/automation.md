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

## Authentication

Every command except `TURN_OFF` needs two things: the automation surface
switched on, and a token passed in the intent.

Open **About → Automation access**, turn on "Allow control from other apps",
and copy the token. Pass it as a string extra on every command:

```
--es com.openlumen.extra.TOKEN <your token>
```

Both are off on a fresh install, and an app upgrade lands in the same closed
state. If you were scripting against an older build, your commands stop
working until you enable the surface and add the token.

`TURN_OFF` is deliberately exempt from both checks. It is the emergency
escape hatch documented in [root-safety.md](root-safety.md), it can only move
the filter toward off, and it has to keep working when the screen is too
tinted to read a token off.

### Why a token and not a caller check

A `BroadcastReceiver` cannot find out who sent it a broadcast.
`Binder.getCallingUid()` inside `onReceive` returns the *receiving* app's own
UID, because a manifest receiver runs after the binder transaction has
already finished. `BroadcastReceiver.getSentFromUid()` exists from API 34 but
only reports a real UID when the sender opts in through
`BroadcastOptions.setShareIdentityEnabled`, which Tasker, Termux and
`am broadcast` do not do. A signature-level permission on the receiver would
lock out exactly the automation apps this surface exists to serve.

Releases before 0.8.0 compared `Binder.getCallingUid()` against the app's own
UID, which is always equal, so every app on the device was trusted. Treat any
build older than 0.8.0 as having an open automation surface.

The token is device-local. It is never sent anywhere, and profile exports
redact it — a `.json` you share with someone else carries no token and lands
with automation switched off.

If you think a token has leaked, hit **New token**. Every script holding the
old one stops working immediately.

## Action reference

All external commands target the exported automation receiver via
`am broadcast -a <ACTION> -n com.openlumen/.service.AutomationReceiver`.
The receiver forwards supported actions into OpenLumen's non-exported
foreground service under the app UID.

Debug builds use `com.openlumen.debug` for the package. The examples below
use the release package — adjust if you're scripting against a debug
install.

### Turn off

```bash
adb shell am broadcast \
  -a com.openlumen.action.TURN_OFF \
  -n com.openlumen/.service.AutomationReceiver
```

Sets `enabled = false`, hard-clears known SurfaceFlinger / KCAL root
state, and stops the service. This is the canonical emergency-off path.

### Turn on

```bash
adb shell am broadcast \
  -a com.openlumen.action.TURN_ON \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

Sets `enabled = true`. The service auto-starts on the next preference
emission.

### Toggle

```bash
adb shell am broadcast \
  -a com.openlumen.action.TOGGLE \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

Flips `enabled`. The QS tile uses this internally.

### Restore the previous preset

```bash
adb shell am broadcast \
  -a com.openlumen.action.RESTORE_PREVIOUS \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

Flips `activePresetKey` back to whatever it was before the last
preset change, and records the now-displaced key as the new previous.
No-op if no previous preset is recorded (fresh install, or every change
so far has been an undo). Tied to roadmap candidate C14.

### Cycle to the next favorite preset

```bash
adb shell am broadcast \
  -a com.openlumen.action.CYCLE_PRESET \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

Advances `activePresetKey` to the next entry in
`favoritePresetKeys`. No-op if the favorites list is empty. The
notification's "Next preset" action uses this.

### Set a specific preset

```bash
adb shell am broadcast \
  -a com.openlumen.action.SET_PRESET \
  --es com.openlumen.extra.PRESET_KEY night \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

Valid preset keys: `off`, `night`, `amber`, `red`, `salmon`, `sepia`,
`gray`, `deep`, `protan`, `deutan`, `tritan`. Unknown keys are ignored
silently (they fail the sanitize check on the next write).

### Set intensity

```bash
adb shell am broadcast \
  -a com.openlumen.action.SET_INTENSITY \
  --ef com.openlumen.extra.VALUE 0.75 \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

`VALUE` is a float in `0.0..1.0`. Anything out of range is clamped.
`NaN` is rejected.

### Set dim

```bash
adb shell am broadcast \
  -a com.openlumen.action.SET_DIM \
  --ef com.openlumen.extra.VALUE 0.50 \
  --es com.openlumen.extra.TOKEN $OPENLUMEN_TOKEN \
  -n com.openlumen/.service.AutomationReceiver
```

`VALUE` is a float in `0.0..0.95`. Same clamping rules as intensity.

## Tasker recipes

Tasker exposes "Send Intent" with these fields:

- **Action**: the action string above (e.g. `com.openlumen.action.SET_PRESET`)
- **Target**: `Broadcast Receiver`
- **Package**: `com.openlumen`
- **Class**: `com.openlumen.service.AutomationReceiver`
- **Extras**: `com.openlumen.extra.TOKEN` on every action except TURN_OFF,
  plus the key/value pairs for SET_PRESET, SET_INTENSITY, SET_DIM

Store the token in a Tasker variable such as `%OLTOKEN` so you only have to
change it in one place after a regenerate.

Example — "Set Night at sunset":

```
Task: Lumen Night
  A1: Send Intent
      Action: com.openlumen.action.SET_PRESET
      Cat: Default
      Package: com.openlumen
      Class: com.openlumen.service.AutomationReceiver
      Extra: com.openlumen.extra.PRESET_KEY:night
      Extra: com.openlumen.extra.TOKEN:%OLTOKEN
      Target: Broadcast Receiver
  A2: Send Intent
      Action: com.openlumen.action.TURN_ON
      Extra: com.openlumen.extra.TOKEN:%OLTOKEN
      ...
```

Tasker doesn't need root to send these intents. Use Broadcast Receiver as the
target type; OpenLumen's receiver forwards the action to the internal service.

## Termux

Install the `termux-api` plugin and use:

```bash
am broadcast -a com.openlumen.action.TOGGLE \
  --es com.openlumen.extra.TOKEN "$OPENLUMEN_TOKEN" \
  -n com.openlumen/.service.AutomationReceiver
```

Same syntax, runs from the device without ADB. Put the token in
`~/.bashrc` as `export OPENLUMEN_TOKEN=...` so scripts pick it up.

## Macrodroid / Automate

Both expose generic "Send Intent" actions. Use the same target package and
automation receiver class as above. Macrodroid sometimes calls intent extras "EXTRA" while
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
- **Reading the token**. There is no command that returns the automation
  token. It comes out of the app screen or not at all, otherwise an
  unauthenticated caller could fetch the credential that authenticates
  callers.
- **Turning automation on**. Enabling the surface is a deliberate in-app
  action. A command for it would be reachable by the apps the switch exists
  to keep out.

## Stability promise

Action and extra strings in this document are part of the app's stable
API. Renaming or removing one is a breaking change and requires:

- A new `Preferences.CURRENT_SCHEMA_VERSION`.
- A note in the CHANGELOG under "Breaking".
- A deprecation period of at least one minor release where the old
  action still works alongside the new one.

Requiring `com.openlumen.extra.TOKEN` in 0.8.0 broke that promise on purpose.
The old surface had no working access control at all, so there was no safe
version of a deprecation window that left it open for another release. It is
recorded under Breaking in the CHANGELOG and carries schema version 3.

If you find a command in the codebase that isn't documented here, treat
it as private and unstable.
