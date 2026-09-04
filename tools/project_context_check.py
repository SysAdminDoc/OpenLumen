"""Validate machine-checked claims in the local project context document."""

from __future__ import annotations

import argparse
import re
import subprocess
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
CONTEXT_RELEASE_RE = re.compile(r"\*\*Latest tagged release\*\*:\s*(v[0-9][^.\s]*(?:\.[0-9]+)*)")
DRIVER_SOURCE = Path(
    "core-engine/src/main/java/com/openlumen/engine/DriverProbe.kt"
)


def validate_project_context(root: Path) -> list[str]:
    """Return consistency errors.

    An absent context used to pass. That made the check fail open in the one
    case it cannot recover from: the release gate calls it to confirm the
    documented contracts still match the code, and with no document there are
    no contracts to confirm.
    """

    context_path = root / "PROJECT_CONTEXT.md"
    if not context_path.exists():
        return [f"PROJECT_CONTEXT.md is missing at {context_path}"]

    errors: list[str] = []

    sources: dict[str, str] = {}
    for label, relative in (
        ("context", "PROJECT_CONTEXT.md"),
        ("build", "app/build.gradle.kts"),
        ("preferences", "core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt"),
        ("driver_source", str(DRIVER_SOURCE)),
    ):
        try:
            sources[label] = (root / relative).read_text(encoding="utf-8")
        except OSError as exc:
            # A source file that moved or lost its read bit used to surface as
            # a traceback, which reads as the gate being broken rather than the
            # repository being wrong.
            errors.append(f"{relative} could not be read: {exc}")
    if errors:
        return errors

    context = sources["context"]
    normalized_context = re.sub(r"\s+", " ", context)
    build = sources["build"]
    preferences = sources["preferences"]
    driver_source = sources["driver_source"]

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
        # Named by the enum constants the source markers below check, not by
        # a driver class name. The rootless path was a reflection driver
        # called ColorDisplayManager and is now a secure-settings one; the
        # contract that matters is the order, and pinning it to a class name
        # meant the gate forbade correcting the document.
        "**Auto driver order**: root drivers → `COLOR_DISPLAY_MANAGER` → `OVERLAY`",
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

    errors.extend(_check_latest_release(root, context))

    return errors


def _check_latest_release(root: Path, context: str) -> list[str]:
    """The document names the newest tag. Nothing kept it in step with git."""
    documented = _first_group(CONTEXT_RELEASE_RE, context)
    if documented is None:
        return ["context has no 'Latest tagged release' line"]
    try:
        described = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as exc:
        return [f"git describe could not run: {exc}"]
    if described.returncode != 0:
        # No tags reachable, e.g. a shallow clone. Nothing to compare against,
        # and failing here would block a gate for a checkout-shape problem.
        return []
    latest = described.stdout.strip()
    if latest and documented != latest:
        return [f"context latest release {documented!r} != newest git tag {latest!r}"]
    return []


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
