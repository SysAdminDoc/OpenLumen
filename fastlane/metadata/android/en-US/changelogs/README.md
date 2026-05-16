# Per-version changelogs

F-Droid expects one file per version, named `<versionCode>.txt`, containing
the user-facing changelog for that release.

Example:

```
4.txt    →  matches versionCode 4 in app/build.gradle.kts
```

The release process (see `docs/release-checklist.md`) creates the matching
file when bumping `versionCode`. Keep entries under ~500 characters; this
is what F-Droid shows in the app's update screen, not the full repo
CHANGELOG.
