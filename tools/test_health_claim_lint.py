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

    def test_flags_out_of_range_kcal_recovery_scalar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "docs/troubleshooting.md",
                'adb shell su -c "echo 255 256 255 > /sys/devices/platform/kcal_ctrl.0/kcal"\n',
            )

            violations = lint.scan(root, ["docs"], lint.build_rules(root))

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "kcal-recovery-scalar-range")

    def test_allows_in_range_kcal_recovery_scalar(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "docs/root-safety.md",
                'adb shell su -c "echo 255 255 255 > /sys/devices/platform/kcal_ctrl.0/kcal"\n',
            )

            violations = lint.scan(root, ["docs"], lint.build_rules(root))

        self.assertEqual(violations, [])


class NegationMustReachTheClaim(unittest.TestCase):
    """C276. The lint whitelisted a whole line containing any negation."""

    def violations_for(self, text):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(root / "README.md", text)
            return lint.scan(root, ["README.md", "docs"], lint.build_rules(root))

    def test_a_negation_after_the_claim_does_not_excuse_it(self):
        # The sentence the lint exists to catch, with the word that used to
        # switch the whole line off sitting after the claim.
        violations = self.violations_for("OpenLumen improves sleep, no question.\n")

        self.assertEqual(len(violations), 1)

    def test_a_negation_in_a_previous_sentence_does_not_reach(self):
        violations = self.violations_for(
            "OpenLumen makes no health claims. OpenLumen helps you sleep.\n"
        )

        self.assertEqual(len(violations), 1)

    def test_a_denial_of_something_else_does_not_cover_the_claim(self):
        # Each of these has a real denial in front of the claim, and in each
        # one the denial belongs to a different clause. A lookbehind that only
        # stopped at a full stop let all three through.
        for line in (
            "OpenLumen, no exaggeration, improves sleep.\n",
            "There is no cure for insomnia, but OpenLumen improves sleep.\n",
            "We make no medical claims, and OpenLumen protects your eyes.\n",
        ):
            with self.subTest(line=line):
                self.assertEqual(len(self.violations_for(line)), 1)

    def test_a_denial_of_the_claim_itself_still_passes(self):
        # Positive control. These are the sentences the exemption exists for,
        # and every one of them has to keep passing.
        for line in (
            'No "improves your sleep" claims.\n',
            "OpenLumen does not improve sleep.\n",
            "This is not a treatment for eye strain.\n",
            "Avoid saying it helps you sleep.\n",
            # A parenthetical between the denial and the claim is a separate
            # clause and no longer exempts it, which is how the three bypasses
            # above worked. Write the aside first: this is the same sentence,
            # reordered, and it passes.
            "On its own, a colour filter does not improve sleep.\n",
        ):
            with self.subTest(line=line):
                self.assertEqual(self.violations_for(line), [])

    def test_a_recovery_command_is_checked_whatever_else_the_line_says(self):
        # The scalar range is a number, not a claim, so a "not" elsewhere on
        # the line says nothing about whether the values are in range. The old
        # whole-line exemption skipped the check entirely.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "docs/troubleshooting.md",
                'Do not run this: su -c "echo 255 256 255 > /sys/devices/platform/kcal_ctrl.0/kcal"\n',
            )

            violations = lint.scan(root, ["docs"], lint.build_rules(root))

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "kcal-recovery-scalar-range")


class KotlinStringsAreScanned(unittest.TestCase):
    """C276. The scanner read three file types and none of them was Kotlin."""

    def violations_for(self, kotlin):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/health-evidence.md", EVIDENCE)
            write(root / "app/src/main/java/com/openlumen/ui/Copy.kt", kotlin)
            return lint.scan(root, ["app/src/main/java", "docs"], lint.build_rules(root))

    def test_a_claim_in_a_string_literal_is_flagged(self):
        violations = self.violations_for(
            'package com.openlumen.ui\n\nval headline = "OpenLumen helps you sleep"\n'
        )

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].rule.name, "helps-you-sleep")
        self.assertEqual(violations[0].line_number, 3)

    def test_a_claim_in_a_raw_string_is_flagged(self):
        violations = self.violations_for(
            'package com.openlumen.ui\n\nval body = """OpenLumen helps you sleep"""\n'
        )

        self.assertEqual(len(violations), 1)

    def test_a_comment_explaining_the_ban_is_not_a_claim(self):
        # Positive control, and the reason only literals are read: a comment
        # recording why a phrase is banned would otherwise trip the rule it
        # documents, and there is nowhere to put that note.
        violations = self.violations_for(
            "package com.openlumen.ui\n\n"
            # Carries no negation of its own on purpose. A comment that
            # denied the claim would pass on the negation rule instead of
            # on the masking, and prove nothing about reading Kotlin.
            "// Copy review: the headline here used to say OpenLumen helps you sleep.\n"
            # And the same phrase in quotes, which is how anyone would
            # actually write that note. A regex over the raw text kept the
            # quoted run and flagged the comment documenting the ban.
            '// Never write "sleep better" in user copy.\n'
            "/** The phrase \"helps you sleep\" is banned; see the evidence doc. */\n"
            'val headline = "Warmer at night"\n'
        )

        self.assertEqual(violations, [])

    def test_a_char_literal_does_not_swallow_the_rest_of_the_line(self):
        # A lone quote in a char literal opened a string as far as a regex was
        # concerned, and everything to the next quote was blanked out, taking
        # a real claim with it.
        violations = self.violations_for(
            "package com.openlumen.ui\n\n"
            "val quote = '\"'\n"
            'val headline = "helps you sleep"\n'
        )

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].line_number, 4)

    def test_kotlin_line_numbers_survive_the_blanking(self):
        violations = self.violations_for(
            "package com.openlumen.ui\n"
            + "\n" * 20
            + 'val headline = "sleep better"\n'
        )

        self.assertEqual(len(violations), 1)
        self.assertEqual(violations[0].line_number, 22)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
