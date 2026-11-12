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
    block = f"  - versionName: {yaml_version}\n    versionCode: {code}\n    commit: v{version}\n    subdir: app\n    gradle:\n      - yes\n\n"
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("auto", *KINDS), default="auto")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--github-output", type=pathlib.Path)
    args = parser.parse_args()
    version, code = current(ROOT)
    tags = [t for t in git(ROOT, "tag", "--merged", "HEAD").splitlines() if t.startswith("v") and VERSION.fullmatch(t[1:])]
    if not tags: raise ValueError("Bootstrap a reviewed version tag before enabling automatic releases")
    base = max(tags, key=lambda t: parts(t[1:]))
    if parts(base[1:]) != parts(version): raise ValueError("Source version differs from latest reachable release tag")
    messages = [m.strip() for m in git(ROOT, "log", f"{base}..HEAD", "--format=%s%n%b%x00").split("\0") if m.strip()]
    messages = [m for m in messages if not m.startswith("chore(release):") and "[skip release]" not in m.splitlines()[0]]
    prepared = bool(messages) or args.kind != "auto"
    kind = classify(messages) if args.kind == "auto" else args.kind
    notes = "\n".join("- " + m.splitlines()[0] for m in messages) or "Initial offline sleep mixer release."
    if prepared:
        version, code = bump(version, kind), code + 1
        if args.write: notes = update(ROOT, version, code, messages)
    if args.write:
        folder = ROOT / "artifacts"
        folder.mkdir(exist_ok=True)
        (folder / "release-notes.md").write_text(
            f"Nightfall {version}\n\n{notes}\n\n"
            "**Pixel 7:** choose the arm64-v8a APK. Choose Universal if unsure. "
            "All APKs include the full offline audio library and require Android 8.0+.\n\n"
            "Use the same GitHub signing channel for updates. Export mixes before "
            "switching from the earlier dev.nightfall package or another signing channel.\n\n"
            "Physical Pixel 7 / Bluetooth / overnight validation remains pending. "
            "See docs/TESTING.md for executed checks.\n"
        )
    result = dict(version=version, code=code, tag="v" + version, prepared=str(prepared).lower(), kind=kind, base=base)
    if args.github_output:
        with args.github_output.open("a") as out:
            for key, value in result.items(): out.write(f"{key}={value}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__": main()
