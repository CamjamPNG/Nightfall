# SPDX-License-Identifier: MIT
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
ROOT = TOOLS.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


version = load("release_version")
packaging = load("package_release")
artifact = load("release_artifact")
site = load("assemble_site")


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

    def test_real_release_metadata_preflight(self):
        result = subprocess.run([sys.executable, str(TOOLS / 'check_release.py')], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_android_and_store_versions_stay_in_sync(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for path in ["app", "fdroid/metadata", "fastlane/metadata/android/en-US/changelogs"]:
                (root / path).mkdir(parents=True)
            (root / "app/build.gradle.kts").write_text('versionName = "1.0"\nversionCode = 1\n')
            (root / "CHANGELOG.md").write_text("# Changes\n\n## 1.0\nInitial\n")
            recipe = root / "fdroid/metadata/io.github.camjampng.nightfall.yml"
            recipe.write_text("Changelog: https://example.org/changelogs/1.txt\nBuilds:\n  - versionName: '1.0'\n    versionCode: 1\n    commit: v1.0\n\nMaintainerNotes: |\n  Preserve these notes.\n\nAutoUpdateMode: Version\nCurrentVersion: '1.0'\nCurrentVersionCode: 1\n")
            version.update(root, "1.0.0.1", 2, ["hotfix: correct timer"])
            self.assertEqual(version.current(root), ("1.0.0.1", 2))
            text = recipe.read_text()
            self.assertIn("commit: v1.0\n", text)
            self.assertIn("commit: v1.0.0.1\n", text)
            self.assertLess(text.index('commit: v1.0.0.1'),text.index('MaintainerNotes:'))
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


class FdroidAssetTest(unittest.TestCase):
    """The F-Droid channel's published APK: its name, its offline claims, its packaging."""

    def stub_build_tools(self, root, name_version, name_code):
        """A build-tools directory whose tools copy and describe files, so packaging logic runs
        without the release key. Signing bytes is not under test; where each file goes is."""
        tools = root / "sdk" / "build-tools/35.0.0"
        tools.mkdir(parents=True)
        pin = (ROOT / "docs/release-signing-certificate.sha256").read_text().strip()
        scripts = {
            "zipalign": "import shutil, sys\nargs = sys.argv[1:]\n"
                        "if args[0] != '-c': shutil.copyfile(args[-2], args[-1])\n",
            "apksigner": "import shutil, sys\nargs = sys.argv[1:]\n"
                         f"if args[0] == 'verify': print('certificate SHA-256 digest: {pin}')\n"
                         "else: shutil.copyfile(args[-1], args[args.index('--out') + 1])\n",
            "aapt": "import sys\n"
                    f"print(\"package: name='io.github.camjampng.nightfall' "
                    f"versionCode='{name_code}' versionName='{name_version}'\")\n",
        }
        for name, body in scripts.items():
            path = tools / name
            path.write_text("#!/usr/bin/env python3\n" + body)
            path.chmod(0o755)
        return root / "sdk"

    def source_apk(self, path, network, update_code):
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00" + (
                b"android.permission.INTERNET" if network else b"android.permission.POST_NOTIFICATIONS"))
            z.writestr("classes.dex", b"Ldev/nightfall/MainActivity;" +
                       (b"Ldev/nightfall/update/GithubUpdater;" if update_code else b""))
            z.writestr("assets/audio/birds.pcm", b"\x00\x01" * 16, compress_type=zipfile.ZIP_STORED)
            for abi in packaging.ABIS: z.writestr(f"lib/{abi}/libexample.so", abi.encode())

    def package(self, root, name_version, name_code, fdroid_network=False):
        self.source_apk(root / "github.apk", network=True, update_code=True)
        self.source_apk(root / "fdroid-source.apk", network=fdroid_network, update_code=fdroid_network)
        key = root / "release.p12"
        key.write_bytes(b"not a real key: the stub signer never reads it")
        env = {"PATH": "/usr/local/bin:/usr/bin:/bin",
               "ANDROID_HOME": str(self.stub_build_tools(root, name_version, name_code)),
               "NIGHTFALL_KEYSTORE_PATH": str(key), "NIGHTFALL_KEYSTORE_PASSWORD": "unused by the stub"}
        return subprocess.run([sys.executable, str(TOOLS / "package_release.py"),
                               "--apk", str(root / "github.apk"), "--fdroid-apk", str(root / "fdroid-source.apk"),
                               "--out", str(root / "publish"), "--version", name_version, "--code", str(name_code)],
                              env=env, capture_output=True, text=True)

    def test_the_asset_name_is_what_a_repository_index_selects(self):
        """The index picks the flavour and the architecture by name, not by a fallback guess."""
        name = artifact.fdroid_asset_name("1.2.2")
        self.assertEqual(name, "Nightfall-1.2.2-fdroid-universal.apk")
        self.assertTrue(re.search(r"(^|[-_.])fdroid([-_.]|$)", name, re.I), name)
        self.assertIn("universal", name.lower())
        self.assertFalse(re.search(r"(^|[-_.])fdroid([-_.]|$)", "Nightfall-1.2.2-universal.apk", re.I))

    def test_the_offline_claim_is_read_from_the_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            self.source_apk(root / "clean.apk", network=False, update_code=False)
            self.source_apk(root / "network.apk", network=True, update_code=False)
            self.source_apk(root / "updater.apk", network=False, update_code=True)
            self.assertIsNone(artifact.fdroid_violation(root / "clean.apk"))
            self.assertEqual(artifact.fdroid_violation(root / "network.apk"), "declares android.permission.INTERNET")
            self.assertIn("GithubUpdater", artifact.fdroid_violation(root / "updater.apk"))

    def test_packaging_publishes_the_asset_beside_the_update_options(self):
        name_version, name_code = version.current(ROOT)
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            result = self.package(root, name_version, name_code)
            self.assertEqual(result.returncode, 0, result.stderr)
            published = {p.name for p in (root / "publish").iterdir()}
            asset = artifact.fdroid_asset_name(name_version)
            self.assertIn(asset, published)
            self.assertIn("without network permission", result.stdout)
            self.assertEqual(published, {asset, "release-manifest.json", "SHA256SUMS.txt",
                                        "nightfall-release-certificate.pem"} |
                             {f"Nightfall-{name_version}-{abi}.apk" for abi in ("universal", *packaging.ABIS)})
            manifest = json.loads((root / "publish/release-manifest.json").read_text())
            self.assertNotIn(asset, {entry["file"] for entry in manifest["apks"]})
            self.assertEqual(len(manifest["apks"]), 5)
            sums = {line.split("  ")[1] for line in (root / "publish/SHA256SUMS.txt").read_text().splitlines()}
            self.assertIn(asset, sums)

    def test_packaging_refuses_a_network_capable_fdroid_build(self):
        name_version, name_code = version.current(ROOT)
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            result = self.package(root, name_version, name_code, fdroid_network=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("declares android.permission.INTERNET", result.stderr)
            self.assertFalse((root / "publish/release-manifest.json").exists(),
                             "a refused F-Droid build must not produce a publishable release")

    def test_check_release_rejects_a_misnamed_fdroid_asset(self):
        name_version, name_code = version.current(ROOT)
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            self.assertEqual(self.package(root, name_version, name_code).returncode, 0)
            asset = root / "publish" / artifact.fdroid_asset_name(name_version)
            env = {"PATH": "/usr/local/bin:/usr/bin:/bin", "ANDROID_HOME": str(root / "sdk")}
            accepted = subprocess.run([sys.executable, str(TOOLS / "check_release.py"), "--fdroid-asset", str(asset)],
                                      env=env, capture_output=True, text=True)
            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            misnamed = root / f"Nightfall-{name_version}-universal.apk"
            asset.rename(misnamed)
            refused = subprocess.run([sys.executable, str(TOOLS / "check_release.py"), "--fdroid-asset", str(misnamed)],
                                     env=env, capture_output=True, text=True)
            self.assertNotEqual(refused.returncode, 0)
            self.assertIn("must be named", refused.stderr)


class SiteArtifactTest(unittest.TestCase):
    """GitHub Pages serves one artifact, so the landing page and the repository ship together."""

    def repository(self, root):
        repo = root / "generated/repo"
        (repo / "icons").mkdir(parents=True)
        for name in ("index-v1.json", "index-v1.jar", "index.xml", "entry.json"):
            (repo / name).write_text("{}")
        (repo / "icons/io.github.camjampng.nightfall.11.png").write_bytes(b"png")
        (repo / "io.github.camjampng.nightfall_11.apk").write_bytes(b"apk")
        return repo

    def test_the_landing_page_owns_the_root_and_the_repository_lives_below_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            summary = root / "summary.json"
            summary.write_text("{}")
            out = site.assemble(self.repository(root), summary, root / "site")
            self.assertTrue((out / "index.html").is_file())
            self.assertTrue((out / "style.css").is_file())
            self.assertTrue((out / ".nojekyll").is_file())
            for name in ("index-v1.json", "index-v1.jar", "index.xml", "entry.json", "index-summary.json"):
                self.assertTrue((out / "fdroid/repo" / name).is_file(), name)
            self.assertEqual([p.name for p in (out / "fdroid/repo").glob("*.apk")],
                             ["io.github.camjampng.nightfall_11.apk"])
            self.assertFalse((out / "index-v1.json").exists(), "the index must not sit at the site root")

    def test_the_landing_page_deploys_alone_when_no_index_exists(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = site.assemble(None, None, pathlib.Path(tmp) / "site")
            self.assertTrue((out / "index.html").is_file())
            self.assertTrue((out / "style.css").is_file())
            self.assertTrue((out / ".nojekyll").is_file())
            self.assertFalse((out / "fdroid").exists(), "no repository means no empty fdroid/repo")

    def test_an_incomplete_artifact_is_refused_rather_than_deployed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            repo = self.repository(root)
            (repo / "index.xml").unlink()
            with self.assertRaises(ValueError): site.assemble(repo, None, root / "site")
            repo = self.repository(root / "second")
            for apk in repo.glob("*.apk"): apk.unlink()
            with self.assertRaises(ValueError): site.assemble(repo, None, root / "other")


if __name__ == "__main__": unittest.main()
