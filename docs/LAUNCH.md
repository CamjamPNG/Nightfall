# Launch kit

How to get Nightfall in front of people who will actually want it, without lying
to anyone on the way. Read `ROADMAP.md` first: three of the four launch channels
below are blocked on verification, not on marketing copy.

Everything here obeys one rule. **Every number in public copy traces to
`docs/verification/` or to a fact in this repository.** No download counts, no
"users love it", no invented benchmarks, no posting as a satisfied user. The
honest unverified list is the differentiator, so it stays in the copy.

## Who this is for

Someone who cannot sleep without continuous sound and has already been let down
by the alternatives: an app that wants an account before it will play a fan
noise, one that ships a thirty-second rain loop the brain latches onto at 2 a.m.,
one that asks for a subscription, a data connection, or a microphone "to
calibrate" a room. They are mildly technical — they know what an APK is and
sideloading one does not scare them — they care that an app cannot phone home,
and they notice when a mixer labels synthesized rain as a field recording. Nightfall
is built for exactly that person: any number of independent layers, a real mixer
with filters and per-layer schedules, everything computed on the phone, and a
testing record honest enough to include the checks that failed.

The secondary audience is people who want to read or change the DSP: the audio
engine is a Kotlin module with no Android dependency and 18 tests that run in
seconds, so the algorithms are inspectable rather than hidden behind a proprietary
library. Those are the people who open pull requests.

## The demo script

Record it on the phone, not in a mockup. `adb shell screenrecord` or the built-in
recorder with **Media sounds** enabled so the audio is captured, 60–90 seconds,
portrait. Turn on **airplane mode** before you start and leave the status bar
visible for the whole clip — that one detail does more work than any sentence in
this document.

| Time | On screen | Say |
| --- | --- | --- |
| 0:00 | Home screen, Nine-Layer Dream loaded, airplane-mode icon visible | "This is a sleep sound mixer with no internet permission. Airplane mode the whole time." |
| 0:05 | Press play, waveform moves | "Nine layers already running: rain, wind, fire, noise, lo-fi music." |
| 0:12 | Drag the rain layer's gain down, then back up | "Every layer is its own synth or recording with its own gain, filters and pan." |
| 0:22 | Solo the rain layer | "Solo it and the other eight drop out — nothing restarts, nothing clicks." |
| 0:30 | Add "Crickets" from the Library | "The library has colored noise, synthesized weather, and seven field recordings, each looping for at least three minutes before it repeats." |
| 0:40 | Open Studio on the lo-fi layer, change key and tempo | "The music is generated on the device — key, tempo, tuning, texture, reverb, all of it." |
| 0:50 | Back to Home, open the sleep timer, set 20 minutes | "Timers fade out rather than cut. Individual layers can stop earlier than the mix." |
| 1:00 | Lock the screen, show lock-screen controls | "It keeps playing with the screen off, with lock-screen and Bluetooth controls." |
| 1:08 | Back to Settings, show load and underrun counters | "It measures its own render load and tells you when your phone is struggling, instead of hard-limiting the number of layers." |

End card: the repository URL, the MIT licence, and one line — *free, offline, no
account*. Do not put a fake five-star badge or a "join 50,000 sleepers" line on it.

Cut a 20-second vertical version for social: press play, solo, add a sound, lock
screen. Same rule — no invented claims, and no music over the audio you are
demonstrating.

## What is actually different, honestly

Each claim below is checkable in the repository, and each one has a limit stated
next to it. Use this table as the source for post copy.

