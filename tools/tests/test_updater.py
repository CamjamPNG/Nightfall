# SPDX-License-Identifier: MIT
"""Tests for the release manifest the in-app updater reads, and for the two built channels."""
import importlib.util
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import unittest
import zipfile

TOOLS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    """check_release.py validates the real tree when it runs; keep that off the test's argv."""
    argv = sys.argv
    sys.argv = [name]
    try:
        spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        sys.argv = argv


packaging = load("package_release")
checking = load("check_release")
versioning = load("release_version")
artifact = load("release_artifact")

PIN = (packaging.ROOT / "docs/release-signing-certificate.sha256").read_text().strip()
MANIFEST_KEYS = {"schemaVersion", "versionName", "versionCode", "applicationId", "minSdk", "targetSdk",
                 "certificateSha256", "apks", "log"}
APK_KEYS = {"file", "abi", "bytes", "sha256", "url"}
LOG_KEYS = {"version", "date", "notes"}
VERSION, CODE = "1.0.2", 5


def apks():
    return [dict(file=f"Nightfall-{VERSION}-{abi}.apk", abi=abi, bytes=1000 + index,
                 sha256=f"{index:064x}")
            for index, abi in enumerate(("universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64"))]


def log():
    return [dict(version="1.0.2", date="2026-11-18", notes=["feat: a newer release"])]


class ReleaseNotesTest(unittest.TestCase):
    """The manifest's `log` is the app's in-app changelog, generated from the written changelog."""

    CHANGELOG = (
        "# Changelog\n\n"
        "## 1.0.2 — 2026-11-18\n\n"
        "- feat(update): install releases on their own (#12)\n"
        "- fix: keep the row readable when notes are missing\n\n"
        "## 1.0.1 — 2026-11-17\n\n"
        "- hotfix(release): verify the signing identity\n\n"
        "## 1.0\n\n"
        "- First public source release.\n"
    )

    def written(self, text):
        folder = tempfile.mkdtemp()
        path = pathlib.Path(folder) / "CHANGELOG.md"
        path.write_text(text, encoding="utf-8")
        return packaging.release_log(path)

    def test_the_written_changelog_becomes_the_release_log(self):
        entries = self.written(self.CHANGELOG)
        self.assertEqual([entry["version"] for entry in entries], ["1.0.2", "1.0.1", "1.0"])
        self.assertEqual(entries[0]["date"], "2026-11-18")
        self.assertEqual(entries[0]["notes"], ["feat(update): install releases on their own",
                                              "fix: keep the row readable when notes are missing"])
        self.assertEqual("", entries[2]["date"], "a release with no date keeps an empty one")

    def test_notes_are_bounded_and_stripped_of_issue_references(self):
        lines = [f"- release {index}: " + 'x' * 500 + ' (#99)' for index in range(9)]
        entries = self.written("# Changelog\n\n## 2.0 — 2026-01-01\n\n" + "\n".join(lines) + "\n")
        self.assertEqual(len(entries), 1)
        self.assertEqual(len(entries[0]["notes"]), packaging.LOG_NOTES)
        self.assertTrue(all(len(note) <= packaging.LOG_NOTE_CHARS for note in entries[0]["notes"]))
        self.assertNotIn("(#99)", entries[0]["notes"][0])
        self.assertTrue(entries[0]["notes"][0].endswith("…"))

    def test_a_missing_changelog_publishes_no_notes(self):
        self.assertEqual(self.written(""), [])
        self.assertEqual(packaging.release_log(packaging.ROOT / "no-such-changelog.md"), [])

    def test_a_repeated_version_is_listed_once(self):
        text = "## 1.0 — 2026-01-01\n\n- first\n\n## 1.0 — 2026-01-02\n\n- second\n"
        self.assertEqual([entry["notes"] for entry in self.written(text)], [["first"]])

    def test_the_manifest_declares_the_schema_and_the_log(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks(), log=log())
        self.assertEqual(manifest["schemaVersion"], packaging.SCHEMA)
        self.assertEqual(manifest["log"], log())
        self.assertEqual(set(manifest["log"][0]), LOG_KEYS)
        self.assertEqual(json.loads(json.dumps(manifest))["log"], log())

    def test_the_manifest_refuses_a_log_it_cannot_publish(self):
        for broken in ([dict(version="1.0-beta", notes=[])], [dict(version="1.0", notes="one line")],
                       [dict(version="1.0", notes=[1, 2])], ["1.0.2"]):
            with self.subTest(log=broken), self.assertRaises(ValueError):
                packaging.release_manifest(VERSION, CODE, apks(), log=broken)

    def test_the_real_changelog_is_publishable(self):
        """Whatever is committed must survive the generator the release workflow runs."""
        manifest = packaging.release_manifest(VERSION, CODE, apks(), log=packaging.release_log())
        self.assertTrue(manifest["log"], "the repository's own changelog produced no release notes")
        self.assertLess(len(json.dumps(manifest["log"])), 4096, "the log is fetched over the network")


