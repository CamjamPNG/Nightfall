# Nightfall

**A free Android sleep sound mixer that never needs the internet.** No account,
no analytics, no advertising, no microphone, no subscription — and nothing to sign
into before it will play a fan noise. Sounds are layered and mixed on the device
through one 48 kHz stereo path, so ten layers of rain, wind, fire and music arrive
as a single night rather than ten loops fighting each other. Everything you hear is
generated or played locally; the one build that can reach the network uses it for
its own updates and nothing else.

Android 8.0+ (API 26) · Kotlin and Compose · MIT

## What it does

**Build a night out of 98 sounds.** Eleven noise colours, rain, storms, winter,
wind, water, fire, indoor hum, travel, seven bundled field recordings and eighteen
generated music profiles. One row — *Distant household ambience* — is declared and
**disabled** rather than filled with substitute audio, because no recording we may
redistribute exists for it. That is a deliberate absence, not a bug. Fifteen built-in
mixes start you off, and every one of them is an ordinary mix you can edit, rename or
duplicate.

**Every layer is its own instrument.** Volume, on/mute/solo, stereo position and
width, high- and low-pass, three-band EQ, brightness, its own stop time, and — for a
storm — a chosen number of minutes after which it becomes rain while the fireplace
keeps going. Duplicate layers keep independent random streams, so two brown-noise
layers are not one layer twice as loud.

**No layer cap.** Nine is the name of a preset, not a limit — the app opens on
*Nine-Layer Dream*, nine independent layers already loaded. Add layers until the
phone runs out of headroom; Home warns at 65% of the render budget or above three
underruns instead of quietly glitching or enforcing an arbitrary count.

**Recordings that do not announce themselves.** Every bundled loop plays for at
least three minutes before it repeats — the cycles are 180.5 to 240 seconds, with a
one-second overlap so the seam is not a click. Thirty-second loops are what the brain
latches onto at 2 a.m.

**A timer that ends the night, not the track.** 5, 10, 15, 30, 45, 60, 90 or 120
minutes, *All night*, or any custom 1–1440. It fades out gently instead of cutting,
pauses when you pause and resumes without drifting, and each layer can hold its own
stop schedule.

**Screen off, phone on the nightstand.** A Media3 foreground media session gives you
lock-screen and Bluetooth controls, pauses when the headphones disconnect, and holds
a wake lock only while it is playing. Playback is not tied to the screen or the
activity, and nothing wakes the radio.

**Your mixes stay yours.** Saved locally in DataStore, with presets, favourites,
duplication, rename, delete, and JSON export and import through Android's own file
picker. No sync, no server, no export you did not ask for.

**Labels that mean something.** Synthesis says "Locally synthesized"; recordings say
"Bundled field recording" with a credit link in Settings. Gray, green and black noise
are called shaping presets rather than pretend standard noise colours. Binaural tones
state their actual frequency difference, the stereo requirement, and that sleep-stage
or healing benefits are not established. A4=432 Hz is labelled as musical tuning, not
therapy.

## Screenshots

> **Placeholder.** Real, current screenshots are coming from an in-repo render job —
> the Compose screens captured by a JVM test (Robolectric with native graphics), which
> is the only route available here because these build machines have no `/dev/kvm` for
> an emulator. The two PNGs already in
> `fastlane/metadata/android/en-US/images/phoneScreenshots/` are genuine captures of
> the running app from a software-emulated Pixel 7, taken in the first release commit
> and not recaptured since, so they predate the update-channel work. Nothing on this
> page is a mockup, and this section stays empty rather than showing a design the app
> no longer has.

## Install

### GitHub Releases — the channel that works today

