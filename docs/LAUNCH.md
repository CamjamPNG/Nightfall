# Launch kit

How to get Nightfall in front of people who will actually want it, without lying
to anyone on the way. Everything here obeys one rule: **every number in public
copy traces to the tree, to a measurement, or to a record in this repository.** The
counts are not a promise to keep them in step by hand: `python3 tools/count_tests.py
--update` rewrites every figure in this file and in `docs/launch/copy/` from the
tree, and `tools/check_release.py` fails the push where one of them disagrees with
the code. The one number it cannot derive is the download size, which is measured
from the published release and dated in each place it appears.

Rule pages were read for this document on 18 November 2026 and are quoted with
their source beside each post. Two of the six channels in the original plan turned
out to be closed to this project, and two more are closed to *AI-drafted copy*;
the reasons are quoted in `docs/launch/copy/dropped.md` rather than quietly
dropped. Reddit itself blocks unauthenticated requests from this build machine
(`403`, "You've been blocked by network security"), so the subreddit rules below
come from Internet Archive captures of those pages, dated in each entry.

## What Camden can post today, and what he cannot post at all yet

**Postable the moment he has five minutes**, because every artifact these need
already exists and is public — no device, no key, no deploy:

- **r/droidappshowcase** — a text post from [`copy/droidappshowcase.md`](copy/droidappshowcase.md):
  the repository link, the eight sample clips inside it, and the strip of rendered
  screens that opens its README. Its rules want a real description, a repository link
  instead of an APK, a 24-hour-old account with 2 karma and a verified email.
- **Lemmy `!androidapps@lemmy.ml`** — [`copy/lemmy.md`](copy/lemmy.md), same day or
  the next. Its rule is a repository link and no direct APK link, which the draft
  keeps.
- **A text post on Mastodon**, from [`copy/mastodon.md`](copy/mastodon.md) — weaker
  without a clip, allowed without one.
- **The GitHub repository itself** — topics, description, `good first issue`
  entries. An hour of settings, no rules to break, and it is where contributors
  arrive from.

**Cannot be posted at all yet**, each for the reason in the table below:

- **Any post that offers an F-Droid install path.** The `fdroid-universal` APK is
  published beside every release, but the self-hosted repository it is meant to
  serve does not exist yet — its address returns `404` — and nothing has been
  submitted to F-Droid's own repository. A post may say the offline build is on the
  Releases page; it may not say Nightfall is on F-Droid.
- **The Fedi video post, or any post whose point is the demo clip.** There is no
  device on these build machines and no `/dev/kvm` for an emulator.
- **Show HN.** The guideline under "why there is no draft" means the text has to be
  Camden's own, so nothing here can be pasted there.
- **r/Android**, until his account is three months old with real history in that
  subreddit.

## What is blocked on what

| Gate | What it blocks | Whose move, and how long |
| --- | --- | --- |
| The one-time F-Droid repository key: `python3 tools/configure_fdroid_repo_key.py --keystore ~/nightfall-fdroid-repo.p12 --upload` | The self-hosted F-Droid index (`https://camjampng.github.io/Nightfall/fdroid/repo` is a `404` until this runs), and therefore every post that offers an F-Droid install path | Camden; one command, one generated key to back up. `docs/FDROID.md` has the procedure |
| The GitHub Pages toggle for this repository | Serves both that index and the one-page site in `docs/site/`; until it is on, no post links either | Camden, in repository settings |
| The demo clip, recorded on his phone | The Fedi video post and the clip-first version of every other post. Mastodon and the text posts do not wait for it | Camden; the script below, 80–90 seconds |
| The IzzyOnDroid inclusion request | Nothing in the launch kit — it is a channel of its own, and it waits on an AI-disclosure decision only Camden can make | Camden; `docs/launch/izzyondroid-request.md` |
| The r/Android account condition | The r/Android post | Camden, or not at all |
| Capy credits for the automated reviewer | Review of this crew's pull requests while they are exhausted. It gates no post, and CI still runs the gate on every push | Nobody in this crew; it comes back on its own |

## Who this is for

Someone who cannot sleep without continuous sound and has already been let down
by the alternatives: an app that wants an account before it will play a fan
noise, one that ships a thirty-second rain loop the brain latches onto at 2 a.m.,
one that asks for a subscription or a data connection. They are mildly technical —
they know what an APK is and sideloading one does not scare them — they care that
an app cannot phone home, and they notice when a mixer labels synthesized rain as
a field recording.

