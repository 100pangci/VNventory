#!/usr/bin/env python3
"""Prepend GitHub-generated release notes to the repository changelog."""

import argparse
from datetime import date, datetime, timezone
from pathlib import Path
import re


VERSION_PATTERN = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)")
CHANGELOG_HEADING = "# Changelog"


def update_changelog(changelog: Path, version: str, release_date: str, notes: str) -> bool:
    if VERSION_PATTERN.fullmatch(version) is None:
        raise ValueError("version must use MAJOR.MINOR.PATCH")
    date.fromisoformat(release_date)
    if not notes.strip():
        raise ValueError("release notes must not be empty")

    existing = changelog.read_text(encoding="utf-8") if changelog.exists() else ""
    if re.search(rf"(?m)^## \[{re.escape(version)}\](?:\s|$)", existing):
        return False

    if existing.startswith(CHANGELOG_HEADING):
        body = existing[len(CHANGELOG_HEADING):].lstrip("\n")
    else:
        body = existing.lstrip("\n")

    entry = f"## [{version}] - {release_date}\n\n{notes.strip()}"
    contents = f"{CHANGELOG_HEADING}\n\n{entry}"
    if body:
        contents += f"\n\n{body.rstrip()}"
    changelog.write_text(contents + "\n", encoding="utf-8")
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="release version without the v prefix")
    parser.add_argument("--date", default=datetime.now(timezone.utc).date().isoformat())
    parser.add_argument("--notes-file", required=True, type=Path)
    parser.add_argument(
        "--changelog",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "CHANGELOG.md",
    )
    args = parser.parse_args()

    try:
        notes = args.notes_file.read_text(encoding="utf-8")
        updated = update_changelog(args.changelog, args.version, args.date, notes)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    if updated:
        print(f"Updated {args.changelog} for v{args.version}.")
    else:
        print(f"{args.changelog} already contains v{args.version}; no change.")


if __name__ == "__main__":
    main()
