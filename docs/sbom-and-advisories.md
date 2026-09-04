# SBOM and Advisory Scan

> Tied to roadmap candidate **C94** (SBOM and advisory scan). The local
> release gate lives at `tools/local_release_gate.py`.

## What we produce

For every release:

1. **SPDX-JSON SBOM** of the release classpath. Captures every
   transitive dependency the production APK is built against. Written
   to `build/reports/openlumen-release-gate/sbom.spdx.json`.
2. **Advisory scan** of the release classpath against OSV's public API.
   Output is written to
   `build/reports/openlumen-release-gate/advisory-report.json`.

The gate records advisory findings for maintainer review instead of
auto-failing on every advisory. The practical exposure for an offline
display-tint app is often lower than the announced severity, so release
maintainers triage the report before shipping.

## Triage workflow

After each scan:

1. Open `build/reports/openlumen-release-gate/advisory-report.json`.
2. For each High / Critical, open a tracking issue with the
   `security` label, link the advisory, and either fix or document why
   we're not exposed.
3. For each Medium, add to the maintainer's quarterly review list. We
   read the list at release planning time; do not block on it.
4. Low findings are noted in the report only — no individual issue
   filing unless the maintainer flags one specifically.

A finding that has been triaged (for example, because OpenLumen does not use
the vulnerable deserialization code path) gets a short note in this document
under "CVE-2024-7254 triage" so the next planning pass does not re-triage it
from scratch.

## CVE-2024-7254 triage

The previous `protobuf-java:3.21.x` note was a historical transitive
dependency observation and is not the current release graph. The release-gate
query generated on 2026-08-10 reported `status: ok`, 169 release-classpath
dependencies, and zero vulnerabilities. Its classpath contains AndroidX
`external-protobuf` artifacts but no `com.google.protobuf:protobuf-java`
coordinate.

CVE-2024-7254 (GHSA-735f-pc8j-v9w8) is a denial-of-service condition when
untrusted, deeply nested Protocol Buffers data is parsed. The fixed
`protobuf-java` lines are 3.25.5, 4.27.5, and 4.28.2 or later. See the
[NVD record](https://nvd.nist.gov/vuln/detail/CVE-2024-7254) and the
[upstream gRPC upgrade discussion](https://github.com/grpc/grpc-java/issues/11542).

This is **resolved for the current OpenLumen release artifact**: the current
SBOM does not contain the affected Java coordinate and the current OSV report
has no finding to allowlist. Re-open the triage if a future release report
lists `com.google.protobuf:protobuf-java` below a fixed line, or if OpenLumen
begins parsing attacker-controlled protobuf input. The no-`INTERNET` posture
remains defense in depth, not the primary reason to ignore a vulnerable
coordinate.

## Historical triage record

| CVE / GHSA | Affected dependency | Historical rationale | Recorded |
|---|---|---|---|
| CVE-2024-7254 (GHSA-735f-pc8j-v9w8) | `com.google.protobuf:protobuf-java:3.21.x` (transitive) | OpenLumen has no INTERNET permission and does not deserialize attacker-controlled protobuf input. The CVE describes a stack-overflow path triggered when deserializing maliciously-nested messages; the code path is not reachable from any OpenLumen call site. Tracked at https://github.com/advisories/GHSA-735f-pc8j-v9w8 (S77 in ROADMAP). Re-evaluate if the artifact is replaced or if a new transitive surfaces a different protobuf path. | 2026-05-17 (rev 4 of ROADMAP) |

## How to read an advisory report

The report is JSON. Each finding has:

- `id`: the CVE, GHSA, or OSV identifier
- `summary`: short description
- `aliases`: related advisory identifiers
- `dependency`: the dependency coordinate

The mapping from advisory severity to OpenLumen's practical exposure
needs to consider:

- **Network attack surface**: zero. OpenLumen does not request
  `INTERNET`. Findings that require attacker-controlled HTTP input
  cannot reach us.
- **Code path exposure**: many transitive deps include features we
  don't use. The SBOM is a complete dependency list; the *actual*
  reachable surface is smaller.
- **Data sensitivity**: OpenLumen stores user-entered coordinates and
  preset preferences. No credentials, no health data, no PII.

## Why not GitHub Dependency Graph + Dependabot alerts?

OpenLumen does not rely on repository automation for release controls.
The local release gate produces the SBOM and advisory report from the
same release classpath that builds the APK, which is the artifact a
downstream packager needs when they ask "what's actually in this APK."

## Future work

- Switch to fail-on-high-or-critical once the triage workflow is
  established and the noise floor is understood.
- Attach the SBOM and advisory report to each GitHub Release alongside
  the signed APK and SHA-256 sums.
- Cross-reference with the threat model. The SBOM is the data; the
  threat model in `docs/threat-model.md` is the interpretation. They
  should stay coherent.
