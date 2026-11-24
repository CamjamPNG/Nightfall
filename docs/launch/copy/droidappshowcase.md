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

> Nightfall — a sleep-sound mixer with 106 sounds, independent layers, no account, and a build with no network permission

Body:

> I built Nightfall because every sleep-sound app I tried wanted an account before it
> would play a fan noise, or streamed the audio, or shipped the same thirty-second
> rain loop that your brain locks onto at 2am.
>
> It's a native Android mixer (Kotlin, Jetpack Compose, Android 8+) with 106 sounds:
> colored noise, synthesized weather and water and fire and wind, generated ambient
> music, and seven bundled field recordings. The active mix is a list of independent
> layers. Each one has its own gain, mute, solo, pan, high/low-pass, three-band EQ and
> fade, and the music layers have their own composition controls — key, scale, A4
> tuning, tempo, density, texture, reverb.
>
> **You can hear it before you install it.** Eight clips, twenty to thirty seconds
> each, rendered offline by the same synthesis code the app runs, are in the repository:
> https://github.com/CamjamPNG/Nightfall/tree/main/docs/samples — including one made by
> typing "heavy rain on a metal roof with a fire" into the phrase box.
>
> Some details I care about:
>
> - **No accounts, no ads, no trackers.** All playback and music generation run on the
>   phone. It keeps no log of how you use it unless you switch the hidden diagnostics on
>   yourself — five taps on the version row — and even then it is a bounded local file with
>   no free text that never leaves the device unless you share it. It is not in the F-Droid
>   build at all.
>
> - **Type a sentence, get a mix.** "rain on a tent", "lo-fi sleep music", "heavy rain on a
>   metal roof with a fire" all build a layer mix through a small parser that runs on the
>   phone. No model, no server, no connection — and a phrase it can't place says so
>   instead of inventing something.
>
> - **Two builds, and the difference is the network.** The `fdroid-universal` APK on the
>   Releases page has no updater, no network code and no network permission at all — it
>   cannot open a socket. It is a file on that page, not a listing: Nightfall isn't in
>   F-Droid's catalogue, and the repository the project wants to serve from isn't up yet.
>   The other APKs on the same page add an updater that reads this repository's release
>   manifest and, by default, installs a newer APK on its own once nothing is playing; it
>   never interrupts a mix and never uploads anything. Playback and music generation use no
>   network in either build.
> - **No layer cap.** It measures its own render load and warns you when the device is
>   struggling, rather than refusing a tenth layer.
> - **Recordings loop for at least three minutes** before repeating, so the loop isn't
>   the thing that keeps you awake. Synthesized sounds are labeled as synthesized
>   instead of being passed off as field recordings.
> - **Sleep timers fade out** rather than cut, and individual layers can stop on their
>   own schedules.
> - **The mix can keep moving.** An endless-night switch lets companion layers from the
>   same families drift in and out, and each layer's tone drifts around its setting. Four
>   presets are themed nights — *Storm That Passes* opens on heavy rain, thunder and
>   howling wind for two hours, moves to rain on a metal roof, then drizzle, then a distant
>   ocean bed. No ear has heard a whole night of either on a phone yet.
> - **A widget and a Quick Settings tile**, both driving the app's own media session, so
>   their state follows what is playing rather than the last tap you made. Neither has been
>   seen on a real launcher or a real shade.
> - **Hidden opt-in diagnostics** you have to ask for (five taps on the version row) and
>   that never transmit anything. Android 16 targeting, and every UI string is in resources
>   so the app can be translated — nothing has been translated yet.
> - **MIT licensed**, source is readable, and the audio engine is a Kotlin module with no
>   Android dependency and 127 tests, so the DSP is inspectable rather than hidden.
>
> What it isn't: there is no iOS version, no cloud sync, no Android Auto.
> A physical-device pass (Pixel 7, Bluetooth, overnight battery) is still pending — I've
> documented that honestly in the README's limitations section instead of claiming
> all-night reliability I haven't measured. Five things that came with features are in the
> same list: the Opus decoder the recordings go through has never run on a phone, nobody has
> heard the catalogue on a device, the widget has never been placed on a real launcher, the
> tile has never been in a real shade, and diagnostics has never recorded a real crash. The
> download is 11.8 MB.
>
> Free, no account, no strings.
>
> Source, screenshots and APKs: https://github.com/CamjamPNG/Nightfall — the four screens
> under the title are the app's own Compose UI rendered on a JVM, not mockups.
>
> MIT licensed. I'm the developer — questions welcome in the thread.

Notes for Camden before posting: the size line is the current release's `arm64-v8a` APK —
11,832,096 bytes, measured 23 November 2026. It moves between releases, so `gh release
view <tag> --json assets` is the check; the test and sound counts in the body are
regenerated from the tree by `python3 tools/count_tests.py --update`, so leave them alone
unless you change the prose around them. Keep the unverified list with the size; five
things shipped unverified, not one, and the README names them. Do not add a review count,
a user count, or a star count.
