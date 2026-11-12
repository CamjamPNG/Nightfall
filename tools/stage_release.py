#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Transport only public signed assets to a temporary branch for Actions upload."""
import argparse
import json
import os
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--assets", type=pathlib.Path, required=True)
parser.add_argument("--notes", type=pathlib.Path, required=True, help="Public release notes only")
args = parser.parse_args()
manifest = json.loads((args.assets / "release-manifest.json").read_text())
version = manifest["versionName"]
if not __import__("re").fullmatch(r"\d+\.\d+(?:\.\d+){0,2}", version): raise ValueError("Invalid version")
tag = "v" + version
files = {a["file"] for a in manifest["apks"]} | {"release-manifest.json", "SHA256SUMS.txt", "nightfall-release-certificate.pem"}
if {p.name for p in args.assets.iterdir()} != files: raise ValueError("Only the expected public release assets may be staged")
subprocess.run(["python3", str(ROOT / "tools/verify_release.py"), "--assets", str(args.assets), "--tag", tag], check=True)
with tempfile.TemporaryDirectory(prefix="nightfall-git-index-") as folder:
    env = os.environ.copy()
    env["GIT_INDEX_FILE"] = str(pathlib.Path(folder) / "index")
    def git(*arguments, **kwargs):
        return subprocess.check_output(["git", "-C", str(ROOT), *arguments], env=env, text=True, **kwargs).strip()
    git("read-tree", "HEAD")
    for file in sorted(args.assets.iterdir()):
        blob = git("hash-object", "-w", str(file.resolve()))
        git("update-index", "--add", "--cacheinfo", f"100644,{blob},release-upload/{file.name}")
    notes_blob = git("hash-object", "-w", str(args.notes.resolve()))
    git("update-index", "--add", "--cacheinfo", f"100644,{notes_blob},release-upload/release-notes.md")
    tree = git("write-tree")
    commit = git("commit-tree", tree, "-p", "HEAD", "-m", f"Stage signed public {tag} assets for GitHub release")
    branch = "release-assets/" + tag
    # A new branch only. Never force-push or replace another staging attempt.
    git("push", "origin", f"{commit}:refs/heads/{branch}")
    print("Staged", branch, "— Actions will verify and publish; no private key was uploaded.")
