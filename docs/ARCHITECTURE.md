# Nightfall architecture

## Ownership and data flow

`NightfallApp` owns one `NightfallRuntime`. `NightfallViewModel` exposes immutable `StateFlow` snapshots to Compose. Navigation and configuration changes do not own or recreate playback. DataStore Preferences stores a versioned JSON `SavedState` containing the active mix, complete layer DSP/music configurations, user presets, favorites, and settings. Writes debounce for 250 ms on the application's coroutine scope. Nothing is uploaded. Android backup is disabled.

`PlaybackService` extends Media3 `MediaSessionService`. Its `SimpleBasePlayer` adapter exposes the actual mixer state to Android media controls, a foreground playback notification, Bluetooth media buttons and the lock screen. It advertises play/pause, prepare and stop, without unsupported seeking. Metadata includes the mix, layer count and timer. Notification/system controls operate the same `AudioHost` as Compose.

`AudioHost` owns one audio-priority thread and one 48 kHz stereo floating-point `AudioTrack`. The Android-selected route is respected; no Bluetooth scanning, microphone permission or hard-coded output selection is used. A partial wake lock is held only during playback. Playback survives activity navigation/task removal; pausing releases audio focus and the wake lock. Transient focus loss pauses and resumes on focus gain; permanent loss requires a new user play action. Duck requests reduce app gain. The becoming-noisy receiver pauses on disconnect unless the user allows rerouting.

## Mixer

The pure Kotlin `core` module has no Android dependencies. `PcmMixer` receives immutable snapshots at buffer boundaries. A dynamically allocated map holds source state per layer. Existing sources retain their oscillator, PRNG, filter, loop and scheduler states on unrelated add/remove/reorder/gain changes. Removed layers retire after fading; preset application gives new layers unique IDs to crossfade complete mixes. There is no channel limit or fixed nine-channel architecture.

The mixer renders 1024-frame blocks into reused arrays. Independent enable/mute/solo determines gain targets. Gains smooth over 25 ms; lifecycle and timer fades are continuous. Per-channel DC/high-pass and low-pass filters precede three-band crossover EQ, stereo width, constant-power pan, independent gain, lifecycle fade and fixed headroom. Master gain is independently smoothed. A soft-knee final limiter leaves samples below 0.75 untouched and asymptotically limits to 0.98. Nonfinite limiter input becomes silence. Layer percentages describe independent digital gain rather than acoustic loudness.

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

## Limits

DSP models are interpretations, not high-fidelity recordings of every named environment. Gray noise is an approximate preset. Headphone acoustic levels cannot be derived from gain percentages. Hardware Bluetooth behavior, power management, perceptual audio quality and all-night battery life need physical-device validation. Background execution is supported through Android's media foreground-service model; no manufacturer-independent all-night guarantee is made.
