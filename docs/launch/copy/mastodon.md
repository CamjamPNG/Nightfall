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

Measured at 527 characters raw, **489 as Mastodon counts it** (a link costs 23
characters, not its real length), so it fits a default 500-character instance with eleven
to spare. The budget is why the privacy line is one clause rather than a paragraph: the
full statement, including the diagnostics journal, is on the repository page the second
line points at. Recount it by pasting the paragraph into a character counter, not by
trusting this line — the previous draft in this file was 521 and claimed to be ~480, which
no default instance would have accepted.

The post is complete without a clip, which is what makes it postable today. Add the
demo clip when it exists; leave the post as text until then, because a clip is only worth
showing once it is real, and this is the only channel in the kit that wants one.

> Nightfall: a sleep-sound mixer for Android. 106 sounds, independent layers, no account,
> no ads. Type "rain on a tent" and it builds the mix on the phone; play/pause is on the
> lock screen and a Quick Settings tile, and the build with no network permission is on
> the releases page. Eight clips you can hear first, no install:
> https://github.com/CamjamPNG/Nightfall/tree/main/docs/samples
>
> 11.8 MB, 127 tests, and a README listing what's unverified — no overnight run on a
> device yet, and no ear has heard it on one.
>
> #FOSS #Android

Notes:

- Put the edited-honest limit in the post itself. On the fediverse the limitation
  line is the part people reply to, and it is also the part that makes the rest of
  the post credible.
- The one link is a folder of audio files inside the repository — not a download page,
  not a store listing. If a mod reads rule 8's "exclusively links" clause strictly, the
  post still has two paragraphs of prose before and after it.
- Do not post the same text on four instances, and do not ask anyone to boost.
- If the account is new, post something else first and let it age; a one-post account
  linking an app trips the same spam heuristics the rule describes.
