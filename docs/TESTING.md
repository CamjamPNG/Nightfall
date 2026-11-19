# Verification record

## Executed checks

- 51 JVM tests in `app/src/testGithub/java/dev/nightfall/update` cover the update channel: release-manifest
  parsing (untrusted documents, wrong types, unknown and duplicate architectures,
  addresses that are not this repository's release assets, oversized input),
  every trust refusal (`UpdatePolicy`), version-name ordering, and the downloader
  against a local socket server (redirect following, progress ordering, a
  response larger than the manifest allows, and a connection that dies mid-file).
  The release-tool suite covers the manifest generator's schema, URLs and
  rejection cases, and asserts that each F-Droid recipe block selects the flavour
  its own tag defines.
- 26 JVM tests in `app/src/testGithub/java/dev/nightfall/diagnostics` cover the hidden diagnostics
  surface, and they measure the file and the screen rather than a mock. `JournalTest` runs against a
  temporary directory: an entry is neither written nor held while the switch is off; five entries
  under a three-entry cap leave the three newest, in order; sixty entries under a 400-byte cap keep
  the newest ones and encode to no more than the cap; an entry older than thirty days is dropped
  whole; entries written by one journal are read by a second one over the same file, which is what
  proves they reached storage; erasing leaves no file, no entries and a report with zero of both, and
  it also removes the `.writing` file an interrupted write leaves behind — that assertion was run
  against the previous `erase()` and failed there, so it bites — and an erase that cannot delete the
  file reports a failure instead of returning success.
- The consent boundary is pinned in two places. `ConsentTest` (5) drives the real preferences: a
  fresh install starts with no cutoff, the first consent is it, it only ever moves forward so a later
  consent cannot re-open an interval collection was off for, an already-imported exit is never
  re-opened, turning the switch on sets the cutoff to that moment, and the switch is off until it is
  turned on. `SessionClockTest` (4) pins the other half: a session is measured from start to stop, a
  night that ran while collection was off records nothing, turning collection off abandons the run
  rather than pausing it so re-enabling mid-night measures only the part that was watched, and one
  session is recorded once.
  `TapGateTest` pins the gesture as arithmetic: four taps never open, five inside three seconds do,
  five spread over ten seconds do not, a tap past the window restarts the run, and the tap after a
  completed run does not continue it. `DiagnosticsGestureTest` drives the app's own version row in
  Settings' own `LazyColumn` through the real activity: four taps leave Settings as it was, five
  inside three seconds reveal the section with its copy, and five spread over ten seconds reveal
  nothing. Every timed scenario asserts that the shadow clock actually advanced, so a clock that
  refused to move would fail there instead of passing as if the gesture were merely inert.
- The diagnostics half of `tools/check_release.py` is pinned from both sides, because a negative
  assertion is only worth something when the build that must contain the names provably does: the
  GitHub APK has to carry code from every file in `app/src/github/java/dev/nightfall/diagnostics`
  (refused otherwise), `fdroid_violation` reports an F-Droid APK containing any of those class
  descriptors, and an APK with neither package is still clean. Nine tool tests cover that, including
  a class nested inside the one stub the F-Droid channel must carry and the surface's own copy found
  in a resource table, and the names are compared as whole dex descriptors so `Diagnostics` cannot be
  matched by `DiagnosticsSurface`. The entry-point groups are read from the directory — a file's own
  name, the facade class Kotlin gives a file of top-level functions, and every type it declares — so
  a class added to the package is covered the day it is written. Measured on the built APKs: the
  GitHub dex holds 54 diagnostics descriptors, the F-Droid dex holds one (`DiagnosticsSurface`, the
  stub), and the switch's copy appears in the GitHub resource table and nowhere in the F-Droid one.

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
- 69 deterministic JVM/core tests pass (68 executed, 1 skipped by design: the
  opt-in `NIGHTFALL_CALIBRATE=1` level-calibration generator). One of them renders
  a whole eight-hour night through the real render path. They cover nine
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

- 33 Robolectric scenarios pass in both channels (see below), plus the three GitHub-only
  diagnostics gesture scenarios described above: eleven
  playback-lifecycle ones, which replace the instrumented attempt that never went
  green, two Android 16 assertions that need the API 36 sandbox CI now runs
  (`Android16RuntimeTest`), and twenty for the home-screen widget.

Machine-readable results: `docs/verification/update-channel.json`,
`release-checks.json`, `dependency-audit.json`, `reproducibility.json`, and
`release-dependencies.txt`.

## Playback lifecycle tests (Robolectric, JVM)

`app/src/test/java/dev/nightfall` runs the real `NightfallApp`, `NightfallRuntime`,
the Compose screens, `PlaybackService`, `AudioHost`, its PCM worker thread and the
core `PcmMixer` on a JVM. Only the Android edges are shadowed: `AudioTrack` is a
float sink that records what the mixer wrote and plays nothing, `AudioManager`
records the focus request without arbitrating between apps, plus the power,
notification and activity managers. Eleven scenarios, all asserting measured
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
- `BackGestureTest`: back on a tab returns to Home and back on Home closes the
  screen, and after the screen is finished the audio thread is still writing
  samples and the foreground service has not stopped itself.
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
strict without a second pass. Robolectric downloads the platform jar for the
sandbox it runs — Android 35 for the `release` job, Android 16 for the `api36`
job — on first use, so each CI job needs network access the first time it runs on
a runner. A cold Gradle home on Temurin 21 resolves the same descriptors the
Temurin 17 job does, so the 21 job needed no new verification entries.

The sandbox level is a build input, not a test constant: Robolectric reads `sdk`
from `robolectric.properties` on the test classpath and has no system-property
override, so `app/build.gradle.kts` puts one of two checked-in files on that
classpath — `src/robolectricApi35/resources/` by default and
`src/robolectricApi36/resources/` for `-PnightfallRobolectricSdk=36`. The tests
are identical in both; only the platform runtime beneath them changes.

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

## Home-screen widget tests (Robolectric, JVM)

`app/src/test/java/dev/nightfall`, run under both channels in CI and in the API 36 job:

- `WidgetStoppedToPlayTest`: from a fresh install, the widget reads `Stopped` while the runtime
  reports a stopped mix; a tap on the widget — through `WidgetSession`, a real `MediaController`
  and the real `PlaybackService` — starts playback, the float sink receives non-silent samples,
  and the widget then reads `Playing` with the pause glyph and the description "Pause soundscape.
  Playing.".
- `WidgetPlayingToPauseTest`: a tap while playing pauses the session, the widget reads `Paused`,
  and a 250 ms window after the block already in flight writes zero samples. The label alone
  could lie about running audio; the sample count cannot.
- `WidgetPausedToPlayTest`: a tap on a paused widget resumes the loaded mix rather than starting
  another one — the layer count is compared across the resume.
- `WidgetFollowsSessionTest`: no tap happens anywhere. Home's pause control, then the media
  session resumed behind the notification, then "Stop all" (which must read `Stopped`, not
  `Paused`), then the service being destroyed while playing — the widget follows each one.
- `WidgetLayoutTest`: reads the provider declaration (no polling, one target cell, reconfigurable,
  both resize axes) and measures both layouts at the Pixel 7's cell sizes, 68×68 dp and 150×70 dp.
  Every corner of the button is required to lie inside the surface's 20 dp rounded rect, the label
  must not be ellipsised, and the label must sit beside the button in the wide layout.
- `WidgetTapModeTest`: the two tap behaviours are two intents — a broadcast to the provider with
  no window, or an activity intent with `NEW_TASK` and no action — the announced action follows the
  tap mode ("Open Nightfall. Playing." rather than a pause that will not happen), and the choice
  survives an edit and dies with the widget.
- `WidgetRebootStateTest`: a state written an hour into a previous boot reads as stopped, stays
  stopped once this boot's uptime passes that mark, and is replaced on disk rather than skipped
  past — the window in which a skip-over guard would resurrect "playing".
- `WidgetCompactSelectionTest` (API 30 sandbox): before Android 12 there is no size-keyed map, so
  the widget reads the launcher's reported width and picks the layout — one cell gets the compact
  one, two cells the wide one, and a resize swaps them.
- `WidgetConfigureTest`: the configure screen is driven as a person drives it; picking "Open
  Nightfall" and pressing Done records the choice, returns `RESULT_OK` with the widget id, and a
  second visit keeps the behaviour the widget already had. A screen opened with no widget id
  answers cancelled and adds nothing.

`app/src/testGithub/java/dev/nightfall/render/WidgetRenderTest` draws the widget itself — real
`RemoteViews`, real Skia canvas, the launcher's `xxhdpi` density — at 1x1 and 2x1 in both theme
variants, writes them to `build/screenshots/github`, and fails if a frame has too few colours or
if light and dark come out identical.

What none of this can show: a launcher's own corner radius and cell padding, the wallpaper behind
the widget, or the bind that creates the service when Nightfall is not running. Robolectric never
starts a service by binding, so the tests create the service and hand its binder to the bind.

## Android 16 (API 36) compatibility

`compileSdk` and `targetSdk` are 36 from this change; `minSdk` stays 26. The build
moves with them: **Android Gradle plugin 8.10.1**, which is the first line whose
release notes state that the maximum API level it supports is 36, paired with the
**Gradle 8.11.1** the wrapper already pinned (8.10's minimum is exactly 8.11.1, so
the wrapper — and its distribution checksum — did not move). Kotlin, the Compose
BOM and every dependency stay where they were.

Targeting 16 turns on the behaviours below. Each one was checked against the
platform note that introduces it, and each is either already satisfied by the code
that was there or covered by a test that measures it.

- **Edge to edge, with no opt-out.** "For apps targeting Android 16 (API level 36),
  `R.attr#windowOptOutEdgeToEdgeEnforcement` is deprecated and disabled, and your
  app can't opt-out of going edge-to-edge." The app never set that attribute and
  has always called `enableEdgeToEdge` in `MainActivity.kt`; what the change puts on
  it is the insets. All five tabs and the layer options inside them are drawn by one
  `Scaffold`, whose content padding is the system-bar inset, and the navigation bar
  carries its own bottom inset; `UpdateOverlay` is the only thing drawn outside
  that `Scaffold` and it calls `statusBarsPadding`. `SystemBarInsetsRenderTest`
  hands the window a 100 px status bar and a 100 px gesture bar and measures both
  moves: the header lands exactly 100 px lower, the navigation bar stays above the
  gesture bar, and the frame with bars is written beside the store set as
  `app/build/screenshots/github/07-home-with-system-bars.png`.
- **Predictive back by default.** "For apps targeting Android 16 (API level 36) or
  higher ... the predictive back system animations ... are enabled by default.
  Additionally, `onBackPressed` is not called and `KeyEvent.KEYCODE_BACK` is not
  dispatched anymore." The app's only back handling is `BackHandler`
  (`NightfallScreen.kt:88`), which registers on `OnBackPressedDispatcher` — the API
  predictive back drives — so nothing was reading the key event that stops
  arriving. `BackGestureTest` drives the dispatcher the way a committed gesture
  does: back on a tab returns to Home, back on Home closes the screen, and after
  the screen is finished the audio thread is still writing samples and the
  foreground service has not stopped itself.
- **Fixed-rate work scheduling.** "When targeting Android 16, at most one missed
  execution of `scheduleAtFixedRate` is immediately executed when the app returns to
  a valid lifecycle." Nothing in `app/` or `core/` schedules fixed-rate work: no
  `WorkManager`, `JobScheduler`, `AlarmManager`, `ScheduledExecutor` or
  `scheduleAtFixedRate` call exists, and the sleep timer is a pure function of
  `elapsedRealtime` in `core`.
- **Adaptive layouts on large screens.** "For apps targeting Android 16 (API level
  36), orientation, resizability, and aspect ratio restrictions no longer apply on
  displays with smallest width >= 600dp." The manifest declares no
  `screenOrientation`, `resizableActivity` or aspect-ratio attribute, so there is
  nothing to stop applying; what the change can expose is a wide, short window this
  app has never drawn. `WideScreenRenderTest` draws the mixer and the catalogue at
  914×411 dp, writes them beside the store set as `08-home-wide.png` and
  `09-library-wide.png`, and asserts the catalogue is still reachable.
