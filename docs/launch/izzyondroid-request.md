# IzzyOnDroid inclusion request — packaged, with the gates named

Read on 18 November 2026 from `izzyondroid.org` and from the `IzzyOnDroid/repodata`
repository on Codeberg. Every quotation below is copied from those pages, with the
section it came from.

This document contains the request text ready to paste, but it does not claim the
request is submittable today. Three gates stand in front of it, and only one of them
is about size: see **Gates** and **Which artifact the request points at**. The request
itself is drafted twice — the honest AI disclosure as the default, and the narrower
claim Camden may choose only if he can stand behind it — plus the option of not
filing at all.

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
`docs/TESTING.md` records what was and was not executed, `core/src/test` holds 69
deterministic tests (68 of which run without an opt-in flag), and the README carries an
explicit unverified list. If the
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

As of `v1.8.2`, six APKs ship, and the per-ABI split is already done:

| Asset | Bytes | MiB |
| --- | --- | --- |
| `Nightfall-1.8.2-arm64-v8a.apk` | 79,190,898 | 75.5 |
| `Nightfall-1.8.2-armeabi-v7a.apk` | 79,186,806 | 75.5 |
| `Nightfall-1.8.2-x86.apk` | 79,186,790 | 75.5 |
| `Nightfall-1.8.2-x86_64.apk` | 79,190,892 | 75.5 |
| `Nightfall-1.8.2-universal.apk` | 79,289,722 | 75.6 |
| `Nightfall-1.8.2-fdroid-universal.apk` | 79,232,378 | 75.6 |

Splitting by architecture saves **102,932 bytes** at most, because it is not the
native libraries that are large: `lib/` in the `arm64-v8a` APK is 17,208 bytes.
Unzipping that APK on 18 November 2026:

```
77449472  assets/audio/*.pcm  (74 MB of 16-bit mono 24 kHz PCM, stored uncompressed)
 2933096  classes.dex       (R8-minified: 2.8 MB, was 47.37 MB across three files before 1.5.2)
   17208  lib/
```

The format of those recordings is in the code, not inferred:
`class RecordingSource` is documented "Mono 24 kHz recording. Every bundled cycle
is longer than three minutes" (`core/src/main/kotlin/dev/nightfall/core/Sources.kt`),
`OVERLAP` is 24,000 frames, and the mixer resamples them into its 48 kHz stereo
mix. Measured from the shipped PCM byte counts, the cycles run 180.5 s to 240.0 s.

Two payloads, two different fixes, and both have now been built — one shipped, one
flagged off.

**Dex: 47,367,428 bytes to 2,933,096.** In `v1.3` the release build set
`buildTypes { release { vcsInfo … } }` and nothing else, so the dex was three files
totalling 47,367,428 bytes. R8 minification and resource shrinking shipped in 1.5.2
(`isMinifyEnabled = true`, `isShrinkResources = true`) and cut that to 2,933,096.
Those are *uncompressed entry* sizes, and dex is compressed inside the APK, so the
download fell by less than they suggest: `v1.3`'s `arm64-v8a` was 91,162,628 bytes and
`v1.8.2`'s is 79,190,898, a measured 11,971,730 bytes (11.4 MiB) less. The 44.4 MB is
a payload reduction and the 12 MB is the download reduction; quoting the first as
bandwidth overstates the saving by nearly a factor of four. Anything that still
describes Nightfall's dex as unshrunk is describing `v1.3`.

**Audio: 77,449,472 bytes of PCM, measured today.** I encoded three of the seven
recordings with ffmpeg to size the options. Those three are 19.4% of the PCM payload,
and the arithmetic below scales them to the set — that scaling is an estimate and is
labelled as one:

| Encoding of the 3 sampled files | Bytes | Share of that PCM | Scaled to all 7 |
| --- | --- | --- | --- |
| PCM 16-bit mono 24 kHz (shipped) | 31,850,496 | 100% | 77.4 MB |
| FLAC, lossless | 19,722,247 | 62% | ≈ 48 MB |
| Opus 48 kbit/s mono | 3,957,851 | 12.4% | ≈ 9.6 MB |

FLAC alone is not enough and neither is R8 alone. Opus plus a minified dex is the
only combination that plausibly lands under 30 MB, and Opus puts a decoder in the
path that currently memory-maps raw PCM — a change to the render path, whose deadline
the project treats as a hard constraint (`docs/ARCHITECTURE.md`).

