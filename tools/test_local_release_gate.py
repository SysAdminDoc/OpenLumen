import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch

import local_release_gate as gate


DEPENDENCIES = """
releaseRuntimeClasspath - Runtime classpath of /main.
+--- androidx.core:core-ktx:1.19.0
+--- com.google.firebase:firebase-analytics:23.0.0
\\--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0 -> 1.9.0
"""


class LocalReleaseGateTest(unittest.TestCase):
    class _Response:
        def __init__(self, payload):
            self.payload = payload

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def read(self):
            return json.dumps(self.payload).encode("utf-8")

    def test_repository_backup_rules_protect_coordinate_blob(self):
        gate.assert_backup_rules(Path(__file__).resolve().parents[1])

    def test_parse_gradle_dependencies_uses_resolved_versions(self):
        deps = gate.parse_gradle_dependencies(DEPENDENCIES)

        self.assertIn("androidx.core:core-ktx:1.19.0", deps)
        self.assertIn("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0", deps)

    def test_banned_dependency_patterns_fail(self):
        with self.assertRaises(gate.GateError):
            gate.assert_no_banned_dependencies(gate.parse_gradle_dependencies(DEPENDENCIES))

    def test_signature_gate_accepts_v2_without_legacy_v1(self):
        output = """
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
"""

        self.assertEqual([], gate.missing_required_signature_schemes(output))

    def test_signature_gate_rejects_an_apk_without_v2(self):
        output = """
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): false
"""

        self.assertEqual(["v2"], gate.missing_required_signature_schemes(output))

    def test_manifest_permission_scan_accepts_offline_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / "AndroidManifest.xml"
            manifest.write_text(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
                </manifest>""",
                encoding="utf-8",
            )

            gate.assert_no_banned_permissions(manifest)

    def test_manifest_permission_scan_rejects_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / "AndroidManifest.xml"
            manifest.write_text(
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
                </manifest>""",
                encoding="utf-8",
            )

            with self.assertRaises(gate.GateError):
                gate.assert_no_banned_permissions(manifest)

    def test_backup_rules_require_encryption_for_cloud_datastore(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rules = root / "app/src/main/res/xml"
            rules.mkdir(parents=True)
            (rules / "data_extraction_rules.xml").write_text(
                """<data-extraction-rules>
                <cloud-backup>
                  <include domain="file" path="datastore/" requireFlags="clientSideEncryption" />
                  <include domain="sharedpref" path="." />
                </cloud-backup>
                <device-transfer>
                  <include domain="file" path="datastore/" />
                  <include domain="sharedpref" path="." />
                </device-transfer>
                </data-extraction-rules>""",
                encoding="utf-8",
            )
            (rules / "backup_rules.xml").write_text(
                """<full-backup-content>
                <include domain="file" path="datastore/" requireFlags="clientSideEncryption" />
                </full-backup-content>""",
                encoding="utf-8",
            )

            gate.assert_backup_rules(root)

    def test_backup_rules_reject_unencrypted_cloud_datastore(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rules = root / "app/src/main/res/xml"
            rules.mkdir(parents=True)
            (rules / "data_extraction_rules.xml").write_text(
                """<data-extraction-rules>
                <cloud-backup><include domain="file" path="datastore/" /></cloud-backup>
                <device-transfer><include domain="file" path="datastore/" /></device-transfer>
                </data-extraction-rules>""",
                encoding="utf-8",
            )
            (rules / "backup_rules.xml").write_text(
                """<full-backup-content>
                <include domain="file" path="datastore/" />
                </full-backup-content>""",
                encoding="utf-8",
            )

            with self.assertRaises(gate.GateError):
                gate.assert_backup_rules(root)

    def test_spdx_report_is_json_serializable(self):
        report = gate.build_spdx(
            ["androidx.core:core-ktx:1.19.0"],
            metadata={
                "androidx.core:core-ktx:1.19.0": {
                    "license": "Apache-2.0",
                    "source": "https://cs.android.com/androidx/platform/frameworks/support",
                    "reason": "fixture metadata",
                }
            },
        )
        encoded = json.dumps(report)

        self.assertIn("pkg:maven/androidx.core/core-ktx@1.19.0", encoded)
        self.assertIn('"licenseDeclared": "Apache-2.0"', encoded)

    def test_spdx_report_supports_multiple_declared_licenses(self):
        report = gate.build_spdx(
            ["example:dual:1.0"],
            metadata={
                "example:dual:1.0": {
                    "license": "Apache-2.0 OR MIT",
                    "source": "https://example.invalid/source",
                    "reason": "fixture metadata",
                }
            },
        )

        self.assertEqual(report["packages"][0]["licenseDeclared"], "Apache-2.0 OR MIT")

    def test_spdx_report_rejects_unknown_license(self):
        with self.assertRaises(gate.GateError):
            gate.build_spdx(
                ["example:unknown:1.0"],
                metadata={
                    "example:unknown:1.0": {
                        "license": "LicenseRef-Unknown",
                        "source": "https://example.invalid/source",
                        "reason": "fixture metadata",
                    }
                },
            )

    def test_license_override_is_exact_and_reviewed(self):
        overrides = gate.load_license_overrides(Path(__file__).resolve().parents[1])

        self.assertEqual(overrides["com.google.guava:listenablefuture:1.0"]["license"], "Apache-2.0")
        self.assertTrue(overrides["com.google.guava:listenablefuture:1.0"]["reason"])

    def test_advisory_policy_rejects_partial_query(self):
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = Path(tmp) / "allowlist.json"
            allowlist.write_text("[]", encoding="utf-8")
            with self.assertRaises(gate.GateError):
                gate.assert_advisory_policy(
                    {"status": "partial", "vulnerabilities": []},
                    mode="query",
                    allow_unsigned_release=False,
                    allowlist_path=allowlist,
                )

    def test_zero_resolved_dependencies_fails_the_gate(self):
        # An empty resolution is silent downstream: no SBOM packages, an OSV
        # loop that never runs, and an advisory status of "ok".
        with self.assertRaises(gate.GateError):
            gate.assert_dependencies_resolved([])

    def test_a_populated_resolution_passes(self):
        gate.assert_dependencies_resolved(["x:y:1.0"])

    def test_an_empty_report_file_fails_the_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            report_dir = Path(tmp)
            for name in gate.REQUIRED_REPORTS:
                (report_dir / name).write_text("content", encoding="utf-8")
            (report_dir / "sbom.spdx.json").write_text("", encoding="utf-8")

            with self.assertRaises(gate.GateError) as caught:
                gate.assert_reports_are_populated(report_dir)

        self.assertIn("sbom.spdx.json", str(caught.exception))

    def test_a_missing_report_file_fails_the_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            report_dir = Path(tmp)
            for name in gate.REQUIRED_REPORTS:
                (report_dir / name).write_text("content", encoding="utf-8")
            (report_dir / "SHA256SUMS").unlink()

            with self.assertRaises(gate.GateError) as caught:
                gate.assert_reports_are_populated(report_dir)

        self.assertIn("SHA256SUMS", str(caught.exception))

    def test_spdx_document_carries_a_namespace_and_describes_every_package(self):
        metadata = {
            "x:y:1.0": {
                "license": "Apache-2.0",
                "source": "https://example.invalid/x.pom",
                "reason": "fixture",
            }
        }
        document = gate.build_spdx(["x:y:1.0"], metadata=metadata)

        self.assertTrue(document["documentNamespace"].startswith("https://"))
        self.assertEqual(
            document["relationships"],
            [
                {
                    "spdxElementId": "SPDXRef-DOCUMENT",
                    "relationshipType": "DESCRIBES",
                    "relatedSpdxElement": document["packages"][0]["SPDXID"],
                }
            ],
        )

    def test_osv_paging_follows_the_next_page_token(self):
        pages = [
            {"vulns": [{"id": "OSV-1"}], "next_page_token": "second"},
            {"vulns": [{"id": "OSV-2"}]},
        ]
        seen = []

        def fetch(query):
            seen.append(query.get("page_token"))
            return pages[len(seen) - 1]

        found = gate.osv_query_all_pages({"package": {"name": "x:y"}}, fetch=fetch)

        self.assertEqual([item["id"] for item in found], ["OSV-1", "OSV-2"])
        self.assertEqual(seen, [None, "second"])

    def test_osv_paging_gives_up_rather_than_looping_forever(self):
        # A server that always returns a token would otherwise pin the gate.
        def fetch(_query):
            return {"vulns": [], "next_page_token": "always"}

        with self.assertRaises(gate.GateError):
            gate.osv_query_all_pages({"package": {"name": "x:y"}}, fetch=fetch)

    def test_a_rate_limited_query_is_retried_and_then_succeeds(self):
        attempts = []

        def urlopen(request, timeout=None):
            attempts.append(1)
            if len(attempts) < 3:
                raise gate.urllib.error.HTTPError(
                    "https://api.osv.dev/v1/query", 429, "Too Many Requests", {}, None
                )
            return self._Response({"vulns": []})

        with patch.object(gate.urllib.request, "urlopen", urlopen):
            with patch.object(gate.time, "sleep", lambda _s: None):
                result = gate.osv_post_query({"package": {"name": "x:y"}})

        self.assertEqual(result, {"vulns": []})
        self.assertEqual(len(attempts), 3)

    def test_a_query_that_stays_rate_limited_is_an_error_not_an_empty_result(self):
        def urlopen(request, timeout=None):
            raise gate.urllib.error.HTTPError(
                "https://api.osv.dev/v1/query", 429, "Too Many Requests", {}, None
            )

        with patch.object(gate.urllib.request, "urlopen", urlopen):
            with patch.object(gate.time, "sleep", lambda _s: None):
                with self.assertRaises(gate.GateError):
                    gate.osv_post_query({"package": {"name": "x:y"}})

    def test_a_client_error_is_not_retried(self):
        attempts = []

        def urlopen(request, timeout=None):
            attempts.append(1)
            raise gate.urllib.error.HTTPError(
                "https://api.osv.dev/v1/query", 400, "Bad Request", {}, None
            )

        with patch.object(gate.urllib.request, "urlopen", urlopen):
            with self.assertRaises(gate.GateError):
                gate.osv_post_query({"package": {"name": "x:y"}})

        self.assertEqual(len(attempts), 1)

    def test_an_unpinned_signing_certificate_is_reported_as_absent(self):
        # apksigner verifying an APK only proves it is signed, not that it is
        # signed with this project's key.
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(gate.load_expected_certificate(Path(tmp)))

    def test_a_pinned_certificate_is_read_with_or_without_colons(self):
        digest = "ab" * 32
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "tools").mkdir()
            (root / gate.CERTIFICATE_PIN).write_text(
                json.dumps({"sha256": ":".join(digest[i : i + 2] for i in range(0, 64, 2))}),
                encoding="utf-8",
            )

            self.assertEqual(gate.load_expected_certificate(root), digest)

    def test_a_malformed_certificate_pin_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "tools").mkdir()
            (root / gate.CERTIFICATE_PIN).write_text('{"sha256": "nope"}', encoding="utf-8")

            with self.assertRaises(gate.GateError):
                gate.load_expected_certificate(root)

    def test_the_certificate_digest_is_read_out_of_apksigner_output(self):
        digest = "cd" * 32
        output = (
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            f"Signer #1 certificate SHA-256 digest: {digest}\n"
        )

        self.assertEqual(gate.apksigner_certificate_sha256(output), digest)
        self.assertIsNone(gate.apksigner_certificate_sha256("no certificate here"))

    def test_advisory_report_preserves_osv_severity(self):
        with patch.object(
            gate.urllib.request,
            "urlopen",
            return_value=self._Response(
                {
                    "vulns": [
                        {
                            "id": "OSV-1",
                            "summary": "fixture",
                            "database_specific": {"severity": "HIGH"},
                        }
                    ]
                }
            ),
        ):
            report = gate.build_advisory_report(["x:y:1.0"], mode="query")

        self.assertEqual(report["status"], "ok")
        self.assertEqual(report["vulnerabilities"][0]["severity"], "HIGH")

    def test_advisory_report_marks_network_error_partial(self):
        with patch.object(gate.urllib.request, "urlopen", side_effect=OSError("offline")):
            report = gate.build_advisory_report(["x:y:1.0"], mode="query")

        self.assertEqual(report["status"], "partial")
        self.assertTrue(report["errors"])

    def test_advisory_policy_rejects_missing_severity(self):
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = Path(tmp) / "allowlist.json"
            allowlist.write_text("[]", encoding="utf-8")
            with self.assertRaises(gate.GateError):
                gate.assert_advisory_policy(
                    {
                        "status": "ok",
                        "vulnerabilities": [{"id": "OSV-1", "dependency": "x:y:1.0", "severity": "UNKNOWN"}],
                    },
                    mode="query",
                    allow_unsigned_release=False,
                    allowlist_path=allowlist,
                )

    def test_advisory_policy_requires_exact_unexpired_high_allowlist(self):
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = Path(tmp) / "allowlist.json"
            allowlist.write_text(
                json.dumps(
                    [{
                        "advisory_id": "OSV-1",
                        "dependency": "x:y:1.0",
                        "reason": "Not reachable from the offline app surface.",
                        "reviewer": "release-team",
                        "expires": "2026-12-31",
                    }]
                ),
                encoding="utf-8",
            )
            report = {
                "status": "ok",
                "vulnerabilities": [{"id": "OSV-1", "dependency": "x:y:1.0", "severity": "HIGH"}],
            }

            gate.assert_advisory_policy(
                report,
                mode="query",
                allow_unsigned_release=False,
                allowlist_path=allowlist,
                today=date(2026, 8, 10),
            )

    def test_advisory_policy_rejects_an_expired_allowlist_entry(self):
        # This case and the one below used to be a single test whose only
        # assertion was "something raised". The expired entry raises during
        # loading, so the CRITICAL branch never ran and mutating it to pass
        # kept the test green. Each now asserts its own message.
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = self._write_allowlist(tmp, expires="2026-08-09")
            with self.assertRaisesRegex(gate.GateError, "expired on 2026-08-09"):
                gate.assert_advisory_policy(
                    {
                        "status": "ok",
                        "vulnerabilities": [{"id": "OSV-1", "dependency": "x:y:1.0", "severity": "HIGH"}],
                    },
                    mode="query",
                    allow_unsigned_release=False,
                    allowlist_path=allowlist,
                    today=date(2026, 8, 10),
                )

    def test_advisory_policy_rejects_a_critical_nobody_signed_off(self):
        # The allowlist is live and covers a different advisory, so loading
        # succeeds and the severity check is what has to refuse.
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = self._write_allowlist(tmp, expires="2026-12-31")
            with self.assertRaisesRegex(gate.GateError, "OSV-2"):
                gate.assert_advisory_policy(
                    {
                        "status": "ok",
                        "vulnerabilities": [{"id": "OSV-2", "dependency": "x:y:1.0", "severity": "CRITICAL"}],
                    },
                    mode="query",
                    allow_unsigned_release=False,
                    allowlist_path=allowlist,
                    today=date(2026, 8, 10),
                )

    def test_an_allowlist_entry_that_is_live_on_the_injected_date_is_accepted(self):
        # Positive control for the expiry case, and the reason the date has to
        # be injected all the way down: read from the wall clock, this starts
        # failing on 2027-01-01 whatever the test asks for.
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = self._write_allowlist(tmp, expires="2026-08-11")

            gate.assert_advisory_policy(
                {
                    "status": "ok",
                    "vulnerabilities": [{"id": "OSV-1", "dependency": "x:y:1.0", "severity": "HIGH"}],
                },
                mode="query",
                allow_unsigned_release=False,
                allowlist_path=allowlist,
                today=date(2026, 8, 10),
            )

    def _write_allowlist(self, tmp, expires):
        allowlist = Path(tmp) / "allowlist.json"
        allowlist.write_text(
            json.dumps(
                [{
                    "advisory_id": "OSV-1",
                    "dependency": "x:y:1.0",
                    "reason": "Not reachable from the offline app surface.",
                    "reviewer": "release-team",
                    "expires": expires,
                }]
            ),
            encoding="utf-8",
        )
        return allowlist

    def test_offline_advisory_mode_requires_unsigned_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            allowlist = Path(tmp) / "allowlist.json"
            allowlist.write_text("[]", encoding="utf-8")
            with self.assertRaises(gate.GateError):
                gate.assert_advisory_policy(
                    {"status": "offline-review-required", "vulnerabilities": []},
                    mode="offline",
                    allow_unsigned_release=False,
                    allowlist_path=allowlist,
                )
            gate.assert_advisory_policy(
                {"status": "offline-review-required", "vulnerabilities": []},
                mode="offline",
                allow_unsigned_release=True,
                allowlist_path=allowlist,
            )

    def test_run_times_out_cleanly(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(gate.GateError, "timed out after 1 seconds"):
                gate.run(
                    [sys.executable, "-c", "import time; time.sleep(2)"],
                    Path(tmp),
                    timeout_seconds=1,
                )


if __name__ == "__main__":
    unittest.main()