1. Open **[Releases](https://github.com/CamjamPNG/Nightfall/releases)** and take the
   newest version. The current one is **1.3.1** (versionCode 12).
2. Pick **`arm64-v8a`** for a modern phone (a Pixel 7, most Android phones from the
   last five years), or **`universal`** if you are unsure. `armeabi-v7a`, `x86` and
   `x86_64` cover older 32-bit devices and Intel emulators. All of them contain the
   complete offline library; they differ only in which native libraries are inside.
3. Open the APK on the phone and allow installs from your file manager when Android
   asks.

The `arm64-v8a` APK is **91,162,628 bytes (86.9 MiB)** and the universal one
**91,261,452 bytes**. That is a large download, and it is not at its floor: 77,449,472
bytes of it are the recordings shipped as uncompressed PCM (so the mixer can memory-map
them instead of copying them into RAM) and the dex is unshrunk because minification is
not configured yet. The route to a much smaller APK — an Opus re-encode of the
recordings plus R8 — is measured in `docs/launch/izzyondroid-request.md`; none of it has
shipped, so the size above is real and the reduction is not.

This channel can update itself afterwards: Settings shows the installed version, and
when a release is newer an update row appears with one **Update** button. On its own it
asks GitHub never sooner than fifteen minutes after any previous attempt, and never
more often than once every six hours counted from the last successful answer — with no
successful answer yet, the fifteen-minute floor is the only limit. A check you start
obeys the same fifteen-minute floor. **Nothing downloads or installs without your tap.**
The APK it accepts must match the manifest's size, SHA-256, package id, version code and
the certificate of the app already installed. No background worker, alarm or periodic
wake-up exists, so an app that is not in the foreground never checks anything. Both
pacing rules are in `CheckThrottle.kt`, which is the place to read before paraphrasing
them. Details in [`docs/RELEASING.md`](docs/RELEASING.md) and [`PRIVACY.md`](PRIVACY.md).

### The F-Droid build — buildable from source today

The second channel is the same app with the updater removed, not the same app
configured not to use it. It has no manifest parser, no fetcher, no installer and no
network code, and it declares none of the four network or install permissions. To get
it today you build it:

```sh
./gradlew :app:assembleFdroidDebug
# app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

CI builds this channel, lints it, and runs the same nine playback-lifecycle scenarios
against it on every push; `tools/check_release.py --fdroid-apk` asserts from the built
dex and manifest that no updater class and no network permission survived. The Releases
page does not publish an F-Droid-flavoured APK yet.

### The self-hosted F-Droid repository — address reserved, not serving yet

Nightfall intends to be installable through the official F-Droid client from an index
it builds and signs itself:

```
https://camjampng.github.io/Nightfall/fdroid/repo
```

**That address returns 404 today, measured on 18 November 2026**, so please use the
Releases page instead. The index builder, its refusal paths and its fingerprint
invariant exist and were exercised locally against published APKs, but the one-time
setup has not been run: `fdroid/repo/fingerprint.txt` is empty, the repository key is
not installed, and GitHub Pages is not enabled for this repository. When it does go
live it will serve only the offline build — the builder refuses any APK that declares
`INTERNET` or `ACCESS_NETWORK_STATE`, and refuses anything not signed with the pinned
release certificate. It will *not* be F-Droid-verified: adding it means trusting the
maintainer's repository key, and F-Droid has checked nothing about it. The full state,
failure modes and key handling are in [`docs/FDROID.md`](docs/FDROID.md).

### F-Droid's main repository — not submitted, and not before February 2027

The build recipe, store metadata, changelogs and category are complete in `fdroid/`,
and they validate: `fdroid readmeta`, `rewritemeta`, `lint` and `scanner` are clean, and
the recipe selects the offline flavour at every tag. The blocker is not metadata, it is
eligibility. F-Droid's Inclusion Policy asks that an application "should have been
actively developed for at least 3 months", and Nightfall's first public release was
12 November 2026, so the earliest ordinary submission is **around 12 February 2027**. No
merge request exists. Nothing here claims F-Droid has accepted, reviewed or blessed
anything.

### An older development build

The first development APK used the package `dev.nightfall` and can stay installed
beside the release. Export your mixes as JSON, install the release, import them, then
remove the old package — uninstalling deletes its private data.

### Which build is which

The two channels differ in exactly one thing — the update check — and it is the only
difference the copy anywhere in this project is allowed to blur.

| | `github` (every APK on Releases) | `fdroid` (built from source, not published yet) |
| --- | --- | --- |
| Playback, generation, presets, timers, storage | identical | identical |
| Works fully offline, radio off | yes | yes |
| Declares `INTERNET`, `ACCESS_NETWORK_STATE` | yes | **no** |
| Declares `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | yes | **no** |
| Updater code in the APK | yes | **none** — no parser, fetcher or installer |
| Talks to the network | only to read one public release manifest and, if you tap Update, download one APK | never |
| Settings → Updates | installed version, **Check for updates**, next check time | installed version and where updates come from |

So "Nightfall has no `INTERNET` permission" is true of the F-Droid channel and false of
every file on the Releases page. There is also no switch that turns the check off in
the current release: the `github` build runs one when it comes to the foreground, never
sooner than fifteen minutes after any previous attempt and never more often than once
every six hours counted from the last successful answer; while a failed-check row is on
screen it makes no request until you dismiss that row or check by hand. Dismissing an
update row hides that notice, and does not stop the next check. If a build that cannot
open a socket at all is the point for you, that build is the `fdroid` one — which today
means building it yourself.

Both channels declare what a background music player needs and nothing more:
foreground service, the media-playback foreground type, notifications, and a wake
lock. Neither declares microphone, storage, location, contacts or account permissions,
and the permission set of each built APK is asserted in CI rather than described here.

## What is proven, and what nobody has checked

This project's differentiator is not a feature list; plenty of apps have rain. It is
that the claims are separated into what a test asserts, what a script measures, and
what no one has ever confirmed. All three columns are kept honest in
[`docs/TESTING.md`](docs/TESTING.md).

### Asserted by a test in this repository

| Claim | Test |
| --- | --- |
| Nine simultaneous sources stay non-silent and independently audible, and 512 layers allocate without falling over | `MixerTest.nineSourcesAreSimultaneousAndIndependentlyAudible`, `…stopResetsSessionAndThereIsNoNineLayerCap` |
| Output stays under the soft limiter's ceiling — no sample above 0.53, 4× oversampled true peaks below −1.0 dBTP | `RenderPathTest.outputNeverLeavesTheLimiterCeiling`, `LoudnessTargetTest.maximumSettingsRespectTheTruePeakCeiling` |
| The render path allocates nothing after warm-up | `RenderPathTest.renderAllocatesNothingAfterWarmup` |
| Adding or removing a layer is no louder a step than the material itself, and does not disturb another layer's phase or random stream | `RenderPathTest.addingAndRemovingALayerIsAsSmoothAsTheMaterial`, `MixerTest.dynamicAdditionRemovalPreservesOtherSourcePhase`, `…duplicateNoiseLayersHaveIndependentRandomStreams` |
| Noise colours have their stated spectral slope, output has no DC offset, and music voices are in tune to the cent | `RenderPathTest.noiseColoursHaveTheirStatedSpectralSlope`, `…outputHasNoDcOffset`, `…musicVoicesAreInTuneToTheCent` |
| No bundled recording loops before three minutes, and every one loops without a seam | `AudioAcceptanceTest.noBundledRecordingLoopsBeforeThreeMinutes`, `RenderPathTest.everyFieldRecordingLoopsWithoutASeam` |
| Every playable catalogue row renders finite, nonzero output | `AudioAcceptanceTest.entireAvailableCatalogHasRealAudibleOutput` |
| Every catalogue source is trimmed to −18 LUFS at full layer gain, so a percentage means the same loudness for every sound | `Levels.REFERENCE_LUFS`, `LoudnessTargetTest.everyContinuousSourceReachesTheReferenceLevel` |
| The updater refuses a foreign package id, a foreign signing certificate, a downgrade, a mismatched checksum, an address outside this repository's assets, and a truncated or oversized response | `UpdatePolicyTest`, `ReleaseManifestParserTest`, `ReleaseFetchTest` (34 tests) |
| Playback survives navigation, activity recreation, backgrounding, audio-focus loss and service teardown | nine Robolectric scenarios in `app/src/test`, run under both channels in CI |
| The F-Droid APK carries no network permission and none of the fourteen updater classes | `tools/check_release.py --fdroid-apk`, on every push |

How many tests: **42 in `core`** (41 run, plus the opt-in `NIGHTFALL_CALIBRATE=1`
calibration generator, which is skipped by design), **9 playback-lifecycle scenarios**
that run twice, once per channel, **34 updater tests**, and **33 release-tooling
tests**. They run on a JVM with no device, no emulator and no network.

### Measured by a script or recorded in the repository

| Claim | Evidence |
| --- | --- |
| The GitHub APK declares all four network and install permissions; the F-Droid one declares none | `tools/check_release.py` on the built APKs, wired into CI |
| Release APKs are aligned and signed by the pinned certificate, and the manifest's download addresses point only at this repository's release assets | `tools/package_release.py`, `tools/verify_release.py` |
| Two machines building the same tag produce byte-identical APKs | `docs/verification/reproducibility.json` and `docs/FDROID.md` |
| The F-Droid recipe parses, rewrites to canonical form, lints clean, and its source scanner reports no errors | `docs/verification/fdroid-source-scan.json`, `fdroid-apk-scan.log` |
| No GMS, Firebase, billing, advertising or tracking SDK is in the resolved dependency graph | `docs/verification/dependency-audit.json` |
| The APK sizes above, and the 77,449,472 bytes of PCM | release asset records for v1.3.1; `app/src/main/assets/audio` |

### Never checked on hardware

| Gap | What is missing |
| --- | --- |
| That it sounds good, or that nine layers sound right | No listening test and no acoustic measurement exists; loudness is digital gain, not a sound-pressure claim |
| Bluetooth sleep masks and headphones, disconnect and reconnect, media buttons | Never exercised on a real device |
| An all-night run with the screen off — underruns, battery, OEM task killers | No overnight result exists for any phone |
| The in-app update actually installing from a published release | `PackageInstaller` sessions and the Android 12+ prompt-free path are described from Android's documentation; the tests drive a local server speaking the same protocol. Also unverified: captive portals, proxies, and how each OEM prompts |
| An F-Droid client adding the self-hosted repository and auto-updating | No deployment has served a request |
| Reproducible builds on F-Droid's own buildserver | Two of our machines agree; theirs has never built it |

If one of those gaps matters to you, an issue with a device name and a logcat excerpt
is the most useful thing anyone can send right now — it is roadmap item 1, and it is
first because it is what turns every sentence above into something measured.

## Build and check it

Needs JDK 17 and Android SDK 35 with build-tools 35.0.0. These three commands are the
documented gate, and CI runs these three plus the rest of the list below.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug   # 42 core tests, both channels, lint aborts on error
python3 -m unittest discover -s tools/tests              # 33 release-tooling tests
python3 tools/check_release.py                           # store metadata, bundled audio, both channels
```

The full CI job also runs the Robolectric suites per channel
(`:app:testGithubDebugUnitTest :app:testFdroidDebugUnitTest`) and both release builds
— see [`.github/workflows/android.yml`](.github/workflows/android.yml). Build outputs
land in `app/build/outputs/apk/`, test reports in `core/build/reports/tests/test/` and
`app/build/reports/tests/`, lint in `app/build/reports/lint-results-debug.html`.

`core` is Android-free Kotlin — models, validated JSON, catalogue, presets,
deterministic sources, timer and the PCM mixer — and `app` is Compose/MVVM,
persistence, the audio host and the media service. Ownership, DSP and the render-path
rules are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

Adding a sound, a preset, an instrument or a fix: read
[`CONTRIBUTING.md`](CONTRIBUTING.md). It has the commit convention that decides the
next version number, the rule that keeps the render path allocation-free, and the
three-minute loop minimum that is enforced in three places. Real open work is queued
in [`ROADMAP.md`](ROADMAP.md), with the evidence for each item beside it.

## Licence and commercial courtesy

Original code is MIT-licensed, including commercial use. Camden would appreciate a
conversation about a small share or an agreed charity contribution if you profit from
the code. This is entirely optional and adds no license condition — see
[`COMMERCIAL_COURTESY.md`](COMMERCIAL_COURTESY.md). Nothing in it obliges a
contributor to anything.

Bundled field recordings keep their own licences (CC BY, CC BY-SA and CC0) and their
attribution obligations; `AUDIO_LICENSES.md` and `docs/audio-credits.json` record every
source, its licence, its edits and the hash of the audio actually shipped, and the same
credits are readable in Settings under *Audio credits & open-source licenses*.
Third-party libraries are listed in `THIRD_PARTY_NOTICES.md`.

## Reporting a problem

- **A bug** → the bug report template. Device, Android version, app version, layer count
  and a logcat excerpt are what make it fixable.
- **A feature idea** → the feature request template. Lead with the problem.
- **A security issue** → do not open a public issue. See [`SECURITY.md`](SECURITY.md).
