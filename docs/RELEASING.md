# GitHub releases

Each public release has six **signed, installable APKs**, a checksum file,
release manifest and public signing certificate. GitHub also provides source
archives. Five come from the `github` flavour — the one with the in-app updater —
and one from the `fdroid` flavour, which has no updater and no network permission.
All of them contain the complete offline library; architecture variants only
omit incompatible native libraries. Their size differences are small because
audio dominates the download. Android 8.0+ is required.

| APK option | Device |
| --- | --- |
| arm64-v8a | Pixel 7 and most modern Android phones — recommended |
| universal | All supported architectures; choose this if unsure |
| armeabi-v7a | Older 32-bit ARM phones |
| x86_64 | 64-bit Intel/AMD Android emulators or compatible devices |
| x86 | Older 32-bit Intel Android emulators or compatible devices |
| fdroid-universal | All architectures, no updater and no network permission |

Use Releases → Assets, download one APK and open it on Android. Updates from
this GitHub channel retain the application ID and signing certificate, allowing
Android to replace an older version while retaining local data.

After the first manual install, this channel can update itself: the app reads
`release-manifest.json` from the latest release, picks the APK for the device's
architecture, verifies size, SHA-256 and signing certificate against the installed
app, and installs it when the user taps Update. On Android 12+ with this target
SDK the install needs no further prompt; where an OEM image still asks, Android's
own confirmation is shown. Nothing is downloaded or installed without that tap,
and nothing is sent to GitHub beyond the request itself. The earlier
`dev.nightfall` development package is separate, and so is a build F-Droid signs
with its own key unless a later independent reproducible-build/signature
configuration is agreed. Export mixes before switching packages or signing
channels; uninstalling deletes private app data.

## The F-Droid channel asset

`Nightfall-<version>-fdroid-universal.apk` is the `fdroid` flavour signed with the
same pinned key as the update options. It is one universal APK rather than four
architecture options, because it exists for a repository index, and it is not an
entry in `release-manifest.json`: the updater will never offer a build that
cannot update itself. Its name is a contract — `tools/verify_release.py` and
`tools/check_release.py` refuse a release whose asset is named otherwise, and the
F-Droid repository workflow selects the index entry by that name and accepts no
substitute. A release published before the asset existed carries none, so it is
skipped with a warning naming it; when the whole release window has none, the
site still deploys with the landing page alone and no `fdroid/repo` under it,
which the first release carrying the asset fills in.

Publishing it is deliberate about two things. Because it carries the pinned
signature, it installs over a `github`-channel install with private data intact,
and it then has no updater: an update must come from whoever serves this asset.
Because it declares no network permission and contains no updater class, an
index built from it passes the same offline claim the app makes. Both facts are
checked on the signed file before it is uploaded, not inferred from the source:
no `INTERNET` or `ACCESS_NETWORK_STATE`, no updater classes in the dex, and the
pinned certificate fingerprint.

## Release manifest

`tools/package_release.py` writes `release-manifest.json` beside the APKs:

```json
{
  "versionName": "1.0.2", "versionCode": 5,
  "applicationId": "io.github.camjampng.nightfall",
  "minSdk": 26, "targetSdk": 35,
  "certificateSha256": "<pinned release identity>",
  "apks": [{"file": "Nightfall-1.0.2-arm64-v8a.apk", "abi": "arm64-v8a",
            "bytes": 91097092, "sha256": "<digest>",
            "url": "https://github.com/CamjamPNG/Nightfall/releases/download/v1.0.2/Nightfall-1.0.2-arm64-v8a.apk"}]
}
```

The manifest lists the five `github`-flavour update options only. The
`fdroid`-flavour asset is published alongside them and deliberately absent here,
so the updater never downloads a build that has no updater of its own. The app
reads this asset through `releases/latest/download/release-manifest.json`
and accepts an APK address only when it is exactly this repository's release
download for that version and file name. `tools/verify_release.py` re-checks the
URLs against the tag before anything staged is published, so a manifest that
points elsewhere cannot ship. Older manifests published before the `url` field
existed are not re-published; `latest` always resolves to the newest release.

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
   New F-Droid recipe entries declare `gradle: - fdroid`, so F-Droid builds the
   flavour with no updater — one APK per build, and the recipe's tags from the
   flavour split onward keep that selector.
3. Runs release-tool tests, core audio tests, compilation, lint and asset checks.
4. Filters the universal APK into real architecture options, aligns to 16 KiB,
   signs all six APKs with the persistent key, verifies
   signatures/package/version, and re-checks the F-Droid asset carries no network
   permission and no updater code.
5. Atomically pushes the version commit and annotated tag, then publishes the
   APKs, hashes and public certificate to GitHub Releases.

Release commits do not recursively trigger more releases. Concurrent source
changes cannot be overwritten: an atomic push fails if `main` changed during
the build; the next queued run includes the accumulated changes. Re-running
after a tag was pushed can complete a missing release without bumping again.
Existing published assets are not silently replaced. Branch protection must
permit the release bot to push version commits; otherwise the workflow fails
without overwriting source. PR validation remains read-only and uses no key.

## Signing setup

A persistent RSA release key has been generated **outside the repository**.
The private key and credentials are in `/workspace/.nightfall-signing/`, mode
0700 for the directory and 0600 for private files. Back up both private files
securely under the owner's control before discarding this workspace. They are
not present in release downloads, logs, source archives or Git history.
The public identity is in `release-signing-certificate.sha256` and
`nightfall-release-certificate.pem` beside this document.

The owner has installed the two repository Actions secrets, and a manual
workflow run confirmed they are readable. The credential check also verifies
the keystore password and compares its exported certificate against the pinned
release identity, including when a run has no new version to publish. The full
build/sign/publish workflow runs on subsequent source updates.

The workspace GitHub connection cannot administer Actions secrets (HTTP 403).
For a future authorized setup or recovery, the required secrets are:

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
./gradlew :core:test :app:testGithubDebugUnitTest :app:assembleGithubRelease :app:assembleFdroidRelease :app:lintRelease
python3 tools/check_release.py \
  --apk app/build/outputs/apk/github/release/app-github-release-unsigned.apk \
  --fdroid-apk app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
# Load NIGHTFALL_KEYSTORE_PASSWORD securely; never put its value in command history.
export NIGHTFALL_KEYSTORE_PATH=/secure/path/nightfall-release.p12
python3 tools/package_release.py \
  --apk app/build/outputs/apk/github/release/app-github-release-unsigned.apk \
  --fdroid-apk app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk \
  --out artifacts/publish --version NEXT_VERSION --code NEXT_CODE
python3 tools/check_release.py \
  --fdroid-asset artifacts/publish/Nightfall-NEXT_VERSION-fdroid-universal.apk
```

Only tag and publish the matching tested source commit. A failed release must
not be advertised as passing. F-Droid submission remains a separate review;
GitHub release automation does not imply F-Droid inclusion.

### Upload-endpoint recovery

This session's GitHub credentials can push source and create releases, but the
`uploads.github.com` endpoint returned HTTP 401 for binary uploads. A recovery
workflow, `publish-staged-release.yml`, accepts locally signed assets through a
temporary `release-assets/vVERSION` branch. It verifies them against the source
tag, all five audio payloads, checksums, architecture, pinned signer and the
F-Droid asset's offline claims before
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
