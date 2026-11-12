#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Upload the existing private release identity to GitHub's encrypted secrets."""
import argparse
import base64
import hashlib
import json
import os
import pathlib
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--keystore", type=pathlib.Path, required=True)
parser.add_argument("--credentials", type=pathlib.Path, required=True,
                    help="Private JSON file with password and alias; never commit this file")
args = parser.parse_args()
credentials = json.loads(args.credentials.read_text())
env = os.environ.copy()
env["NIGHTFALL_KEYSTORE_PASSWORD"] = credentials["password"]
keytool = str(pathlib.Path(env["JAVA_HOME"]) / "bin/keytool") if env.get("JAVA_HOME") else "keytool"
certificate = subprocess.check_output([keytool, "-exportcert", "-keystore", str(args.keystore),
                                      "-alias", credentials["alias"], "-storepass:env", "NIGHTFALL_KEYSTORE_PASSWORD"], env=env)
pin = (pathlib.Path(__file__).resolve().parents[1] / "docs/release-signing-certificate.sha256").read_text().strip()
if credentials["alias"] != "nightfall" or hashlib.sha256(certificate).hexdigest() != pin:
    raise ValueError("This is not Nightfall's existing release signing identity; do not rotate it")
for name, value in [("NIGHTFALL_KEYSTORE_BASE64", base64.b64encode(args.keystore.read_bytes())),
                    ("NIGHTFALL_KEYSTORE_PASSWORD", credentials["password"].encode())]:
    subprocess.run(["gh", "secret", "set", name, "--repo", "CamjamPNG/Nightfall"], input=value, check=True)
    print("Configured", name)
print("Signing secrets configured. Future pushes to main can publish signed updates.")
