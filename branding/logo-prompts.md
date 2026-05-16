# OpenLumen — Logo Prompts

Five prompts targeting ChatGPT / DALL-E. All require **true alpha-channel RGBA
PNG output** (see global branding rule).

The brand cue is **eclipse / waning crescent** — a soft warm crescent (sunset, red
filter active) edged into a cool slate disk (daytime, filter off). Final files
land in `branding/` alongside this doc.

## Common ending — paste at the end of each prompt

```
Background/output requirements: The final image must be a true transparent PNG in
RGBA format with a real alpha channel. Everything outside the main icon/logo must
be fully transparent, alpha = 0. Do not render a checkerboard pattern. Do not
render a white, gray, black, colored, or textured background. Do not simulate
transparency. Only the main icon/logo should contain visible pixels. If the
generated image includes a checkerboard or any visible background, remove it with
image processing and export a corrected transparent PNG artifact.

Final output: 1024x1024 PNG, RGBA, true transparent background, alpha channel
enabled, no checkerboard, no solid background, no watermark, no text unless
explicitly requested.
```

---

## 1. Minimal — pure geometry

```
Design a minimal logo for an Android app called "OpenLumen", an open-source display
color filter. Composition: a single waning-crescent shape, formed by subtracting a
slate-blue circle (color #45475A) from a warm peach circle (color #FAB387). The
crescent should be the only visible element. No text, no inner detail, no shading
gradient. Flat, vector-style edges. Sized to fill ~78% of the canvas with even
breathing room.
```

## 2. App — adaptive-icon ready

```
Design an Android adaptive-icon foreground for "OpenLumen", a display color filter
app. Composition: a waning crescent shape (warm rose-pink #F38BA8) on a transparent
background. The crescent must sit centered in a 108x108dp safe area and not extend
past the 66dp circular mask radius — leave at least 12% margin on every side so the
icon doesn't get clipped by Pixel's circular launcher mask or Samsung's squircle.
No background fill. Smooth curves, vector-clean edges. No text.
```

## 3. Wordmark

```
Design a wordmark for "OpenLumen". The word "Open" is in a regular weight in cool
slate (#A6ADC8), the word "Lumen" is in semi-bold in warm rose-pink (#F38BA8),
joined inline. Typeface: a clean geometric sans with rounded terminals (think Inter
or Manrope, semi-bold). Below the wordmark, a thin 1px slate underline that thickens
into a peach crescent on the right end. Centered, tight tracking, no tagline.
```

## 4. Emblem

```
Design a circular emblem for "OpenLumen": an outer slate ring (#45475A, 6% stroke
width) enclosing a centered warm-peach waning crescent (#FAB387). Inside the
crescent's negative space, a small open-circle motif (alluding to "Open"). The
emblem should read clearly at 24x24px favicon size — keep details bold. No text
inside the ring; no decorative serifs.
```

## 5. Abstract

```
Design an abstract identity mark for "OpenLumen": three concentric arcs of
decreasing radius, each in a different warm hue (#FAB387 outer, #F38BA8 middle,
#CBA6F7 inner), suggesting the warm-to-cool gradient that a screen filter applies
across the night. The arcs should not be full circles — each terminates at a
different angle, evoking sunset progression. No text, no halo, no glow. Sharp
vector curves.
```

---

## Integration checklist

After picking a winner:

- [ ] Crop the foreground variant (#2) to 432x432 PNG (adaptive icon foreground at xxxhdpi).
- [ ] Place at `app/src/main/res/drawable/ic_launcher_foreground.png` (or keep the
      current vector at the same path; verify the adaptive-icon XML still
      references `@drawable/ic_launcher_foreground`).
- [ ] Generate 48 / 72 / 96 / 144 / 192 mipmaps via Android Studio's Image Asset
      Studio (or ImageMagick) if not relying purely on the adaptive icon.
- [ ] Export a 512x512 store icon PNG for F-Droid / Play.
- [ ] Drop a banner.png and logo.png at repo root for the README badge / hero image.
- [ ] Bump CHANGELOG with the logo-finalized version (likely v0.5.0 or v1.0.0).
