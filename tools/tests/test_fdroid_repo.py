# SPDX-License-Identifier: MIT
import importlib.util
import pathlib
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


repo = load("fdroid_repo")
PACKAGE = "io.github.camjampng.nightfall"
SIGNER = "0ef5904042e684e4158711036c04c6fb2527cb48c023d5173fd8ded18db032de"


def apk(**overrides):
    info = dict(path=pathlib.Path("Nightfall-1.1.3-universal.apk"), package=PACKAGE,
                versionName="1.1.3", versionCode=8,
                permissions=("android.permission.FOREGROUND_SERVICE", "android.permission.WAKE_LOCK"),
                signers=(SIGNER,), sha256="0" * 64, size=91212300)
    info.update(overrides)
    return info


class EligibilityTest(unittest.TestCase):
    def test_offline_build_signed_with_the_pinned_certificate_is_served(self):
        self.assertIsNone(repo.refusal(apk(), PACKAGE, SIGNER))

    def test_internet_permission_is_refused(self):
        for permission in repo.NETWORK_PERMISSIONS:
            with self.subTest(permission=permission):
                reason = repo.refusal(
                    apk(permissions=("android.permission.WAKE_LOCK", permission)), PACKAGE, SIGNER)
                self.assertIn(permission, reason)
                self.assertIn("fdroid", reason)

    def test_other_package_is_refused(self):
        self.assertIn("dev.nightfall", repo.refusal(apk(package="dev.nightfall"), PACKAGE, SIGNER))

    def test_foreign_signer_is_refused(self):
        reason = repo.refusal(apk(signers=("ab" * 32,)), PACKAGE, SIGNER)
        self.assertIn("pinned release certificate", reason)

    def test_unsigned_apk_is_refused(self):
        self.assertIn("not signed", repo.refusal(apk(signers=()), PACKAGE, SIGNER))

    def test_pinned_identity_matches_the_documented_certificate(self):
        self.assertEqual(repo.pinned_signer(), SIGNER)
        self.assertEqual(repo.application_id(), PACKAGE)


class RetentionTest(unittest.TestCase):
    def test_keeps_the_newest_versions(self):
        kept = repo.retain([apk(versionCode=n) for n in (8, 6, 7)], 2)
        self.assertEqual([i["versionCode"] for i in kept], [7, 8])

    def test_keep_beyond_available_keeps_everything(self):
        kept = repo.retain([apk(versionCode=8), apk(versionCode=7)], 5)
        self.assertEqual([i["versionCode"] for i in kept], [7, 8])

    def test_duplicate_versioncode_is_rejected(self):
        with self.assertRaises(ValueError):
            repo.retain([apk(versionCode=8), apk(versionCode=8, sha256="1" * 64)], 2)


class DocumentedFingerprintTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.path = pathlib.Path(self.folder.name) / "fingerprint.txt"
        self.saved = repo.FINGERPRINT
        repo.FINGERPRINT = self.path

    def tearDown(self):
        repo.FINGERPRINT = self.saved
        self.folder.cleanup()

    def test_empty_fingerprint_is_refused(self):
        self.path.write_text("")
        with self.assertRaises(ValueError) as caught:
            repo.documented_fingerprint()
        self.assertIn("configure_fdroid_repo_key", str(caught.exception))

    def test_committed_fingerprint_is_read_case_insensitively(self):
        self.path.write_text("  " + SIGNER.upper() + "\n")
        self.assertEqual(repo.documented_fingerprint(), SIGNER)

    def test_non_hex_fingerprint_is_refused(self):
        self.path.write_text("nightfall\n")
        with self.assertRaises(ValueError):
            repo.documented_fingerprint()


if __name__ == "__main__":
    unittest.main()
