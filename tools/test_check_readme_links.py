import subprocess
import tempfile
import unittest
from pathlib import Path

import check_readme_links as checker


def git(root: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class LinkTargetTest(unittest.TestCase):
    def test_collects_local_targets_and_drops_remote_ones(self):
        readme = (
            "[docs](docs/troubleshooting.md) and [site](https://example.com/x.md)\n"
            "[anchor only](#privacy) [mail](mailto:a@b.c)\n"
        )

        self.assertEqual(checker.link_targets(readme), ["docs/troubleshooting.md"])

    def test_strips_anchors_and_deduplicates(self):
        readme = "[a](docs/x.md#one) [b](docs/x.md#two) [c](docs/y.md)\n"

        self.assertEqual(checker.link_targets(readme), ["docs/x.md", "docs/y.md"])


class CheckTest(unittest.TestCase):
    def _repo(self, tmp: str) -> Path:
        root = Path(tmp)
        git(root, "init", "-q")
        git(root, "config", "user.email", "t@example.com")
        git(root, "config", "user.name", "t")
        return root

    def test_passes_when_every_target_is_tracked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._repo(tmp)
            write(root / "README.md", "[docs](docs/a.md)\n")
            write(root / "docs/a.md", "hi\n")
            git(root, "add", "README.md", "docs/a.md")

            self.assertEqual(checker.check(root), [])

    def test_flags_a_target_that_is_present_but_gitignored(self):
        # The whole point: the file reads fine locally and 404s for everyone
        # else, which is how the docs tree went missing from GitHub.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._repo(tmp)
            write(root / ".gitignore", "*.md\n!README.md\n")
            write(root / "README.md", "[docs](docs/a.md)\n")
            write(root / "docs/a.md", "hi\n")
            git(root, "add", "-A")

            problems = checker.check(root)

            self.assertEqual(len(problems), 1)
            self.assertIn("docs/a.md", problems[0])
            self.assertIn("not tracked", problems[0])

    def test_flags_a_target_that_does_not_exist(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._repo(tmp)
            write(root / "README.md", "[gone](docs/missing.md)\n")
            git(root, "add", "README.md")

            problems = checker.check(root)

            self.assertEqual(len(problems), 1)
            self.assertIn("no such file", problems[0])

    def test_main_exits_non_zero_on_a_broken_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._repo(tmp)
            write(root / "README.md", "[gone](docs/missing.md)\n")
            git(root, "add", "README.md")

            self.assertEqual(checker.main(["--root", str(root)]), 1)


if __name__ == "__main__":
    unittest.main()
