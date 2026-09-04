#!/usr/bin/env python3
"""Lint OpenLumen copy for unsupported health and sleep claims."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


TEXT_SUFFIXES = {".md", ".txt", ".xml", ".kt"}
DEFAULT_TARGETS = (
    "README.md",
    "app/src/main/res",
    "app/src/main/java",
    "fastlane/metadata/android",
    "docs",
)
# Kotlin is scanned for its string literals alone. Identifiers and comments are
# the developers' own words, and a comment recording why a phrase is banned
# would otherwise trip the rule it documents. See kotlin_string_spans.
APPROVED_EVIDENCE_PATHS = {
    Path("docs/health-evidence.md"),
}
# A denial has to reach the phrase it denies. Exempting a whole line that
# contains one of these anywhere let "OpenLumen improves sleep, no question."
# through, and a 60-character lookbehind that stopped only at a full stop let
# "OpenLumen, no exaggeration, improves sleep." through as well.
#
# Not English-only: a localised disclaimer has to be able to pass too, and the
# claim rules cover five other languages. Japanese negation is a verb ending
# rather than a word, so the Japanese rules have no exemption at all and a
# Japanese denial has to be worded to avoid the flagged phrase.
NEGATION_RE = re.compile(
    r"\b(?:no|not|never|without|avoid|avoids|don't|doesn't|isn't|aren't|"
    r"forbids?|unsupported|rather than|instead of|"
    r"ne|pas|jamais|sans|aucun|aucune|"
    r"nicht|kein|keine|niemals|ohne|"
    r"nunca|sin|ningun|ninguna|ningún|ninguna|"
    r"nao|não|nem|sem)\b"
)
# What may sit between the denial and the phrase it denies. Enough for
# `No "improves your sleep" claims` and `Avoid saying it helps you sleep`, and
# nothing with a comma or a sentence break in it: a parenthetical between the
# two is a different clause, which is how every bypass above worked.
NEGATION_GAP = 24
GAP_BREAK_RE = re.compile(r"[,;:!?.]")
KCAL_RECOVERY_RE = re.compile(
    r"(?i)\becho\s+(-?\d+)\s+(-?\d+)\s+(-?\d+)\s+>[^\n]*\bkcal\b"
)
KCAL_MAX_SCALAR = 255


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[str]
    source: str


@dataclass(frozen=True)
class Violation:
    path: Path
    line_number: int
    rule: Rule
    line: str


FALLBACK_PHRASES = (
    "improves sleep",
    "improve sleep",
    "improves your sleep",
    "helps you sleep",
    "sleep better",
    "reduces eye strain",
    "reduce eye strain",
    "prevents eye damage",
    "prevent eye damage",
    "protects your eyes",
    "protect your eyes",
    "doctor recommended",
    "clinically proven",
)


MULTILINGUAL_PATTERNS = (
    ("sleep-claim-es", r"\b(?:mejora(?:r)? el sueño|dormir mejor|ayuda a dormir)\b"),
    ("sleep-claim-fr", r"\b(?:améliore(?:r)? le sommeil|mieux dormir|aide à dormir)\b"),
    ("sleep-claim-de", r"\b(?:schlaf verbessern|besser schlafen|hilft (?:dir )?beim schlafen)\b"),
    ("sleep-claim-pt", r"\b(?:melhora(?:r)? o sono|dormir melhor|ajuda (?:voce|você)? a dormir)\b"),
    ("sleep-claim-ja", r"(?:睡眠を改善|よく眠)"),
    ("eye-claim-es", r"\b(?:reduce la fatiga visual|protege (?:tus|los) ojos)\b"),
    ("eye-claim-fr", r"\b(?:réduit la fatigue oculaire|protège (?:vos|les) yeux)\b"),
    ("eye-claim-de", r"\b(?:reduziert augenbelastung|schützt (?:deine )?augen)\b"),
    ("eye-claim-pt", r"\b(?:reduz a fadiga ocular|protege (?:seus|os) olhos)\b"),
    ("eye-claim-ja", r"(?:目の疲れを軽減|目を保護)"),
    ("clinical-claim-es", r"\b(?:clínicamente probado|recomendado por médicos?)\b"),
    ("clinical-claim-fr", r"\b(?:cliniquement prouvé|recommandé par (?:un )?médecin)\b"),
    ("clinical-claim-de", r"\b(?:klinisch bewiesen|ärztlich empfohlen)\b"),
    ("clinical-claim-pt", r"\b(?:clinicamente comprovado|recomendado por médicos?)\b"),
    ("clinical-claim-ja", r"(?:臨床的に証明|医師推奨)"),
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fail on unsupported OpenLumen health claims.")
    parser.add_argument("--root", default=".", help="Repository root to scan.")
    parser.add_argument(
        "--target",
        action="append",
        dest="targets",
        help="File or directory to scan. Defaults to README, strings, metadata, and docs.",
    )
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    rules = build_rules(root)
    violations = scan(root, args.targets or list(DEFAULT_TARGETS), rules)
    if violations:
        print("Unsupported health claims found:", file=sys.stderr)
        for violation in violations:
            rel = violation.path.relative_to(root)
            print(
                f"{rel}:{violation.line_number}: {violation.rule.name}: {violation.line.strip()}",
                file=sys.stderr,
            )
        return 1

    print(f"Health-claim lint passed ({len(list(iter_scan_files(root, args.targets or list(DEFAULT_TARGETS))))} files scanned).")
    return 0


def build_rules(root: Path) -> list[Rule]:
    phrases = set(FALLBACK_PHRASES)
    evidence = root / "docs/health-evidence.md"
    if evidence.exists():
        phrases.update(extract_avoid_phrases(evidence.read_text(encoding="utf-8")))

    rules = [
        Rule(
            normalize_rule_name(phrase),
            re.compile(re.escape(normalize_text(phrase))),
            "docs/health-evidence.md",
        )
        for phrase in sorted(phrases)
    ]
    rules.append(
        Rule(
            "treat-cure-prevent-condition",
            re.compile(
                r"\b(?:treats?|cures?|prevents?)\b.{0,80}\b"
                r"(?:condition|disorder|disease|symptoms?|sleep|insomnia|eye strain|digital eye fatigue|"
                r"eye damage|migraine|photophobia|photosensitive epilepsy|pwm sensitivity)\b"
            ),
            "docs/health-evidence.md",
        )
    )
    for name, pattern in MULTILINGUAL_PATTERNS:
        rules.append(Rule(name, re.compile(pattern, re.IGNORECASE), "localized guardrail"))
    rules.append(
        Rule(
            "kcal-recovery-scalar-range",
            KCAL_RECOVERY_RE,
            "core-engine KcalEngine.MAX_SCALAR",
        )
    )
    return rules


def extract_avoid_phrases(text: str) -> set[str]:
    phrases: set[str] = set()
    in_words_we_avoid = False
    for line in text.splitlines():
        if line.startswith("## Words we avoid"):
            in_words_we_avoid = True
            continue
        if in_words_we_avoid and line.startswith("## "):
            break
        if not in_words_we_avoid:
            continue
        for quoted in re.findall(r'"([^"]+)"', line):
            if quoted.casefold() not in {"treats", "cures", "prevents"}:
                phrases.add(quoted)
    return phrases


def scan(root: Path, targets: Iterable[str], rules: list[Rule]) -> list[Violation]:
    violations: list[Violation] = []
    for path in iter_scan_files(root, targets):
        relative = path.relative_to(root)
        if relative in APPROVED_EVIDENCE_PATHS:
            continue
        try:
            lines = scannable_lines(path, path.read_text(encoding="utf-8"))
        except UnicodeDecodeError:
            continue
        for index, line in enumerate(lines, start=1):
            normalized = normalize_text(line)
            if not normalized:
                continue
            for rule in rules:
                if rule.name == "kcal-recovery-scalar-range":
                    match = KCAL_RECOVERY_RE.search(normalized)
                    if not match:
                        continue
                    # No negation exemption here: this one is a numeric range,
                    # not a claim, and a "not" elsewhere on the line says
                    # nothing about whether the scalars are in range.
                    if any(
                        not 0 <= int(value) <= KCAL_MAX_SCALAR
                        for value in match.groups()
                    ):
                        violations.append(Violation(path, index, rule, line))
                    continue
                for match in rule.pattern.finditer(normalized):
                    if is_negated(normalized, match.start()):
                        continue
                    violations.append(Violation(path, index, rule, line))
                    break
    return violations


def scannable_lines(path: Path, text: str) -> list[str]:
    """The lines to lint, with everything that is not user-facing blanked out.

    Line numbers are preserved so a violation still points at the right line.
    """
    if path.suffix.lower() != ".kt":
        return text.splitlines()
    kept = [" " if char != "\n" else "\n" for char in text]
    for start, end in kotlin_string_spans(text):
        for index in range(start, end):
            if text[index] != "\n":
                kept[index] = text[index]
    return "".join(kept).splitlines()


def kotlin_string_spans(text: str) -> list[tuple[int, int]]:
    """Where the string literals are, walking the file rather than pattern
    matching it.

    A regex over the raw text cannot tell a quote in a comment from a quote in
    code, so `// Never write "improves sleep" here` kept its quoted phrase and
    tripped the rule the comment was there to document. It also cannot tell a
    quote inside a char literal from the start of a string, which swallowed
    the rest of the line.
    """
    spans: list[tuple[int, int]] = []
    index = 0
    length = len(text)
    block_depth = 0
    while index < length:
        char = text[index]
        if block_depth:
            if text.startswith("/*", index):
                block_depth += 1
                index += 2
            elif text.startswith("*/", index):
                block_depth -= 1
                index += 2
            else:
                index += 1
            continue
        if text.startswith("//", index):
            end = text.find("\n", index)
            index = length if end < 0 else end
            continue
        if text.startswith("/*", index):
            block_depth = 1
            index += 2
            continue
        if text.startswith('"""', index):
            end = text.find('"""', index + 3)
            end = length if end < 0 else end + 3
            spans.append((index, end))
            index = end
            continue
        if char == '"':
            end = index + 1
            while end < length and text[end] != '"':
                if text[end] == "\\":
                    end += 1
                elif text[end] == "\n":
                    break
                end += 1
            end = min(end + 1, length)
            spans.append((index, end))
            index = end
            continue
        if char == "'":
            end = index + 1
            while end < length and text[end] != "'":
                if text[end] == "\\":
                    end += 1
                end += 1
            index = min(end + 1, length)
            continue
        index += 1
    return spans


