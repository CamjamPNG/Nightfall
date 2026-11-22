# Roadmap

This is a queue of real open work, not a feature wishlist. Every item here has
evidence in the repository — a pending check in `docs/TESTING.md`, a disabled
catalogue entry, a limitation written in `README.md`, or an unfinished step in
`fdroid/README.md`. There are no dates and no promises: this is maintained by one
person in their own time, and a date on a hobby project is a lie with a calendar.

The ordering is deliberate. Item 1 unblocks the most claims, so it is first even
though it is the least fun.

## Where it stands

Shipping today: a working offline mixer with no `INTERNET` permission, no layer
cap, 25 built-in presets (four of them themed nights that change what plays over the night),
local preset storage and JSON exchange, sleep timers
with per-layer stop schedules, a Media3 foreground media session with lock-screen
and Bluetooth controls, a deterministic core suite, a release-tooling suite and
Robolectric playback scenarios, all sized in the generated table in [README](README.md)
so no copy of a count lives here. Signed GitHub releases with a pinned certificate.
`docs/TESTING.md` records exactly what was executed and what was not.

What each suite needs, none of which includes a phone or an emulator:

| Suite | Needs | Check it |
| --- | --- | --- |
| `./gradlew :core:test` | a JDK, and no Android SDK | with `ANDROID_HOME` unset and no `local.properties`: `BUILD SUCCESSFUL`, with only the opt-in calibration generator skipped |
| the Robolectric playback scenarios in `app/src/test` | the Android SDK, no device | `./gradlew :app:testGithubDebugUnitTest` |
| `python3 -m unittest discover -s tools/tests` | Python 3 | `OK` |

The gap between "shipping" and "proven" is the whole roadmap.

## 1. Verify it on real hardware

**Why it is open:** every acoustic, Bluetooth and battery claim in the README is
explicitly not tested. `docs/TESTING.md` lists the pending pass: a physical Pixel
7 with the intended Bluetooth sleep mask or headphones, media buttons, disconnect
and reconnect, audio-focus interruption, navigation and configuration changes,
lock-screen controls, independent layer adjustment, timer expiry, acoustic
quality, and an overnight underrun/battery run.

**Why it is first:** it gates the F-Droid submission, it gates any sentence in the
README that sounds confident, and it is the difference between "we think nine
layers is fine on a Pixel 7" and "nine layers ran all night on a Pixel 7 at this
underrun count". Nothing else on this list improves the project's credibility as
much per hour spent.

**Done when:** a date-stamped pass/fail record lands in `docs/TESTING.md`, the
corresponding values in `docs/verification/release-checks.json` stop saying
`not tested`, and only then does any prose claim get upgraded. Not before.

Size: one evening with the phone, plus the discipline to write down what failed.

## 2. Take the playback lifecycle from Robolectric onto a phone

**Why it is open:** playback surviving navigation and activity recreation, audio-focus pause
and resume, teardown, background playback, saving and restore are twelve Robolectric
scenarios in `app/src/test` — `AudioFocusTest`, `BackgroundPlaybackTest`,
`NineLayerPlaybackTest`, `SavingTest`, `StateRestorationTest`, `TeardownTest`,
`ThemedPresetPlayTest` and `BackGestureTest`. They run with no device attached and need no
`/dev/kvm`; CI runs them in both channels, and the `api36` job runs the same suite again on
Temurin 21 against the Android 16 platform classes. That is real coverage of the app's own
code, and none of it is a real process: there is no instrumented suite left, so
`:app/connectedDebugAndroidTest` has nothing to run, and every one of these paths executes
against Robolectric's stand-ins for the platform's audio, focus and lifecycle scheduling. The
one recorded attempt to instrument them failed with `Process crashed` and a startup ANR on a
constrained software emulator, and was never explained or retracted.

**Why it matters:** these are the behaviors most likely to break silently — service
lifecycle, focus, persistence — and the substitutions a JVM makes for the platform are
exactly where a scenario and a phone diverge.

**Done when:** the twelve scenarios pass on one physical device, either through a
`connectedDebugAndroidTest` companion or by driving the same paths by hand with the result
recorded in `docs/TESTING.md`, and the emulator failure is either explained or retracted. If
the ANR turns out to be emulator-only, say that with the evidence.

Size: small on a real phone, potentially large if the ANR is genuine.

## 3. F-Droid inclusion

