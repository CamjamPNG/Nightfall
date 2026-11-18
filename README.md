# Nightfall

A free, native Android sleep sound mixer. Kotlin, Jetpack Compose / Material 3, Android 8.0+ (API 26), target API 35. Midnight navy and lavender, with a local-first mixer at the center. No advertising, accounts, subscriptions, analytics or microphone access. Two builds ship: the GitHub build can update itself from this repository's releases, which is its only network access; the F-Droid build has no updater and no network permission at all.

## Install and use

Download signed APK options from [GitHub Releases](https://github.com/CamjamPNG/Nightfall/releases).
Choose **arm64-v8a for Pixel 7**, or Universal if unsure. All options include the
same complete offline sound library. See [release and version rules](docs/RELEASING.md)
for automatic publishing, signing setup and update-channel details.

An install from GitHub keeps working offline and does not need to be reinstalled
by hand: Nightfall updates itself, and Settings → Updates lists what changed in
each recent release. See [Updates](#updates).

The project builds two channels from the same source: `github` (the updater and
its network permission) and `fdroid` (no updater, no network permission, no
network code). Build the installable GitHub debug APK with
`./gradlew :app:assembleGithubDebug`, or the F-Droid one with
`:app:assembleFdroidDebug`. `:app:assembleDebug` builds both. Generated APKs are
not committed to source. The public release application ID is
`io.github.camjampng.nightfall`. Install on a Pixel 7 or another Android 8+ device:

```sh
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
```

Alternatively, copy the APK to the phone and open it, allowing installation from your file manager when Android asks. This is a debug-signed development build, not a store release.

On first launch the active mix is **Nine-Layer Dream**. Press Play. Its independent gains are lo-fi 55%, brown noise 35%, rain 50%, blizzard 20%, hail 15%, thunder 20%, wind through pine trees 30%, fireplace 45%, and A4=440 Hz ambient music 20%. Master app gain starts at 50%. Android's device volume remains independent.

There is **no fixed layer limit**. Nine is only the named integration test. Add repeated or different sources from Library; each has its own synthesis/read head and effects. Long lists are virtualized. Hardware has finite processing capacity: Home warns when rendering approaches its buffer budget or underruns accumulate. It does not enforce a nine-layer or other arbitrary channel cap.

Connect a Bluetooth sleep mask or headphones using Android, then select Play. Nightfall uses the system-selected output, supplies media controls, and runs a foreground media service while listening. It pauses on disconnect by default; Settings can allow Android to reroute. Manufacturer battery restrictions, hardware and Android interruptions can affect long sessions; all-night behavior is not guaranteed for every phone.

## Updates

The GitHub build replaces itself in place. **Update automatically** is on by
default: Nightfall checks for a new release at most once every six hours while it
is open, downloads it, verifies it, and installs it. The one thing it will not do is
interrupt playback — an install restarts the app, so an update that arrives while
sound is playing waits, says so in a row over the app, and installs when you stop
the sound or the next time you open Nightfall. A row also appears whenever a
release is newer and waiting for something. Turn the setting off and nothing is
downloaded or installed until you press **Update**, and **Check for updates** stays
a check you started.

What happens:

1. Nightfall reads `release-manifest.json` from the latest GitHub release and
   picks the one APK for this device's architecture (an exact match, otherwise
   Universal). The manifest carries the version code and name, the per-variant
   download address, the byte size, the SHA-256, the signing certificate and the
   notes for the recent releases.
2. It downloads that one APK and checks four things before Android is asked to
   install anything: the SHA-256 and size match the manifest, the package is
   `io.github.camjampng.nightfall`, the version code is the released one, and the
   signing certificate is **the same certificate as the installed app** and
   contains only that device's native libraries. Any mismatch is discarded with a
   visible reason. A verified download is kept, so trying again does not fetch
   ninety megabytes a second time.
3. It refuses downgrades: a version code that is not strictly greater than the
   installed one, or a version name that is not newer, is never installed.
4. Android's `PackageInstaller` replaces the app when playback is stopped. On
   Android 12 and newer with this app's target SDK the install completes without
   further prompts; devices whose image still requires one show Android's own
   confirmation dialog, which Nightfall opens for you. Nightfall restarts
   afterwards, and the new version shows **What's new** once with the notes of the
   release it installed.

It asks GitHub at most **once every six hours** on its own, and a check you start
is rate-limited to **one every fifteen minutes**; a failed attempt never spends the
six-hour window, so a dropped connection costs you fifteen minutes, not an
afternoon. Settings says when the next check is allowed. There is no background
worker, alarm or periodic wake-up, so an idle Nightfall never touches the radio.
Nothing is sent anywhere: the check is a plain download of a public file, and the
APKs are downloaded from this repository's releases only.

**What's new** in Settings → Updates lists the recent releases — version, date and a
few lines each — from the copy Nightfall already holds, so reading it asks the
network for nothing. The notes come from the release manifest, which is generated
from this repository's changelog. With no notes stored yet, it says just that.

Hiding the row stops it nagging; it never hides the update, which stays available
in Settings until it is installed. If Android refuses or you cancel its
confirmation, the reason appears at once, and a failure that outlived the app is
reported the next time it opens. The F-Droid build has none of this: it ships no
updater, no network permission and no network code, and Settings says updates
arrive through F-Droid or your store.

Not verified on hardware: see [docs/TESTING.md](docs/TESTING.md). No install, no
unattended restart and no post-install "What's new" has been observed on a physical
device in this workspace, and the playback guard is verified as a rule and a state
sequence, not by watching a phone mid-night.

## Implemented

- One coherent 48 kHz stereo PCM mixer feeding one AudioTrack, running off the UI thread.
- Independent gain, enable, mute, solo, pan, width, high-/low-pass filtering, three-band EQ, brightness, tilt, intensity, event rate and fades. Source state survives unrelated changes. Hold a drag handle to reorder; TalkBack also offers move actions.
- Smooth gain changes, layer/preset crossfades, headroom, and a final soft limiter.
- Eleven noise colors. White, pink, brown/red, blue and violet/purple use spectral algorithms; gray/green/black are clearly identified shaping presets; velvet uses sparse signed impulses.
- Individually mixable synthesized rain, storms, winter, wind, water, fire, fans, travel and mechanical textures. Random thunder, weather evolution, music ducking, slow variation, storm-to-rain transitions and a low-distraction setting.
- Seven offline field-recording layers: forest birds, crickets, frogs, nighttime owl/forest, quiet study room, distant urban rain/storm montage and café ambience. **Every bundled recording has at least 180 seconds before repeating**, including loop overlap. Most cycles are around four minutes. Files are memory mapped as shared 16-bit PCM, with per-layer playheads and smooth overlap. Sources are honestly attributed; environmental synthesis is labeled as synthesis.
- Eighteen original procedural music profiles: independently running lo-fi/ambient/piano/pads/bells/strings/bowls/lullabies, multiple progressions, scale/key, tuning, generative melody, seeded variation, tempo, density, complexity, instrument texture, reverb, stereo delay and optional quiet lo-fi percussion.
- A4=432/440 Hz means musical tuning, using correct note-frequency calculations. Optional binaural synthesis labels its actual frequency difference and stereo requirement. No sleep-stage, healing or clinical claims.
- Studio controls for composition, tone and effects; save to a layer or local preset.
- All 15 requested built-in presets, plus unrestricted local preset storage; save, rename, duplicate, favorite, delete, JSON import/export via Android's file picker.
- Local DataStore persistence of current mix, gains, effects, compositions, presets, favorites and settings. JSON streaming import validates IDs and numeric ranges without imposing a layer-count limit.
- Sleep timer presets, custom durations up to 24 hours, all-night mode, pause/resume, configurable final fade, independent layer stop schedules and per-layer fades. Time uses monotonic elapsed time / rendered frames, not wall-clock deadlines.
- Media3 MediaSessionService, foreground playback notification, lock-screen and Bluetooth media-button controls, audio focus, becoming-noisy handling, partial wake lock only while playing, and resource cleanup. Navigation/configuration changes do not restart the engine.
- Five functioning screens, optional light theme, reduced motion, accessible semantics, 48 dp interaction targets, saved screen state, and back navigation to Home. Visual guidance from the supplied [Android design skill](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/SKILL.md) is applied; wallpaper color overrides are omitted to preserve the specified palette.

## Limitations and unverified behavior

- **Distant household ambience** is explicitly unavailable because a suitable licensed recording was not obtained. It is disabled in the library and never plays substitute noise.
- Weather, water, fire, wind, transport and fan entries are original synthesized interpretations. Different material profiles are not claimed to be high-fidelity field recordings. Instrument textures are additive synthesis rather than sampled acoustic instruments.
- A physical Pixel 7, Bluetooth sleep mask, actual acoustic loudness, perceptual mix quality and overnight battery life must be checked on hardware. See `docs/TESTING.md` for the precise executed and pending checks.
- Playback/timers do not automatically resume after force-stop, process death or reboot. The saved mix remains; a stale session deadline is never restored. User pause also pauses the timer. A stopped layer's schedule is measured from its source's playback lifetime, not the moment a later schedule edit is made.
- Preset/settings writes debounce for 250 ms. A force-kill inside that window can lose the last edit. Normal screen changes preserve state.
- No Android Auto browser, widgets, calendar schedules, cloud sync or external audio import is included.

## Build

Prerequisites: a free OpenJDK 17 or later, Android SDK Platform 35, Build Tools 35.0.0, and network access for the first dependency download. Android Studio can install these. All versions are pinned in `gradle/libs.versions.toml`; Gradle Wrapper 8.11.1 is included.

```sh
export JAVA_HOME=/path/to/openjdk-17
export ANDROID_HOME=/path/to/android-sdk
# Or put sdk.dir=/path/to/android-sdk in local.properties.
./gradlew :core:test :app:testGithubDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleGithubDebugAndroidTest
# Both channels, including the release builds the update channel publishes:
./gradlew :app:assembleGithubRelease :app:assembleFdroidRelease
# With an unlocked device/emulator attached:
./gradlew :app:connectedGithubDebugAndroidTest
```

Build output: `app/build/outputs/apk/github/debug/app-github-debug.apk` (and the
`fdroid` equivalent). Unit-test reports: `core/build/reports/tests/test/index.html`. Lint: `app/build/reports/lint-results-debug.html`. Instrumentation reports: `app/build/reports/androidTests/connected/debug/index.html`.

The running application does not need a network connection, in either channel. Audio assets are committed with the source. To reproduce them, install ffmpeg and Python 3 and run `python3 tools/prepare_audio.py`; downloads are build-time only. The generator verifies the three-minute minimum cycle for every recording.

`core` is Android-independent Kotlin: models, validated JSON, catalogue, presets, deterministic sources, timer and PCM mixer. `app` provides Compose/MVVM, persistence, the audio host and media service. See `docs/ARCHITECTURE.md` for ownership, DSP, lifecycle and performance policy.

## Audio and licensing

Application code: MIT. Original procedural music and DSP are part of the application source. Bundled recordings retain their individual public-domain / Creative Commons licenses. Attribution, source links, edits, license URLs and output hashes are in `AUDIO_LICENSES.md` and `docs/audio-credits.json`, and are viewable inside Settings. Dependency notices are in `THIRD_PARTY_NOTICES.md`.

Running the core acceptance test generates `artifacts/nine-layer-dream.wav`, a 24-second output of the actual mixer, and `artifacts/dsp-metrics.txt` with desktop JVM render measurements. Generated artifacts are excluded from Git. These values are not Pixel 7 battery or CPU measurements.

## License and commercial courtesy

Original code is MIT-licensed, including commercial use. Camden would appreciate
a conversation about a small share or an agreed charity contribution if you
profit from the code. This is entirely optional and adds no license condition.
See [COMMERCIAL_COURTESY.md](COMMERCIAL_COURTESY.md). Third-party recordings retain
their own licenses and attribution obligations.

## F-Droid preparation

Source repository: https://github.com/CamjamPNG/Nightfall

See [fdroid/README.md](fdroid/README.md) for the proposed recipe, store metadata,
release checks, signing considerations and remaining eligibility gates. No
F-Droid inclusion or production signing approval is claimed. Nightfall is new;
F-Droid's development-age/user-adoption policy still needs review.

The earlier development APK used `dev.nightfall`. It can remain installed beside
the public release. Export mixes as JSON before removing it, then import them
into the new package. Uninstalling a package removes its private local data.
