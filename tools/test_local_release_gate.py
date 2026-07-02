import json
import sys
import tempfile
import unittest
from pathlib import Path

import local_release_gate as gate


DEPENDENCIES = """
releaseRuntimeClasspath - Runtime classpath of /main.
+--- androidx.core:core-ktx:1.19.0
+--- com.google.firebase:firebase-analytics:23.0.0
\\--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0 -> 1.9.0
"""


class LocalReleaseGateTest(unittest.TestCase):
    def test_parse_gradle_dependencies_uses_resolved_versions(self):
        deps = gate.parse_gradle_dependencies(DEPENDENCIES)

        self.assertIn("androidx.core:core-ktx:1.19.0", deps)
        self.assertIn("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0", deps)

    def test_banned_dependency_patterns_fail(self):
        with self.assertRaises(gate.GateError):
            gate.assert_no_banned_dependencies(gate.parse_gradle_dependencies(DEPENDENCIES))

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

    def test_spdx_report_is_json_serializable(self):
        report = gate.build_spdx(["androidx.core:core-ktx:1.19.0"])
        encoded = json.dumps(report)

        self.assertIn("pkg:maven/androidx.core/core-ktx@1.19.0", encoded)

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
