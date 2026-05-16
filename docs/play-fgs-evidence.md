# Play Store `specialUse` Foreground Service Evidence Pack

> Tied to roadmap candidate **C93**. F-Droid remains OpenLumen's primary
> distribution channel; this document exists so an optional Play Store
> listing can be defensible without compromising the offline philosophy.
> If we decide not to ship on Play, this document is still useful as a
> formal justification of the manifest entry.

OpenLumen declares `android:foregroundServiceType="specialUse"` in
`AndroidManifest.xml`. Google requires apps using `specialUse` to:

1. Declare *why* via the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest
   property.
2. Be ready to justify the declaration in a Play review with text and
   video evidence.

Both are non-negotiable as of [Play Console policy
2024-08-31](https://support.google.com/googleplay/android-developer/answer/13392821).

## What we declare

In `AndroidManifest.xml`:

```xml
<service
    android:name=".service.LumenService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Persistent display color tint for eye comfort and sleep hygiene." />
</service>
```

The property text is what shows in Play's policy review queue.

## Why we're not in any other `foregroundServiceType` category

The published `foregroundServiceType` values as of API 35:

- `camera`, `connectedDevice`, `dataSync`, `location`, `mediaPlayback`,
  `mediaProcessing`, `mediaProjection`, `microphone`, `phoneCall`,
  `remoteMessaging`, `health`, `shortService`, `specialUse`,
  `systemExempted`.

OpenLumen does not match any specific category:

- **Not `dataSync`**: we do no network sync.
- **Not `mediaPlayback` / `mediaProjection`**: we don't record or
  project the screen.
- **Not `location`**: we never request location permission. Solar
  schedules use user-entered coordinates.
- **Not `health`**: we explicitly do **not** claim health benefits
  (see `docs/health-evidence.md`).
- **Not `shortService`**: filter sessions run indefinitely while the
  user has the filter on. `shortService` is capped at 3 minutes.
- **Not `systemExempted`**: that category is for system apps. We are
  not a system app.

`specialUse` is the correct bucket: a long-running service that owns a
device-wide display transform, started by user action, controllable
from a Quick Settings tile and a foreground notification, with no
network or media surface.

## User-impact narrative

The narrative below is what we'd submit to a Play reviewer.

> OpenLumen is a screen color-temperature filter app. When the user
> enables the filter (via the in-app toggle, a Quick Settings tile, an
> AppWidget, a notification action, or an ADB intent), the
> foreground service starts and:
>
> 1. Owns the device's color transform via one of four engines:
>    - The AOSP `ColorDisplayManager` API (the same path the OS uses for
>      its built-in Night Light), requiring a one-time ADB grant of
>      `WRITE_SECURE_SETTINGS`.
>    - SurfaceFlinger's color-matrix transaction code via `su` (root
>      devices).
>    - The KCAL kernel driver via `su` (root + Qualcomm + custom
>      kernel).
>    - A `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_TOUCHABLE` and
>      `FLAG_NOT_FOCUSABLE` (universal rootless fallback).
> 2. Shows an ongoing notification with a "Turn off" action so the user
>    can disable the filter without entering the app.
> 3. Re-applies the user's configured matrix when the schedule fires,
>    when the ambient-light sensor reading crosses the user's
>    threshold, or when the user changes a preset / slider in any UI
>    surface.
>
> The service runs only while the user has the filter enabled. It
> stops itself the moment the user disables the filter from any
> surface. It does not collect data, does not access the network, and
> does not request `INTERNET`, `ACCESS_NETWORK_STATE`, location, or
> microphone permissions. CI fails the build on any such permission
> reaching the merged manifest.

## Evidence checklist

Before submitting to Play, gather (and store outside this F-Droid
metadata tree):

- [ ] Screenshots: Home tab (toggle), Schedule tab (mode + duration),
      Driver tab (engine pick + share report), Presets tab (preset list
      + favorites), About tab (backup + crash log + emergency off).
- [ ] 30-second screen recording: user enables the filter, sees the
      tint, drags Intensity, opens QS shade to confirm tile and
      notification, hits Turn off, sees tint clear.
- [ ] APK with `INTERNET` *not* present in the merged manifest. The
      `permissions-audit` CI job is the canonical proof.
- [ ] A brief written statement of the narrative above.
- [ ] Privacy policy URL pointing at `docs/threat-model.md` or an
      equivalent page describing what we do and don't store.

## Where to store the evidence pack

Not in this Git repository. The Play evidence pack should be
maintainer-private (the Play Console account already has access
controls). Tracking it in Git would mean every fork carries it, which
is fine for screenshots but not appropriate for the maintainer's Play
credentials, screen-recording session files, or any signed-asset
metadata.

The intent of this document is to keep the *reasoning* in the repo so
any future maintainer can recreate the evidence pack from primary
sources.

## What if Play rejects?

If `specialUse` is challenged:

1. Re-read this document and the user-impact narrative. Does the
   description still match the code? If not, fix the code first.
2. Offer the link to the AOSP Night Light documentation and the
   `WRITE_SECURE_SETTINGS` ADB-grant workflow — `ColorDisplayManager`
   is the same path Google's own Night Light uses, and reviewers may
   not realize that's what OpenLumen wraps.
3. If Play still rejects, **do not** switch to `dataSync` or
   `mediaPlayback` as a workaround. That would be a false declaration
   and a policy violation in the other direction.
4. Pull the Play listing. F-Droid distribution continues unaffected.
