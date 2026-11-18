# F-Droid submission preparation

This directory contains the proposed `fdroiddata` recipe for Nightfall. It does
not submit the app or assert that F-Droid has accepted it.

Source: https://github.com/CamjamPNG/Nightfall
Application ID: `io.github.camjampng.nightfall`
Release versions/codes: see `app/build.gradle.kts` and this directory's recipe;
each build targets its matching annotated `vVERSION` tag.
License: MIT for original code; separately licensed recordings and dependencies.

The maintainer-side status, requirement checklist, blocker list and
reproducibility measurements are in [`docs/FDROID.md`](../docs/FDROID.md). This
file is the review-facing summary of what the recipe asserts.

## Self-hosted repository (separate from this recipe)

`fdroid/repo/` is not part of the inclusion recipe. It is the configuration for
our own signed repository at
`https://camjampng.github.io/nightfall/fdroid/repo`, which serves the APKs we
publish so F-Droid-client users can install and update Nightfall without waiting
for `fdroiddata`. F-Droid has not verified those builds and they are signed by
the maintainer's repository key rather than by F-Droid. Only offline builds
enter that index, and
[`tools/fdroid_repo.py`](../tools/fdroid_repo.py) refuses any APK that declares
`INTERNET`. Procedure, secrets and limits: [`docs/FDROID.md`](../docs/FDROID.md).

## Build and validate

Use a free OpenJDK 17 or later, Android SDK 35, Build Tools 35.0.0 and Gradle 8.11.1.
The measured toolchain is OpenJDK 21.0.13; nothing in the build requires a JDK
newer than 17.
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
fdroid scanner io.github.camjampng.nightfall
fdroid checkupdates --auto io.github.camjampng.nightfall
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
  permission. It uses Android's selected media route. This describes the source
  the recipe builds today. Once the update channel splits the build into
  `github` and `fdroid` product flavors, only the `fdroid` flavor keeps that
  property, and the recipe must name it (see below).
- Source builds produce an unsigned release APK. GitHub releases use a
  persistent production key; its public certificate is in `docs/`. The earlier
  user-test APK was debug signed. `Binaries` and `AllowedAPKSigningKeys` remain
  absent: default F-Droid-managed signing is proposed as a separate channel.
  Switching to upstream-signature reproduction requires independent agreement
  and validation; local unsigned reproducibility alone is insufficient.
- The release ID uses the maintainer's GitHub namespace. The earlier development
  APK used `dev.nightfall`; it can stay installed separately. Export mixes as
  JSON from the development app and import into the release app. Do not remove
  the development app before exporting mixes you want to retain.

## Submission status and remaining work

1. **Blocks for releases after the flavor split must select the `fdroid` build
   flavor.** Each build block currently asks for no flavor, which fdroidserver
   runs as `assembleRelease`. The four existing blocks pin tags `v1.0` …
   `v1.0.1.2`, which predate any flavors, so they stay as they are: at those
   tags `assembleRelease` is the single offline variant, and naming a flavor
   there would ask for a task that tag does not have. Once a release tag defines
   `github` and `fdroid`, its block needs `gradle: [fdroid]`, which fdroidserver
   runs as `assembleFdroidRelease` and finds at
   `app/build/outputs/apk/fdroid/release/`. `tools/release_version.py` writes new
   blocks from a template and needs the same change from that release onward.
2. Source is public. Each recipe build targets its matching release tag;
   verify it with `git ls-remote --tags https://github.com/CamjamPNG/Nightfall.git`.
3. Review the development-age/user-adoption policy: the app was first published
   on 12 November 2026. F-Droid's current policy requests three months of active
   development unless a listed exception applies, such as proven users. No
   exception or age is claimed.
4. Physical Pixel 7 / Bluetooth-mask / overnight listening and battery tests
   are still needed. Software-emulator ANRs must not be reported as passed
   instrumentation tests. See `docs/TESTING.md`.
5. F-Droid maintainers must independently assess the source, licenses,
   dependencies, build recipe and applicable Anti-Features.

Verified locally on 18 November 2026 with fdroidserver 2.4.5: `fdroid readmeta`,
`fdroid lint` and `fdroid rewritemeta` pass with no changes to the recipe,
`fdroid scanner` reports wrapper removals only across all four release tags, and
`fdroid checkupdates --auto` detects 1.0.1.2 (code 4) from the tags. Five clean
release builds at tag `v1.0.1.2` across two machines produced byte-identical APKs,
and their payload matches the published universal APK entry for entry;
`apksigcopier compare` does not verify against the published APK because
`tools/package_release.py` recompresses 16 entries while packaging. No build has
run on F-Droid's buildserver, so nothing here demonstrates that F-Droid's own
toolchain reproduces these bytes. GitLab credentials are not connected in this
workspace, so no inclusion request or merge request has been posted. The prepared
request text is in [SUBMISSION.md](SUBMISSION.md). Hardware verification remains
a product-quality task, separate from policy eligibility.

## Official references

- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Build_Metadata_Reference/
