import json
import unittest

import driver_report_matrix as drm


RAW_REPORT = """OpenLumen driver report v2
===
Generated: 2026-07-01T00:00:00Z

App
---
Version: 0.6.2 (code 10)
Package: com.openlumen
Build type: release

Device
---
Manufacturer: Google
Brand: google
Model: Pixel 8
Device: shiba
Product: shiba
Hardware: shiba
SoC: Google Tensor G3
Android: 15 (API 35)
Fingerprint: google/shiba/shiba:15/AP4A.250105.002/13174299:user/release-keys

Engine probes
---
- COLOR_DISPLAY_MANAGER (rank 100, root=false): AVAILABLE
- SURFACE_FLINGER (rank 90, root=true): not available
- KCAL (rank 70, root=true): not available
- OVERLAY (rank 10, root=false): AVAILABLE
"""


ISSUE_BODY = """### OpenLumen version

0.6.2

### Device

Pixel 8 (shiba)

### Android version + fingerprint

15 / google/shiba/shiba:15/AP4A.250105.002/13174299:user/release-keys

### OEM software / ROM

Stock Pixel

### Root status

none

### Engine

Overlay

### Status

Works with caveats

### Driver report

{report}

### Additional notes

Installer touch block reproduced.
"""


class DriverReportMatrixTest(unittest.TestCase):
    def test_raw_driver_report_drafts_review_row(self):
        parsed = drm.parse_input(RAW_REPORT)
        output = drm.render_suggestion(parsed)

        self.assertIn("| Pixel 8 | 15 (API 35) | Google | review | ? | ? | ? | ? | v0.6.2 |", output)
        self.assertIn("CDM AVAILABLE", output)
        self.assertIn("engine result cells intentionally left as ?", output)

    def test_issue_json_preserves_reported_status_without_marking_pass_fail(self):
        payload = {
            "number": 42,
            "url": "https://github.com/SysAdminDoc/OpenLumen/issues/42",
            "body": ISSUE_BODY.format(report=RAW_REPORT),
        }

        parsed = drm.parse_input(json.dumps(payload))
        output = drm.render_suggestion(parsed)

        self.assertIn("review reported Overlay: Works with caveats", output)
        self.assertIn("| Pixel 8 (shiba) | 15 / google/shiba/shiba:15", output)
        self.assertIn("| ? | ? | ? | ? |", output)
        self.assertNotIn("| ✅ |", output)
        self.assertNotIn("| ❌ |", output)

    def test_malformed_input_has_actionable_errors(self):
        with self.assertRaises(drm.ParseError) as caught:
            drm.parse_input("not a driver report")

        self.assertIn("OpenLumen driver report", "\n".join(caught.exception.errors))

    def test_issue_json_missing_status_is_rejected(self):
        payload = {"body": "### Device\n\nPixel 8\n\n### Driver report\n\n" + RAW_REPORT}

        with self.assertRaises(drm.ParseError) as caught:
            drm.parse_input(json.dumps(payload))

        self.assertIn("reported engine status", "\n".join(caught.exception.errors))


if __name__ == "__main__":
    unittest.main()
