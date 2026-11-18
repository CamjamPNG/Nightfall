# Launch kit

How to get Nightfall in front of people who will actually want it, without lying
to anyone on the way. Everything here obeys one rule: **every number in public
copy traces to `docs/verification/` or to a fact in this repository.**

Rule pages were read for this document on 18 November 2026 and are quoted with
their source beside each post. Two of the six channels in the original plan turned
out to be closed to this project, and two more are closed to *AI-drafted copy*;
the reasons are quoted in `docs/launch/copy/dropped.md` rather than quietly
dropped. Reddit itself blocks unauthenticated requests from this build machine
(`403`, "You've been blocked by network security"), so the subreddit rules below
come from Internet Archive captures of those pages, dated in each entry.

## Who this is for

Someone who cannot sleep without continuous sound and has already been let down
by the alternatives: an app that wants an account before it will play a fan
noise, one that ships a thirty-second rain loop the brain latches onto at 2 a.m.,
one that asks for a subscription or a data connection. They are mildly technical —
they know what an APK is and sideloading one does not scare them — they care that
an app cannot phone home, and they notice when a mixer labels synthesized rain as
a field recording.

The secondary audience reads or changes the DSP: the audio engine is a Kotlin
module with no Android dependency and 42 JVM tests that run in seconds.

## State of the product today

Everything in this section was read out of the tree or measured from the published
release on 18 November 2026. Copy that contradicts it is wrong.

| Fact | Value | Where |
| --- | --- | --- |
| Latest release | `v1.2`, versionCode 9, published 2026-11-18 | `gh release list`; `CHANGELOG.md` |
| Published APK options | five: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `universal` | release assets for `v1.2` |
| `arm64-v8a` size | 91,129,860 bytes (86.9 MiB) | release asset metadata |
| `universal` size | 91,228,684 bytes (87.0 MiB) | release asset metadata |
| Payload split | 77.45 MB of PCM under `assets/audio`, 47.26 MB of dex, 17 KB of native libs | `unzip -l` of the `arm64-v8a` APK |
| Audio format | 7 recordings, 16-bit mono, 24 kHz, cycles 180.5–240 s, resampled into the 48 kHz mix | `Sources.kt:299` (`RecordingSource`); `docs/audio-credits.json` |
| Permissions | no `INTERNET`, no `RECORD_AUDIO`; background service, notifications, wake lock — checked on the published `v1.2` APK's manifest, not only in source | `app/src/main/AndroidManifest.xml`; `strings -el` on the APK's `AndroidManifest.xml` |
| Tests | 42 `@Test` in `core/src/test`, no Android dependency | `grep -c @Test`; `docs/TESTING.md` |
| Licence | MIT code; recordings CC BY / CC BY-SA / CC0 | `LICENSE`, `AUDIO_LICENSES.md` |
| Not verified | anything on hardware: Pixel 7 pass, Bluetooth, loudness, overnight battery | `docs/TESTING.md` |

The size is the number that decides two of the channels below, and it is worth
being precise about why it is 87 MiB. Native libraries are not the cause: all five
per-ABI APKs are within 100 KB of each other. The payload is 74 MB of uncompressed
PCM (`noCompress += "pcm"` in `app/build.gradle.kts`, so the mixer can map the
files) and 47 MB of dex from a release build that sets no `minifyEnabled`.

## Channels, with the rule that actually bites

Ordered by whether the channel is open to this project at all, not by reach.

