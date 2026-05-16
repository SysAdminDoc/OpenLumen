# F-Droid / Fastlane metadata

This directory follows the [F-Droid build metadata
reference](https://f-droid.org/docs/Build_Metadata_Reference/) so OpenLumen
can ship through `fdroiddata` when the time comes.

## Layout

```
fastlane/metadata/android/
├── en-US/
│   ├── title.txt                Short title, ≤30 chars
│   ├── short_description.txt    Tagline, ≤80 chars
│   ├── full_description.txt     Long description, ≤4000 chars
│   ├── images/                  (TBD — see image checklist below)
│   └── changelogs/
│       ├── README.md            How per-version entries work
│       └── <versionCode>.txt    One file per release
└── <other-locales>/             Future translations (see C58, C59)
```

## Image checklist (not yet populated)

F-Droid expects, at minimum:

- `images/icon.png` — 512×512 PNG, the launcher icon at full size
- `images/featureGraphic.png` — 1024×500 PNG, banner
- `images/phoneScreenshots/1.png` … `8.png` — phone screenshots in display
  order; tile size depends on device

We deliberately don't fabricate these. They land when:

1. The adaptive icon work (C35) is finalized.
2. The screenshot matrix (C36) has at least one device capture for each
   tab.

Until then, F-Droid renders the placeholder. The textual metadata is
already complete and F-Droid-conformant.

## Submitting to F-Droid

See `docs/release-checklist.md`. The `fdroiddata` PR also needs build
metadata pointing at the GitHub tag and the signing fingerprint —
maintainer task at release time.
