from pathlib import Path
import runpy
import unittest

guard = runpy.run_path(str(Path(__file__).resolve().parents[1] / "check-ui-strings.py"))


class GuardTest(unittest.TestCase):
    def test_high_risk_copy(self):
        for source in ['Text("Save")', 'Text(text = "Save")', 'contentDescription = "Close"',
                       'stateDescription = "Ready"', 'host.showSnackbar("Saved")',
                       'Toast.makeText(context, "Saved", 0)', 'val hint = "请重试"']:
            self.assertTrue(guard["violations"](source, "sample.kt"), source)

    def test_technical_constants_and_comments(self):
        source = '\n'.join(['// Text("示例")', '/* contentDescription = "示例" */',
                            'Modifier.testTag("card")', 'animate(label = "motion")',
                            'val id = "v17"', 'val sql = "SELECT * FROM owned_copy"',
                            'Text(stringResource(R.string.action_save))'])
        self.assertEqual([], guard["violations"](source, "sample.kt"))

    def test_exact_allowlist(self):
        guard["ALLOWLIST"][("sample.kt", '"Brand"')] = "Untranslated brand"
        self.assertEqual([], guard["violations"]('Text("Brand")', "sample.kt"))
        self.assertTrue(guard["violations"]('Text("Brand")', "other.kt"))


if __name__ == "__main__":
    unittest.main()
