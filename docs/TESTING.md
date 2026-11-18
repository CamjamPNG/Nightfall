# Verification record

## Executed checks

- 51 JVM tests in `app/src/testGithub` cover the update channel: release-manifest
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
- `InstallWindowTest` pins the second, commit-time half of the guard. Writing a
  ninety-megabyte APK into an installer session takes seconds while Play and the
  media-session controls stay live, so the window is read again immediately before
  the commit: playback that starts during the copy abandons the session and never
  reaches the commit, playback that starts and stops again during the copy still
  commits, and a tap the owner made is not vetoed by the copy-time fact. The test
  drives the real `InstallWindow.copyThenCommit` with a copy that moves the
  playback fact, so it asserts the ordering — copy, then window, then commit or
  abandon — rather than a copy of it.
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
  F-Droid APK declares none of the network or install permissions and that no
  class from the updater package appears in any dex. That list is read from
  `app/src/github/java/dev/nightfall/update` at check time — the file's own name
  plus every type it declares — rather than kept by hand, so a class added to the
  package is covered the day it is written; `--apk` asserts the GitHub APK
  declares all the permissions and carries code from every one of those files,
  which is what stops the negative half from passing vacuously if a name is
  dropped from the minified build. The recipe check reads `app/build.gradle.kts`
  at each pinned tag, and the CI job fetches tags so that comparison runs instead
  of being skipped.

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

- 10 JVM playback-lifecycle tests pass under Robolectric in both channels (see
  below). They replace the instrumented attempt that never went green.

Machine-readable results: `docs/verification/update-channel.json`,
`release-checks.json`, `dependency-audit.json`, `reproducibility.json`, and
`release-dependencies.txt`.

## Playback lifecycle tests (Robolectric, JVM)

`app/src/test/java/dev/nightfall` runs the real `NightfallApp`, `NightfallRuntime`,
the Compose screens, `PlaybackService`, `AudioHost`, its PCM worker thread and the
core `PcmMixer` on a JVM. Only the Android edges are shadowed: `AudioTrack` is a
float sink that records what the mixer wrote and plays nothing, `AudioManager`
records the focus request without arbitrating between apps, plus the power,
notification and activity managers. Ten scenarios, all asserting measured
behavior rather than that a method was called:

- `NineLayerPlaybackTest`: the transport control starts the nine-layer mix; the
  worker writes non-silent, finite PCM; the service reaches foreground with its
  notification attached and does not stop itself; the media session reports
  `STATE_READY`, `playWhenReady`, the mix title and nine layers; navigating to the
  Library and recreating the activity keep audio flowing and the mix intact.
- `LayerEditingTest`: adding a layer (9 → 10) and removing three (10 → 7) while
  playing keep samples flowing, keep the output finite, and update the layer count
  the media session reports. Its closing assertion on the app's save error is read
  after the edited mix is on disk (`PlaybackHarness.awaitSaved`), not at whatever
  moment the test reaches the line: the earlier version added and removed layers
  faster than the 250 ms debounce could write them, so under CPU load it asserted
  on a save that was still in flight and failed at random.
- `SavingTest`: an edit that arrives after the debounce has expired — a second
  drag while the first write is still inside DataStore — is not reported as a
  failed save, and the newest state is the one on disk. It is the scenario the
  flaky teardown turned out to be, and it reproduces on the unfixed code without
  needing a slow machine: the first write is put in flight by advancing the main
  clock past the debounce, then the next edit supersedes it.
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

### Build wiring

The suite is configured in the repository: `app/build.gradle.kts` sets
`testOptions { unitTests.isIncludeAndroidResources = true }` and
`unitTests.all { it.forkEvery = 1 }`, and `testImplementation` carries the Compose
BOM, `androidx.test:core`, `androidx.test.ext:junit`, `androidx.compose.ui:ui-test-junit4`
and Robolectric 4.18.1 alongside the JUnit that was already there. Because the two
channels are flavours rather than one variant, the scenarios run twice — under
`:app:testGithubDebugUnitTest` and `:app:testFdroidDebugUnitTest` — and the CI job
runs both, so the F-Droid build's playback lifecycle is gated as well as the
GitHub build's.

