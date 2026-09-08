import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import dependency_update_review as review


CATALOG = """
[versions]
agp = "9.2.1"
kotlin = "2.3.21"
compose-bom = "2026.05.00"
material3 = "1.4.0"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "androidx.compose.ui:ui" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
"""

REPORT_CATALOG = """
[versions]
androidx = "1.0.0"
held = "2.0.0"
uncertain = "1.0.0"

[libraries]
androidx = { module = "androidx.example:example", version.ref = "androidx" }
held = { module = "androidx.example:held", version.ref = "held" }
uncertain = { module = "androidx.example:uncertain", version.ref = "uncertain" }
"""


class DependencyUpdateReviewTest(unittest.TestCase):
    def test_parse_version_catalog_includes_libraries_and_plugin_markers(self):
        with tempfile.TemporaryDirectory() as tmp:
            catalog = Path(tmp) / "libs.versions.toml"
            catalog.write_text(CATALOG, encoding="utf-8")

            refs = {ref.name: ref for ref in review.parse_version_catalog(catalog)}

        self.assertEqual(refs["agp"].current, "9.2.1")
        self.assertIn(
            review.Coordinate(
                "com.android.application",
                "com.android.application.gradle.plugin",
                "plugin:android-application",
            ),
            refs["agp"].coordinates,
        )
        self.assertIn(
            review.Coordinate("androidx.compose.material3", "material3", "library:compose-material3"),
            refs["material3"].coordinates,
        )
        self.assertNotIn("compose-ui", refs)

    def test_stable_filter_rejects_pre_release_qualifiers(self):
        self.assertTrue(review.is_stable_version("2.10.0"))
        self.assertTrue(review.is_stable_version("2026.05.00"))
        self.assertFalse(review.is_stable_version("2.10.0-rc01"))
        self.assertFalse(review.is_stable_version("1.0.0-alpha14"))
        self.assertFalse(review.is_stable_version("1.0.0-SNAPSHOT"))

    def test_version_sort_key_handles_numeric_segments(self):
        self.assertGreater(review.version_sort_key("2.10.0"), review.version_sort_key("2.9.8"))
        self.assertGreater(review.version_sort_key("2026.05.00"), review.version_sort_key("2025.12.99"))

    def test_classify_status_reports_available_updates(self):
        candidate = review.Candidate(
            "1.2.0",
            "google",
            review.Coordinate("androidx.example", "example", "library:example"),
        )

        self.assertEqual(review.classify_status("1.1.0", candidate, []), "update-available")
        self.assertEqual(review.classify_status("1.2.0", candidate, []), "current")
        self.assertEqual(review.classify_status("1.2.0-alpha01", None, []), "pre-release-only")
        self.assertEqual(review.classify_status("1.2.0", None, ["network error"]), "unresolved")

    def test_report_attaches_release_evidence_and_distinguishes_holds(self):
        policy = {
            "held": {"held": {"version": "2.0.0", "reason": "wait for device matrix"}},
            "release_notes": {
                "androidx": {
                    "url": "https://developer.android.com/jetpack/androidx/versions",
                    "module": "AndroidX example",
                    "area": "androidx",
                }
            },
        }

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / "libs.versions.toml"
            catalog.write_text(REPORT_CATALOG, encoding="utf-8")
            policy_path = root / "policy.json"
            policy_path.write_text(json.dumps(policy), encoding="utf-8")

            def candidates(ref, include_pre_releases, timeout_seconds):
                if ref.name == "androidx":
                    return [
                        review.Candidate(
                            "1.1.0",
                            "google",
                            ref.coordinates[0],
                        )
                    ], []
                if ref.name == "held":
                    return [
                        review.Candidate(
                            "3.0.0",
                            "google",
                            ref.coordinates[0],
                        )
                    ], []
                return [], ["network error"]

            with patch.object(review, "collect_candidates", side_effect=candidates):
                report = review.review_catalog(catalog, False, 1, policy_path)

        update = next(entry for entry in report["updates"] if entry["name"] == "androidx")
        self.assertEqual(update["evidence_status"], "mapped")
        self.assertEqual(update["release_notes_url"], policy["release_notes"]["androidx"]["url"])
        self.assertEqual(update["affected_module"], "AndroidX example")
        self.assertTrue(update["compatibility_risk_note"])
        self.assertTrue(update["required_commands"])
        self.assertIn("verification-metadata.xml", update["verification_metadata_impact"])

        self.assertEqual([entry["name"] for entry in report["held"]], ["held"])
        uncertain = next(entry for entry in report["unresolved"] if entry["name"] == "uncertain")
        self.assertNotEqual(uncertain["status"], "current")

    def test_missing_release_note_mapping_is_explicit(self):
        ref = review.VersionRef(
            "unknown",
            "1.0.0",
            (review.Coordinate("com.example", "example", "library:example"),),
        )

        evidence = review.build_update_evidence(ref, {})

        self.assertEqual(evidence["evidence_status"], "missing")
        self.assertIsNone(evidence["release_notes_url"])

    def test_checked_in_policy_covers_current_catalog(self):
        root = Path(__file__).resolve().parents[1]
        refs = review.parse_version_catalog(root / "gradle/libs.versions.toml")
        policy = review.load_policy(root / "tools/dependency_update_policy.json")

        self.assertEqual(
            {ref.name for ref in refs} - set(policy["release_notes"]),
            set(),
        )


