# Health and Evidence Notes

> Tied to roadmap candidates **C60** (Health evidence note) and **C100**
> (Medical/pain-mode disclaimer templates).

OpenLumen reduces blue light in the displayed image. It does not treat
medical conditions. This document is the canonical reference for what we
will and will not claim, with the sources behind those choices.

## What we say

OpenLumen is for:

- **Comfort.** Many users find warmer or dimmer displays more comfortable in
  low light. This is a subjective experience and we treat it as such.
- **Schedule control.** Users can decide when their display tint changes —
  the app respects the schedule and doesn't second-guess.
- **Color preference.** Custom RGB, presets, and per-channel gamma let
  users dial in a look that matches their environment.

## What we deliberately do not say

OpenLumen is **not**:

- A sleep aid.
- A treatment for circadian rhythm disorders.
- A treatment for eye strain or digital eye fatigue.
- A treatment for migraine, PWM sensitivity, photophobia, or photosensitive
  epilepsy.
- A medical device.

We don't say these things because the evidence base for "blue-light filters
improve health outcomes" is weaker than ad copy in the industry would
suggest. See **Sources** below.

## Words we avoid

Avoid these in app copy, README, marketing, or PR descriptions:

- "improves sleep" / "helps you sleep" / "sleep better"
- "reduces eye strain" / "prevents eye damage"
- "protects your eyes"
- "doctor recommended" / "clinically proven"
- "treats" / "cures" / "prevents" anything

Prefer:

- "warmer tones in the evening"
- "reduces blue light in the displayed image"
- "for comfort" / "for low-light viewing"
- "many users find …" (subjective claim, not medical)

## In-app surfaces

Currently in the app:

- **About screen**: app name, version, license, source, "100% offline" note.
- **Schedule screen**: "When should it run?" — descriptive, no claims.
- **Presets screen**: preset names (Night, Amber, etc) — descriptive of
  visual character, not health benefit.
- **Permission rationale**: explains what permission does, not why it helps
  you.

Any future copy that ventures near health territory needs to cite a source
from this document or be removed.

## Preset naming

The presets are named after their *look*, not a health benefit:

- `Night` — warm, ~3200K equivalent.
- `Amber` — strong orange tint.
- `Red` — full red channel only.
- `Salmon` — pink-orange middle ground.
- `Sepia` — desaturated warm.
- `Grayscale` — saturation removed.
- `Deep Sleep` — historical name carried over from CF.Lumen parity. The
  preset itself is just a deeper warm-plus-dim combination; the name is
  about its position in the strength ladder, not a sleep claim.
- `Protan` / `Deutan` / `Tritan` — color-vision-deficiency simulation /
  remap presets named after the corresponding deficiency types.

The "Deep Sleep" label is the one that comes closest to making a health
claim. We keep it because it's a recognizable label in the lineage and
removing it would break user expectations from CF.Lumen. We do **not**
suggest it improves sleep anywhere in the app.

## Roadmap items that touch this

- **C60** (Health evidence note): this document. Done in v0.5.0.
- **C62** (Research-based preset labels): potential rename of "Deep Sleep"
  if we can find a non-medical label that retains recognition. Under
  consideration.
- **C100** (Medical/pain-mode disclaimer templates): when/if PWM-sensitive
  guidance lands, it must explicitly state "this is anecdotal user
  guidance, not medical advice."

## Sources

Cited by roadmap candidate ID:

- **S45** — Blue-light blocking glasses systematic review.
  PubMed Central, https://pmc.ncbi.nlm.nih.gov/articles/PMC12668929/
  Takeaway: insufficient evidence that blue-light-blocking products improve
  visual fatigue, sleep quality, or macular health.
- **S46** — Frontiers in Photonics consensus on circadian lighting.
  https://www.frontiersin.org/journals/photonics/articles/10.3389/fphot.2023.1272934
  Takeaway: melanopic effects of light on circadian biology are real but
  the dose-response of a screen-only tint adjustment is small and highly
  individual.
- **S47** — Sleep Advances review of blue-light interventions.
  https://academic.oup.com/sleepadvances/article/doi/10.1093/sleepadvances/zpaa002/5851240
  Takeaway: mixed results, methodological concerns in much of the
  literature, no consensus that blue-light filters improve sleep onset or
  quality.

What this means for OpenLumen: tinting the display **may** reduce the
melanopic stimulus of the screen, especially at night, but the leap from
that to "this app makes you sleep better" is not supported by the
literature we can cite. We let users make their own subjective judgment
about whether they like the result.

## If a contributor or downstream packager wants to add health claims

Don't. Open an issue first. We'd rather lose a feature than lose the
"honest about what we don't know" stance.
