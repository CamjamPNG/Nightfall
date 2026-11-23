# Camden — what to post, in what order, and what is missing

Everything else in `docs/launch/` is research and drafts. This file is the short
version. The version, sizes and permissions were read from release `v2.6.2` on
23 November 2026; `docs/LAUNCH.md` has the evidence command beside every one of them,
and the test, sound and clip counts are regenerated from the tree rather than typed.

## Post today: r/droidappshowcase, then Lemmy, then a text Mastodon post

None of the three needs anything that does not exist yet. All three link the
repository, and the repository is where the new material is: the strip of four rendered
screens under the README title, the eight clips in `docs/samples/`, and the six APKs on
the Releases page.

**First**, r/droidappshowcase. It is the only subreddit on the list whose stated purpose
is developers showing their own apps rather than tolerating them, and its rules are the
ones this project can meet without an argument: a real description, a repository link
instead of an APK, one app post per week, a 24-hour-old account with 2 karma and a
verified email. No AI-content clause, no "no self-promotion" clause. That is why it goes
first.

Paste from [`copy/droidappshowcase.md`](copy/droidappshowcase.md). It carries the
rule text it was checked against, an archive URL and the date the rule page was read,
so if a mod says the rule changed you have the version you complied with.

**Second**, Lemmy `!androidapps@lemmy.ml`, same day or a day later, from
[`copy/lemmy.md`](copy/lemmy.md) — the rules there allow a GitHub link and forbid
direct APK links, which the draft already respects.

**Third**, Mastodon, from [`copy/mastodon.md`](copy/mastodon.md). It is 489 characters as
Mastodon counts it, so it fits a default instance, and it is written to work as text.
The clip makes it better, not possible — it is the one channel that wants video.

**Last**, r/Android, and only if your account already lives there
([`copy/reddit-android.md`](copy/reddit-android.md)) — its rules want a three-month
account with history in that subreddit and the author present in the comments.

Show HN has facts but no draft, because HN's guideline forbids the post being
generated text at all. [`copy/show-hn.md`](copy/show-hn.md) is what you would say in
your own words, not something to paste. It is a much better pitch than it was: HN wants
something a reader can try out, and eight clips in a browser are exactly that.

Four channels are closed and the reasons are quoted in
[`copy/dropped.md`](copy/dropped.md). Don't spend a post finding out they are closed.

## Before you paste anything: three checks

1. **The counts are generated; the size and version are not.** `python3
   tools/count_tests.py` prints the test table, and `--update` rewrites every test,
   sound and clip count in these files from the tree — if CI or `tools/check_release.py`
   says a figure drifted, run it and paste the result. What it cannot derive is the
   download: `gh release list --limit 1` and `gh release view <tag> --json assets` settle
   the version and the sizes in under a minute, and the drafts say 11.8 MB, which is the
   `arm64-v8a` APK of `v2.6.2` (11,832,096 bytes).
2. **Catalogue and preset counts, if you add one.** These are not in any draft, and if you
   want them in a post, take them from the compiled module rather than from prose:

   ```sh
   ./gradlew :core:jar
   CP="core/build/libs/core.jar:$(find ~/.gradle/caches -name 'kotlin-stdlib-2.1.20.jar' | head -1):$(find ~/.gradle/caches -name 'kotlinx-serialization-core-jvm-*.jar' | head -1):$(find ~/.gradle/caches -name 'kotlinx-serialization-json-jvm-*.jar' | head -1)"
   printf 'var l = dev.nightfall.core.Library.INSTANCE;\nvar p = dev.nightfall.core.Presets.INSTANCE;\nSystem.out.println("catalogue=" + l.getAll().size() + " available=" + (int) l.getAll().stream().filter(s -> s.getAvailable()).count() + " presets=" + p.getAll().size());\n/exit\n' | jshell --class-path "$CP" -q -
   # catalogue=106 available=105 presets=25
   ```

   The comment is what it prints today: 106 rows in Appendix A of `docs/LOCALIZATION.md`,
   and 21 presets plus four themed nights in `Library.kt`. Run it before quoting it — a
   wrong count in public copy is the one error a reader catches.
3. **Post as the developer, in the first lines.** Every draft says so. Keep it.

## Keep the five unverified lines, and do not offer an F-Droid install

2.0 shipped an Opus decoder that no phone has run, a catalogue no ear has heard on a
device, a widget no launcher has hosted and a diagnostics journal that has never recorded
a real crash; 2.6 added a Quick Settings tile no shade has hosted. Each draft carries
them; a post that drops one because the new release reads better is the kind of claim this
project has spent two releases avoiding. They are listed by name in `README.md` under
*Never checked on hardware*.

The `fdroid`-channel APK is real and published — `Nightfall-<version>-fdroid-universal.apk`
sits on every release — but the repository it is meant to serve from returns `404`, because
the repository key command and the Pages toggle have not been run. So say "the offline
build is on the Releases page" and never "Nightfall is on F-Droid". `docs/FDROID.md` has
the two commands that would change that.

## The one thing this kit cannot do: the demo clip needs your Pixel 7

The clip is scripted in `docs/LAUNCH.md` (80–90 seconds, airplane mode on, status bar
visible, media sounds enabled) and it has never been recorded, because no build
machine here has a device and there is no `/dev/kvm` for an emulator. It now has to show
the phrase box and the tile as well as the mixer.

Without it, Mastodon gets a text post, which is allowed and works, and nothing else
changes — the subreddit and Lemmy posts are text posts by design. Note that the clip
deliberately cannot show the update row or *What's new*, since both need a connection to
appear; that is a second, shorter clip for later, not a reason to weaken the first one.

## What is not blocked, and costs an hour

GitHub itself: topics (`android`, `kotlin`, `jetpack-compose`, `dsp`, `offline-first`,
`sleep`), a repository description, and a few genuine `good first issue` entries. That
work has no rules to break, no account to age, and it is where contributors actually
arrive from. It can be done today, before any post — and the README's first screen, which
is now a strip of four rendered screens and a *Hear it* section, is what those topics
point at.

## Posting is yours, and that is correct

These are your accounts, your reputation and your judgement about what your own
project sounds like. Nobody else should post as you, and no draft here should be
posted in a place whose rules forbid it. The drafts are here to be edited into your
voice, not pasted over it.

IzzyOnDroid is the one place worth a decision before you post anywhere: their policy
is explicitly against AI-generated code, and `docs/launch/izzyondroid-request.md`
explains what filing honestly would mean. Nothing has been submitted, and nothing
should be until you read that file.
