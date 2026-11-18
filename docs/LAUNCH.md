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
module with no Android dependency and 68 JVM tests. Most of them finish in seconds;
one renders a whole eight-hour night through the real render path and takes a couple
of minutes.

## State of the product today

Everything in this section was read out of the tree or measured from the published
release on 18 November 2026. Copy that contradicts it is wrong. Each row carries the
command that produced it, because a number without one is a number nobody re-checked.

| Fact | Value | Where |
| --- | --- | --- |
| Latest release | `v1.8.2`, versionCode 22, published 2026-11-18T11:39:18Z | `gh release list --limit 3`; `app/build.gradle.kts` (`versionCode = 22`); `CHANGELOG.md` |
| Builds | two: `github` (updater, four network and install permissions) and `fdroid` (none of them) | `app/build.gradle.kts`, `flavorDimensions += "channel"` |
| Published APK options | six: five **`github`** splits — `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `universal` — and `fdroid-universal`, the offline channel in one universal APK | `gh release view v1.8.2 --json assets` |
| `arm64-v8a` size | 79,190,898 bytes (75.5 MiB) | release asset metadata for `v1.8.2` |
| `universal` size | 79,289,722 bytes (75.6 MiB) | release asset metadata for `v1.8.2` |
| `fdroid-universal` size | 79,232,378 bytes (75.6 MiB) | release asset metadata for `v1.8.2` |
| Payload split | 77,449,472 bytes of PCM under `assets/audio` (73.9 MiB; 77,453,797 with the seven `.license` sidecars), 2,933,096 bytes of dex (2.8 MiB, R8-minified), 17,208 bytes of native libraries | `unzip -l Nightfall-1.8.2-arm64-v8a.apk` summed per entry type |
| Audio format | 7 recordings, 16-bit mono, 24 kHz, cycles 180.5–240 s with a 1 s overlap, resampled into the 48 kHz mix | PCM entry sizes in the published APK; `Sources.kt` (`RecordingSource`, `OVERLAP`) |
| Permissions, `github` | `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, plus foreground service, media-playback foreground type, notifications and wake lock — read out of the published `v1.8.2` `arm64-v8a` APK | `strings -el` on the APK's `AndroidManifest.xml`; `app/src/github/AndroidManifest.xml` |
| Permissions, `fdroid` | the same four are `tools:node="remove"`d, and the published `fdroid-universal` APK declares none of them and carries no class from the updater package | `tools/check_release.py`'s `fdroid_violation()` and `updater_symbols()` on the downloaded asset — both empty |
| Mapping files | the release workflow now attaches one R8 `mapping.txt` per channel; `v1.8.2` predates that step, so its assets have none and the next release will | `.github/workflows/release.yml` upload line; `git show v1.8.2:.github/workflows/release.yml` has no mapping step |
| Tests | 68 `@Test` in `core/src/test` (67 execute; the level-calibration generator is skipped unless `NIGHTFALL_CALIBRATE=1`), 10 playback scenarios that run under both channels, 54 updater tests, 53 release-tooling tests | `grep -A2 '@Test' core/src/test`; `app/src/test`; `app/src/testGithub`; `python3 -m unittest discover -s tools/tests` |
| Licence | MIT code; recordings CC BY / CC BY-SA / CC0 | `LICENSE`, `AUDIO_LICENSES.md` |
| Not verified | anything on hardware: Pixel 7 pass, Bluetooth, loudness, overnight battery, and an update actually installing on a phone | `docs/TESTING.md` |

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