- **Elegant font APIs.** "Android 16 deprecates the `elegantTextHeight` attribute,
  and the attribute will be ignored once your app targets Android 16." The app sets
  no `TextView` font attributes at all: every string is Compose text, and the
  attribute has no Compose equivalent.
- **Unchanged, and required either way:** the notification runtime permission is
  already requested on API 33+ (`MainActivity.kt`), and playback declares the
  `mediaPlayback` foreground-service type with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

Not applicable, with the reason: the health and fitness permissions (no
`BODY_SENSORS`), the Bluetooth bond-loss intents (no Bluetooth code), MediaStore
version lockdown (no `MediaStore` use), and GPU syscall filtering (the app issues no
Mali ioctls). Local Network Permission and Safer Intents are both opt-in in Android
16 and the app opts into neither; the updater's only socket is an HTTPS request to
GitHub on the public internet, not a local network address.

The 16 KB page-size change is the one that needed measuring rather than reading,
because the app does carry native code: `libandroidx.graphics.path.so` and
`libdatastore_shared_counter.so`, four ABIs each, from AndroidX. Both are aligned in
the release APK — `zipalign -c -P 16` reports every `.so` `(OK)` and
`readelf -l` shows a `p_align` of `0x4000` — so Android 16 runs the app directly
instead of offering it page-size compatibility mode.

