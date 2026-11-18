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

PIN = (packaging.ROOT / "docs/release-signing-certificate.sha256").read_text().strip()
MANIFEST_KEYS = {"versionName", "versionCode", "applicationId", "minSdk", "targetSdk", "certificateSha256", "apks"}
APK_KEYS = {"file", "abi", "bytes", "sha256", "url"}
VERSION, CODE = "1.0.2", 5


def apks():
    return [dict(file=f"Nightfall-{VERSION}-{abi}.apk", abi=abi, bytes=1000 + index,
                 sha256=f"{index:064x}")
            for index, abi in enumerate(("universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64"))]


class ReleaseManifestTest(unittest.TestCase):
    def test_manifest_carries_what_the_updater_needs(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks())
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
        manifest = packaging.release_manifest(VERSION, CODE, apks())
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
            packaging.release_manifest("1.0-beta", CODE, apks())
        with self.assertRaises(ValueError):
            packaging.release_manifest(VERSION, 0, apks())

    def test_source_tag_and_published_manifest_agree(self):
        manifest = packaging.release_manifest(VERSION, CODE, apks())
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
                z.writestr("classes.dex", b"Ldev/nightfall/update/UpdateSupport;")
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
        for block in blocks:
            tag = re.search(r"commit:\s*(\S+)", block)[1]
            source = checking.tag_source(tag)
            if source is None:
                continue
            self.assertEqual(checking.selects_fdroid(block), "productFlavors" in source, tag)

    def test_utf16_manifest_strings_are_also_recognised(self):
        self.assertTrue(checking.declares("x".encode() + "android.permission.INTERNET".encode("utf-16-le"),
                                          "android.permission.INTERNET"))


if __name__ == "__main__":
    unittest.main()