The size is still the number that decides two of the channels below, but it has moved.
The APK is 79.2 MB, not 91.2 MB, because R8 minification and resource shrinking landed
in 1.5.2 (`isMinifyEnabled = true`, `isShrinkResources = true` in
`app/build.gradle.kts`) and took the dex from 47,367,428 bytes to 2,933,096. What is left
is almost entirely the sound library: 77,449,472 bytes of uncompressed PCM
(`noCompress += "pcm"`, so the mixer can map the files), which is 97.8% of the download.
Native libraries are not the cause and never were: all five per-ABI APKs are within
103 KB of each other. **The one remaining size lever is the recordings, not the code:**
an Opus decode path exists behind `-PnightfallOpus=true` and ships off by default (1.8),
so no published APK has been encoded smaller, and any copy that implies the download is
at its floor is still wrong.

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
2. **Size.** 30 MB rule of thumb, and the contributing page states plainly: "We
   cannot currently accept APK files over 30 MB." The smallest published APK is
   79,186,790 bytes — 75.5 MiB, which is 2.6× that ceiling. Per-ABI splits are their
   usual remedy and are already in place, so they cannot help: native libraries are
   17 KB and the bulk is 77,449,472 bytes of raw recordings. R8 has since landed
   (1.5.2) and took the dex from 47.26 MB to 2.93 MB, which is why the APK is 79 MB
   instead of 91 MB — but the recordings dominate it, and the Opus decode path that
   would shrink them ships behind a build flag that is off by default (1.8), so
   **no published APK is under 75 MiB**. See `docs/launch/izzyondroid-request.md` for
   the measurements and what is left.
3. **They ship our APK, not their build.** Their FAQ: "App Source — APK provided
   by the app's dev team". Reproducible-build verification is optional trust on
   top, so our `apksigcopier` gap does not block inclusion. This used to be blocked
   by *which* APK: pointing them at a `github`-channel file would ship a
   self-updater. That is resolved — every release since 1.5 publishes
   `Nightfall-<version>-fdroid-universal.apk`, the offline channel with no updater,
   no network code and no network permission, and it is deliberately kept out of
   `release-manifest.json` so the in-app updater never offers it. The request should
   name `Nightfall-1.8.2-fdroid-universal.apk`.
4. **Submission is a Codeberg issue**, using the `App Inclusion Request` form in
   `IzzyOnDroid/repodata`. That needs a Codeberg account, which only Camden can
   create.

So IzzyOnDroid has moved from three blockers to two — the AI policy and the 30 MB
ceiling — both of which are Camden's call rather than a drafting problem.

### F-Droid

The F-Droid listing is the repository's own work (`fdroid/`, `docs/FDROID.md`).
Nothing in this document submits to F-Droid or claims it has been submitted. Two
things about it are current as of `v1.8.2`: the `fdroid`-channel APK *is* published,
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
| A DSP core with real tests | 68 JVM tests in `core/src/test/` (67 execute; the calibration generator is opt-in), free of Android dependencies, one of which renders an eight-hour night | Tests prove signal behavior, not that it sounds good to you |
| No layer-count cap, and measured capacity instead | `MixerTest` no-cap case; the render-budget warning on Home | Your phone has finite CPU; a heavy mix can still underrun |
| Bundled recordings loop for at least 180 seconds | `docs/audio-credits.json` cycle lengths; enforced by `tools/prepare_audio.py` and `tools/check_release.py` | Long loops are not the same as a well-recorded loop |
| Synthesis is labeled as synthesis | Catalogue descriptions; "distant household ambience" is declared unavailable rather than faked | Some sounds are interpretations, not recordings of that place |
| MIT licence, voluntary commercial courtesy | `LICENSE`, `COMMERCIAL_COURTESY.md` | The courtesy request is not a licence condition |
| Two local release builds are byte-identical | `docs/verification/reproducibility.json`, tag `v1.0.1.2` | Not an independent F-Droid build, and not a signature reproduction |
| Six APKs plus `SHA256SUMS.txt` and the release certificate | release assets for `v1.8.2` | Five of them are the `github` channel; only the sixth, `fdroid-universal`, has no network permission. All of them are 79.2 MB, and 77.4 MB of each is recordings — the code is down to 2.9 MB, so the download is as small as its PCM |

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
  requests, and the six screenshots in `docs/site/shots/` (540 px wide copies of the
  1078×2399 renders in `fastlane/metadata/android/en-US/images/phoneScreenshots/`).