Two runtimes, two jobs. Robolectric's SDK 36 sandbox refuses to start on the
Temurin 17 that builds the release APK and the F-Droid recipe ("Android SDK 36
requires Java 21"), so the `release` job keeps running the whole suite on the
**API 35 runtime** with the app's `targetSdk` at 36, and it is what ships. The
`api36` job runs the same tests on Temurin 21 with the **API 36 runtime**: the
platform classes are Android 16's own rather than 15's.

What the 36 sandbox adds is measured, not asserted in prose: on API 36 the
window's back dispatcher is a `ProxyOnBackInvokedDispatcher` that forwards to a
`WindowOnBackInvokedDispatcher` once the view is attached, the app has a callback
registered on it at `PRIORITY_DEFAULT` (the priority AndroidX's `ComponentActivity`
uses), and invoking that callback — what the system does when a predictive-back
gesture commits — returns to Home without finishing the activity, with the PCM
worker still writing more samples after the screen closed and `PlaybackService`
not stopped by itself. The same runtime is handed a 132 px
status bar and a 96 px gesture bar: the header moves from 16 px to 148 px and the
navigation bar's bottom from 463 px to 367 px, so the screens consume the window's
own non-zero insets on a platform that no longer allows the edge-to-edge opt-out.

On the 35 runtime the two scenarios are reported skipped rather than passed, so
the `api36` job checks its own result: a step after Gradle fails the job unless
both flavours' `TEST-dev.nightfall.Android16RuntimeTest.xml` reports
`tests="2" skipped="0"`, which is what stops a job that lost the sandbox property
from staying green while measuring nothing. Breaking the app's `BackHandler` turns
the predictive-back assertion red ("back reported through OnBackInvokedDispatcher
closed the screen instead of returning to Home"), which is what makes it an
assertion rather than a re-run.

What this still does not prove: a phone. No gesture is generated, no system bar is
real, and `SystemClock` is virtual. Predictive back and the insets above are
measured on a JVM against the 36 platform classes; whether a physical Android 16
device reports the same insets and animation stays unverified and is part of the
hardware list below. The `release` job's runtime is unchanged: on Temurin 17 those
two scenarios are skipped and every other test still runs on API 35.

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
tests (51 in `app/src/testGithub/java/dev/nightfall/update`) and not as observed device
behaviour. The Compose
surfaces — the Settings toggle, the waiting row and the changelog dialog — were
compiled and linted, never rendered on a screen. The commit-time re-check is
tested as an ordering over a fake session, not against Android's installer.

Also unverified: a real download of a published release from GitHub (the tests use
a local server with the same protocol), behaviour behind a captive portal or a
proxy, and how each OEM image prompts. Releasing a version with this channel and
installing it on a physical device is the check that closes this gap.

## Hidden diagnostics that were not exercised

The diagnostics surface was drawn and driven on a JVM and never on a phone. The gesture is measured
against the real Settings screen, but Robolectric supplies its own touch dispatch and its own clock;
whether five real taps inside three seconds feel like a natural hidden gesture, and whether the
version row still reads as ordinary copy with the gesture on it, is not something a JVM can answer.

Three things in the collection path have no measurement behind them at all. `ActivityManager
.getHistoricalProcessExitReasons` is called on API 30 and newer and returns nothing meaningful under
Robolectric, so no crash and no ANR record has ever been read out of a real device by this code; the
token mapping is written from the platform's documented reason codes. The share sheet was never
opened: `Share report` builds a `text/plain` `ACTION_SEND` intent and starts a chooser, and no test
asserts that a device routes it. And playback sessions, playback failures and update outcomes are
recorded as subscribers to flows that the JVM tests drive by hand — the wiring between a real night's
playback and a journal entry has not been observed on hardware.

What is verified is the part that could quietly become a hoard or a leak: the caps, the oldest-first
drop, the emptiness before the switch is on, the erase, the closed token set, and the absence of the
whole package from the F-Droid APK.

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
