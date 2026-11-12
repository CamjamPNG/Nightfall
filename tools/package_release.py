#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create and verify five consistently signed APK options from one unsigned build."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
PACKAGE = "io.github.camjampng.nightfall"


def filter_apk(source, output, abi):
    """Preserve every resource/PCM byte; filter only packaged native libraries."""
    with zipfile.ZipFile(source) as src, zipfile.ZipFile(output, "w") as dst:
        present = {n.split("/")[1] for n in src.namelist() if n.startswith("lib/") and n.endswith(".so")}
        if abi is not None and abi not in present: raise ValueError("Missing native ABI: " + abi)
        for info in src.infolist():
            name = info.filename
            if name.startswith("META-INF/") and re.search(r"\.(SF|RSA|DSA|EC)$", name, re.I): continue
            if abi and name.startswith("lib/") and name.split("/")[1] != abi: continue
            with src.open(info) as read, dst.open(info, "w") as write:
                shutil.copyfileobj(read, write, 1024 * 1024)


def sha256(path):
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256")
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+){0,2}", args.version): raise ValueError("Invalid version")
    sdk = pathlib.Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    key = pathlib.Path(os.environ["NIGHTFALL_KEYSTORE_PATH"])
    if not key.is_file() or not os.environ.get("NIGHTFALL_KEYSTORE_PASSWORD"):
        raise ValueError("A persistent private signing key and password are required")
    pin = (ROOT / "docs/release-signing-certificate.sha256").read_text().strip()
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = dict(versionName=args.version, versionCode=args.code, applicationId=PACKAGE,
                    minSdk=26, targetSdk=35, certificateSha256=pin, apks=[])
    with zipfile.ZipFile(args.apk) as z:
        actual = {n.split("/")[1] for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")}
        if actual != set(ABIS): raise ValueError("Unexpected source ABI set: " + str(actual))
    with tempfile.TemporaryDirectory(prefix="nightfall-packaging-") as folder:
        temp = pathlib.Path(folder)
        for variant in ("universal", *ABIS):
            abi = None if variant == "universal" else variant
            raw, aligned = temp / "filtered.apk", temp / "aligned.apk"
            filter_apk(args.apk, raw, abi)
            subprocess.run([str(sdk / "zipalign"), "-P", "16", "-f", "4", str(raw), str(aligned)], check=True)
            name = f"Nightfall-{args.version}-{variant}.apk"
            output = args.out / name
            subprocess.run([str(sdk / "apksigner"), "sign", "--ks", str(key), "--ks-key-alias", "nightfall",
                            "--v4-signing-enabled", "false",
                            "--ks-pass", "env:NIGHTFALL_KEYSTORE_PASSWORD", "--key-pass", "env:NIGHTFALL_KEYSTORE_PASSWORD",
                            "--out", str(output), str(aligned)], check=True)
            cert = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(output)], text=True)
            fingerprints = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", cert)
            if fingerprints != [pin]: raise ValueError("Signing certificate differs from pinned release identity")
            subprocess.run([str(sdk / "zipalign"), "-c", "-P", "16", "4", str(output)], check=True)
            badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(output)], text=True)
            for field in (f"name='{PACKAGE}'", f"versionName='{args.version}'", f"versionCode='{args.code}'"):
                if field not in badging.splitlines()[0]: raise ValueError("APK version/package mismatch")
            with zipfile.ZipFile(output) as z:
                included = {n.split("/")[1] for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")}
                if included != (set(ABIS) if abi is None else {abi}): raise ValueError("Incorrect APK architecture")
            manifest["apks"].append(dict(file=name, abi=variant, bytes=output.stat().st_size, sha256=sha256(output)))
            print("Verified", name, flush=True)
    metadata = args.out / "release-manifest.json"
    metadata.write_text(json.dumps(manifest, indent=2) + "\n")
    certificate = args.out / "nightfall-release-certificate.pem"
    shutil.copyfile(ROOT / "docs/nightfall-release-certificate.pem", certificate)
    files = [args.out / item["file"] for item in manifest["apks"]] + [metadata, certificate]
    (args.out / "SHA256SUMS.txt").write_text("".join(f"{sha256(file)}  {file.name}\n" for file in files))


if __name__ == "__main__": main()
