#!/usr/bin/env python3
"""Review OpenLumen version-catalog updates without a Gradle plugin."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


REPOSITORIES = (
    ("google", "https://dl.google.com/dl/android/maven2"),
    ("mavenCentral", "https://repo.maven.apache.org/maven2"),
    ("gradlePluginPortal", "https://plugins.gradle.org/m2"),
)
UNSTABLE_RE = re.compile(
    r"(?i)(?:^|[.\-_+])(?:alpha|a|beta|b|rc|cr|m|milestone|preview|dev|eap|snapshot)(?:\d+)?(?:$|[.\-_+])"
)


@dataclass(frozen=True)
class Coordinate:
    group: str
    artifact: str
    source: str

    @property
    def label(self) -> str:
        return f"{self.group}:{self.artifact}"


@dataclass(frozen=True)
class VersionRef:
    name: str
    current: str
    coordinates: tuple[Coordinate, ...]


@dataclass(frozen=True)
class Candidate:
    version: str
    repository: str
    coordinate: Coordinate


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Review stable updates for gradle/libs.versions.toml.")
    parser.add_argument(
        "--catalog",
        default="gradle/libs.versions.toml",
        help="Version catalog to inspect.",
    )
    parser.add_argument(
        "--include-pre-releases",
        action="store_true",
        help="Include alpha/beta/rc/snapshot metadata candidates.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=20,
        help="Network timeout per Maven metadata request.",
    )
    parser.add_argument(
        "--output-json",
        help="Optional path for a machine-readable review report.",
    )
    parser.add_argument(
        "--fail-on-updates",
        action="store_true",
        help="Exit with code 2 when stable updates are available.",
    )
    args = parser.parse_args(argv)
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")

    root = Path(__file__).resolve().parents[1]
    catalog_path = (root / args.catalog).resolve()
    report = review_catalog(catalog_path, args.include_pre_releases, args.timeout_seconds)
    print_report(report)

    if args.output_json:
        output_path = (root / args.output_json).resolve()
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    if args.fail_on_updates and report["updates"]:
        return 2
    return 0


def review_catalog(catalog_path: Path, include_pre_releases: bool, timeout_seconds: int) -> dict[str, object]:
    refs = parse_version_catalog(catalog_path)
    entries = []
    for ref in refs:
        candidates, errors = collect_candidates(ref, include_pre_releases, timeout_seconds)
        latest = max(candidates, key=lambda candidate: version_sort_key(candidate.version), default=None)
        status = classify_status(ref.current, latest, errors)
        entries.append(
            {
                "name": ref.name,
                "current": ref.current,
                "latest": latest.version if latest else None,
                "latest_repository": latest.repository if latest else None,
                "latest_coordinate": latest.coordinate.label if latest else None,
                "status": status,
                "coordinates": sorted({coordinate.label for coordinate in ref.coordinates}),
                "errors": errors,
            }
        )

    updates = [entry for entry in entries if entry["status"] == "update-available"]
    current = [entry for entry in entries if entry["status"] == "current"]
    unresolved = [entry for entry in entries if entry["status"] in {"unresolved", "pre-release-only"}]
    return {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "catalog": str(catalog_path),
        "include_pre_releases": include_pre_releases,
        "checked": len(entries),
        "updates": updates,
        "current": current,
        "unresolved": unresolved,
    }


def parse_version_catalog(catalog_path: Path) -> list[VersionRef]:
    data = tomllib.loads(catalog_path.read_text(encoding="utf-8"))
    versions = data.get("versions", {})
    coordinates_by_ref: dict[str, set[Coordinate]] = {name: set() for name in versions}

    for alias, library in data.get("libraries", {}).items():
        version_ref = find_version_ref(library)
        module = library.get("module")
        if not version_ref or not module:
            continue
        group, artifact = module.split(":", 1)
        coordinates_by_ref.setdefault(version_ref, set()).add(Coordinate(group, artifact, f"library:{alias}"))

    for alias, plugin in data.get("plugins", {}).items():
        version_ref = find_version_ref(plugin)
        plugin_id = plugin.get("id")
        if not version_ref or not plugin_id:
            continue
        coordinates_by_ref.setdefault(version_ref, set()).add(
            Coordinate(plugin_id, f"{plugin_id}.gradle.plugin", f"plugin:{alias}")
        )

    refs = []
    for name, current in versions.items():
        coordinates = tuple(sorted(coordinates_by_ref.get(name, set()), key=lambda coord: coord.label))
        if coordinates:
            refs.append(VersionRef(name, str(current), coordinates))
    return sorted(refs, key=lambda ref: ref.name)


def find_version_ref(entry: dict[str, object]) -> str | None:
    version = entry.get("version")
    if isinstance(version, dict):
        ref = version.get("ref")
        return str(ref) if ref else None
    return None


def collect_candidates(
    version_ref: VersionRef,
    include_pre_releases: bool,
    timeout_seconds: int,
) -> tuple[list[Candidate], list[str]]:
    candidates: list[Candidate] = []
    errors: list[str] = []
    for coordinate in version_ref.coordinates:
        for repository, base_url in REPOSITORIES:
            try:
                versions = fetch_metadata_versions(base_url, coordinate, timeout_seconds)
            except urllib.error.HTTPError as exc:
                if exc.code != 404:
                    errors.append(f"{repository} {coordinate.label}: HTTP {exc.code}")
                continue
            except (OSError, urllib.error.URLError, ET.ParseError, TimeoutError) as exc:
                errors.append(f"{repository} {coordinate.label}: {exc}")
                continue
            for version in versions:
                if include_pre_releases or is_stable_version(version):
                    candidates.append(Candidate(version, repository, coordinate))
    return dedupe_candidates(candidates), errors


def fetch_metadata_versions(base_url: str, coordinate: Coordinate, timeout_seconds: int) -> list[str]:
    url = metadata_url(base_url, coordinate)
    request = urllib.request.Request(url, headers={"User-Agent": "OpenLumen dependency update review"})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        document = ET.fromstring(response.read())
    return [node.text.strip() for node in document.findall("./versioning/versions/version") if node.text]


def metadata_url(base_url: str, coordinate: Coordinate) -> str:
    group_path = coordinate.group.replace(".", "/")
    return f"{base_url.rstrip('/')}/{group_path}/{coordinate.artifact}/maven-metadata.xml"


def dedupe_candidates(candidates: Iterable[Candidate]) -> list[Candidate]:
    by_key: dict[tuple[str, str], Candidate] = {}
    for candidate in candidates:
        key = (candidate.coordinate.label, candidate.version)
        by_key.setdefault(key, candidate)
    return sorted(by_key.values(), key=lambda candidate: (candidate.coordinate.label, version_sort_key(candidate.version)))


def classify_status(current: str, latest: Candidate | None, errors: list[str]) -> str:
    if latest is None:
        return "unresolved" if errors else "pre-release-only"
    if version_sort_key(latest.version) > version_sort_key(current):
        return "update-available"
    return "current"


def is_stable_version(version: str) -> bool:
    return not UNSTABLE_RE.search(version)


def version_sort_key(version: str) -> tuple[tuple[int, object], ...]:
    tokens = re.findall(r"\d+|[A-Za-z]+", version)
    key: list[tuple[int, object]] = []
    for token in tokens:
        if token.isdigit():
            key.append((1, int(token)))
        else:
            key.append((0, token.lower()))
    return tuple(key)


def print_report(report: dict[str, object]) -> None:
    print("OpenLumen dependency update review")
    print(f"Catalog: {report['catalog']}")
    print(f"Version refs checked: {report['checked']}")

    updates = report["updates"]
    if updates:
        print("\nStable updates available:")
        for entry in updates:
            print(
                f"- {entry['name']}: {entry['current']} -> {entry['latest']} "
                f"({entry['latest_coordinate']} from {entry['latest_repository']})"
            )
    else:
        print("\nStable updates available: none")

    unresolved = report["unresolved"]
    if unresolved:
        print("\nNeeds maintainer review:")
        for entry in unresolved:
            if entry["status"] == "pre-release-only":
                print(f"- {entry['name']}: no stable candidate found; current is {entry['current']}")
            else:
                print(f"- {entry['name']}: metadata unresolved for {', '.join(entry['coordinates'])}")
                for error in entry["errors"]:
                    print(f"  {error}")


if __name__ == "__main__":
    raise SystemExit(main())