| Claim | Evidence | What it does not mean |
| --- | --- | --- |
| No internet permission in the app | `app/src/main/AndroidManifest.xml`; `tools/check_release.py` fails the release if `INTERNET`, `RECORD_AUDIO` or the ad ID permission appears | It says nothing about your device's other apps |
| A DSP core with real tests | 18 deterministic JVM tests in `core/src/test/`, free of Android dependencies | Tests prove signal behavior, not that it sounds good to you |
| No layer-count cap, and measured capacity instead | `MixerTest.stopResetsSessionAndThereIsNoNineLayerCap`, the render-budget warning on Home | Your phone has finite CPU; a heavy mix can still underrun |
| Bundled recordings loop for at least 180 seconds | `docs/audio-credits.json` cycle lengths and hashes; enforced by `tools/prepare_audio.py` and `tools/check_release.py` | Long loops are not the same as a well-recorded loop |
| Synthesis is labeled as synthesis | Catalogue descriptions in `Library.kt`; "distant household ambience" is declared unavailable rather than faked | Some sounds are interpretations, not field recordings of that exact place |
| MIT licence, voluntary commercial courtesy | `LICENSE`, `COMMERCIAL_COURTESY.md` | The courtesy request is not a licence condition and nobody owes a payment |
| Two local unsigned release builds are byte-identical | `docs/verification/reproducibility.json` | This is not an independent F-Droid build, and not a signature reproduction |
| F-Droid preparation done | `fdroid/README.md`, scanner logs in `docs/verification/` | F-Droid has **not** accepted the app and a submission has not been made |

**Never say:** "clinically proven", "improves sleep quality", "scientifically
tuned", "500,000 users", "rated 4.8", "bestseller", "tested on hundreds of
devices". The binaural and A4=432 Hz entries in the catalogue carry explicit
statements that no sleep-stage or healing benefit is established, and public copy
must not contradict the app.

## Channels, with the rule that actually bites

Order matters less than eligibility. Read the rule for each one on the day you
post, because subreddit and instance rules change and an out-of-date rule is how
accounts get banned rather than apps get seen.

### GitHub itself — do this first

Topics (`android`, `kotlin`, `jetpack-compose`, `dsp`, `offline-first`,
`sleep`), a short repository description, the release notes, and a handful of
genuine `good first issue` entries. This is where contributors arrive from, and
it costs an hour. The risk is zero and the compounding is real: a repository that
looks maintained gets linked.

### F-Droid — the channel that matches the pitch

**Rule:** F-Droid's Inclusion Policy requires the app to have been actively
developed for at least three months, unless it has proven users, or the
maintainer already has other apps in the repository, or it was built for an event.
Applications must not download executable code or self-update without explicit
opt-in consent. Nightfall as it stands downloads nothing and has no `INTERNET`
permission, so the F-Droid build needs no carve-out and meets that clause as-is.
Independent build, licence and anti-feature review follow.

**Risk:** submitting before the development-age question has an honest answer is a
decline that also makes the project look careless. The other trap is claiming a
difference between channels that does not exist: today the GitHub APKs and an
F-Droid build are the same offline app, updated by installing a new APK by hand.
Say that plainly. If an in-app update channel is ever added, the F-Droid build
must not carry it, and that is a fact to state only once it is true of the tree.

**Prerequisite:** `ROADMAP.md` item 3, plus a working hardware verification pass so
the listing's claims and the build are not contradicted by the testing record.

**Worth trying:** the app's fastlane metadata and recipe already exist, so this is
mostly a merge request. A second independent build environment is more valuable
than the installs.

### Show HN

**Rule:** it must be something people can run or hold, it should be non-trivial,
you must have built it yourself and be present in the thread to discuss it, and
you must not ask anyone to upvote or comment. Landing pages and fundraisers are
off topic; a version bump is not substantive enough.

**Risk:** the audience mostly does not have an Android phone in hand, and "sideload
this APK" is a barrier that kills Show HN posts. Do not post it as an app
announcement. Post it as what a technical audience can actually evaluate: an
offline-first audio engine written from scratch — the source families, the render
budget approach, the testing record that includes the instrumentation failure — with
the APK and APK-less build instructions as the "try it" path. If nobody can try it
without an emulator, it is not ready for this channel yet.

### Lemmy

**Rule:** instance-specific. Technology and FOSS communities on the larger
instances generally tolerate a maintainer posting their own project, but read the
community sidebar and any pinned mod post first, post in the on-topic community,
and disclose that you are the author in the first line. Some instances have
blanket no-self-promotion rules; check before you pick a home community.

