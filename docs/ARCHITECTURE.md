# Nightfall architecture

## Ownership and data flow

`NightfallApp` owns one `NightfallRuntime`. `NightfallViewModel` exposes immutable `StateFlow` snapshots to Compose. Navigation and configuration changes do not own or recreate playback. DataStore Preferences stores a versioned JSON `SavedState` containing the active mix, complete layer DSP/music configurations, user presets, favorites, and settings. Writes debounce for 250 ms on the application's coroutine scope. Nothing is uploaded. Android backup is disabled.

`PlaybackService` extends Media3 `MediaSessionService`. Its `SimpleBasePlayer` adapter exposes the actual mixer state to Android media controls, a foreground playback notification, Bluetooth media buttons and the lock screen. It advertises play/pause, prepare and stop, without unsupported seeking. Metadata includes the mix, layer count and timer. Notification/system controls operate the same `AudioHost` as Compose.

`AudioHost` owns one audio-priority thread and one 48 kHz stereo floating-point `AudioTrack`. The Android-selected route is respected; no Bluetooth scanning, microphone permission or hard-coded output selection is used. A partial wake lock is held only during playback. Playback survives activity navigation/task removal; pausing releases audio focus and the wake lock. Transient focus loss pauses and resumes on focus gain; permanent loss requires a new user play action. Duck requests reduce app gain. The becoming-noisy receiver pauses on disconnect unless the user allows rerouting.

## Mixer

The pure Kotlin `core` module has no Android dependencies. `PcmMixer` receives immutable snapshots at buffer boundaries. A dynamically allocated map holds source state per layer. Existing sources retain their oscillator, PRNG, filter, loop and scheduler states on unrelated add/remove/reorder/gain changes. Removed layers retire after fading; preset application gives new layers unique IDs to crossfade complete mixes. There is no channel limit or fixed nine-channel architecture.

The mixer renders 1024-frame blocks into reused arrays. Independent enable/mute/solo determines gain targets. Gains smooth over 25 ms; lifecycle and timer fades are continuous. Per-channel DC/high-pass and low-pass filters precede three-band crossover EQ, stereo width, constant-power pan, independent gain and lifecycle fade. Master gain is independently smoothed. A soft-knee final limiter leaves samples below 0.45 untouched and asymptotically limits to 0.53, a -5.5 dBFS sample ceiling that begins at -6.9 dBFS and holds 4x oversampled true peaks under -1.0 dBTP. Nonfinite limiter input becomes silence. `Levels` trims every catalogue source to -18 LUFS at full layer gain, so a layer percentage means the same loudness for every sound and summing is unnormalised. Layer percentages remain independent digital gain rather than acoustic loudness.

Rendering uses a 4-block minimum output buffer (about 85 ms at 48 kHz), or Android's minimum if larger. This favors reliable sleep audio over instrument-like latency. AudioTrack underruns and moving-average render time as a fraction of the block budget are shown in Settings. At 65% render budget or more than three underruns, Home displays a warning. No fixed source cap is substituted for measuring device capacity. Source buffers and music voice pools allocate on layer creation; the sample loops avoid object allocation. The number of notes inside a music source is bounded for predictable CPU; the number of independent layers is not.

## Source algorithms

- White: uniform PRNG noise. Pink: Paul Kellet seven-pole approximation (44.1–48 kHz). Brown/red: leaky integration. Blue: differentiated pink. Violet/purple: differentiated white. Gray is an approximate perceptual shaping preset, not calibrated equal loudness. Green: mid-band shaping; black: very quiet low-passed noise; velvet: sparse random signed impulses. Nonstandard names are identified in the UI.
- Weather/water/wind/fire/hum: original stochastic physical-model textures, with filtered beds, multi-timescale gust/swell envelopes, randomized material resonances, drops, impacts and crackle. Thunder has nonperiodic events, randomized decay/pan, and continuous low-frequency roll modulation. These are explicitly described as synthesis, not captured recordings. Gentle snowfall represents an atmosphere, not an assertion that snow makes an audible noise.
- Field recordings: seven separately licensed sources in `assets/audio`, including birds, crickets, frogs, tawny owl at night, study room, distant urban storm and quiet café. Memory-mapped mono 24 kHz signed PCM data is shared between layers; each layer has its own read head. Every loop cycle exceeds three minutes (180.5–240 seconds). One-second overlap wrapping avoids hard loop seams without loading full recordings into float arrays. See AUDIO_LICENSES.md and the retained Commons metadata.
- Music: seeded progressions, key/major/minor scale, chord extensions, bass voices and probabilistic melodic notes. Pitches use `A4 * 2^((midi-69)/12)`. Timbres use additive and inharmonic partials, long release envelopes, cross-channel delay and four short feedback reverb lines. A wavetable avoids transcendental sine calls in the audio loop. Lo-fi and ambient sources remain entirely independent. Tuning is musical, without clinical claims.
- Binaural: independent 200 Hz and `200 + difference` oscillators (difference 1–20 Hz), with explicit stereo-headphone and evidence limitations.

