"""Tests for automatic changelog generation."""

from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True
from update_changelog import update_changelog
from release_notes import release_notes


class ChangelogTest(unittest.TestCase):
    def test_reviewed_release_section_preserves_subsections_and_excludes_other_versions(self):
        contents = "# Changelog\n\n## [1.0.2] - 2026-10-05\n\n### 新增\n\n- 双标题。\n\n### 修复\n\n- 修复列表。\n\n## [1.0.1] - 2026-10-04\n\nOld notes.\n"
        self.assertEqual("### 新增\n\n- 双标题。\n\n### 修复\n\n- 修复列表。\n", release_notes(contents, "1.0.2"))

    def test_reviewed_notes_reject_missing_duplicate_empty_or_invalid_headings(self):
        for contents in ["# Changelog", "## [1.0.2] - 2026-10-05\n",
                         "## [1.0.2]\nNotes", "## [1.0.2] - 2026-02-30\nNotes",
                         "## [1.0.2] - 2026-10-05\n## Changes\nNotes",
                         "## [1.0.2] - 2026-10-05\nNotes\n## [1.0.2] - 2026-10-05\nNotes"]:
            with self.subTest(contents=contents), self.assertRaises(ValueError):
                release_notes(contents, "1.0.2")
        with self.assertRaises(ValueError):
            release_notes("", "v1.0.2")

    def test_current_release_notes_are_reviewed_and_do_not_list_commits(self):
        root = Path(__file__).resolve().parents[2]
        notes = release_notes((root / "CHANGELOG.md").read_text(encoding="utf-8"), "1.0.2")
        self.assertIn("### 数据兼容与验证", notes)
        self.assertIn("compare/v1.0.1...v1.0.2", notes)
        self.assertNotRegex(notes, r"`[0-9a-f]{7,40}`")

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
