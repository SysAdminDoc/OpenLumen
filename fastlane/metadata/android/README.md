# F-Droid / Fastlane metadata

This directory follows the F-Droid Fastlane metadata structure so OpenLumen
can ship through `fdroiddata` when the time comes.

## Layout

```
fastlane/metadata/android/
├── en-US/                       F-Droid fallback language
│   ├── title.txt                Short title, <=50 chars
│   ├── short_description.txt    Tagline, <=80 chars
│   ├── full_description.txt     Long description, <=4000 chars
│   ├── images/
│   └── changelogs/
│       ├── README.md            How per-version entries work
│       └── <versionCode>.txt    One file per release, <=500 chars
├── de/
├── es/
├── fr/
├── ja/
└── pt/
```

## Text limits

F-Droid expects, at minimum:

- `short_description.txt`: short summary, max 80 chars, no trailing dot.
- `full_description.txt`: longer listing body, max 4000 chars.
- `title.txt`: optional but included for clarity, max 50 chars.
- `changelogs/<versionCode>.txt`: localized release notes, max 500 chars.

## Image checklist

F-Droid expects, at minimum:

- `images/icon.png`: 512x512 PNG, the launcher icon at full size.
- `images/featureGraphic.png`: 1024x500 PNG banner.
- `images/phoneScreenshots/1.png` through `8.png`: phone screenshots in
  display order; tile size depends on device.

We deliberately do not fabricate screenshots. They land when the screenshot
matrix has at least one device capture for each tab. The textual metadata is
already complete and F-Droid-conformant.

## Submitting to F-Droid

See `docs/release-checklist.md`. The `fdroiddata` PR also needs build
metadata pointing at the GitHub tag and the signing fingerprint; this remains
a maintainer task at release time.