SHARED_REF_CATALOG = """
[versions]
shared = "1.0.0"

[libraries]
first = { module = "com.example:first", version.ref = "shared" }
second = { module = "com.example:second", version.ref = "shared" }
"""


class VersionPrecedenceTest(unittest.TestCase):
    """C277. A pre-release is by definition older than the release it leads to."""

    def assertOlder(self, older, newer):
        self.assertLess(review.version_sort_key(older), review.version_sort_key(newer))

    def test_a_pre_release_never_outranks_its_release(self):
        self.assertOlder("2.0.0-alpha01", "2.0.0")
        self.assertOlder("2.0.0-rc03", "2.0.0")
        self.assertOlder("1.0.0-SNAPSHOT", "1.0.0")
        self.assertOlder("2026.05.00-alpha01", "2026.05.00")

    def test_pre_releases_of_one_version_order_among_themselves(self):
        self.assertOlder("2.0.0-alpha01", "2.0.0-alpha02")
        self.assertOlder("2.0.0-alpha09", "2.0.0-beta01")
        self.assertOlder("2.0.0-beta01", "2.0.0-rc01")
        # Numeric identifiers rank below alphanumeric ones inside a
        # pre-release, which is the one place the ordering inverts.
        self.assertOlder("1.0.0-alpha.1", "1.0.0-alpha.beta")

    def test_a_numeric_suffix_is_a_build_number_and_not_a_pre_release(self):
        # Maven, not semantic versioning, is what this tool reads. A hyphen
        # followed by digits is a build number there and is newer than the
        # bare release: KSP publishes 2.3.11-1.0.5 against Kotlin 2.3.11, and
        # Guava publishes 33.0.0-jre. Reading either as a pre-release reported
        # a real update as already current.
        self.assertOlder("2.3.11", "2.3.11-1.0.5")
        self.assertOlder("2.3.11-1.0.4", "2.3.11-1.0.5")
        self.assertOlder("33.0.0", "33.0.0-jre")

    def test_a_qualifier_suffix_is_still_a_pre_release(self):
        # Positive control for the rule above: the qualifier list is what
        # decides, so alpha, beta, rc and SNAPSHOT keep ranking below.
        self.assertOlder("2.3.11-alpha01", "2.3.11")
        self.assertOlder("2.3.11-rc1", "2.3.11-1.0.5")

    def test_a_release_still_outranks_an_older_release(self):
        # Positive control: the fix must not flatten ordinary comparisons.
        self.assertOlder("2.9.8", "2.10.0")
        self.assertOlder("2025.12.99", "2026.05.00")
        self.assertOlder("1.0.0", "1.0.1")

    def test_a_pre_release_still_outranks_the_release_before_it(self):
        self.assertOlder("1.9.0", "2.0.0-alpha01")

    def test_build_metadata_carries_no_precedence(self):
        self.assertEqual(
            review.version_sort_key("1.2.3+build9"),
            review.version_sort_key("1.2.3"),
        )


