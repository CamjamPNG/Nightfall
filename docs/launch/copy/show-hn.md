# Show HN — facts for Camden to write from, and no drafted copy

This file deliberately contains no post text. See the rule.

## Rule checked

Show HN guidelines, read on **18 November 2026** from
`https://news.ycombinator.com/showhn.html`:

> On topic: things people can run on their computers or hold in their hands.
> Off topic: blog posts, sign-up pages, newsletters, lists, and other reading
> material. Those can't be tried out, so can't be Show HNs.
>
> The project should be **non-trivial**. Don't post quickly-generated one-offs …
> Share something that is deeply personal and interesting to you. Explain how and why.
>
> The project must be **something you've worked on personally and which you're around
> to discuss**.
>
> Please make it **easy for users to try your thing out**, ideally without barriers
> such as signups or emails. …
> If your work isn't ready for users to try out, please don't do a Show HN.
>
> New features and upgrades ("Foo 1.3.1 is out") generally aren't substantive enough
> to be Show HNs. A major overhaul is probably ok.
>
> Please **don't ask friends to upvote or comment.** That's not ok on HN.

And from the HN guidelines page (`https://news.ycombinator.com/newsguidelines.html`),
the clause that decides this file:

> **Don't post generated text or AI-edited text. HN is for conversation between
> humans.**

That is why there is no draft here. HN is a conversation, the submitter has to be
present in it, and a pre-written post — even a good one — is the thing the guideline
names. Anything Camden submits there has to be in his own words, written by him.

## What HN can actually evaluate, and what it can't

The audience mostly does not have an Android phone on their desk, so "install this
APK" is the barrier the guidelines warn about. Two things a technical audience *can*
evaluate without one: eight clips you can play in a browser, and an engine you can
clone and run.

- **Hear the engine before installing anything.** `docs/samples/` holds eight Opus
  clips, twenty to thirty seconds each, rendered offline by the same synthesis code the
  app runs — including a preset, a themed night's opening phase, and four mixes built by
  typing a phrase. `docs/samples/README.md` gives each clip's exact settings, its peak,
  its integrated loudness and its byte length, and a test (`SampleClipTest`) re-renders
  each recipe and fails if a level has moved. Nobody has listened to any of them with an
  ear; the document says so at the bottom, which is the point.
- **A DSP core with no Android dependency.** `core/` is plain Kotlin/JVM: one 48 kHz
  stereo float mix, 1024-frame blocks, soft limiter, deterministic sources, timers.
  It runs as ordinary JUnit — `./gradlew :core:test` — so a reader can clone it and
  watch the suite pass without an emulator or a device: 127 tests are defined, 126 run,
  and the one skipped by default is an opt-in level-calibration generator. One of the
  suite renders an eight-hour night through the real render path.
- **The render path is treated as a hard real-time constraint.** Zero allocations per
  block is asserted by a test, not a comment; click size at layer add/remove, loop
  seams on every bundled recording, spectral slope, DC offset and tuning in cents all
  have measured thresholds. `docs/ARCHITECTURE.md` explains the deadline and the
  decoder now sitting on it.
- **The testing record includes the failure.** `docs/TESTING.md` records that the
  instrumentation suite terminated with `Process crashed` and a startup ANR on a
  software-emulated Pixel 7 profile, and that the hardware pass is still pending.
  Playback lifecycle is covered instead by twelve Robolectric scenarios that run under
  both builds in CI. That is the unusual part: the repo publishes what did not work
  and what replaced it.
- **Two local builds are byte-identical** at tag `v1.0.1.2`
  (`docs/verification/reproducibility.json`), and the same document states plainly
  that this is not an independent rebuild, and that `apksigcopier compare` fails
  against the published APK because the packaging step recompresses entries. If a
  commenter asks about reproducible builds, that is the honest answer including the
  part that doesn't flatter the project.
- **The size story has an ending now.** 1.8 was 79 MB, of which 77.4 MB was uncompressed
  16-bit mono 24 kHz PCM memory-mapped by the mixer. R8 and resource shrinking had
  already taken the dex from 47.4 MB to 2.9 MB. 1.12 closed the rest: the seven
  recordings ship as 10,089,142 bytes of Opus in containers the app parses itself, and
  the current release's `arm64-v8a` APK is 11,832,096 bytes (3,138,552 of it dex, 17,208
  native libraries). The interesting half is the cost — a decoder now sits in the
  render path where a mapped file used to be, and it has never run on a phone. The
  containers are verified against a conformant pure-Java decoder in the test suite,
  which proves the format and says nothing about Android's `MediaCodec`. A platform
  that refuses Opus gets a message at start-up instead of silence.
- **A phrase becomes a mix locally, deterministically.** Type "heavy rain on a metal roof
  with a fire" and `LocalPhraseInterpreter` maps it onto catalogue entries and intensity
  levels, on the device. It is a small parser, not a language model: a phrase it cannot
  place says so instead of inventing a mix, and `Samples.kt` refuses a documented phrase
  that would not parse. That is a design argument an HN thread can actually have —
  offline, inspectable, no server, no model weights.
- **Things re-introduced since 2.0, each with its own unverified edge.** An endless-night
  mode that evolves a mix over hours (measured: 0.22 LU largest level difference between
  night on and night off at the same hour, across eight-hour renders; not listened to on
  hardware). Four themed-night presets whose phases take over across three minutes, landing
  within 0.01 dB of the same mix with the plan off. A home-screen widget and a Quick
  Settings tile driving the app's own media session (20 and six Robolectric scenarios;
  never placed on a launcher or in a shade). A hidden, opt-in diagnostics journal behind
  five taps on the version row — bounded to 200 entries / 64 KB / 30 days, fixed tokens,
  no free text, nothing transmitted, and compiled out of the `fdroid` build (never recorded
  a real crash). An Android 16 target with the app's suite running on an API 36 runtime in
  CI. And every UI string extracted into resources behind a build-time guard against the
  next literal — translatable-ready, with no translation yet.
- **The network decision is a design argument, not a slogan.** Two product flavours
  (`app/src/github`, `app/src/fdroid`): the `fdroid` build removes `INTERNET`,
  `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` and
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION` and compiles a stub instead of the updater;
  the GitHub build carries a thirteen-file updater that verifies SHA-256, package
  id, version code and the installed app's signing certificate before handing an APK
  to Android's installer, and refuses to install while audio is playing. "Remove the capability from the build that does not need it"
  is the sort of thing this audience argues about productively, and the honest
  caveat is that the GitHub APK does declare `INTERNET`.

## The conditions before posting

1. Camden writes the submission and the comments himself, from the facts above.
2. He is around for the day to answer — the guideline's "around to discuss" clause.
3. There is a "try it" path that costs a reader nothing. That is now the eight clips in
   `docs/samples/` — a browser and no install — with `./gradlew :core:test` and a
   release APK behind it for anyone who wants the code. Hardware is still unverified and
   should be said, not discovered.
4. Nobody is asked to upvote or comment, including in any other community.

If those four cannot be met, skip Show HN. A thin Show HN that gets eight points does
the project less good than no post, because the submission stays indexable.
