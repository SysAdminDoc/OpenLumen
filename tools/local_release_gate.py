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
        run_gradle_validation(
            root,
            args.allow_unsigned_release,
            args.skip_screenshots,
            args.gradle_timeout_seconds,
        )
        dependency_output = collect_dependencies(root, args.gradle_timeout_seconds)
        dependencies = parse_gradle_dependencies(dependency_output)
        write_text(report_dir / "releaseRuntimeClasspath.txt", dependency_output)
        write_json(report_dir / "sbom.spdx.json", build_spdx(dependencies))
        write_json(report_dir / "advisory-report.json", build_advisory_report(dependencies, args.advisory_mode))
        assert_no_banned_dependencies(dependencies)
        manifest = find_release_manifest(root)
        assert_no_banned_permissions(manifest)
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


def build_spdx(dependencies: Iterable[str]) -> dict[str, object]:
    packages = []
    for coord in dependencies:
        group, name, version = coord.split(":", 2)
        spdx_id = re.sub(r"[^A-Za-z0-9.-]", "-", f"SPDXRef-{group}-{name}-{version}")
        packages.append(
            {
                "SPDXID": spdx_id,
                "name": f"{group}:{name}",
                "versionInfo": version,
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
