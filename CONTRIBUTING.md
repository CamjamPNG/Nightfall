# Contributing to Nightfall

Nightfall is a native Android sleep-sound mixer. A pure-Kotlin DSP core mixes any
number of independent sound layers — colored noise, synthesized weather, water,
wind, fire, indoor and travel textures, original generative music, and seven
bundled field recordings — into one 48 kHz stereo `AudioTrack`. A Jetpack Compose
app drives it with per-layer controls, presets, sleep timers and a foreground
media session.

It runs with no network, no account and no microphone access, and it keeps no log of how
you use it unless you switch the diagnostics journal on yourself — a bounded local file that
transmits nothing and is absent from the F-Droid build entirely. The tests that prove the
audio engine is deterministic run on any machine with a JVM. The [README](README.md) says what the app does; [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
says how; [docs/TESTING.md](docs/TESTING.md) says exactly what has and has not
been verified. Read that last one before you rely on anything.

Contributions of every size are welcome: a corrected comment, a new sound, a
filter that sounds better, a bug with a logcat excerpt, a test that catches
something the current 97 do not.

## Before you start

These are the product's non-negotiables. A change that breaks one of them is not
mergable no matter how good the code is:

- **Offline by default.** The app is fully usable with the radio off. No new
  permission, SDK, service or background work that reaches the network.
- **No accounts, analytics, ads, tracking, purchases, subscriptions or microphone
  access.**
- **No fixed layer limit.** Performance warnings tell the user their device is
  struggling; a hard cap is not the fix.
- **Release signing is the update path.** Never change the application id
  (`io.github.camjampng.nightfall`), never commit a key or keystore, never
  regenerate signing material. Android replaces an installed app only when the
  new APK carries the same certificate, so a new key strands every existing user.
- **Published releases are immutable.** Never move a tag, replace a published
  APK, or hand-bump a version. The workflow owns versions.
- **Honest claims.** Anything not measured stays described as not measured. See
  [docs/TESTING.md](docs/TESTING.md) for the current unverified list.

## Five minutes to a green build

Prerequisites, all of them free:

| Tool | Version | Where |
| --- | --- | --- |
| JDK | 17 or newer | Temurin, Corretto, OpenJDK. CI uses Temurin 17. |
| Android SDK Platform | 36 | `sdkmanager "platforms;android-36"` |
| Android Build Tools | 35.0.0 | `sdkmanager "build-tools;35.0.0"` |
| Gradle | do not install | the wrapper in the repo is 8.11.1 |

Android Studio installs the SDK for you; a bare SDK plus a terminal works too.
No API key, proprietary IDE or Google Play Services is involved.

```sh
git clone https://github.com/CamjamPNG/Nightfall.git
cd Nightfall

# Point Gradle at your SDK. local.properties is gitignored; do not commit it.
# Linux / Windows default: ~/Android/Sdk     macOS: ~/Library/Android/sdk
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# The DSP core alone, no emulator, no device.
./gradlew :core:test

# The same, minus the eight-hour night render: about four minutes instead of six.
./gradlew :core:test -PwithoutNight
```

`:core:test` includes `NightRunTest`, which renders a whole eight-hour night of audio in
accelerated blocks and takes around two minutes. It always runs in CI, because that is where
the build gate lives; `-PwithoutNight` skips it locally when you did not touch the render
path. Tagged tests live under `@Category(Night::class)` if you need to select them directly.

`:core:test` needs no Android SDK: the core module has no Android dependency, so with
`ANDROID_HOME` unset and no `local.properties` the task still configures and runs. Only the
`:app:*` tasks, the Robolectric playback scenarios included, need the SDK installed. The
first run downloads the Gradle distribution and the dependency set and takes a few
minutes; after that this command takes seconds.

If `sdk.dir` points at the wrong place you get a `SDK location not found` error
naming the path it tried. If `JAVA_HOME` is wrong you get an unsupported class
file version. Those two errors are almost every first-run failure.

Audio assets are committed as PCM. Nothing downloads sounds at build or run time.

## The build gate

This is the command a change has to survive, and the same one CI runs in
`.github/workflows/android.yml`:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

- `:core:test` — the 119 deterministic JVM tests (118 run; one calibration generator is
  opt-in): nine simultaneous sources,
  independent gain/mute/solo, phase continuity when layers are added or removed,
  limiting, colored-noise spectra, musical tuning, timers and fades, JSON
  round-trips, and 512 allocated layers.
- `:app:assembleDebug` — a real debug APK at
  `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:lintDebug` — Android Lint. `lint { abortOnError = true }` is set in
  `app/build.gradle.kts`, so a clean lint run is an actual gate, not advice. A
  clean run means 0 errors; the warnings that legitimately remain are
  dependency and Android Gradle Plugin version advisories, plus the intentional
  partial wake lock and exported media service (all 30 of them are accounted for
  in `app/build/reports/lint-results-debug.txt`). A new error fails the build, and
  a new warning on a line you touched is worth answering.

The release workflow additionally runs:

```sh
python3 -m unittest discover -s tools/tests -v
./gradlew :core:test :app:assembleRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

The `tools/tests` suite is ten stdlib-only Python tests over the release tooling
and needs neither the Android SDK nor an APK, so it is a useful sanity check when
you touch anything in `tools/`.

## Running one test

Iterating on one behavior? Do not run the whole suite:

```sh
# One class
./gradlew :core:test --tests "dev.nightfall.core.MixerTest"

# One method
./gradlew :core:test --tests "dev.nightfall.core.MixerTest.timerStartPauseResumeFadeExpiration"

# One release-tooling test
python3 -m unittest tools.tests.test_releasing.VersionPolicyTest.test_largest_change_wins -v
```

Test names are the source of truth for what is covered; they read as sentences
on purpose. HTML reports land in `core/build/reports/tests/test/index.html`, lint
in `app/build/reports/lint-results-debug.html`.

`AudioAcceptanceTest` additionally writes `artifacts/nine-layer-dream.wav` (a
24-second render of the real mixer) and `artifacts/dsp-metrics.txt` with host JVM
render timings. Both are gitignored. That file is useful for A/B-ing a DSP change
by ear, and the numbers in it are host-JVM numbers, **not** phone measurements —
do not quote them as device performance.

### Playback lifecycle is tested on the JVM

Playback surviving navigation and activity recreation, audio-focus pause and
resume, teardown, background playback, saving and restore are twelve Robolectric
scenarios in `app/src/test` — `AudioFocusTest`, `BackgroundPlaybackTest`,
`NineLayerPlaybackTest`, `SavingTest`, `StateRestorationTest`, `TeardownTest`,
`ThemedPresetPlayTest` and `BackGestureTest`. They run with no device attached and
need no `/dev/kvm`:

```sh
./gradlew :app:testGithubDebugUnitTest :app:testFdroidDebugUnitTest
```

CI runs both flavours that way, and the `api36` job runs the same suite again on
Temurin 21 against the Android 16 platform classes. There is no instrumented suite
left, so `:app:connectedDebugAndroidTest` has nothing to run. If your change
touches `AudioHost`, `PlaybackService`, focus handling or persistence, say plainly
in the PR what you ran on a device and what you did not.

## Repository map

| Path | What lives there |
| --- | --- |
| `core/src/main/kotlin/dev/nightfall/core/` | Android-free DSP: `Library.kt` catalogue, `Sources.kt` renderers, `MusicSource.kt`, `PcmMixer.kt`, `Models.kt` state and JSON |
| `core/src/test/kotlin/` | The JVM core suite, sized in [README](README.md)'s generated table; one calibration generator there is opt-in |
| `app/src/main/java/dev/nightfall/` | Compose UI, `AudioHost` (the single output stream), `PlaybackService` (Media3), DataStore persistence |
| `app/src/opus/assets/audio/` | Bundled 24 kHz mono Opus containers, one per field recording |
| `app/src/pcm/assets/audio/` | The raw PCM capture those containers were encoded from plus its `.pcm.license` sidecars: read by the host measurement tests, no longer an asset source, so no build packs it |
| `docs/` | Architecture, testing record, release process, verification JSON, pinned public certificate |
| `tools/` | Release and audio tooling (Python, stdlib only) |
| `fdroid/` | Prepared F-Droid recipe and submission notes |
| `fastlane/metadata/` | Store listing, icon and the six JVM-rendered screenshots (not device captures; see `docs/TESTING.md`) |

`core` must stay Android-free: no `android.*` imports, no `Context`, no logging
framework. That restriction is what lets the audio engine be tested anywhere and
is the reason the project has a real test suite at all.

## Adding a sound source, end to end

`PcmMixer.sync` chooses a renderer from `Sound.kind` in `Library.kt`:

| `kind` | Renderer | New sound needs |
| --- | --- | --- |
| `noise` | `NoiseSource` | nothing but a catalogue entry; `variant` selects the noise algorithm |
| `music` | `MusicSource` | catalogue entry; `variant` seeds a default `MusicConfig` in `Library.layer` |
| `binaural` | `BinauralSource` | catalogue entry |
| `recording` | `RecordingSource` | a bundled PCM asset and a licence |
| anything else | `EnvironmentSource` | catalogue entry; `kind` and `variant` pick a branch inside `EnvironmentSource.render` |

The `else` in that `when` is why a typo in `kind` is silent: an unrecognized kind
compiles, plays through the hum/fan default, and looks fine until you listen. Add
a family, add its branch.

### Adding a variant of an existing family (smallest useful change)

Most sound contributions are one line. In `core/src/main/kotlin/dev/nightfall/core/Library.kt`,
the `group(...)` calls take a category, a `kind`, and names; the index inside the
list becomes `variant`, which the renderer already reads for its character.

```kotlin
group(
    "Wind",
    "wind",
    listOf(
        "Gentle breeze",
        "Strong wind",
        // ...
        "Fog-bank stillness",   // <- new entry; variant becomes 8
    ),
)
```

Then:

1. Check that `EnvironmentSource.render`'s `when (kind)` handles `variant == 8`
   sensibly, or add a `variant == 8` detail to the `"wind"` branch. Variants that
   fall outside a branch's special cases still work — they just reuse the default
   character, which may not be what you meant.
2. Keep the name and description honest. If it is synthesis, the description must
   not imply a recording. The catalogue's field recordings say
   `Bundled field recording • see Audio credits`; everything else says
   `Locally synthesized`.
3. `id` comes from `Library.slug(name)` and is what presets, saved mixes and JSON
   exports store. Picking a name picks a permanent id. Renaming a sound later
   orphans every saved mix that used it — prefer adding a new entry.
4. Add it to a built-in preset in `Presets` only if it genuinely improves that
   preset; presets are a curated demo, not a complete index of the library.
5. `./gradlew :core:test`. The catalogue test in `AudioAcceptanceTest` renders
   every available library entry and asserts finite, nonzero output, so a source
   that silently produces silence fails the gate.
6. If the sound depends on a new DSP path — a new filter, a new event model — add
   a test to `MixerTest` that asserts the behavior you claim, with a threshold
   and a reason.

### Adding a bundled recording

This is the path that needs care, because it is the one that involves someone
else's licence.

1. **Find audio you may redistribute and modify.** Public domain, CC0, CC BY or
   CC BY-SA are all acceptable; record the exact licence and the source page. Do
   not add audio you cannot cite.
2. Add an entry to `docs/audio-sources.json`: an `id`, and one or more `sources`
   with `title`, `url`, `page`, `license`, `artist`, `seconds` and `cacheName`.
   Two sources are crossfaded into one layer, which is how the existing café and
   distant-storm entries are built.
3. Run the generator (needs `ffmpeg` and Python 3):

   ```sh
   python3 tools/prepare_audio.py
   ```

   It downloads sources into `.audio-cache/` (gitignored), writes
   `app/src/pcm/assets/audio/<id>.pcm` as mono 24 kHz signed 16-bit PCM,
   regenerates `AUDIO_LICENSES.md` and `docs/audio-credits.json` with the cycle
   length and SHA-256, and **fails if the loop is shorter than 180 seconds**.
   Commit the `.pcm`, the rewritten credit files, and the `.pcm.license` sidecar.
4. Register the sound in `Library.kt` with `kind = "recording"` and `asset = "<id>"`.
5. `python3 tools/check_release.py` verifies that every credited recording exists,
   matches its hash, cycles for at least three minutes and has a licence sidecar.
   It also asserts the release APK carries the `.nfop` container for every
   recording, that the container inside the APK is the one in the checkout, and
   that an APK never carries both kinds. `OpusAssetDecodeTest` then decodes each
   container with an independent Opus decoder in the gate.
6. Do not reload a full recording into a float array, and do not shorten the loop
   for download size. The three-minute minimum is a stated product property and
   is enforced in three places.

If a sound is wanted but no licensable recording exists, follow the existing
precedent: `distant-household-ambience` in `Library.kt` is a declared entry with
`kind = "unavailable"` that never plays substitute noise. That is a better answer
than a half-honest one.

### Adding a new synthesis family

Copy the shape of an existing branch in `EnvironmentSource.render`
(`core/src/main/kotlin/dev/nightfall/core/Sources.kt`): filtered noise beds, a
multi-timescale envelope, randomized events, and an `eventEnergy` report so
thunder-style ducking keeps working. Keep the sample loop allocation-free — every
buffer it needs is allocated in the constructor or by `PcmMixer`, never inside
`render`. Then add the family to the table above and to `Library`.

## Code style

There is no formatter in CI. Match the file you are editing; the surrounding code
is the specification.

- Kotlin official style (`kotlin.code.style=official`). Four-space indent.
- Every source file starts with `// SPDX-License-Identifier: MIT`. Python files
  start with the shebang, then the same line. Workflow files carry it too.
- `tools/*.py` is deliberately written dense: one line per statement, no typed
  annotations, small functions. Do not reformat it to be more verbose and do not
  spread that style into the Kotlin sources.
- **The render path is a deadline, not a style preference.** Inside
  `PcmMixer.render` and any `Source.render`, no allocation, no locking, no IO, no
  logging, no exception handling per frame. Sources get their state in
  constructors. A stray `listOf` or string interpolation in a sample loop is an
  audible click, and it is the single most common way to get a change sent back.
- `Layer`, `Mix`, `Settings` and `MusicConfig` are `@Serializable` and persisted
  in users' local DataStore. Add optional fields with defaults; never rename,
  renumber or repurpose one. `PresetJson.sanitize` is the validator that clamps
  ranges — extend it when you add a field.
- `core` stays Android-free.
- UI: 48 dp minimum touch targets, TalkBack semantics including move actions for
  reordering, reduced-motion honored, and the fixed midnight-navy/lavender palette.
- **Tests assert measured behavior with an explicit threshold and margin.** Write
  down why the threshold is what it is. A threshold tuned until it passes is a
  lie with a green checkmark, and it is worse than no test.

## Commit messages select the release version

Every merge to `main` runs `.github/workflows/release.yml`, which reads commit
subjects since the last tag and picks a version level. The biggest level in the
batch wins:

| Subject | Version | When |
| --- | --- | --- |
| `hotfix: ...` | 1.0.1.2 → 1.0.1.3 | urgent, narrow fix |
| `fix: ...`, `docs: ...`, `build: ...`, anything else | 1.0.1.2 → 1.0.2 | ordinary change, the default |
| `feat: ...`, `sounds: ...`, `ui: ...`, `fix(major): ...` | 1.0.1.2 → 1.1 | new sound, new UI, substantial fix |
| `type!: ...` or a `BREAKING CHANGE:` body | 1.0.1.2 → 2.0 | major overhaul |

So the subject line is not cosmetic. Write it as a real sentence in that
convention — `fix: stop layer fades on a stale monotonic deadline`, not
`updated stuff`. A `sound:`/`sounds:` prefix is how a new layer gets a minor bump.

`[skip release]` anywhere in the subject suppresses the version bump entirely.
That is for repository housekeeping, not for shipping work.

The workflow owns `versionName`, `versionCode`, `CHANGELOG.md`, the store
changelog under `fastlane/`, and the F-Droid recipe version block. **Do not edit
those by hand**; a hand edit collides with the automation, and after the tag is
pushed it is a published-release edit, which is not allowed.

## What a good pull request looks like

One unit of work per PR, titled in the convention above, with a body that answers
four questions:

1. **What ships.** Behavior, not a restatement of the diff.
2. **Why.** The problem, or the reason the old behavior was wrong.
3. **How you verified it.** The commands you ran and what you observed. Paste the
   relevant lines. "Build passed" is not evidence; `:core:test` showing only the opt-in generator skipped, and
   `:app:lintDebug` clean is.
4. **What you did not verify.** Listening quality, a Bluetooth mask, overnight
   battery — say so. That honesty is the project's differentiator, and reviewers
   will ask.

Also:

- Add or update a test when behavior changes. Do not weaken an existing test to
  get green; fix the code under test. If a test is genuinely wrong, say why in the
  PR and change it there.
- Keep generated files out: no APKs, `build/`, `artifacts/`, `.audio-cache/`,
  `local.properties`, `__pycache__/`.
- No secrets, keystores, `credentials.json`, or `.p12` files, ever.
- Do not touch `app/build.gradle.kts`'s `applicationId`, `minSdk`, or the signing
  configuration without opening an issue first; those are not casual edits.
- Reviewers check three things beyond correctness: does it keep the app offline,
  does the render path stay allocation-free, and is every new claim traceable to a
  measurement.

CI runs `:core:test`, `:app:assembleRelease`, `:app:lintRelease`, the release
tooling tests and the metadata/audio validator, read-only and with no signing key.

## Licensing of contributions

The code is MIT (`LICENSE`). By opening a pull request you agree your
contribution is licensed the same way; there is no CLA to sign and no copyright
assignment. Bundled recordings keep their own licences — a contributed recording
must have one that permits redistribution and modification, and its attribution
must land in `AUDIO_LICENSES.md` through `tools/prepare_audio.py`.
`COMMERCIAL_COURTESY.md` asks commercially successful users to get in touch. It is
a request, not a condition, and it puts no obligation on contributors.

## Reporting a problem

- **A bug** → the bug report issue template. Device, Android version, app
  version, layer count and a logcat excerpt are what make it fixable.
- **A feature idea** → the feature request template. Lead with the problem.
- **A security issue** → do not open a public issue. See [SECURITY.md](SECURITY.md).
