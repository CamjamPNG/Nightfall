#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build the signed Nightfall F-Droid repository index from released APKs.

This runs fdroidserver's own indexer against the APKs we published, the same
tooling fdroiddata uses, and never rebuilds the app. Two properties are
enforced before anything reaches the index:

* the APK declares no network permission, so only offline-capable builds
  (the ``fdroid`` flavor, or any pre-flavor release, which is the same app)
  can be served;
* the APK carries the pinned release signing certificate in ``docs/``, so the
  repository cannot quietly start serving a differently signed build.

Repository signing is a separate key from the app signing key. It is read from
``FDROID_REPO_KEYSTORE`` / ``FDROID_REPO_KEYSTORE_PASSWORD`` through
``{env: ...}`` expansion in
``fdroid/repo/config.yml``; no key material or password is written to the tree
and the build fails loudly when a key is missing rather than publishing an
unsigned index.
"""
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
TEMPLATE = ROOT / "fdroid/repo"
STORE = ROOT / "fastlane/metadata/android"
PIN = ROOT / "docs/release-signing-certificate.sha256"
FINGERPRINT = TEMPLATE / "fingerprint.txt"
NETWORK_PERMISSIONS = (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def application_id():
    config = (ROOT / "app/build.gradle.kts").read_text()
    return re.search(r'applicationId\s*=\s*"([^"]+)"', config)[1]


def pinned_signer():
    return PIN.read_text().strip()


def inspect_apk(path):
    """Read the fields the gate decides on, and the certificate, from an APK."""
    try:
        from androguard.core.apk import APK
        from loguru import logger

        logger.remove()
    except ImportError as exc:
        raise ValueError(
            "androguard is required and comes with fdroidserver; run this script with the "
            "interpreter that has fdroidserver installed (the same one behind FDROID_BIN)"
        ) from exc

    apk = APK(str(path))
    return dict(
        path=path,
        package=apk.get_package(),
        versionName=apk.get_androidversion_name(),
        versionCode=int(apk.get_androidversion_code()),
        permissions=tuple(apk.get_permissions()),
        signers=tuple(hashlib.sha256(c.dump()).hexdigest() for c in apk.get_certificates()),
        sha256=sha256(path),
        size=path.stat().st_size,
    )


def refusal(info, package, signer):
    """Why this APK must not enter the index, or None when it is eligible."""
    if info["package"] != package:
        return f"{info['path'].name} is package {info['package']}, not {package}"
    for permission in NETWORK_PERMISSIONS:
        if permission in info["permissions"]:
            return (
                f"{info['path'].name} declares {permission}; a build that can reach the "
                "network must never be published to F-Droid users, so publish the fdroid "
                "flavor APK instead (no INTERNET permission, no updater)"
            )
    if not info["signers"]:
        return f"{info['path'].name} is not signed"
    if info["signers"] != (signer,):
        return (
            f"{info['path'].name} is signed by {', '.join(info['signers'][:2])}, not the "
            f"pinned release certificate {signer}"
        )
    return None


def retain(infos, keep):
    """The newest ``keep`` versionCodes, one APK each, newest last."""
    ordered = sorted(infos, key=lambda i: i["versionCode"])
    codes = [i["versionCode"] for i in ordered]
    require(len(codes) == len(set(codes)), "Two APKs share a versionCode: " + str(codes))
    return ordered[-keep:] if keep > 0 else ordered


def stage(out, infos, package):
    """Assemble the fdroidserver directory tree this index is generated from."""
    if out.exists():
        shutil.rmtree(out)
    (out / "repo").mkdir(parents=True)
    shutil.copyfile(TEMPLATE / "config.yml", out / "config.yml")
    out.joinpath("config.yml").chmod(0o600)
    recipes = out / "metadata" / package
    recipe = TEMPLATE / "metadata" / f"{package}.yml"
    require(recipe.exists(), "Missing repository metadata template: " + str(recipe))
    (out / "metadata").mkdir()
    shutil.copyfile(recipe, out / "metadata" / f"{package}.yml")
    english = STORE / "en-US"
    require((english / "full_description.txt").exists(), "Missing store metadata: " + str(english))
    shutil.copytree(english, recipes / "en-US")
    shutil.copyfile(english / "images/icon.png", out / "icon.png")
    for info in infos:
        shutil.copyfile(info["path"], out / "repo" / f"{package}_{info['versionCode']}.apk")


def fdroid_binary():
    found = os.environ.get("FDROID_BIN") or shutil.which("fdroid")
    require(found is not None, "fdroidserver is not installed (pip install fdroidserver==2.4.5)")
    return found


def java_tool(name):
    require(
        shutil.which(name) is not None,
        f"{name} is not on PATH; a JDK (jarsigner/keytool) is required to sign the index",
    )


def update(out):
    java_tool("jarsigner")
    java_tool("keytool")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    apksigner = None
    if sdk and (pathlib.Path(sdk) / "build-tools").is_dir():
        candidates = sorted((pathlib.Path(sdk) / "build-tools").glob("*/apksigner"))
        apksigner = str(candidates[-1]) if candidates else None
    require(
        apksigner or shutil.which("apksigner"),
        "apksigner is required to sign index-v1.jar; install Android build-tools "
        "(sdkmanager 'build-tools;35.0.0') and export ANDROID_HOME",
    )
    command = [fdroid_binary(), "update", "--clean", "--use-date-from-apk", "--pretty"]
    result = subprocess.run(command, cwd=out, capture_output=True, text=True)
    require(
        result.returncode == 0,
        "fdroid update failed:\n" + (result.stdout + result.stderr)[-4000:],
    )
    return result


def documented_fingerprint():
    """The repository identity the documentation gives users, or a refusal.

    This file is written from the signing keystore by
    tools/configure_fdroid_repo_key.py and committed, so the fingerprint in the
    client add-URL cannot drift away from the key that signs the index.
    """
    if not FINGERPRINT.exists():
        raise ValueError("Missing " + str(FINGERPRINT) + "; run tools/configure_fdroid_repo_key.py --upload")
    value = FINGERPRINT.read_text().strip().lower()
    require(
        re.fullmatch(r"[0-9a-f]{64}", value) is not None,
        "fdroid/repo/fingerprint.txt is empty or malformed: run "
        "tools/configure_fdroid_repo_key.py --upload and commit the fingerprint it writes, so the "
        "published index and the documented add-URL cannot disagree",
    )
    return value


def keystore_fingerprint():
    keystore = os.environ.get("FDROID_REPO_KEYSTORE", "")
    password = os.environ.get("FDROID_REPO_KEYSTORE_PASSWORD", "")
    require(keystore and pathlib.Path(keystore).is_file(), "FDROID_REPO_KEYSTORE is not set to an existing keystore")
    require(password, "FDROID_REPO_KEYSTORE_PASSWORD is empty")
    alias = re.search(r"(?m)^repo_keyalias:\s*(\S+)", (TEMPLATE / "config.yml").read_text())[1]
    with tempfile.TemporaryDirectory() as folder:
        der = pathlib.Path(folder) / "repo.der"
        subprocess.run(
            ["keytool", "-exportcert", "-keystore", keystore, "-alias", alias,
             "-storepass:env", "FDROID_REPO_KEYSTORE_PASSWORD", "-file", str(der)],
            check=True, capture_output=True,
            env={**os.environ, "FDROID_REPO_KEYSTORE_PASSWORD": password},
        )
        return sha256(der)


def jar_fingerprint(jar):
    output = subprocess.run(
        ["keytool", "-printcert", "-jarfile", str(jar)], check=True, capture_output=True, text=True
    ).stdout
    match = re.search(r"SHA256: ([0-9A-F:]+)", output)
    require(match is not None, f"No certificate found in {jar.name}")
    return match.group(1).replace(":", "").lower()


def verify(out, infos, package, signer):
    """Prove the published tree is exactly what we meant to publish."""
    repo = out / "repo"
    for name in ("index-v1.json", "index-v1.jar", "index.xml", "index.jar", "index-v2.json", "entry.jar"):
        require((repo / name).is_file(), "Missing " + name + " in the generated repository")
    with zipfile.ZipFile(repo / "index-v1.jar") as jar:
        require(
            any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in jar.namelist()),
            "index-v1.jar carries no signature: refusing to publish an unsigned index",
        )
    expected_codes = sorted(i["versionCode"] for i in infos)
    index = json.loads((repo / "index-v1.json").read_text())
    apps = index["apps"]
    require(len(apps) == 1 and apps[0]["packageName"] == package, "Index does not describe exactly " + package)
    versions = sorted(index["packages"][package], key=lambda v: v["versionCode"])
    require(
        [v["versionCode"] for v in versions] == expected_codes,
        f"Index lists {[v['versionCode'] for v in versions]}, expected {expected_codes}",
    )
    summary = []
    for version in versions:
        granted = [name for name, _ in version.get("uses-permission", [])]
        for permission in NETWORK_PERMISSIONS:
            require(permission not in granted, f"Index version {version['versionCode']} grants {permission}")
        require(version["signer"] == signer, f"Index version {version['versionCode']} has signer {version['signer']}")
        apk = repo / version["apkName"]
        require(apk.is_file(), "Index references a missing APK: " + version["apkName"])
        require(version["hash"] == sha256(apk), "Index hash does not match " + apk.name)
        summary.append(dict(versionCode=version["versionCode"], versionName=version["versionName"],
                            apk=apk.name, bytes=apk.stat().st_size, sha256=version["hash"],
                            permissions=granted))
    fingerprint = jar_fingerprint(repo / "index-v1.jar")
    require(
        fingerprint == keystore_fingerprint(),
        "index-v1.jar is not signed by the configured repository key",
    )
    require(
        fingerprint == documented_fingerprint(),
        f"index-v1.jar is signed by {fingerprint}, but fdroid/repo/fingerprint.txt documents "
        f"{documented_fingerprint()}; clients given the documented fingerprint would reject this index",
    )
    return dict(package=package, fingerprint=fingerprint, appSigner=signer,
                address=index["repo"]["address"], versions=summary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    build_parser = sub.add_parser("build", help="generate the signed index from release APKs")
    build_parser.add_argument("--apk", type=pathlib.Path, action="append", required=True)
    build_parser.add_argument("--out", type=pathlib.Path, required=True,
                              help="fdroidserver directory to write; it is recreated on every run")
    build_parser.add_argument("--keep", type=int, default=2,
                              help="newest versions to publish, oldest dropped (default: 2)")
    build_parser.add_argument("--summary", type=pathlib.Path,
                              help="write the verification summary JSON here")
    args = parser.parse_args()
    package = application_id()
    signer = pinned_signer()
    keystore_fingerprint()
    documented_fingerprint()
    infos = [inspect_apk(path) for path in args.apk]
    for info in infos:
        reason = refusal(info, package, signer)
        require(reason is None, reason)
    require(args.keep >= 1, "--keep must be at least 1")
    kept = retain(infos, args.keep)
    require(kept, "No APKs to index")
    stage(args.out, kept, package)
    log = update(args.out)
    shutil.rmtree(args.out / "repo/status", ignore_errors=True)
    summary = verify(args.out, kept, package, signer)
    summary["dropped"] = sorted(i["versionCode"] for i in infos if i not in kept)
    output = json.dumps(summary, indent=2)
    if args.summary:
        args.summary.write_text(output + "\n")
    print(log.stderr.strip())
    print(output)


if __name__ == "__main__":
    main()