**Risk:** low traffic, so a bad post mostly does nothing. The upside is that this
is the audience most likely to install from F-Droid and least likely to be annoyed
by "no account, no analytics". Match the post to the community: a technical
write-up to a programming community, a plain "I made this, here is what it does and
does not do" to an Android community.

### Mastodon

**Rule:** norms rather than a policy — tag accurately (`#FOSS`, `#Android`,
`#F-Droid`), put a content warning on self-promotion where the instance expects it,
and never use follow or boost pods. Instance moderators can and do remove accounts.

**Risk:** reach is small unless the account already has a following, and posting
the same link repeatedly across tags reads as spam. One post, one thread with the
demo clip, then answer replies.

### r/Android

**Rule:** developer self-promotion is restricted. The post must be a self post
with a detailed description, the account must be at least three months old with a
reasonable posting history in r/Android (not just app promotion), and the
developer must be present in the thread. Link dumps get removed. Note also that
r/androidapps does not allow app promotion and redirects developers elsewhere, and
r/androiddev bans promotion outright.

**Risk:** the highest blast radius of any channel here and the strictest gate.
Self-posts are manually filtered, so a post can take hours to appear, and an
account without history will be removed rather than argued with. Do not attempt
this channel without an established account and a genuinely useful write-up.
A technical post about the audio engine in a development-oriented community will
fare better than an app announcement there.

### Other listings worth a PR

`alternativeto.net` and open-source Android "awesome" lists accept submissions,
but each list has its own inclusion rules and many require the app to be on
F-Droid first. Treat these as free, low-reach, zero-risk follow-ups after
F-Droid.

## Checklist, cheapest useful thing first

| Order | Action | Cost | Expected reach |
| --- | --- | --- | --- |
| 1 | Repository topics, description, release notes, 5–8 real `good first issue` entries | 1 hour | Compounding; where contributors come from |
| 2 | Get the app onto a real Pixel 7 and a Bluetooth mask and record the pass in `docs/TESTING.md` | One evening | Unblocks everything below; it is the credibility item |
| 3 | Record the demo clip with airplane mode on | 1 hour | Reused by every channel |
| 4 | Post the technical write-up about the audio engine to Lemmy and Mastodon, with the clip | 1 hour | Small but well-targeted; FOSS-literate |
| 5 | Show HN, framed around the engine and the testing record rather than the app | 1 hour + a day of replies | Largest if it lands; needs the repo to be genuinely readable |
| 6 | Open the F-Droid merge request once the development-age question has an honest answer | A few hours, variable wait | Slow, compounding, best-fit audience |
| 7 | r/Android self post, only with an eligible account and a write-up worth reading | 1 hour + replies | Largest single spike, highest risk of removal |
| 8 | Android "awesome list" and directory PRs after F-Droid is live | 30 min each | Small, permanent |

Rationale for that order: the first three make every later channel work better and
cannot fail. The middle three are ordered by how much they reward a readable
repository. r/Android is last because it punishes a thin account and has the
strictest rules, so it wants the project to be at its most presentable.

## What success looks like

Not installs. The signals worth watching, in order:

1. A pull request from someone who is not the maintainer.
2. An issue with a real logcat excerpt and a layer count — someone used it enough
   to hit a bug.
3. An issue that argues with a claim in the README, because that means the honesty
   is legible.
4. A star count that grows while you are not posting anything.

If a channel produces none of those, it was the wrong channel and one attempt is
enough.

## Hard nos

- No bought stars, followers or reviews. No fake accounts, no alt accounts, no
  sockpuppets in a thread.
- No posting as a user. If you post about Nightfall, you are the developer and you
  say so.
- No invented numbers — downloads, users, ratings, "hours of audio", benchmark
  figures. If a number is not in `docs/verification/` or the repository, it does
  not go in public copy.
- No editing the unverified list out of the README to look more finished. That list
  is the reason a careful reader believes the rest.
- No asking anyone to upvote, boost, star or "check it out and comment" in a
  coordinated way. That is manipulation on every platform listed here.
