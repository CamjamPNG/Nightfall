#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Nightfall's four-level version policy. Android versionCode always increases."""
import argparse
import datetime
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSION = re.compile(r"^(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?$")
KINDS = ("hotfix", "patch", "minor", "major")

DEFAULT_NOTES = (
    "Each APK carries the complete offline library: no account, no log of how you use it unless "
    "you turn the opt-in diagnostics on yourself, and no connection needed to play. What this release "
    "changed is the list above; what has never been exercised on a phone is recorded in README.md "
    "under \"Never checked on hardware\"."
)

# The long-form notes a subject line cannot carry: what a notable release *is*, what it measures
# and which parts of it are still unverified. A version with no entry falls back to DEFAULT_NOTES,
# so this map holds only the releases whose paragraph someone actually wrote and checked.
RELEASE_NOTES = {
    "2.0": (
        "Nightfall 2.0 is not a rewrite. It is the release where the app became small, "
        "configurable and current.\n\n"
        "- 11.7 MB instead of 79.5. The seven bundled recordings ship as 10,089,142 bytes of Opus "
        "in place of 77,449,472 bytes of raw PCM, in both channels. The arm64-v8a APK is "
        "11,737,776 bytes against 79,465,722 for the same file on 1.11, the last release that "
        "shipped PCM.\n"
        "- Endless night. An optional switch on Home lets the mix evolve over hours instead of "
        "looping one fixed bed: companion layers from the same families as your own arrive and "
        "leave, and every layer's tone drifts around its setting. Over eight hours the hourly "
        "level stays within 0.22 LU of the same mix with the night off.\n"
        "- A home-screen play/pause widget that drives the app's own media session, so its state "
        "follows what is playing rather than the last tap.\n"
        "- Hidden, opt-in diagnostics: five taps on the Settings version row. Off until you turn "
        "it on, a bounded local journal (200 entries, 64 KB or 30 days, closed-set tokens and no "
        "free text), a Share action you start yourself, and no transport in either channel.\n"
        "- Android 16. targetSdk 36, with a CI job that runs the app's own suite on an API 36 "
        "runtime, so the behaviour a 36 target turns on is executed rather than read.\n"
        "- Every UI string in resources, with a build-time guard that fails the build on the next "
        "literal typed into a Compose call.\n\n"
        "What none of that proves, said as plainly as the wins:\n\n"
        "- The Opus decoder has never run on a device. 1.12 was the first build to depend on it, "
        "and if a platform refuses Opus the app says so at start-up instead of playing silence.\n"
        "- No ear has heard the current catalogue on a phone.\n"
        "- The widget has never been seen on a launcher.\n"
        "- Diagnostics has never recorded a real crash."
    ),
}


def notes_body(version):
    return RELEASE_NOTES.get(version, DEFAULT_NOTES)


def parts(version):
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError("Expected major.minor[.patch[.hotfix]]: " + version)
    return tuple(int(x or 0) for x in match.groups())


def bump(version, kind):
    major, minor, patch, hotfix = parts(version)
    if kind == "major": return f"{major + 1}.0"
    if kind == "minor": return f"{major}.{minor + 1}"
    if kind == "patch": return f"{major}.{minor}.{patch + 1}"
    if kind == "hotfix": return f"{major}.{minor}.{patch}.{hotfix + 1}"
    raise ValueError("Unknown release kind: " + kind)


def classify(messages):
    level = "hotfix"
    for message in messages:
        subject = message.splitlines()[0]
        if re.match(r"\w+(?:\([^)]*\))?!:", subject) or re.search(r"(?m)^BREAKING[ -]CHANGE:", message):
            candidate = "major"
        elif re.match(r"(?:feat|sounds?|ui)(?:\([^)]*\))?:", subject) or subject.startswith("fix(major):"):
            candidate = "minor"
        elif subject.startswith("hotfix:") or re.match(r"hotfix\([^)]*\):", subject):
            candidate = "hotfix"
        else:
            candidate = "patch"
        if KINDS.index(candidate) > KINDS.index(level): level = candidate
    return level


