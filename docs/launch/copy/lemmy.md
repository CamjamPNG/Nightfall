# Lemmy

Lemmy has no sitewide self-promotion ban, but individual communities do, and one of
the two obvious Android communities does. The rules were read through Lemmy's own API
on **18 November 2026**, which is the live authoritative copy — no archive needed.

## Rule checked — where this post goes

`!androidapps@lemmy.ml`, community description via
`https://lemmy.ml/api/v3/community?name=androidapps`:

> **1. No distribution of APKs from unknown sources or direct downloads. You are only
> allowed to share from GitHub (no direct links, repo only), Play Store, F-Droid, or
> Aurora Store.**
> 2. No piracy …
> 3. No rooting apps allowed …
> 4. No apps that require root to function …
> 5. No software that requires ADB or patching system files …
> These rules are subject to change at any point in time with or without user notice.

So: link the **repository**, never a release asset URL, and never "download the APK
here". Nothing in this list forbids a maintainer posting their own project, and there
is no AI-content clause.

`!android@lemmy.ml` is the larger community (11,598 subscribers) and its description
says "Anything directly or indirectly related to Android is allowed" with "Obvious
spam will be removed" — an app announcement from its developer is inside its stated
scope, but it is a news-and-discussion community, so post there second, if at all,
not first.

## Draft — `!androidapps@lemmy.ml`

Title:

> Nightfall — an offline sleep-sound mixer where the DSP is a testable Kotlin module

Body:

> I'm the developer of Nightfall, a sleep-sound mixer for Android that I've been
> building for a few months. Two things make it what it is.
>
> **The network is an opt-in, not a feature.** It ships in two builds. The `fdroid`
> build has no updater, no network code and no network permission at all — it cannot
> open a socket — and it is on the Releases page as `Nightfall-<version>-fdroid-universal.apk`
> (a file there, not a listing: Nightfall is not in F-Droid's catalogue, and the
> repository the project intends to serve from is not up yet). The other APKs on that
> page add an updater that reads this repository's release manifest and, by default,
> installs a newer APK on its own once nothing is playing; that is its only network
> use, it never interrupts a mix, and it never uploads anything. Playback and music
> generation use no network in either build. No account, no ads, and no log of how you
> use it unless you switch its opt-in diagnostics on yourself: a bounded local journal
> that transmits nothing. The whole sound library ships in the APK.
>
> **The engine is a plain Kotlin module.** One 48 kHz stereo float mix, 1024-frame
> blocks, one audio-priority thread, a soft limiter at the end. It has no Android
> dependency, so it runs as plain JUnit: 127 tests, including zero per-block allocation
> inside the render path, click size at layer add/remove, loop seams on every bundled
> recording, and a measured loudness table that calibrates every sound in the library
> to the same reference level. The DSP being boring and testable was the point.
>
> What you actually use: independent layers (gain, mute, solo, pan, filters, EQ, fade,
> no layer cap — it warns when your device is struggling instead), 106 sounds across
> eleven noise colors, synthesized weather and water and fire, seven field recordings
> that loop for three minutes or more, and eighteen procedural music profiles generated
> on device with keys, tuning and seeded variation. Sleep timers fade out instead of
> cutting, and layers can stop on their own schedules.
>
> **Type a sentence rather than assemble a mix.** "rain on a tent", "ocean waves",
> "heavy rain on a metal roof with a fire" — a small parser on the phone turns the
> phrase into layers, at the intensity the words ask for. No model, no server, no
> connection, and a phrase it cannot place says so instead of guessing.
>
> **You can hear it before you install it.** Eight clips, twenty to thirty seconds each,
> rendered offline by this same engine, are in the repository:
> https://github.com/CamjamPNG/Nightfall/tree/main/docs/samples
>
> Since 2.0, six things worth naming. An **endless night** switch, so the mix evolves
> over hours instead of looping one bed — companion layers drift in and out, and every
> layer's tone drifts around its setting; on eight-hour renders the largest level gap
> between the night on and the night off at the same hour was 0.22 LU, which is a level
> measurement and not a claim about how it sounds. **Themed nights**: four presets that
> are a plan over the night rather than a snapshot of layers, each phase taking over
> across three minutes. A **home-screen play/pause widget** and a **Quick Settings tile**,
> both driving the app's own media session so their state follows what is playing rather
> than the last tap. A **first-run card** with three ways into a mix. A **hidden, opt-in
> diagnostics journal** (five taps on the version row, bounded, fixed tokens, nothing
> transmitted, absent entirely from the `fdroid` build). And **Android 16 targeting**, with
> the app's own suite run on an API 36 runtime in CI. Every UI string is in resources now
> with a build guard against the next literal, though nothing has been translated yet.
>
> Honest limits: a physical-device pass (Pixel 7, Bluetooth, overnight battery) is
> still pending and the README says so; the widget has never been placed on a real
> launcher; the tile has never been in a real shade; the diagnostics journal has never
> recorded a real crash; the Opus decoder the recordings now go through has never run on a
> phone, so if a platform refuses Opus the app tells you at start-up instead of playing
> silence; and no ear has heard the catalogue on a device. The APK is 11.8 MB — the seven
> recordings ship as 10,089,142 bytes of Opus where 1.8 carried 77,449,472 bytes of raw PCM.
>
> MIT licensed, no CLA, issues and PRs welcome:
> https://github.com/CamjamPNG/Nightfall
>
> Happy to answer anything about the mixer or the build.

Note: the repository link, not a release or APK link — that is rule 1, and it is the
rule most likely to get the post removed. The second link is a `tree/main/docs/samples`
path inside that repository, which is the same host and the same project; drop it if a
moderator reads rule 1 more strictly than it reads.
