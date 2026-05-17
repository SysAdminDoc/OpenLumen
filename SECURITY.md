# Security Policy

OpenLumen is an offline Android display-filter app distributed under
GPL-3.0-or-later. The full threat model lives in
[docs/threat-model.md](docs/threat-model.md); this file is the public-facing
disclosure policy.

## Reporting a vulnerability

If you find a security issue in OpenLumen, please report it **privately**
before disclosing publicly. We do not run a bug-bounty program but we
credit reporters in release notes when they request it.

Preferred path:

1. **GitHub Private Vulnerability Reporting** — open a draft advisory at
   <https://github.com/SysAdminDoc/OpenLumen/security/advisories/new>.
   This routes directly to the maintainer and gives us a private place
   to coordinate a fix without disclosing the bug.

Fallback path (if GitHub PVR is unavailable):

2. **GitHub issue with the `security` label** — only if the issue is
   low severity and public disclosure does not put users at risk. Do
   NOT use a public issue for anything that involves user data, the
   release signing key, or a workaround that an attacker could weaponise
   before a fix lands.

We are watching the issue queue and the security advisory inbox.

## Scope

The OpenLumen `com.openlumen` Android app, its foreground service, Quick
Settings tile, app widgets, boot receiver, schedule alarm receiver, and
the four display engines.

Also in scope:

- The GitHub Actions workflows under [.github/workflows/](.github/workflows/).
- The exported JSON profile format.
- The on-disk DataStore preferences blob.

Out of scope:

- Vulnerabilities in third-party apps the user installs separately
  (Magisk, KernelSU, Shizuku, Tasker). Those projects have their own
  security policies; we link to them where relevant in
  [docs/automation.md](docs/automation.md) and
  [docs/root-safety.md](docs/root-safety.md).
- Vendor / OEM firmware behavior that diverges from AOSP.
- Side-channel attacks against the display panel itself (PWM patterns,
  refresh-rate fingerprinting).
- Cosmetic UX bugs without a privilege-escalation or data-leak vector.

## Response commitments

We commit to:

- **Acknowledge** receipt within 7 days.
- **Assign a severity** (informational / low / medium / high / critical)
  and respond with an action plan within 14 days.
- **Ship a fix or documented mitigation**:
  - High / Critical — within 14 days of confirming.
  - Medium — within 30 days of confirming.
  - Low — bundled into the next planned release.
- **File a CVE** when the issue meets MITRE's criteria (third-party
  exploitable vulnerability with a clear remediation path).
- **Credit you** in the CHANGELOG and the GitHub Release notes if you
  request it.

## Disclosure timeline

We follow a coordinated-disclosure model. After a fix lands and a
release is out for 7 days (so users have a chance to update), we
publish the advisory publicly with details. If you have a specific
disclosure deadline (e.g., upcoming conference), tell us in the first
message so we can coordinate.

If a vulnerability is being actively exploited in the wild, we will
ship the fix immediately and publish the advisory the same day.

## What an OpenLumen vulnerability could look like

Sometimes a finding turns out to be platform behavior rather than an
OpenLumen bug. Examples we'd treat as in-scope:

- An overlay or root path lets the app touch privileged state outside
  what's documented (display-only color matrix).
- An importable profile blob triggers a crash, an OOM, an FGS leak,
  or any execution beyond the JSON deserialiser.
- A bug lets a third-party app on the same device extract or modify
  OpenLumen's preferences blob, crash log, or diagnostics log.
- The release APK has a signing-key or attestation discrepancy from
  what the release workflow produced.
- The `permissions-audit` CI job somehow lets a banned permission
  through.
- A user without `WRITE_SECURE_SETTINGS` can still write secure
  settings via OpenLumen.

Examples we'd treat as out-of-scope unless they imply something
deeper:

- An overlay covers a system installer dialog and blocks taps
  (documented Android 12+ behavior; the overlay engine info card
  surfaces it).
- A root engine fails on a kernel that doesn't expose the right
  sysfs nodes (documented per-device behavior in
  [docs/compatibility-table.md](docs/compatibility-table.md)).
- The app does not protect against an attacker who already has root
  on the device.

## Cross-references

- [docs/threat-model.md](docs/threat-model.md) — full MASVS-aligned
  threat model and risk inventory.
- [docs/sbom-and-advisories.md](docs/sbom-and-advisories.md) — SBOM
  workflow, advisory triage, and accepted-exposure register.
- [docs/release-checklist.md](docs/release-checklist.md) — release
  signing and supply-chain steps.

## Why no PGP key

Most reporters use the GitHub PVR flow (encrypted in transit, scoped
to the project, audit trail attached). We will publish a PGP key if a
real-world reporter asks for one.