| Channel | Open? | What the rule says | Draft |
| --- | --- | --- | --- |
| GitHub itself | Yes | No gate; topics, description, release notes | — (one hour of settings, no PR needed) |
| r/droidappshowcase | Yes | 1 app post/week; link the app page, not the APK; karma ≥ 2 | `copy/droidappshowcase.md` |
| Lemmy `!androidapps@lemmy.ml` | Yes | Repo link allowed, direct APK links not | `copy/lemmy.md` |
| Mastodon / Fedi | Yes | No link-only posts, no hashtag spam, no repeated self-promotion | `copy/mastodon.md` |
| r/Android | Conditionally | Self post, ≥ 3-month account with r/Android history, present in the thread | `copy/reddit-android.md` |
| Show HN | Yes, but Camden must write it | "Don't post generated text or AI-edited text" | `copy/show-hn.md` — facts, not text |
| r/FOSSdroid | **No** | "Vibe-coded apps are banned from this subreddit" | `copy/dropped.md` |
| r/insomnia | **No** | "No self-promotion"; "developing an app … keep it to yourself" | `copy/dropped.md` |
| Lemmy `!android@lemmy.world` | **No** | "No spam, self promotion, or upvote farming" | `copy/dropped.md` |
| r/androidapps | **No** | Self-promotion "not allowed"; redirects to r/droidappshowcase | `copy/dropped.md` |

The one-line version: **four channels are ready to use, one needs Camden to write
it himself, and four are closed.** Posting order is r/droidappshowcase or Lemmy
first (small, on-topic, rule-friendly), Mastodon next with the demo clip, R/Android
last and only from an account that already lives there.

### GitHub itself — do this first

Topics (`android`, `kotlin`, `jetpack-compose`, `dsp`, `offline-first`, `sleep`),
a repository description, and a handful of genuine `good first issue` entries.
This is where contributors arrive from, it costs an hour, and it has no rules to
break. A repository that looks maintained gets linked by other people, which is
the only distribution that compounds.

### IzzyOnDroid — the channel that fits the product, and does not fit the APK

Read `docs/launch/izzyondroid-request.md` for the full evidence trail and the
ready-to-paste request. Short version, in the order the blockers matter:

1. **The AI policy is a hard conflict, not a size problem.** Their App Inclusion
   Policy says they are "strongly opposed to apps which are fully or in part
   created by generative AI tools" and that "Vibe-coded apps will be rejected",
   while their request form *requires* an "Assistance Level" answer with a
   "Dominant – Most code or content was 'AI'-generated" option. Nightfall's code
   was written largely by AI coding agents under Camden's direction. This is
   Camden's call to make, not a copy problem to be worked around, and the form
   must not be filed with "None".
2. **Size.** 30 MB rule of thumb, and the contributing page states plainly: "We
   cannot currently accept APK files over 30 MB." Every published APK is ~87 MiB.
   Per-ABI splits are their usual remedy and are already in place, so they cannot
   help — native libraries are 17 KB and the bulk is 74 MB of raw recordings plus
   47 MB of unminified dex. A lossy Opus re-encode of the recordings is in progress
   (~96 kbit/s, roughly 20 MB of audio) with no date; see
   `docs/launch/izzyondroid-request.md` for the measurements and what is left.
3. **They ship our APK, not their build.** Their FAQ: "App Source — APK provided
   by the app's dev team". Reproducible-build verification is optional trust on
   top, so our `apksigcopier` gap does not block inclusion.
4. **Submission is a Codeberg issue**, using the `App Inclusion Request` form in
   `IzzyOnDroid/repodata`. That needs a Codeberg account, which only Camden can
   create.

### F-Droid

The F-Droid listing is the repository's own work (`fdroid/`, `docs/FDROID.md`).
Nothing in this document submits to F-Droid or claims it has been submitted.

## The demo script

Record it on the phone, not in a mockup. `adb shell screenrecord` or the built-in
recorder with **Media sounds** enabled so the audio is captured, 60–90 seconds,
portrait. Turn on **airplane mode** before you start and leave the status bar
visible for the whole clip — that one detail does more work than any sentence here.

