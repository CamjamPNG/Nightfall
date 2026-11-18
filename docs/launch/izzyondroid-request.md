# IzzyOnDroid inclusion request — packaged, with the gates named

Read on 18 November 2026 from `izzyondroid.org` and from the `IzzyOnDroid/repodata`
repository on Codeberg. Every quotation below is copied from those pages, with the
section it came from.

This document contains the request text ready to paste, but it does not claim the
request is submittable today. Two gates stand in front of it and one of them is not
about size: see **Gates**.

## What IzzyOnDroid actually does

From their FAQ's comparison table:

> | App Source | compiled from sources provided by the app's dev team *(F-Droid)* | **APK provided by the app's dev team** *(IzzyOnDroid)* |
> | Signature | signed by F-Droid or, when reproducible builds were established, by the app's dev team | **signed by the app's dev team** |

So they take the APK Camden signs and serve it. There is no buildserver review, and
no requirement that they can rebuild it. Reproducible builds are extra trust they
may add later, not an entry condition. The consequence for Nightfall: the
`apksigcopier` failure recorded in `docs/FDROID.md` does not block inclusion, it
only means no reproducibility badge.

From their YAML metadata page, the fields that matter for a GitHub-hosted app:
`Method: github-release`, `AutoUpdateMode: Version v%v`, `UpdateCheckMode: Tags`,
and `AllowedAPKSigningKeys`, which is where the pinned certificate hash goes
(`docs/release-signing-certificate.sha256` →
`0ef5904042e684e4158711036c04c6fb2527cb48c023d5173fd8ded18db032de`).

## Gates

### Gate 1 — the AI policy, which is a conflict rather than a checklist item

From the App Inclusion Policy, "AI policy" section:

> We are strongly opposed to apps which are fully or in part created by generative
> AI tools.
> …
> Specifically, this means: **Vibe-coded apps will be rejected.**
> Readme, Changelogs and similar documentation files are allowed to include
> LLM-generated texts, but the code itself should be free of it.
> …
> Any lack of transparency discovered by us, can lead to the project being degraded
> to rejected state.

The request form makes disclosure mandatory. Their `app-inclusion-request.yaml`
(the issue template, read from `IzzyOnDroid/repodata` on 18 November 2026) has a
required dropdown:

> **Assistance Level** — How extensively were the "AI" tools used?
> - None – No "AI" tools were used
> - Minimal – Occasional suggestions or lookups
> - Moderate – Used for specific tasks or modules
> - **Substantial – Used throughout development**
> - **Dominant – Most code or content was "AI"-generated**

