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
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


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
        run_health_claim_lint(root)
        run_gradle_validation(
            root,
            args.allow_unsigned_release,
            args.skip_screenshots,
            args.gradle_timeout_seconds,
        )
        dependency_output = collect_dependencies(root, args.gradle_timeout_seconds)
        dependencies = parse_gradle_dependencies(dependency_output)
        write_text(report_dir / "releaseRuntimeClasspath.txt", dependency_output)
        write_json(report_dir / "sbom.spdx.json", build_spdx(dependencies, root=root))
        write_json(report_dir / "advisory-report.json", build_advisory_report(dependencies, args.advisory_mode))
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
            write_json(report_dir / "signature-report.json", verify_signed_apk(root, apk))
    except GateError as exc:
        print(f"release gate failed: {exc}", file=sys.stderr)
        return 1

    print(f"OpenLumen release gate passed. Reports: {report_dir}")
    return 0


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
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "OpenLumen releaseRuntimeClasspath",
        "creationInfo": {
            "created": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "creators": ["Tool: tools/local_release_gate.py"],
        },
        "packages": packages,
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


def build_advisory_report(dependencies: list[str], mode: str) -> dict[str, object]:
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

    queries = []
    for coord in dependencies:
        group, name, version = coord.split(":", 2)
        queries.append({"package": {"ecosystem": "Maven", "name": f"{group}:{name}"}, "version": version})

    vulnerabilities = []
    errors = []
    for start in range(0, len(queries), 100):
        payload = json.dumps({"queries": queries[start : start + 100]}).encode("utf-8")
        request = urllib.request.Request(
            "https://api.osv.dev/v1/querybatch",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                data = json.loads(response.read().decode("utf-8"))
        except (OSError, urllib.error.URLError, TimeoutError) as exc:
            errors.append(str(exc))
            continue

        for query_index, result in enumerate(data.get("results", []), start=start):
            for vuln in result.get("vulns", []) or []:
                vulnerabilities.append(
                    {
                        "dependency": dependencies[query_index],
                        "id": vuln.get("id"),
                        "summary": vuln.get("summary"),
                        "modified": vuln.get("modified"),
                        "aliases": vuln.get("aliases", []),
                    }
                )

    report["vulnerabilities"] = vulnerabilities
    report["status"] = "ok" if not errors else "partial"
    if errors:
        report["errors"] = errors
    return report


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


def verify_signed_apk(root: Path, apk: Path) -> dict[str, object]:
    apksigner = find_apksigner()
    if apksigner is None:
        raise GateError("apksigner was not found in PATH, ANDROID_HOME, or ANDROID_SDK_ROOT")
    try:
        result = run([str(apksigner), "verify", "-v", str(apk)], root, capture=True).stdout
    except subprocess.CalledProcessError as exc:
        raise GateError(f"apksigner verification failed:\n{exc.stdout or ''}") from exc

    required = {
        "v1": "Verified using v1 scheme (JAR signing): true",
        "v2": "Verified using v2 scheme (APK Signature Scheme v2): true",
        "v3": "Verified using v3 scheme (APK Signature Scheme v3): true",
    }
    missing = [scheme for scheme, needle in required.items() if needle not in result]
    if missing:
        raise GateError("release APK is missing signature schemes: " + ", ".join(missing))
    return {"apk": str(apk), "signed": True, "apksigner": str(apksigner), "output": result}


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
