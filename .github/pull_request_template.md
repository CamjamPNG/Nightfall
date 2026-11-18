## What this changes

<!-- The behavior that is different after this PR. Not a diff summary. -->

## Why

<!-- The bug, the limitation, or the reason the old behavior was wrong. -->

## Verification evidence

Paste the commands you ran and the lines that show they passed. A ticked box with
no output is not evidence.

- [ ] `./gradlew :core:test`
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew :app:lintDebug` (lint has `abortOnError = true`, so clean here is real)
- [ ] `python3 -m unittest discover -s tools/tests -v` (if `tools/` changed)
- [ ] `./gradlew :app:connectedDebugAndroidTest` on a physical device (if `app/`,
      `AudioHost`, `PlaybackService`, focus handling or persistence changed)

```
<output of the checks that matter here>
```

## What I could not verify

<!-- Listening quality, a Bluetooth sleep mask, overnight battery, a specific
device. Write it down honestly; an unverified claim is the one thing this project
will not merge. -->

## Sound changes

<!-- Delete if not applicable. For a new or changed source: the catalogue id, the
renderer family, whether it is synthesis or a recording, and for a recording the
licence and that tools/prepare_audio.py regenerated the credits. -->

## Checklist

- [ ] One unit of work; no unrelated reformatting
- [ ] Existing tests untouched, or the PR explains why a test was wrong
- [ ] New or changed behavior has a test with a stated threshold
- [ ] The app still needs no network, and no permission was added
- [ ] No layer cap introduced
- [ ] No generated files committed (`build/`, `artifacts/`, `.audio-cache/`,
      `local.properties`, `*.apk`)
- [ ] No secrets, keystores or signing material
- [ ] `versionName`, `versionCode`, `CHANGELOG.md`, store changelog and the
      F-Droid recipe were not hand-edited — the release workflow owns them
