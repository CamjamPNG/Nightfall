#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create and verify the published APK assets from the two unsigned channel builds."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import release_artifact  # noqa: E402

ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
# R8 renames classes in both release channels, so each one's mapping file is the only way to read a
# stack trace from its APK. They are published as separate assets rather than per architecture: the
# mapping is identical across the architecture options of one channel.
MAPPING_FLAVOURS = ("github", "fdroid")
PACKAGE = "io.github.camjampng.nightfall"
REPOSITORY = "CamjamPNG/Nightfall"
# 1 was the original document: version, package, SDK levels, certificate, APKs.
# 2 adds `log`: the recent releases with their notes, so the app can list what changed.
SCHEMA = 2
LOG_VERSIONS = 8
LOG_NOTES = 4
LOG_NOTE_CHARS = 120
LOG_HEADING = re.compile(r"(?m)^## +(\d+\.\d+(?:\.\d+){0,2})(?: +[—–-] +(\d{4}-\d{2}-\d{2}))? *$")
ISSUE_REFERENCE = re.compile(r"\s*\(#\d+\)\s*$")


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


def mapping_name(version, flavour):
    """The published name of one channel's mapping file; it names the build it explains."""
    if flavour not in MAPPING_FLAVOURS: raise ValueError("Unknown mapping flavour: " + str(flavour))
    return f"Nightfall-{version}-{flavour}-mapping.txt"


def checked_mapping(source):
    """One R8 mapping file's text, refused unless it is one and covers this app's classes."""
    try: text = pathlib.Path(source).read_text(encoding="utf-8", errors="replace")
    except OSError: raise ValueError("Missing mapping file: " + str(source))
    if not text.startswith("# compiler:"): raise ValueError("Not an R8 mapping file: " + str(source))
    if "dev.nightfall" not in text: raise ValueError("Mapping file explains no Nightfall class: " + str(source))
    return text


def sha256(path):
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256")
    return digest.hexdigest()


def release_url(version, name):
    """The address the app downloads one variant from; the parser in the app checks it exactly."""
    return f"https://github.com/{REPOSITORY}/releases/download/v{version}/{name}"


def plain_note(line):
    """One changelog line as the app shows it: a single bounded sentence, no issue reference."""
    note = " ".join(ISSUE_REFERENCE.sub("", line).split())
    if len(note) > LOG_NOTE_CHARS: note = note[:LOG_NOTE_CHARS - 1].rstrip() + "…"
    return note


def release_log(path=None, versions=LOG_VERSIONS):
    """The recent releases and their notes, read from the changelog the release workflow writes.

    Never hand-authored: the workflow runs release_version.py before this tool, so the changelog's
    newest section is the release being packaged. A missing or unreadable changelog publishes no
    notes rather than failing a release the app can otherwise install.
    """
    path = pathlib.Path(path or ROOT / "CHANGELOG.md")
    try: text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError): return []
    headings = list(LOG_HEADING.finditer(text))
    entries, seen = [], set()
    for index, heading in enumerate(headings):
        version, date = heading.group(1), heading.group(2) or ""
        if version in seen: continue
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        notes = []
        for line in text[heading.end():end].splitlines():
            if not line.lstrip().startswith("- "): continue
            note = plain_note(line.lstrip()[2:])
            if note and note not in notes: notes.append(note)
            if len(notes) == LOG_NOTES: break
        if not notes and not date: continue
        seen.add(version)
        entries.append(dict(version=version, date=date, notes=notes))
        if len(entries) == versions: break
    return entries


def release_manifest(version, code, apks, log=None):
    """The machine-readable manifest published with each release, read by the in-app updater."""
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+){0,2}", version): raise ValueError("Invalid version")
    if not 1 <= code <= 2100000000: raise ValueError("Invalid version code")
    if len(apks) != 5: raise ValueError("Expected five APK options")
    seen = set()
    entries = []
    for apk in apks:
        name, abi = apk["file"], apk["abi"]
        if abi not in ABIS + ("universal",): raise ValueError("Unknown ABI: " + str(abi))
        if abi in seen: raise ValueError("Duplicate ABI: " + abi)
        seen.add(abi)
        if not name.endswith(".apk") or "/" in name: raise ValueError("Invalid APK name: " + name)
        if not re.fullmatch(r"[a-f0-9]{64}", apk["sha256"]): raise ValueError("Invalid checksum for " + name)
        if not isinstance(apk["bytes"], int) or apk["bytes"] <= 0: raise ValueError("Invalid size for " + name)
        entries.append(dict(file=name, abi=abi, bytes=apk["bytes"], sha256=apk["sha256"],
                            url=release_url(version, name)))
    releases = []
    for release in release_log() if log is None else log:
        if not isinstance(release, dict): raise ValueError("Invalid release log entry")
        if not re.fullmatch(r"\d+\.\d+(?:\.\d+){0,2}", str(release.get("version", ""))):
            raise ValueError("Invalid release log version: " + str(release.get("version")))
        notes = release.get("notes", [])
        if not isinstance(notes, list) or not all(isinstance(note, str) for note in notes):
            raise ValueError("Invalid release notes for " + str(release["version"]))
        releases.append(dict(version=release["version"], date=str(release.get("date", "")),
                             notes=[note for note in (plain_note(n) for n in notes) if note][:LOG_NOTES]))
    min_sdk, target_sdk = release_artifact.sdk_levels()
    return dict(schemaVersion=SCHEMA, versionName=version, versionCode=code, applicationId=PACKAGE,
                minSdk=min_sdk, targetSdk=target_sdk, certificateSha256=pin(), apks=entries, log=releases)


