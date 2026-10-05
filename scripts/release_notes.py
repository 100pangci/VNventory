#!/usr/bin/env python3
"""Use the reviewed version section in CHANGELOG.md, never commit subjects."""
import argparse
from datetime import date
from pathlib import Path
import re

from update_changelog import VERSION_PATTERN


def release_notes(contents: str, version: str) -> str:
    if VERSION_PATTERN.fullmatch(version) is None:
        raise ValueError("version must use MAJOR.MINOR.PATCH")
    headings = list(re.finditer(r"(?m)^## \[([^\]\n]+)\]([^\n]*)$", contents))
    matching = [(index, heading) for index, heading in enumerate(headings) if heading[1] == version]
    if len(matching) != 1:
        raise ValueError(f"CHANGELOG.md must contain exactly one section for {version}")
    index, heading = matching[0]
    suffix = re.fullmatch(r" - (\d{4}-\d{2}-\d{2})", heading[2])
    if suffix is None:
        raise ValueError("version heading must use ## [MAJOR.MINOR.PATCH] - YYYY-MM-DD")
    date.fromisoformat(suffix[1])
    end = headings[index + 1].start() if index + 1 < len(headings) else len(contents)
    notes = contents[heading.end():end].strip()
    if not notes:
        raise ValueError("release notes must not be empty")
    if re.search(r"(?m)^#{1,2} ", notes):
        raise ValueError("release subsections must use ### or deeper headings")
    return notes + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--changelog", type=Path, default=Path(__file__).resolve().parents[1] / "CHANGELOG.md")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        notes = release_notes(args.changelog.read_text(encoding="utf-8"), args.version)
        args.output.write_text(notes, encoding="utf-8")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
