# Translations and Localization

> Tied to roadmap candidate **C59** (Weblate / translation workflow).

OpenLumen externalizes all user-facing strings into
`app/src/main/res/values/strings.xml`. This is the source of truth for
translations and the entry point for any contributor who wants to localize
the app.

## How to contribute a translation

1. **Pick a locale.** Choose the Android resource qualifier — e.g. `fr`
   for French, `de` for German, `ja` for Japanese, `zh-rCN` for
   Simplified Chinese, `ar` for Arabic (which also enables RTL).
2. **Create a new resource directory** at
   `app/src/main/res/values-<qualifier>/`.
3. **Copy `strings.xml`** from `app/src/main/res/values/strings.xml` to
   the new directory.
4. **Translate the string contents only.** Do not change the `name`
   attributes — those are the keys that Kotlin code references.
5. **Preserve placeholders.** Strings like `%1$s` (positional) or `%s`
   must remain unchanged. Their *position* in the sentence may move
   between languages, but the placeholder itself does not.
6. **Preserve escaped characters.** Strings containing `\'`, `\"`,
   `\\`, or `%%` must keep those escapes.
7. **Open a PR.** Title it `i18n: <language name> translation`. Note in
   the description that you are submitting under
   GPL-3.0-or-later (the same license as the rest of the project).

## What gets translated

Everything in `app/src/main/res/values/strings.xml`. Specifically:

- Tab names, button labels, dialog titles, error messages, notification
  text.
- Preset display names (Night, Amber, etc.) — though many of these are
  also recognizable English brand-equivalents and may stay untranslated
  in some locales.
- Tile subtitle, widget label, accessibility content descriptions.

What does **not** get translated:

- Roadmap candidate IDs (C01..C100) — these are internal.
- ADB command strings — these are technical, not user-facing prose.
- Source code comments — separately, the project keeps comments in
  English to lower the barrier for contributors who already speak
  English (the conventional Android dev lingua franca).

## Translation hosting policy

We have not adopted Weblate / Crowdin / Transifex. The reasons:

- The string surface is small (~80 strings as of v0.5.0). PR-based
  translations work fine at this scale.
- Hosted translation platforms introduce a per-translation
  attribution model that conflicts with the project's preference for
  a single license (GPL-3.0-or-later) and the standard "Co-Authored-By"
  attribution.
- A platform integration would add a network dependency to the
  contribution workflow — not to the app itself, but to the
  contribution surface. We'd rather contributors clone, edit a file,
  open a PR.

If the string surface grows past a few hundred entries, we'll revisit.

## RTL support

Tied to roadmap candidate **C58**. RTL is enabled at the manifest level:

```xml
<application android:supportsRtl="true" ... >
```

Compose Material 3 layouts honor `LayoutDirection.Rtl` automatically when
the system locale is RTL. The components in
`app/src/main/java/com/openlumen/ui/components/` use logical Modifier
APIs (`start` / `end`) rather than `left` / `right`, so they mirror
correctly.

If you find a layout that doesn't mirror, file it as a bug with the
locale you're testing in and a screenshot.

## What about the docs?

The `docs/` directory is intentionally English-only. Translating
architectural documentation has high maintenance cost and limited user
benefit. Contributors who need the docs in another language are
encouraged to read the English source; we'll happily review a
translation of the README if someone takes it on.

## Contribution attribution

We accept translations under GPL-3.0-or-later, the project's overall
license. The PR's commit messages should follow the project's
convention (see `CONTRIBUTING.md`). Translators are credited in the
release notes for the version their translation first ships in.

## Per-version policy

Translations that fall behind the English `strings.xml` (i.e., the
English file gains new entries that the translation doesn't have) do
not block releases. The Android resource fallback chain naturally
returns the English string when a key is missing in the user's
locale, so a partial translation is better than no translation.

Maintainers will ping outdated translators at release-planning time
with a list of new strings to translate. No reply is required.
