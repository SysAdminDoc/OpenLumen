#!/usr/bin/env python3
"""Local release gate for OpenLumen.

This replaces the old workflow-shaped release checks with one command that can
run on a maintainer workstation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Callable, Iterable

from project_context_check import validate_project_context


BANNED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
}
BANNED_DEPENDENCY_PATTERNS = (
    "com.google.android.gms",
    "com.google.firebase",
    "play-services",
    "firebase",
)
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
DEPENDENCY_RE = re.compile(r"([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([A-Za-z0-9_.+\-]+)")
PERMITTED_SPDX_LICENSES = {
    "Apache-2.0",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "CC0-1.0",
    "EPL-2.0",
    "GPL-3.0-only",
    "GPL-3.0-or-later",
    "ISC",
    "LGPL-2.1-only",
    "LGPL-3.0-only",
    "MIT",
    "MPL-2.0",
    "Zlib",
}
LICENSE_NAME_MAP = {
    "apache 2 0": "Apache-2.0",
    "the apache license version 2 0": "Apache-2.0",
    "the apache software license version 2 0": "Apache-2.0",
    "bsd 2 clause": "BSD-2-Clause",
    "bsd 3 clause": "BSD-3-Clause",
    "cc0 1 0": "CC0-1.0",
    "eclipse public license version 2 0": "EPL-2.0",
    "epl 2 0": "EPL-2.0",
    "gpl 3 0": "GPL-3.0-only",
    "gpl 3 0 or later": "GPL-3.0-or-later",
    "isc license": "ISC",
    "lgpl 2 1": "LGPL-2.1-only",
    "lgpl 3 0": "LGPL-3.0-only",
    "mit": "MIT",
    "mit license": "MIT",
    "mozilla public license version 2 0": "MPL-2.0",
    "mpl 2 0": "MPL-2.0",
    "zlib": "Zlib",
}


OSV_MAX_PAGES = 50
OSV_MAX_ATTEMPTS = 4
OSV_BACKOFF_SECONDS = 1.0
RETRYABLE_STATUS = {429, 500, 502, 503, 504}


class GateError(RuntimeError):
    pass


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the OpenLumen local release gate.")
    parser.add_argument(
        "--allow-unsigned-release",
        action="store_true",
        help="Pass the explicit unsigned-release Gradle override for reproducibility/F-Droid rebuild checks.",
    )
    parser.add_argument(
        "--advisory-mode",
        choices=("query", "offline"),
        default="query",
        help="query uses OSV's public API; offline writes a review scaffold without network.",
    )
    parser.add_argument(
        "--advisory-allowlist",
        default="tools/osv-advisory-allowlist.json",
        help="JSON file of exact, reviewed High/Critical OSV exceptions.",
    )
    parser.add_argument(
        "--report-dir",
        default="build/reports/openlumen-release-gate",
        help="Directory for SBOM, advisory, SHA-256, and signature outputs.",
    )
    parser.add_argument(
        "--skip-screenshots",
        action="store_true",
        help="Skip screenshot lanes for local debugging only. Do not use for release acceptance.",
    )
    parser.add_argument(
        "--gradle-timeout-seconds",
        type=int,
        default=1800,
        help="Maximum runtime for each Gradle command before the gate fails cleanly.",
    )
    args = parser.parse_args(argv)
    if args.gradle_timeout_seconds <= 0:
        parser.error("--gradle-timeout-seconds must be positive")

    root = Path(__file__).resolve().parents[1]
    report_dir = (root / args.report_dir).resolve()
    report_dir.mkdir(parents=True, exist_ok=True)

    try:
        run_project_context_check(root)
        run_health_claim_lint(root)
        run_gradle_validation(
            root,
            args.allow_unsigned_release,
            args.skip_screenshots,
            args.gradle_timeout_seconds,
        )
        dependency_output = collect_dependencies(root, args.gradle_timeout_seconds)
        dependencies = parse_gradle_dependencies(dependency_output)
        assert_dependencies_resolved(dependencies)
        write_text(report_dir / "releaseRuntimeClasspath.txt", dependency_output)
        write_json(report_dir / "sbom.spdx.json", build_spdx(dependencies, root=root))
        advisory_report = build_advisory_report(dependencies, args.advisory_mode)
        write_json(report_dir / "advisory-report.json", advisory_report)
        assert_advisory_policy(
            advisory_report,
            mode=args.advisory_mode,
            allow_unsigned_release=args.allow_unsigned_release,
            allowlist_path=(root / args.advisory_allowlist).resolve(),
        )
        assert_no_banned_dependencies(dependencies)
        manifest = find_release_manifest(root)
        assert_no_banned_permissions(manifest)
        assert_backup_rules(root)
        apk = find_release_apk(root, args.allow_unsigned_release)
        write_sha256(report_dir / "SHA256SUMS", apk)
        if args.allow_unsigned_release:
            write_json(
                report_dir / "signature-report.json",
                {
                    "apk": str(apk),
                    "signed": False,
                    "skipped": "unsigned release output was explicitly requested",
                },
            )
        else:
            write_json(
                report_dir / "signature-report.json",
                verify_signed_apk(root, apk, expected=load_expected_certificate(root)),
            )
        write_json(
            report_dir / "gate-run.json",
            {
                "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "allow_unsigned_release": args.allow_unsigned_release,
                "advisory_mode": args.advisory_mode,
                # Recorded because a run that skipped the screenshot lanes is
                # not a release-acceptance run, and the artifacts alone cannot
                # otherwise be told apart.
                "skip_screenshots": args.skip_screenshots,
                "dependency_count": len(dependencies),
            },
        )
        assert_reports_are_populated(report_dir)
    except GateError as exc:
        print(f"release gate failed: {exc}", file=sys.stderr)
        return 1

    print(f"OpenLumen release gate passed. Reports: {report_dir}")
    return 0


REQUIRED_REPORTS = (
    "releaseRuntimeClasspath.txt",
    "sbom.spdx.json",
    "advisory-report.json",
    "SHA256SUMS",
    "signature-report.json",
    "gate-run.json",
)

CERTIFICATE_PIN = "tools/release-signing-certificate.json"


def assert_dependencies_resolved(dependencies: list[str]) -> None:
    """A gate that checked nothing must not report success.

    An empty resolution is silent everywhere downstream: the SBOM has no
    packages, the OSV loop never executes, and the advisory status is "ok".
    A release build always has dependencies, so zero means the report failed
    to parse rather than that the app has none.
    """
    if not dependencies:
        raise GateError(
            "releaseRuntimeClasspath resolved to zero dependencies, so the SBOM and "
            "advisory scan would both have checked nothing"
        )


def assert_reports_are_populated(report_dir: Path) -> None:
    """Every artifact the gate promises has to exist and hold something."""
    missing = []
    for name in REQUIRED_REPORTS:
        path = report_dir / name
        if not path.is_file():
            missing.append(f"{name} (not written)")
        elif path.stat().st_size == 0:
            missing.append(f"{name} (empty)")
    if missing:
        raise GateError("release gate reports are incomplete: " + ", ".join(missing))


def load_expected_certificate(root: Path) -> str | None:
    """The signing certificate this project's releases are expected to carry.

    Returns None only when the pin file is absent, which the signed path
    treats as a failure: an APK signed with the wrong key verifies perfectly
    well, so "apksigner said it is signed" is not the same as "signed by us".
    """
    path = root / CERTIFICATE_PIN
    if not path.is_file():
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"signing certificate pin could not be read: {path}: {exc}") from exc
    fingerprint = raw.get("sha256") if isinstance(raw, dict) else None
    if not isinstance(fingerprint, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", fingerprint.replace(":", "")):
        raise GateError(f"{CERTIFICATE_PIN} must hold a 64-character hex sha256 field")
    return fingerprint.replace(":", "").lower()


def apksigner_certificate_sha256(apksigner_output: str) -> str | None:
    match = re.search(
        r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})",
        apksigner_output,
    )
    return match.group(1).lower() if match else None


def gradle_executable(root: Path) -> Path:
    return root / ("gradlew.bat" if os.name == "nt" else "gradlew")


def run(
    cmd: list[str],
    root: Path,
    capture: bool = False,
    timeout_seconds: int | None = None,
) -> subprocess.CompletedProcess[str]:
    print("+ " + " ".join(cmd))
    try:
        return subprocess.run(
            cmd,
            cwd=root,
            check=True,
            text=True,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.STDOUT if capture else None,
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as exc:
        raise GateError(f"command timed out after {timeout_seconds} seconds: {' '.join(cmd)}") from exc


def run_gradle_validation(
    root: Path,
    allow_unsigned_release: bool,
    skip_screenshots: bool,
    timeout_seconds: int,
) -> None:
    tasks = [
        ":app:assembleDebug",
        "testDebugUnitTest",
        ":app:lint",
        ":app:assembleRelease",
    ]
    if not skip_screenshots:
        tasks.extend(
            [
                ":app:validateDebugScreenshotTest",
                ":app:verifyRoborazziDebug",
            ]
        )

    cmd = [
        str(gradle_executable(root)),
        "--dependency-verification=strict",
        "--no-configuration-cache",
        *tasks,
    ]
    if allow_unsigned_release:
        cmd.append("-Popenlumen.allowUnsignedRelease=true")
    try:
        run(cmd, root, timeout_seconds=timeout_seconds)
    except subprocess.CalledProcessError as exc:
        raise GateError(f"Gradle validation failed with exit code {exc.returncode}") from exc


def run_health_claim_lint(root: Path) -> None:
    cmd = [sys.executable, str(root / "tools" / "health_claim_lint.py")]
    try:
        run(cmd, root)
    except subprocess.CalledProcessError as exc:
        raise GateError(f"health-claim lint failed with exit code {exc.returncode}") from exc


def run_project_context_check(root: Path) -> None:
    errors = validate_project_context(root)
    if errors:
        raise GateError("PROJECT_CONTEXT consistency check failed: " + "; ".join(errors))


def collect_dependencies(root: Path, timeout_seconds: int) -> str:
    cmd = [
        str(gradle_executable(root)),
        "--dependency-verification=strict",
        ":app:dependencies",
        "--configuration",
        "releaseRuntimeClasspath",
    ]
    try:
        return run(cmd, root, capture=True, timeout_seconds=timeout_seconds).stdout
    except subprocess.CalledProcessError as exc:
        output = exc.stdout or ""
        raise GateError(f"releaseRuntimeClasspath dependency report failed:\n{output}") from exc


def parse_gradle_dependencies(output: str) -> list[str]:
    coordinates: set[str] = set()
    for line in output.splitlines():
        if "project :" in line:
            continue
        match = DEPENDENCY_RE.search(line)
        if not match:
            continue
        group, name, version = match.groups()
        arrow = re.search(r"->\s*([A-Za-z0-9_.+\-]+)", line)
        if arrow:
            version = arrow.group(1)
        coordinates.add(f"{group}:{name}:{version}")
    return sorted(coordinates)


def build_spdx(
    dependencies: Iterable[str],
    root: Path | None = None,
    metadata: dict[str, dict[str, str]] | None = None,
) -> dict[str, object]:
    root = root or Path(__file__).resolve().parents[1]
    overrides = load_license_overrides(root)
    packages = []
    for coord in dependencies:
        group, name, version = coord.split(":", 2)
        package_metadata = (
            metadata[coord]
            if metadata is not None and coord in metadata
            else resolve_dependency_metadata(coord, root, overrides)
        )
        validate_package_metadata(coord, package_metadata)
        spdx_id = re.sub(r"[^A-Za-z0-9.-]", "-", f"SPDXRef-{group}-{name}-{version}")
        packages.append(
            {
                "SPDXID": spdx_id,
                "name": f"{group}:{name}",
                "versionInfo": version,
                "downloadLocation": package_metadata["source"],
                "licenseConcluded": package_metadata["license"],
                "licenseDeclared": package_metadata["license"],
                "copyrightText": "NOASSERTION",
                "packageComment": package_metadata.get("reason", "License and provenance read from the resolved Maven POM."),
                "externalRefs": [
                    {
                        "referenceCategory": "PACKAGE-MANAGER",
                        "referenceType": "purl",
                        "referenceLocator": f"pkg:maven/{group}/{name}@{version}",
                    }
                ],
            }
        )
    created = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "OpenLumen releaseRuntimeClasspath",
        # Required by SPDX 2.3 and has to be unique per document, or two SBOMs
        # cannot be told apart by a consumer.
        "documentNamespace": (
            "https://github.com/SysAdminDoc/OpenLumen/spdx/releaseRuntimeClasspath-"
            + created.replace(":", "").replace("-", "")
        ),
        "creationInfo": {
            "created": created,
            "creators": ["Tool: tools/local_release_gate.py"],
        },
        "packages": packages,
        "relationships": [
            {
                "spdxElementId": "SPDXRef-DOCUMENT",
                "relationshipType": "DESCRIBES",
                "relatedSpdxElement": package["SPDXID"],
            }
            for package in packages
        ],
    }


def load_license_overrides(root: Path) -> dict[str, dict[str, str]]:
    path = root / "tools/sbom-license-overrides.json"
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"SBOM license override file could not be read: {path}: {exc}") from exc
    if not isinstance(raw, dict):
        raise GateError("SBOM license override file must contain an object keyed by exact coordinates")
    overrides: dict[str, dict[str, str]] = {}
    for coord, value in raw.items():
        if not isinstance(coord, str) or not DEPENDENCY_RE.fullmatch(coord):
            raise GateError(f"invalid SBOM license override coordinate: {coord!r}")
        if not isinstance(value, dict):
            raise GateError(f"SBOM license override must be an object: {coord}")
        overrides[coord] = {str(key): str(item) for key, item in value.items()}
    return overrides


def resolve_dependency_metadata(
    coord: str,
    root: Path,
    overrides: dict[str, dict[str, str]],
) -> dict[str, str]:
    override = overrides.get(coord)
    if override is not None:
        return override

    group, name, version = coord.split(":", 2)
    gradle_user_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    module_dir = gradle_user_home / "caches/modules-2/files-2.1" / group / name / version
    poms = sorted(module_dir.rglob("*.pom")) if module_dir.is_dir() else []
    if not poms:
        raise GateError(
            f"no cached Maven POM for {coord}; resolve it before generating the SBOM "
            "or add an exact reviewed entry to tools/sbom-license-overrides.json"
        )

    try:
        pom = ET.parse(poms[0]).getroot()
    except (OSError, ET.ParseError) as exc:
        raise GateError(f"Maven POM could not be read for {coord}: {exc}") from exc

    licenses = []
    license_urls = []
    for element in pom.iter():
        if _xml_local_name(element.tag) != "license":
            continue
        name_text = _xml_child_text(element, "name")
        if not name_text:
            continue
        license_id = normalize_spdx_license(name_text)
        licenses.append(license_id)
        url_text = _xml_child_text(element, "url")
        if url_text:
            license_urls.append(url_text)
    if not licenses:
        raise GateError(
            f"Maven POM for {coord} declares no recognizable license; add an exact reviewed "
            "entry to tools/sbom-license-overrides.json"
        )

    source = ""
    for element in pom.iter():
        if _xml_local_name(element.tag) == "scm":
            source = _xml_child_text(element, "url")
            if source:
                break
    if not source:
        for element in pom:
            if _xml_local_name(element.tag) == "url":
                source = (element.text or "").strip()
                if source:
                    break
    if not source:
        raise GateError(
            f"Maven POM for {coord} has no source URL; add an exact reviewed entry to "
            "tools/sbom-license-overrides.json"
        )

    metadata_url = maven_pom_url(group, name, version)
    return {
        "license": " OR ".join(dict.fromkeys(licenses)),
        "source": source,
        "metadata_url": metadata_url,
        "license_url": license_urls[0] if license_urls else "",
        "reason": f"License and provenance read from {metadata_url}.",
    }


def normalize_spdx_license(name: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", " ", name.lower()).strip()
    license_id = LICENSE_NAME_MAP.get(normalized)
    if license_id is None:
        raise GateError(f"unrecognized Maven license name: {name}")
    return license_id


def validate_package_metadata(coord: str, metadata: dict[str, str]) -> None:
    license_expression = metadata.get("license", "")
    source = metadata.get("source", "")
    reason = metadata.get("reason", "")
    license_ids = [part.strip() for part in license_expression.split(" OR ") if part.strip()]
    if not license_ids or any(item not in PERMITTED_SPDX_LICENSES for item in license_ids):
        raise GateError(f"SBOM license is unknown or prohibited for {coord}: {license_expression!r}")
    if not source.startswith(("https://", "http://")):
        raise GateError(f"SBOM provenance source is missing or invalid for {coord}: {source!r}")
    if not reason.strip():
        raise GateError(f"SBOM license/provenance metadata has no review reason for {coord}")


def maven_pom_url(group: str, name: str, version: str) -> str:
    repository = "https://dl.google.com/dl/android/maven2" if group.startswith("androidx.") else "https://repo1.maven.org/maven2"
    path = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.pom"
    return f"{repository}/{path}"


def _xml_local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _xml_child_text(element: ET.Element, name: str) -> str:
    for child in element:
        if _xml_local_name(child.tag) == name:
            return (child.text or "").strip()
    return ""


def build_advisory_report(
    dependencies: list[str],
    mode: str,
    fetch: "Callable[[dict[str, object]], dict[str, object]] | None" = None,
) -> dict[str, object]:
    report: dict[str, object] = {
        "mode": mode,
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "dependency_count": len(dependencies),
        "vulnerabilities": [],
    }
    if mode == "offline":
        report["status"] = "offline-review-required"
        report["note"] = "OSV was not queried; use this SBOM as the advisory review input."
        return report

    vulnerabilities: list[dict[str, object]] = []
    errors: list[str] = []
    for coord in dependencies:
        group, name, version = coord.split(":", 2)
        query = {
            "package": {"ecosystem": "Maven", "name": f"{group}:{name}"},
            "version": version,
        }
        try:
            for vuln in osv_query_all_pages(query, fetch=fetch):
                severity, severity_detail = advisory_severity(vuln)
                vulnerabilities.append(
                    {
                        "dependency": coord,
                        "id": vuln.get("id"),
                        "summary": vuln.get("summary"),
                        "modified": vuln.get("modified"),
                        "aliases": vuln.get("aliases", []),
                        "severity": severity,
                        "severity_detail": severity_detail,
                    }
                )
        except GateError as exc:
            errors.append(f"{coord}: {exc}")

    report["vulnerabilities"] = vulnerabilities
    report["status"] = "ok" if not errors else "partial"
    if errors:
        report["errors"] = errors
    return report


def osv_query_all_pages(
    query: dict[str, object],
    fetch: "Callable[[dict[str, object]], dict[str, object]] | None" = None,
) -> list[dict[str, object]]:
    """Every vulnerability OSV holds for one package, across every page.

    `/v1/query` returns whole vulnerability objects, unlike `/v1/querybatch`
    which returns only `{id, modified}`. The batch endpoint is cheaper, but the
    severity tiering built on top of it could never fire: every advisory came
    back UNKNOWN because the field was simply not in the response.
    """
    fetch = fetch or osv_post_query
    collected: list[dict[str, object]] = []
    page = dict(query)
    for _ in range(OSV_MAX_PAGES):
        data = fetch(page)
        raw = data.get("vulns", []) or []
        if not isinstance(raw, list):
            raise GateError("OSV returned a malformed vulnerability list")
        for vuln in raw:
            if not isinstance(vuln, dict):
                raise GateError("OSV returned a malformed vulnerability")
            collected.append(vuln)
        token = data.get("next_page_token")
        if not token:
            return collected
        page = dict(query)
        page["page_token"] = token
    raise GateError(f"OSV paging did not terminate within {OSV_MAX_PAGES} pages")


def osv_post_query(query: dict[str, object]) -> dict[str, object]:
    """POST one query, retrying while OSV is rate limiting us.

    A 429 used to be indistinguishable from a clean empty result, which turned
    a throttled scan into a silent pass.
    """
    payload = json.dumps(query).encode("utf-8")
    last_error = "unknown"
    for attempt in range(OSV_MAX_ATTEMPTS):
        request = urllib.request.Request(
            "https://api.osv.dev/v1/query",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_error = f"HTTP {exc.code}"
            if exc.code not in RETRYABLE_STATUS:
                raise GateError(last_error) from exc
        except (OSError, urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last_error = str(exc)
        if attempt + 1 < OSV_MAX_ATTEMPTS:
            time.sleep(OSV_BACKOFF_SECONDS * (2**attempt))
    raise GateError(f"OSV query failed after {OSV_MAX_ATTEMPTS} attempts: {last_error}")


def advisory_severity(vulnerability: dict[str, object]) -> tuple[str, str]:
    """Extract a normalized severity without treating missing metadata as safe."""
    candidates: list[str] = []
    for container_key in ("database_specific", "ecosystem_specific"):
        container = vulnerability.get(container_key)
        if isinstance(container, dict):
            value = container.get("severity")
            if isinstance(value, str):
                candidates.append(value)
    severity_entries = vulnerability.get("severity")
    if isinstance(severity_entries, list):
        for entry in severity_entries:
            if isinstance(entry, dict):
                score = entry.get("score")
                if isinstance(score, (int, float)):
                    return cvss_score_severity(float(score)), str(score)
                if isinstance(score, str) and re.fullmatch(r"\d+(?:\.\d+)?", score):
                    return cvss_score_severity(float(score)), score
                if isinstance(score, str):
                    candidates.append(score)

    for candidate in candidates:
        normalized = candidate.strip().upper()
        if normalized in {"CRITICAL", "HIGH", "MEDIUM", "LOW"}:
            return normalized, candidate
    return "UNKNOWN", "; ".join(candidates) or "missing"


def cvss_score_severity(score: float) -> str:
    if score >= 9.0:
        return "CRITICAL"
    if score >= 7.0:
        return "HIGH"
    if score >= 4.0:
        return "MEDIUM"
    return "LOW"


def load_advisory_allowlist(path: Path, today: date | None = None) -> list[dict[str, str]]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError(f"OSV advisory allowlist could not be read: {path}: {exc}") from exc
    if not isinstance(raw, list):
        raise GateError("OSV advisory allowlist must be a JSON array")

    entries: list[dict[str, str]] = []
    for index, raw_entry in enumerate(raw):
        if not isinstance(raw_entry, dict):
            raise GateError(f"OSV advisory allowlist entry {index} must be an object")
        required = ("advisory_id", "dependency", "reason", "reviewer", "expires")
        missing = [key for key in required if not str(raw_entry.get(key, "")).strip()]
        if missing:
            raise GateError(
                f"OSV advisory allowlist entry {index} is missing: {', '.join(missing)}"
            )
        dependency = str(raw_entry["dependency"])
        if not DEPENDENCY_RE.fullmatch(dependency):
            raise GateError(f"OSV advisory allowlist entry {index} has invalid dependency: {dependency}")
        try:
            expiry = datetime.strptime(str(raw_entry["expires"]), "%Y-%m-%d").date()
        except ValueError as exc:
            raise GateError(
                f"OSV advisory allowlist entry {index} expiry must be YYYY-MM-DD"
            ) from exc
        if expiry < (today or date.today()):
            raise GateError(
                f"OSV advisory allowlist entry {index} expired on {expiry.isoformat()}"
            )
        entries.append({key: str(raw_entry[key]).strip() for key in required})
    return entries


def assert_advisory_policy(
    report: dict[str, object],
    mode: str,
    allow_unsigned_release: bool,
    allowlist_path: Path,
    today: date | None = None,
) -> None:
    if mode == "offline":
        if not allow_unsigned_release:
            raise GateError(
                "offline advisory mode is allowed only with --allow-unsigned-release; "
                "a signed release must query OSV"
            )
        return

    if report.get("status") != "ok":
        raise GateError(
            "OSV advisory query was incomplete; signed/query-mode releases require a complete response"
        )

    # The loader does the expiry check, against the same date, so an injected
    # clock reaches every comparison. It used to read date.today() directly,
    # which made the tests below pass or fail on the wall clock no matter what
    # they injected.
    entries = load_advisory_allowlist(allowlist_path, today=today)

    vulnerabilities = report.get("vulnerabilities", [])
    if not isinstance(vulnerabilities, list):
        raise GateError("OSV advisory report has an invalid vulnerabilities list")
    for vulnerability in vulnerabilities:
        if not isinstance(vulnerability, dict):
            raise GateError("OSV advisory report contains a malformed vulnerability")
        severity = vulnerability.get("severity")
        if severity == "UNKNOWN":
            raise GateError(
                f"OSV advisory {vulnerability.get('id')} has no usable severity metadata"
            )
        if severity not in {"HIGH", "CRITICAL"}:
            continue
        advisory_ids = {str(vulnerability.get("id", ""))}
        advisory_ids.update(str(alias) for alias in vulnerability.get("aliases", []) or [])
        dependency = str(vulnerability.get("dependency", ""))
        matching = next(
            (
                entry
                for entry in entries
                if entry["advisory_id"] in advisory_ids and entry["dependency"] == dependency
            ),
            None,
        )
        if matching is None:
            raise GateError(
                f"{severity} OSV advisory {vulnerability.get('id')} affects {dependency}; "
                "add an exact, unexpired reviewed allowlist entry or update the dependency"
            )


def assert_no_banned_dependencies(dependencies: Iterable[str]) -> None:
    hits = [
        dep
        for dep in dependencies
        if any(pattern in dep.lower() for pattern in BANNED_DEPENDENCY_PATTERNS)
    ]
    if hits:
        raise GateError("banned Google/Firebase dependency found: " + ", ".join(hits))


def find_release_manifest(root: Path) -> Path:
    patterns = [
        "app/build/intermediates/merged_manifests/release/**/AndroidManifest.xml",
        "app/build/intermediates/packaged_manifests/release/**/AndroidManifest.xml",
        "app/build/intermediates/merged_manifest/release/**/AndroidManifest.xml",
    ]
    candidates = [path for pattern in patterns for path in root.glob(pattern)]
    if not candidates:
        raise GateError("release merged manifest was not found after assembleRelease")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def assert_no_banned_permissions(manifest: Path) -> None:
    root = ET.parse(manifest).getroot()
    found = set()
    for child in root:
        if child.tag not in {"uses-permission", "uses-permission-sdk-23"}:
            continue
        name = child.attrib.get(f"{ANDROID_NS}name")
        if name in BANNED_PERMISSIONS:
            found.add(name)
    if found:
        raise GateError(f"banned release manifest permissions in {manifest}: {', '.join(sorted(found))}")


def assert_backup_rules(root: Path) -> None:
    """Require encryption for every cloud path containing user coordinates."""
    data_rules_path = root / "app/src/main/res/xml/data_extraction_rules.xml"
    legacy_rules_path = root / "app/src/main/res/xml/backup_rules.xml"
    try:
        data_rules = ET.parse(data_rules_path).getroot()
        legacy_rules = ET.parse(legacy_rules_path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise GateError(f"backup-rule parse failed: {exc}") from exc

    cloud = data_rules.find("cloud-backup")
    device_transfer = data_rules.find("device-transfer")
    if cloud is None or device_transfer is None:
        raise GateError("data extraction rules must define cloud-backup and device-transfer")

    def includes_datastore(parent: ET.Element) -> list[ET.Element]:
        return [
            child
            for child in parent.findall("include")
            if child.attrib.get("domain") == "file"
            and child.attrib.get("path") in {"datastore", "datastore/"}
        ]

    cloud_datastore = includes_datastore(cloud)
    if len(cloud_datastore) != 1 or cloud_datastore[0].attrib.get("requireFlags") != "clientSideEncryption":
        raise GateError(
            "cloud backup must require clientSideEncryption for the coordinate-bearing datastore"
        )

    transfer_datastore = includes_datastore(device_transfer)
    if len(transfer_datastore) != 1 or transfer_datastore[0].attrib.get("requireFlags"):
        raise GateError("device-transfer must include the datastore without a cloud encryption flag")

    legacy_datastore = includes_datastore(legacy_rules)
    if len(legacy_datastore) != 1 or legacy_datastore[0].attrib.get("requireFlags") != "clientSideEncryption":
        raise GateError(
            "legacy Auto Backup must require clientSideEncryption for the coordinate-bearing datastore"
        )


def find_release_apk(root: Path, allow_unsigned_release: bool) -> Path:
    release_dir = root / "app/build/outputs/apk/release"
    signed = release_dir / "app-release.apk"
    unsigned = release_dir / "app-release-unsigned.apk"
    preferred = unsigned if allow_unsigned_release else signed
    fallback = signed if allow_unsigned_release else unsigned
    if preferred.exists():
        return preferred
    if fallback.exists():
        return fallback
    raise GateError("release APK was not found after assembleRelease")


def write_sha256(path: Path, apk: Path) -> None:
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    path.write_text(f"{digest}  {apk.name}\n", encoding="utf-8")


def verify_signed_apk(root: Path, apk: Path, expected: str | None = None) -> dict[str, object]:
    apksigner = find_apksigner()
    if apksigner is None:
        raise GateError("apksigner was not found in PATH, ANDROID_HOME, or ANDROID_SDK_ROOT")
    try:
        result = run(
            [str(apksigner), "verify", "--print-certs", "-v", str(apk)],
            root,
            capture=True,
        ).stdout
    except subprocess.CalledProcessError as exc:
        raise GateError(f"apksigner verification failed:\n{exc.stdout or ''}") from exc

    missing = missing_required_signature_schemes(result)
    if missing:
        raise GateError("release APK is missing signature schemes: " + ", ".join(missing))

    observed = apksigner_certificate_sha256(result)
    if observed is None:
        raise GateError("apksigner did not report a signer certificate SHA-256 digest")
    if expected is None:
        raise GateError(
            "no signing certificate is pinned, so this gate cannot tell our key from any "
            f"other. Record the expected fingerprint in {CERTIFICATE_PIN} as "
            f'{{"sha256": "{observed}"}} once you have confirmed it is the release key.'
        )
    if observed != expected:
        raise GateError(
            f"release APK is signed with an unexpected certificate: {observed}, "
            f"expected {expected}"
        )
    return {
        "apk": str(apk),
        "signed": True,
        "apksigner": str(apksigner),
        "certificate_sha256": observed,
        "output": result,
    }


def missing_required_signature_schemes(apksigner_output: str) -> list[str]:
    required = {
        "v2": "Verified using v2 scheme (APK Signature Scheme v2): true",
    }
    return [scheme for scheme, needle in required.items() if needle not in apksigner_output]


def find_apksigner() -> Path | None:
    path_name = "apksigner.bat" if os.name == "nt" else "apksigner"
    for env_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env_name)
        if not sdk:
            continue
        candidates = sorted(Path(sdk).glob(f"build-tools/*/{path_name}"), reverse=True)
        if candidates:
            return candidates[0]
    for entry in os.environ.get("PATH", "").split(os.pathsep):
        candidate = Path(entry) / path_name
        if candidate.exists():
            return candidate
    return None


def write_json(path: Path, data: object) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
