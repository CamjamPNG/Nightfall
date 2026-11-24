# r/Android

Highest reach, strictest gate, and not skippable in a way that goes unnoticed:
r/Android's developer rule has three conditions and an account that fails any of
them gets the post removed rather than argued with.

## Rule checked

Rules page for `r/Android`, read from an Internet Archive capture dated **2 August
2026** (`web.archive.org/web/20260802214400/https://old.reddit.com/r/Android/about/rules`).
Reddit `403`s this machine; re-read the live page before posting.

> **4. Rule 4 - No self-promotional spamming**
> Developers may post their own apps, provided:
> 1- Their post must be a **self post with a detailed description** of their app.
> 2- The developer's account must be **at least three months old AND have a
> reasonable posting history on r/Android**.
> 3- The developer must **interact with users in the thread**. We do not allow
> developers to simply dump a link to their app and leave.

The subreddit's own content philosophy, printed above the rules, is the reason to
read condition 2 strictly:

> Content which benefits the community (news, rumours, and discussions) is generally
> allowed and is valued over content which benefits only the individual (technical
> questions, help, buying/selling, rants, self-promotion, etc.) which will be removed
> if it's in violation of the rules.

The same page lists a weekly thread worth using first: **Saturday APPreciation**, a
standing "Day/Topic" thread for apps. Contributing an app to a thread the community
already runs is a much cheaper first move than a standalone self post, and it does
not require the three-month history a standalone post needs.

Verdict: **not ready today.** Condition 2 is an account precondition, not something
copy can satisfy. Camden should only post if his Reddit account is at least three
months old and already has genuine r/Android participation; otherwise the right move
is the Saturday thread, or skip r/Android entirely and take the smaller channels.

## Draft

Suggested title:

> Nightfall: I made a sleep-sound mixer where playback needs no network at all, and the DSP is a testable Kotlin module

Body:

> I've been building Nightfall, a native sleep-sound mixer for Android, and I want to
> share it with a community that will tell me what's wrong with it.
>
> **The premise.** Sleep audio is the category where developers seem most comfortable
> shipping something that phones home — account walls, streaming, an analytics SDK, a
> subscription for a rain loop. Nightfall ships no accounts, no ads and no tracking — it
> keeps no log unless you switch a local diagnostics journal on yourself, and that transmits
> nothing — and it ships in two builds: the `fdroid` one has no updater, no network code and
> no network permission at all, so it cannot open a socket; the GitHub one adds an updater that
> reads the release manifest and, by default, installs a newer APK on its own once
> nothing is playing — it never interrupts a mix — and that is its only network use.
> Playback and music generation run entirely on the phone.
>
> **How it's built.** A single 48 kHz stereo float mix feeds one `AudioTrack` from one
> audio-priority thread, in 1024-frame blocks, with a soft limiter at the end. The
> engine lives in a pure Kotlin module with no Android dependency, so it runs under
> plain JUnit: 127 tests covering nine simultaneous sources, 512 allocated layers,
> phase continuity across unrelated sources, limiters, seeded music generation, timer
> fades, loop seams, spectral slope and click size at layer add/remove.
>
> **The mixer part.** Layers are independent: gain, mute, solo, pan, filters, EQ,
> fades, plus per-layer stop schedules on the timer. There's no hard layer cap — the
> app measures its own render load and warns when the device is struggling, which felt
> more honest than refusing a tenth layer. Play/pause is on the lock screen, a home-screen
> widget and a Quick Settings tile, all three driving the same media session so they show
> what is playing rather than the last tap.
>
> **The content part.** 106 sounds: eleven noise colors (five from actual spectral
> algorithms, three documented as shaping presets), synthesized weather and water and
> fire, and seven bundled field recordings with loops of three minutes or more. Music is
> generated on-device from eighteen procedural profiles with keys, scales, tuning and
> seeded variation. Synthesized material says it's synthesized; one ambience I wanted
> couldn't be licensed, so the library marks it unavailable rather than substituting
> noise. You can also type a phrase — "rain on a tent", "heavy rain on a metal roof with a
> fire" — and a local parser builds the layers at the intensity the words ask for. No
> model, no server, no connection.
>
> **Hear it before you install it.** Eight clips, twenty to thirty seconds each, rendered
> offline by the same engine and encoded to Opus, are in the repository:
> https://github.com/CamjamPNG/Nightfall/tree/main/docs/samples — that is the part of the
> project a phone can't show you.
>
> **What has shipped since 2.0.** An endless-night switch, so the mix evolves across hours
> instead of looping one bed: companion layers from the same families drift in and out, and
> every layer's tone drifts around its setting. On eight-hour renders of one preset, the
> largest level difference between the night on and the night off at the same sampled hour
> was 0.22 LU — a level measurement, not a claim about how it sounds. Four presets that are
> themed nights, a plan over the night rather than a snapshot of layers, each phase taking
> over across three minutes. The widget and tile above. A first-run card with three ways
> into a mix. A hidden diagnostics journal behind five taps on the version row: bounded,
> fixed tokens, no free text, nothing transmitted, and absent from the `fdroid` build
> entirely. And an Android 16 target with the app's own suite executing on an API 36 runtime
> in CI. Every UI string moved into resources with a build guard against the next literal, so
> the app is translatable-ready — though English is still the only locale.
>
> **What I haven't proven.** A physical Pixel 7 pass, Bluetooth behaviour, acoustic
> loudness, overnight battery and an update actually installing on a phone are all
> pending, and the README says so. So are five newer things, and they belong in the same
> breath: the Opus decoder the seven recordings now go through has never run on a phone
> (if a platform refuses Opus the app says so at start-up rather than playing silence),
> nobody has heard the catalogue on a device, the widget has never been placed on a real
> launcher, the tile has never been in a real shade, and diagnostics has never recorded a
> real crash. Playback lifecycle is covered by twelve Robolectric scenarios that run under
> both builds in CI; the emulator instrumentation attempt before that never passed, and the
> failure is written down in `docs/TESTING.md` instead of being dropped. And two local
> builds of tag v1.0.1.2 are byte-identical, which is weaker than an independent rebuild and
> is documented as such.
>
> **On size, because it changed.** The current release is 11.8 MB, down from 79.5 MB in
> 1.11: the seven recordings ship as 10,089,142 bytes of Opus instead of 77,449,472 bytes of
> raw PCM. The cost is a decoder in the path that used to memory-map a file, and that
> decoder is the unverified piece above. The per-ABI split exists but is not what fixed it —
> it saves about 100 KB.
>
> **What I'd like from this thread:** anyone who runs it overnight on a device I don't
> have. The app shows render-load and underrun counters in Settings, and a logcat
> excerpt with a layer count and the counters is worth more to me than a bug report
> without them.
>
> Source, APKs, screenshots and the honest limitations list:
> https://github.com/CamjamPNG/Nightfall — MIT licensed.
>
> I'm the developer; I'll be in the comments.

Notes: this post is a technical write-up because that is what survives r/Android.
Do not post it before the account condition is met, and do not post it and leave —
condition 3 is enforced. Two links are in the body (the repository and a folder of audio
inside it); neither is a download link, which keeps rule 4's "self post with a detailed
description" intact.