class MissingCoordinateTest(unittest.TestCase):
    """C277. A 404 everywhere means the coordinate moved, not that the pin is fine."""

    def not_found(self, base_url, coordinate, timeout_seconds):
        raise review.urllib.error.HTTPError(base_url, 404, "Not Found", {}, None)

    def ref(self):
        return review.VersionRef(
            "gone",
            "1.0.0",
            (review.Coordinate("com.example", "gone", "library:gone"),),
        )

    def test_a_coordinate_no_repository_publishes_is_an_error(self):
        with patch.object(review, "fetch_metadata_versions", side_effect=self.not_found):
            candidates, errors = review.collect_candidates(self.ref(), False, 1)

        self.assertEqual(candidates, [])
        self.assertEqual(len(errors), 1)
        self.assertIn("com.example:gone", errors[0])
        self.assertEqual(review.classify_status("1.0.0", None, errors), "unresolved")

    def test_one_repository_answering_is_not_an_error(self):
        # Positive control. Every coordinate 404s on two of the three
        # repositories in normal operation, and that must stay silent.
        def only_google(base_url, coordinate, timeout_seconds):
            if "google" not in base_url:
                raise review.urllib.error.HTTPError(base_url, 404, "Not Found", {}, None)
            return ["1.0.0", "1.1.0"]

        with patch.object(review, "fetch_metadata_versions", side_effect=only_google):
            candidates, errors = review.collect_candidates(self.ref(), False, 1)

        self.assertEqual(errors, [])
        self.assertEqual({candidate.version for candidate in candidates}, {"1.0.0", "1.1.0"})

    def test_the_review_exits_non_zero_when_a_reference_is_unresolved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / "libs.versions.toml"
            catalog.write_text(SHARED_REF_CATALOG, encoding="utf-8")
            policy = root / "policy.json"
            policy.write_text(json.dumps({"held": {}, "release_notes": {}}), encoding="utf-8")

            with patch.object(review, "fetch_metadata_versions", side_effect=self.not_found):
                code = review.main(
                    ["--catalog", str(catalog), "--policy", str(policy), "--timeout-seconds", "1"]
                )

        self.assertEqual(code, 1)

    def test_a_withdrawn_artifact_is_not_reported_as_an_available_update(self):
        # The shared reference drives two artifacts. One publishes a newer
        # version, the other is gone. Proposing the bump would leave the
        # catalog unresolvable, and reporting it green would hide the removal.
        def one_missing(base_url, coordinate, timeout_seconds):
            if coordinate.artifact == "first":
                return ["1.0.0", "1.1.0"]
            raise review.urllib.error.HTTPError(base_url, 404, "Not Found", {}, None)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / "libs.versions.toml"
            catalog.write_text(SHARED_REF_CATALOG, encoding="utf-8")
            policy = root / "policy.json"
            policy.write_text(json.dumps({"held": {}, "release_notes": {}}), encoding="utf-8")

            with patch.object(review, "fetch_metadata_versions", side_effect=one_missing):
                report = review.review_catalog(catalog, False, 1, policy)
                code = review.main(
                    ["--catalog", str(catalog), "--policy", str(policy), "--timeout-seconds", "1"]
                )

        entry = next(e for e in report["unresolved"] if e["name"] == "shared")
        self.assertEqual(entry["status"], "unresolved")
        self.assertEqual(report["updates"], [])
        self.assertEqual(code, 1)

    def test_two_artifacts_with_no_version_in_common_are_not_silent(self):
        def disjoint(base_url, coordinate, timeout_seconds):
            return ["1.2.0"] if coordinate.artifact == "first" else ["1.1.0"]

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / "libs.versions.toml"
            catalog.write_text(SHARED_REF_CATALOG, encoding="utf-8")
            policy = root / "policy.json"
            policy.write_text(json.dumps({"held": {}, "release_notes": {}}), encoding="utf-8")

            with patch.object(review, "fetch_metadata_versions", side_effect=disjoint):
                code = review.main(
                    ["--catalog", str(catalog), "--policy", str(policy), "--timeout-seconds", "1"]
                )

        self.assertEqual(code, 1)

    def test_an_outage_is_not_reported_as_a_withdrawal(self):
        def unreachable(base_url, coordinate, timeout_seconds):
            raise review.urllib.error.URLError("connection refused")

        ref = review.VersionRef(
            "gone", "1.0.0", (review.Coordinate("com.example", "gone", "library:gone"),)
        )
        with patch.object(review, "fetch_metadata_versions", side_effect=unreachable):
            _, errors = review.collect_candidates(ref, False, 1)

        self.assertTrue(any("no repository could be reached" in e for e in errors))
        self.assertFalse(any("not published" in e for e in errors))

    def test_a_clean_review_still_exits_zero(self):
        # Positive control for the exit code: an ordinary run must not start
        # failing the release checklist.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog = root / "libs.versions.toml"
            catalog.write_text(SHARED_REF_CATALOG, encoding="utf-8")
            policy = root / "policy.json"
            policy.write_text(json.dumps({"held": {}, "release_notes": {}}), encoding="utf-8")

            with patch.object(review, "fetch_metadata_versions", return_value=["1.0.0"]):
                code = review.main(
                    ["--catalog", str(catalog), "--policy", str(policy), "--timeout-seconds", "1"]
                )

        self.assertEqual(code, 0)


