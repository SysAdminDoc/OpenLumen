import tempfile
import unittest
from pathlib import Path

import project_context_check as check


CONTEXT = """\
## Current contracts (machine-checked)

- **Current main version**: `v0.6.6` (from `app/build.gradle.kts`).
- **Current preference schema**: `2` (from `Preferences.CURRENT_SCHEMA_VERSION`).
- **Auto driver order**: root drivers → `ColorDisplayManager` → `Overlay`.
- **Auto no-driver behavior**: when no probed driver is available, Auto
  selects no driver and the service/Driver report surfaces unavailable state.
"""

DRIVER_SOURCE = """\
class DriverProbe {
    internal fun pickBestFrom(probes: List<Probe>): ColorEngine? =
        bestAvailableKind(probes)?.let { kind -> engines.firstOrNull { it.kind == kind } }

    fun bestAvailableKind(probes: List<Probe>): EngineKind? =
        probes.firstOrNull { it.available && it.engine.kind.requiresRoot }?.engine?.kind
            ?: probes.firstOrNull {
                it.available && it.engine.kind == EngineKind.COLOR_DISPLAY_MANAGER
            }?.engine?.kind
            ?: probes.firstOrNull { it.available && it.engine.kind == EngineKind.OVERLAY }
                ?.engine?.kind
}
"""


class ProjectContextCheckTest(unittest.TestCase):
    def test_current_repository_context_matches_sources(self):
        root = Path(__file__).resolve().parents[1]

        self.assertEqual(check.validate_project_context(root), [])

    def test_stale_release_and_schema_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(
                root / "PROJECT_CONTEXT.md",
                CONTEXT.replace("0.6.6", "0.6.5").replace("`2`", "`1`"),
            )
            write(root / "app/build.gradle.kts", 'versionName = "0.6.6"\n')
            write(
                root / "core-prefs/src/main/java/com/openlumen/prefs/Preferences.kt",
                "const val CURRENT_SCHEMA_VERSION: Int = 2\n",
            )
            write(root / check.DRIVER_SOURCE, DRIVER_SOURCE)

            errors = check.validate_project_context(root)

        self.assertTrue(any("main version" in error for error in errors))
        self.assertTrue(any("context schema" in error for error in errors))

    def test_missing_local_context_is_optional(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(check.validate_project_context(Path(tmp)), [])


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
