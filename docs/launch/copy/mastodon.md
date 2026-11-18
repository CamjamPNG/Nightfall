# Mastodon / Fedi

Fediverse norms are enforced by instance moderators, not by a global policy, so the
post has to satisfy the instance Camden posts from. Fosstodon is the natural home for
this project: a FOSS on Android app, posted from a FOSS-focused instance.

## Rule checked

Fosstodon's server rules, read from the instance API on **18 November 2026**
(`https://fosstodon.org/api/v1/instance/rules`), rule 8:

> **DO NOT post commercial promotions, or advertise** through posts that are
> exclusively links and/or which contain excessive hashtags. This includes repetitive
> self-promotion for profit. We don't want spam.

Three things follow, and they are the whole rule for this post:

1. **Not link-only.** A post that is just a URL breaks rule 8 literally.
2. **No hashtag walls.** Two or three accurate tags, not eight.
3. **Not repetitive.** One post, then answers. A second announcement about the same
   release reads as the "repetitive self-promotion" the rule names.

Fosstodon's rule 9 also matters for automation: "DO NOT use automated tools to post
without also monitoring and/or interacting from your account."

Verdict: **open**, and it is the lowest-risk channel on the list. It is also the
smallest unless the account already has followers — one post, an honest reply to
anyone who asks, and no boost requests to anyone.

## Draft

~480 characters, which fits a Post without a content warning. Add the demo clip if
you have recorded it; leave the post as text if you haven't, because a clip is only
worth showing once it exists.

> I've been building Nightfall: a sleep-sound mixer for Android with independent
> layers — noise, weather, seven field recordings, and ambient music generated on the
> phone. No account, no analytics, no ads. Two builds: the F-Droid one has no network
> permission at all, the GitHub one's only network use is its opt-in update check.
> The mixer engine is plain Kotlin with 42 tests, and the README lists what I haven't
> verified yet (a real-device overnight pass, mostly).
> MIT. https://github.com/CamjamPNG/Nightfall
>
> #FOSS #Android

Notes:

- Put the edited-honest limit in the post itself. On the fediverse the limitation
  line is the part people reply to, and it is also the part that makes the rest of
  the post credible.
- Do not post the same text on four instances, and do not ask anyone to boost.
- If the account is new, post something else first and let it age; a one-post account
  linking an app trips the same spam heuristics the rule describes.