followed by an optional `"AI" Tool(s)` field, a `What did the tools help with, and
how?` field, and an `AI Accountability` pair of checkboxes ("reviewed and edited all
'AI'-generated outputs", "ran manual tests and manually verified all changes").

Nightfall's Kotlin, Gradle, workflow and documentation work in this tree was
written largely by AI coding agents working from Camden's specifications and
reviews. On that form the honest answer is **Substantial or Dominant**, which is
precisely what the policy says will be rejected.

This is Camden's decision and nobody else's: file it with a truthful Assistance
Level and let the maintainers decide, or do not file it. Writing
"None" is a lie about the artifact and is exactly the "lack of transparency" the
policy says gets a project degraded. Nothing in this repository is set up to file
that answer, and no request has been filed.

One honest counterweight worth having ready, because it is true and checkable:
`docs/TESTING.md` records what was and was not executed, `core/src/test` holds 42
deterministic tests, and the README carries an explicit unverified list. If the
request is filed, that record is the argument that a human is accountable for the
output — not a claim that no tool was used.

### Gate 2 — size

App Inclusion Policy, "APK files", item 3:

> APK size: Running on private resources (insufficient funding), IzzyOnDroid usually
> reserves up to 30 megabytes per app (exceptions are being made for some larger
> apps, so this is considered as rule-of-thumb). That's at the same time the upper
> size limit for single `.apk` files.

Their contributing page, "App tests", is blunter:

> check if the developer attached an `.apk` file to the most recent release … and
> check if it's less than 30 MB. **We cannot currently accept APK files over 30 MB.**

And the FAQ makes clear that per-ABI builds are their standard remedy, with
exceptions available but rationed: "And in rare cases, we indeed do make
exceptions — though those must be well reasoned." A real refusal on record shows
the remedy failing when the payload is not native code: on
[repodata#495](https://codeberg.org/IzzyOnDroid/repodata/issues/495) (August 2026)
a maintainer wrote "the APK size even of the per-ABI builds is already twice our
per-app size limit."

#### What Nightfall publishes today, measured

`v1.2` ships five APKs, and the per-ABI split is already done:

| Asset | Bytes | MiB |
| --- | --- | --- |
| `Nightfall-1.2-arm64-v8a.apk` | 91,129,860 | 86.9 |
| `Nightfall-1.2-armeabi-v7a.apk` | 91,129,864 | 86.9 |
| `Nightfall-1.2-x86.apk` | 91,129,848 | 86.9 |
| `Nightfall-1.2-x86_64.apk` | 91,129,854 | 86.9 |
| `Nightfall-1.2-universal.apk` | 91,228,684 | 87.0 |

Splitting by architecture saves **98,824 bytes**, because it is not the native
libraries that are large: `lib/arm64-v8a` is 17,208 bytes. Unzipping the
`arm64-v8a` APK on 18 November 2026:

```
77453797  assets/audio      (74 MB of 16-bit mono 24 kHz PCM, stored uncompressed)
31741184  classes.dex
 9139704  classes2.dex
 6382812  classes3.dex
```

The format of those recordings is in the code, not inferred:
`class RecordingSource` is documented "Mono 24 kHz recording. Every bundled cycle
is longer than three minutes" (`core/src/main/kotlin/dev/nightfall/core/Sources.kt:299`),
`OVERLAP` is 24,000 frames, and the mixer resamples them into its 48 kHz stereo
mix. So the seven files are ~240 seconds each, not ~60.

Two payloads, two different fixes. Both are outside this thread's file scope; the
numbers are here so they can be scoped by whoever owns `app/**` and the build files.

**Dex, 47.26 MB.** `app/build.gradle.kts` sets `buildTypes { release { vcsInfo … } }`
and nothing else — no `minifyEnabled`, no `shrinkResources`. Turning R8 on for
release is a small, well-understood change, and this repository already runs
`check_release.py` plus a full test suite against the result. **Not measured
here**: nobody has built a minified release, so the resulting size is unknown and
must not be quoted until it is.

**Audio, 77.45 MB, measured today.** I encoded three of the seven recordings with
ffmpeg to size the options. Those three are 19.4% of the PCM payload, and the
arithmetic below scales them to the set — that scaling is an estimate and is
labelled as one:

| Encoding of the 3 sampled files | Bytes | Share of that PCM | Scaled to all 7 |
| --- | --- | --- | --- |
| PCM 16-bit mono 24 kHz (shipped) | 31,850,496 | 100% | 77.4 MB |
| FLAC, lossless | 19,722,247 | 62% | ≈ 48 MB |
| Opus 48 kbit/s mono | 3,957,851 | 12.4% | ≈ 9.6 MB |

FLAC alone is not enough and neither is R8 alone. Opus plus a minified dex is the
only combination that plausibly lands under 30 MB, and Opus would put a decoder in
the path that currently memory-maps raw PCM — a change to the render path, whose
deadline the project treats as a hard constraint (`docs/ARCHITECTURE.md`).

**In progress, no date.** A lossy re-encode of the recordings is being built, at
around 96 kbit/s, which puts the audio at roughly 20 MB. That figure agrees with the
measurement above: 48 kbit/s scaled to ~9.6 MB, so 96 kbit/s is about double. It is
real work — a decoder in the render path plus regenerated assets and hashes — not a
build flag, so it carries no date and must not be quoted in the request as done.
The honest sequence is: land the re-encode, minify and measure, then file the request
with an APK under 30 MB, or file it with a measured justification for the exception.

### Gate 3 — the channel, which needs Camden's account

Submission is a Codeberg issue in `IzzyOnDroid/repodata`, using the form named
"App Inclusion Request" (`title: '[AppRequest] <App Name>'`, labels `app-request`
and `needs/apk-scan`). The FAQ confirms the tracker is the entry point: "The
preferred way is to file an issue."

No Codeberg account is connected to this workspace, so nothing can be filed from
here. Codeberg's own banner when the repository is opened also now reads
"LLM-generated content is being restricted", which is worth reading before filing.

## Which artifact the request should point at

No flavour-specific APK has ever been published. `v1.2` ships one build, five
architectures, and its manifest declares `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK` and AndroidX's
own dynamic-receiver permission — and no `INTERNET`, no
`REQUEST_INSTALL_PACKAGES`, no network-state permission. That was checked on the
downloaded `Nightfall-1.2-arm64-v8a.apk`, not only in the source manifest, and it
matches a tree with no `github`/`fdroid` product flavours in
`app/build.gradle.kts` and no updater code under `app/src`.

The practical consequence for the request: it currently points at the published
GitHub-channel APK, and that APK *is* the offline build, so the description is
accurate today. If a flavour split ever lands and a separate `fdroid` APK is
published, the request's asset selection must name it (`ApkMatch` in their metadata),
because a request that says "the fdroid build" while no such file exists points at
nothing. Write it correctly when the asset exists; today, describe what ships.



1. **Decide the AI disclosure**, then file or don't file. The dropdown and the two
   `AI Accountability` checkboxes must reflect what actually happened.
2. **Create or use a Codeberg account** and open the issue in
   `https://codeberg.org/IzzyOnDroid/repodata/issues`.
3. **Own the size work or delegate it.** The Opus re-encode of the recordings is
   already in progress elsewhere; the remaining piece is a minified release build
   (`minifyEnabled` is not set in `app/build.gradle.kts` today), which nobody has
   measured yet.
4. **Confirm the author fields**: name, public email for `AuthorEmail`, and whether
   a website goes in `WebSite` (the landing page in `docs/site/` is a candidate
   once it is published).
5. **Tag the release the request points at**, and attach the APK to it. Nightfall
   already does this automatically; the point is that the asset and the tag must
   match what the request says.

## The request, ready to paste

Paste into the form fields as labelled. Square brackets hold what Camden fills in;
nothing else needs editing.

**Title:** `[AppRequest] Nightfall`

**Guidelines** — check all four. The second one is the AI-policy acknowledgement in
the form's own words; it is true only in the sense argued under Gate 1.

**Link to the source code**
`https://github.com/CamjamPNG/Nightfall`

**Link to app in another app store**
`[none yet — or the F-Droid link once the fdroiddata merge request lands]`

**License used**
`MIT`

**Categories**
`Multimedia`

**Summary**
> A free, offline sleep-sound mixer with independent layers and original ambient
> music, with no account, no analytics and no INTERNET permission.

**Description**
> Nightfall builds a soundscape for sleeping out of independent layers. Each layer
> has its own gain, mute, solo, pan, high-/low-pass filters, three-band EQ and fade,
> and there is no fixed layer cap: the app measures its own render load and warns
> when a device is struggling instead of refusing a tenth layer.
>
> The library is all local. Colored noise (eleven colors, five of them spectral
> algorithms), synthesized weather, water, wind, fire and indoor textures, and seven
> bundled field recordings — birds, crickets, frogs, an owl-and-forest night, a
> quiet study room, distant urban rain and a café — each looping for at least three
> minutes before it repeats. Synthesized environments are labelled as synthesized,
> and one requested ambience is declared unavailable rather than faked.
>
> Music is generated on the device: eighteen procedural profiles with key, scale,
> A4 tuning, tempo, density, texture, reverb and seeded variation. A4=432 and 440 Hz
> are musical tuning references; no sleep-stage or healing benefit is claimed.
>
> Sleep timers fade out rather than cut, layers can stop on their own schedules,
> playback continues with the screen off through a foreground media service, and
> lock-screen, Bluetooth and headset media buttons work. Presets, mixes and settings
> are stored locally and can be exported as JSON.
>
> Code is MIT. The bundled recordings keep their CC BY, CC BY-SA and CC0 terms, and
> attribution ships inside the app and in `AUDIO_LICENSES.md`. The app has no
> account, no analytics, no advertising and no INTERNET permission; nothing leaves
> the device.
>
> Limits: the README states what has not been verified. A physical Pixel 7 pass,
> Bluetooth testing, acoustic loudness measurement and an overnight battery run are
> pending, and all-night playback cannot be guaranteed on every phone.

**Build instructions**
> ```sh
> # OpenJDK 17+, Android SDK Platform 35, Build Tools 35.0.0
> export ANDROID_HOME=/path/to/android-sdk
> ./gradlew :core:test :app:assembleRelease
> ```
> The release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`;
> signing is `apksigner` with the certificate whose SHA-256 is pinned in
> `docs/release-signing-certificate.sha256`. Reproducibility for tag `v1.0.1.2` is
> recorded in `docs/verification/reproducibility.json`; no upstream-signature
> reproduction is claimed, because the packaging step recompresses entries.
> Fastlane metadata is at `fastlane/metadata/android/en-US/`.

**Assistance Level**
`Substantial – Used throughout development` (or `Dominant` — Camden's call, and the
only two honest options).

**"AI" Tool(s)**
`Claude-based coding agents (Capy), acting on the maintainer's written specifications and reviews`

**What did the tools help with, and how?**
> Most of the implementation: Compose UI, the Kotlin DSP module, Gradle and release
> automation, tests and documentation were written by coding agents working from the
> maintainer's specifications and review comments. The maintainer set the product
> constraints (offline, no account, no tracking, signing continuity, honest limits),
> reviewed every change that shipped, and owns what is published.

**AI Accountability** — check both boxes only if Camden can stand behind them; they
describe review and manual verification, and the second one is the one to think
about given `docs/TESTING.md` still lists the hardware pass as pending.

**Further Notices**
> The APK is 87 MiB, above the 30 MB rule of thumb, because 74 MB of it is bundled
> 16-bit mono 24 kHz PCM recordings that the mixer memory-maps; native libraries are
> 17 KB and the per-ABI split is already in place, saving under 100 KB, so the split
> does not address the limit. A lossy re-encode of the recordings (Opus, ~96 kbit/s,
> roughly 20 MB of audio) is in progress and carries no date. `docs/LAUNCH.md`
> records the measurements.
>
> This source has no self-updater: the app declares no `INTERNET` permission and
> downloads nothing, which was verified on the published APK's manifest as well as in
> the source. `tools/check_release.py` fails a release if `INTERNET`, `RECORD_AUDIO`
> or the advertising ID permission appears in it.
