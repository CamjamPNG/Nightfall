#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Verify externally signed release assets before an Actions runner publishes them."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import ssl
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=pathlib.Path, required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"v\d+\.\d+(?:\.\d+){0,2}", args.tag): raise ValueError("Invalid tag")
    folder = args.assets
    manifest = json.loads((folder / "release-manifest.json").read_text())
    pin = (ROOT / "docs/release-signing-certificate.sha256").read_text().strip()
    assert manifest["certificateSha256"] == pin, "Unexpected signing identity"
    assert manifest["applicationId"] == "io.github.camjampng.nightfall"
    assert manifest["versionName"] == args.tag[1:]
    assert manifest["minSdk"] == 26 and manifest["targetSdk"] == 35
    source = subprocess.check_output(["git", "show", f"refs/tags/{args.tag}:app/build.gradle.kts"], text=True)
    assert re.search(r'versionName\s*=\s*"([^"]+)"', source)[1] == manifest["versionName"]
    assert int(re.search(r"versionCode\s*=\s*(\d+)", source)[1]) == manifest["versionCode"]
    assert {a["abi"] for a in manifest["apks"]} == ABIS | {"universal"}
    assert len(manifest["apks"]) == 5
    expected = {"release-manifest.json", "nightfall-release-certificate.pem", "SHA256SUMS.txt"}
    for apk in manifest["apks"]:
        assert apk["file"] == f'Nightfall-{manifest["versionName"]}-{apk["abi"]}.apk'
        expected.add(apk["file"])
    public_notes = {"release-notes.md"} if (folder / "release-notes.md").exists() else set()
    assert {p.name for p in folder.iterdir()} == expected | public_notes, "Unexpected/missing release files"
    der = ssl.PEM_cert_to_DER_cert((folder / "nightfall-release-certificate.pem").read_text())
    assert hashlib.sha256(der).hexdigest() == pin, "Wrong public certificate file"
    credits = json.loads(subprocess.check_output(["git", "show", f"refs/tags/{args.tag}:docs/audio-credits.json"], text=True))
    sums = {}
    for line in (folder / "SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split("  ", 1)
        assert pathlib.Path(name).name == name and name in expected and name not in sums
        assert re.fullmatch(r"[a-f0-9]{64}", digest)
        sums[name] = digest
    assert set(sums) == expected - {"SHA256SUMS.txt"}
    for name, digest in sums.items():
        with (folder / name).open("rb") as stream: actual = hashlib.file_digest(stream, "sha256").hexdigest()
        assert actual == digest, "Checksum mismatch: " + name
    sdk = pathlib.Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    for apk in manifest["apks"]:
        file = folder / apk["file"]
        assert apk["sha256"] == sums[apk["file"]] and apk["bytes"] == file.stat().st_size
        cert = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(file)], text=True)
        assert re.findall(r"certificate SHA-256 digest: ([a-f0-9]+)", cert) == [pin], "Invalid signer"
        subprocess.run([str(sdk / "zipalign"), "-c", "-P", "16", "4", str(file)], check=True)
        badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(file)], text=True)
        for field in (f'name=\'{manifest["applicationId"]}\'', f'versionName=\'{manifest["versionName"]}\'', f'versionCode=\'{manifest["versionCode"]}\''):
            assert field in badging.splitlines()[0], "Wrong APK package/version"
        assert "sdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging
        with zipfile.ZipFile(file) as z:
            abis = {n.split("/")[1] for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")}
            assert abis == (ABIS if apk["abi"] == "universal" else {apk["abi"]})
            for asset in credits:
                entry = z.getinfo(f'assets/audio/{asset["id"]}.pcm')
                assert entry.compress_type == zipfile.ZIP_STORED and entry.file_size / 48000 - 1 >= 180
                with z.open(entry) as stream:
                    assert hashlib.file_digest(stream, "sha256").hexdigest() == asset["sha256"], "Audio differs from source tag"
        print("Validated", file.name, flush=True)
    print("Release assets verified against source tag and pinned certificate.")


if __name__ == "__main__": main()
