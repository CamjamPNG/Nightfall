# Security policy

Nightfall is a single-purpose offline Android app: it mixes locally generated
audio and locally bundled recordings and plays them. It has no account system,
no analytics, no advertising, no purchases, no microphone access and **no
`INTERNET` permission**. There is no server component, no cloud sync and no
remote configuration. `tools/check_release.py` fails the release if an
`INTERNET`, `RECORD_AUDIO` or advertising-ID permission appears in the manifest.

That removes most of the usual attack surface, and this document says exactly
what remains rather than implying the project is too small to have one.

## What is actually in scope

1. **Import parsing.** `PresetJson.decodeStream` reads a JSON mix file the user
   chooses through Android's file picker. It validates sound IDs, layer ID
   uniqueness, name length and numeric ranges, and rejects non-finite values.
   A crafted file that crashes the app, corrupts saved state, or gets past those
   checks is a real bug worth reporting.
2. **Saved-state handling.** Mixes, presets and settings are stored in local
   DataStore as versioned JSON and restored at startup. A file or state that
   bricks startup, or that restores a stale timer deadline as a live one, is in
   scope.
3. **Bundled asset handling.** The seven field recordings are memory mapped from
   the APK as raw PCM and decoded by the app's own `Pcm16Data`. A path that
   reads outside a mapping, or a missed bounds check on a short or truncated
   asset, is in scope.
4. **Release integrity and signing.** Release signing is the update path: Android
   replaces an installed app only when the new APK carries the same certificate.
   The pinned SHA-256 of the release certificate is in
   `docs/release-signing-certificate.sha256` and the public certificate is in
   `docs/nightfall-release-certificate.pem`. The release workflow refuses to
   publish unless the key it built with matches that pin. Anything that would let
   someone else publish to the same application id, replace a published asset,
   move a tag, or weaken that check is the most severe class of bug in this
   project.
5. **Updates.** No build of this app ships an in-app updater today: you install a
   new APK yourself. If one is ever added, Android still requires the user to
   approve every install, and the app must never silently download or install an
   update, add network access on its own, or follow a redirect to an unexpected
   host. An update path that bypasses the pinned signer, or that fetches
   executable code, would be in scope.

## What is out of scope

- **Physical access, a rooted device, or a malicious app with equivalent
  privileges.** The app's data directory and any exported JSON are readable by
  someone who already controls the device.
- **Exported mix files are plain text.** This is documented in `PRIVACY.md`, not
  a vulnerability. Do not put secrets in a preset name.
- **Bugs in the Android platform, AndroidX, Media3, Kotlin or Gradle.** Report
  those upstream. If Nightfall's use of a library turns the bug into a Nightfall
  problem, that use is in scope.
- **Battery, Doze, manufacturer task-killer and Bluetooth-stack misbehavior.**
  All-night playback is explicitly not guaranteed; see `docs/TESTING.md`. Slow
  timers under aggressive battery managers are a known, documented limitation,
  not a vulnerability.
- **Acoustic, sleep or health claims.** The app makes none, and none are
  asserted here.
- **Permissions the user grants.** Notification permission, foreground media
  playback and a partial wake lock held only while playing are all intended.

## Reporting privately

Please do not open a public issue for a security problem, and do not attach
exploit code, keystores, credentials or private recordings to anything public.

1. Use GitHub's private reporting form on the repository's **Security** tab
   ("Report a vulnerability") when it is available. That keeps the report
   private and gives us a thread to work in.
2. If that form is not available to you, contact the maintainer through
   [Camden's GitHub profile](https://github.com/CamjamPNG) and say only that you
   have a security report and want a private channel. Details go in that
   channel, not in the first message.

A useful report includes: the release version from the APK file name or the
release page, the Android version and device, which build channel you installed from
(a GitHub release, or F-Droid), the exact steps, what you observed, and the
impact as you understand it. Say whether it needs a malicious imported file, a
particular device state, or local access — that changes the severity a lot.

## What to expect

This is a maintained but small project with one primary maintainer. There is no
bug bounty and no guaranteed response time, and this document will not pretend
otherwise. The intent is: acknowledge a report, tell you honestly whether it is
in scope and when a fix is likely, and credit you in the release notes if you
want credit. Fixes ship through the normal release convention — a security fix
is a `hotfix:` or `fix:` commit and a signed release — because there is no
silent update path to patch anyone out of band.

If a report is declined, you get the reason. "Not in scope" above is the honest
list, not a way to close things.

## Verifying a release yourself

Every release publishes six signed APKs — five from the GitHub channel and one
from the F-Droid channel — a checksum file, a release manifest and the public
certificate. The verification records this project keeps are in
`docs/verification/`: `release-checks.json`, `dependency-audit.json`,
`reproducibility.json`, the F-Droid scanner output, and the resolved dependency
inventory. `docs/TESTING.md` says which of those results are machine-checked and
which are not.

You can check a downloaded APK against the pinned identity yourself:

```sh
apksigner verify --print-certs Nightfall-<version>-arm64-v8a.apk
sha256sum -c SHA256SUMS.txt
```

The certificate fingerprint from `apksigner` must match the value in
`docs/release-signing-certificate.sha256`. If it does not, do not install the
file and report where you got it.
