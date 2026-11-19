# SPDX-License-Identifier: MIT
import importlib.util
import io
import pathlib
import unittest
import zipfile

TOOLS = pathlib.Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class VerifyAudioTest(unittest.TestCase):
    """The staged-release verifier has to accept the containers this build actually ships.

    It used to require `assets/audio/<id>.pcm` in every published APK, which raised KeyError on the
    first recording once the build packed containers, so the publish job failed before creating a
    release. These check the branch that replaced it without needing a signed APK.
    """

    def setUp(self):
        self.verify_release = load("verify_release")
        self.asset = "crickets"
        self.source = (ROOT / "app/src/opus/assets/audio" / f"{self.asset}.nfop").read_bytes()
        self.credits = [{"id": self.asset, "sha256": "0" * 64}]

    def package(self, payload, name=None):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as z:
            z.writestr(f"assets/audio/{name or self.asset}.nfop", payload)
        buffer.seek(0)
        return zipfile.ZipFile(buffer)

    def test_a_container_is_accepted_and_matched_to_its_revision(self):
        # "HEAD" rather than a tag so the check runs on any checkout, and still goes through git.
        self.verify_release.verify_audio(self.package(self.source), self.credits, "HEAD")

    def test_a_container_that_differs_from_the_revision_is_refused(self):
        tampered = bytearray(self.source)
        for i in range(500_000, 500_200):
            tampered[i] = 0xFF
        with self.assertRaises(AssertionError):
            self.verify_release.verify_audio(self.package(bytes(tampered)), self.credits, "HEAD")

    def test_a_truncated_container_is_refused(self):
        with self.assertRaises(AssertionError):
            self.verify_release.verify_audio(self.package(self.source[:2048]), self.credits, "HEAD")

    def test_a_missing_recording_is_refused(self):
        with self.assertRaises(AssertionError):
            self.verify_release.verify_audio(self.package(self.source, name="frogs"), self.credits, "HEAD")


if __name__ == "__main__":
    unittest.main()
