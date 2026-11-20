# Camden — what to post, in what order, and what is missing

Everything else in `docs/launch/` is research and drafts. This file is the short
version. Facts and numbers were measured on 19 November 2026 against release
`v2.0`; `docs/LAUNCH.md` has the evidence command beside every one of them.

## Post first: r/droidappshowcase

It is the only subreddit on the list whose stated purpose is developers showing
their own apps rather than tolerating them, and its rules are the ones this project
can meet without an argument: a real description, a repository link instead of an
APK, one app post per week, a 24-hour-old account with 2 karma and a verified email.
No AI-content clause, no "no self-promotion" clause. That is why it goes first.

Paste from [`copy/droidappshowcase.md`](copy/droidappshowcase.md). It carries the
rule text it was checked against, an archive URL and the date the rule page was read,
so if a mod says the rule changed you have the version you complied with.

Lemmy `!androidapps@lemmy.ml` is the second post, same day or a day later, from
[`copy/lemmy.md`](copy/lemmy.md) — the rules there allow a GitHub link and forbid
direct APK links, which the draft already respects. Mastodon comes third, with the
demo clip once you have it ([`copy/mastodon.md`](copy/mastodon.md)); the fediverse
post is worth little without the clip and reads as spam without the limitation line.
r/Android is last and only if your account already lives there
([`copy/reddit-android.md`](copy/reddit-android.md)) — its rules want a three-month
account with history in that subreddit and the author present in the comments.

Show HN has facts but no draft, because HN's guideline forbids the post being
generated text at all. [`copy/show-hn.md`](copy/show-hn.md) is what you would say in
your own words, not something to paste.

Four channels are closed and the reasons are quoted in
[`copy/dropped.md`](copy/dropped.md). Don't spend a post finding out they are
closed.

## Before you paste anything: two checks

1. **Re-read the numbers against the release you are actually linking.** These drafts
   name `v2.0`, 11.7 MB, 97 core tests and six APKs, all measured on 19 November 2026.
   No draft quotes a catalogue or preset count; if you add one, take it from the command
   below rather than from `docs/LAUNCH.md`, which carries figures the posts do not need. `gh release list --limit 1` and
   `gh release view <tag> --json assets` are enough to confirm or correct the version
   and the sizes in under a minute; the catalogue and preset counts come from the
   compiled module:

   ```sh
   ./gradlew :core:jar
   CP="core/build/libs/core.jar:$(find ~/.gradle/caches -name 'kotlin-stdlib-2.1.20.jar' | head -1):$(find ~/.gradle/caches -name 'kotlinx-serialization-core-jvm-*.jar' | head -1):$(find ~/.gradle/caches -name 'kotlinx-serialization-json-jvm-*.jar' | head -1)"
   printf 'var l = dev.nightfall.core.Library.INSTANCE;\nvar p = dev.nightfall.core.Presets.INSTANCE;\nSystem.out.println("catalogue=" + l.getAll().size() + " available=" + (int) l.getAll().stream().filter(s -> s.getAvailable()).count() + " presets=" + p.getAll().size());\n/exit\n' | jshell --class-path "$CP" -q -
   # catalogue=106 available=105 presets=25
   ```

   Run both before posting: these figures move whenever a release or the catalogue
   does, and a wrong count in public copy is the one error a reader catches. A stale
   size is the other.
2. **Post as the developer, in the first lines.** Every draft says so. Keep it.
3. **Keep the four unverified lines.** 2.0 shipped an Opus decoder that no phone has run,
   a catalogue no ear has heard on a device, a widget no launcher has hosted, and a
   diagnostics journal that has never recorded a real crash. Each draft carries them; a
   post that drops one because the new release reads better is the kind of claim this
   project has spent two releases avoiding. They are listed by name in `README.md` under
   *Never checked on hardware*.

## The one blocker this kit cannot clear: the demo clip needs your Pixel 7

The clip is scripted in `docs/LAUNCH.md` (60–90 seconds, airplane mode on, status bar
visible, media sounds enabled) and it has never been recorded, because no build
machine here has a device and there is no `/dev/kvm` for an emulator. Without it:

- Mastodon gets a text post, which is allowed but weaker.
- The subreddit and Lemmy posts work fine — they are text posts by design.

Record it straight from the app on the phone. Airplane mode is not decoration: it is
the strongest sentence in the clip. Note that the clip deliberately cannot show the
update row or *What's new*, since both need a connection to appear; that is a second,
shorter clip for later, not a reason to weaken the first one.

## What is not blocked, and costs an hour

GitHub itself: topics (`android`, `kotlin`, `jetpack-compose`, `dsp`, `offline-first`,
`sleep`), a repository description, and a few genuine `good first issue` entries. That
work has no rules to break, no account to age, and it is where contributors actually
arrive from. It can be done today, before any post.

## Posting is yours, and that is correct

These are your accounts, your reputation and your judgement about what your own
project sounds like. Nobody else should post as you, and no draft here should be
posted in a place whose rules forbid it. The drafts are here to be edited into your
voice, not pasted over it.

IzzyOnDroid is the one place worth a decision before you post anywhere: their policy
is explicitly against AI-generated code, and `docs/launch/izzyondroid-request.md`
explains what filing honestly would mean. Nothing has been submitted, and nothing
should be until you read that file.
