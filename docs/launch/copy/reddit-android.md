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

> Nightfall: I made an offline sleep-sound mixer that has no INTERNET permission, and the DSP is a testable Kotlin module

Body:

> I've been building Nightfall, a native sleep-sound mixer for Android, and I want to
> share it with a community that will tell me what's wrong with it.
>
> **The premise.** Sleep audio is the category where developers seem most comfortable
> shipping something that phones home — account walls, streaming, an analytics SDK, a
> subscription for a rain loop. So this one doesn't have the permission to do any of
> that: no `INTERNET` in the manifest, no accounts, no ads, no tracking. Everything is
> computed on the phone.
>
> **How it's built.** A single 48 kHz stereo float mix feeds one `AudioTrack` from one
> audio-priority thread, in 1024-frame blocks, with a soft limiter at the end. The
> engine lives in a pure Kotlin module with no Android dependency, so it runs under
> plain JUnit: 42 tests covering nine simultaneous sources, 512 allocated layers,
> phase continuity across unrelated sources, limiters, seeded music generation, timer
> fades, loop seams, spectral slope and click size at layer add/remove.
>
> **The mixer part.** Layers are independent: gain, mute, solo, pan, filters, EQ,
> fades, plus per-layer stop schedules on the timer. There's no hard layer cap — the
> app measures its own render load and warns when the device is struggling, which felt
> more honest than refusing a tenth layer.
>
> **The content part.** Eleven noise colors (five from actual spectral algorithms,
> three documented as shaping presets), synthesized weather and water and fire, and
> seven bundled field recordings with loops of three minutes or more. Music is
> generated on-device from eighteen procedural profiles with keys, scales, tuning and
> seeded variation. Synthesized material says it's synthesized; one ambience I wanted
> couldn't be licensed, so the library marks it unavailable rather than substituting
> noise.
>
> **What I haven't proven.** A physical Pixel 7 pass, Bluetooth behaviour, acoustic
> loudness and overnight battery are all pending, and the README says so. The
> instrumentation suite that was supposed to cover playback lifecycle has not gone
> green on this setup. And two local builds of tag v1.0.1.2 are byte-identical, which
> is weaker than an independent rebuild and is documented as such.
>
> **What I'd like from this thread:** anyone who runs it overnight on a device I don't
> have. The app shows render-load and underrun counters in Settings, and a logcat
> excerpt with a layer count and the counters is worth more to me than a bug report
> without them.
>
> Source, APKs, and the honest limitations list:
> https://github.com/CamjamPNG/Nightfall — MIT licensed.
>
> I'm the developer; I'll be in the comments.

Notes: this post is a technical write-up because that is what survives r/Android.
Do not post it before the account condition is met, and do not post it and leave —
condition 3 is enforced.
