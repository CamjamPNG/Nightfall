# Verification record

## Executed checks

- 45 JVM tests in `app/src/testGithub` cover the update channel: release-manifest
  parsing (untrusted documents, wrong types, unknown and duplicate architectures,
  addresses that are not this repository's release assets, oversized input),
  every trust refusal (`UpdatePolicy`), version-name ordering, and the downloader
  against a local socket server (redirect following, progress ordering, a
  response larger than the manifest allows, and a connection that dies mid-file).
  The release-tool suite covers the manifest generator's schema, URLs and
  rejection cases, and asserts that each F-Droid recipe block selects the flavour
  its own tag defines.
- The manifest's release notes are exercised in three shapes: present (version,
  date and the notes themselves, as the generator writes them), absent (a document
  without `log` or `schemaVersion`, which parses as schema 1 with an empty log and
  an unchanged version code, certificate and APK list), and hostile (`log` that is
  not a list, entries that are not objects, versions that are not versions, notes
  that are not strings, a note with a newline in its date, repeated versions, two
  hundred releases and a two-thousand-character line of control characters). Every
  hostile case is asserted to leave the version, code, certificate and APK list
  byte-identical to the clean parse and to bound what the view can draw: at most
  eight entries, four notes each, 160 characters a note, no control characters. A
  declared schema newer than this build is read, not refused.
- `UpdateAutomationTest` pins the playback guard as a truth table and as the
  sequence it exists for: with the setting on, an install started by the app is
  refused for `PLAYING` and for `UNKNOWN` (a foreground entry before the app has
  reported playback), and allowed only for `STOPPED`; a night that begins in
  playback and ends when audio stops produces exactly one install, at the stop. It
  also pins that the setting gates the app's own request, that the fifteen-minute
  floor and the six-hour interval both apply, that a failed attempt never spends the
  six hours, that a corrected clock is expired rather than a lock, and that a
  download from an earlier session resumes only while it is still newer and its file
  is still on disk.
- `UpdateChannelStateTest` pins the channel's two pacing rules (a fifteen-minute
  floor for every request, a six-hour interval only for the app's own check, a
  failed attempt never starting that interval, a corrected clock treated as
  expired), the minutes reported to the user, and the rule that hiding a banner
  suppresses only that release's notice, including the notice that an update is
  waiting for playback. The installer-outcome plumbing, the foreground
  reconciliation, the "What's new" once-only record and the Compose surfaces are
  Android-bound and are not unit-tested.
- Both channels build and pass lint: `:app:assembleGithubDebug`,
  `:app:assembleFdroidDebug`, `:app:assembleGithubRelease`,
  `:app:assembleFdroidRelease`, `:app:lintGithubDebug`, `:app:lintFdroidDebug`
  (`abortOnError = true`). `tools/check_release.py --fdroid-apk` asserts the
  F-Droid APK declares none of the network or install permissions and that none of
  the fourteen updater classes it names appears in any dex; `--apk` asserts the
  GitHub APK declares all of them and does carry the updater. The recipe check
  reads `app/build.gradle.kts` at each pinned tag, and the CI job fetches tags so
  that comparison runs instead of being skipped.

- The release-tool suite covers all four version levels, ordering and resets,
  classification of mixed changes, synchronized Android/store/F-Droid metadata,
  rejection of duplicate versions and preservation of code/audio in ABI options.
  Signed APK variants are additionally checked with Android's apksigner, aapt,
  zipalign and source-tag audio hashes before publication.
- 42 deterministic JVM/core tests pass (41 executed, 1 skipped by design: the
  opt-in `NIGHTFALL_CALIBRATE=1` level-calibration generator). They cover nine
  simultaneous non-silent sources, 512 allocated layers, unrelated-source phase
  continuity, independent gain/master/mute/solo, limiting, colored-noise
  behavior, musical tuning/seeds, local-state serialization, timers and fades,
  plus the loudness ledger and per-source level calibration, true-peak ceiling,
  zero per-block allocation, click size at layer add/remove, loop seams on the
  bundled recordings, spectral slope, DC offset and tuning in cents.
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

Machine-readable results: `docs/verification/update-channel.json`,
`release-checks.json`, `dependency-audit.json`, `reproducibility.json`, and
`release-dependencies.txt`.

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

## Update channel that was not exercised

The install path was not run on hardware. This workspace has no `/dev/kvm`, so no
emulator and no device was available: `PackageInstaller` sessions, the
`STATUS_PENDING_USER_ACTION` fallback, the "install unknown apps" screen and the
post-install restart are described from Android's documented behaviour, not from a
measured run. Nothing here claims a silent install was observed.

The same applies to everything this release adds around that path: no install, no
unattended restart and no post-install "What's new" has been observed on a device.
The automatic download of a published release, the waiting state that follows it
while audio plays, the install that a play-to-pause transition triggers, the
resumption of a verified download after the process was killed, and the once-only
changelog on the next launch are all covered as rules and state transitions in JVM
tests (45 in `app/src/testGithub`) and not as observed device behaviour. The Compose
surfaces — the Settings toggle, the waiting row and the changelog dialog — were
compiled and linted, never rendered on a screen.

Also unverified: a real download of a published release from GitHub (the tests use
a local server with the same protocol), behaviour behind a captive portal or a
proxy, and how each OEM image prompts. Releasing a version with this channel and
installing it on a physical device is the check that closes this gap.

## Pending hardware checks

Verify the public package on a physical Pixel 7 with the intended Bluetooth
sleep mask/headphones: media buttons, disconnect/reconnect, focus interruption,
app navigation, configuration changes, lock-screen controls, independent layer
adjustments, timer expiration, acoustic quality and an overnight battery/
underrun run. Manufacturer-independent all-night playback is not guaranteed.

## Reproduce

```sh
./gradlew :core:test :app:testGithubDebugUnitTest :app:assembleGithubRelease :app:assembleFdroidRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew :app:connectedGithubDebugAndroidTest
```

The core acceptance test creates `artifacts/nine-layer-dream.wav` and host JVM
render measurements. These are not Pixel 7 CPU, battery or listening results.
