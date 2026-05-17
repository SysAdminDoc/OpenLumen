# Prioritization Matrix — 2026-05-17

Scored / tiered shortlist that informs the rev 4 update of `ROADMAP.md`.
Preserves rev 3's scoring discipline (Impact / Effort / Risk on 1-5
scales; "Parity" vs "Leapfrog" classifications) and folds in this
session's four new candidates and the proposed tier shifts.

For the full rev 3 candidate inventory and tier placement, see
`ROADMAP.md` rev 3. This file shows *only the deltas* this session
proposes.

## Scoring rubric (unchanged from rev 3)

- **Impact**: 5 = release blocker / trust gate; 3 = parity; 1 = nice-to-
  have.
- **Effort**: 5 = multi-release; 3 = one PR; 1 = trivial.
- **Risk**: 5 = could break the trust posture or destabilize main; 3 =
  device-specific regression risk; 1 = isolated change.

## New candidate scoring

| ID | Candidate | Tier | Impact | Effort | Risk | Net (I-E-R) | Notes |
|---|---|---|---:|---:|---:|---:|---|
| C128 | FabricatedOverlay engine spike | Under Consideration | 4 | 4 | 3 | -3 | Gate on C06 spike outcome; could land as a 5th engine for the Shizuku-not-root tier. |
| C129 | OLED-aware gamma LUT clamp | Later | 3 | 4 | 3 | -4 | Successor to C66 scalar clamp; bundled-LUT vs runtime tradeoff same as C63. |
| C130 | AAPM driver-report surface | Now | 3 | 1 | 1 | +1 | Cheap transparency win; pairs with rev 3's C79/C80 rejection. |
| C131 | Eye Dropper integration on Android 17+ | Later | 2 | 2 | 1 | -1 | Optional UI affordance gated on Android 17 device base. |

## Proposed tier shifts

| ID | Candidate | Rev 3 Tier | Rev 4 Tier | Reason | Evidence |
|---|---|---|---|---|---|
| C123 | Glance widget rewrite | Under Consideration | Next | Glance is stable since 1.0.0; 1.1.0 shipped 2024-06-12. Removes the alpha-stability blocker rev 3 cited. | S193, S194 |
| C101 | Compose Preview Screenshot Testing CI | Now (risk 1) | Now (risk 2) | Tool is still 0.0.1-alphaXX (Apr 2026). Bump risk and document version-pin policy. | S148, S149 |

No other tier shifts proposed. Rev 3's placements are evidence-backed and
remain valid.

## Proposed score adjustments (no tier shift)

| ID | Field | Rev 3 | Rev 4 | Reason | Evidence |
|---|---|---|---|---|---|
| C111 | Risk | 1 | 1 | (unchanged) | S128, S137 confirm the deprecation; rev 3 was already correct. |
| C28 / C102 | Effort | 3 | 3 | (unchanged from rev 3) | S146 reconfirmed but rev 3 already dropped effort 4 → 3. |
| C120 | Effort | 1 | 1 | (unchanged) | S156 provides a direct disabling recipe — link it in `docs/reproducible-build.md`. |

## Now-tier composition for rev 4

Rev 3's Now list (12 items) plus C130 = 13. The Now-tier composition for
v0.5.0/v0.6.0:

1. C01 — Real-device validation
2. C34/C35/C36/C37/C45 — F-Droid release packaging (icon, screenshots,
   reproducibility, checklist)
3. C95 — AGP 9 migration
4. C96 — Hilt Compose artifact rename
5. C82/C103 — Android 17 readiness (renamed from API 36)
6. C105 — SAW-app FGS-from-background fallback
7. C106 — BOOT_COMPLETED FGS verification
8. C111 — BAL hardening readiness
9. C10/C11/C12/C90/C91 — Overlay-safe interaction model (largely shipped;
   per-app blocked-by-Shizuku documentation finalizing)
10. C83/C84/C91/C94 — Test and CI hardening
11. C38/C47-C51/C94 — Security and supply-chain baseline (already shipped;
    cadence visibility)
12. C100/C127 — Sleep-evidence consensus update
13. **C130 — AAPM driver-report surface (new)**

## Next-tier composition for rev 4

Rev 3's Next list plus C123. The Next-tier composition for v0.7.0 → v0.8.0:

1. C28/C102 — Direct Boot restore
2. C06 — Shizuku-backed privileged backend (also unblocks C11, C12, C69)
3. C21 — Wear OS companion
4. **C123 — Glance API widget rewrite (promoted from Under Consideration)**
5. C63 — CVD LUT correction
6. Driver-compatibility learning (continued)
7. Preset system v2 polish
8. C84 / C91 — Connected permission / overlay tests
9. Research-watchlist maintenance

## Later-tier additions for rev 4

Rev 3's Later list plus C129, C131.

- C22 — Android TV flavor
- C67 — AMOLED content-aware dimming (privacy-heavy; effectively blocked)
- C68 — Partial-screen filters
- C89 — Pixel-grid AMOLED dimming
- PWM-sensitive workflow guidance
- C81 — Multi-user / work-profile behavior
- C39 — Optional Play Store listing
- C86 — System brightness write
- **C129 — OLED-aware gamma LUT clamp (new)**
- **C131 — Eye Dropper integration on Android 17+ (new)**

## Under Consideration adjustments

Rev 3's UC list minus C123, plus C128.

- Optional FusedLocationProvider flavor
- C06 — Shizuku spike-on; ship-conditional
- C121 — EncryptedSharedPreferences successor (Tink + Proto DataStore)
- C08 — Reduce Bright Colors / system Extra Dim integration
- PWM-sensitive overlay-at-high-brightness preset bundle
- **C128 — FabricatedOverlay engine spike (new)**

## Rejected (rev 4)

No changes to rev 3's Rejected list. C79 (AccessibilityService default
backend) and C80 (UsageStats foreground detection) remain Rejected.

## "Why this matters now" summary

Three forces drive the rev 4 ordering:

1. **Android 17 stable lands June 2026.** Now-tier items C82/C103, C105,
   C106, C111, C130 are all gates for trustworthy v0.6.0 behavior on
   Android 17.
2. **AGP 10 closes the opt-out window mid-2026.** C95/C96 are now or
   panic-migrate later — rev 3 correctly Now-tiered both.
3. **F-Droid 1.0 cut is unblocked except for real-device rows and
   icon/screenshots.** C01 + C34-C37 are the single biggest non-platform
   gate.

If we hit all 13 Now items, OpenLumen ships its first F-Droid-ready 1.0
inside the Android 17 stable window. If we slip on C95 or C82, AGP 10
and Android 17's behavior changes both become forced migrations under
deadline.
