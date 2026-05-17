# Competitor Matrix — 2026-05-17

Direct competitors, adjacent projects, commercial products. Builds on the
sourcing already captured in `ROADMAP.md` rev 3's "Direct OSS and near-OSS
competitors" table (S10-S22, S69-S71, S81-S82, S86, S87, S103) and adds
the wider 2026 sweep delivered by the `Competitor & adjacent project sweep`
agent (S166-S184 in [SOURCE_REGISTER.md](SOURCE_REGISTER.md)).

## Direct OSS competitors

| Project | Stars / activity | Why it matters | Source |
|---|---|---|---|
| **Red Moon** (LibreShift) | 721★ — issue queue active through 2026-04-05 even though README says "not actively maintained" | The open-source Android baseline OpenLumen is replacing. Recent open issues (#339 previous-profile, #340 contrast, #342 Shizuku, #347 GrapheneOS shade, #348 F-Droid icon, #349 don't-resume-after-restart, #351 one-handed dim, #353 filter-melanopsin, #354 backup) are still useful roadmap fodder. | S10, S11, S86 |
| **corphish/Night-Light** | F-Droid: `com.corphish.nightlight.generic` — still actively distributed in 2025 | Canonical KCAL reference implementation; useful for cross-checking our `KcalEngine` sysfs probe behavior on Snapdragon kernels. (Listed in user's known set; flagged here because the competitor sweep agent re-discovered it as still-active in 2025.) | S15 |
| **EcoDimmer** (cartman-156) | 1★ / v1.0.0 May 2026 / MIT / Kotlin | **Privacy-hardened AccessibilityService overlay** — explicitly disables `canRetrieveWindowContent` and `flagRequestFilterKeyEvents`. The exact config that would let an a11y-using app pass Play review. Plus a "Shake to Rescue" accelerometer panic disable that's interesting prior art for C90 emergency-unlock gesture. | S166 |
| **Grayscaler** (C10udburst) | 143★ / v1.0 Feb 2025 / GPL-3.0 | **Per-app via Shizuku.** Uses the minimal permission triple `WRITE_SECURE_SETTINGS + PACKAGE_USAGE_STATS + QUERY_ALL_PACKAGES` granted in one Shizuku flow. This is directly relevant to our C06 (Shizuku backend) and C69 (per-app profiles) spike — they need exactly this trio for foreground-aware per-app color-matrix flipping. | S167 |
| **ColorBlendr** (Mahmud0808) | 2.1k★ / v2.1.1 Jan 2026 / GPL-3.0 | **Three-tier privilege ladder** (Root → Shizuku → Wireless ADB) with feature degradation per tier — a UX template for "you have N of 4 engines available." More importantly, uses the Android 12+ `FabricatedOverlay` API to mutate Material You tokens at runtime *without persistent files*. This is a potential **fifth engine** we don't list. | S168 (new candidate C128) |
| **Adaptive Theme** (xLexip) | 123★ / v2.0.0 Apr 2026 / GPL-3.0 | Event-driven ambient-light sensor read only on screen-on (we already do screen-off invalidation per C99); offers 4 setup paths for `WRITE_SECURE_SETTINGS` (web tool, Shizuku, root, manual ADB). The 4-path onboarding UX is borrowable for OpenLumen's Driver tab. | S169 |
| Twilight (Urbandroid, commercial) | Active commercial — v14.25 Feb 2026 | Reference for sun-cycle filtering, per-app, Wear OS tile, Chromebook, Hue + IKEA TRÅDFRI. Out-of-scope to mimic the network-bound integrations; in-scope to track the per-app and Wear directions. | S20, S87 |
| Shades / Night-Light (farmerbb) / Eye-Rest / Screen Filter / Shadovix / Pixel Filter | mostly archived ≤2019, occasional 2024-2026 hobby forks | Historical references for the overlay-engine approach + light-sensor trigger. Already documented in rev 2/3 of ROADMAP. | S14, S15, S18, S19, S69, S70 |
| **Low Brightness** (MihaiCristianCondrea) | 30★ / v5.1.0 Jan 2026 / Material You / no internet / Accessibility-service overlay | Active small modern reference; same broad shape as OpenLumen overlay engine. | S17 |
| **Screen Dimming** (Darexsh) | 0★ / v1.0 Feb 2026 / emergency-unlock gesture | Reference for C90 (emergency unlock). | S71 |
| **OLED Saver / Screen Dimmer (dev.rewhex)** | Active 2026 — Play | PWM-sensitive overlay-at-high-brightness workflow + pixel-level dim. Confirms PWM-sensitive demand (S80, S107). | S103 |
| **DimTV** (MarshMeadow) | 10★ / 2025-02 | The Android TV niche occupier. | S16 |
| **dim_overlay_app** / **SwingShift** | demo-only | Minimal sample code that's useful for understanding the shipping bar. | S81, S82 |

## Dormant / ancestor references

| Project | Status | Why we still care | Source |
|---|---|---|---|
| **CF.Lumen** | dormant since Dec 2020 | The product OpenLumen is the spiritual successor to. Documented driver-backend approach lets us rebuild without source. | S21, S22 |
| **f.lux Android** | beta, root-required, unmaintained | Reference for "root-required system path" demand on Android. | S22 |
| Nocturnal (macOS) | **archived 2024-08** | Cautionary tale: archived after macOS Sonoma broke private gamma APIs. Same risk applies to our `ColorDisplayManagerEngine` reflection ladder. | S180 |

## Commercial / platform references

| Product | Opportunity signal | Sources |
|---|---|---|
| Twilight | Sun-cycle ramp, Wear OS tile, automation, Hue/IKEA TRÅDFRI, AccessibilityService overlay, per-app, translations. Network features are out-of-scope; per-app + Wear are roadmap items. | S20, S87 |
| f.lux (multi-platform) | Established Kelvin + schedule UX. Android beta proves system-level demand. | S21, S22 |
| Iris (iristech.co) | PWM-aware dimming, partial-screen filters, presets, automation, color effects, multi-display. PWM-aware is C114; partial-screen is C68. | S23 |
| CareUEyes | Break-reminders + dim + filter bundle. Not in OpenLumen scope — single-purpose product. | S24 |
| Android Night Light (AOSP) | The system surface OpenLumen's CDM engine extends. | S25 |
| Android Extra Dim | Built-in dimming often too weak; third-party demand persists. | S41, S44 |
| Lunar (macOS) | Adaptive brightness from ambient sensors + location; "dim below zero" technique. | S39, S104 |
| **LightBulb** (Tyrrrz, Windows) | 2.7k★, 51 releases through Mar 2026. **Minimum-API-calls** engine throttling pattern. Battery-life analog to OpenLumen's overlay/CDM apply rate. | S179 |
| **Shifty** (macOS) | 1.3k★. **Per-website Night Shift disable** via AppleScript browser bridges. Validates per-app/per-context exclusion as an industry-standard need. | S178 |
| **Solace** (macOS, $4.99 one-time, zero-telemetry) | Weather-aware tinting — overcast = warmer. Out-of-scope for OpenLumen offline-first but interesting idea. | S177 |

## Adjacent OSS to borrow from (desktop / Wayland)

The original rev 2/3 list (Redshift, Hyprshade, sunsetr, wl-gammarelay-rs,
wluma, Lunar, ScreenDimmer) is preserved. New 2025/2026 finds:

| Project | Borrowable pattern | Source |
|---|---|---|
| **sunsetr** v0.11.1 (Nov 2025) | Named-preset profiles (Reading / Gaming / Sleep) bound to tile/widget. 10k-city interactive picker — far richer than our bundled ~95. | S172 |
| **hyprsunset** v0.3.3 (Oct 2025) | Compositor-side Kelvin→3x3 CTM matrix. Cross-check our Kelvin code against AOSP `mDisplayWhiteBalanceTintMatrix` for parity. | S173 |
| **wl-gammarelay-rs** v1.0.1 (Mar 2025) | DBus IPC surface (vs CLI flags). Android analog is our intent surface (already shipped) plus potentially an AIDL surface. | S174 |
| **nerdshade** v1.3.0 (Jun 2025) | Transition *curve*, not step. Plus `acpi_listen` lid-open resume. Android analog: resume on `ACTION_SCREEN_ON`. | S175 |
| **cosmos** v1.0.0 (Codeberg, Feb 2026) | **OLED-aware brightness emulation** — scale gamma LUT to keep `(0,0,0)` truly off rather than uniform pixel scaling. Directly relevant to OpenLumen's overlay engine on OLED phones. (Our C66 AMOLED clamp is the scalar version; this is the LUT version.) | S176 |

## Shizuku-backed peer-architecture exemplars

These are the prior art for OpenLumen's C06 (Shizuku backend) work.

| Project | Architecture lesson | Source |
|---|---|---|
| **LSFG-Android** (FrankBarretta) | **Cleanest 2026 per-app overlay pattern**: AccessibilityService for the visible overlay layer (passthrough at full opacity), `ITaskStackListener` via Shizuku-bound `IActivityManager` for foreground detection, Shizuku off the hot path (used only for diagnostics so revocation doesn't break the user's tint). | S181 |
| **DarQ** (KieronQuinn) | "No a11y needed" per-app force-dark via Shizuku-elevated `IActivityManager.ITaskStackListener`. Last commit 2022, but the architecture is sound and directly applicable to C11/C12/C69. | S182 |
| **RootlessJamesDSP** (timschneeb, audio) | "Shizuku as the bridge to a system-wide effect that normally requires root" — the same play OpenLumen makes for SurfaceFlinger. Worth reading their service-binding code for prior art on session survival across Shizuku restarts. | S183 |

## Wear OS

**Negative result**: no qualifying open-source Wear OS tint/dim app found
pushed after 2024-12-31. This is **an opportunity gap**: a minimal Wear OS
Tile that toggles the phone filter would be the first FOSS entry in this
form factor. Tracked as roadmap C21 (Wear OS companion).

## Android TV

| Project | Lesson | Source |
|---|---|---|
| **TvOverlay** (gugutab) | 0-95% transparent black layer as software dimming substitute on TVs where there's no `ColorDisplayManager` (same trick DimTV uses). **REST API + MQTT + Home Assistant integration** is a killer integration story for the living-room form factor. OpenLumen's TV flavor (C22, Later) could ship the same integration without the network permission by going through Android's intent system + an optional companion app that runs HA's MQTT bridge. | S184 |

## Strategic takeaways (rolled into FEATURE_BACKLOG.md and PRIORITIZATION_MATRIX.md)

1. **FabricatedOverlay (S168) is a candidate fifth engine** — Shizuku-only, Android 12+, survives reboot via persistent overlays. Tracked as new candidate C128.
2. **Per-app architecture is converging on LSFG-Android pattern (S181)** — keep Shizuku off the hot path. Update C06 design notes in `docs/overlay-and-per-app-design.md` to incorporate this.
3. **OLED-aware gamma LUT (S176)** is the next-tier AMOLED dim beyond our scalar clamp (C66). Tracked as new candidate C129.
4. **Wear OS is open** — first-mover FOSS opportunity. C21 already on roadmap.
5. **Per-website / per-context (S178)** validates the broader per-app/per-context exclusion design (C11/C12/C69).
6. **TV REST/MQTT (S184)** as integration story for C22 — ship the intent surface, let a separate HA bridge translate.

## Rev 5 competitor saturation update

Third-pass search did not find a new direct OpenLumen-grade competitor
with both root/framebuffer paths and a modern F-Droid-clean posture.

| Project / source | New signal | Lesson |
|---|---|---|
| DimTV (S254) | README now positions it as an Android TV + phone dimmer with overlay display, notification controls, scheduled dimming, color filter support, and a stated 3.3.1 build 37 release. | Keep Android TV flavor (C22) separate; TV users value simple overlay / blackout behavior, but DimTV's permission surface includes system settings / running-app retrieval that OpenLumen avoids. |
| F-Droid Dimmer package (S255) | Old overlay-only app is still useful as store taxonomy and permission-language reference. | F-Droid category / permission copy should be plain and conservative: Theming + System, overlay permission explained directly. |
| General blue-light-filter roundups (S256) | User-facing content still separates native Night Light, overlay filters, and root/KCAL quality paths. | OpenLumen's README distinction between CDM / SurfaceFlinger / KCAL / Overlay remains strategically important; don't blur it into generic "blue light filter" marketing. |
