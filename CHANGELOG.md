# Changelog

## 1.7.1 — 2026-11-18

- test(screenshots): render the real screens to PNGs on the JVM, and make them the store set (#19)

## 1.7 — 2026-11-18

- sounds: stop the music repeating one four-chord phrase all night (#18)

## 1.6 — 2026-11-18

- feat(update): install new releases on their own, and show what changed (#15)
- sounds: stop the bundled recordings and the wind repeating on a fixed cycle (#17)

## 1.5.2 — 2026-11-18

- fix(build): enable R8 minification and resource shrinking (#16)

## 1.5.1 — 2026-11-18

- docs: rewrite the README as a front door whose every claim is checkable (#14)

## 1.5 — 2026-11-18

- feat(release): publish the F-Droid flavour APK beside the update options (#13)

## 1.4 — 2026-11-18

- sounds: level-match the catalogue and presets, distinguish names, fill preset gaps (#11)
- test: run the playback lifecycle scenarios on Robolectric, ready for CI (#8)

## 1.3.1 — 2026-11-18

- docs: launch kit with verified channel rules, an IzzyOnDroid request, and a no-JS landing page (#12)

## 1.3 — 2026-11-18

- feat(update): one-tap in-app updates on the GitHub channel, no updater in the F-Droid build (#2)

## 1.2.1 — 2026-11-18

- fix: type the output route and the library filter so wording decides nothing (#9)

## 1.2 — 2026-11-18

- sounds: loudness-matched levels, a measured true-peak ceiling and an allocation-free render path (#5)
- fix: show the installed version, name the real control, and stop counting wrong (#7)

## 1.1.3 — 2026-11-18

- docs: inventory every user-visible string and stage the localization extraction (#6)

## 1.1.2 — 2026-11-18

- docs: add contributor, security, roadmap and launch surface (#4)

## 1.1.1 — 2026-11-18

- Audit F-Droid metadata and record two-machine release reproduction (#3)

## 1.1 — 2026-11-18

- ui: rebuild the mixer layer list for per-row state and visible options (#1)

## 1.0.1.2 — 2026-11-12

- hotfix(release): verify signing identity before automatic publication

## 1.0.1.1 — 2026-11-12

- hotfix(release): accept formatted F-Droid version entries

## 1.0.1 — 2026-11-12

- build(release): add signed APK options and four-level release automation

## 1.0

- First public source release of the native offline sleep mixer.
- Independent layers, colored noise, stochastic weather and original ambient music.
- Local presets, JSON exchange, composition studio, sleep timers and smooth fades.
- Foreground media service and Android media controls.
- Three-minute minimum recording cycles with documented free audio licenses.
- Public release ID `io.github.camjampng.nightfall`; earlier development APKs used
  `dev.nightfall` and can export their mixes for import into this release.
- MIT code license with an explicitly voluntary commercial-courtesy request.