def pin():
    return (ROOT / "docs/release-signing-certificate.sha256").read_text().strip()


def signed_apk(source, abi, output, version, code, identity):
    """Filter one APK to `abi`, align it, sign it with the pinned key, and prove its identity."""
    sdk = release_artifact.build_tools()
    key = pathlib.Path(os.environ["NIGHTFALL_KEYSTORE_PATH"])
    with tempfile.TemporaryDirectory(prefix="nightfall-packaging-") as folder:
        raw, aligned = pathlib.Path(folder) / "filtered.apk", pathlib.Path(folder) / "aligned.apk"
        filter_apk(source, raw, abi)
        subprocess.run([str(sdk / "zipalign"), "-P", "16", "-f", "4", str(raw), str(aligned)], check=True)
        subprocess.run([str(sdk / "apksigner"), "sign", "--ks", str(key), "--ks-key-alias", "nightfall",
                        "--v4-signing-enabled", "false",
                        "--ks-pass", "env:NIGHTFALL_KEYSTORE_PASSWORD", "--key-pass", "env:NIGHTFALL_KEYSTORE_PASSWORD",
                        "--out", str(output), str(aligned)], check=True)
    certificate = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(output)], text=True)
    if re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", certificate) != [identity]:
        raise ValueError("Signing certificate differs from pinned release identity: " + output.name)
    subprocess.run([str(sdk / "zipalign"), "-c", "-P", "16", "4", str(output)], check=True)
    badging = subprocess.check_output([str(sdk / "aapt"), "dump", "badging", str(output)], text=True)
    for field in (f"name='{PACKAGE}'", f"versionName='{version}'", f"versionCode='{code}'"):
        if field not in badging.splitlines()[0]: raise ValueError("APK version/package mismatch: " + output.name)
    if release_artifact.universal_abis(output) != (set(ABIS) if abi is None else {abi}):
        raise ValueError("Incorrect APK architecture: " + output.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True, help="Unsigned GitHub-channel release APK")
    parser.add_argument("--fdroid-apk", type=pathlib.Path, required=True, help="Unsigned F-Droid-channel release APK")
    parser.add_argument("--out", type=pathlib.Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--mapping", action="append", default=[], metavar="FLAVOUR=PATH",
                        help="R8 mapping.txt to publish for the github or fdroid channel")
    args = parser.parse_args()
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+){0,2}", args.version): raise ValueError("Invalid version")
    if not pathlib.Path(os.environ["NIGHTFALL_KEYSTORE_PATH"]).is_file() or not os.environ.get("NIGHTFALL_KEYSTORE_PASSWORD"):
        raise ValueError("A persistent private signing key and password are required")
    identity = pin()
    for source in (args.apk, args.fdroid_apk):
        if release_artifact.universal_abis(source) != set(ABIS):
            raise ValueError("Unexpected source ABI set in " + source.name + ": " + str(release_artifact.universal_abis(source)))
    args.out.mkdir(parents=True, exist_ok=True)
    signed = []
    for variant in ("universal", *ABIS):
        abi = None if variant == "universal" else variant
        name = f"Nightfall-{args.version}-{variant}.apk"
        signed_apk(args.apk, abi, args.out / name, args.version, args.code, identity)
        signed.append(dict(file=name, abi=variant, bytes=(args.out / name).stat().st_size, sha256=sha256(args.out / name)))
        print("Verified", name, flush=True)
    # The F-Droid channel ships one universal APK: no updater, no network permission. It is
    # published beside the update options but kept out of release-manifest.json, so the updater
    # can never offer a build that cannot update itself.
    asset = release_artifact.fdroid_asset_name(args.version)
    signed_apk(args.fdroid_apk, None, args.out / asset, args.version, args.code, identity)
    violation = release_artifact.fdroid_violation(args.out / asset)
    if violation: raise ValueError(asset + " " + violation)
    print("Verified", asset, "without network permission or updater code", flush=True)
    mappings = {}
    for entry in args.mapping:
        flavour, _, path = entry.partition("=")
        if flavour not in MAPPING_FLAVOURS: raise ValueError("Unknown mapping flavour: " + str(entry))
        if flavour in mappings: raise ValueError("Duplicate mapping for " + flavour)
        mappings[flavour] = path
    if mappings and set(mappings) != set(MAPPING_FLAVOURS):
        raise ValueError("Both channels' mapping files are published together")
    for flavour, path in mappings.items():
        name = mapping_name(args.version, flavour)
        (args.out / name).write_text(checked_mapping(path), encoding="utf-8")
        print("Published", name, flush=True)
    manifest = release_manifest(args.version, args.code, signed)
    print("Manifest carries", len(manifest["log"]), "release notes entries", flush=True)
    metadata = args.out / "release-manifest.json"
    metadata.write_text(json.dumps(manifest, indent=2) + "\n")
    certificate = args.out / "nightfall-release-certificate.pem"
    shutil.copyfile(ROOT / "docs/nightfall-release-certificate.pem", certificate)
    files = [args.out / item["file"] for item in signed] + [args.out / asset, metadata, certificate]
    files += [args.out / mapping_name(args.version, flavour) for flavour in mappings]
    (args.out / "SHA256SUMS.txt").write_text("".join(f"{sha256(file)}  {file.name}\n" for file in files))


if __name__ == "__main__": main()
