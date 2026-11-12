# F-Droid submission preparation

This directory contains the proposed `fdroiddata` recipe for Nightfall. It does
not submit the app or assert that F-Droid has accepted it.

Source: https://github.com/CamjamPNG/Nightfall
Application ID: `io.github.camjampng.nightfall`
Release: `1.0` / version code `1` / source tag `v1.0`
License: MIT for original code; separately licensed recordings and dependencies.

## Build and validate

Use a free OpenJDK 17 or later, Android SDK 35, Build Tools 35.0.0 and Gradle 8.11.1.
No proprietary IDE, API key, Google Play Services or Firebase is needed. Audio is
committed in source; the release build never downloads sounds.

```sh
./gradlew :core:test :app:assembleRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

For `fdroidserver` checks, copy the recipe into a full `fdroiddata` fork and run there with the SDK configured. Its `config/categories.yml` provides the current category definitions (the recipe uses `Ambient Sound`):

```sh
fdroid readmeta
fdroid rewritemeta io.github.camjampng.nightfall
fdroid lint io.github.camjampng.nightfall
fdroid scanner -e io.github.camjampng.nightfall
fdroid build --server io.github.camjampng.nightfall:1
```

Copy `metadata/io.github.camjampng.nightfall.yml` into a fork of `fdroiddata`.
Then use F-Droid's buildserver or merge-request CI for the complete independent
build and dependency scan. Do not add scanner exclusions to hide a dependency.
The Gradle wrapper JAR is the standard Gradle wrapper, not an app library;
F-Droid's source preparation handles wrapper files normally.

## Review notes

- MIT is unmodified. `COMMERCIAL_COURTESY.md` is explicitly a voluntary request
  for conversation, revenue sharing or an agreed charity contribution. It adds
  no payment, contact, donation or commercial-use condition.
- No donation endpoint has been chosen, so the recipe contains no `Donate`,
  `Liberapay`, `OpenCollective` or other unverified payment destination.
- Field recordings are non-executable media. Their creators, original URLs,
  licenses, modifications and hashes are in `AUDIO_LICENSES.md`,
  `docs/audio-credits.json`, and adjacent `.pcm.license` files. They are not
  relicensed under MIT. In particular, share-alike recording obligations remain.
- Dependencies come from Google Maven and Maven Central; Gradle plugins also
  use the standard Gradle Plugin Portal. No vendored executable library is
  bundled. AndroidX's native libraries arrive through its free Maven artifacts.
- The app has no internet, microphone, ad-ID, location or Bluetooth-scanning
  permission. It uses Android's selected media route.
- Source builds produce an unsigned release APK. The earlier user-test APK was
  debug signed; it is not a production signing reference. `Binaries` and
  `AllowedAPKSigningKeys` are deliberately absent until a production signing
  strategy and independently reproduced upstream APK exist.
- The release ID uses the maintainer's GitHub namespace. The earlier development
  APK used `dev.nightfall`; it can stay installed separately. Export mixes as
  JSON from the development app and import into the release app. Do not remove
  the development app before exporting mixes you want to retain.

## Submission status and remaining work

1. Source is public. The recipe targets the matching `v1.0` release tag;
   verify it with `git ls-remote --tags https://github.com/CamjamPNG/Nightfall.git`.
2. Review the development-age/user-adoption policy: the app is new. F-Droid's
   current policy requests three months of active development unless a listed
   exception applies, such as proven users. No exception or age is claimed.
3. Physical Pixel 7 / Bluetooth-mask / overnight listening and battery tests
   are still needed. Software-emulator ANRs must not be reported as passed
   instrumentation tests. See `docs/TESTING.md`.
4. F-Droid maintainers must independently assess the source, licenses,
   dependencies, build recipe and applicable Anti-Features.

The source and APK scanner checks passed locally with fdroidserver 2.4.5.
Two local unsigned release builds were byte-identical; this does not establish
reproduction on F-Droid's infrastructure. GitLab credentials are not connected
in this workspace, so no inclusion request or merge request has been posted.
The prepared request text is in [SUBMISSION.md](SUBMISSION.md). Hardware
verification remains a product-quality task, separate from policy eligibility.

## Official references

- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Build_Metadata_Reference/
