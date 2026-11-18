# Prepared F-Droid inclusion request

Status: prepared, not submitted, and not yet eligible. Two things must happen
first: every recipe build block has to select the `fdroid` product flavor once
the update channel introduces flavors (otherwise the same blocks would build the
network-capable variant), and the project needs the development history F-Droid
asks for. This project was first published on 12 November 2026 and does not yet
establish the normal three-month history or a qualifying exception, which on the
current policy puts the earliest ordinary submission around 12 February 2027.
The full checklist is in [`docs/FDROID.md`](../docs/FDROID.md). Do not submit as
eligible without evidence or a maintainer-approved exception.

## Request text

**Nightfall — offline sleep sound mixer**

- Source: https://github.com/CamjamPNG/Nightfall
- Release: 1.0.1.2, versionCode 4, tag `v1.0.1.2`
- Application ID: `io.github.camjampng.nightfall`
- Category: Ambient Sound
- Original code license: MIT
- Store metadata, real native screenshots and icon: `fastlane/metadata/android/en-US`
- Recipe: `fdroid/metadata/io.github.camjampng.nightfall.yml`
- Build: OpenJDK 17+, SDK 35, Build Tools 35.0.0, Gradle 8.11.1; `subdir: app`.
  Blocks pinned to tags from before the flavor split build the single
  `assembleRelease` variant, which has no network permission and no updater;
  blocks for later tags build the `fdroid` flavor with `assembleFdroidRelease`.

Nightfall mixes independent procedural sources and locally bundled field
recordings through one PCM AudioTrack. It has no arbitrary channel-count cap,
accounts, advertising, tracking, microphone access, runtime internet permission
or downloaded audio. Mixes and settings stay local. MediaSessionService provides
foreground and lock-screen playback controls.

The F-Droid build has no network code and no updater, so it declares no
anti-features: there is no self-update path to consent to and no network service
to depend on. The GitHub build's update channel is a separate artifact, is not
built by this recipe, and is not part of this request.

The MIT license permits commercial reuse. `COMMERCIAL_COURTESY.md` asks for
voluntary contact, sharing or charity support; it imposes no additional term.
Recordings retain their CC BY / CC BY-SA / CC0 licenses. See `AUDIO_LICENSES.md`,
`docs/audio-credits.json` and adjacent media license sidecars. No proprietary
SDK was found in the checked release dependency inventory.

Local verification on 18 November 2026: `fdroid readmeta`, `fdroid lint` and
`fdroid rewritemeta` pass with fdroidserver 2.4.5 and leave the recipe unchanged;
`fdroid scanner` reports wrapper removals only across all four release tags;
`fdroid checkupdates --auto` detects 1.0.1.2 (code 4) from the tags. Five clean
release builds at tag `v1.0.1.2` across two independent machines produced
byte-identical APKs, and their payload matches the published universal APK entry
for entry. No F-Droid build has been performed and no F-Droid-built bytes have
been compared to ours. Android instrumentation failed with a startup ANR on the
constrained software emulator; physical Pixel 7, Bluetooth and overnight tests
remain pending. Details are in `docs/TESTING.md`, `docs/FDROID.md` and
`docs/verification/`.

Standard F-Droid-managed APK signing is proposed as a separate channel from the
production-key-signed GitHub APKs. No Binaries field is set: rebuilding the
published APK bytes is not achievable while `tools/package_release.py`
recompresses entries, so upstream-signature reproduction is not claimed. The
public GitHub certificate is in `docs/`. The old development APK's debug key is
not a release identity.

## Posting steps when eligible

1. Land the `fdroid` flavor, then give every block whose tag contains it
   `gradle: [fdroid]` and teach the block template in `tools/release_version.py`
   to emit it. Leave the pre-flavor blocks on `gradle: [yes]`: their tags have no
   flavor and `assembleFdroidRelease` does not exist there. Then rebuild and
   re-run `fdroid lint` and `fdroid build --server` against the resulting recipe.
2. Sign into GitLab and follow the current official quick-start guide:
   https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
3. Check for an existing Nightfall request. State the development history or
   provide evidence for a current policy exception; do not invent users or dates.
4. Fork https://gitlab.com/fdroid/fdroiddata and add the recipe as
   `metadata/io.github.camjampng.nightfall.yml`. Store assets remain upstream.
5. Run metadata checks and the buildserver build in that fork. Open a merge
   request with this text adapted to the actual results and eligibility evidence.
6. Address the independent CI and maintainer review. Submission, merging and
   availability in the public repository are separate steps.

No GitLab login is connected here. This file does not schedule an automatic
future submission or claim that F-Droid has accepted Nightfall.
