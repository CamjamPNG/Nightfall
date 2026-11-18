# F-Droid inclusion

Status on 18 November 2026: the recipe validates against F-Droid's own tooling
at 1.0.1.2 (versionCode 4), and the store metadata F-Droid reads from this
repository is complete. Nightfall is **not ready to submit**, for two reasons
that are not about metadata quality: the recipe still builds the release variant
that the update channel will make network-capable, and F-Droid's inclusion
policy asks for three months of active development while the project is six days
old. Both are listed under [Blockers](#blockers).

This document is the maintainer-side view. The request text is in
[`fdroid/SUBMISSION.md`](../fdroid/SUBMISSION.md), the build recipe is
[`fdroid/metadata/io.github.camjampng.nightfall.yml`](../fdroid/metadata/io.github.camjampng.nightfall.yml),
and the review-facing notes are in [`fdroid/README.md`](../fdroid/README.md).

## What an inclusion request needs

Requirements come from F-Droid's [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/),
the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
and the [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).

| Requirement | State | Evidence |
| --- | --- | --- |
| Public source repository, FOSS licence | Met | MIT in `LICENSE`; `License: MIT` in the recipe |
| FOSS-only dependencies, no GMS/Firebase/ads | Met | `docs/verification/dependency-audit.json`, `release-dependencies.txt` |
| A tag per release | Met | `v1.0`, `v1.0.1`, `v1.0.1.1`, `v1.0.1.2`, each an annotated tag on its release commit |
| Localised store metadata in the source repo | Met | `fastlane/metadata/android/en-US/`: title, summary, description, icon, two screenshots, changelog per versionCode |
| Build recipe for the F-Droid buildserver | Met for today's source | `fdroid/metadata/io.github.camjampng.nightfall.yml`, four build blocks |
| Recipe passes metadata validation | Met | `fdroid lint` clean and `fdroid rewritemeta` idempotent with fdroidserver 2.4.5 |
| Source scan clean | Met | `fdroid scanner` over all four tags: wrapper removals only, no warnings |
| Update detection works | Met | `fdroid checkupdates --auto` moved 1.0.1.1/3 to 1.0.1.2/4 from the tags |
| No tracking, accounts or ad SDKs | Met | No such permission or dependency; see `PRIVACY.md` |
| Anti-features declared honestly | Met | None apply; see [Anti-features](#anti-features) |
| Builds the variant F-Droid should ship | **No** | Recipe builds `assembleRelease`, which becomes the network-capable variant |
| Policy development history | **No** | First published 12 November 2026 |
| Screenshots showing the shipped version | Weak | Real captures, but not refreshed for 1.0.1.2 |

## Store metadata

F-Droid takes `Name`, `Summary`, `Description`, the icon, the screenshots and the
"what's new" text from the app's own repository, so those files stay under the
maintainer's control and are pulled on every release. The recipe therefore
declares no `Summary` or `Description`: per the metadata reference, fields in the
recipe would override the localised files and drift out of date.

`fastlane/metadata/android/en-US/` covers F-Droid's Latest-tab requirements —
title (10 characters), summary (68, under 80, no trailing dot), description
(1727, under 4000), a 512×512 PNG icon, two 720×1600 PNG screenshots, and a
changelog for every existing versionCode. Only `en-US` exists, which is F-Droid's
fallback language, so nothing is translated yet.
`tools/check_release.py` enforces the rest on every release, so the metadata
cannot silently fall outside the format between releases.

The one soft spot is the ("what's new") text. `changelogs/4.txt` reads
`- hotfix(release): verify signing identity before automatic publication`,
because the release workflow derives it from commit subjects. That is accurate
and developer-shaped. F-Droid shows it verbatim in the client, so user-facing
release notes are worth writing before submission.

`Categories: Ambient Sound` is a real category in `fdroiddata`'s
`config/categories.yml` ("Play ambient sound, noise"), not an invented one.

## Blockers

### The recipe builds the wrong variant (waiting on build flavors)

The recipe's build blocks run `subdir: app` with `gradle: yes`, which fdroidserver
turns into `assembleRelease` (`build.py`: `flavors == ['yes']` means no flavor
prefix). Once the update channel lands as `github` and `fdroid` product flavors,
`assembleRelease` stops meaning "the offline app": it would build the
network-capable variant with the updater and `INTERNET`.

The migration is per block, not across the whole file. F-Droid checks out each
block's own commit, so a block pinned to `v1.0` … `v1.0.1.2` must keep
`gradle: yes`: those tags have no product flavors, `assembleFdroidRelease` does
not exist in them, and switching them would make a legitimate re-build of a
published version fail. They are also still correct as written — at those tags
`assembleRelease` is the single offline variant, with no network permission and
no updater. Only blocks whose tag contains the `fdroid` flavor name it:

```yaml
    gradle:
      - fdroid
```

fdroidserver turns that into `assembleFdroidRelease`, and it finds the resulting
`app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk` on its own
(`build.py` appends `build/outputs/apk/<flavor>/release` to its search), so
`subdir: app` stays correct and no `output:` override is needed.

`tools/release_version.py` also writes new build blocks from a hardcoded
template, so that template has to emit `- fdroid` from the first release that
carries the flavor; otherwise every automatic release adds a block that builds
the network-capable variant. Both files are outside this document's ownership
(build config and release tooling), which is why the change is sequenced rather
than applied here.

### Development history

The Inclusion Policy, Quality Control item 1.6, asks that an application "should
have been actively developed for at least 3 months", with exceptions for apps
with proven users, authors who already maintain other F-Droid apps, and apps
written only for an event. Nightfall's first published release was 12 November
2026. None of the three exceptions is true today, and inventing evidence for one
would be a lie. On the policy as written, the earliest ordinary submission date
is around 12 February 2027.

## Anti-features

Nightfall declares no anti-features, and that is the honest answer rather than a
convenient one: the F-Droid build contains no network code, no updater and no
`INTERNET` permission, so there is nothing for `NonFreeNet`, `Tracking` or an
updater label to describe. F-Droid's Inclusion Policy (Security & Legal
Compliance, item 5) permits in-app self-updating only as clearly explained opt-in
consent that tells the user they are bypassing F-Droid's checks; a build with no
updater at all satisfies that rule by having nothing to consent to. If the recipe
ever built the `github` variant instead, the result would need `NonFreeNet` (the
GitHub release API is a proprietary network service) and would be the harder
review, which is the whole reason the recipe must name the `fdroid` flavor.

The bundled recordings are not an anti-feature either: the policy's
`NonFreeAssets` test is a licence that restricts commercial use or derivative
works, and the CC BY, CC BY-SA and CC0 recordings in `AUDIO_LICENSES.md`
restrict neither. Share-alike obligations are met by shipping the credits and
sidecars alongside the audio.

## Reproducible builds

The honest answer to "does a second machine reproduce it today" is **yes for the
release APK on a matched toolchain, and nothing beyond that**. Measured on 18
November 2026.

**Two machines, identical bytes.** A detached worktree at tag `v1.0.1.2`
(`43fbb9c07d0baf33a5aaf22ecb2dd731be3371ce`) was built on two separate machines
with `./gradlew --no-daemon :app:assembleRelease` under Ubuntu OpenJDK 21.0.13,
the Gradle 8.11.1 wrapper, and a freshly downloaded Android SDK 35 with
build-tools 35.0.0, deleting build outputs between runs. Everything the two
machines shared was in the repository or public: the JDK release, the Gradle
wrapper and its pinned checksum, and the Maven artifacts. Four builds across the
two machines produced the same file, confirmed with `cmp` as well as
`sha256sum`:

```
sha256 e2c1d44469b984ef8ae7ec29325f136e8b6422fbc7b0b7e8de964235e55ed431
91167049 bytes
```

One of those builds also ran the shape fdroidserver actually uses —
`assembleRelease` invoked from the `app/` subdirectory that the recipe's
`subdir: app` points at — and produced the same hash, so the recipe's invocation
is covered and not only the project's own build command.

**Source to shipped payload.** The rebuild was compared with the published
`Nightfall-1.0.1.2-universal.apk`. All 270 zip entries match on CRC-32,
uncompressed size and compression method, and the published APK adds only the
three v1 signature files. The two files are still not byte-identical: 16 entries
carry a different *compressed* size, because `tools/package_release.py` re-zips
the APK (to drop signature files and filter native libraries) before aligning and
signing it, and Python's zlib does not emit the same deflate stream as AGP's.
Re-running that same filtering and `zipalign -P 16` step reproduces the published
APK's compressed sizes and CRCs for all 270 entries, which confirms the
difference is the packaging step and not the source.

**What that costs.** `apksigcopier compare`, the check F-Droid uses for
upstream-signature reproduction, does not verify against the published APK
(`APK Signature Scheme v3 signer #1: APK integrity check failed`, apksigner 0.9),
because the chunked digest covers the recompressed entries. So upstream-signature
reproduction would require `tools/package_release.py` to stop recompressing
entries, and then an independent machine to produce the same bytes. Neither is
true today. The recipe therefore sets no `Binaries` and no
`AllowedAPKSigningKeys`: F-Droid signs its own builds with its own key, which is
a separate installation channel from the GitHub APKs signed with the pinned
certificate in `docs/`. Users switching between the two channels get a signature
mismatch and must reinstall; that is documented in `README.md` and
`docs/RELEASING.md`.

**What is still unverified.** F-Droid's buildserver: it builds on its own
hardware with Debian-packaged JDK and build tools and replaces the Gradle
wrapper, so its bytes are not compared to ours anywhere. Matching a second Capy
machine establishes that the build does not depend on one machine's state; it
does not establish that F-Droid's toolchain lands on the same bytes. And
reproducible build bytes are not a product claim: nothing here exercised the app
on hardware. Today's numbers are recorded in
`docs/verification/fdroid-reproduction-2026-11-18.json`.

## Screenshots

F-Droid shows screenshots from the source repository, and the two in
`fastlane/metadata/android/en-US/images/phoneScreenshots/` are real captures of
the running app from a software-emulated Pixel 7 profile, not mockups
(`docs/TESTING.md` records that run). They were committed once, in the first
release commit on 12 November 2026, and have not been recaptured since, so they
show the app before the update-channel work and whatever UI that adds. They
cannot be refreshed on these machines: there is no `/dev/kvm`, so an emulator
does not run.

The route that does not need an emulator is JVM rendering: Paparazzi, or
Robolectric with native graphics, capturing the real Compose screens in a unit
test and writing PNGs into the fastlane tree. That is a self-contained piece of
work — a test source set, a Gradle plugin, a capture task and a checked-in output
directory — and belongs in its own change rather than in this metadata job. A
`featureGraphic.png` (landscape promo banner) would come from the same pipeline;
it is optional, and without it the listing shows the first screenshot as the
header image.

## Submitting

Steps, in order, once the two blockers are cleared:

1. `fdroid build --server` one version against the final recipe in a full
   `fdroiddata` checkout, so the buildserver proves the recipe before review.
2. Fork `fdroiddata`, add
   `metadata/io.github.camjampng.nightfall.yml`, run `fdroid readmeta`,
   `fdroid rewritemeta`, `fdroid lint` and `fdroid build`, and open a merge
   request using the text in `fdroid/SUBMISSION.md`.
3. Answer review. Acceptance, merging and appearance in the repository are three
   separate steps; none of them is automatic.

No GitLab account is connected to this workspace, so nothing has been submitted
and no merge request exists.

## Verifying this document

Every claim above about the recipe was produced with fdroidserver 2.4.5 in a
throwaway `fdroiddata`-shaped directory holding the recipe, `fdroiddata`'s
`config/categories.yml` and its category icons:

```sh
fdroid readmeta                                   # parses, no errors
fdroid rewritemeta io.github.camjampng.nightfall   # no changes: canonical form
fdroid lint io.github.camjampng.nightfall          # clean
fdroid scanner io.github.camjampng.nightfall       # wrapper removals only, 4 tags
fdroid checkupdates --auto io.github.camjampng.nightfall
```

`checkupdates` was run with `CurrentVersion: 1.0.1.1` / `CurrentVersionCode: 3`
in the working copy to prove detection actually moves; it reported
`...updating to version 1.0.1.2 (4)`. Re-running it against the committed recipe
is a no-op, which is why the moving-update check is described rather than left
to inference.
