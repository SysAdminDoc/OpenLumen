# OpenLumen Roadmap

Tracking from v0.1.0 (scaffold) → v1.0.0 (CF.Lumen parity). Items are grouped by
release; check the box when an item is shipped and tagged.

## v0.2.0 — UI completeness ✅ (shipped 2026-05-16)

- [x] Time-picker dialog for fixed-time schedule
- [x] Location picker: manual coordinate entry (FusedLocationProvider deferred —
      adds Play Services dep that breaks F-Droid build)
- [x] Custom RGB color picker on Home screen
- [x] Sunset / sunrise offset sliders
- [x] Per-channel gamma sliders (R / G / B) — `pow(scale*(1-dim), 1/gamma)` math
- [x] Light-sensor calibration UI (live lux + threshold + "use current" button)
- [x] Permission rationale dialog for SYSTEM_ALERT_WINDOW (overlay driver)
- [ ] App-open transition animation (fade vs slide) — deferred to v0.4.0 polish

## v0.3.0 — Persistence and reliability

- [ ] AlarmManager fallback for schedule transitions (replace 1-minute ticker on
      devices where Doze is aggressive)
- [ ] Profile export / import as JSON file (Settings → Backup)
- [ ] Migration framework for `Preferences` schema bumps
- [ ] Restore on `LOCKED_BOOT_COMPLETED` for direct-boot-aware devices
- [ ] Crash log (local file only, never network) accessible from About screen

## v0.4.0 — Polish + tile UX

- [ ] Long-press tile → open driver settings directly
- [ ] Notification action: cycle through favorite presets
- [ ] Material You dynamic color opt-in (default stays Catppuccin)
- [ ] Settings: choose AMOLED true-black vs Mocha base surface
- [ ] In-app driver probe report with shareable text dump

## v0.5.0 — Differentiators CF.Lumen never shipped

- [ ] Home-screen widget (1x1 toggle, 4x1 preset chooser)
- [ ] Per-app rules (when YouTube/Kindle/Maps is foreground, switch preset)
- [ ] AMOLED-aware darken (push near-black pixels to 0,0,0 on OLED panels)
- [ ] Tasker integration — Intent receivers for set-preset / toggle / set-intensity
- [ ] Local HTTP toggle (opt-in, loopback only, for Home Assistant / MQTT bridges)

## v1.0.0 — Stability

- [ ] Confirmed working on Pixel 6 / 7 / 8 / 9 (Tensor)
- [ ] Confirmed working on a Snapdragon device with KCAL kernel
- [ ] Confirmed working on a non-rooted device via overlay
- [ ] F-Droid submission (metadata in `fastlane/`)
- [ ] Play Store listing (optional; F-Droid is primary)
- [ ] Screenshots for README and store listings (DPI-aware capture)
- [ ] Logo finalized from one of the 5 prompts in `branding/logo-prompts.md`
- [ ] Branch protection verified, release workflow exercised end-to-end

## Deferred / post-v1.0

- Wear OS companion — TileService that toggles the phone-side filter
- Color-vision-deficiency LUT-based correction (more accurate than the current
  channel-scaling presets, but needs OpenCV or a small precomputed LUT table)
- On-device screen-content detector for adaptive profiles (movie / e-reader / photo)