def current(root):
    text = (root / "app/build.gradle.kts").read_text()
    return re.search(r'versionName\s*=\s*"([^"]+)"', text)[1], int(re.search(r"versionCode\s*=\s*(\d+)", text)[1])


def update(root, version, code, messages):
    """Update Android, F-Droid, store changelog and human changelog together."""
    if not 1 <= code <= 2100000000: raise ValueError("Android versionCode exhausted")
    parts(version)
    gradle = root / "app/build.gradle.kts"
    text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version}"', gradle.read_text())
    text = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {code}", text)
    recipe = root / "fdroid/metadata/io.github.camjampng.nightfall.yml"
    metadata = recipe.read_text()
    yaml_version = f"'{version}'" if version.count('.') == 1 else version
    # Releases after the two-channel split build the F-Droid flavor, which has no updater and no
    # network permission. Earlier entries stay on the default build of their own tag.
    block = f"  - versionName: {yaml_version}\n    versionCode: {code}\n    commit: v{version}\n    subdir: app\n    gradle:\n      - fdroid\n\n"
    if f"commit: v{version}\n" in metadata: raise ValueError("Version already exists in F-Droid metadata")
    build_start = metadata.index("Builds:\n") + len("Builds:\n")
    next_field = re.search(r"(?m)^[A-Za-z]\w*:", metadata[build_start:])
    if not next_field: raise ValueError("Cannot find end of F-Droid Builds list")
    insertion = build_start + next_field.start()
    metadata = metadata[:insertion] + block + metadata[insertion:]
    metadata = re.sub(r"(?m)^CurrentVersion:.*$", f"CurrentVersion: {yaml_version}", metadata)
    metadata = re.sub(r"(?m)^CurrentVersionCode:.*$", f"CurrentVersionCode: {code}", metadata)
    metadata = re.sub(r"(/changelogs/)\d+(\.txt)", rf"\g<1>{code}\2", metadata)
    subjects = [m.splitlines()[0] for m in messages] or ["Maintenance release"]
    summary = "\n".join("- " + s for s in subjects)
    changelog = root / "CHANGELOG.md"
    old = changelog.read_text()
    heading, separator, rest = old.partition("\n")
    entry = f"\n## {version} — {datetime.date.today().isoformat()}\n\n{summary}\n"
    gradle.write_text(text)
    recipe.write_text(metadata)
    changelog.write_text(heading + separator + entry + rest)
    (root / f"fastlane/metadata/android/en-US/changelogs/{code}.txt").write_text(summary[:499] + "\n")
    return summary


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


# Files a change to which can alter the APK a user installs. A range releases only when at least one
# of them moved. On 05bf6c0 a workflow-only change classified as a patch and published v2.7.1, whose
# notes are one sentence about CI, and every `github`-channel install was offered an update it could
# not observe. `app/` and `core/` include each module's own build file, and `gradle/` includes the
# wrapper. `fastlane/` is deliberately absent: `update()` writes those changelogs from the same commit
# subjects, and they never enter an APK.
PRODUCT_DIRS = ("app/", "core/", "gradle/")
PRODUCT_FILES = ("gradle.properties", "gradlew", "gradlew.bat", "settings.gradle.kts",
                 "build.gradle.kts")


def ships(path):
    """True when changing this file can change what ships in the APK."""
    return path in PRODUCT_FILES or path.startswith(PRODUCT_DIRS)


def product_files(root, base, head="HEAD"):
    """Files in a commit range that can change the shipped APK, in path order.

    A deleted file counts: removing a shipped asset or a source file changes the APK as surely as
    adding one, and `git diff --name-only` lists both.
    """
    return [path for path in git(root, "diff", "--name-only", f"{base}..{head}").splitlines() if ships(path)]