## Time and automation

`SleepTimer` tracks `SystemClock.elapsedRealtime()` deadlines. It can pause/resume without wall-clock drift. After the deadline, it fades for the configured duration and the host stops after expiration. No alarm, WorkManager poll, network or periodic wake alarm is required while the audio thread is running. Independent layer stop schedules are based on frames played; paused playback consumes no layer time. Layer fade runs immediately before its stop time. Expired sources stop rendering, so a finished music layer does not consume synthesis CPU all night. Removing or extending its schedule fades it back in. A storm can evolve into rain over a per-layer duration. Timer state is intentionally session-only, never restored from a stale monotonic deadline after process death/reboot.

## Persistence and file exchange

JSON imports validate known sound IDs, unique layer IDs, finite numeric values, ranges and names. JSON is decoded from the input stream without an arbitrary file or layer-count cap; actual available memory remains finite. Android's Storage Access Framework supplies import/export access without broad filesystem permission. User presets can be renamed, duplicated, favorited, deleted and exported; bundled presets can be duplicated and favorited.

## Update channel

`app` builds two flavours from one source set. Every updater source file lives in
`app/src/github`, which declares `INTERNET`, `ACCESS_NETWORK_STATE`,
`REQUEST_INSTALL_PACKAGES` and `UPDATE_PACKAGES_WITHOUT_USER_ACTION`. `app/src/main`
names no update type at all: it calls three flavour-provided composables —
`UpdateOverlay`, `UpdateForegroundEffect` and `UpdateSettingsSection` — and hands
`UpdateForegroundEffect` one `Flow<Boolean>` of play/pause, which `app/src/fdroid`
answers in `UpdateSurfaces.kt` with no updater, stripping the four permissions and
showing only the installed version and where updates come from. The F-Droid APK therefore contains no manifest parser, no fetcher, no
installer, no state machine and no network code; `tools/check_release.py` asserts
that from the built APK's dex and permissions, not from the source tree.

The `github` channel reads the `release-manifest.json` asset that
`tools/package_release.py` publishes with every release: version name and code,
application id, `minSdk`/`targetSdk`, the pinned signing certificate digest, and
one entry per APK with its file name, ABI, byte size, SHA-256 and absolute download
address. Schema 2 adds `log`: the eight most recent releases, newest first, each
with its version, date and up to four notes. The notes are generated by
`release_log()` from the `CHANGELOG.md` section the release workflow has just
written — never hand-authored, and bounded to 120 characters a line, so the whole
document the app fetches stays a few kilobytes. `UpdateLogParser` bounds and
sanitises them again on arrival and on every read from Preferences: a hostile or
malformed `log` costs the notes, never the release, because none of it decides an
install. Parsing (`ReleaseManifestParser`) and every trust decision
(`UpdatePolicy`) are pure Kotlin with no Android types, so they are unit-tested
against hostile documents: unknown architectures, downgrades and replays, foreign
package ids, foreign certificate digests, checksums that do not match, addresses
pointing anywhere but this repository's release assets, and truncated or oversized
responses.

A check is one ranged read of the manifest. With **Update automatically** on — the
GitHub channel's default — the channel then downloads the single APK chosen for the
device's architecture and installs it without a tap; with it off, the download
waits for the Settings or banner button, which is what the previous release did.
`ReleaseFetch` reports progress, refuses a response larger than the manifest's byte
count, and refuses a download that stops short. The file is then inspected by
Android itself: package name, version code, signer certificate list and the native
libraries it actually carries. Only a file that matches all of that reaches
`PackageInstaller`. The verified download is recorded in private preferences, so a
process that dies mid-install finds it and a retry does not fetch ninety megabytes
again.

