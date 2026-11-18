# Nightfall maintenance

The owner requests published GitHub APK releases for updates. Preserve offline
operation, signing continuity and the existing F-Droid-compatible MIT license.

Use commit intent to select the version level; the biggest level in a batch wins:

- `hotfix: ...`: urgent narrow fix; 1.0 → 1.0.0.1.
- `fix: ...` or ordinary maintenance: patch; 1.0 → 1.0.1.
- `feat: ...`, `sounds: ...`, `ui: ...`, `fix(major): ...`: minor; 1.0 → 1.1.
- `type!: ...` or a `BREAKING CHANGE:` body: major; 1.0 → 2.0.

Read `docs/RELEASING.md` before publishing. Use the release workflow, or its
documented local recovery steps when necessary. Keep Android versionCode
monotonically increasing and synchronize F-Droid/store metadata. Do not move
existing release tags or replace published APK assets. Release only source that
passed the applicable checks; preserve the hardware-test limitations in notes.

Never commit private signing material or generate a new signing key per update.
The pinned public certificate identifies the GitHub update channel. Automatic
signed publishing requires the existing key in repository Actions secrets;
report missing access honestly. Changes are published by GitHub Actions after
pushes. Two channels are built from one source. Two pacing rules govern the
`github` channel's requests: every one, including a check the user starts,
waits at least fifteen minutes since the previous attempt, which is recorded
before the request so a failure counts too, and the app's own check
additionally waits six hours since the last successful answer, so a failed
attempt never spends that window. It may download the APK for this device and
install it through `PackageInstaller` on its own, which is on by default in
the `github` channel and can be turned off, and it always waits for playback
to stop before it installs so an update never silences a night's audio. The
`fdroid` channel carries no updater, no network code and no network permission
at all. Both stay fully usable with no connection.
