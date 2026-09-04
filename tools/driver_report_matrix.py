#!/usr/bin/env python3
"""Draft OpenLumen device-matrix rows from driver reports or issue JSON."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


REPORT_MARKER = "OpenLumen driver report"
NO_RESPONSE = {"", "_No response_", "No response", "n/a", "N/A"}

# A driver report is a stranger's text on its way into a committed document.
# Anyone can open an issue, so every field that comes from one is bounded and
# stripped of the characters that would otherwise let it close a table cell,
# open a code span, inject raw HTML, or plant a link on a maintainer.
MAX_FIELD_CHARS = 80
MAX_CELL_CHARS = 400
# Parentheses are deliberately absent. A link needs its brackets, and every
# second device string reads "Pixel 8 (shiba)" or "15 (API 35)": escaping
# those would cost the table's readability and buy nothing.
MARKDOWN_ACTIVE = "`<>[]"

ISSUE_LABELS = {
    "openlumen version": "version",
    "device": "device",
    "android version fingerprint": "android",
    "android version and fingerprint": "android",
    "oem software rom": "rom",
    "root status": "root",
    "engine": "engine",
    "status": "status",
    "driver report": "driver_report",
    "additional notes": "notes",
}

ENGINE_LABELS = {
    "COLOR_DISPLAY_MANAGER": "CDM",
    "ColorDisplayManager (CDM)": "CDM",
    "ColorDisplayManager": "CDM",
    # The issue template calls it what the Driver tab calls it now. The
    # enum name stays in the label so this mapping cannot go stale again.
    "System Night Light (COLOR_DISPLAY_MANAGER)": "CDM",
    "System Night Light": "CDM",
    "SurfaceFlinger": "SF",
    "SURFACE_FLINGER": "SF",
    "KCAL": "KCAL",
    "Overlay": "Overlay",
    "OVERLAY": "Overlay",
}


@dataclass
class ParsedInput:
    source: str
    issue: dict[str, str] = field(default_factory=dict)
    report: dict[str, str] = field(default_factory=dict)
    probes: dict[str, str] = field(default_factory=dict)
    flags: list[str] = field(default_factory=list)


class ParseError(ValueError):
    def __init__(self, errors: list[str]) -> None:
        super().__init__("\n".join(errors))
        self.errors = errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Draft a docs/device-matrix.md row from an OpenLumen driver "
            "report or GitHub issue JSON. Engine result cells are left as ? "
            "for maintainer review."
        )
    )
    parser.add_argument(
        "input",
        nargs="?",
        help="Path to a report/issue JSON file. Reads stdin when omitted or '-'.",
    )
    args = parser.parse_args(argv)

    raw = read_input(args.input)
    try:
        parsed = parse_input(raw)
        print(render_suggestion(parsed))
    except ParseError as exc:
        print("Could not draft a device-matrix row:", file=sys.stderr)
        for error in exc.errors:
            print(f"- {error}", file=sys.stderr)
        return 2
    return 0


def read_input(input_arg: str | None) -> str:
    if not input_arg or input_arg == "-":
        return sys.stdin.read()
    return Path(input_arg).read_text(encoding="utf-8")


def parse_input(raw: str) -> ParsedInput:
    text = raw.strip()
    if not text:
        raise ParseError(["Input is empty. Paste a driver report or issue JSON."])

    parsed = try_parse_issue_json(text)
    if parsed is None:
        parsed = ParsedInput(source="driver report text")
        report_text = text
    else:
        report_text = parsed.issue.get("driver_report", "")

    if REPORT_MARKER in report_text:
        report, probes = parse_driver_report(report_text)
        parsed.report.update(report)
        parsed.probes.update(probes)
        parsed.flags.append("driver report: parsed")
    elif parsed.issue:
        parsed.flags.append("driver report: missing from issue JSON")
    else:
        raise ParseError(
            [
                f"Input does not contain '{REPORT_MARKER}'.",
                "For issue JSON, include the GitHub issue body or a driver_report field.",
            ]
        )

    validate_for_row(parsed)
    return parsed


def try_parse_issue_json(text: str) -> ParsedInput | None:
    try:
        obj = json.loads(text)
    except json.JSONDecodeError:
        return None

    if not isinstance(obj, dict):
        raise ParseError(["JSON input must be an object exported from a driver report issue."])

    issue = fields_from_json(obj)
    body = value_or_empty(obj.get("body"))
    if body:
        issue.update({k: v for k, v in parse_issue_body(body).items() if v})

    parsed = ParsedInput(source="issue JSON", issue=issue)
    number = value_or_empty(obj.get("number"))
    url = value_or_empty(obj.get("url") or obj.get("html_url"))
    if number:
        parsed.flags.append(f"issue number: {number}")
    if url:
        parsed.flags.append(f"issue url: {url}")
    return parsed


def fields_from_json(obj: dict[str, Any]) -> dict[str, str]:
    fields: dict[str, str] = {}
    for key in ("version", "device", "android", "rom", "root", "engine", "status", "driver_report", "notes"):
        value = value_or_empty(obj.get(key))
        if value:
            fields[key] = value
    return fields


def parse_issue_body(body: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    current_label: str | None = None
    current_lines: list[str] = []

    def flush() -> None:
        if current_label is None:
            return
        key = ISSUE_LABELS.get(normalize_label(current_label))
        value = "\n".join(current_lines).strip()
        if key and value not in NO_RESPONSE:
            fields[key] = value

    for line in body.splitlines():
        heading = re.match(r"^###\s+(.+?)\s*$", line)
        if heading:
            flush()
            current_label = heading.group(1)
            current_lines = []
        else:
            current_lines.append(line)
    flush()
    return fields


def parse_driver_report(report_text: str) -> tuple[dict[str, str], dict[str, str]]:
    report: dict[str, str] = {}
    probes: dict[str, str] = {}
    for raw_line in report_text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        version_match = re.match(r"OpenLumen driver report v(\d+)", line)
        if version_match:
            report["report_version"] = version_match.group(1)
            continue
        probe_match = re.match(r"-\s+([A-Z_]+)\s+\(.+?\):\s+(.+)", line)
        if probe_match:
            engine = ENGINE_LABELS.get(probe_match.group(1), probe_match.group(1))
            probes[engine] = probe_match.group(2).strip()
            continue
        if ":" not in line:
            continue
        key, value = [part.strip() for part in line.split(":", 1)]
        if key == "Version":
            report["version"] = normalize_version(value)
        elif key == "Model":
            report["device"] = value
            report["model"] = value
        elif key == "Device":
            report["device_code"] = value
        elif key in {"Manufacturer", "Brand", "Product", "Hardware", "SoC"}:
            report[key.lower()] = value
        elif key == "Android":
            report["android"] = value
        elif key == "Fingerprint":
            report["fingerprint"] = value
    return report, probes


def validate_for_row(parsed: ParsedInput) -> None:
    errors: list[str] = []
    if not choose(parsed, "device"):
        errors.append("Missing device name/model.")
    if not choose(parsed, "android"):
        errors.append("Missing Android version or fingerprint.")
    if not choose(parsed, "version"):
        errors.append("Missing OpenLumen version.")
    if parsed.source == "issue JSON" and not parsed.issue.get("status"):
        errors.append("Issue JSON is missing the reported engine status.")
    if errors:
        raise ParseError(errors)


def render_suggestion(parsed: ParsedInput) -> str:
    row = build_row(parsed)
    flags = build_flags(parsed)
    return "\n".join(
        [
            "Proposed device-matrix row (maintainer review required):",
            row,
            "",
            "Confidence flags:",
            *[f"- {flag}" for flag in flags],
        ]
    )


def build_row(parsed: ParsedInput) -> str:
    device = choose(parsed, "device") or "review"
    android = choose(parsed, "android") or "review"
    oem = reported(parsed, "rom") or choose(parsed, "manufacturer") or choose(parsed, "brand") or "review"
    root = reported(parsed, "root") or "review"
    release = normalize_version(choose(parsed, "version") or "review")
    notes = build_notes(parsed)
    cells = [
        device,
        android,
        oem,
        root,
        "?",
        "?",
        "?",
        "?",
        release,
        notes,
    ]
    return "| " + " | ".join(escape_cell(cell) for cell in cells) + " |"


def build_notes(parsed: ParsedInput) -> str:
    notes: list[str] = []
    engine = reported(parsed, "engine")
    status = reported(parsed, "status")
    if engine or status:
        notes.append(f"review reported {engine or 'engine'}: {status or 'status missing'}")
    if parsed.probes:
        probe_bits = [
            f"{truncate(engine, MAX_FIELD_CHARS)} {truncate(state, MAX_FIELD_CHARS)}"
            for engine, state in sorted(parsed.probes.items())
        ]
        notes.append("probes: " + ", ".join(probe_bits))
    fingerprint = choose(parsed, "fingerprint")
    if fingerprint:
        notes.append("fingerprint captured")
    issue_url = next((flag.removeprefix("issue url: ") for flag in parsed.flags if flag.startswith("issue url: ")), "")
    if issue_url:
        notes.append(issue_url)
    notes.append("marks require maintainer smoke review")
    return "; ".join(notes)


def build_flags(parsed: ParsedInput) -> list[str]:
    flags = [f"input: {parsed.source}"]
    flags.extend(parsed.flags)
    if parsed.report.get("report_version"):
        flags.append(f"driver report version: {parsed.report['report_version']}")
    version_sources = source_values(parsed, "version")
    if len(set(version_sources)) > 1:
        flags.append("version mismatch: " + " vs ".join(version_sources))
    engine = parsed.issue.get("engine")
    status = parsed.issue.get("status")
    if engine or status:
        flags.append(f"reported status preserved for review: {engine or 'engine missing'} - {status or 'status missing'}")
    flags.append("engine result cells intentionally left as ?")
    flags.append("do not commit this row until a maintainer verifies the smoke flow")
    return flags


def choose(parsed: ParsedInput, key: str) -> str:
    value = parsed.issue.get(key) or parsed.report.get(key) or ""
    return truncate(value, MAX_FIELD_CHARS)


def source_values(parsed: ParsedInput, key: str) -> list[str]:
    values = []
    if key in parsed.issue:
        values.append(parsed.issue[key])
    if key in parsed.report:
        values.append(parsed.report[key])
    return values


def value_or_empty(value: Any) -> str:
    return str(value).strip() if value is not None else ""


def normalize_label(label: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", label.lower()).strip()


def normalize_version(value: str) -> str:
    version = value.strip()
    if " " in version:
        version = version.split(" ", 1)[0]
    if version and version != "review" and not version.startswith("v"):
        version = f"v{version}"
    return version


def escape_cell(value: str, limit: int = MAX_CELL_CHARS) -> str:
    # Whitespace first: collapsing folds any newline the reporter used to
    # break out of the row. Then cut, then escape. The bound is on what the
    # reporter wrote, so escapes must not count toward it, and a cut taken
    # afterwards can split an escape from the character it was escaping and
    # leave a backslash standing on its own.
    collapsed = " ".join(value.replace("|", "/").split())
    return escape_markdown(truncate(collapsed, limit))


def escape_markdown(value: str) -> str:
    return "".join(
        "\\" + ch if ch == "\\" or ch in MARKDOWN_ACTIVE else ch for ch in value
    )


def truncate(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    return value[: max(limit - 3, 0)].rstrip() + "..."


def reported(parsed: ParsedInput, key: str) -> str:
    """A field straight from the issue body, bounded before it is used."""
    return truncate(value_or_empty(parsed.issue.get(key)), MAX_FIELD_CHARS)


if __name__ == "__main__":
    raise SystemExit(main())
