# F-Droid Submission Recipe Draft

> **Status**: draft. This file is the *content* of the `metadata/com.openlumen.yml`
> file the maintainer would commit to a fork of
> [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
> Tied to roadmap candidate **C140** (rev 4.1).
>
> Source for the recipe format:
> [F-Droid Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/).
> Source for the submission workflow:
> [F-Droid Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
> (S206).
>
> Do NOT commit this file's *content* to OpenLumen's repo as
> `metadata/com.openlumen.yml`. The file lives in the *fdroiddata* repo
> after the MR is merged. This draft just makes the recipe diff-reviewable
> in OpenLumen's own tree before the maintainer files the MR.

## What goes in the fdroiddata MR

Paste the YAML block below into `metadata/com.openlumen.yml` in your fork
of `gitlab.com/fdroid/fdroiddata`. Adjust `CurrentVersion` and
`CurrentVersionCode` to match the release you want to submit (current:
v0.4.0 / versionCode 5; if you wait for v0.5.0 to ship first, the
submission can target that instead).

```yaml
Categories:
  - Theming
  - System

License: GPL-3.0-or-later

WebSite: https://github.com/SysAdminDoc/OpenLumen
SourceCode: https://github.com/SysAdminDoc/OpenLumen
IssueTracker: https://github.com/SysAdminDoc/OpenLumen/issues
Changelog: https://github.com/SysAdminDoc/OpenLumen/blob/main/CHANGELOG.md

AutoName: OpenLumen
Description: |-
  OpenLumen is an offline display color filter for Android. It runs as a
  foreground service, owns the display color transform, and falls back
  gracefully across four drivers depending on what your device supports:

  * AOSP ColorDisplayManager (rootless, framebuffer transform — same path
    the built-in Night Light uses)
  * SurfaceFlinger color matrix (root, framebuffer transform)
  * KCAL kernel driver (root, Qualcomm panels with KCAL kernels)
  * Overlay (rootless, universal fallback — capped at ~80% opacity by
    Android 12+ rules)

  OpenLumen does not request INTERNET. It has no telemetry, no remote
  crash reporting, no remote config, and no analytics. The manifest does
  not contain a network permission and CI enforces this on every build.

  Features:

  * Named presets — Night, Amber, Red, Salmon, Sepia, Grayscale, Deep
    Sleep, and three color-vision-deficiency channel-remap presets
  * Custom R/G/B picker with per-channel gamma sliders
  * Intensity and dim sliders, range-clamped on every read and write
  * Fixed-time and solar (sunset/sunrise, NOAA algorithm) schedules with
    configurable offsets
  * "Until my next alarm" schedule mode driven by the system alarm clock
  * Ambient-light-sensor trigger that engages the filter when the room
    dims
  * Quick Settings tile for one-tap toggle; 1x1 toggle widget and 4x1
    preset widget
  * AlarmManager-driven schedule transitions — no background polling
  * Boot persistence with crash-window panic reset
  * Profile export/import as JSON via the Storage Access Framework
  * Named profile library
  * Local-only crash log and diagnostics log (never sent anywhere)
  * Documented Tasker / Termux / ADB intent surface
  * Compose UI with Catppuccin Mocha theme + AMOLED true-black surface

  Privacy and trust:

  * GPL-3.0-or-later
  * No accounts, no ads, no paywall
  * No "improves your sleep" claims — see docs/health-evidence.md in
    the repository

RepoType: git
Repo: https://github.com/SysAdminDoc/OpenLumen.git

Builds:
  - versionName: 0.4.0
    versionCode: 5
    commit: v0.4.0
    subdir: app
    gradle:
      - yes
    rm:
      - dist/

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v(\d+\.\d+\.\d+)$
CurrentVersion: 0.4.0
CurrentVersionCode: 5
```

## Notes for the submitter

1. **`commit:` must be a tagged release**, not `main`. F-Droid pins each
   build to an immutable commit. The release tag for v0.4.0 is `v0.4.0`
   (created by the GitHub Release workflow). Verify the tag exists on
   `github.com/SysAdminDoc/OpenLumen/releases`.

2. **`AutoUpdateMode: Version`** + **`UpdateCheckMode: Tags ^v(\d+\.\d+\.\d+)$`**
   means F-Droid will pick up future releases automatically as long as
   you keep tagging them `vX.Y.Z`. The release workflow already does
   this.

3. **No `AntiFeatures:` entries required.** OpenLumen does not request
   INTERNET, ad networks, non-free deps, or upstream-only data. The
   F-Droid Anti-Features list (S210) does not flag `specialUse` FGS as
   an anti-feature — the only justification needed is the `<property>`
   manifest element, which we have.

4. **`Categories:`** uses F-Droid's official taxonomy. Theming + System
   are the closest fits; System is required for FGS-using utilities.

5. **`Description:`** mirrors `fastlane/metadata/android/en-US/full_description.txt`.
   Keep them in sync if you edit one.

6. **`rm:`** strips local `dist/` artefacts from the build sandbox.

7. **The build server runs `./gradlew assembleRelease`** by default with
   `gradle: yes`. OpenLumen requires `OPENLUMEN_KEYSTORE` for signing;
   F-Droid does its own signing for the F-Droid repo, so we do NOT
   provide the keystore. The unsigned APK from the build server is then
   re-signed by F-Droid's signing keys. Our `app/build.gradle.kts`
   handles unsigned releases via the `if (System.getenv("OPENLUMEN_KEYSTORE") != null)`
   gate at line 47.

8. **Reproducible builds checklist** (S154, S155, S156):
   - Confirm `JavaVersion.VERSION_17` matches the buildserver default.
   - The `META-INF/version-control-info.textproto` AGP file is a known
     non-determinism source (S156, C120). Disable per the recipe in
     `docs/reproducible-build.md`.
   - After the first build on F-Droid, watch for the per-app
     reproducibility indicator (S155) to surface.

## Submission steps

1. **Wait for v0.5.0** (or submit v0.4.0 today — either works, but
   v0.5.0 has the trust/distribution polish from rev 3 plus the
   2026-05-17 audit hardening).
2. **Verify the latest release tag is on GitHub**.
3. **Fork** `gitlab.com/fdroid/fdroiddata` to your GitLab account.
4. **Clone** the fork; create branch `add-com.openlumen`.
5. **Add** `metadata/com.openlumen.yml` with the YAML above (adjust
   versions to match the chosen release).
6. **Run** `fdroid lint com.openlumen` locally (requires the F-Droid
   server tools — `pipx install fdroidserver` per S206) to catch
   syntax errors before CI does.
7. **Push** + **open MR** labelled "New App" and titled
   `New app: com.openlumen (OpenLumen)`.
8. **Wait** 24–48h after merge for the F-Droid repo to refresh.

## What the maintainer needs to provide alongside the MR

Pulled from the F-Droid Quick Start Guide:

- ✅ `fastlane/metadata/android/en-US/title.txt` — already present.
- ✅ `fastlane/metadata/android/en-US/short_description.txt` — already
  present, 73 bytes ≤ 80-char limit.
- ✅ `fastlane/metadata/android/en-US/full_description.txt` — already
  present.
- ✅ `fastlane/metadata/android/en-US/changelogs/5.txt` — added in
  this pass (444 bytes ≤ 500-byte target).
- ✅ `fastlane/metadata/android/en-US/images/icon.png` — present.
  C35 shipped the 512×512 final icon on 2026-05-17.
- ⚠️ `fastlane/metadata/android/en-US/images/phoneScreenshots/{1..5}.png`
  — **missing**. Blocked on C36 (capture pass). Minimum 2 PNGs.
- ⏸ `fastlane/metadata/android/en-US/images/featureGraphic.png` —
  optional (banner). 1024×500 PNG.
- ⏸ Translations — not required for initial submission. The
  70% translation threshold per S207 / S111 applies only when you ship
  a translated release; the en-US baseline is enough for a v1.0 cut.

## Risk register for this submission

| Risk | Likelihood | Mitigation |
|---|---|---|
| `commit: v0.4.0` tag missing on GitHub | low | Verify before opening the MR; release workflow creates the tag on `workflow_dispatch` |
| F-Droid buildserver doesn't have JDK 17 by default | low | AGP 9.2.1 builds with JDK 17 in the local validation path; keep the recipe's JDK floor aligned with the Gradle/AGP version actually committed |
| `version-control-info.textproto` breaks reproducibility | high | Apply S156 fix as part of C120 |
| `specialUse` FGS gets pushback from F-Droid reviewer | low | Anti-Features list (S210) does not flag it; the `<property>` element provides the use-case justification |
| Description copy contains "Deep Sleep" preset name | low | The preset name is legacy from CF.Lumen; not a health claim. `docs/health-evidence.md` documents this explicitly |
| MR review takes >48h | medium | Normal first-time-submission cadence; the maintainer should not push for faster review |

## Why this is a draft, not a committed `metadata/com.openlumen.yml`

The YAML file lives in *fdroiddata*'s repository, not OpenLumen's. We
keep a draft in `docs/` so:

1. The recipe is reviewable in OpenLumen's own commit history.
2. Future edits to the description copy can be made in one place
   (here + the `full_description.txt`).
3. If the F-Droid submission gets pushback that requires recipe
   changes, the conversation can happen in OpenLumen's issues without
   bouncing across two repos.

When ready to submit, paste the YAML block above into the fork; do
**not** copy this entire markdown file.
