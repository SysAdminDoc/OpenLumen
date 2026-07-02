import tempfile
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