class ReleaseManifestTest(unittest.TestCase):
    def test_manifest_carries_what_the_updater_needs(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks(), log=log())
        self.assertEqual(set(manifest), MANIFEST_KEYS)
        self.assertEqual(manifest["applicationId"], "io.github.camjampng.nightfall")
        self.assertEqual((manifest["minSdk"], manifest["targetSdk"]), (26, 35))
        self.assertEqual(manifest["certificateSha256"], PIN)
        self.assertEqual(manifest["versionName"], VERSION)
        self.assertEqual(manifest["versionCode"], CODE)
        self.assertEqual(len(manifest["apks"]), 5)
        for entry in manifest["apks"]:
            self.assertEqual(set(entry), APK_KEYS)
            self.assertEqual(entry["url"], f"https://github.com/CamjamPNG/Nightfall/releases/download/"
                                           f"v{VERSION}/{entry['file']}")
            self.assertTrue(entry["url"].startswith("https://github.com/CamjamPNG/Nightfall/releases/"))

    def test_manifest_is_valid_json_that_round_trips(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks(), log=log())
        text = json.dumps(manifest, indent=2)
        self.assertEqual(json.loads(text), manifest)
        self.assertNotIn("http://", text)

    def test_manifest_refuses_inputs_it_cannot_publish_safely(self):
        cases = {
            "too few variants": lambda a: a[:4],
            "duplicate architecture": lambda a: [a[0], dict(a[1], abi="universal"), a[2], a[3], a[4]],
            "unknown architecture": lambda a: [dict(a[0], abi="mips")] + a[1:],
            "checksum not hex": lambda a: [dict(a[0], sha256="x" * 64)] + a[1:],
            "zero size": lambda a: [dict(a[0], bytes=0)] + a[1:],
            "path in name": lambda a: [dict(a[0], file="../x.apk")] + a[1:],
            "wrong extension": lambda a: [dict(a[0], file="nightfall.zip")] + a[1:],
        }
        for label, mutate in cases.items():
            with self.subTest(label=label), self.assertRaises(ValueError):
                packaging.release_manifest(VERSION, CODE, mutate(apks()))
        with self.assertRaises(ValueError):
            packaging.release_manifest("1.0-beta", CODE, apks(), log=log())
        with self.assertRaises(ValueError):
            packaging.release_manifest(VERSION, 0, apks(), log=log())

    def test_source_tag_and_published_manifest_agree(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks(), log=log())
        for entry in manifest["apks"]:
            self.assertEqual(entry["file"], f"Nightfall-{manifest['versionName']}-{entry['abi']}.apk")
            self.assertIn(entry["abi"], {"universal", *packaging.ABIS})


