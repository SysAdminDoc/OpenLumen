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
- **C114** (Fine-grain dim precision for PWM-sensitive users): shipped
  2026-05-17 as inline ±0.5% nudge buttons next to the Home tab dim
  slider. Documentation framing: the affordance is a precision
  convenience for users who self-report needing sub-1% landing
  resolution, not a treatment for PWM sensitivity.
- **C115** ("Filter green light too"): the Kelvin slider on the Home
  tab (1000-10 000 K, via the Tanner Helland approximation) already
  suppresses green channel output at low Kelvin values; e.g. 1500 K
  drives green to roughly 17/255 alongside its red-saturation behavior.
  Users asking for "filter green light" (Red Moon issue #353, S86)
  should use Kelvin below ~2000 K. We deliberately do not add a
  separate "filter green" control because the Kelvin axis is the
  physically-grounded one and a separate G-channel suppressor would
  produce non-physical color casts the user couldn't reason about.
  Documented here so the answer is in the canonical evidence note
  rather than buried in a forum reply.

## Sources

Cited by roadmap candidate ID. Source IDs match `ROADMAP.md`'s Source
Appendix.

### Original sources (rev 2)

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

### 2025/2026 consensus shift (rev 3, C126 in ROADMAP rev 3)

The 2025/2026 evidence base has shifted further toward *"total luminance
matters more than spectrum for sleep onset."* One prominent researcher
publicly retracted earlier blue-light advocacy. We update this section
rather than the body of the document because the framing OpenLumen has
always used — "comfort, not treatment" — is *strengthened*, not
contradicted, by the new evidence. Sources:

- **S99** — Hacker News discussion thread synthesising the 2025/2026
  "blue light filters don't work; total luminance is the lever" position.
  https://news.ycombinator.com/item?id=47091606
- **S100** — Nature Scientific Reports (2026) — "Home lighting, blue-light
  filtering, and their effects on melatonin suppression."
  https://www.nature.com/articles/s41598-025-29882-7
  Takeaway: melanopic content (which combines intensity and spectrum)
  predicts suppression; raw spectrum alone is a weaker predictor.
- **S101** — PubMed (2025) — "Optimizing blue-blocking glasses for sleep
  and circadian health."
  https://pubmed.ncbi.nlm.nih.gov/40728371/
- **S102** — Sleep (2024) — "Melanopic irradiance defines display-light
  impact on sleep latency."
  https://pubmed.ncbi.nlm.nih.gov/36854795/

### 2026 evidence base expansion (rev 4)

- **S158** — Frontiers in Neurology (2025) — Systematic review and meta-
  analysis of blue-light-blocking glasses on actigraphic sleep outcomes.
  https://www.frontiersin.org/journals/neurology/articles/10.3389/fneur.2025.1699303/full
  Takeaway: trial evidence remains inconsistent; effect sizes small and
  heterogeneous.
- **S159** — Nature Scientific Reports (2025) — Home lighting, blue-light
  filtering, melatonin suppression (companion to S100).
  https://www.nature.com/articles/s41598-025-29882-7
- **S160** — medRxiv (Oct 2025) — "Effects of Melanopic Equivalent Daylight
  Illuminance on Sleep Regulation and Chronotype-Specific Responses."
  https://www.medrxiv.org/content/10.1101/2025.10.21.25338466v1.full
  Takeaway: melanopic EDI (which combines intensity and spectrum) is the
  right metric for predicting sleep regulation — supports OpenLumen's
  C127 "Perceived luminance reduction" indicator direction.
- **S161** — Cochrane — Blue-light-filtering spectacles probably make no
  difference to eye strain or sleep.
  https://www.cochrane.org/about-us/news/blue-light-filtering-spectacles-probably-make-no-difference-eye-strain-eye-health-or-sleep
  Takeaway: most-cited authoritative negative finding.
- **S162** — SAGE Journals (2026) — Blue-light-filtering spectacle lenses
  in managing vision-related symptoms: updated review.
  https://journals.sagepub.com/doi/10.1177/25158414251412798

Implementation note: C127 shipped the Home-tab "Perceived brightness
reduced by N%" metric on 2026-05-17. It is computed from transformed-white
relative luminance using sRGB channel weights, and remains a display-output
metric rather than a sleep or treatment claim.
  Takeaway: 2026 update confirms little/no clinical benefit on visual
  fatigue from blue-blocking products.

What this means for OpenLumen: tinting the display **may** reduce the
melanopic stimulus of the screen, especially at night, but the leap from
that to "this app makes you sleep better" is not supported by the
literature we can cite. The 2025/2026 consensus shift makes this gap
*larger*, not smaller — the evidence base now actively points away from
"spectrum-only" interventions. We let users make their own subjective
judgment about whether they like the result.

## If a contributor or downstream packager wants to add health claims

Don't. Open an issue first. We'd rather lose a feature than lose the
"honest about what we don't know" stance.
