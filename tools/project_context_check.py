"""Validate machine-checked claims in the local project context document."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


BUILD_VERSION_RE = re.compile(r"(?m)^\s*versionName\s*=\s*\"([^\"]+)\"\s*$")
SCHEMA_VERSION_RE = re.compile(
    r"(?m)^\s*const\s+val\s+CURRENT_SCHEMA_VERSION:\s*Int\s*=\s*(\d+)\s*$"
)
CONTEXT_VERSION_RE = re.compile(
    r"(?m)^\s*-\s*\*\*Current main version\*\*:\s*`v([^`]+)`"
)
CONTEXT_SCHEMA_RE = re.compile(
    r"(?m)^\s*-\s*\*\*Current preference schema\*\*:\s*`(\d+)`"
)
DRIVER_SOURCE = Path(
    "core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt"
)


def validate_project_context(root: Path) -> list[str]:
    """Return consistency errors; an absent local-only context is optional."""

    context_path = root / "PROJECT_CONTEXT.md"
    if not context_path.exists():
        return []

    context = context_path.read_text(encoding="utf-8")
    normalized_context = re.sub(r"\s+", " ", context)
    build = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    preferences = (
        root
        / "core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt"
    ).read_text(encoding="utf-8")
    driver_source = (root / DRIVER_SOURCE).read_text(encoding="utf-8")
    errors: list[str] = []

    build_version = _first_group(BUILD_VERSION_RE, build)
    context_version = _first_group(CONTEXT_VERSION_RE, context)
    if build_version is None:
        errors.append("app/build.gradle.kts has no versionName")
    elif context_version != build_version:
        errors.append(
            f"context main version {context_version!r} != build version {build_version!r}"
        )

    schema_version = _first_group(SCHEMA_VERSION_RE, preferences)
    context_schema = _first_group(CONTEXT_SCHEMA_RE, context)
    if schema_version is None:
        errors.append("Preferences.kt has no CURRENT_SCHEMA_VERSION")
    elif context_schema != schema_version:
        errors.append(
            f"context schema {context_schema!r} != Preferences schema {schema_version!r}"
        )

    required_context_contracts = (
        "**Auto driver order**: root drivers → `ColorDisplayManager` → `Overlay`",
        "**Auto no-driver behavior**: when no probed driver is available, Auto selects no driver and the service/Driver report surfaces unavailable state.",
    )
    for contract in required_context_contracts:
        if contract not in normalized_context:
            errors.append(f"context is missing current driver contract: {contract}")

    required_source_markers = (
        "fun bestAvailableKind(probes: List<Probe>): EngineKind?",
        "fun pickBestFrom(probes: List<Probe>): ColorEngine?",
        "it.available && it.engine.kind.requiresRoot",
        "it.available && it.engine.kind == EngineKind.COLOR_DISPLAY_MANAGER",
        "it.available && it.engine.kind == EngineKind.OVERLAY",
    )
    source_positions = []
    for marker in required_source_markers:
        position = driver_source.find(marker)
        if position < 0:
            errors.append(f"DriverProbe.kt is missing contract marker: {marker}")
        source_positions.append(position)
    if all(position >= 0 for position in source_positions[2:]):
        if source_positions[2:] != sorted(source_positions[2:]):
            errors.append("DriverProbe Auto resolution order no longer matches context")

    return errors


def _first_group(pattern: re.Pattern[str], text: str) -> str | None:
    match = pattern.search(text)
    return match.group(1) if match else None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Repository root to inspect.")
    args = parser.parse_args(argv)
    errors = validate_project_context(Path(args.root).resolve())
    if errors:
        print("PROJECT_CONTEXT consistency check failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("PROJECT_CONTEXT consistency check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
