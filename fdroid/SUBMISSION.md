# Prepared F-Droid inclusion request

Status: prepared, not submitted. Check current inclusion policy before posting.
This project was first published on 12 November 2026 and does not yet establish
the normal three-month development history or a qualifying exception.
Do not submit as eligible without evidence or a maintainer-approved exception.

## Request text

**Nightfall — offline sleep sound mixer**

- Source: https://github.com/CamjamPNG/Nightfall
- Release: current version/code in `app/build.gradle.kts`, corresponding
  annotated `vVERSION` tag and matching recipe build block.
- Application ID: `io.github.camjampng.nightfall`
- Category: Ambient Sound
- Original code license: MIT
- Store metadata, real native screenshots and icon: `fastlane/metadata/android/en-US`
- Recipe: `fdroid/metadata/io.github.camjampng.nightfall.yml`
- Build: OpenJDK 17+, SDK 35, Build Tools 35.0.0, Gradle 8.11.1

Nightfall mixes independent procedural sources and locally bundled field
recordings through one PCM AudioTrack. It has no arbitrary channel-count cap,
accounts, advertising, tracking, microphone access, runtime internet permission
or downloaded audio. Mixes and settings stay local. MediaSessionService provides
foreground and lock-screen playback controls.

The MIT license permits commercial reuse. `COMMERCIAL_COURTESY.md` asks for
voluntary contact, sharing or charity support; it imposes no additional term.
Recordings retain their CC BY / CC BY-SA / CC0 licenses. See `AUDIO_LICENSES.md`,
`docs/audio-credits.json` and adjacent media license sidecars. No proprietary
SDK was found in the checked release dependency inventory.

Local verification: 18 deterministic core tests, release compilation, release
lint, metadata lint, source scan and APK scan pass. Two local unsigned release
builds are byte-identical. No independent F-Droid build has been performed.
Android instrumentation failed with startup ANR on the constrained software
emulator; physical Pixel 7, Bluetooth and overnight tests remain pending.
Details are in `docs/TESTING.md` and `docs/verification/`.

Standard F-Droid-managed APK signing is proposed as a separate channel from the
production-key-signed GitHub APKs. No Binaries field is set. The public GitHub
certificate is in `docs/`; independent upstream-signature reproduction has not
been established. The old development APK's debug key is not a release identity.

## Posting steps when eligible

1. Sign into GitLab and follow the current official quick-start guide:
   https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
2. Check for an existing Nightfall request. State the development history or
   provide evidence for a current policy exception; do not invent users or dates.
3. Fork https://gitlab.com/fdroid/fdroiddata and add the recipe as
   `metadata/io.github.camjampng.nightfall.yml`. Store assets remain upstream.
4. Run metadata checks and the buildserver build in that fork. Open a merge
   request with this text adapted to the actual results and eligibility evidence.
5. Address the independent CI and maintainer review. Submission, merging and
   availability in the public repository are separate steps.

No GitLab login is connected here. This file does not schedule an automatic
future submission or claim that F-Droid has accepted Nightfall.
