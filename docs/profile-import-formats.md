# Profile Import: Lineage Formats

> Tied to roadmap candidates **C32** (Red Moon profile import) and **C33**
> (CF.Lumen import notes). Neither importer is implemented yet — this
> document captures what we know about the legacy formats so a contributor
> can pick them up.

OpenLumen's native profile format is the JSON dump produced by
About → Export profile. Importing a legacy format means decoding the
foreign blob, mapping fields onto OpenLumen's
[`Preferences`](../core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt)
DTOs, and routing the result through the same
`PreferencesStore.previewImport(uri)` → preview-dialog → confirm path the
native importer uses. The preview path returns `ImportSummary` so callers
can surface duplicate saved-profile names skipped by the sanitizer.

## Native format reminder

For comparison, an OpenLumen export looks like (abridged):

```json
{
  "schemaVersion": 1,
  "enabled": false,
  "activePresetKey": "night",
  "customMatrix": { "r": 1.0, "g": 0.78, "b": 0.55, ... },
  "presetIntensity": 1.0,
  "dim": 0.0,
  "schedule": {
    "mode": "Solar",
    "startHour": 22, "startMinute": 0,
    "endHour": 7, "endMinute": 0,
    "latitude": 40.7128, "longitude": -74.006,
    "sunsetOffsetMin": 0, "sunriseOffsetMin": 0
  },
  "engine": "Auto",
  "lightSensorEnabled": false,
  "lightSensorLuxThreshold": 2.0,
  "favoritePresetKeys": ["night", "amber", "red", "deep"],
  "transitionDurationMs": 0,
  "previousPresetKey": null
}
```

Every importer's goal is to produce a `Preferences` instance with at
least the fields filled in that the source format expressed, and
defaults elsewhere.

## Red Moon (`LibreShift/red-moon`)

**Status**: Not implemented. Source repo:
https://github.com/LibreShift/red-moon

What we know:

- Red Moon stores its named profiles internally as `Profile` objects.
  Exposed serialization format is not documented in the README; the
  source files to read are `app/src/main/.../ProfilesModel.kt` and
  `app/src/main/.../filter/Filter.kt`.
- Red Moon's "intensity" and "dim" semantics are roughly compatible
  with ours: intensity 0..100% lerps toward identity; dim is an alpha
  on the overlay.
- Red Moon has no smooth-transition concept and no concept of multiple
  engines. An import should map to OpenLumen's
  `engine = EngineKindDto.Overlay` unless the user opts to change it.

Implementation sketch (when picked up):

1. Add a `core-prefs/.../RedMoonImport.kt` that decodes Red Moon's
   format and produces a `Preferences`. Don't include the format
   string parser inline — separate file so it can be tested.
2. Add a UI affordance on the About → Import dialog that auto-detects
   the format via a marker string or filename suffix.
3. Map preset names by best-effort label match (Red Moon's "Sleep" →
   OpenLumen's "Night", etc.). Document the mapping in this file.
4. Existing `Preferences.sanitize()` is the post-import safety net —
   the mapper need not duplicate range checks.

Open questions:

- Does Red Moon's exported file include a stable format identifier? If
  not, we should reject any file that doesn't include
  `{"schemaVersion": …}` as our native key unless the user explicitly
  opts into legacy-import.

## CF.Lumen (Chainfire's CF.Lumen)

**Status**: Not implemented. Source not publicly available.

CF.Lumen v3.x stored its profiles in shared-preferences keys with a
prefix like `pref_profile_<index>_...`. Exporting these requires
either:

- A user-supplied `data/data/eu.chainfire.lumen/shared_prefs/...xml`
  dump (root required to read on stock Android), or
- A user-typed conversion: read the relevant preferences in CF.Lumen's
  UI and re-enter the values in OpenLumen.

Without the source code we can't write a robust importer. The best we
can offer is documentation of the manual mapping:

| CF.Lumen concept | OpenLumen equivalent |
|---|---|
| Profile slot 1..5 | Named preset; create a custom preset with the saved RGB |
| Engine override (KCAL / Default) | `engine = EngineKindDto.Kcal` / `EngineKindDto.Auto` |
| Schedule (start/end + days of week) | `ScheduleDto.FixedTime` (days of week not supported, see below) |
| Auto-on-power-connected | Not supported in OpenLumen; deliberate scope choice |
| Always-on | `ScheduleDto.AlwaysOn` |

**Days-of-week** is a feature CF.Lumen had that OpenLumen doesn't.
Rather than implement it for an importer of a long-dormant app, the
recommended workaround is to leave the filter on with `AlwaysOn` and
toggle the schedule manually on the off-days.

## Importer contract

When (if) either of these importers lands:

- Add to `PreferencesStore` as `previewImport*(uri): Result<Preferences>`
  variants — same shape as the native one.
- Route through the same preview dialog. The user **never** loses
  state silently to an import.
- Log the import via `DiagnosticsLog.PREFS` so the event is traceable.
- Sanitize before persist — the foreign value space may overlap ours
  only loosely.

## Scope choice

The reason these importers are documented but not implemented:

- **Red Moon**: low-volume request and high test surface (no clear
  format identifier; the format itself could move if the project
  resumes maintenance). Reasonable Next-tier work; not Now-tier.
- **CF.Lumen**: no source, no stable format. A manual mapping table is
  the most honest deliverable.

If you want to take this on, file a tracking issue, link this
document, and start with the format-decoder unit tests before the
UI hookup.
