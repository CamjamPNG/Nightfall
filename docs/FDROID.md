# F-Droid distribution

Nightfall reaches F-Droid users by two routes, and this document covers both.

**The self-hosted repository is the route that works today**: an index we build
and sign ourselves from the APKs we publish, which the official F-Droid client
can add to install Nightfall and receive updates. It is not F-Droid-verified;
adding it means trusting the maintainer's repository key. Its address is
`https://camjampng.github.io/Nightfall/fdroid/repo`, and nothing is served there
until the one-time setup below has run once. See
[the self-hosted repository](#the-self-hosted-repository).

**Inclusion in F-Droid's main repository is not submitted.** The metadata is
complete and the recipe validates against F-Droid's own tooling (measured at
1.0.1.2, versionCode 4); the one remaining blocker is the development-history
rule, not metadata quality. See [Blockers](#blockers).

This document is the maintainer-side view. The request text is in
[`fdroid/SUBMISSION.md`](../fdroid/SUBMISSION.md), the build recipe is
[`fdroid/metadata/io.github.camjampng.nightfall.yml`](../fdroid/metadata/io.github.camjampng.nightfall.yml),
and the review-facing notes are in [`fdroid/README.md`](../fdroid/README.md).

## The self-hosted repository

```
https://camjampng.github.io/Nightfall/fdroid/repo
```

Add that address in the F-Droid client (Settings → Repositories → Add). Because
it is a custom repository the client asks for a fingerprint before it will trust
the index: the add-URL is `address?fingerprint=…` with the upper-case SHA-256 of
the repository certificate, and `tools/configure_fdroid_repo_key.py` prints the
finished URL. The path keeps the repository's capital N because GitHub Pages
project paths are case-sensitive (`/Nightfall/` serves, `/nightfall/` is a 404);
that was checked against existing Pages sites, not assumed.

**What it serves, and what it does not.** Only the offline build: the `fdroid`
flavor once releases carry product flavors, and today's single offline release
variant, which is the same app with no updater, no network code and no
`INTERNET` permission. The index builder refuses any APK that declares
`INTERNET` or `ACCESS_NETWORK_STATE`, so the network-capable `github` channel
cannot enter this repository even if it is handed the wrong file. It refuses
APKs that are not signed with the pinned release certificate in `docs/` for the
same reason: two signing identities in one repository is a confusing update
path.

**Trust.** These are our builds, signed by us, on an index signed by a key
Camden holds. F-Droid has not verified the source, the build, the signature or
the bug reports, and a compromise of this repository would not be caught by
anyone else. That is the trade for getting an install and updates through the
official client while the main repository's development-history rule keeps
Nightfall out of `fdroiddata`.

**Two different keys, never interchangeable.** The app APKs keep the pinned
release certificate in `docs/` (the same identity the GitHub channel uses, so an
installed app updates normally). The repository index is signed by a separate
key whose certificate is recorded in `fdroid/repo/fingerprint.txt` and whose
private half exists only in the Actions secret. That file is committed and is
what the add-URL's fingerprint is built from, and `tools/fdroid_repo.py`
refuses to build when the key it signs with does not match it, so the published
index and the documented identity cannot disagree. Losing the key means every
existing user has to remove and re-add the repository, so keep the `.p12` and
the `<keystore>.password` file the setup writes beside it.

**How it is generated.** `.github/workflows/fdroid-repo.yml` runs when
`Publish Android release` or `Publish already signed APKs` finishes
successfully, on a published release, and on manual dispatch. The workflow_run
triggers exist because a release created with the repository's `GITHUB_TOKEN`
does not itself start a workflow run, so the release event alone would leave the
index stale. It downloads the newest two releases'
APKs, then `tools/fdroid_repo.py build` inspects each one (package, versionCode,
permissions, signing certificate), copies the accepted APKs into an
fdroidserver directory, and runs `fdroid update --clean --use-date-from-apk
--pretty`. That is the same indexer `fdroiddata` uses, run against our APKs
instead of a recipe, so nothing is rebuilt and the bytes users download are the
published release assets. The same command reproduces the repository locally:

```sh
pip install fdroidserver==2.4.5           # plus a JDK and Android build-tools for apksigner
export ANDROID_HOME=~/android-sdk
export FDROID_REPO_KEYSTORE=~/nightfall-fdroid-repo.p12
export FDROID_REPO_KEYSTORE_PASSWORD=…    # never in a file that is committed
python3 tools/fdroid_repo.py build \
  --apk Nightfall-1.1.3-universal.apk --apk Nightfall-1.1.2-universal.apk \
  --out build/fdroid --keep 2
```

Secrets reach fdroidserver through `{env: …}` expansion in
`fdroid/repo/config.yml`, so no key path or password is ever written into the
tree. The run re-creates the output directory, keeps the newest `--keep`
versions, and verifies the result before publishing: the index must parse,
describe exactly this package with exactly those versionCodes, list no network
permission, carry matching APK hashes, and its `index-v1.jar` must be signed by
the configured repository key. A missing key, a missing `apksigner` or an
unsigned jar fails the run rather than publishing anything.

Re-running it is idempotent in content: the same APKs produce the same versions,
hashes, permissions and listing. Two things move on every run because they are
timestamps — `repo.timestamp` in the index and the `added`/`lastUpdated` fields
of every version — and the index signature changes with them. That is
fdroidserver's own behaviour, not a choice made here.

**Publishing.** The workflow assembles `site/fdroid/repo/` and deploys it with
GitHub Actions Pages, so the workflow owns the index and no branch carries it.
Only the newest two versions are published; at about 91 MB per APK the site
stays well inside the 1 GB Pages limit, and a device on an older version still
updates straight to the newest one. GitHub Pages is not an F-Droid mirror:
its 100 GB monthly bandwidth cap is roughly a thousand downloads per month.

The workflow can only index what a release actually publishes, and that is the
one thing still missing upstream: the release workflow builds
`app-fdroid-release-unsigned.apk` for its checks but signs and uploads only the
`github` flavor. Its asset picker prefers an asset whose name contains `fdroid`,
falls back to the `universal` one, and then the permission check refuses it. So
until a release uploads a signed `fdroid`-flavor APK, a publishing run fails
with a message naming the refusal instead of shipping the network-capable build
— fail-closed, but also no updates for F-Droid users. The change that closes it
belongs to the release workflow's owner; it is one extra
`tools/package_release.py` invocation for the `fdroid` flavor, emitting an asset
whose name contains `fdroid` (for example
`Nightfall-1.1.3-fdroid-universal.apk`).

**One-time setup only Camden can do.** Two repository secrets, and Pages
pointed at Actions:

1. Generate the repository key once and install the secrets, from a machine
   whose `gh` may write Actions secrets:

   ```sh
   python3 tools/configure_fdroid_repo_key.py --keystore ~/nightfall-fdroid-repo.p12 --upload
   ```

   The path must be outside the checkout: a key or password inside it is
   refused, so no git command can stage either. That creates a 4096-bit RSA
   PKCS12 key, writes its password to `~/nightfall-fdroid-repo.p12.password`, sets `FDROID_REPO_KEYSTORE_BASE64`
   (base64 of the `.p12`) and `FDROID_REPO_KEYSTORE_PASSWORD`, records the
   fingerprint in `fdroid/repo/fingerprint.txt`, and prints the add-URL. The
   password is generated locally and sent straight to GitHub; it is never
   printed. Back up the `.p12` and its `.password` file with the app signing key
   — they are the only copy of the repository identity, and the password is not
   recoverable from GitHub once written. Commit `fdroid/repo/fingerprint.txt`
   (it is the one source file this setup touches); the index build fails until
   it matches the key.
2. Settings → Pages → Build and deployment → Source: **GitHub Actions**.
3. Actions → **Publish F-Droid repository** → Run workflow.

**What has not been verified.** No Pages deployment has run from this
repository, so the URL above is the address the workflow and `config.yml`
target, not one that has served a request. The trigger chain has not been
exercised either: the repository has never seen `Publish Android release` finish
and wake this workflow. No F-Droid client on a phone has added the repository,
fetched the index, installed a version or auto-updated between two of them. The
index was generated and verified locally against the published 1.1.3 and 1.2
universal APKs with a throwaway repository key, which proves the procedure, the
fingerprint invariant and the refusal paths but not the deployed key.
`index-v1.jar` is SHA-1 JAR-signed because fdroidserver signs it that way for
client compatibility; `entry.jar` and the index-v2 files carry the modern
algorithm.

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
| Builds the variant F-Droid should ship | Rule in place | Pre-flavor blocks keep `gradle: [yes]`; a post-flavor block names `fdroid` — see [Variant selection](#variant-selection-is-settled-per-build-block) |
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

### Variant selection is settled, per build block

The recipe must build the offline app, and the rule that guarantees it is per
block rather than global. Every block pinned to a tag from before the
`github`/`fdroid` product flavors existed keeps `gradle: [yes]`: at those tags
`assembleRelease` *is* the single offline variant, and naming a flavor there
would ask fdroidserver for a task that tag does not define. A block whose tag
does contain the flavors names one instead:

```yaml
    gradle:
      - fdroid
```

fdroidserver turns that into `assembleFdroidRelease` and finds the result at
`app/build/outputs/apk/fdroid/release/` on its own (`build.py` appends
`build/outputs/apk/<flavor>/release` to its search), so `subdir: app` stays
correct and no `output:` override is needed. `tools/release_version.py` writes
new blocks from a template, and that template is what has to emit `- fdroid`
from the first release cut on a flavored tree; until it does, a new block would
build the network-capable variant.

This is a rule to follow rather than an open blocker, and it is not specific to
inclusion: the same boundary decides what the self-hosted repository will accept,
except that the repository enforces it by inspecting the APK's permissions
instead of trusting a recipe.

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

Steps, in order, once the development-history blocker is cleared:

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