The secondary audience reads or changes the DSP: the audio engine is a Kotlin
module with no Android dependency and 127 tests. Most of them finish in seconds;
`NightRunTest`, `ThemedNightRunTest` and `EndlessNightRunTest` render whole eight-hour nights
through the real render path, and they are where the suite's minutes go.

## State of the product today

Everything in this section was read out of the tree or measured from the published
release on 23 November 2026. Copy that contradicts it is wrong. Each row carries the
command that produced it, because a number without one is a number nobody re-checked.

| Fact | Value | Where |
| --- | --- | --- |
| Latest release | `v2.6.2`, versionCode 45, published 2026-11-23T00:49:07Z | `gh release view v2.6.2 --json publishedAt`; `app/build.gradle.kts` (`versionCode = 45`); `CHANGELOG.md` |
| Builds | two: `github` (updater, four network and install permissions) and `fdroid` (none of them) | `app/build.gradle.kts`, `flavorDimensions += "channel"` |
| Published APK options | six: five **`github`** splits — `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `universal` — and `fdroid-universal`, the offline channel in one universal APK | `gh release view v2.6.2 --json assets` |
| `arm64-v8a` size | 11,832,096 bytes (11.8 MB, 11.3 MiB) | release asset metadata for `v2.6.2` |
| `universal` size | 11,930,920 bytes (11.4 MiB) | release asset metadata for `v2.6.2` |
| `fdroid-universal` size | 11,840,808 bytes (11.3 MiB) | release asset metadata for `v2.6.2` |
| Payload split | 10,089,142 bytes of Opus in seven `.nfop` containers under `assets/audio` (9.6 MiB), 3,138,552 bytes of dex (3.0 MiB, R8-minified), 17,208 bytes of native libraries | `unzip -l Nightfall-2.6.2-arm64-v8a.apk`, summed over `assets/audio/*`, `classes.dex` and `lib/` |
| Audio format | 7 recordings, mono 24 kHz captured, cycles 180.5–240 s with a 1 s overlap, resampled into the 48 kHz mix. Shipped as Opus containers since 1.12 (`OpusFile` parses them, `MediaCodec` decodes each read head, ahead of the render path); 1.11 and earlier carried raw PCM at 77,449,472 bytes | the entry sizes in the published APK; `Sources.kt` (`RecordingSource`, `OVERLAP`); `OpusStream.kt` |
| Catalogue | 106 sounds, 105 playable — one is declared and disabled rather than filled with substitute audio — in ten categories, plus 25 presets, four of which are themed nights | `docs/LOCALIZATION.md` Appendix A (one row per sound), `core/src/main/kotlin/dev/nightfall/core/Library.kt`; the `jshell` command in `docs/launch/README.md` prints it |
| Sample clips | 8 clips, 1,306,777 bytes of Opus, rendered by the app's own engine and playable in a browser before installing anything | `docs/samples/`, `docs/samples/README.md` |
| Permissions, `github` | `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, plus foreground service, media-playback foreground type, notifications and wake lock — read out of the published `v2.6.2` `arm64-v8a` APK | `tools/check_release.py --apk` on the downloaded asset; `app/src/github/AndroidManifest.xml` |
| Permissions, `fdroid` | the same four are `tools:node="remove"`d, and the published `fdroid-universal` APK declares none of them and carries no class from the updater package | `tools/check_release.py --fdroid-apk` on the downloaded asset — both lists empty |
| Mapping files | each release attaches one R8 `mapping.txt` per channel: `Nightfall-<version>-github-mapping.txt` and `-fdroid-mapping.txt`. `v2.6.2` has both (36,161,306 and 34,870,566 bytes) | `gh release view v2.6.2 --json assets` |
| Diagnostics | hidden behind five taps on the version row, off by default, bounded to 200 entries / 64 KB / 30 days, fixed tokens and no free text, nothing transmitted; compiled out of the `fdroid` channel entirely | `app/src/github/java/dev/nightfall/diagnostics/DiagnosticsJournal.kt`; the `fdroid` stub in `app/src/fdroid/java/dev/nightfall/diagnostics/`; `tools/check_release.py`'s dex check |
| Licence | MIT code; recordings CC BY / CC BY-SA / CC0 | `LICENSE`, `AUDIO_LICENSES.md` |
| Not verified | anything on hardware: Pixel 7 pass, Bluetooth, loudness, overnight battery, an update actually installing on a phone — and the five gaps that shipped with the features, the Opus decoder on a phone, the catalogue heard on a device, the widget on a real launcher, the tile in a real shade, diagnostics recording a real crash | `docs/TESTING.md`; `README.md` *Never checked on hardware* |

### Tests, counted from the tree

The row this table used to carry was hand-kept, and it went stale in four documents at
once. It is generated now, by the same command that writes README's copy of it, so the
two cannot disagree with each other or with the code:

<!-- test-counts:begin (generated by `python3 tools/count_tests.py --update`; do not edit by hand) -->
| Suite | Tests | Runs by default | Opt-in | Ignored | Robolectric | Plain JVM | `unittest` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `app/src/test` | 100 | 100 | 0 | 0 | 94 | 6 | 0 |
| `app/src/testGithub` | 93 | 93 | 0 | 0 | 24 | 69 | 0 |
| `core/src/test` | 127 | 126 | 1 | 0 | 0 | 127 | 0 |
| `tools/tests` | 120 | 120 | 0 | 0 | 0 | 0 | 120 |
| **Total** | 440 | 439 | 1 | 0 | 118 | 202 | 120 |
<!-- test-counts:end -->

`python3 tools/count_tests.py --files` prints a row per file, so a count can be traced to
the file that declares it. `--update` with no document rewrites this block and every count
in `docs/launch/`, and `tools/check_release.py` fails the push if anything disagrees.

### The two builds, in the words the copy has to use

`README.md` and `PRIVACY.md` describe this properly and the launch copy must not
drift from it. The `github` build replaces itself in place from this repository's
releases, and that is its only network use: it is a download and never an upload. The
details any privacy sentence has to keep, because a summary that drops them is an
overclaim: it reads one public `release-manifest.json` on its own at most every six
hours, measured from the last successful answer (`CheckThrottle.INTERVAL_MS`); every
request, including a press of *Check for updates*, waits at least fifteen minutes since
the previous attempt, recorded before the request so a failure counts too
(`CheckThrottle.FLOOR_MS`); a newer release is downloaded from the release asset, so
GitHub sees that request too, along with the request IP and the `Nightfall-Updater` user
agent; **`Update automatically` is on by default in this build, so the download and the
install need no tap** — the install is refused while audio is playing or while playback
state is unknown, because a replacement restarts the process and would silence a night
(`UpdateAutomation.mayInstallNow`), and it happens when sound stops or on the next
launch; turning the switch off returns it to a download that waits for a tap. It keeps
all of that state in one private preferences file (`UpdatePrefs`): the pacing timestamps,
the automatic switch, the installer's verdict on the last attempt, which release's row
you dismissed, the release notes from the last manifest, and the verified download
waiting for a window. There is no background worker, alarm or wake-up. The `fdroid`
build contains no updater, no network code and no network permission, and "cannot open a
socket" (`PRIVACY.md`). Playback, generation, presets and timers never touch the network
in either build.

*What's new* is part of that channel now and costs no request: the manifest carries the
recent releases and their notes (`schemaVersion: 2`), generated by
`tools/package_release.py` from this repository's own changelog, and Settings → Updates
shows them from the copy the app already holds.

What that means for public copy: the sentence "no INTERNET permission" is **true of the
F-Droid channel — including the published `fdroid-universal` APK — and false of the five
`github` APKs**, including the `universal` one. The honest version is "the build you
download here can update itself, and that is the only thing it uses the network for; the
F-Droid build has no network permission at all."

### The size, and why it is the number to quote carefully

The download is **11.8 MB on the current release** (`arm64-v8a`, 11,832,096 bytes,
measured 23 November 2026). It is quoted with a date because it moves; the release
assets are the only place that can settle it, and `gh release view <tag> --json assets`
is the command. The two moves behind it matter to copy that explains the number.

The first was R8: minification and resource shrinking landed in 1.5.2
(`isMinifyEnabled = true`, `isShrinkResources = true` in `app/build.gradle.kts`) and took
the dex from 47,367,428 bytes to 2,933,096, which is why the APK fell from 91.2 MB to
79.2 MB rather than staying above 90. Those are uncompressed entry sizes and dex is
compressed inside the APK, so the download fell by the smaller measured 11,971,730 bytes
— quote the payload figure as payload and the APK delta as the download.

The second was the recordings, and it finished in 1.12. The seven field recordings now
ship as Opus containers totalling 10,089,142 bytes, where every release up to 1.11
carried 77,449,472 bytes of uncompressed PCM (`noCompress += "pcm"`, so the mixer could
map the files). `arm64-v8a` measured 79,465,722 bytes in 1.11 and 11,832,096 in 2.6.2, so
the download is 14.9% of what it was. The per-ABI split still saves about 100 KB and is
not what makes the download small. **The size lever was the recordings, not the code, and
the lever has been pulled.**

What that move cost is a decoder in the render path. The mixer parses the containers and
hands packets to the platform's Opus decoder ahead of the render path instead of mapping
raw PCM, and that platform binding has never run on a phone: the test suite decodes every
container with an independent conformant decoder (`OpusAssetDecodeTest`), which proves the
format and says nothing about `MediaCodec`. A platform that refuses Opus is reported at
start-up rather than played as silence. Every row above is the `v2.6.2` record; the
`v1.8.2` figure of 79,190,898 bytes is history and should not appear in current copy.

The one thing on the app's surface that grows the download is another sound, and no sound
has been added since 2.1; the two thousand downloads a launch post may bring in do not
change any of these bytes.

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

The repository's first screen is also the strongest asset a text post has: the strip
of four rendered screens under the README's title (`docs/site/shots/`, full-size PNGs in
`fastlane/metadata/android/en-US/images/phoneScreenshots/`), the *Hear it* section's
eight clips, and the *Never checked on hardware* table. A post that links
`https://github.com/CamjamPNG/Nightfall` and points at those three does more than a
paragraph of adjectives, and it is the link every draft below uses.

### IzzyOnDroid — the channel that fits the product, and now fits the APK

Read `docs/launch/izzyondroid-request.md` for the full evidence trail and the
ready-to-paste request. Short version, in the order the blockers matter:

1. **The AI policy is a hard conflict, not a size problem.** Their App Inclusion
   Policy says they are "strongly opposed to apps which are fully or in part
   created by generative AI tools" and that "Vibe-coded apps will be rejected",
   while their request form *requires* an "Assistance Level" answer with a
   "Dominant – Most code or content was 'AI'-generated" option. Nightfall's code
   was written largely by AI coding agents under Camden's direction. This is
   Camden's call to make, not a copy problem to be worked around, and the form
   must not be filed with "None". Both versions of the request are drafted:
   `docs/launch/izzyondroid-request.md` defaults to the honest disclosure.
2. **Size was the second, and it is settled.** 30 MB rule of thumb, and the
   contributing page states plainly: "We cannot currently accept APK files over 30 MB."
   When this kit was written the smallest published APK was 79,186,790 bytes (75.5 MiB,
   2.6× that ceiling) and the bulk of it was 77,449,472 bytes of raw PCM, so per-ABI
   splits could not help. That is over: every APK in `v2.6.2` is between 11,828,004 and
   11,930,920 bytes, because the seven recordings ship as 10,089,142 bytes of Opus
   instead of PCM. The smallest is now 0.39× the ceiling rather than 2.6×. What the
   smaller number costs is stated next to it — the platform Opus decoder has never run
   on a phone.
3. **They ship our APK, not their build.** Their FAQ: "App Source — APK provided
   by the app's dev team". Reproducible-build verification is optional trust on
   top, so our `apksigcopier` gap does not block inclusion. This used to be blocked
   by *which* APK: pointing them at a `github`-channel file would ship a
   self-updater. That is resolved — every release since 1.5 publishes
   `Nightfall-<version>-fdroid-universal.apk`, the offline channel with no updater,
   no network code and no network permission, and it is deliberately kept out of
   `release-manifest.json` so the in-app updater never offers it. The request should
   name `Nightfall-2.6.2-fdroid-universal.apk`.
4. **Submission is a Codeberg issue**, using the `App Inclusion Request` form in
   `IzzyOnDroid/repodata`. That needs a Codeberg account, which only Camden can
   create.

So IzzyOnDroid has moved from three blockers to one, and the last is Camden's call rather
than a drafting problem: since 19 November 2026 the size ceiling is met by the Opus
containers, leaving the AI-use disclosure.

### F-Droid — one APK published, no repository serving yet

Three different things are called "F-Droid here", and copy has to keep them apart.

- **The `fdroid`-channel APK is published.** Every release carries
  `Nightfall-<version>-fdroid-universal.apk`, the offline build in one universal APK,
  signed with the pinned certificate. This is a fact a post may state, pointing at the
  Releases page.
- **The self-hosted repository is not serving.** `https://camjampng.github.io/Nightfall/fdroid/repo`
  returns `404` today. The index builder, its refusal paths and its fingerprint invariant
  exist and were exercised locally against published APKs, but the one-time setup has not
  been run: `fdroid/repo/fingerprint.txt` is empty, the repository key is not installed,
  and GitHub Pages is not enabled for this repository. That is Camden's two-step gate at
  the top of this file. Until it is done, **no post may offer an F-Droid install path** —
  not the address, not the client, not "available on F-Droid".
- **F-Droid's own repository has nothing from us.** Nothing in this document submits to
  F-Droid or claims it has been submitted. Their Inclusion Policy asks for three months of
  active development, which puts the earliest ordinary submission in February 2027 — that
  is a date arithmetic, not a submission.

`docs/FDROID.md` is the full state, including the failure modes and the key handling.

## The demo script

Record it on the phone, not in a mockup. `adb shell screenrecord` or the built-in
recorder with **Media sounds** enabled so the audio is captured, 80–90 seconds,
portrait. Turn on **airplane mode** before you start and leave the status bar
visible for the whole clip — that one detail does more work than any sentence here.

| Time | On screen | Say |
| --- | --- | --- |
| 0:00 | Home, Nine-Layer Dream loaded, airplane-mode icon visible | "This is a sleep sound mixer with no internet permission. Airplane mode the whole time." |
| 0:05 | Press play, waveform moves | "Nine layers already running: rain, wind, fire, noise, lo-fi music." |
| 0:12 | Drag the rain layer's gain down, then back up | "Every layer is its own synth or recording with its own gain, filters and pan." |
| 0:20 | Solo the rain layer | "Solo it and the other eight drop out — nothing restarts, nothing clicks." |
| 0:28 | Add "Crickets" from the Library | "The library has colored noise, synthesized weather, and seven field recordings, each looping for at least three minutes before it repeats." |
| 0:36 | Type "heavy rain on a metal roof with a fire" into the phrase box | "Type a sentence and it builds the mix here on the phone — a local parser, no model, no server, no connection." |
| 0:48 | Open Studio on the lo-fi layer, change key and tempo | "The music is generated on the device — key, tempo, tuning, texture, reverb." |
| 0:56 | Pull down the shade, tap the Quick Settings tile twice | "The tile plays and pauses without unlocking the phone, and it follows what is actually playing." |
| 1:06 | Back to Home, sleep timer, 20 minutes | "Timers fade out rather than cut. Individual layers can stop earlier." |
| 1:14 | Lock the screen, show lock-screen controls | "It keeps playing with the screen off, with lock-screen and Bluetooth controls." |
| 1:22 | Settings, load and underrun counters | "It measures its own render load and says when the phone is struggling, instead of capping layers." |

End card: the repository URL, the MIT licence, and one line — *free, offline, no
account*. No star badge, no invented user count. Cut a 20-second vertical version
for Fedi: press play, solo, type a phrase, lock screen.

This clip has not been recorded: there is no device on these build machines and no
`/dev/kvm` for an emulator, so `docs/TESTING.md` still records the Pixel 7 pass as
pending. The Mastodon post is the only channel that shows a clip, and it reads as text
until this exists. Nothing else in the kit waits for it.

Two things the clip cannot show while airplane mode is on: the update row, and the
release notes behind *What's new*, because both appear only after a check answers.
They stay unproven on camera until someone records a second, shorter clip with a
connection — the README lists an actual install on a phone under what has never been
checked, and that gap is worth keeping visible rather than papering over with the
airplane-mode clip.

## What is actually different, honestly

Each claim is checkable in the repository and each has its limit stated next to it.

| Claim | Evidence | What it does not mean |
| --- | --- | --- |
| Playback needs no network, in either build | `PRIVACY.md`; the `fdroid` channel removes `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` and `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (`app/src/fdroid/AndroidManifest.xml`), and `tools/check_release.py` fails a release if either channel's permissions drift | The GitHub APK *does* have `INTERNET`, for its own update check; the claim is about playback and about the F-Droid build, not about every download |
| The update channel is bounded, and it never interrupts a night | `README.md` "Updates"; `PRIVACY.md`: one manifest read at most every six hours after a successful check and never more than once every fifteen minutes, no upload; an install is refused while audio is playing or while playback state is unknown (`UpdateAutomation.mayInstallNow`) and waits for the stop | Installing needs no tap by default. The automatic check can be switched off, but *Check for updates* stays, so a check is always something the app can be made to do; it is still a network call from an app whose selling point is not making them, and no automatic update has been watched on a phone |
| A DSP core with real tests | 127 JVM tests in `core/src/test/` (126 execute; the calibration generator is opt-in), free of Android dependencies, three of which render eight-hour nights | Tests prove signal behavior, not that it sounds good to you |
| A sentence becomes a mix, on the device | `LocalPhraseInterpreter`; `Samples.kt` renders each documented phrase and refuses one that would not parse, and the eight clips in `docs/samples/` include four built from phrases | It is a small local parser over the catalogue, not a language model: a phrase that matches nothing says so rather than inventing a mix, and nobody has heard the result on a phone |
| No layer-count cap, and measured capacity instead | `MixerTest` no-cap case; the render-budget warning on Home | Your phone has finite CPU; a heavy mix can still underrun |
| Endless night and themed nights | Eight-hour renders in `core`; endless night's worst hour-to-hour level difference is 0.22 LU, a themed night lands within 0.01 dB of the same mix with its plan off, and the closest two phases inside a theme are 0.6 dB apart by band profile | Those are level and spectrum measurements. No ear has heard a whole night of either on a phone |
| Bundled recordings loop for at least 180 seconds | `docs/audio-credits.json` cycle lengths; enforced by `tools/prepare_audio.py` and `tools/check_release.py` | Long loops are not the same as a well-recorded loop |
| Synthesis is labeled as synthesis | Catalogue descriptions; "distant household ambience" is declared unavailable rather than faked | Some sounds are interpretations, not recordings of that place |
| MIT licence, voluntary commercial courtesy | `LICENSE`, `COMMERCIAL_COURTESY.md` | The courtesy request is not a licence condition |
| Two local release builds are byte-identical | `docs/verification/reproducibility.json`, tag `v1.0.1.2` | Not an independent F-Droid build, and not a signature reproduction |
| Six APKs plus two R8 mappings, `SHA256SUMS.txt`, `release-manifest.json` and the release certificate | release assets for `v2.6.2` | Five of them are the `github` channel; only the sixth, `fdroid-universal`, has no network permission. All six are between 11.8 and 11.9 MB, and 10,089,142 bytes of each is the Opus recording set |

**Never say:** "clinically proven", "improves sleep quality", "scientifically
tuned", "500,000 users", "rated 4.8", "bestseller", "tested on hundreds of
devices", "available on F-Droid". The A4=432 Hz and binaural entries carry explicit
statements that no sleep-stage or healing benefit is established, and public copy must
not contradict the app.

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
- No claim that a device has verified anything, or that any ear has heard it.
- No editing the unverified list out of the README to look more finished.
- No asking anyone to upvote, boost, star or "check it out and comment".
- No paste of a draft from here into a community that forbids AI-written posts;
  those communities are listed in `docs/launch/copy/dropped.md` for that reason.
- No post that offers an F-Droid install path while the repository is a `404`.

## Files

- `docs/launch/README.md` — the short version for Camden: what to post, where the
  copy is, and what is still blocking it.
- `docs/launch/izzyondroid-request.md` — policy evidence, the size arithmetic, and
  the form-ready request.
- `docs/launch/copy/` — one file per channel: the rule that was checked, then the
  post. `dropped.md` holds the channels that are closed and why.
- `docs/samples/` — the eight clips a post can link, each one a render of the app's own
  engine with its measurements in `docs/samples/README.md`.
- `docs/site/` — the one-page site every post can link *once Pages is on*. No
  JavaScript, no external requests, and the six screenshots it shows in `docs/site/shots/`
  (540 px wide copies of the 1078×2399 renders in
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`), beside a seventh of the
  first-run card that the page itself does not show. Until the toggle above is flipped the
  address is a `404`, so the drafts link GitHub instead.
- `tools/count_tests.py` — the one place the test, catalogue and clip counts come from.
  `--update` rewrites them here and in the drafts; `--check` and `tools/check_release.py`
  refuse a push where one has drifted.
