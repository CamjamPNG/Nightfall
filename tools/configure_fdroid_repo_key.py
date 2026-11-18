#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create the F-Droid repository signing key once and install it as Actions secrets.

This key signs the repository index, not the app: it is a separate identity from
the pinned release certificate in docs/. Run it once, from a machine whose GitHub
CLI is authorized to write repository Actions secrets:

    python3 tools/configure_fdroid_repo_key.py --keystore ~/nightfall-fdroid-repo.p12

It generates the key if the file does not exist, uploads
FDROID_REPO_KEYSTORE_BASE64 and FDROID_REPO_KEYSTORE_PASSWORD, and prints the
repository fingerprint and the client add-URL. The password is generated locally
and sent straight to GitHub; it is never printed. Keep the .p12 file: without it
the repository cannot be re-signed under the same identity.
"""
import argparse
import base64
import hashlib
import json
import os
import pathlib
import re
import secrets
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "fdroid/repo/config.yml"
SECRETS = ("FDROID_REPO_KEYSTORE_BASE64", "FDROID_REPO_KEYSTORE_PASSWORD")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def settings():
    text = CONFIG.read_text()
    alias = re.search(r"(?m)^repo_keyalias:\s*(\S+)", text)[1]
    dname = re.search(r"(?m)^keydname:\s*(.+)$", text)[1].strip()
    url = re.search(r"(?m)^repo_url:\s*(\S+)", text)[1]
    return alias, dname, url


def generate(path, alias, dname, password):
    subprocess.run(
        ["keytool", "-genkeypair", "-keystore", str(path), "-storetype", "PKCS12", "-alias", alias,
         "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000", "-dname", dname,
         "-storepass:env", "FDROID_REPO_KEYSTORE_PASSWORD",
         "-keypass:env", "FDROID_REPO_KEYSTORE_PASSWORD"],
        check=True, capture_output=True,
        env={**os.environ, "FDROID_REPO_KEYSTORE_PASSWORD": password},
    )
    path.chmod(0o600)


def fingerprint(path, alias, password):
    with tempfile.TemporaryDirectory() as folder:
        der = pathlib.Path(folder) / "repo.der"
        subprocess.run(
            ["keytool", "-exportcert", "-keystore", str(path), "-alias", alias,
             "-storepass:env", "FDROID_REPO_KEYSTORE_PASSWORD", "-file", str(der)],
            check=True, capture_output=True,
            env={**os.environ, "FDROID_REPO_KEYSTORE_PASSWORD": password},
        )
        return hashlib.sha256(der.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keystore", type=pathlib.Path, required=True,
                        help="path to the PKCS12 key; generated when it does not exist")
    parser.add_argument("--repository", default=None, help="owner/repo; defaults to the checkout's origin")
    parser.add_argument("--password", default=None,
                        help="password for an existing keystore; generated for a new one")
    parser.add_argument("--upload", action="store_true",
                        help="create FDROID_REPO_KEYSTORE_BASE64 and FDROID_REPO_KEYSTORE_PASSWORD")
    args = parser.parse_args()
    alias, dname, url = settings()
    path = args.keystore.expanduser()
    password = args.password or os.environ.get("FDROID_REPO_KEYSTORE_PASSWORD")
    if not path.exists():
        require(password or args.upload, "Pass --upload to generate the key and its password")
        password = password or secrets.token_urlsafe(32)
        generate(path, alias, dname, password)
    require(password, "An existing keystore needs its password: pass --password or set FDROID_REPO_KEYSTORE_PASSWORD")
    digest = fingerprint(path, alias, password)
    if args.upload:
        require(shutil.which("gh") is not None, "The GitHub CLI is required to install Actions secrets")
        subprocess.run(["gh", "secret", "set", SECRETS[0], "--body", base64.b64encode(path.read_bytes()).decode()] +
                       (["--repo", args.repository] if args.repository else []), check=True)
        subprocess.run(["gh", "secret", "set", SECRETS[1], "--body", password] +
                       (["--repo", args.repository] if args.repository else []), check=True, capture_output=True)
    print(json.dumps(dict(keystore=str(path), alias=alias, fingerprint=digest,
                          add_url=f"{url}?fingerprint={digest.upper()}", uploaded=bool(args.upload),
                          key_material_committed=False), indent=2))


if __name__ == "__main__":
    main()
