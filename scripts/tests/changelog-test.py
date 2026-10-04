"""Tests for automatic changelog generation."""

from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True
from update_changelog import update_changelog


class ChangelogTest(unittest.TestCase):
    def test_prepends_entry_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "CHANGELOG.md"
            path.write_text("# Changelog\n\n## [0.1.0] - 2026-10-01\n\nOld notes.\n", encoding="utf-8")
            notes = "## What's Changed\n\n- Added a shelf.\n"

            self.assertTrue(update_changelog(path, "1.0.0", "2026-10-04", notes))
            content = path.read_text(encoding="utf-8")
            self.assertTrue(content.startswith("# Changelog\n\n## [1.0.0] - 2026-10-04"))
            self.assertIn("## [0.1.0] - 2026-10-01", content)
            self.assertFalse(update_changelog(path, "1.0.0", "2026-10-04", notes))
            self.assertEqual(content, path.read_text(encoding="utf-8"))

    def test_creates_changelog_when_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "CHANGELOG.md"
            self.assertTrue(update_changelog(path, "1.2.3", "2026-10-04", "Initial notes."))
            self.assertIn("Initial notes.", path.read_text(encoding="utf-8"))

    def test_rejects_invalid_version_and_empty_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "CHANGELOG.md"
            with self.assertRaises(ValueError):
                update_changelog(path, "v1.2.3", "2026-10-04", "Notes")
            with self.assertRaises(ValueError):
                update_changelog(path, "1.2.3", "2026-10-04", "  \n")


if __name__ == "__main__":
    unittest.main()
