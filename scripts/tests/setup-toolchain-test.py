"""Offline bootstrap regressions; mock tools avoid installing packages or running Java."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        parent = ROOT / "toolchain" / "bootstrap-tests"
        parent.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="with spaces-", dir=parent)
        self.addCleanup(self.temporary.cleanup)
        self.project = Path(self.temporary.name)
        (self.project / "scripts").mkdir()
        shutil.copy2(ROOT / "scripts/setup-android-env.sh", self.project / "scripts")
        self.jdk = self.project / "mock jdk"
        self.executable(self.jdk / "bin/java", '#!/bin/sh\necho \'openjdk version "21.0.1"\' >&2\n')
        sdk = self.project / "toolchain/android-sdk"
        self.executable(sdk / "cmdline-tools/latest/bin/sdkmanager", "#!/bin/sh\nexit 77\n")
        self.executable(sdk / "platform-tools/adb", "#!/bin/sh\nexit 0\n")
        self.executable(sdk / "build-tools/37.0.0/aapt2", "#!/bin/sh\nexit 0\n")
        platform = sdk / "platforms/android-37.2/android.jar"
        platform.parent.mkdir(parents=True)
        platform.touch()
        self.executable(self.project / "toolchain/gradle-9.8.0/bin/gradle", "#!/bin/sh\nexit 0\n")
        self.executable(self.project / "gradlew", """#!/usr/bin/env python3
import os, json, sys
from pathlib import Path
with (Path(__file__).parent / 'invocations.jsonl').open('a') as out:
    out.write(json.dumps({'args':sys.argv[1:], 'sdk':os.environ['ANDROID_HOME'],
                         'gradle':os.environ['GRADLE_USER_HOME'],
                         'android':os.environ['ANDROID_USER_HOME']}) + '\\n')
""")

    @staticmethod
    def executable(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        path.chmod(0o755)

    def run_setup(self, discover=False):
        env = {k: v for k, v in os.environ.items() if k not in (
            "ANDROID_HOME", "ANDROID_SDK_ROOT", "GRADLE_USER_HOME", "ANDROID_USER_HOME", "JAVA_HOME", "VNVENTORY_JDK"
        )}
        if discover:
            env["HOME"] = str(self.project / "mock home")
        else:
            env["VNVENTORY_JDK"] = str(self.jdk)
        subprocess.run(["bash", str(self.project / "scripts/setup-android-env.sh")], env=env,
                       cwd=ROOT, capture_output=True, text=True, check=True)

    def test_fresh_paths_spaces_idempotence_and_cleanup(self):
        self.run_setup()
        properties = (self.project / "local.properties").read_text()
        self.assertIn("sdk.dir=", properties)
        self.assertIn("\\ ", properties)
        self.run_setup()
        self.assertEqual(properties, (self.project / "local.properties").read_text())
        records = [json.loads(line) for line in (self.project / "invocations.jsonl").read_text().splitlines()]
        self.assertEqual([r["args"] for r in records], [["--no-daemon", "--version"], ["--stop"]] * 2)
        for record in records:
            for key in ("sdk", "gradle", "android"):
                self.assertTrue(record[key].startswith(str(self.project / "toolchain")))

    def test_existing_sdk_and_jdk_config_not_overwritten(self):
        local = self.project / "local.properties"
        local.write_text("# user's settings\nsdk.dir=/custom/sdk\n")
        config = self.project / "toolchain/gradle-home/gradle.properties"
        config.parent.mkdir(parents=True)
        config.write_text("org.gradle.java.home=/custom/jdk\norg.gradle.workers.max=1\n")
        self.run_setup()
        self.assertEqual("# user's settings\nsdk.dir=/custom/sdk\n", local.read_text())
        self.assertEqual("org.gradle.java.home=/custom/jdk\norg.gradle.workers.max=1\n", config.read_text())

    def test_discovers_jdk_under_software_lib_with_spaces_in_home(self):
        jdk = self.project / "mock home/Software/lib/jdk-21.0.12.1+1"
        shutil.copytree(self.jdk, jdk)
        self.run_setup(discover=True)
        properties = (self.project / "toolchain/gradle-home/gradle.properties").read_text()
        self.assertIn("org.gradle.java.home=" + str(jdk).replace(" ", "\\ "), properties)


if __name__ == "__main__":
    unittest.main()
