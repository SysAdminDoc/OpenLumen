# Fastlane

This directory exists only for the F-Droid / Play Store metadata layout.
OpenLumen does not use a Fastlane pipeline for builds. The release flow is
local: run `py -3 tools/local_release_gate.py` from the repo root before
tagging, then follow the canonical release procedure in
`docs/release-checklist.md`.

The `metadata/android/` subtree follows F-Droid's expected layout. See
`metadata/android/README.md` for what goes where.
