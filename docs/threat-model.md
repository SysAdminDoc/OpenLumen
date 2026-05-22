# OpenLumen Threat Model (MASVS-lite)

> Tied to roadmap candidate **C51** (OWASP MASVS-lite threat model). Loosely
> follows the [OWASP MASVS](https://mas.owasp.org/MASVS/) categories,
> abbreviated for the size of this project.

This document is **not** a formal security certification. It is a written
record of what OpenLumen does, what it deliberately doesn't do, and where
the trust boundaries are. Contributors should update it when they touch
anything in the affected category.

## In scope

- The OpenLumen Android application (`com.openlumen`, F-Droid distribution).
- Its foreground service, Quick Settings tile, Boot receiver, Schedule
  alarm receiver, App widgets, and AppWidget receivers.
- The four display engines (`ColorDisplayManager`, `SurfaceFlinger`,
  `KCAL`, `Overlay`).
- The local DataStore-backed preferences blob.
- The exported JSON profile format.

## Out of scope

- Anything that requires the user to install an entirely separate app
  (Magisk, KernelSU, Shizuku, Tasker). These are the user's choice and
  carry their own threat models. We document recommended settings in
  `docs/root-safety.md` and `docs/automation.md`.
- Vendor / OEM firmware behavior that diverges from AOSP. We track these
  in `docs/device-matrix.md` but cannot model them centrally.
- Side-channel attacks against the display panel (PWM patterns, refresh-
  rate fingerprinting). OpenLumen does not attempt to prevent or amplify
  these.

## Trust boundaries

```
+-------------------------------------------------------------+
|                       User (touch + ADB)                    |
+-----------------------+-------------------------------------+
                        |
                        v
+-------------------------------------------------------------+
|                     OpenLumen process                       |
|                                                             |
|  MainActivity ──> ViewModel ──> PreferencesStore (DataStore)|
|                                       ^                     |
|                                       |                     |
|  TileService ──┬─────> prefs.update() |                     |
|  BootReceiver  |                                            |
|  AlarmReceiver |                                            |
|  WidgetReceiver|                                            |
|                ^                                            |
|  LumenService -+ (foreground, owns engine)                  |
|       |                                                     |
|       v                                                     |
+-------|-----------------------------------------------------+
        | engine.apply(matrix)
        |
        +---> Engines:
              - CDM: reflection -> system_server (privileged)
              - SF:  su -> service call SurfaceFlinger 1015
              - KCAL: su -> /sys/devices/platform/kcal_ctrl.0/*
              - Overlay: WindowManager.addView (TYPE_APPLICATION_OVERLAY)
```

Inside the OpenLumen process there is one source of truth while the user
is unlocked (the credential-protected DataStore blob); all UI surfaces
are read-views or write-throughs. Direct Boot adds a narrow
device-protected mirror containing only the last active tint matrix,
selected engine, and enabled/active flags so pre-unlock restore can run
without opening the full preferences blob.

The engine layer is the only place that touches anything privileged. Two
of the four engines (`SF`, `KCAL`) shell out via `su`. The other two
(`CDM`, `Overlay`) use platform APIs that are already gated by the
Android security model.

## Identified risks

Categorized loosely by MASVS verticals.

### MASVS-STORAGE — Local data storage

| Risk | Severity | Mitigation |
|---|---|---|
| DataStore blob contains coordinates the user typed in | Low | App-private credential-protected storage; redacted from driver report; profile export warns the user it includes location |
| Direct Boot mirror is readable before first unlock | Low | Mirror excludes coordinates, profile names, saved profiles, and the full preference blob; it stores only enabled/active flags, selected engine, last active matrix, and AMOLED clamp state |
| Imported profile causes out-of-range values | Med | All values clamped in `PreferencesStore.sanitize()`; out-of-range fields fall to defaults; corrupt blob falls back to empty `Preferences()` |
| Crash log contains stack traces with class names | Low | Local-only file (`filesDir/crash.log`), never read or sent without user action; user can clear it from the About screen |
| Migrations corrupt user data | Low | Pure-function migrations; tested; sanitize always runs after; downgrade leaves blob unchanged |

### MASVS-CRYPTO — Cryptography

OpenLumen does no encryption. There is no key material, no signing, no
TLS. This is intentional — there is no network surface to encrypt. The
release APK signature is the only crypto we touch, and that lives in the
release workflow, not in the running app.

**Future-proofing note (C121).** AndroidX Security's
`EncryptedSharedPreferences` is deprecated as of late-2024 (S122).
OpenLumen does not currently use it — we use plain DataStore with
no encryption — so the deprecation does not affect us today. **If**
a future candidate ever needs at-rest encryption (e.g. an
"encrypted profile bundle" export feature, or a sensitive-secret
field added to the preference schema), the modern replacement is
Tink (`com.google.crypto.tink:tink-android`) wrapping a Proto
DataStore. The migration path looks like:

1. Add Tink to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
2. Generate or unwrap a master key via
   `KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)` and
   persist the keyset to Android Keystore via Tink's
   `AndroidKeysetManager`.
3. Wrap the existing JSON serializer in an `Aead.encrypt` /
   `Aead.decrypt` round-trip; or, for a typed Proto DataStore, use
   Tink's `StreamingAead` if the payload is large.
4. Bump `Preferences.CURRENT_SCHEMA_VERSION` and add a one-way
   migration that reads the plaintext blob, encrypts it, and writes
   it back. The migration must tolerate downgrade (next read on a
   pre-encryption build would see ciphertext and fall back to
   defaults — acceptable for the trust-recovery-from-downgrade
   posture).

Decision today: **defer**. We have no field that warrants the cost
of bringing in Tink + key-rotation complexity + the deprecated
EncryptedSharedPreferences class would otherwise still be the path,
but it's wrong-for-2026 so we won't reach for it either. Documented
here so a future contributor doesn't reach for the deprecated API
under time pressure.

### MASVS-AUTH — Authentication

OpenLumen has no accounts and no authentication surfaces. The only
"authentication" anywhere in the system is:

1. The OS-level grant for `SYSTEM_ALERT_WINDOW` (user action in system
   Settings).
2. The OS-level grant for `WRITE_SECURE_SETTINGS` (ADB shell, requires
   developer-mode access).
3. The OS-level grant from Magisk / KernelSU for `su` (root manager UI).

Each is the platform's responsibility. We surface the relevant grant
states in the Driver tab and the driver report. On Android 17+, the
driver report also declares and queries `QUERY_ADVANCED_PROTECTION_MODE`
only to report Advanced Protection state.

### MASVS-NETWORK — Network communication

The manifest deliberately omits `android.permission.INTERNET`,
`ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE`. CI fails the build if
any of these reach the merged manifest (`permissions-audit` job).

OpenLumen also forbids these dependencies on the release classpath:
- `com.google.android.gms:*` (Play Services)
- `com.google.firebase:*`
- Any artifact whose group contains `play-services`

There is no telemetry, no analytics, no remote config, no crash
reporting. The crash log lives in app-private storage and never leaves
the device unless the user manually exports it.

### MASVS-PLATFORM — Platform interaction

This is the deepest category for OpenLumen because we're a foreground
service plus an overlay app plus a root-shell app.

| Risk | Severity | Mitigation |
|---|---|---|
| **Overlay tapjacking** — an attacker's app uses our overlay to bait taps | Med | OpenLumen's overlay is `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`. We don't accept input; Android 12+ untrusted-touch rule blocks system-installer taps under our overlay, which is the expected platform behavior. C12 will surface an in-app warning. |
| **Overlay over sensitive system flows** (installer, fingerprint, lockscreen) | Med | Same OS protection. The Driver screen has an info card explaining the blocked-touch behavior. C12 plans an auto-pause hook. |
| **Foreground service abuse** (running with no user value) | Med | `specialUse` type + manifest property documenting the use. The service stops itself the moment `enabled = false`. The notification's "Turn off" action lets the user kill it without entering the app. |
| **Boot receiver auto-starts the service unwantedly** | Low | `BootReceiver` only re-applies if the user had `enabled = true` before reboot. First-launch default is `enabled = false`. |
| **Root drivers black-screen the device** | High (impact), Low (likelihood) | Auto mode does not select root engines by default; they require explicit Driver-tab selection. `TURN_OFF` hard-clears known SurfaceFlinger/KCAL state through `DisplayEmergencyReset`, and `docs/root-safety.md` documents recovery procedures. |
| **`service call SurfaceFlinger` transaction-code drift** | Low | Probe iterates candidate codes (1015, 1023, 1030, 1036) and caches the winner per device. Failed writes invalidate the engine cache and emergency-off sends the disable transaction through the known code ladder. |

### MASVS-CODE — Code quality

| Risk | Severity | Mitigation |
|---|---|---|
| Unsafe reflection in `ColorDisplayManagerEngine` | Low | All reflection wrapped in `runCatching`; method/field cache nulled on failure; gate on `WRITE_SECURE_SETTINGS` before any reflection lookup |
| `su` subprocess hangs the service | Low | All `Su.runCommand`/`runShell` invocations gate on a `withTimeoutOrNull`; on timeout the process is `destroyForcibly()`'d and `-1` returned |
| Concurrent apply/clear races on the engine | Low | `LumenService.applyMutex` serializes every engine call; prefs flow is `.conflate()`d so slider drags don't queue multiple su calls |
| Lint / Dependabot drift | Low | CI runs `./gradlew :app:lint`; Dependabot opens weekly Gradle PRs grouped by ecosystem |

### MASVS-PRIVACY — Privacy posture (added in rev 4)

MASVS v2.1.0 added MASVS-PRIVACY as a first-class category
([S192](https://github.com/OWASP/masvs/releases/tag/v2.1.0)). The
substance was already covered across the **Data inventory** and
**Permission inventory** sections below; this section makes the
categorical coverage explicit.

| Risk | Severity | Mitigation |
|---|---|---|
| App identifies the user across installs | Low | OpenLumen does not collect any identifier stronger than `Build.MODEL`. No advertising ID, no Firebase ID, no Android ID, no IMEI, no MAC. Verified by the absence of `READ_PHONE_STATE` and Play Services on the release classpath. |
| App identifies the user across devices | None | Zero network egress (no INTERNET); zero account, zero cloud sync. There is no cross-device identifier surface. |
| Sensitive location data leaks | Low | Solar mode uses **user-entered** lat/lng only. No `LocationManager` / `FusedLocationProvider`. Coordinates redacted from driver report. Coordinates appear only in the user's own exported profile JSON, which the user controls via SAF. |
| Per-app usage data leaks | None | OpenLumen does not query `PACKAGE_USAGE_STATS` (rev 3 explicitly rejected C80). Does not query `QUERY_ALL_PACKAGES`. Does not iterate installed apps. |
| Foreground-app detection used as a tracking vector | None | C79 (Accessibility) and C80 (UsageStats) both rejected. The Shizuku path (C06) keeps task-state on-device with no network egress. |
| Crash reporting exfiltration | None | `CrashLogger` writes only to `filesDir/crash.log` (app-private). The file is excluded from `fullBackupContent` rules. No network upload path exists. |
| Display content / screen content extraction | None | `MediaProjection` is not used. AccessibilityService is not declared. The overlay engine sets `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` so it cannot read events. |
| Sensors used as a side channel | Low | Light sensor is the only sensor we touch; reading lux is the documented use, and the listener is paused while the screen is off. |
| Diagnostic / telemetry surfaces | Low | `DiagnosticsLog` is ring-buffered local text. Not auto-uploaded. User shares manually via SAF. |
| AAPM transparency | Med (UX, not risk) | C130 surfaces `AdvancedProtectionManager` state in the driver report so users can see whether they're on a hardened profile and what it implies. |

### MASVS-RESILIENCE — Anti-tampering / anti-debugging

Out of scope. OpenLumen is open-source GPL software; we don't try to
prevent reverse engineering or rooting. The integrity story is:

- Signed APKs (v1 + v2 + v3) so the OS can detect tampering at install.
- SHA-256 of every release artifact published alongside the binary.
- `actions/attest-build-provenance` on each CI release run.

If an attacker has installed a modified OpenLumen APK on your device,
the threat scope is far larger than display tinting; that's outside what
we can help with.

## Data inventory

What we store and where:

| Data | Where | Backed up? | Encrypted? | Notes |
|---|---|---|---|---|
| Preferences blob (JSON) | DataStore (app-private) | yes (cloud + device transfer) | OS-default app storage encryption | Includes coordinates the user entered |
| Direct Boot mirror | Device-protected DataStore | not intentionally backed up | Device-protected storage | Last active tint matrix, selected engine, enabled/active flags only |
| Crash log | `filesDir/crash.log` | excluded from backup rules | OS-default | Stack traces only; no PII |
| Adaptive icon / drawable resources | APK | n/a | n/a | Static assets |
| Compiled DEX | APK | n/a | n/a | Public code |

What we do **not** store:

- Identifiers stronger than `Build.MODEL` (no IMEI, no Android ID, no
  advertising ID).
- The list of installed apps.
- Location obtained from any platform API (we never call
  `LocationManager` / FusedLocationProvider).
- Per-app usage data.

## Permission inventory

What we ask for, why, and what would happen if denied:

| Permission | Why | If denied |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Overlay engine fallback | Overlay engine unavailable; CDM/SF/KCAL still work |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Long-running service | Filter cannot stay on across screen-off; revoked by Play if `specialUse` is challenged |
| `RECEIVE_BOOT_COMPLETED` | Auto-restore after reboot | User has to re-enable manually after reboot |
| `POST_NOTIFICATIONS` (API 33+) | Foreground service notification visibility | Service runs but notification is silent / hidden |
| `SCHEDULE_EXACT_ALARM` | Precise schedule transitions | Schedule fires within Doze tolerance instead of on the minute |
| `WRITE_SECURE_SETTINGS` (granted only via ADB) | CDM engine | CDM engine unavailable; SF/KCAL/Overlay still work |

Permissions we deliberately do **not** request:

- `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`
- `ACCESS_*_LOCATION`
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- `BLUETOOTH_*`

## Review cadence

This document is reviewed:

- Before each release planning pass (alongside `docs/research-watchlist.md`).
- After any change to a permission, an engine, or a Manifest entry.
- After any AOSP / OEM advisory that affects overlays or foreground
  services.

The latest review date is the date on the most recent commit that
touched this file. Run `git log --format='%ad %s' -- docs/threat-model.md`
to see it.

## Reporting a security issue

Open a GitHub issue with the `security` label, or — for issues serious
enough that public disclosure would harm users — email the maintainer
(see commit log). We do not run a bug-bounty program and cannot pay for
reports, but we will credit reporters in release notes.

We commit to:

- A response within 7 days.
- A fix or documented mitigation for confirmed issues within 30 days
  for medium / 14 days for high severity.
- A CVE filing when the issue meets MITRE's criteria.
