"""Fail when a README link points at a file GitHub will not serve.

README.md is the only markdown file most people ever read, and it is the only
one that is definitely published. Every relative link in it therefore has to
resolve in the repository as GitHub sees it: the target must exist on disk and
be tracked by git. A file that is present locally but gitignored produces a 404
for everyone else, which is what happened to the whole `docs/` tree.

Usage:
    py -3 tools/check_readme_links.py [--root .]
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# Markdown inline links: [text](target). Reference-style links and bare URLs
# are not used in this README, and adding support for them without a case to
# test against would be guesswork.
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")

REMOTE_SCHEMES = ("http://", "https://", "mailto:", "tel:")


def link_targets(readme: str) -> list[str]:
    """Local link targets in document order, anchors and duplicates removed."""
    seen: list[str] = []
    for raw in LINK.findall(readme):
        if raw.startswith(REMOTE_SCHEMES) or raw.startswith("#"):
            continue
        target = raw.split("#", 1)[0].strip()
        if target and target not in seen:
            seen.append(target)
    return seen


def tracked_files(root: Path) -> set[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files"],
        capture_output=True,
        text=True,
        check=True,
    )
    return {line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip()}


def check(root: Path) -> list[str]:
    readme = root / "README.md"
    if not readme.is_file():
        return ["README.md is missing"]

    tracked = tracked_files(root)
    problems: list[str] = []
    for target in link_targets(readme.read_text(encoding="utf-8")):
        path = (root / target).resolve()
        if not path.exists():
            problems.append(f"{target}: no such file")
        elif target.rstrip("/") not in tracked and not any(
            entry.startswith(target.rstrip("/") + "/") for entry in tracked
        ):
            problems.append(f"{target}: present locally but not tracked, so it 404s on GitHub")
    return problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="repository root")
    args = parser.parse_args(argv)

    problems = check(Path(args.root).resolve())
    if problems:
        print("README links that will not resolve on GitHub:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print("README link check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
