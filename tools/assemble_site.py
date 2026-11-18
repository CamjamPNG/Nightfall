#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Assemble the single GitHub Pages artifact: the landing page at the root, the repository beneath.

GitHub Pages serves one artifact per deployment, so the landing page and the F-Droid repository
cannot be published by separate workflows: whichever deployed last would erase the other. The
repository lives at `fdroid/repo` because that is the path the documented client URL and the
fingerprint URL already point at.
"""
import argparse
import pathlib
import shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANDING = ROOT / "docs/site"
LANDING_FILES = ("index.html", "style.css")
REPOSITORY_FILES = ("index-v1.json", "index-v1.jar", "index.xml", "entry.json")


def assemble(repository, summary, out):
    """Copy the landing page and the generated repository into one deployable tree."""
    out = pathlib.Path(out)
    if out.exists(): shutil.rmtree(out)
    shutil.copytree(LANDING, out)
    target = out / "fdroid/repo"
    shutil.copytree(repository, target)
    if summary: shutil.copyfile(summary, target / "index-summary.json")
    (out / ".nojekyll").touch()
    for name in LANDING_FILES:
        if not (out / name).is_file(): raise ValueError("Landing page is missing " + name)
    for name in REPOSITORY_FILES:
        if not (target / name).is_file(): raise ValueError("Repository index is missing fdroid/repo/" + name)
    if not list(target.glob("*.apk")): raise ValueError("Repository carries no APK")
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=pathlib.Path, required=True, help="Generated fdroidserver repository")
    parser.add_argument("--summary", type=pathlib.Path, help="Build summary to publish with the index")
    parser.add_argument("--out", type=pathlib.Path, required=True, help="Deployable site directory")
    args = parser.parse_args()
    out = assemble(args.repo, args.summary, args.out)
    for file in sorted(out.rglob("*")):
        if file.is_file(): print(file.relative_to(out).as_posix(), flush=True)


if __name__ == "__main__": main()
