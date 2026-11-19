# SPDX-License-Identifier: MIT
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile

TOOLS = pathlib.Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


artifact = load("release_artifact")

PERMISSIONS = (b"android.permission.INTERNET" + b"android.permission.ACCESS_NETWORK_STATE" +
               b"android.permission.REQUEST_INSTALL_PACKAGES" +
               b"android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION")


def apk(path, marks, network=False, copy=()):
    """A zip shaped like an APK: a binary manifest, the descriptors a dex table would hold, and
    optionally a resource table carrying some copy."""
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00" +
                   (PERMISSIONS if network else b"android.permission.POST_NOTIFICATIONS"))
        z.writestr("classes.dex", b"Ldev/nightfall/MainActivity;" + b"".join(marks))
        if copy: z.writestr("resources.arsc", b"\x02\x00\x0c\x00" + b"".join(copy))
    return path


def run_check(path):
    return subprocess.run(
        [sys.executable, str(TOOLS / "check_release.py"), "--github-apk", str(path)],
        capture_output=True, text=True)


class DiagnosticsPackageTest(unittest.TestCase):
    def test_the_entry_points_are_read_from_the_directory(self):
        """Every file in the diagnostics package is covered, including one no list ever named."""
        groups = artifact.diagnostics_entry_points()
        written = sorted(path.stem for path in
                         (ROOT / "app/src/github/java/dev/nightfall/diagnostics").glob("*.kt"))
        self.assertEqual(sorted(groups), written)
        for name in ("DiagnosticsContract", "DiagnosticsJournal", "DiagnosticsSurface"):
            self.assertIn(name, groups)
        for name, markers in groups.items():
            self.assertTrue(markers, name)
            for marker in markers:
                self.assertTrue(marker.startswith("dev/nightfall/diagnostics/"), marker)
            self.assertIn("dev/nightfall/diagnostics/" + name, markers)

    def test_the_offline_claim_finds_a_diagnostics_class_no_list_named(self):
        """A class added to the package is caught without anybody extending a list first."""
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "diagnostics.apk",
                       [b"Ldev/nightfall/diagnostics/JournalStore;"])
            violation = artifact.fdroid_violation(path)
            self.assertIsNotNone(violation)
            self.assertIn("diagnostics", violation)
            self.assertIn("JournalStore", violation)

    def test_an_apk_with_neither_package_is_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "clean.apk", [b"Ldev/nightfall/AudioHost;"])
            self.assertIsNone(artifact.fdroid_violation(path))

    def test_the_stub_the_fdroid_channel_must_answer_with_is_clean(self):
        """`app/src/fdroid` provides the same three names, so that one type has to be allowed."""
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "fdroid.apk",
                       [b"Ldev/nightfall/diagnostics/DiagnosticsSurface;"])
            self.assertIsNone(artifact.fdroid_violation(path))

    def test_a_nested_diagnostics_class_is_caught(self):
        """A class nobody named, inside a class that is allowed, is still a leak."""
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "nested.apk",
                       [b"Ldev/nightfall/diagnostics/DiagnosticsSurface;",
                        b"Ldev/nightfall/diagnostics/DiagnosticsSurface$Report;"])
            self.assertIn("DiagnosticsSurface$Report", artifact.fdroid_violation(path))

    def test_a_prefix_is_not_a_match(self):
        """`Diagnostics` must not be found just because `DiagnosticsSurface` is there."""
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "prefix.apk",
                       [b"Ldev/nightfall/diagnostics/DiagnosticsSurface;"])
            self.assertEqual(artifact.symbols(
                path, ["dev/nightfall/diagnostics/Diagnostics"]), [])
            self.assertEqual(artifact.symbols(
                path, ["dev/nightfall/diagnostics/DiagnosticsSurface"]),
                ["dev/nightfall/diagnostics/DiagnosticsSurface"])

    def test_the_surfaces_copy_is_caught_in_a_resource_table(self):
        """The feature is its copy as well as its code, so an APK carrying the copy is refused."""
        with tempfile.TemporaryDirectory() as tmp:
            values = artifact.diagnostics_copy()
            word = values["settings_diagnostics_switch"]
            path = apk(pathlib.Path(tmp) / "copy.apk",
                       [b"Ldev/nightfall/diagnostics/DiagnosticsSurface;"],
                       copy=[b"\x00\x00" + word.encode() + b"\x00"])
            violation = artifact.fdroid_violation(path)
            self.assertIsNotNone(violation)
            self.assertIn("diagnostics copy", violation)
            self.assertIn("settings_diagnostics_switch", violation)

class NegativeAssertionTest(unittest.TestCase):
    """The F-Droid half is a negative check, so it is only worth something when the GitHub build
    provably carries the names it searches for. Both halves are pinned here."""

    def github_marks(self, include_diagnostics):
        marks = [b"L" + name.encode() + b";" for name in artifact.updater_markers()]
        if include_diagnostics:
            marks += [b"L" + name.encode() + b";" for name in artifact.diagnostics_markers()]
        return marks

    def test_the_check_refuses_a_github_apk_with_no_diagnostics_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "github.apk", self.github_marks(False), network=True)
            result = run_check(path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("diagnostics package", result.stderr)

    def test_the_check_passes_and_reports_both_packages(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = apk(pathlib.Path(tmp) / "github.apk", self.github_marks(True), network=True)
            result = run_check(path)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('"diagnosticsCode": true', result.stdout)
            sources = len(artifact.diagnostics_entry_points())
            self.assertIn(f'"diagnosticsSources": {sources}', result.stdout)
            self.assertIn('"updaterCode": true', result.stdout)


if __name__ == "__main__":
    unittest.main()
