# SPDX-License-Identifier: MIT
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

TOOLS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


version = load("release_version")
packaging = load("package_release")


class VersionPolicyTest(unittest.TestCase):
    def test_requested_examples(self):
        for kind, expected in [("minor", "1.1"), ("patch", "1.0.1"), ("major", "2.0"), ("hotfix", "1.0.0.1")]:
            with self.subTest(kind=kind): self.assertEqual(version.bump("1.0", kind), expected)

    def test_increment_and_reset_subordinate_fields(self):
        self.assertEqual(version.bump("1.3.4.7", "hotfix"), "1.3.4.8")
        self.assertEqual(version.bump("1.3.4.7", "patch"), "1.3.5")
        self.assertEqual(version.bump("1.3.4.7", "minor"), "1.4")
        self.assertEqual(version.bump("1.3.4.7", "major"), "2.0")

    def test_numeric_order_and_equivalent_names(self):
        self.assertGreater(version.parts("1.0.1"), version.parts("1.0.0.9"))
        self.assertGreater(version.parts("1.10"), version.parts("1.9.9.9"))
        self.assertEqual(version.parts("1.0"), version.parts("1.0.0.0"))

    def test_bad_versions_rejected(self):
        for value in ["1", "1.2.3.4.5", "v1.0", "1.2-beta", "../1.0", "-1.0"]:
            with self.subTest(value=value), self.assertRaises(ValueError): version.parts(value)

    def test_largest_change_wins(self):
        self.assertEqual(version.classify(["hotfix: timer", "feat(sounds): add forest", "fix: typo"]), "minor")
        self.assertEqual(version.classify(["feat: sound", "refactor!: replace audio architecture"]), "major")
        self.assertEqual(version.classify(["fix: persistence\n\nBREAKING CHANGE: incompatible format"]), "major")

    def test_ui_and_significant_fixes_are_minor(self):
        self.assertEqual(version.classify(["ui: adjust mixer spacing"]), "minor")
        self.assertEqual(version.classify(["fix(major): repair background playback"]), "minor")
        self.assertEqual(version.classify(["hotfix(audio): urgent crackle fix"]), "hotfix")
        self.assertEqual(version.classify(["fix: minor layout bug"]), "patch")

    def test_android_and_store_versions_stay_in_sync(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for path in ["app", "fdroid/metadata", "fastlane/metadata/android/en-US/changelogs"]:
                (root / path).mkdir(parents=True)
            (root / "app/build.gradle.kts").write_text('versionName = "1.0"\nversionCode = 1\n')
            (root / "CHANGELOG.md").write_text("# Changes\n\n## 1.0\nInitial\n")
            recipe = root / "fdroid/metadata/io.github.camjampng.nightfall.yml"
            recipe.write_text("Changelog: https://example.org/changelogs/1.txt\nBuilds:\n  - versionName: '1.0'\n    versionCode: 1\n    commit: v1.0\n\nAutoUpdateMode: Version\nCurrentVersion: '1.0'\nCurrentVersionCode: 1\n")
            version.update(root, "1.0.0.1", 2, ["hotfix: correct timer"])
            self.assertEqual(version.current(root), ("1.0.0.1", 2))
            text = recipe.read_text()
            self.assertIn("commit: v1.0\n", text)
            self.assertIn("commit: v1.0.0.1\n", text)
            self.assertIn("CurrentVersionCode: 2", text)
            self.assertIn("/changelogs/2.txt", text)
            self.assertEqual((root / "fastlane/metadata/android/en-US/changelogs/2.txt").read_text(), "- hotfix: correct timer\n")
            with self.assertRaises(ValueError): version.update(root, "1.0.0.1", 2, ["duplicate"])


class ApkVariantsTest(unittest.TestCase):
    def test_variants_preserve_all_sounds_resources_and_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            source = root / "source.apk"
            sounds = bytes(range(256)) * 256
            with zipfile.ZipFile(source, "w") as z:
                z.writestr("classes.dex", b"identical bytecode")
                z.writestr("assets/audio/birds.pcm", sounds, compress_type=zipfile.ZIP_STORED)
                for abi in packaging.ABIS: z.writestr(f"lib/{abi}/libexample.so", abi.encode())
                z.writestr("META-INF/OLD.RSA", b"stale signature")
            for abi in [None, *packaging.ABIS]:
                target = root / "variant.apk"
                packaging.filter_apk(source, target, abi)
                with zipfile.ZipFile(target) as z:
                    self.assertEqual(z.read("assets/audio/birds.pcm"), sounds)
                    self.assertEqual(z.getinfo("assets/audio/birds.pcm").compress_type, zipfile.ZIP_STORED)
                    self.assertEqual(z.read("classes.dex"), b"identical bytecode")
                    expected = set(packaging.ABIS) if abi is None else {abi}
                    self.assertEqual({n.split("/")[1] for n in z.namelist() if n.startswith("lib/")}, expected)
                    self.assertNotIn("META-INF/OLD.RSA", z.namelist())

    def test_cannot_publish_architecture_without_native_libraries(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            with zipfile.ZipFile(root / "empty.apk", "w") as z: z.writestr("classes.dex", b"code")
            with self.assertRaises(ValueError): packaging.filter_apk(root / "empty.apk", root / "arm.apk", "arm64-v8a")


if __name__ == "__main__": unittest.main()
