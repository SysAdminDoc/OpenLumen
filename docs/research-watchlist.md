# Research Watchlist

> Lightweight, periodically reviewed log of upstream ecosystem signals.
> Tied to roadmap candidate **C97** (Awesome/topic-index watchlist).

Reviewed before each release planning pass. Not a hard dependency for any
release — it exists so we notice ecosystem-level shifts before they show up
as user pain.

Last review: **2026-05-17** (rev 3 + rev 4 of `ROADMAP.md` walked every
entry in this watchlist; see `.ai/research/2026-05-17/RESEARCH_LOG.md`).

## Quick rules

- **Add a source when** it has either (a) a maintainer or community that
  produces signal we'd otherwise miss, or (b) policy/spec output that
  affects what OpenLumen can ship.
- **Drop a source when** it goes silent for >12 months *and* hasn't
  produced a usable signal in the same window.
- **Don't add a source just because it's adjacent.** If we wouldn't change
  the roadmap based on its output, it doesn't belong here.

## Active watchlist

### Direct competitors / lineage

| Source | Why we watch | Last useful signal |
|---|---|---|
| Red Moon GitHub issues | Open feature requests and bug reports from a similar user base | 2025-12 commit feed; open enhancement queue still useful |
| Red Moon F-Droid page | New release cuts, build metadata changes | F-Droid 4.0.0 dated 2022-08-23; activity since is minimal |
| Twilight (Play) changelog | Commercial competitor's feature trajectory (not feature parity goals, but trend signal) | Ongoing |
| Low Brightness (GitHub) | Modern small-team overlay tool, useful comparison for our Overlay engine | v5.1.0 on 2026-01-28 |
| Screen Dimming (GitHub) | Recent overlay microproject with emergency-unlock gesture — reference for C90 | v1.0 on 2026-02-18 |

### Platform / OS policy

| Source | Why we watch | What we'd change |
|---|---|---|
| Android Behavior Changes pages (annual) | Each release tightens FGS, overlay, exact-alarm, or sensor rules | Update manifest, runtime checks |
| `developer.android.com` foreground service types | `specialUse` rules can change between releases | Update the manifest property and `docs/release-checklist.md` |
| Android Developers blog → Privacy / Trust labels | Play Store labeling impacts the optional Play track | Adjust `fastlane/metadata/` if we ever ship to Play |
| Android Gradle Plugin release notes | AGP 9 plugin behavior changes | C95 spike |
| AndroidX Hilt release notes | Hilt Compose artifact moves | C96 spike |

### Security / supply chain

| Source | Why we watch | What we'd change |
|---|---|---|
| GitHub Advisory Database (Gradle / Maven) | Transitive vulnerabilities in build deps | Bump or replace, document in `docs/release-checklist.md` |
| F-Droid build metadata reference | Format / required fields can change | `fastlane/metadata/` updates |
| OWASP MASVS / MASTG updates | Overlay-attack guidance and threat-model changes | C51 threat model doc |

### Accessibility / i18n

| Source | Why we watch | What we'd change |
|---|---|---|
| Android Accessibility test guidance | New TalkBack/contrast/touch-target requirements | C55–C58 |
| Weblate Android workflows (community) | Translation cadence patterns | C59 |

## Pruned (kept here for memory)

When we drop a source, leave a one-line note about why so it doesn't get
re-added.

- *(none yet — first revision of this doc)*

## Out of scope on purpose

- **Google Play developer policy updates not related to FGS / overlay /
  privacy labels.** OpenLumen's Play track is optional; we don't chase
  policy noise that doesn't affect F-Droid.
- **Wearable OS roadmaps.** Wear support is C21 on the roadmap; revisit
  the watchlist after that lands.
- **Generic Android news sites.** Too much noise per signal. Use them as
  pointers to primary sources (developer.android.com, AOSP code search),
  but don't watch them directly.

## Process

1. Each release planning pass, walk this list. ≤30 minutes total.
2. For each source: any signal worth a roadmap candidate adjustment?
   - **Yes** → open an issue with the `roadmap` label and link the
     source. Add the candidate to ROADMAP.md if needed.
   - **No** → next source.
3. Update the "Last useful signal" column when a source produces
   something we acted on.
4. Update `Last review:` at the top of this file.
