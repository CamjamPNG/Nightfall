# SPDX-License-Identifier: MIT
"""A commit range releases only when it changed a file that can reach the APK.

The defect these hold: 05bf6c0 changed `.github/workflows/retry-failed-publish.yml` and nothing else,
classified as a patch, and published v2.7.1 with one CI sentence for notes. Every `github`-channel
install was then offered an update it could not observe. A commit subject is not evidence about files.
"""
import importlib.util
import pathlib
import subprocess
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


version = load("release_version")


class RangeTest(unittest.TestCase):
    """A throwaway repository per test, so the rule is read from real `git diff` output."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary.name)
        self.run_git("init", "-q")
        self.run_git("config", "user.email", "release-gate@example.invalid")
        self.run_git("config", "user.name", "Release gate")
        self.run_git("config", "commit.gpgsign", "false")
        self.base = self.commit("chore(release): v1.0 [skip ci]", {"app/build.gradle.kts": "versionName = \"1.0\"\n"})

    def tearDown(self):
        self.temporary.cleanup()

    def run_git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True, text=True)

    def commit(self, subject, files, body=""):
        for path, text in files.items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
        self.run_git("add", "-A")
        self.run_git("commit", "-q", "-m", subject + (f"\n\n{body}" if body else ""))
        return version.git(self.root, "rev-parse", "HEAD")

    def decide(self):
        return version.decide(self.root, self.base)

    def test_a_change_under_app_releases(self):
        self.commit("fix: stop the timer at the wall clock", {"app/src/main/java/dev/nightfall/Timer.kt": "class Timer\n"})
        decision = self.decide()
        self.assertTrue(decision["prepared"])
        self.assertEqual(decision["kind"], "patch")
        self.assertEqual(decision["files"], ["app/src/main/java/dev/nightfall/Timer.kt"])

    def test_a_change_under_core_releases_with_its_level(self):
        self.commit("feat: end the night at a clock time", {"core/src/main/kotlin/dev/nightfall/core/EndsAt.kt": "object EndsAt\n"})
        decision = self.decide()
        self.assertTrue(decision["prepared"])
        self.assertEqual(decision["kind"], "minor")

    def test_a_build_graph_change_releases(self):
        self.commit("build: raise the Kotlin language level", {"gradle.properties": "kotlin.code.style=official\n",
                                                              "settings.gradle.kts": "rootProject.name = \"nightfall\"\n",
                                                              "gradle/wrapper/gradle-wrapper.properties": "distributionUrl=x\n"})
        files = self.decide()["files"]
        self.assertEqual(files, ["gradle.properties", "gradle/wrapper/gradle-wrapper.properties", "settings.gradle.kts"])

    def test_a_docs_only_range_does_not_release(self):
        self.commit("docs: say what has never been checked on hardware", {"docs/TESTING.md": "limits\n"})
        decision = self.decide()
        self.assertEqual(decision["files"], [])
        self.assertFalse(decision["prepared"])
        self.assertEqual(len(decision["messages"]), 1)

    def test_a_workflow_only_range_does_not_release(self):
        self.commit("ci: retry a failed publish once, from a workflow that runs after it",
                    {".github/workflows/retry-failed-publish.yml": "on: workflow_run\n"})
        self.assertFalse(self.decide()["prepared"])

    def test_a_tooling_only_range_does_not_release(self):
        self.commit("fix: make the release notes say which file blocked them", {"tools/release_version.py": "print()\n",
                                                                               "tools/tests/test_release_gate.py": "pass\n"})
        self.assertFalse(self.decide()["prepared"])

    def test_a_metadata_only_range_does_not_release(self):
        """fastlane changelogs and the F-Droid recipe are written by this tool, never into an APK."""
        self.commit("chore: regenerate the store changelog", {
            "fastlane/metadata/android/en-US/changelogs/48.txt": "notes\n",
            "fdroid/metadata/io.github.camjampng.nightfall.yml": "CurrentVersionCode: 48\n",
            "CHANGELOG.md": "## 2.7.1\n",
        })
        self.assertFalse(self.decide()["prepared"])

    def test_a_mixed_range_releases(self):
        self.commit("docs: describe the timer", {"docs/ARCHITECTURE.md": "timer\n"})
        self.commit("ui: put the timer on Home", {"app/src/main/java/dev/nightfall/NightfallScreen.kt": "fun Home() {}\n"})
        decision = self.decide()
        self.assertTrue(decision["prepared"])
        self.assertEqual(decision["files"], ["app/src/main/java/dev/nightfall/NightfallScreen.kt"])

    def test_a_deleted_product_file_releases(self):
        self.commit("feat: add a recording", {"app/src/opus/assets/audio/rain.nfop": "container\n"})
        self.base = version.git(self.root, "rev-parse", "HEAD")
        (self.root / "app/src/opus/assets/audio/rain.nfop").unlink()
        self.run_git("add", "-A")
        self.run_git("commit", "-q", "-m", "fix: drop a recording that never played")
        decision = self.decide()
        self.assertEqual(decision["files"], ["app/src/opus/assets/audio/rain.nfop"])
        self.assertTrue(decision["prepared"])

    def test_skip_release_still_suppresses_a_feat_that_touches_app(self):
        self.commit("feat: a sleep timer [skip release]", {"app/src/main/java/dev/nightfall/Timer.kt": "class Timer\n"})
        decision = self.decide()
        self.assertEqual(decision["files"], ["app/src/main/java/dev/nightfall/Timer.kt"])
        self.assertEqual(decision["messages"], [])
        self.assertFalse(decision["prepared"])

    def test_a_release_commit_alone_does_not_release_again(self):
        """The version bump itself touches `app/build.gradle.kts`; it is not a reason to bump again."""
        self.commit("chore(release): v1.0.1 [skip ci]", {"app/build.gradle.kts": "versionName = \"1.0.1\"\n"})
        self.assertFalse(self.decide()["prepared"])

    def test_the_subject_alone_never_decides(self):
        """A `feat:` in a docs-only range is a documentation change no user can hear."""
        self.commit("feat: a sleep timer that ends at a clock time, not only after a duration",
                    {"README.md": "a timer exists\n"})
        decision = self.decide()
        self.assertEqual(decision["kind"], "minor")
        self.assertFalse(decision["prepared"])
