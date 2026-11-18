#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Verify externally signed release assets before an Actions runner publishes them."""
import argparse
import hashlib
import json
import pathlib
import re
import ssl
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import release_artifact  # noqa: E402

ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def verify_apk(file, abis, version, code, credits, pin, levels):
    """Prove one published APK's signer, alignment, identity, architecture and audio payloads."""
    sdk = release_artifact.build_tools()
    certificate = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(file)], text=True)
    assert re.findall(r"certificate SHA-256 digest: ([a-f0-9]+)", certificate) == [pin], "Invalid signer: " + file.name
    subprocess.run([str(sdk / "zipalign"), "-c", "-P", "16", "4", str(file)], check=True)
    badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(file)], text=True)
    for field in ("name='io.github.camjampng.nightfall'", f"versionName='{version}'", f"versionCode='{code}'"):
        assert field in badging.splitlines()[0], "Wrong APK package/version: " + file.name
    assert f"sdkVersion:'{levels[0]}'" in badging and f"targetSdkVersion:'{levels[1]}'" in badging, (
        "Wrong SDK levels: " + file.name)
    with zipfile.ZipFile(file) as z:
        assert release_artifact.universal_abis(file) == abis, "Wrong architecture in " + file.name
        for asset in credits:
            entry = z.getinfo(f'assets/audio/{asset["id"]}.pcm')
            assert entry.compress_type == zipfile.ZIP_STORED and entry.file_size / 48000 - 1 >= 180
            with z.open(entry) as stream:
                assert hashlib.file_digest(stream, "sha256").hexdigest() == asset["sha256"], "Audio differs from source tag"


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
    source = subprocess.check_output(["git", "show", f"refs/tags/{args.tag}:app/build.gradle.kts"], text=True)
    levels = release_artifact.sdk_levels(source)
    assert (manifest["minSdk"], manifest["targetSdk"]) == levels, "The manifest's SDK levels differ from the tagged source"
    assert re.search(r'versionName\s*=\s*"([^"]+)"', source)[1] == manifest["versionName"]
    assert int(re.search(r"versionCode\s*=\s*(\d+)", source)[1]) == manifest["versionCode"]
    assert {a["abi"] for a in manifest["apks"]} == ABIS | {"universal"}
    assert len(manifest["apks"]) == 5
    expected = {"release-manifest.json", "nightfall-release-certificate.pem", "SHA256SUMS.txt"}
    for apk in manifest["apks"]:
        assert apk["file"] == f'Nightfall-{manifest["versionName"]}-{apk["abi"]}.apk'
        assert apk["url"] == ('https://github.com/CamjamPNG/Nightfall/releases/download/v'
                              f'{manifest["versionName"]}/{apk["file"]}'), "Manifest download URL is not this release's asset"
        expected.add(apk["file"])
    # The F-Droid-channel APK is published beside the update options and deliberately left out of
    # release-manifest.json: the updater must never offer a build that cannot update itself.
    fdroid_asset = release_artifact.fdroid_asset_name(manifest["versionName"])
    assert fdroid_asset not in {a["file"] for a in manifest["apks"]}, "The F-Droid asset must not be an update option"
    if "productFlavors" in source: expected.add(fdroid_asset)
    # Each channel's R8 mapping is published beside its APKs so a stack trace from a published build
    # can be read. Both or neither: a release published before they were published carries none.
    mapping_files = {f'Nightfall-{manifest["versionName"]}-{flavour}-mapping.txt' for flavour in ("github", "fdroid")}
    published = {p.name for p in folder.iterdir()}
    if published & mapping_files:
        assert mapping_files <= published, "Both channels' mapping files are published together"
        expected |= mapping_files
    public_notes = {"release-notes.md"} if (folder / "release-notes.md").exists() else set()
    assert published == expected | public_notes, "Unexpected/missing release files"
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
    for name in sorted(expected & mapping_files):
        text = (folder / name).read_text(encoding="utf-8", errors="replace")
        assert text.startswith("# compiler:"), "Not an R8 mapping file: " + name
        assert "dev.nightfall" in text, "Mapping file explains no Nightfall class: " + name
        print("Validated", name, "as an R8 mapping of this app", flush=True)
    version, code = manifest["versionName"], manifest["versionCode"]
    for apk in manifest["apks"]:
        file = folder / apk["file"]
        assert apk["sha256"] == sums[apk["file"]] and apk["bytes"] == file.stat().st_size
        verify_apk(file, ABIS if apk["abi"] == "universal" else {apk["abi"]}, version, code, credits, pin, levels)
        print("Validated", file.name, flush=True)
    if fdroid_asset in expected:
        file = folder / fdroid_asset
        verify_apk(file, ABIS, version, code, credits, pin, levels)
        violation = release_artifact.fdroid_violation(file)
        assert violation is None, "F-Droid asset " + violation
        print("Validated", file.name, "without network permission or updater code", flush=True)
    print("Release assets verified against source tag and pinned certificate.")


if __name__ == "__main__": main()
