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
> **The network is an opt-in, not a feature.** It ships in two builds. The F-Droid
> build has no updater, no network code and no network permission at all — it cannot
> open a socket. The build on the Releases page adds an updater that reads this
> repository's release manifest and, by default, installs a newer APK on its own once
> nothing is playing; that is its only network use, it never interrupts a mix, and it
> never uploads anything. Playback and music generation use no network in either
> build. No account, no analytics, no ads. The whole sound library ships in the APK.
>
> **The engine is a plain Kotlin module.** One 48 kHz stereo float mix, 1024-frame
> blocks, one audio-priority thread, a soft limiter at the end. It has no Android
> dependency, so it runs as plain JUnit: 97 tests, including zero per-block allocation
> inside the render path, click size at layer add/remove, loop seams on every bundled
> recording, and a measured loudness table that calibrates every sound in the library
> to the same reference level. The DSP being boring and testable was the point.
>
> What you actually use: independent layers (gain, mute, solo, pan, filters, EQ, fade,
> no layer cap — it warns when your device is struggling instead), eleven noise
> colors, synthesized weather and water and fire, seven field recordings that loop for
> three minutes or more, and eighteen procedural music profiles generated on device
> with keys, tuning and seeded variation. Sleep timers fade out instead of cutting,
> and layers can stop on their own schedules.
>
> 2.0 added four things worth naming. An **endless night** switch, so the mix evolves
> over hours instead of looping one bed — companion layers drift in and out, and every
> layer's tone drifts around its setting; on eight-hour renders the worst hour-to-hour
> level move was 0.22 LU. A **home-screen play/pause widget** that drives the app's own
> media session. A **hidden, opt-in diagnostics journal** (five taps on the version row,
> bounded, fixed tokens, nothing transmitted, and absent entirely from the F-Droid
> build). And **Android 16 targeting**, with the app's own suite run on an API 36
> runtime in CI. Every UI string is in resources now with a build guard against the
> next literal, though nothing has been translated yet.
>
> Honest limits: a physical-device pass (Pixel 7, Bluetooth, overnight battery) is
> still pending and the README says so; the widget has never been placed on a real
> launcher; the diagnostics journal has never recorded a real crash; the Opus decoder
> the recordings now go through has never run on a phone, so if a platform refuses Opus
> the app tells you at start-up instead of playing silence; and no ear has heard the
> catalogue on a device. The APK is 11.7 MB — the seven recordings ship as 10,089,142
> bytes of Opus where 1.8 carried 77,449,472 bytes of raw PCM.
>
> MIT licensed, no CLA, issues and PRs welcome:
> https://github.com/CamjamPNG/Nightfall
>
> Happy to answer anything about the mixer or the build.

Note: the repository link, not a release or APK link — that is rule 1, and it is the
rule most likely to get the post removed.
