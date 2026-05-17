# SBOM and Advisory Scan

> Tied to roadmap candidate **C94** (SBOM and advisory scan). The CI
> workflow lives at `.github/workflows/sbom.yml`.

## What we produce

For every release and weekly on Monday:

1. **SPDX-JSON SBOM** of the release classpath. Captures every
   transitive dependency the production APK is built against. Uploaded
   as a GitHub Actions artifact (`openlumen-sbom-<run-id>`) with a
   30-day retention.
2. **Advisory scan** of the SBOM against the Anchore vulnerability
   feeds (NVD / GitHub Advisory / OS distros). Output is uploaded as a
   second artifact (`openlumen-advisory-report-<run-id>`).

We do **not** fail CI on the advisory scan today. Anchore's `medium`
severity floor surfaces a steady drumbeat of low-priority noise
(protobuf-java CVE-2024-7254 was a recent example) where the
*announced* severity is medium but the *practical* exposure for an
offline display-tint app is zero. Auto-failing builds on those would
push maintainers to ignore the report, which is worse than reading it.

## Triage workflow

After each scan:

1. Download the advisory report artifact.
2. For each High / Critical, open a tracking issue with the
   `security` label, link the advisory, and either fix or document why
   we're not exposed.
3. For each Medium, add to the maintainer's quarterly review list. We
   read the list at release planning time; do not block on it.
4. Low findings are noted in the report only — no individual issue
   filing unless the maintainer flags one specifically.

A finding that has been triaged and accepted (e.g., "we don't use the
deserialization code path that has the CVE") gets a short note in
this document under "Accepted exposures" so the next planning pass
doesn't re-triage from scratch.

## Accepted exposures

| CVE / GHSA | Affected dependency | Why we accept | Recorded |
|---|---|---|---|
| CVE-2024-7254 (GHSA-735f-pc8j-v9w8) | `com.google.protobuf:protobuf-java:3.21.x` (transitive) | OpenLumen has no INTERNET permission and does not deserialize attacker-controlled protobuf input. The CVE describes a stack-overflow path triggered when deserializing maliciously-nested messages; the code path is not reachable from any OpenLumen call site. Tracked at https://github.com/advisories/GHSA-735f-pc8j-v9w8 (S77 in ROADMAP). Re-evaluate if the artifact is replaced or if a new transitive surfaces a different protobuf path. | 2026-05-17 (rev 4 of ROADMAP) |

## How to read an advisory report

The artifact is a SARIF-format file. Open it in any SARIF viewer or
read it as JSON. Each finding has:

- `ruleId`: the CVE or GHSA identifier
- `level`: severity (note / warning / error / etc.)
- `message.text`: short description
- `locations[].physicalLocation.artifactLocation.uri`: the dependency
  coordinate (e.g., `com.google.protobuf:protobuf-java:3.21.7`)

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

## Rotation

The SBOM workflow uses `anchore/sbom-action@v0` and
`anchore/scan-action@v6`, pinned to major-version tags so Dependabot
can bump them like any other Action dependency. If either project
changes ownership or its scanning model materially, the workflow file
needs review — there's no automated check that the scanner is still
trustworthy.

Rev 5 update: GitHub Actions starts defaulting JavaScript actions to
Node 24 on 2026-06-02. `anchore/scan-action` has a v7 line and
`actions/attest-build-provenance` has a v4 line; new attestation work
should consider `actions/attest@v4`. ROADMAP C142 tracks the action
major rotation plus the explicit decision between current major tags and
full commit-SHA pinning.

## Why not GitHub Dependency Graph + Dependabot alerts?

We use both. The repository has Dependabot version updates configured
(`.github/dependabot.yml`) for Gradle and GitHub Actions, and Dependabot
security alerts are enabled by default on public repos. The SBOM
workflow exists alongside that because:

- The Dependency Graph view doesn't produce a downloadable SBOM
  artifact for release pipelines.
- Dependabot security alerts cover only GitHub's advisory feed; the
  Anchore scan adds NVD and OS-distro feeds.
- The SBOM artifact is the right thing to hand a downstream packager
  (F-Droid maintainer, distro packager) when they ask "what's actually
  in this APK."

## Future work

- Switch to fail-on-high-or-critical once the triage workflow is
  established and the noise floor is understood. Tracked as a TODO,
  not a roadmap candidate, because it's a tuning decision.
- Publish the SBOM alongside the release tag, not just as a CI
  artifact, so users without GitHub Actions access can audit it.
  Probably attach to the GitHub Release alongside the signed APK.
- Cross-reference with the threat model. The SBOM is the data; the
  threat model in `docs/threat-model.md` is the interpretation. They
  should stay coherent.