**The encoded set exists; it does not ship.** The re-encode landed in the shape the
estimate predicted: `app/src/opus/assets/audio` holds seven `.nfop` containers
totalling 10,089,142 bytes, close to the ~9.6 MB the 48 kbit/s sample scaled to. The
decode path that reads them is tested on the JVM (`OpusStreamTest`, 14 of the core
tests) and is selected by `-PnightfallOpus=true`, off by default, because nobody has
heard the decoded output on a device yet (`app/build.gradle.kts`: "until an Opus
decode path has been heard on a device").

Arithmetic on the measured assets — **not a build, and not to be quoted as one** —
puts a flagged APK around 11.8 MB: 79,190,898 minus 77,449,472 plus 10,089,142. That
is comfortably under the 30 MB ceiling, so the size gate is now one build and one
listening test away rather than an engineering project. The remaining work is to build
`-PnightfallOpus=true`, check it against `tools/check_release.py`, hear it on hardware,
and publish it — a product decision for the audio-engine owner and Camden, not a copy
decision.

### Gate 3 — the channel, which needs Camden's account

Submission is a Codeberg issue in `IzzyOnDroid/repodata`, using the form named
"App Inclusion Request" (`title: '[AppRequest] <App Name>'`, labels `app-request`
and `needs/apk-scan`). The FAQ confirms the tracker is the entry point: "The
preferred way is to file an issue."

No Codeberg account is connected to this workspace, so nothing can be filed from
here. Codeberg's own banner when the repository is opened also now reads
"LLM-generated content is being restricted", which is worth reading before filing.

## Which artifact the request points at — and why it cannot be filed yet

The project now builds two channels
(`flavorDimensions += "channel"` in `app/build.gradle.kts`):

| Channel | Network permission | Updater | Published as a release asset? |
| --- | --- | --- | --- |
| `github` | `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | yes, thirteen files under `app/src/github/java/dev/nightfall/update/` | **yes — five APKs on every release** |
| `fdroid` | all four `tools:node="remove"`d | no; `app/src/fdroid/java/dev/nightfall/update/UpdateSurfaces.kt` is a stub | **yes — `Nightfall-<version>-fdroid-universal.apk`, since 1.5** |

Both rows were confirmed twice: in the source manifests, and on the downloaded
`Nightfall-1.8.2-arm64-v8a.apk`, whose binary manifest lists `INTERNET`,
`ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` and
`UPDATE_PACKAGES_WITHOUT_USER_ACTION`, and on the downloaded
`Nightfall-1.8.2-fdroid-universal.apk`, whose binary manifest lists none of them and
whose dex contains no class from the updater package.

That second confirmation is what changed since this section was first written. The
request can now point at a **published `fdroid`-channel APK** —
`Nightfall-1.8.2-fdroid-universal.apk`, attached to every release since 1.5, one
universal APK so `ApkMatch` can select it by name, signed with the same pinned
certificate as the rest, and deliberately absent from `release-manifest.json` so the
in-app updater never offers it. The old "third gate" — publish that asset — is closed.
What is left is the AI disclosure and the size, and the size is one build away: a
`-PnightfallOpus=true` APK would be about 11.8 MB by arithmetic on measured assets.



1. **Decide the AI disclosure**, then file, file a narrower claim that is still true,
   or don't file. The dropdown and the two `AI Accountability` checkboxes must reflect
   what actually happened.
2. **Create or use a Codeberg account** and open the issue in
   `https://codeberg.org/IzzyOnDroid/repodata/issues`.
3. **Own the size work or delegate it.** The Opus decode path and its encoded assets
   are in the repository behind `-PnightfallOpus=true`, off by default; the remaining
   piece is to build that variant, measure it, hear it on a device and make it the
   default, which lands the APK near 12 MB by arithmetic on measured assets.
4. **Confirm the author fields**: name, public email for `AuthorEmail`, and whether
   a website goes in `WebSite` (the landing page in `docs/site/` is a candidate
   once it is published).
5. **Tag the release the request points at**, and attach the APK to it. Nightfall
   already does this automatically; the point is that the asset and the tag must
   match what the request says.

## The request, ready to paste — Version A (honest disclosure, the default)

Paste into the form fields as labelled. Square brackets hold what Camden fills in;
nothing else needs editing. Version A states the AI assistance truthfully and lets
the maintainers decide; Version B is the narrower claim Camden may substitute **only
if
he can honestly stand behind it**; Version C is the decision not to file.

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
> music: no account, no analytics, and the F-Droid build declares no network
> permission at all.

**Description**
> Nightfall builds a soundscape for sleeping out of independent layers. Each layer
> has its own gain, mute, solo, pan, high-/low-pass filters, three-band EQ and fade,
> and there is no fixed layer cap: the app measures its own render load and warns
> when a device is struggling instead of refusing a tenth layer.
>
> The library is all local. Colored noise, synthesized weather, water, wind, fire and
> indoor textures, and seven bundled field recordings — birds, crickets, frogs, an
> owl-and-forest night, a quiet study room, distant urban rain and a café — each
> looping for at least three minutes before it repeats. Synthesized environments are
> labelled as synthesized, and one requested ambience is declared unavailable rather
> than faked.
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
> Two builds exist: the F-Droid channel, which is the one offered here, contains no
> updater, no network code and no network permission and cannot open a socket; the
> GitHub channel adds an updater that reads this repository's release manifest and,
> by default, installs a newer APK on its own once playback has stopped. Playback,
> generation and timers never use the network in either build.
>
> Code is MIT. The bundled recordings keep their CC BY, CC BY-SA and CC0 terms, and
> attribution ships inside the app and in `AUDIO_LICENSES.md`. No account, no
> analytics, no advertising.
>
> Limits: the README states what has not been verified. A physical Pixel 7 pass,
> Bluetooth testing, acoustic loudness measurement and an overnight battery run are
> pending, and all-night playback cannot be guaranteed on every phone. The download
> is large: about 79 MB, most of it bundled PCM recordings.

**Build instructions**
> ```sh
> # OpenJDK 17+, Android SDK Platform 35, Build Tools 35.0.0
> export ANDROID_HOME=/path/to/android-sdk
> ./gradlew :core:test :app:assembleFdroidRelease
> ```
> The F-Droid-channel APK is
> `app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk`; the
> `github` channel (`:app:assembleGithubRelease`) adds the updater and must not be
> the one distributed here. Signing is `apksigner` with the certificate whose SHA-256
> is pinned in `docs/release-signing-certificate.sha256`. Reproducibility for tag
> `v1.0.1.2` is recorded in `docs/verification/reproducibility.json`; no
> upstream-signature reproduction is claimed, because the packaging step recompresses
> entries. Fastlane metadata is at `fastlane/metadata/android/en-US/`.

**Assistance Level**
`Dominant – Most code or content was "AI"-generated` — or `Substantial – Used
throughout development` if Camden judges that a fairer description of the split. Both
are honest; only "None" and "Minimal" are not.

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
about given `docs/TESTING.md` still lists the hardware pass as pending. Checking the
first without the second is more honest than checking both.

**Further Notices**
> The APK is 79 MB (75.5 MiB), above the 30 MB rule of thumb, because 77.4 MB of it is
> bundled 16-bit mono 24 kHz PCM recordings that the mixer memory-maps; native
> libraries are 17 KB and the per-ABI split is already in place, saving about 100 KB,
> so the split does not address the limit. The release dex is minified (2.9 MB, from
> 47.4 MB before R8 was enabled in 1.5.2). An Opus decode path with its encoded
> recordings exists behind a build flag that is off by default, because it has not yet
> been heard on a device; an APK built with it would be about 12 MB by arithmetic on
> the measured assets. `docs/LAUNCH.md` records the measurements.
>
> The asset to distribute is the `fdroid`-channel APK,
> `Nightfall-1.8.2-fdroid-universal.apk`: it declares no `INTERNET`,
> `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` or
> `UPDATE_PACKAGES_WITHOUT_USER_ACTION` permission and contains no updater code. The
> five other APKs attached to that release are the `github` channel, which does carry
> the updater; `tools/check_release.py` fails a release if either channel's permissions
> drift from that statement.
>
> Note on the recordings: two of the seven field recordings are CC BY-SA. Their
> attribution ships in-app and in `AUDIO_LICENSES.md`.

## Version B — the narrower claim, only if it is true

The form's Assistance Level is a judgement about how much of the code a tool wrote,
and Camden is the only person who can make it. If, after looking at the tree, he
judges that "Moderate – Used for specific tasks or modules" or "Minimal" is a fair
description — because he wrote and understands the majority of what ships and can
defend any file in it — he may select that instead. What he may not do is select
"None", or select a lower level while the tool history says otherwise.

If Version B is used, the `AI Accountability` checkboxes and the `What did the tools
help with` field carry more weight, not less: they are where the reviewer checks
whether the claim is plausible. Fill them specifically — which modules, which
decisions, which reviews — rather than with a general assurance.

## Version C — not filing

The honest default may simply not be worth a rejection, and that is a legitimate
outcome. What remains if Camden chooses it:

- The F-Droid listing in `fdroid/` (the repository thread's work) faces the same AI
  policy question, alongside the development-age rule; nothing in this document
  changes that.
- Publishing the `fdroid`-channel APK as a release asset is still worth doing, for
  people who want the build without the updater and for any future store.
- The landing page in `docs/site/` and the four open channel drafts do not depend on
  IzzyOnDroid at all.

Do not file a request with a false Assistance Level to get listed. The policy says
transparency failures "can lead to the project being degraded to rejected state", so
the upside of a false answer is a listing that can be removed later, and the downside
is a documented reason never to accept the app.