`gradle/verification-metadata.xml` carries the SHA-256 entries for every
transitive artifact these dependencies resolve, so dependency verification stays
strict without a second pass. Robolectric downloads its Android 35 runtime jar on
first use, so the CI step needs network access the first time it runs on a runner.

## Android emulator observations

An API 35 software-emulated Pixel 7 profile rendered the native Home and Library
screens. The development build created one active 48 kHz stereo floating-point
AudioTrack; Android's media session reported PLAYING with the Nine-Layer Dream
name and nine layers while the screen was off. The two PNGs from that run were
the store screenshots until 18 November 2026; they are no longer, and the emulator
itself is not reproducible here (see below).

## Store screenshots (Robolectric, JVM)

`fastlane/metadata/android/en-US/images/phoneScreenshots/` now holds six frames
generated from the shipped UI by `app/src/testGithub/java/dev/nightfall/render/`,
not by a screen-capture of a running phone. The reasons are the ones in this file:
these machines have no `/dev/kvm`, so an emulator does not run, and the two
emulator captures had not been recaptured since 12 November 2026, so they showed
an app without the update channel, the automatic install and the changelog —
screens and copy the app no longer has.

Each frame is 1078×2399 (411×914 dp at 420 dpi, a Pixel 7's logical size) and is
the app's own window, drawn by a real Skia canvas under Robolectric's native
graphics: `MainActivity`'s decor view is drawn into an off-screen bitmap. The
screens are reached through the app's own controls — the navigation bar for tabs,
a layer's own options button for the panel — and the mix is `Presets.all.last()`,
so the title, the layer names, the gains and the layer count on screen are read
out of the model the audio thread mixes. Regenerate with:

```sh
./gradlew :app:testGithubDebugUnitTest --tests 'dev.nightfall.render.*'
```

which writes `app/build/screenshots/github/`, uploads that directory with the CI
run, and fails on the two ways a render goes quiet instead of wrong: a frame with
too few distinct colours (it did not draw) and a frame identical to an earlier
one (a screen stopped rendering).

What these frames are not: a device. They have no status bar, no gesture bar and
zero window insets, so their content sits 24–32 dp higher than on a phone that
shows its status bar; the waveform is a single phase of an animation that is
switched off for the render; `02-home-layers.png` is a scrolled frame, not the
whole mixer; and the render-load and underrun numbers Settings prints are
Robolectric's, not a phone's. No emulator or hardware render of the current UI
exists.

The automated Android instrumentation attempt did not pass. It terminated with
`Process crashed`; Android process-exit diagnostics identified a startup ANR.
System UI also showed ANRs and the emulator Bluetooth stack repeatedly aborted.
The heavily constrained software emulator produced audio underruns and is not a
valid phone-performance or audio-quality benchmark. No Bluetooth hardware or
instrumentation passes are claimed from this run. Startup restoration was then
moved off the main thread; this does not establish that every device issue has
been resolved. That never-green `PlaybackIntegrationTest` has since been replaced
by the Robolectric scenarios above, which run on a JVM without an emulator.

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
tests (51 in `app/src/testGithub`) and not as observed device behaviour. The Compose
surfaces — the Settings toggle, the waiting row and the changelog dialog — were
compiled and linted, never rendered on a screen. The commit-time re-check is
tested as an ordering over a fake session, not against Android's installer.

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
./gradlew :core:test :app:testGithubDebugUnitTest :app:testFdroidDebugUnitTest \
  :app:assembleGithubRelease :app:assembleFdroidRelease :app:lintRelease
python3 tools/check_release.py --apk app/build/outputs/apk/github/release/app-github-release-unsigned.apk --fdroid-apk app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
```

The core acceptance test creates `artifacts/nine-layer-dream.wav` and host JVM
render measurements. The playback lifecycle tests report under
`app/build/reports/tests/testGithubDebugUnitTest/` and
`app/build/reports/tests/testFdroidDebugUnitTest/`. Neither is a Pixel 7 CPU,
battery or listening result. `:app:connectedGithubDebugAndroidTest` is no longer
part of the record: there is no instrumented test left, and no `/dev/kvm` on the
machines that run these checks.
