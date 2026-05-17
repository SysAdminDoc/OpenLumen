# Contributing to OpenLumen

OpenLumen is a privileged display-control utility. It runs as a foreground
service, owns the display color transform, and on root paths it talks to
SurfaceFlinger / KCAL via `su`. Contributions are welcome, but the bar is
"would you trust this on your own daily driver?" — please bring the same care.

## Hard constraints

These are non-negotiable for any PR that wants to merge:

- **License**: GPL-3.0-or-later. New files must carry the same license intent
  as the rest of the tree (no MIT/Apache-only additions that conflict).
- **No INTERNET permission, ever.** OpenLumen never requests
  `android.permission.INTERNET`, never adds a network library, and never adds
  telemetry, remote crash reporting, or remote config. CI enforces this on the
  merged manifest.
- **F-Droid clean.** No Google Play Services, no Firebase, no closed-source
  binaries, no reproducibility-breaking generated artifacts checked in.
- **No marketing copy in the app.** Strings should describe behavior, not sell
  outcomes. Health-adjacent copy must follow `docs/health-evidence.md`.
- **Catppuccin Mocha / AMOLED dark.** No pill-shaped buttons. New components
  should reuse the `LumenButton` / `LumenOutlinedButton` / `LumenTextButton`
  family in `app/src/main/java/com/openlumen/ui/components/`.

## Before you open a PR

1. **Pick a roadmap item.** Look in [ROADMAP.md](ROADMAP.md). The candidate
   inventory (`C01` … `C100`) is the canonical backlog. If your idea isn't
   listed, open an issue first so we can decide whether it lands in Now / Next
   / Later / Under Consideration / Rejected before code is written.
2. **Open a tracking issue** for non-trivial changes. Link the PR to it.
3. **Stay within one module if possible.** OpenLumen is four modules:
   `app`, `core-engine`, `core-schedule`, `core-prefs`. Cross-module changes
   are fine but they should be intentional, not accidental.

## Style and structure

- **Kotlin**, official code style. The repo enables `kotlin.code.style=official`
  in `gradle.properties`.
- **Compose Material 3** for UI. Use existing components in
  `ui/components/`; don't introduce a parallel button system.
- **Hilt** for injection. The DI graph lives in `app/src/main/java/com/openlumen/di/`.
- **DataStore** (single JSON blob) for persistence. Don't add a second
  preference store.
- **kotlinx.serialization** for any persisted data.
- **No deprecated APIs.** If you must, gate behind `@RequiresApi` /
  `Build.VERSION.SDK_INT` checks and add a short comment explaining the floor.
- **No reflection** outside `core-engine/engines/ColorDisplayManagerEngine.kt`,
  which is reflection-by-design (AOSP private API).

## Comments policy

- Comments explain *why*, not *what*. Identifier names already say what.
- Comment when there's a non-obvious constraint: an OEM quirk, an Android
  version-specific behavior, a security trade-off, a reason a value isn't the
  default you'd guess.
- Don't write comments that just restate the code or reference the current PR
  ("added for issue #42"). Use commit messages and `git blame` for history.

## Tests

- **Unit tests** for any matrix math, schedule logic, or preferences
  serialization. See `core-engine/src/test/`, `core-schedule/src/test/`,
  `core-prefs/src/test/` for patterns. We use JUnit 4 + Google Truth.
- **Tests must run on the JVM**. Robolectric is available only for the
  Roborazzi screenshot lane; instrumented device tests are still tracked
  in C83/C84 on the roadmap.
- New driver code should add at minimum a "doesn't throw on unavailable
  hardware" test if it can't be exercised on the JVM.
- Run `./gradlew test` before pushing. For UI or theme changes, also run
  `./gradlew :app:validateDebugScreenshotTest :app:verifyRoborazziDebug --no-configuration-cache`.
  CI runs `:app:assembleDebug`, `:app:lint`, module tests, and both
  screenshot lanes; please don't make CI the first place a failure is seen.

## Commit messages

- **Subject line under 72 characters.** Imperative mood: "add", "fix", "drop",
  not "added" / "adds" / "removed".
- **Body wraps at 72.** Explain *why* the change is being made, not just what
  changed. Reference roadmap candidate IDs when applicable (e.g. `Closes C42`).
- **One concern per commit.** Refactors get their own commit; behavior changes
  get theirs.
- We don't squash. Don't open a PR with `wip` / `fix typo` / `address review`
  noise — rebase locally first.

## Driver work

Anything that touches a `ColorEngine` implementation deserves extra scrutiny:

- **Document the device(s) you tested on.** Pixel 6, OnePlus 9 with KCAL kernel,
  etc. Put it in the PR description. We'll move it to `docs/device-matrix.md`
  on merge.
- **Never assume `su` succeeded.** Always check `SuResult.exitCode` and log on
  failure — see `core-engine/src/main/java/com/openlumen/engine/Su.kt`.
- **Never assume reflection succeeded.** AOSP private APIs drift; the
  `ColorDisplayManagerEngine` reflection layer must keep its current
  defensive `runCatching` shape.
- **Test the disable path.** A driver that can apply but can't cleanly clear
  leaves the user staring at red pixels. The `onDestroy` clear path in
  `LumenService` is your safety net — make sure your engine respects it.

## Security and overlay rules

- **No new overlay surfaces.** The existing `OverlayEngine` is intentionally
  the only `TYPE_APPLICATION_OVERLAY` window. New overlay UIs require an
  explicit design discussion (see C11 / C12 / C90 on the roadmap).
- **`SYSTEM_ALERT_WINDOW`** is the only sensitive permission OpenLumen
  requests. Don't add others without a roadmap candidate that justifies them.
- **`WRITE_SECURE_SETTINGS`** is declared but only granted via ADB. The CDM
  engine gates on it at runtime — don't bypass that gate.

## What you don't need to do

- You don't have to write a CHANGELOG entry — release commits handle that.
- You don't have to bump the version — release commits handle that.
- You don't have to write a Discord/Slack post — there is no chat.

## Reporting bugs

Use the issue templates in `.github/ISSUE_TEMPLATE/`. A driver bug without
the in-app driver report (Driver tab → "Share report") is hard to act on.

## Code of conduct

Be technical, be specific, be polite. Disagreement about implementation is
fine. Personal attacks aren't. Maintainers reserve the right to close issues
or PRs that violate this — no warnings necessary.
