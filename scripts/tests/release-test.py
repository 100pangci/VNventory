"""Offline tests for release versioning and publication safeguards."""

from pathlib import Path
import sys
import json
import re
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True
from release import missing_signing_secrets, prepare_release_asset, validate_tag, version_code, validate_apk_metadata


ROOT = Path(__file__).resolve().parents[2]


class ReleaseTest(unittest.TestCase):
    def test_tag_and_android_version_code_use_one_version(self):
        self.assertEqual(("1.12.3", 1_012_003), validate_tag("v1.12.3"))
        self.assertEqual(1_012_003, version_code("1.12.3"))

    def test_rejects_malformed_or_android_incompatible_tags(self):
        for tag in ("1.2.3", "v01.2.3", "v1.2", "v1.1000.0", "v0.0.0", "v2148.0.0"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate_tag(tag)

    def test_release_requires_every_signing_secret_but_keystore_type_is_optional(self):
        self.assertEqual(
            ["ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"],
            missing_signing_secrets({}),
        )
        values = {
            "ANDROID_KEYSTORE_BASE64": "encoded",
            "ANDROID_KEYSTORE_PASSWORD": "store-password",
            "ANDROID_KEY_ALIAS": "release",
            "ANDROID_KEY_PASSWORD": "key-password",
        }
        self.assertEqual([], missing_signing_secrets(values))
        for name in values:
            for empty in (None, "", "   "):
                self.assertEqual([name], missing_signing_secrets(values | {name: empty}))

    def test_asset_requires_nonempty_apk_and_uses_tag_version_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "app-release.apk"
            output = root / "release"
            with self.assertRaises(ValueError):
                prepare_release_asset(apk, "1.2.3", output)
            apk.touch()
            with self.assertRaises(ValueError):
                prepare_release_asset(apk, "1.2.3", output)
            apk.write_bytes(b"signed-apk")
            target = prepare_release_asset(apk, "1.2.3", output)
            self.assertEqual("VNventory-v1.2.3.apk", target.name)
            self.assertEqual(b"signed-apk", target.read_bytes())

    def test_built_apk_version_and_filename_must_match_tag(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = root / "output-metadata.json"
            apk = root / "app-release.apk"
            entry = {"versionName": "1.2.3", "versionCode": 1_002_003, "outputFile": apk.name}
            metadata.write_text(json.dumps({"elements": [entry]}))
            validate_apk_metadata(metadata, apk, "1.2.3")
            for invalid in (entry | {"versionName": "1.2.4"}, entry | {"versionCode": 123}, entry | {"outputFile": "app-release-unsigned.apk"}):
                metadata.write_text(json.dumps({"elements": [invalid]}))
                with self.assertRaises(ValueError):
                    validate_apk_metadata(metadata, apk, "1.2.3")

    def test_local_version_uses_the_same_version_code_semantics(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        version = re.search(r'getOrElse\("(\d+\.\d+\.\d+)"\)', gradle).group(1)
        self.assertEqual((version, version_code(version)), validate_tag("v" + version))
        self.assertNotIn('"0.1.0"', gradle)

    def test_workflow_verifies_and_publishes_before_updating_changelog(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("scripts/release.py validate-tag", workflow)
        self.assertIn("scripts/release.py validate-signing", workflow)
        self.assertIn("apksigner\" verify", workflow)
        self.assertIn("-PvnventoryVersionName=", workflow)
        self.assertIn("-PvnventoryVersionCode=", workflow)
        self.assertLess(workflow.index("Publish GitHub Release"), workflow.index("Update CHANGELOG.md"))
        self.assertIn("continue-on-error: true", workflow.split("Update CHANGELOG.md", 1)[1])
        self.assertIn("umask 077", workflow)
        self.assertIn("Remove temporary signing files", workflow)
        self.assertIn("fail_on_unmatched_files: true", workflow)
        self.assertNotIn("app-release-unsigned.apk", workflow)
        self.assertIn("paths-ignore:", (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