**Why it is open:** the recipe, store metadata, scanner results and prepared
request text all exist in `fdroid/`. What is missing is eligibility and a
submission: the app was first published on 12 November 2026, and F-Droid's
Inclusion Policy asks for three months of active development, or an accepted
exception (proven users, or a maintainer with other apps in the repository). No
GitLab credentials are connected, so no merge request has been opened.

**Why it matters:** it is the distribution channel that matches the product's
pitch — a free, offline, no-account app in a catalogue that does not require an
account to install from. It is also a real second build environment, which is
worth more than the downloads.

**Done when:** an `fdroiddata` merge request exists with the recipe, the
buildserver build succeeds, and the listing is live — or a maintainer declines and
the reason is recorded in `fdroid/README.md`. Do not open the request before the
development-age question has an honest answer.

Size: policy reading plus one merge request; variable waiting.

## 4. Distant household ambience

**Why it is open:** `Library.kt` declares the entry with `kind = "unavailable"`
because no suitably licensed recording was obtained. It is visible in the library
and never plays substitute noise — that honesty is the point of the entry.

**Why it matters:** it is the one place where the catalogue visibly says "we would
rather be missing a sound than fake one". Closing it properly (a real recording,
properly credited) removes the only permanently disabled row.

**Done when:** a recording with a licence permitting redistribution and
modification is prepared through `tools/prepare_audio.py`, credited in
`AUDIO_LICENSES.md`, and the entry flips to `kind = "recording"` with at least a
180-second cycle. If no such recording exists, the entry stays unavailable and
that is a fine outcome.

Size: small, but gated on finding audio you may actually use.

## 5. Surface the version in the app

**Why it is open:** nothing in `app/src/main/java/dev/nightfall/` reads
`BuildConfig.VERSION_NAME` or `VERSION_CODE`. The only way to learn which build
is installed is the file manager or the store listing.

**Why it matters:** every bug report and security report needs the version, and
right now asking for it is a guessing game. It is also the cheapest possible
first contribution: a text row in `SettingsScreen` and one test.

**Done when:** Settings shows the version name and `versionCode`, they match
`app/build.gradle.kts`, and a screenshot in `docs/` shows it.

Size: an afternoon, mostly reading `Screens.kt`.

## 6. Decide what happens after process death or reboot

**Why it is open:** playback and timers do not resume after force-stop, process
death or reboot, and `SleepTimer` deliberately stores only a monotonic deadline so
a stale timer is never restored as live. Both decisions are right individually
and the combination is a real user annoyance: the mix is saved, the reason it
stopped is not.

**Why it is a decision, not a bug fix:** resuming needs a persisted intent and a
deliberate rule about whether the timer restarts, continues from wall-clock, or is
dropped — and the last of those is what the code does today on purpose. Someone
has to choose, and the choice needs to be defensible before code exists.

**Done when:** `docs/ARCHITECTURE.md`'s "Time and automation" section states the
rule and the code follows it, with a test for the restore path.

Size: design first, then small.

## 7. External audio import

**Why it is listed, not started:** the README says plainly that external audio
import is not included. Letting users add their own recordings is the most-requested
shape of feature for this kind of app and the largest scope change on this list:
file pickers, format decoding, long-file streaming, storage size, and a policy for
what happens to imported files when a mix is exported. It is also the change most
likely to break the decode model that makes the current engine cheap, where each
read head decodes its own stream ahead of itself instead of loading a file.

**Done when:** someone decides it is worth the architectural cost. Until then it
stays a documented absence rather than a half-built path.

Size: large.

## Not planned

These are decisions, not backlog. They will not be merged:

- Accounts, cloud sync, or any server.
- Analytics, crash reporting, advertising or tracking SDKs.
- A fixed layer cap. The app measures device capacity and warns; it does not
  arbitrate.
- Any claim about sleep stages, healing, therapy or clinical benefit, including
  for binaural tones or A4=432 Hz.
- Silent or automatic app updates, or any update mechanism that adds network
  access on its own.
- Buyable growth. No purchased stars, no fake accounts, no invented download
  counts, reviews or testimonials.

## How to pick something up

Open an issue first for anything larger than a typo, so two people do not write
the same patch. Items 4 and 5 are genuinely good first contributions; item 1 needs
the device. [CONTRIBUTING.md](CONTRIBUTING.md) has the build gate, the commit
convention and the rules on adding a sound.
