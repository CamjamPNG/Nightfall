# Verification record

## Executed checks

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

Machine-readable results: `docs/verification/release-checks.json`,
`dependency-audit.json`, `reproducibility.json`, and `release-dependencies.txt`.

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
been resolved.

## Pending hardware checks

Verify the public package on a physical Pixel 7 with the intended Bluetooth
sleep mask/headphones: media buttons, disconnect/reconnect, focus interruption,
app navigation, configuration changes, lock-screen controls, independent layer
adjustments, timer expiration, acoustic quality and an overnight battery/
underrun run. Manufacturer-independent all-night playback is not guaranteed.

## Reproduce

```sh
./gradlew :core:test :app:assembleRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew :app:connectedDebugAndroidTest
```

The core acceptance test creates `artifacts/nine-layer-dream.wav` and host JVM
render measurements. These are not Pixel 7 CPU, battery or listening results.
