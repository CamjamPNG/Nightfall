# GitHub releases

Each public release has five **signed, installable APKs**, a checksum file,
release manifest and public signing certificate. GitHub also provides source
archives. All APKs contain the complete offline library; architecture variants
only omit incompatible native libraries. Their size differences are small
because audio dominates the download. Android 8.0+ is required.

| APK option | Device |
| --- | --- |
| arm64-v8a | Pixel 7 and most modern Android phones — recommended |
| universal | All supported architectures; choose this if unsure |
| armeabi-v7a | Older 32-bit ARM phones |
| x86_64 | 64-bit Intel/AMD Android emulators or compatible devices |
| x86 | Older 32-bit Intel Android emulators or compatible devices |

Use Releases → Assets, download one APK and open it on Android. Updates from
this GitHub channel retain the application ID and signing certificate, allowing
Android to replace an older version while retaining local data. Installation
still requires the user's action; Nightfall does not silently update itself or
add network access. The earlier `dev.nightfall` development package is separate.
F-Droid-managed signing is also a separate channel unless a later independent
reproducible-build/signature configuration is agreed. Export mixes before
switching packages or signing channels; uninstalling deletes private app data.

## Version policy

| Change | Example | Commit convention |
| --- | --- | --- |
| Urgent narrow hotfix | 1.0 → 1.0.0.1 | `hotfix: ...` |
| Minor fixes | 1.0 → 1.0.1 | `fix: ...` or ordinary maintenance |
| New sounds, small UI changes, substantial fixes | 1.0 → 1.1 | `feat: ...`, `sounds: ...`, `ui: ...`, `fix(major): ...` |
| Major overhaul | 1.0 → 2.0 | `feat!: ...`, another `type!: ...`, or `BREAKING CHANGE:` in the commit body |

Higher changes reset lower fields. For example, a patch after `1.0.0.3` is
`1.0.1`; a hotfix after `1.2.4` is `1.2.4.1`. Android's separate integer
`versionCode` always increases by one, including hotfixes. Every architecture
uses the same code so changing APK architecture does not change data/versioning.

The biggest declared change since the last reachable version tag wins. This
classification uses change intent in commit messages, not unreliable estimates
from line counts. Choose the correct convention when making each update.
Ordinary maintenance also produces a patch; `[skip release]` suppresses an
explicitly non-release commit. Manual Actions → Publish Android release → Run
workflow can override the level with `major`, `minor`, `patch` or `hotfix`.

## Automatic publication

`.github/workflows/release.yml` runs for pushes to `main` and manual dispatch.
Once the two signing secrets below are installed, it:

1. Checks out current `main` and computes the next version.
2. Updates Android, F-Droid build metadata, store changelog and CHANGELOG together.
3. Runs release-tool tests, core audio tests, compilation, lint and asset checks.
4. Filters the universal APK into real architecture options, aligns to 16 KiB,
   signs all APKs with the persistent key, and verifies signatures/package/version.
5. Atomically pushes the version commit and annotated tag, then publishes the
   APKs, hashes and public certificate to GitHub Releases.

Release commits do not recursively trigger more releases. Concurrent source
changes cannot be overwritten: an atomic push fails if `main` changed during
the build; the next queued run includes the accumulated changes. Re-running
after a tag was pushed can complete a missing release without bumping again.
Existing published assets are not silently replaced. Branch protection must
permit the release bot to push version commits; otherwise the workflow fails
without overwriting source. PR validation remains read-only and uses no key.

## One-time signing setup — currently blocked by connection permissions

A persistent RSA release key has been generated **outside the repository**.
The private key and credentials are in `/workspace/.nightfall-signing/`, mode
0700 for the directory and 0600 for private files. Back up both private files
securely under the owner's control before discarding this workspace. They are
not present in release downloads, logs, source archives or Git history.
The public identity is in `release-signing-certificate.sha256` and
`nightfall-release-certificate.pem` beside this document.

The current GitHub connection returned HTTP 403 when fetching the Actions
secrets public key. Automatic signed publication is **not active** until these
repository Actions secrets are installed using a suitably authorized account:

- `NIGHTFALL_KEYSTORE_BASE64`: base64 of the existing `nightfall-release.p12`.
- `NIGHTFALL_KEYSTORE_PASSWORD`: its existing private password; key alias is `nightfall`.

With GitHub CLI authenticated for repository Actions-secret writes, run:

```sh
python3 tools/configure_release_signing.py \
  --keystore /workspace/.nightfall-signing/nightfall-release.p12 \
  --credentials /workspace/.nightfall-signing/credentials.json
```

This checks the pinned public certificate and transmits secrets directly to
GitHub without printing their values. Do not paste secrets into chat, commit
them, publish the private backup, or replace the key on every build. A new key
would prevent installed users from updating normally. No temporary/debug key
fallback is used when production signing credentials are missing.

Repository secret settings:
https://github.com/CamjamPNG/Nightfall/settings/secrets/actions

## Local release / recovery

```sh
python3 -m unittest discover -s tools/tests -v
python3 tools/release_version.py --kind auto                 # preview only
python3 tools/release_version.py --kind patch --write        # prepare next version
./gradlew :core:test :app:assembleRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
# Load NIGHTFALL_KEYSTORE_PASSWORD securely; never put its value in command history.
export NIGHTFALL_KEYSTORE_PATH=/secure/path/nightfall-release.p12
python3 tools/package_release.py \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --out artifacts/publish --version NEXT_VERSION --code NEXT_CODE
```

Only tag and publish the matching tested source commit. A failed release must
not be advertised as passing. F-Droid submission remains a separate review;
GitHub release automation does not imply F-Droid inclusion.

### Upload-endpoint recovery

This session's GitHub credentials can push source and create releases, but the
`uploads.github.com` endpoint returned HTTP 401 for binary uploads. A recovery
workflow, `publish-staged-release.yml`, accepts locally signed assets through a
temporary `release-assets/vVERSION` branch. It verifies them against the source
tag, all five audio payloads, checksums, architecture and pinned signer before
the runner uploads and publishes. Only public APKs, public certificate, hashes
and public notes are transported; private keys never enter that branch. The
workflow deletes the temporary branch after successful publication. Binary Git
objects may remain on GitHub until its normal garbage collection.

```sh
python3 tools/stage_release.py --assets artifacts/publish --notes artifacts/release-notes.md
```

Use this recovery path only after the matching tested source tag is pushed.
Fully automatic builds still require the two signing secrets above; staging is
a fallback for publishing already-signed files, not a replacement signing key.
