# r/droidappshowcase

The one Reddit community where a developer posting an app is the point of the
subreddit rather than a tolerated exception. `r/androidapps` explicitly forwards
this content here, and that is the rule that makes this the first Reddit channel.

## Rule checked

Rules page for `r/droidappshowcase`, read from an Internet Archive capture dated
**9 August 2026** (`web.archive.org/web/20260809105233/https://old.reddit.com/r/droidappshowcase/about/rules`).
Reddit returns `403` to this machine, so the archive copy is the evidence and the
page should be re-read before posting.

> **4. Use Suggested Template / Only High Quality Posts** … we do require at least a
> basic description and link … Posts that are just a link with no description may be
> removed.
>
> **5. No Spam or Excessive Self-Promotion** … • Limit: **1 app post per week per
> account** • No cross-sub spam, link-dumping, or affiliate links • No obvious
> promo-only accounts or spam patterns
>
> **6. App Link Requirement & Policy** — All showcases must include a direct link to
> the app page (**no direct apk links**) from a reputable source (Google Play, Galaxy
> Store, F-Droid, GitHub, Codeberg etc.)
>
> **8. Account Age & Karma Requirements** … Age: 24+ hours, Karma: 2+ combined
>
> **9. Verified email** — To post you must have a verified email.

What the rules mean for this post: a self post with a real description, the
**repository** link (not a release APK link, not a direct download), one post this
week, and an account that is not brand new. Nothing in these rules requires an
established posting history, which is why this channel comes before r/Android.

## Draft

Suggested title (no editorialising, no invented numbers):

> Nightfall — a sleep-sound mixer with independent layers, no account, no usage log unless you opt in, and no network permission in the F-Droid build

Body:

> I built Nightfall because every sleep-sound app I tried wanted an account before it
> would play a fan noise, or streamed the audio, or shipped the same thirty-second
> rain loop that your brain locks onto at 2am.
>
> It's a native Android mixer (Kotlin, Jetpack Compose, Android 8+). The active mix is
> a list of independent layers: colored noise, synthesized weather/water/fire/wind,
> synthesized ambient music, and seven field recordings. Each layer has its own gain,
> mute, solo, pan, high/low-pass, three-band EQ and fade, and the music layers have
> their own composition controls — key, scale, A4 tuning, tempo, density, texture,
> reverb.
>
> Some details I care about:
>
> - **No accounts, no ads, no trackers.** All playback and music generation run on the
>   phone. It keeps no log of how you use it unless you switch the hidden diagnostics on
>   yourself — five taps on the version row — and even then it is a bounded local file with
>   no free text that never leaves the device unless you share it. It is not in the F-Droid
>   build at all.
>
> - **Two builds, and the difference is the network.** The F-Droid build has no updater, no
>   network code and no network permission at all — it cannot open a socket. The build on
>   the Releases page adds an updater that reads this repository's release manifest and,
>   by default, installs a newer APK on its own once nothing is playing; it never
>   interrupts a mix and never uploads anything. Playback and music generation use no
>   network in either build.
> - **No layer cap.** It measures its own render load and warns you when the device is
>   struggling, rather than refusing a tenth layer.
> - **Recordings loop for at least three minutes** before repeating, so the loop isn't
>   the thing that keeps you awake. Synthesized sounds are labeled as synthesized
>   instead of being passed off as field recordings.
> - **Sleep timers fade out** rather than cut, and individual layers can stop on their
>   own schedules.
> - **The mix can keep moving.** 2.0 added an endless-night switch: companion layers from
>   the same families as the ones you picked drift in and out, and each layer's tone
>   drifts around its setting. No ear has heard a whole night of it on a phone yet.
> - **A home-screen play/pause widget**, plus hidden opt-in diagnostics you have to ask
>   for (five taps on the version row) and that never transmit anything. Android 16
>   targeting, and every UI string is now in resources so the app can be translated —
>   nothing has been translated yet.
> - **MIT licensed**, source is readable, and the audio engine is a Kotlin module with
>   no Android dependency and 97 tests, so the DSP is inspectable rather than hidden.
>
> What it isn't: there is no iOS version, no cloud sync, no Android Auto.
> A physical-device pass (Pixel 7, Bluetooth mask, overnight battery) is still pending
> — I've documented that honestly in the README's limitations section instead of
> claiming all-night reliability I haven't measured. The widget has never been placed on
> a real launcher, and the Opus decoder the recordings now go through has never run on a
> phone. The download is 11.7 MB.
>
> Free, no account, no strings.
>
> Source and APKs: https://github.com/CamjamPNG/Nightfall
>
> MIT licensed. I'm the developer — questions welcome in the thread.

Notes for Camden before posting: the size line changed in 2.0 — the APK is 11.7 MB, not
79 MB, because the recordings ship as Opus instead of raw PCM. Keep the line about what
has not been verified with it; four things are unverified, not one, and the README lists
them by name. Do not add a review count, a user count, or a star count.
