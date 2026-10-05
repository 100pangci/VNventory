"""Offline checks that committed Android brand assets stay derived from the SVG."""

from pathlib import Path
import importlib.util
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CONVERTER_PATH = ROOT / "scripts/convert-branding.py"
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("convert_branding", CONVERTER_PATH)
convert_branding = importlib.util.module_from_spec(spec)
spec.loader.exec_module(convert_branding)
ANDROID = "{http://schemas.android.com/apk/res/android}"


class BrandingTest(unittest.TestCase):
    def test_monochrome_holes_are_read_from_the_source_not_duplicate_geometry(self):
        converter = convert_branding.Converter()
        before = converter.monochrome()
        converter.ids["tab-mark-face"].set("d", "M1 1L2 1L2 2Z")
        self.assertNotEqual(before, converter.monochrome())
        self.assertEqual("M1,1L2,2L2,1Z", convert_branding.reverse_closed_path("M1 1L2 1L2 2Z"))
        with self.assertRaises(ValueError):
            convert_branding.reverse_closed_path("M1 1C2 2 3 3 4 4Z")

    def test_single_active_svg_generates_all_committed_vectors_without_bitmaps(self):
        self.assertEqual(ROOT / "assets/branding/vnventory.svg", convert_branding.SOURCE)
        source = ET.parse(convert_branding.SOURCE).getroot()
        self.assertFalse(any(convert_branding.tag(node) == "image" for node in source.iter()))
        self.assertFalse(any(value.startswith("data:") for node in source.iter() for value in node.attrib.values()))

        generated = convert_branding.Converter().files()
        self.assertEqual(
            {
                "ic_launcher_foreground.xml",
                "ic_launcher_foreground_safe.xml",
                "ic_launcher_background.xml",
                "vnventory_logo_background.xml",
                "ic_launcher_monochrome.xml",
                "vnventory_mark.xml",
            },
            set(generated),
        )
        for name, expected in generated.items():
            with self.subTest(drawable=name):
                actual = (convert_branding.DRAWABLES / name).read_text(encoding="utf-8")
                self.assertEqual(expected, actual)
                self.assertNotIn("<bitmap", actual)

    def test_adaptive_icons_reference_generated_foreground_background_and_monochrome(self):
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            with self.subTest(icon=name):
                root = ET.parse(ROOT / "app/src/main/res/mipmap-anydpi" / name).getroot()
                drawables = {child.tag.rsplit("}", 1)[-1]: child.get(ANDROID + "drawable") for child in root}
                self.assertEqual("@drawable/ic_launcher_background", drawables.get("background"))
                self.assertEqual("@drawable/ic_launcher_foreground_safe", drawables.get("foreground"))
                self.assertEqual("@drawable/ic_launcher_monochrome", drawables.get("monochrome"))

    def test_launcher_foregrounds_use_matching_uniform_insets(self):
        generated = convert_branding.Converter().files()
        for name in ("ic_launcher_foreground_safe.xml", "ic_launcher_monochrome.xml"):
            root = ET.fromstring(generated[name])
            inset = root.find("group/group")
            self.assertEqual("0.68", inset.get(ANDROID + "scaleX"))
            self.assertEqual(inset.get(ANDROID + "scaleX"), inset.get(ANDROID + "scaleY"))
            self.assertEqual("512", inset.get(ANDROID + "pivotX"))
            self.assertEqual("512", inset.get(ANDROID + "pivotY"))


if __name__ == "__main__":
    unittest.main()