class ChannelTest(unittest.TestCase):
    def test_new_fdroid_recipe_entries_build_the_fdroid_flavor(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for path in ["app", "fdroid/metadata", "fastlane/metadata/android/en-US/changelogs"]:
                (root / path).mkdir(parents=True)
            (root / "app/build.gradle.kts").write_text('versionName = "1.0"\nversionCode = 1\n')
            (root / "CHANGELOG.md").write_text("# Changes\n\n## 1.0\nInitial\n")
            recipe = root / "fdroid/metadata/io.github.camjampng.nightfall.yml"
            recipe.write_text("Builds:\n  - versionName: '1.0'\n    versionCode: 1\n    commit: v1.0\n"
                              "    subdir: app\n    gradle:\n      - yes\n\nAutoUpdateMode: Version\n"
                              "CurrentVersion: '1.0'\nCurrentVersionCode: 1\n")
            versioning.update(root, "1.0.1", 2, ["fix: updater"])
            text = recipe.read_text()
            self.assertIn("commit: v1.0.1\n    subdir: app\n    gradle:\n      - fdroid\n", text)
            self.assertIn("commit: v1.0\n    subdir: app\n    gradle:\n      - yes\n", text)

    def test_apk_inspection_finds_permissions_and_updater_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = pathlib.Path(tmp)
            fdroid_apk = folder / "fdroid.apk"
            with zipfile.ZipFile(fdroid_apk, "w") as z:
                z.writestr("AndroidManifest.xml",
                           b"\x03\x00\x08\x00" + b"android.permission.POST_NOTIFICATIONS" + b"\x00\x00")
                z.writestr("classes.dex", b"Ldev/nightfall/audio/AudioHost;")
            github_apk = folder / "github.apk"
            with zipfile.ZipFile(github_apk, "w") as z:
                z.writestr("AndroidManifest.xml",
                           b"\x03\x00\x08\x00" + b"android.permission.INTERNET" +
                           b"android.permission.REQUEST_INSTALL_PACKAGES" +
                           b"android.permission.ACCESS_NETWORK_STATE" +
                           b"android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION")
                z.writestr("classes.dex", b"Ldev/nightfall/update/GithubUpdater;"
                                          b"Ldev/nightfall/update/ReleaseFetch;")
            self.assertFalse(checking.declares(checking.apk_manifest(fdroid_apk), "android.permission.INTERNET"))
            self.assertEqual(checking.updater_symbols(fdroid_apk), [])
            binary = checking.apk_manifest(github_apk)
            for permission in checking.NETWORK_PERMISSIONS:
                self.assertTrue(checking.declares(binary, permission), permission)
            self.assertIn("dev/nightfall/update/GithubUpdater", checking.updater_symbols(github_apk))

    def test_the_updater_entry_points_are_read_from_the_directory(self):
        """Every file in the updater package is covered, including the ones no list ever named."""
        groups = checking.updater_entry_points()
        written = sorted(path.stem for path in
                         (checking.ROOT / "app/src/github/java/dev/nightfall/update").glob("*.kt"))
        self.assertEqual(sorted(groups), written)
        for name in ("UpdateAutomation", "UpdateLog", "InstallWindow"):
            self.assertIn(name, groups)
        for name, markers in groups.items():
            self.assertTrue(markers, name)
            for marker in markers:
                self.assertTrue(marker.startswith("dev/nightfall/update/"), marker)
            self.assertIn("dev/nightfall/update/" + name, markers)

    def test_the_offline_claim_finds_a_class_no_list_named(self):
        """A class added to the package is caught without anybody extending a list first."""
        with tempfile.TemporaryDirectory() as tmp:
            apk = pathlib.Path(tmp) / "updater.apk"
            with zipfile.ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00")
                z.writestr("classes.dex", b"Ldev/nightfall/update/UpdateAutomation;")
            violation = artifact.fdroid_violation(apk)
            self.assertIsNotNone(violation)
            self.assertIn("UpdateAutomation", violation)

    def test_the_recipe_flavour_of_a_block_is_read_exactly(self):
        plain = "  - versionName: '1.0'\n    versionCode: 1\n    gradle:\n      - yes\n"
        chosen = "  - versionName: 1.2\n    versionCode: 9\n    gradle:\n      - fdroid\n"
        missing = "  - versionName: 1.2\n    versionCode: 9\n"
        self.assertFalse(checking.selects_fdroid(plain))
        self.assertTrue(checking.selects_fdroid(chosen))
        self.assertFalse(checking.selects_fdroid(missing))

    def test_the_real_recipe_matches_its_tags(self):
        """Every block selects the flavour exactly when its own tag defines one."""
        self.assertEqual(subprocess.run([sys.executable, str(TOOLS / "check_release.py")],
                                        capture_output=True, text=True).returncode, 0)
        text = (packaging.ROOT / "fdroid/metadata/io.github.camjampng.nightfall.yml").read_text()
        blocks = re.split(r"(?m)^  - versionName:", text)[1:]
        self.assertTrue(blocks)
        verified = 0
        for block in blocks:
            tag = re.search(r"commit:\s*(\S+)", block)[1]
            source = checking.tag_source(tag)
            if source is None:
                continue
            verified += 1
            self.assertEqual(checking.selects_fdroid(block), "productFlavors" in source, tag)
        self.assertGreater(verified, 0, "no release tag could be read, so nothing was verified")

    def test_utf16_manifest_strings_are_also_recognised(self):
        self.assertTrue(checking.declares("x".encode() + "android.permission.INTERNET".encode("utf-16-le"),
                                          "android.permission.INTERNET"))


if __name__ == "__main__":
    unittest.main()
