"""Offline guards for app-owned copy and resource formatting. No dependencies or downloads."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java"
# Skip Kotlin comments; include quoted and raw string literals. Test/preview samples are deliberately excluded.
TOKENS = re.compile(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"', re.DOTALL)
CJK = re.compile(r"[\u3400-\u9fff\u3040-\u30ff]")


class StringResourcesTest(unittest.TestCase):
    def test_production_copy_is_not_embedded_in_kotlin(self):
        found = []
        for path in SOURCE.rglob("*.kt"):
            source = path.read_text()
            for token in TOKENS.finditer(source):
                if token.group().startswith('"') and CJK.search(token.group()):
                    found.append(f"{path.relative_to(ROOT)}:{source.count(chr(10), 0, token.start()) + 1}")
            if "/ui/" in str(path):
                stripped = TOKENS.sub(lambda token: "" if token.group().startswith("/") else token.group(), source)
                self.assertIsNone(re.search(r'\b(?:Text|Tag|LabeledRow)\(\s*"[^"\n]+"', stripped), str(path))
        self.assertEqual([], found, "Move app copy into string resources: " + ", ".join(found))

    def test_resource_names_are_unique_and_format_parameters_are_positional(self):
        names = set()
        for path in sorted((ROOT / "app/src/main/res/values").glob("*.xml")):
            for entry in ET.parse(path).getroot():
                if entry.tag not in ("string", "plurals"):
                    continue
                name = entry.attrib["name"]
                self.assertNotIn(name, names, str(path))
                names.add(name)
                values = list(entry) if entry.tag == "plurals" else [entry]
                for value in values:
                    for parameter in re.finditer(r"%(?!%)(?:[1-9][0-9]*\$)?[sd]", "".join(value.itertext())):
                        self.assertIn("$", parameter.group(), name)
        self.assertGreater(len(names), 100)

    def test_domain_stays_independent_of_android_and_resources(self):
        for path in (SOURCE / "com/vnventory/app/domain").rglob("*.kt"):
            source = path.read_text()
            self.assertNotRegex(source, r"(?m)^import (?:android\.|androidx\.|com\.vnventory\.app\.R\b)", str(path))

    def test_all_kotlin_string_and_plural_references_exist(self):
        resources = {"string": set(), "plurals": set()}
        for path in (ROOT / "app/src/main/res/values").glob("*.xml"):
            for entry in ET.parse(path).getroot():
                if entry.tag in resources:
                    resources[entry.tag].add(entry.attrib["name"])
        for path in (ROOT / "app/src").rglob("*.kt"):
            for kind, name in re.findall(r"\bR\.(string|plurals)\.([A-Za-z0-9_]+)", path.read_text()):
                self.assertIn(name, resources[kind], str(path))


if __name__ == "__main__":
    unittest.main()