The install requests `USER_ACTION_NOT_REQUIRED` on Android 12 and newer, which
completes with no prompt when the platform's conditions hold (this app targets 35
and declares the permission the platform requires). `STATUS_PENDING_USER_ACTION`
is handled as the fallback: the confirmation intent Android supplies is shown and
its outcome recorded. `STATUS_SUCCESS` and every failure code are written to
private preferences so the next launch can report a failure it did not live to
see.

`GithubUpdater` owns the state machine — idle, checking, available, downloading
with progress, ready to install, installing, up to date, a notice that a check was
requested inside the interval, and failed with a reason — in a single `StateFlow`
observed by a Compose banner and the Settings row. All I/O runs on `Dispatchers.IO`;
nothing here is reachable from the audio thread, no work is scheduled when the app
is not in the foreground, and there is no worker, alarm or service, so checking
never wakes the radio behind the user's back.

**The playback guard.** An install kills the process. An unattended install during
playback would therefore silence, at three in the morning, the sound the app exists
to make. So `ready to install` is a real state rather than a moment: a verified
update waits there until `UpdateAutomation.mayInstallNow` is true, which requires
the setting, and a playback fact of *stopped*, and nothing else. A foreground entry
that arrives before the app has reported playback is `UNKNOWN`, which refuses, so a
race between the updater loading and the status flow can only postpone an install
and never interrupt audio. The window opens on a play-to-pause transition or on the
next launch, and the banner and Settings row say which one it is waiting for. It is
also read a second time immediately before the installer session commits
(`InstallWindow`): writing the APK into that session takes seconds with Play and the
media-session controls still live, so playback that resumed during the copy abandons
the session and puts the update back to waiting instead of killing the audio. A tap
on Update is the user asking, and the guard does not hold that back.

The commit window is narrowed, not eliminated. After the guard's last read the
commit is one binder write away — microseconds — and a play request in that
instant would still be interrupted by the replacement. The copy is what made the
defect reachable (seconds, not microseconds) and it is guarded; the residue is
set against a play path that needs tens of milliseconds to make sound (tap →
Compose → `runtime.play()` → foreground service → `AudioTrack.play`), so it is
not reachable by a person. Closing it to zero would mean the audio start path
blocking on the installer, and the audio path never observes update state.

`CheckThrottle` keeps two rules because they are two different promises. Every
request — the foreground hint and the Settings button take the same path — waits at
least fifteen minutes since the previous attempt, and the attempt is recorded before
the request, so a fetch or parse that fails counts too and nothing can be hammered.
The app's own check additionally requires six hours since the last *successful*
answer, so a failed attempt never spends that window. A press inside the floor makes
no request: it re-shows the update already known or says how long until the next one
is allowed. A stored timestamp in the future means the device clock moved and is
treated as expired rather than locking the channel.

Installer verdicts reach the live row through `InstallResults`, a process-local
flow the receiver publishes to, so a rejection or a cancelled confirmation is shown
immediately rather than after a restart. The same outcome is written to private
preferences and reconciled on the next foreground entry, which is what covers a
process Android killed mid-install. An install row that survives into a foreground
entry in the same process means the replacement never happened, so the row drops
back to idle instead of claiming an install forever. Dismissing the banner records
the release whose notice was hidden and nothing else: the update stays available in
Settings, and a different release brings the banner back.

**What's new.** The changelog is the `log` from the last manifest read, kept in
private preferences, so the view renders from data the app already holds and makes
no request of its own; with no log stored it says so in one line. Settings →
Updates lists every stored release — version, date, notes. After the app replaces
itself, the first foreground entry of the new build shows the notes of the release
it now runs, once: the log's newest entry must name the installed version and carry
notes, the notes must not have been seen, and `dismissLog()` records that they
were. There is no build flavour or instrumented path behind this beyond Compose.

## Limits

The update guard's residue: an install re-reads playback immediately before committing and defers if playback resumed, so the interval in which a play request can be overtaken by the commit is one binder write (microseconds) rather than the multi-second APK copy, which is guarded. That interval is not zero, and it is not closed by design: the alternative is the audio start path waiting on the installer, which would put update state in the audio path.

DSP models are interpretations, not high-fidelity recordings of every named environment. Gray noise is an approximate preset. Headphone acoustic levels cannot be derived from gain percentages. Hardware Bluetooth behavior, power management, perceptual audio quality and all-night battery life need physical-device validation. Background execution is supported through Android's media foreground-service model; no manufacturer-independent all-night guarantee is made.