class SharedVersionRefTest(unittest.TestCase):
    """C277. One version ref sets the version for every artifact using it."""

    FIRST = review.Coordinate("com.example", "first", "library:first")
    SECOND = review.Coordinate("com.example", "second", "library:second")

    BOTH = (FIRST, SECOND)

    def candidates(self, first_versions, second_versions):
        return [
            review.Candidate(version, "central", self.FIRST) for version in first_versions
        ] + [
            review.Candidate(version, "central", self.SECOND) for version in second_versions
        ]

    def test_only_a_version_every_artifact_publishes_is_proposed(self):
        # Bumping the ref to 1.2.0 would leave com.example:second unresolvable,
        # because the ref sets the version for both at once.
        latest = review.latest_shared_candidate(
            self.candidates(["1.0.0", "1.1.0", "1.2.0"], ["1.0.0", "1.1.0"]),
            self.BOTH,
        )

        self.assertIsNotNone(latest)
        self.assertEqual(latest.version, "1.1.0")

    def test_an_artifact_that_answered_nothing_proposes_nothing(self):
        # The one the earlier version of this test got wrong. An artifact that
        # 404s everywhere contributes no candidates, so an intersection taken
        # over the candidates alone simply lost it and proposed a version it
        # cannot supply. The coordinates the reference drives are the input.
        latest = review.latest_shared_candidate(
            self.candidates(["1.0.0", "1.2.0"], []),
            self.BOTH,
        )

        self.assertIsNone(latest)

    def test_a_single_artifact_reference_is_unaffected(self):
        # Positive control: most refs drive one coordinate and must still see
        # their newest version. One coordinate in, one coordinate declared.
        latest = review.latest_shared_candidate(
            self.candidates(["1.0.0", "1.2.0"], []),
            (self.FIRST,),
        )

        self.assertEqual(latest.version, "1.2.0")

    def test_no_version_in_common_proposes_nothing(self):
        latest = review.latest_shared_candidate(
            self.candidates(["1.2.0"], ["1.1.0"]), self.BOTH
        )

        self.assertIsNone(latest)

    def test_an_empty_candidate_list_proposes_nothing(self):
        self.assertIsNone(review.latest_shared_candidate([], self.BOTH))


if __name__ == "__main__":
    unittest.main()
