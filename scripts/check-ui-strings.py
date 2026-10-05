#!/usr/bin/env python3
"""Conservative source guard, not a Kotlin parser. Samples/assertions are not translations.

Exact (relative path, literal) exceptions require an explanation in ALLOWLIST.
Comments, SQL/IDs/logs, animation labels and testTag are not UI copy.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST = {}  # ("app/src/main/java/...kt", '"literal"'): "reason"
TOKENS = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"', re.S)
CJK = re.compile(r"[\u3400-\u9fff\u3040-\u30ff]")
HIGH_RISK = re.compile(
    r'\b(?:Text|Tag|LabeledRow|Snackbar)\(\s*(?:text\s*=\s*)?(?P<text>"(?:\\.|[^"\\])+")'
    r'|\b(?:contentDescription|stateDescription)\s*=\s*(?P<accessibility>"(?:\\.|[^"\\])+")'
    r'|\bshowSnackbar\(\s*(?:message\s*=\s*)?(?P<snackbar>"(?:\\.|[^"\\])+")'
    r'|\bToast\.makeText\([^,]+,\s*(?P<toast>"(?:\\.|[^"\\])+")', re.S)


def violations(source, relative):
    # Keep positions stable, including line numbers.
    stripped = TOKENS.sub(lambda m: re.sub(r"[^\n]", " ", m[0]) if m[0].startswith("/") else m[0], source)
    found = {}
    for match in TOKENS.finditer(stripped):
        if match[0].startswith('"') and CJK.search(match[0]):
            found[match.start()] = match[0]
    for match in HIGH_RISK.finditer(stripped):
        group = next(name for name, value in match.groupdict().items() if value is not None)
        found[match.start(group)] = match[group]
    return [f"{relative}:{source.count(chr(10), 0, pos) + 1}: {literal}"
            for pos, literal in sorted(found.items()) if (relative, literal) not in ALLOWLIST]


def main():
    errors = []
    for path in sorted((ROOT / "app/src/main/java").rglob("*.kt")):
        errors.extend(violations(path.read_text(), path.relative_to(ROOT).as_posix()))
    if errors:
        print("Move user-visible copy to strings/plurals or MessageKey:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print("UI string guard passed (production Kotlin; test/preview fixtures excluded).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