def iter_scan_files(root: Path, targets: Iterable[str]) -> Iterable[Path]:
    for target in targets:
        path = (root / target).resolve()
        if not path.exists():
            continue
        if path.is_file():
            if should_scan(path):
                yield path
            continue
        for candidate in sorted(path.rglob("*")):
            if candidate.is_file() and should_scan(candidate):
                yield candidate


def should_scan(path: Path) -> bool:
    return path.suffix.lower() in TEXT_SUFFIXES


def is_negated(normalized_line: str, start: int) -> bool:
    """Whether a denial directly before [start] covers the match there.

    Directly: the last denial in front of the phrase, with nothing but a short
    run of connecting words between the two. A comma, a colon or a full stop
    in that gap means the denial belongs to another clause and says nothing
    about this claim.
    """
    window = normalized_line[max(0, start - NEGATION_GAP - 16):start]
    matches = list(NEGATION_RE.finditer(window))
    if not matches:
        return False
    gap = window[matches[-1].end():]
    return len(gap) <= NEGATION_GAP and not GAP_BREAK_RE.search(gap)


def normalize_text(text: str) -> str:
    normalized = (
        text.casefold()
        .replace("’", "'")
        .replace("“", '"')
        .replace("”", '"')
        .replace("—", " ")
        .replace("–", " ")
    )
    return re.sub(r"\s+", " ", normalized).strip()


def normalize_rule_name(phrase: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", normalize_text(phrase)).strip("-")


if __name__ == "__main__":
    raise SystemExit(main())