| Time | On screen | Say |
| --- | --- | --- |
| 0:00 | Home, Nine-Layer Dream loaded, airplane-mode icon visible | "This is a sleep sound mixer with no internet permission. Airplane mode the whole time." |
| 0:05 | Press play, waveform moves | "Nine layers already running: rain, wind, fire, noise, lo-fi music." |
| 0:12 | Drag the rain layer's gain down, then back up | "Every layer is its own synth or recording with its own gain, filters and pan." |
| 0:22 | Solo the rain layer | "Solo it and the other eight drop out — nothing restarts, nothing clicks." |
| 0:30 | Add "Crickets" from the Library | "The library has colored noise, synthesized weather, and seven field recordings, each looping for at least three minutes before it repeats." |
| 0:40 | Open Studio on the lo-fi layer, change key and tempo | "The music is generated on the device — key, tempo, tuning, texture, reverb." |
| 0:50 | Back to Home, sleep timer, 20 minutes | "Timers fade out rather than cut. Individual layers can stop earlier." |
| 1:00 | Lock the screen, show lock-screen controls | "It keeps playing with the screen off, with lock-screen and Bluetooth controls." |
| 1:08 | Settings, load and underrun counters | "It measures its own render load and says when the phone is struggling, instead of capping layers." |

End card: the repository URL, the MIT licence, and one line — *free, offline, no
account*. No star badge, no invented user count. Cut a 20-second vertical version
for Fedi: press play, solo, add a sound, lock screen.

This clip has not been recorded: there is no device on these build machines and no
`/dev/kvm` for an emulator, so `docs/TESTING.md` still records the Pixel 7 pass as
pending. Every channel below that shows the clip needs it first.

## What is actually different, honestly

Each claim is checkable in the repository and each has its limit stated next to it.

| Claim | Evidence | What it does not mean |
| --- | --- | --- |
| No internet permission in the app | `app/src/main/AndroidManifest.xml`; `tools/check_release.py` fails the release if `INTERNET`, `RECORD_AUDIO` or the ad ID permission appears | Says nothing about your device's other apps |
| A DSP core with real tests | 42 deterministic JVM tests in `core/src/test/`, free of Android dependencies | Tests prove signal behavior, not that it sounds good to you |
| No layer-count cap, and measured capacity instead | `MixerTest` no-cap case; the render-budget warning on Home | Your phone has finite CPU; a heavy mix can still underrun |
| Bundled recordings loop for at least 180 seconds | `docs/audio-credits.json` cycle lengths; enforced by `tools/prepare_audio.py` and `tools/check_release.py` | Long loops are not the same as a well-recorded loop |
| Synthesis is labeled as synthesis | Catalogue descriptions; "distant household ambience" is declared unavailable rather than faked | Some sounds are interpretations, not recordings of that place |
| MIT licence, voluntary commercial courtesy | `LICENSE`, `COMMERCIAL_COURTESY.md` | The courtesy request is not a licence condition |
| Two local release builds are byte-identical | `docs/verification/reproducibility.json`, tag `v1.0.1.2` | Not an independent F-Droid build, and not a signature reproduction |
| Five per-architecture APKs plus `SHA256SUMS.txt` | release assets for `v1.2` | The APKs are 87 MiB each; the size is the reason two stores said no |

**Never say:** "clinically proven", "improves sleep quality", "scientifically
tuned", "500,000 users", "rated 4.8", "bestseller", "tested on hundreds of
devices". The A4=432 Hz and binaural entries carry explicit statements that no
sleep-stage or healing benefit is established, and public copy must not contradict
the app.

## What success looks like

Not installs. In order: a pull request from someone who is not the maintainer; an
issue with a real logcat excerpt and a layer count; an issue that argues with a
claim in the README; a star count that grows while nothing is being posted. If a
channel produces none of those, it was the wrong channel and one attempt is enough.

## Hard nos

- No bought stars, followers or reviews. No fake or alt accounts, no sockpuppets.
- No posting as a user. If Camden posts about Nightfall, he is the developer and
  the post says so in the first lines.
- No invented numbers — downloads, users, ratings, "hours of audio", benchmarks.
- No editing the unverified list out of the README to look more finished.
- No asking anyone to upvote, boost, star or "check it out and comment".
- No paste of a draft from here into a community that forbids AI-written posts;
  those communities are listed in `docs/launch/copy/dropped.md` for that reason.

## Files

- `docs/launch/izzyondroid-request.md` — policy evidence, the size arithmetic, and
  the form-ready request.
- `docs/launch/copy/` — one file per channel: the rule that was checked, then the
  post. `dropped.md` holds the channels that are closed and why.
- `docs/site/` — the one-page site every post can link to. No JavaScript.
