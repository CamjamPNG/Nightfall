# Verification record

## Executed checks

- 10 release-tool tests cover all four version levels, ordering and resets,
  classification of mixed changes, synchronized Android/store/F-Droid metadata,
  rejection of duplicate versions and preservation of code/audio in ABI options.
  Signed APK variants are additionally checked with Android's apksigner, aapt,
  zipalign and source-tag audio hashes before publication.
- 18 deterministic JVM/core tests pass, with no failures or skips. They cover
  nine simultaneous non-silent sources, 512 allocated layers, unrelated-source
  phase continuity, independent gain/master/mute/solo, limiting, colored-noise
  behavior, musical tuning/seeds, local-state serialization, timers and fades.
- Duplicate noise layers have independent random streams. Pausing in the final
  timer fade preserves the fade level. Expired layer schedules stop source
  reads/rendering and can resume with a new schedule.
- Every available library entry produces finite, nonzero output in the core
  catalogue test. This is a signal test, not a listening-quality certification.
- Every bundled recording cycle exceeds three minutes after overlap; asset
  hashes and the release APK's uncompressed PCM entries are validated.
- Debug and unsigned release APKs build. Release lint reports zero errors;
  remaining warnings include dependency-version advisories and intentional
  foreground-media/wake-lock patterns.
- F-Droid metadata parses, is formatted by fdroidserver, and passes metadata
  lint with the current upstream category definitions.
- F-Droid's source scanner reports no errors or warnings; it only removes
  standard Gradle wrapper files. Its APK scanner exits successfully on the
  unsigned release. These automated results do not replace maintainer review.
- Two local release builds, one from a separate source export without Git
  metadata, produce byte-identical unsigned APKs. Release VCS metadata is
  disabled. This is not an independent F-Droid build or signing verification.
- Gradle's official wrapper/distribution checksums are checked, and resolved
  dependencies have SHA-256 verification metadata. The release dependency
  inventory has no GMS, Firebase, billing, ad or tracking SDK from the checked
  denylist. F-Droid's independent review remains necessary.

- 9 JVM playback-lifecycle tests pass under Robolectric (see below).

Machine-readable results: `docs/verification/release-checks.json`,
`dependency-audit.json`, `reproducibility.json`, and `release-dependencies.txt`.

## Playback lifecycle tests (Robolectric, JVM)

`app/src/test/java/dev/nightfall` runs the real `NightfallApp`, `NightfallRuntime`,
the Compose screens, `PlaybackService`, `AudioHost`, its PCM worker thread and the
core `PcmMixer` on a JVM. Only the Android edges are shadowed: `AudioTrack` is a
float sink that records what the mixer wrote and plays nothing, `AudioManager`
records the focus request without arbitrating between apps, plus the power,
notification and activity managers. Nine scenarios, all asserting measured
behavior rather than that a method was called:

- `NineLayerPlaybackTest`: the transport control starts the nine-layer mix; the
  worker writes non-silent, finite PCM; the service reaches foreground with its
  notification attached and does not stop itself; the media session reports
  `STATE_READY`, `playWhenReady`, the mix title and nine layers; navigating to the
  Library and recreating the activity keep audio flowing and the mix intact.
- `LayerEditingTest`: adding a layer (9 → 10) and removing three (10 → 7) while
  playing keep samples flowing, keep the output finite, and update the layer count
  the media session reports.
- `BackgroundPlaybackTest`: with the activity moved to CREATED the worker keeps
  writing, the wake lock stays held and the foreground notification stays
  attached; the sleep timer fades, then stops playback and releases the wake lock
  without an error, and the audio thread stops writing.
- `AudioFocusTest`: the request is `AUDIOFOCUS_GAIN` for `USAGE_MEDIA`; a
  transient loss stops the samples and releases the wake lock without abandoning
  the request, and a gain resumes both; a permanent loss stops playback and a
  later gain does not restart it; a user pause abandons the request.
- `StateRestorationTest`: a gain and a setting reach the DataStore file on disk,
  and a fresh runtime restores them, including nine layers.
- `TeardownTest`: destroying the service ends the `Nightfall PCM` thread, drops
  playback state, releases the wake lock, and stops sample writes.

Limits of this harness, stated plainly:

- No audio hardware, driver or route is exercised. A float sink receiving samples
  is not a speaker producing sound, and underruns are shadowed counters.
- Robolectric does not arbitrate audio focus between apps, and `SystemClock` is
  virtual, so timer expiry is deterministic but does not show that a phone keeps
  the CPU awake for an hour with the screen off.
- "Background" here means the activity layer does not stop playback; process
  death, Doze and OEM task killers are not simulated.
- The transport control's label is asserted only where a click drives the change.
  A state flip arriving from the focus handler does not reliably recompose the
  Compose tree under Robolectric, so those scenarios assert sample flow and wake
  lock state instead.
- One JVM per test class (`forkEvery = 1`) is required: in a shared JVM a later
  class restores state left by an earlier one and two scenarios time out.

### Build wiring these tests need

`app/build.gradle.kts`:

```kotlin
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // DataStore is a per-process singleton: one JVM per test class keeps scenarios isolated.
        unitTests.all { it.forkEvery = 1 }
    }
}

dependencies {
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.androidx.test)          // already present for androidTest
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.test)
    testImplementation(libs.robolectric)
}
```

`gradle/libs.versions.toml`:

```toml
[versions]
robolectric = "4.18.1"
androidx-test-core = "1.7.0"

[libraries]
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

`gradle/verification-metadata.xml` already carries the SHA-256 entries for every
transitive artifact these dependencies resolve, so dependency verification stays
strict without a second pass.

`.github/workflows/android.yml`, in the existing Gradle step:

```yaml
          ./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
```

and in the artifact upload, add `app/build/reports/tests/testDebugUnitTest/`.
Robolectric downloads its Android 35 runtime jar on first use, so that step needs
network access the first time it runs on a runner.

## Android emulator observations

An API 35 software-emulated Pixel 7 profile rendered the native Home and Library
screens. The development build created one active 48 kHz stereo floating-point
AudioTrack; Android's media session reported PLAYING with the Nine-Layer Dream
name and nine layers while the screen was off. The actual captures are used for
the store screenshots; they are not UI mockups.

The automated Android instrumentation attempt did not pass. It terminated with
`Process crashed`; Android process-exit diagnostics identified a startup ANR.
System UI also showed ANRs and the emulator Bluetooth stack repeatedly aborted.
The heavily constrained software emulator produced audio underruns and is not a
valid phone-performance or audio-quality benchmark. No Bluetooth hardware or
instrumentation passes are claimed from this run. Startup restoration was then
moved off the main thread; this does not establish that every device issue has
been resolved. That never-green `PlaybackIntegrationTest` has since been replaced
by the Robolectric scenarios above, which run on a JVM without an emulator.

## Pending hardware checks

Verify the public package on a physical Pixel 7 with the intended Bluetooth
sleep mask/headphones: media buttons, disconnect/reconnect, focus interruption,
app navigation, configuration changes, lock-screen controls, independent layer
adjustments, timer expiration, acoustic quality and an overnight battery/
underrun run. Manufacturer-independent all-night playback is not guaranteed.

## Reproduce

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

The core acceptance test creates `artifacts/nine-layer-dream.wav` and host JVM
render measurements. The playback lifecycle tests report under
`app/build/reports/tests/testDebugUnitTest/`. Neither is a Pixel 7 CPU, battery or
listening result. `:app:connectedDebugAndroidTest` is no longer part of the
record: there is no instrumented test left, and no `/dev/kvm` on the machines
that run these checks.