def commit_messages(root, base, head="HEAD"):
    """Commit messages in a range, minus the release commits and the `[skip release]` override."""
    messages = [m.strip() for m in git(root, "log", f"{base}..{head}", "--format=%s%n%b%x00").split("\0") if m.strip()]
    return [m for m in messages if not m.startswith("chore(release):") and "[skip release]" not in m.splitlines()[0]]


def decide(root, base, head="HEAD"):
    """What one range decides: the product files it touched, the commits it counts, and the release.

    Both halves must be non-empty. The file rule is the one a squash cannot lose: `[skip release]`
    still suppresses, and a subject that says nothing about files cannot publish a change no user can
    observe.
    """
    files, messages = product_files(root, base, head), commit_messages(root, base, head)
    return dict(files=files, messages=messages, prepared=bool(files) and bool(messages), kind=classify(messages))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("auto", *KINDS), default="auto")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--github-output", type=pathlib.Path)
    parser.add_argument("--base", metavar="REV",
                        help="decide this range instead of the latest release tag; a dry run, never with --write")
    parser.add_argument("--head", metavar="REV", default="HEAD")
    args = parser.parse_args()
    if args.base and args.write: parser.error("--base is a dry run and cannot be combined with --write")
    version, code = current(ROOT)
    if args.base:
        base = args.base
    else:
        tags = [t for t in git(ROOT, "tag", "--merged", "HEAD").splitlines() if t.startswith("v") and VERSION.fullmatch(t[1:])]
        if not tags: raise ValueError("Bootstrap a reviewed version tag before enabling automatic releases")
        base = max(tags, key=lambda t: parts(t[1:]))
        if parts(base[1:]) != parts(version): raise ValueError("Source version differs from latest reachable release tag")
    decision = decide(ROOT, base, args.head)
    messages = decision["messages"]
    # A dispatch names its level, which is a person deciding to publish; auto is the file rule.
    prepared = args.kind != "auto" or decision["prepared"]
    kind = classify(messages) if args.kind == "auto" else args.kind
    notes = "\n".join("- " + m.splitlines()[0] for m in messages) or "Initial offline sleep mixer release."
    if prepared:
        version, code = bump(version, kind), code + 1
        if args.write: notes = update(ROOT, version, code, messages)
    if args.write:
        folder = ROOT / "artifacts"
        folder.mkdir(exist_ok=True)
        (folder / "release-notes.md").write_text(
            f"Nightfall {version}\n\n{notes}\n\n{notes_body(version)}\n\n"
            "**Pixel 7:** choose the arm64-v8a APK. Choose Universal if unsure. "
            "All APKs include the full offline audio library and require Android 8.0+. "
            "The `-fdroid-universal` APK has no updater and no network permission; it is for "
            "stores and installers that refuse a self-updating build.\n\n"
            "Use the same GitHub signing channel for updates. Export mixes before "
            "switching from the earlier dev.nightfall package or another signing channel.\n\n"
            "Physical Pixel 7 / Bluetooth / overnight validation remains pending. "
            "See docs/TESTING.md for executed checks.\n"
        )
    # A run list that says `prepared=false` and nothing else is the same bug in another dress, so the
    # decision names the files it read and why it stopped.
    print(f"Release decision for {base}..{args.head}: {len(decision['files'])} product file(s), "
          f"{len(messages)} commit(s) counted, prepared={str(prepared).lower()}")
    for path in decision["files"]: print(f"  changed {path}")
    if not decision["files"]:
        print("  No file under " + ", ".join(PRODUCT_DIRS + PRODUCT_FILES) + " changed, so nothing a "
              "user can observe in the APK changed and no release is cut.")
    elif not messages:
        print("  Every counted commit carries [skip release], so no release is cut.")
    result = dict(version=version, code=code, tag="v" + version, prepared=str(prepared).lower(), kind=kind,
                  base=base, files=",".join(decision["files"]))
    if args.github_output:
        with args.github_output.open("a") as out:
            for key, value in result.items(): out.write(f"{key}={value}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__": main()
