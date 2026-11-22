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
module with no Android dependency and 119 JVM tests. Most of them finish in seconds;
`NightRunTest`, `ThemedNightRunTest` and `EndlessNightRunTest` render whole eight-hour nights
through the real render path, and they are where the suite's minutes go.

## State of the product today

Everything in this section was read out of the tree or measured from the published
release on 19 November 2026. Copy that contradicts it is wrong. Each row carries the
command that produced it, because a number without one is a number nobody re-checked.

| Fact | Value | Where |
| --- | --- | --- |
| Latest release | `v2.0`, versionCode 33, published 2026-11-19T14:56:02Z | `gh release view v2.0 --json publishedAt`; `app/build.gradle.kts` (`versionCode = 33`); `CHANGELOG.md` |
| Builds | two: `github` (updater, four network and install permissions) and `fdroid` (none of them) | `app/build.gradle.kts`, `flavorDimensions += "channel"` |
| Published APK options | six: five **`github`** splits — `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `universal` — and `fdroid-universal`, the offline channel in one universal APK | `gh release view v2.0 --json assets` |
| `arm64-v8a` size | 11,737,776 bytes (11.2 MiB) | release asset metadata for `v2.0` |
| `universal` size | 11,836,600 bytes (11.3 MiB) | release asset metadata for `v2.0` |
| `fdroid-universal` size | 11,754,680 bytes (11.2 MiB) | release asset metadata for `v2.0` |
| Payload split | 10,089,142 bytes of Opus in seven `.nfop` containers under `assets/audio` (9.6 MiB), 3,041,456 bytes of dex (2.9 MiB, R8-minified) | `unzip -l Nightfall-2.0-arm64-v8a.apk`, summed over `assets/audio/*` and `classes.dex` |
| Audio format | 7 recordings, mono 24 kHz captured, cycles 180.5–240 s with a 1 s overlap, resampled into the 48 kHz mix. Shipped as Opus containers since 1.12 (`OpusFile` parses them, `MediaCodec` decodes each read head, ahead of the render path); 1.11 and earlier carried raw PCM at 77,449,472 bytes | the entry sizes in the published APK; `Sources.kt` (`RecordingSource`, `OVERLAP`); `OpusStream.kt` |
| Permissions, `github` | `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, plus foreground service, media-playback foreground type, notifications and wake lock — read out of the published `v2.0` `arm64-v8a` APK | `tools/check_release.py --apk` on the downloaded asset; `app/src/github/AndroidManifest.xml` |
| Permissions, `fdroid` | the same four are `tools:node="remove"`d, and the published `fdroid-universal` APK declares none of them and carries no class from the updater package | `tools/check_release.py --fdroid-apk` on the downloaded asset — both lists empty |
| Mapping files | each release attaches one R8 `mapping.txt` per channel: `Nightfall-<version>-github-mapping.txt` and `-fdroid-mapping.txt`. `v2.0` has both, which were its largest assets (35,019,412 and 33,746,541 bytes) | `gh release view v2.0 --json assets` |
| Diagnostics | hidden behind five taps on the version row, off by default, bounded to 200 entries / 64 KB / 30 days, fixed tokens and no free text, nothing transmitted; compiled out of the `fdroid` channel entirely | `app/src/github/java/dev/nightfall/diagnostics/DiagnosticsJournal.kt`; the `fdroid` stub in `app/src/fdroid/java/dev/nightfall/diagnostics/`; `tools/check_release.py`'s dex check |
| Tests | 119 `@Test` in `core/src/test` (118 execute; the level-calibration generator is skipped unless `NIGHTFALL_CALIBRATE=1`), 71 in `app/src/test` (68 Robolectric, which execute under both channels, and 3 plain JVM), 92 in `app/src/testGithub` (51 updater, 18 diagnostics, 23 Robolectric), 85 release-tooling tests — the same table as `README.md`, which `tools/check_release.py` holds to the tree on every push | `python3 tools/count_tests.py`; `python3 tools/count_tests.py --files` for a row per file |
| Licence | MIT code; recordings CC BY / CC BY-SA / CC0 | `LICENSE`, `AUDIO_LICENSES.md` |
| Not verified | anything on hardware: Pixel 7 pass, Bluetooth, loudness, overnight battery, an update actually installing on a phone — and the four 2.0 gaps, the Opus decoder on a phone, the catalogue heard on a device, the widget on a real launcher, diagnostics recording a real crash | `docs/TESTING.md`; `README.md` *Never checked on hardware* |

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

The size used to be the number that decided two of the channels below. It has moved
twice, and both moves matter to copy that quotes a download size.

The first was R8: minification and resource shrinking landed in 1.5.2
(`isMinifyEnabled = true`, `isShrinkResources = true` in `app/build.gradle.kts`) and took
the dex from 47,367,428 bytes to 2,933,096, which is why the APK fell from 91.2 MB to
79.2 MB rather than staying above 90. Those are uncompressed entry sizes and dex is
compressed inside the APK, so the download fell by the smaller measured 11,971,730 bytes
— quote the payload figure as payload and the APK delta as the download.

The second was the recordings, and it finished in 1.12. The seven field recordings now
ship as Opus containers totalling 10,089,142 bytes, where every release up to 1.11
carried 77,449,472 bytes of uncompressed PCM (`noCompress += "pcm"`, so the mixer could
map the files). `arm64-v8a` measured 79,465,722 bytes in 1.11 and 11,737,776 in 2.0, so
the download is 14.7% of what it was; 2.0 adds no code and publishes at 1.12's size. The
per-ABI split still saves about 100 KB and is not what makes the download small. **The
size lever was the recordings, not the code, and the lever has been pulled.**

What that move cost is a decoder in the render path. The mixer parses the containers and
hands packets to the platform's Opus decoder ahead of the render path instead of mapping
raw PCM, and that platform binding has never run on a phone: the test suite decodes every
container with an independent conformant decoder (`OpusAssetDecodeTest`), which proves the
format and says nothing about `MediaCodec`. A platform that refuses Opus is reported at
start-up rather than played as silence. Every row above is the `v2.0` record; the `v1.8.2`
figure of 79,190,898 bytes is history and should not appear in current copy.

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
   must not be filed with "None". Both versions of the request are drafted:
   `docs/launch/izzyondroid-request.md` defaults to the honest disclosure.
2. **Size was the second, and it is settled.** 30 MB rule of thumb, and the
   contributing page states plainly: "We cannot currently accept APK files over 30 MB."
   When this kit was written the smallest published APK was 79,186,790 bytes (75.5 MiB,
   2.6× that ceiling) and the bulk of it was 77,449,472 bytes of raw PCM, so per-ABI
   splits could not help. That is over: every APK in `v2.0` is between 11,737,764 and
   11,836,600 bytes, because the seven recordings ship as 10,089,142 bytes of Opus
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
   name `Nightfall-2.0-fdroid-universal.apk`.
4. **Submission is a Codeberg issue**, using the `App Inclusion Request` form in
   `IzzyOnDroid/repodata`. That needs a Codeberg account, which only Camden can
   create.

So IzzyOnDroid has moved from three blockers to one, and the last is Camden's call rather
than a drafting problem: from 19 November 2026 the size ceiling is met by the Opus
containers, leaving the AI-use disclosure.

### F-Droid

The F-Droid listing is the repository's own work (`fdroid/`, `docs/FDROID.md`).
Nothing in this document submits to F-Droid or claims it has been submitted. Two
things about it are current as of `v2.0`: the `fdroid`-channel APK *is* published,
as `Nightfall-<version>-fdroid-universal.apk` on every release, so the offline build
is downloadable without building it; and the self-hosted repository the project
intends to serve (`https://camjampng.github.io/Nightfall/fdroid/repo`) returns `404`
today, because the one-time key setup and Pages enablement have not been run. F-Droid's
own Inclusion Policy asks for three months of active development, which puts the
earliest ordinary submission in February 2027 — that is a date arithmetic, not a
submission.

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
| A DSP core with real tests | 119 JVM tests in `core/src/test/` (118 execute; the calibration generator is opt-in), free of Android dependencies, three of which render eight-hour nights | Tests prove signal behavior, not that it sounds good to you |
| No layer-count cap, and measured capacity instead | `MixerTest` no-cap case; the render-budget warning on Home | Your phone has finite CPU; a heavy mix can still underrun |
| Bundled recordings loop for at least 180 seconds | `docs/audio-credits.json` cycle lengths; enforced by `tools/prepare_audio.py` and `tools/check_release.py` | Long loops are not the same as a well-recorded loop |
| Synthesis is labeled as synthesis | Catalogue descriptions; "distant household ambience" is declared unavailable rather than faked | Some sounds are interpretations, not recordings of that place |
| MIT licence, voluntary commercial courtesy | `LICENSE`, `COMMERCIAL_COURTESY.md` | The courtesy request is not a licence condition |
| Two local release builds are byte-identical | `docs/verification/reproducibility.json`, tag `v1.0.1.2` | Not an independent F-Droid build, and not a signature reproduction |
| Six APKs plus two R8 mappings, `SHA256SUMS.txt`, `release-manifest.json` and the release certificate | release assets for `v2.0` | Five of them are the `github` channel; only the sixth, `fdroid-universal`, has no network permission. All six are between 11.7 and 11.8 MB, and 10,089,142 bytes of each is the Opus recording set |

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

- `docs/launch/README.md` — the short version for Camden: what to post, where the
  copy is, and what is still blocking it.
- `docs/launch/izzyondroid-request.md` — policy evidence, the size arithmetic, and
  the form-ready request.
- `docs/launch/copy/` — one file per channel: the rule that was checked, then the
  post. `dropped.md` holds the channels that are closed and why.
- `docs/site/` — the one-page site every post can link to. No JavaScript, no external
  requests, and the six screenshots it shows in `docs/site/shots/` (540 px wide copies of
  the 1078×2399 renders in `fastlane/metadata/android/en-US/images/phoneScreenshots/`),
  beside a seventh of the first-run card that the page itself does not show.
