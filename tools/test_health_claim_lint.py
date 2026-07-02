import tempfile
import unittest
from pathlib import Path

import health_claim_lint as lint


EVIDENCE = """
# Health and Evidence Notes

## Words we avoid

- "improves sleep" / "helps you sleep" / "sleep better"
- "reduces eye strain" / "prevents eye damage"
- "protects your eyes"
- "doctor recommended" / "clinically proven"
- "treats" / "cures" / "prevents" anything

## Sources
"""


class HealthClaimLintTest(unittest.TestCase):
    def test_flags_banned_claim_from_policy(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(root / "README.md", "OpenLumen helps you sleep.\n")

            violations = lint.scan(root, ["README.md", "docs"], lint.build_rules(root))

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "helps-you-sleep")

    def test_allows_disclaimer_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(root / "fastlane/metadata/android/en-US/full_description.txt", 'No "improves your sleep" claims.\n')

            violations = lint.scan(root, ["fastlane/metadata/android"], lint.build_rules(root))

        self.assertEqual(violations, [])

    def test_flags_localized_claims_in_string_resources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(
                root / "app/src/main/res/values-es/strings.xml",
                '<resources><string name="bad">Mejora el sueño</string></resources>\n',
            )

            violations = lint.scan(root, ["app/src/main/res"], lint.build_rules(root))

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "sleep-claim-es")

    def test_skips_canonical_evidence_document(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE + "\nThis app does not helps you sleep.\n")

            violations = lint.scan(root, ["docs"], lint.build_rules(root))

        self.assertEqual(violations, [])

    def test_treatment_rule_requires_condition_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(root / "README.md", "This prevents duplicate probes.\nThis treats migraine symptoms.\n")

            violations = lint.scan(root, ["README.md"], lint.build_rules(root))

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "treat-cure-prevent-condition")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
