# Localization

Every string Nightfall shows a person used to be a Kotlin literal in `app/src/main/java/dev/nightfall/`.
The app's own chrome is now in `app/src/main/res/values/strings.xml` and `plurals.xml`: 278 keys,
every one of them used, extracted against `main` at `1ffbee1` and counted again at this branch's head.
The count is `grep -c '<string name=\|<plurals name='` over the two files, and §2 below is the
inventory as it stood when the extraction landed: the keys added afterwards — the home-screen widget's,
the diagnostics journal's, and the endless-night switch's — are counted but only the switch is listed
(§2.23). What still ships as English in code
is the catalogue — 106 names, 10 categories, 15 preset names in
`core/src/main/kotlin/dev/nightfall/core/Library.kt` — plus two defaults that are written into saved
data rather than drawn (`Your soundscape`, `My night mix`). Both are listed in §5 as stages 5 and 6
and both need work outside `app/`.

There is still no `res/values-<language>/`, no `android:localeConfig`, and no per-app language
selection. English is the only locale, and this document is the inventory, the judgement calls, and
what is left of the extraction plan: the sections below are the record of the tree as it was, kept
because the reasoning about what must not be extracted still governs the keys that now exist.

The inventory was taken against `main` at `0df94ac` (v1.1, versionCode 5), and the line column is
valid at that commit and nowhere else. Both UI files change often and this list will drift within a
release or two. Keys and file names are the stable reference: locate a string by its key, or by
searching its file for the quoted text, and read the line as a hint that was true once. Anyone acting
on a stage in §5 works from the key, never from the number.

## 1. What counts as a user-visible string

Included:

- text drawn by Compose (`Text`, `label`, `placeholder`, `supportingText`), including text inside a
  dialog, a dropdown, a chip and a snackbar;
- accessibility text — `contentDescription`, `stateDescription` and `CustomAccessibilityAction`
  labels. TalkBack says these out loud, so a sighted review will never catch them being untranslated
  or stale;
- the media notification: channel name, title, text, and the Media3 metadata shown on the lock
  screen and on Bluetooth head units;
- the launcher label in `AndroidManifest.xml`;
- the catalogue: 106 sound names, 10 category names, the per-kind descriptions, and the 15 built-in
  preset names. They are content rather than chrome, but a person reads them off the screen, so an
  inventory that skipped them would be a lie about its own completeness.

Excluded, with the reason: §3.

## 2. Inventory

252 user-visible surface strings and 129 catalogue strings, 381 in total:

| Surface | Strings |
| --- | --- |
| App shell, dialogs, Home, layer row and options panel (§2.1-§2.12, §2.23) | 126 |
| Library, Presets, Studio, Settings (§2.13-§2.16) | 103 |
| Snackbars, errors, notification, waveform a11y, launcher label (§2.17-§2.21) | 23 |
| Catalogue: 106 sound names, 10 categories, 6 descriptions, 15 preset names (§2.22) | 137 |

Keys follow `screen.feature.role`. `home`, `library`, `presets`, `studio`, `settings` match the five
navigation tabs; `common.*` is shared vocabulary; `error.*` and `notification.*` are surfaces with no
screen of their own. Where one literal is reused at several call sites, it is one key, and the table
says so.

### 2.1 App shell and navigation — `NightfallScreen.kt:167-190`

The five tabs are built from a list of pairs, so the labels are literals inside a `listOf` rather
than in a `Text` call. Extracting them means the list must resolve five resources before it is built.

| Key | Line | Text |
| --- | --- | --- |
| `nav.home` | 169 | Home |
| `nav.library` | 170 | Library |
| `nav.presets` | 171 | Presets |
| `nav.studio` | 172 | Studio |
| `nav.settings` | 173 | Settings |

### 2.2 Save and rename dialog — `NightfallScreen.kt:272-302`

| Key | Line | Text |
| --- | --- | --- |
| `home.save_dialog.title.new` | 277 | Save your mix |
| `home.save_dialog.title.rename` | 277 | Rename preset |
| `home.save_dialog.field.name` | 283 | Mix name |
| `common.action.save` | 298 | Save |
| `common.action.cancel` | 301 | Cancel |

`common.action.done` is reused at lines 349, 372 and 1137, and `common.action.save` at 298 only.

### 2.3 Gentle transitions dialog — `NightfallScreen.kt:304-350`

| Key | Line | Text |
| --- | --- | --- |
| `home.fade_dialog.title` | 307 | Gentle transitions |
| `home.fade_dialog.body` | 311 | New layers and preset changes fade in and out using each layer’s fade setting. |
| `home.fade_dialog.fade_duration.label` | 320 | Timer fade duration |
| `home.fade_dialog.fade_duration.value` | 328 | `{seconds} seconds` |
| `home.fade_dialog.action.fade_out_now` | 346 | Fade out now |
| `common.action.done` | 349 | Done |

### 2.4 Audio credits dialog — `NightfallScreen.kt:351-373`

| Key | Line | Text |
| --- | --- | --- |
| `settings.credits_dialog.title` | 354 | Audio credits & licenses |

The body is the contents of `assets/AUDIO_LICENSES.md`, read at line 364. That is a document, not a
string: it stays in one language, because it mirrors `LICENSES/` and `THIRD_PARTY_NOTICES.md` and
because mis-stating a licence is worse than leaving it in English.

### 2.5 Home tab — `NightfallScreen.kt:400-511`

| Key | Line | Text |
| --- | --- | --- |
| `home.title` | 418 | Nightfall |
| `home.action.presets` | 435 | Presets |
| `home.action.open_settings` | 443 | Open settings |
| `home.master_volume.label` | 455 | Master volume |
| `home.section.soundscape` | 467 | Your soundscape |
| `home.section.layer_summary` | 473 | `{count} layers · hold the grip to reorder` |
| `home.action.add_sound` | 481 | Add sound |
| `home.action.add_another_sound` | 497 | Add another sound |
| `home.footer.motto` | 502 | OFFLINE BY NATURE  ·  FREE FOREVER |

`home.action.presets` (435) is the accessibility label of the header icon and duplicates `nav.presets`;
one key serves both. `home.title` (418) is the product name next to the moon icon and has no
translation — it is the same key as the launcher label, `app_name`.

Case is part of the design here: `home.title` is 28 sp, `home.footer.motto` is 9 sp with 1.5 sp
letter-spacing and its own double spaces around the `·`. Russian, Greek and CJK have no small-caps
convention, and German nouns keep their capitals mid-sentence, so the "all caps" reading of the motto
and of `home.now_playing.playing` is an English typographic choice, not a semantic one.

### 2.6 Now playing card — `NightfallScreen.kt:513-613`

| Key | Line | Text |
| --- | --- | --- |
| `home.now_playing.playing` | 530 | NOW PLAYING |
| `home.now_playing.idle` | 530 | READY WHEN YOU ARE |
| `home.transport.play` | 545 | Play soundscape |
| `home.transport.pause` | 545 | Pause soundscape |
| `common.output.system_default` | 521, `AudioHost.kt:145`, `NightfallApp.kt:31` | System audio output |
| `home.status.active_layers` | 587 | `{count} layers` |
| `home.timer.all_night` | 596, 598 | All night |
| `home.timer.fading` | 597 | Fading |
| `home.timer.content_description` | 605 | Sleep timer |

Line 521 passes `"System audio output"` as the *initial value* of the collected state, before the
first real route arrives, so the fallback is run-time text, not a compile-time label.

### 2.7 Quick actions — `NightfallScreen.kt:616-631`

| Key | Line | Text |
| --- | --- | --- |
| `home.action.unmute_all` | 620 | Unmute |
| `home.action.mute_all` | 620 | Mute all |
| `home.action.stop_all` | 626 | Stop all |
| `home.action.fade` | 627 | Fade |
| `home.action.save_mix` | 628 | Save mix |
| `home.action.reset` | 629 | Reset |

These are 10 sp labels under 19 dp icons, five across a phone. German and Finnish versions of these
words are much longer than "Fade" and "Reset"; the row has `widthIn(min = 48.dp)` and no wrap
allowance, so this is the single most likely place for a translated build to look broken.

### 2.8 Preset strip and capacity warning — `NightfallScreen.kt:634-665`

| Key | Line | Text |
| --- | --- | --- |
| `home.presets.all` | 650 | All presets → |
| `home.presets.all.content_description` | 651 | See all presets |
| `home.capacity_warning` | 661 | This mix is approaching the device’s audio capacity. Reduce music effects or layers if you hear gaps. Render load `{load}%` · `{underruns}` underruns |

`All presets →` hardcodes a right arrow. In a right-to-left locale it must become `←`, which is a
`res/values-ldrtl` variant or a mirrored glyph, not a translated word.

### 2.9 Layer row — `NightfallScreen.kt:687-847`

| Key | Line | Text |
| --- | --- | --- |
| `home.layer.reorder.content_description` | 724 | Reorder `{name}` |
| `home.layer.reorder.move_up` | 727 | Move up |
| `home.layer.reorder.move_down` | 731 | Move down |
| `home.layer.enable.content_description` | 771 | Enable `{name}` |
| `home.layer.volume.label` | 775 | Volume |
| `home.layer.volume.spoken_name` | 778 | `{name}` volume |
| `home.layer.mute.label` | 784 | Mute |
| `home.layer.mute.content_description` | 787 | Mute `{name}` |
| `home.layer.solo.label` | 794 | Solo |
| `home.layer.solo.content_description` | 797 | Solo `{name}` |
| `home.layer.remove.content_description` | 808 | Remove `{name}` |
| `home.layer.options.content_description` | 818 | Options for `{name}` |
| `home.layer.options.state.expanded` | 820 | Expanded |
| `home.layer.options.state.collapsed_defaults` | 821 | Collapsed, all defaults |
| `home.layer.options.state.collapsed_adjusted` | 822 | Collapsed, `{count}` adjusted |
| `home.layer.options.label.default` | 829 | Options |
| `home.layer.options.label.adjusted` | 829 | Options · `{count}` adjusted |

Note the order dependence in the spoken names: `{name} volume` puts the user's layer name first. A
language that marks the genitive on the head noun ("Volume de Ruisseau" / "Ruisseau 音量") needs the
placeholder position to be the translator's choice, which is why these are format strings rather than
concatenation.

### 2.10 Layer options panel — `NightfallScreen.kt:848-1005`

| Key | Line | Text |
| --- | --- | --- |
| `home.layer.group.placement` | 856 | Placement |
| `home.layer.pan.label` | 858 | Pan |
| `home.layer.pan.value.center` | 862 | Center |
| `home.layer.pan.value.side` | 863 | `{percent}% left` / `{percent}% right` |
| `home.layer.pan.spoken_name` | 864 | `{name}` pan |
| `home.layer.width.label` | 867 | Stereo width |
| `home.layer.width.spoken_name` | 870 | `{name}` stereo width |
| `home.layer.group.tone` | 873 | Tone |
| `home.layer.bass.label` | 875 | Bass |
| `common.unit.decibels` | 879, 887, 895 | `{value}` dB |
| `home.layer.bass.spoken_name` | 880 | `{name}` bass |
| `home.layer.mid.label` | 883 | Mid |
| `home.layer.mid.spoken_name` | 888 | `{name}` mid |
| `home.layer.treble.label` | 891 | Treble |
| `home.layer.treble.spoken_name` | 896 | `{name}` treble |
| `home.layer.brightness.label` | 899 | Brightness |
| `home.layer.brightness.spoken_name` | 902 | `{name}` brightness |
| `home.layer.tilt.label` | 905 | Spectral tilt |
| `common.unit.ratio` | 909 | `%.1f`, no unit |
| `home.layer.tilt.spoken_name` | 910 | `{name}` spectral tilt |
| `home.layer.group.filters` | 913 | Filters |
| `home.layer.high_pass.label` | 915 | High-pass filter |
| `common.unit.hertz` | 919, 927 | `{value}` Hz |
| `home.layer.high_pass.spoken_name` | 920 | `{name}` high-pass filter |
| `home.layer.low_pass.label` | 923 | Low-pass filter |
| `home.layer.low_pass.spoken_name` | 928 | `{name}` low-pass filter |
| `home.layer.group.texture` | 932 | Texture |
| `home.layer.beat_frequency.label` | 935 | Beat frequency |
| `common.unit.hertz_one_decimal` | 938 | `%.1f` Hz |
| `home.layer.beat_frequency.spoken_name` | 939 | `{name}` beat frequency |
| `home.layer.intensity.label` | 943 | Intensity |
| `home.layer.intensity.spoken_name` | 946 | `{name}` intensity |
| `home.layer.event_frequency.label` | 950 | Event frequency |
| `home.layer.event_frequency.spoken_name` | 953 | `{name}` event frequency |
| `home.layer.modulation.label` | 956 | Slow modulation |
| `home.layer.modulation.spoken_name` | 959 | `{name}` slow modulation |
| `home.layer.group.timing` | 963 | Timing |
| `home.layer.fade.label` | 965 | Fade in and out over |
| `common.unit.seconds_one_decimal` | 969 | `%.1f` s |
| `home.layer.fade.spoken_name` | 970 | `{name}` fade duration |
| `home.layer.stop_after.label` | 973 | Stop this layer after |
| `home.layer.stop_after.value.all_night` | 977 | All night |
| `common.unit.minutes` | 978, 988 | `{value}` min |
| `home.layer.stop_after.spoken_name` | 979 | `{name}` stop time |
| `home.layer.storm_transition.label` | 983 | Storm becomes rain over |
| `home.layer.storm_transition.value.off` | 987 | Off |
| `home.layer.storm_transition.spoken_name` | 989 | `{name}` storm transition |
| `home.layer.action.edit_in_studio` | 999 | Edit composition in Studio |

`home.layer.stop_after.value.all_night` (977) and `home.timer.all_night` (596) are the same two
words meaning two different things — "no time limit" in both cases — and should be one key.

### 2.11 Layer subtitle and option summary — `NightfallScreen.kt:1022-1067`

| Key | Line | Text |
| --- | --- | --- |
| `home.layer.subtitle.recording` | 1054 | Field recording |
| `home.layer.subtitle.music` | 1055 | Generative music |
| `home.layer.subtitle.binaural` | 1056 | Binaural tones |
| `home.layer.subtitle.noise` | 1057 | Noise texture |
| `home.layer.subtitle.clock` | 1058 | Ticking clock |
| `home.layer.subtitle.hum` | 1059 | Hum |
| `home.layer.subtitle.generic` | 1061 | `{Kind}` texture |

The last one builds its text from a source-kind identifier
(`sound.kind.replaceFirstChar { it.uppercase() }`), so the English word "Rain"/"Wind"/"Fire" arrives
in the middle of a sentence. It cannot be translated as a format string; it needs one key per kind.

The fifteen short tags in `adjustedOptions` (1027-1041) are displayed joined by `"  ·  "` in the
collapsed summary, and each is also the value behind the "N adjusted" count:
`pan`, `stereo width`, `high-pass`, `low-pass`, `bass`, `mid`, `treble`, `brightness`, `tilt`,
`intensity`, `events`, `modulation`, `fade`, `stop time`, `storm transition`
— keys `home.layer.option_tag.<name>`, fifteen strings at lines 1027-1041.

### 2.12 Sleep timer dialog — `NightfallScreen.kt:1069-1139`

| Key | Line | Text |
| --- | --- | --- |
| `home.timer_dialog.title` | 1076 | Sleep timer |
| `home.timer_dialog.body` | 1080 | Audio fades gently after the countdown. Pausing playback pauses the timer. |
| `home.timer_dialog.all_night` | 1098 | All night |
| `home.timer_dialog.preset_minutes` | 1098 | `{minutes} min` |
| `home.timer_dialog.custom.label` | 1106 | Custom minutes (1–1440) |
| `home.timer_dialog.action.resume` | 1118 | Resume timer |
| `home.timer_dialog.action.pause` | 1118 | Pause timer |
| `home.timer_dialog.action.set` | 1134 | Set timer |

`home.timer_dialog.title` duplicates `home.timer.content_description` (605): one key can serve both.

### 2.13 Library — `Screens.kt:61-283`

| Key | Line | Text |
| --- | --- | --- |
| `library.count_in_mix` | 55 | `{count}` in your mix |
| `library.search.placeholder` | 116 | Find your sound |
| `library.search.content_description` | 121 | Search sounds |
| `library.category.all` | 130 | All |
| `library.title` | 143 | Sound library |
| `library.subtitle` | 144 | A little of everything. As many layers as you like. |
| `library.empty` | 151 | No sounds match your search. |
| `library.empty.action` | 161 | Show all sounds |
| `library.disclaimer` | 184 | Environmental synthesis is an interpretation of a sound, not a field recording. Gray, green, black and velvet are labeled configurable presets. All music is generated on your device. |
| `library.section.collapsed` | 202, 203 | Collapsed |
| `library.section.expanded` | 202, 203 | Expanded |
| `library.section.available` | 220 | `{count}` available |
| `library.item.subtitle.recording` | 258 | Field recording · offline |
| `library.item.subtitle.unavailable` | 259 | Recording not bundled |
| `library.item.subtitle.music` | 260 | Original generative music |
| `library.item.action.add` | 272 | Add `{name}` |

`library.category.all` is the sharpest structural problem in the file, and it has since been solved
the way this document said it had to be: `"All"` used to be both a button label and the filter
sentinel, so translating it broke the comparison. The chip's value is `null` for every category, and
the word is only ever a label. `library.disclaimer` also names four catalogue entries ("Gray, green,
black and velvet") that must stay recognisable against the names in §2.22, so this sentence has to be
translated with the catalogue in front of the translator.

`library.section.explore` ("Explore sounds") was the one label over a single 98-row list. The list is
now grouped by category with a header per section, so that key was deleted rather than left unused,
and the three keys added in its place are the empty state's way out and a section's two states, which
TalkBack reads as the chip's `stateDescription`.

### 2.14 Presets — `Screens.kt:150-298`

| Key | Line | Text |
| --- | --- | --- |
| `presets.title` | 164 | Find your night |
| `presets.subtitle` | 164 | Carefully composed. Always yours to change. |
| `presets.action.import` | 170 | Import JSON |
| `presets.filter.favorites` | 175 | Favorites |
| `presets.section.user` | 183 | Your mixes |
| `presets.section.user_count` | 183 | `{count}` saved |
| `presets.section.builtin` | 190 | Nightfall collection |
| `presets.section.builtin_count` | 190 | `{count}` soundscapes |
| `presets.card.layer_count` | 218 | `{count}` layers |
| `presets.card.favorite.content_description` | 239 | Favorite `{name}` |
| `presets.card.menu.content_description` | 246 | Options for `{name}` |
| `presets.card.menu.duplicate` | 250 | Duplicate |
| `presets.card.duplicate_suffix` | 252 | `{name}` copy |
| `presets.card.menu.export` | 257 | Export JSON |
| `presets.card.menu.rename` | 265 | Rename |
| `presets.card.menu.delete` | 272 | Delete |
| `presets.card.play` | 294 | Play soundscape |

`presets.card.duplicate_suffix` (252) is a translated pattern written into a saved preset name, and
`presets.card.layer_count` (218) counts a slice. See §4.5 for why the first of those is a real
hazard rather than a formatting detail.

### 2.15 Studio — `Screens.kt:300-572`

| Key | Line | Text |
| --- | --- | --- |
| `studio.title` | 329 | Sound studio |
| `studio.subtitle.editing` | 330 | Shaping `{name}` |
| `studio.subtitle.new` | 331 | A quiet composition, made by you. |
| `studio.intro.title` | 347 | Original. Evolving. Endless. |
| `studio.intro.body` | 353 | Seeded chords and gentle melodies develop together. Save to your mixer to hear your composition. |
| `studio.music_type.label` | 362 | Music type |
| `studio.section.foundation` | 381 | Musical foundation |
| `studio.tuning.option` | 396 | A4 = `{hz}` Hz |
| `studio.tuning.disclaimer` | 403 | Tuning changes musical pitch. Neither tuning is established to improve sleep or produce clinical benefits. |
| `studio.key.label` | 411 | Key |
| `studio.scale.label` | 417 | Scale |
| `studio.scale.major` | 418, 419 | Major |
| `studio.scale.minor` | 418, 419 | Minor |
| `studio.tempo.label` | 428 | Tempo |
| `common.unit.bpm` | 432 | `{value}` BPM |
| `studio.density.label` | 435 | Note density |
| `studio.complexity.label` | 438 | Harmonic complexity |
| `studio.instrument_texture.label` | 445 | Instrument texture |
| `studio.section.character` | 452 | Space & character |
| `studio.bass.label` | 453 | Bass |
| `studio.treble.label` | 455 | Treble |
| `studio.reverb.label` | 457 | Reverb |
| `studio.delay.label` | 458 | Stereo delay |
| `studio.randomness.label` | 461 | Melodic randomization |
| `studio.melody.title` | 467 | Melody |
| `studio.melody.description` | 467 | Let quiet notes wander over the chords. |
| `studio.percussion.title` | 474 | Low-volume percussion |
| `studio.percussion.description` | 475 | A restrained pulse for lo-fi compositions. |
| `studio.seed.label` | 487 | Composition seed |
| `studio.seed.supporting` | 488 | The same seed and settings reproduce the same notes. |
| `studio.action.new_variation` | 497 | New variation |
| `studio.snackbar.updated` | 531 | Composition updated |
| `studio.snackbar.added` | 532 | Composition added to your mixer |
| `studio.action.update_layer` | 541 | Update mixer layer |
| `studio.action.add_to_mixer` | 541 | Add composition to mixer |
| `studio.preset.name` | 549 | Studio · `{name}` |
| `studio.snackbar.saved_preset` | 557 | Composition saved as a preset |
| `studio.action.save_as_preset` | 561 | Save composition as a preset |
| `studio.footer` | 566 | Adjust midrange, filters and independent fade timing in the layer’s Effects controls on Home. Save the whole mix as a preset at any time. |
| `studio.instrument_texture.soft_pad` | 576 | Soft pad |
| `studio.instrument_texture.felt_piano` | 576 | Felt piano |
| `studio.instrument_texture.warm_analog` | 576 | Warm analog |
| `studio.instrument_texture.bells_bowls` | 576 | Bells & bowls |
| `studio.instrument_texture.soft_strings` | 576 | Soft strings |

`studio.bass.label` (453) and `home.layer.bass.label` (875) are the same word in two places; one key
serves both. `studio.footer` (566) names a control that no longer exists — the options panel is
called `Options` now, not `Effects` — so it is already stale in English and needs a decision rather
than a translation. `studio.preset.name` (549) is a translated pattern written into a saved preset
name; see §4.5.

### 2.16 Settings — `Screens.kt:633-758`

| Key | Line | Text |
| --- | --- | --- |
| `settings.title` | 647 | Make yourself at home |
| `settings.subtitle` | 647 | Quiet by design. Private by default. |
| `settings.section.appearance` | 648 | Appearance |
| `settings.light_theme.title` | 650 | Light theme |
| `settings.light_theme.description` | 650 | Midnight navy is the default. |
| `settings.reduced_motion.title` | 655 | Reduced motion |
| `settings.reduced_motion.description` | 655 | Keep the waveform still. |
| `settings.section.sound` | 659 | Sound behavior |
| `settings.ducking.title` | 662 | Thunder ducking |
| `settings.ducking.description` | 663 | Gently lower music during thunder events. |
| `settings.weather.title` | 671 | Dynamic weather |
| `settings.weather.description` | 672 | Let weather intensity evolve slowly. |
| `settings.slow_variation.title` | 680 | Slow variation |
| `settings.slow_variation.description` | 681 | Subtle texture changes over long sessions. |
| `settings.low_distraction.title` | 689 | Low-distraction mode |
| `settings.low_distraction.description` | 690 | Soften thunder and other sudden events. |
| `settings.pause_on_disconnect.title` | 698 | Pause on disconnect |
| `settings.pause_on_disconnect.description` | 699 | Pause when Bluetooth or wired audio disconnects. Turn off to allow the system to reroute audio. |
| `settings.timer_fade.label` | 707 | Sleep timer fade |
| `settings.timer_fade.value` | 711 | `{seconds} seconds` |
| `settings.section.device` | 714 | On this device |
| `settings.device.diagnostics` | 717 | four-line block, see below |
| `settings.limits.body` | 725 | There is no software layer limit. If rendering approaches 65% of the buffer budget or underruns accumulate, Nightfall warns you. Music layers use more processing than noise. Actual battery life and all-night playback depend on Android and device power policies. |
| `settings.section.data` | 730 | Your data & audio |
| `settings.data.body` | 733 | Everything runs on your device. No account, advertising, analytics, tracking, purchases or network permission. Mixes and settings are stored locally. Keep listening levels comfortable; percentages describe layer gain, not acoustic loudness. |
| `settings.action.export` | 740 | Export current mix as JSON |
| `settings.action.credits` | 745 | Audio credits & open-source licenses |
| `settings.footer` | 750 | `Nightfall 1.0` — Free forever. A small space for a quieter night. |

`settings.device.diagnostics` (717) is one literal containing three `\n` and four independently
number-bearing lines:

```
Output: $output
Audio render load: $load% of buffer time
AudioTrack underruns: $underruns
48 kHz · stereo · floating-point PCM
```

It has to become four strings, or one format string with five positional arguments. The last line is
a specification — 48 kHz stereo float — so it is a technical statement, not prose: a translator
should not be able to turn "48 kHz" into something else, which is an argument for making it
untranslatable and keeping only the three labels around it.

`settings.footer` (750) already contains a bug this inventory found: it says **Nightfall 1.0** while
`app/build.gradle.kts` has `versionName = "1.1"`. The version is pasted into translatable prose, so
it will keep going stale in every locale. It should be `%1$s` fed from `BuildConfig.VERSION_NAME`
before it is extracted at all.

`settings.data.body` (733) is the privacy claim in the product. "No account, advertising, analytics,
tracking, purchases or network permission" is a list a translator can easily soften — a translated
"keine Konten, Werbung…" is fine, a translated "kaum Werbung" is a false claim. This string and
`settings.limits.body` (725) are the two places where a bad translation is a product-integrity
problem, not a copy problem.

### 2.17 Snackbars — `NightfallScreen.kt:99-135`

| Key | Line | Text |
| --- | --- | --- |
| `home.snackbar.exported` | 112 | Mix exported |
| `home.snackbar.export_failed` | 114 | Export failed: `{detail}` |
| `presets.snackbar.imported` | 130 | Imported `{name}` |
| `presets.snackbar.import_failed` | 132 | Import failed: `{detail}` |

### 2.18 Runtime and playback errors — `NightfallApp.kt`, `AudioHost.kt`

These reach the same snackbar as §2.17, from a `LaunchedEffect` at `NightfallScreen.kt:137-149`.

| Key | Line | Text |
| --- | --- | --- |
| `error.restore_failed` | `NightfallApp.kt:72` | Could not restore saved mix: `{detail}` |
| `error.save_failed` | `NightfallApp.kt:88` | Saving failed: `{detail}` |
| `error.playback_start_failed` | `NightfallApp.kt:165` | Playback could not start: `{detail}` |
| `error.background_failed` | `AudioHost.kt:96` | Background playback could not start: `{detail}` |
| `error.audio_stopped` | `AudioHost.kt:269` | Audio stopped: `{detail}` |
| `error.audio_focus_unavailable` | `AudioHost.kt:104` | Audio focus is unavailable. Try again after the other audio ends. |
| `output.bluetooth` | `AudioHost.kt:141` | Bluetooth · `{product}` |
| `output.wired` | `AudioHost.kt:143` | Wired headphones |
| `output.speaker` | `AudioHost.kt:144` | Built-in speaker |
| `common.output.system_default` | `AudioHost.kt:145` | System audio output |

`{detail}` is an exception message (`e.message`), which is never translated and sometimes not
English either. Every one of those six strings therefore has a localized prefix in front of an
unlocalized fragment. That is acceptable — a diagnostic is better than "something went wrong" — but
it has to be a deliberate decision recorded here, not an accident discovered in a German build.

`output.bluetooth` is also the origin of a real structural problem: `NightfallScreen.kt:562` decides
which icon to draw with `output.startsWith("Bluetooth")`. Translating "Bluetooth" makes the icon
wrong in every non-English locale. See §3.1.

### 2.19 Media notification and system surfaces — `PlaybackService.kt`

| Key | Line | Text |
| --- | --- | --- |
| `notification.channel.playback` | 57 | Nightfall playback |
| `notification.preparing` | 65 | Preparing your soundscape |
| `notification.artist.fading` | 132 | ` · Fading out` |
| `notification.artist.remaining` | 134 | ` · {minutes}:{seconds} remaining` |
| `notification.artist.all_night` | 135 | ` · All night` |
| `notification.artist.count` | 142 | `{count} layers` |

`notification.channel.playback` is not drawn by the app: Android shows it in Settings → Apps →
Notifications, and it is fixed at the moment the channel is created, so a later translation never
reaches an installed device. It is also not a Compose string, so it needs `context.getString`
rather than `stringResource`.

Lines 132-135 build the Media3 "artist" line by appending an English fragment to the layer count.
The three fragments each start with their own `" · "` separator and are concatenated at line 142, so
the result is 4 concatenated pieces with a hand-rolled `M:SS`. This is the one surface where the
seam is visible on a Bluetooth head unit, and it must be one string with a quantity and a time
argument before it can be translated.

### 2.20 Waveform accessibility — `Design.kt:194`

| Key | Line | Text |
| --- | --- | --- |
| `a11y.waveform.playing` | 194 | Soundscape playing |
| `a11y.waveform.paused` | 194 | Soundscape paused |

### 2.21 Launcher label — `AndroidManifest.xml:9`

| Key | Line | Text |
| --- | --- | --- |
| `app_name` | 9 | Nightfall |

The manifest uses `android:label="Nightfall"` as a literal. It should be `@string/app_name` so that
the launcher, the task switcher, the notification header and the installer all follow the same
resource. Same key as `home.title` (§2.5), which is the product name on screen.

### 2.22 Catalogue content — `core/src/main/kotlin/dev/nightfall/core/Library.kt`

106 sound names, 10 category names, six description strings, and 15 built-in preset names. These are
not chrome, and they do not extract the same way: for 94 of the 106 sounds the id is
`Library.slug(name)` (line 230), computed from the English name at construction. Display name and id
are the same information for those, which is why the id must be frozen as a literal before any name
is translated.

Twelve sounds break that derivation, and the appendix records the real pair for each rather than the
rule:

- **Eleven noises** (`white` … `velvet`) have a one-word id and a two-word name: the id is
  `n.lowercase()` (line 38) while the displayed name is `"$n noise"` (line 39). `white` shows
  "White noise", not "White" — the layer row and the library both print the longer form.
- **Binaural tones** carries the explicit id `binaural` (line 219) against the name `Binaural tones`
  (line 220), so `slug(name)` would produce the wrong id.

Anyone generating keys mechanically from the names would get those twelve wrong in exactly the way
the appendix exists to prevent.

**Categories** (displayed as filter chips at `Screens.kt:80` and in the library section heading at
`Screens.kt:87`):

| Key | Line | Text |
| --- | --- | --- |
| `library.category.noise` | 40 | Noise |
| `library.category.rain` | 53 | Rain |
| `library.category.storms` | 69 | Storms |
| `library.category.winter` | 83 | Winter |
| `library.category.wind` | 97 | Wind |
| `library.category.nature` | 111 | Nature |
| `library.category.indoor` | 132 | Indoor |
| `library.category.travel` | 154 | Travel |
| `library.category.other` | 168 | Other |
| `library.category.music` | 221 | Music |

**Descriptions** (a `Sound.description`, shown in the expanded layer panel at
`NightfallScreen.kt:851`):

| Key | Line | Text |
| --- | --- | --- |
| `library.description.synthesized` | 10 (default) | Locally synthesized |
| `library.description.noise.continuous` | 45 | Continuous spectral synthesis |
| `library.description.noise.named` | 44 | Creative configurable spectral preset; not a standardized noise color |
| `library.description.recording` | 125, 170, 180, 190 | Bundled field recording • see Audio credits |
| `library.description.recording.missing` | 149 | Recording not bundled; no substitute audio |
| `library.description.binaural` | 224 | 200 Hz carrier; frequency difference adjustable 1–20 Hz. Stereo headphones required. Sleep-stage and healing benefits are not established. |

**Sound names** — the full 106-row list, with id and line, is Appendix A. Grouped by catalogue section:

| Group | Count | Lines |
| --- | --- | --- |
| Noise | 11 | 23-33 |
| Rain | 10 | 56-65 |
| Storms | 8 | 72-79 |
| Winter | 8 | 86-93 |
| Wind | 8 | 100-107 |
| Nature — water, wind, fire, field recordings | 23 | 113, 115, 116, 118, 166-170, 192 |
| Indoor — fire, hum, clock, unavailable | 9 | 130, 135-140, 146, 152 |
| Travel | 5 | 157-161 |
| Other — recordings, hums | 5 | 166, 176, 186, 194 |
| Music | 18 | 197-214 |
| Music — binaural | 1 | 220 |

Total 106. Names are single words or short noun phrases (`Light drizzle`, `Wind through pine trees`,
`Occasional lightning-associated thunder`); the longest is 37 characters and the row gives it one
line at 14 sp, so a longer translation ellipsizes rather than wraps through the row.

**Built-in preset names** — `Library.kt:305-340` (15):

`Quiet Skies`, `Cozy Cabin`, `Rainy Lo-fi`, `Deep Sleep Noise`, `Ocean Night`, `Storm Watch`,
`Winter Storm`, `Forest Cabin`, `Space Drift`, `Airplane Sleep`, `Mountain Stream`, `Piano Rain`,
`Night Library`, `Midnight Fire`, `Nine-Layer Dream`.

Preset keys are `library.preset.<id>.name` with the same `Library.slug` derivation. Preset ids are
persisted in `SavedState.favoriteIds` and used as Compose `key = "builtin-${it.id}"` at
`Screens.kt:193`, so the same freeze-the-id rule applies.

`Mix.name` defaults to `Nine-Layer Dream` (`core/Models.kt:56`) and `NightfallApp.kt:45` seeds the
in-memory mix with `Your soundscape`; both end up written to DataStore.

### 2.23 Endless night switch — `NightfallScreen.kt:488-497`

| Key | Line | Text |
| --- | --- | --- |
| `home_endless_title` | 491 | Endless night |
| `home_endless_description` | 492 | Sources drift in and out as the night goes on, so this mix keeps moving instead of settling into the same loop. |

The switch is a `SettingToggle` in the Home list, after the master volume and the quick actions, and
it edits the mix rather than the app's settings, so the words are `home.*` and the sentence travels
with the mix it describes. The title is also the switch's `contentDescription`, so a screen reader
announces one name for the control and the row above it rather than two.

The description is a full sentence with a full stop, and it is the only place the product explains
what the switch does. Two things it must keep saying in any translation: the sources move *in and
out* rather than being added, and the mix keeps moving instead of settling into a loop — a translation
that promises "more sounds" would be describing a different feature.

## 3. Strings that must not be extracted

### 3.1 String comparisons that would break

Three literals are compared in code, not just displayed. Translating the displayed text without
changing the comparison breaks behaviour in a way no screenshot would show:

| Literal | Compared at | Why it is a hazard |
| --- | --- | --- |
| `"Bluetooth"` | `NightfallScreen.kt:562` | `output.startsWith("Bluetooth")` picks the Bluetooth icon. Translate the route label and this is false forever. The route must become a typed value (an enum with a label resource) before `output.*` is extracted. |
| `"All"` | `Screens.kt:50, 54, 87` | Category filter sentinel, initial value, and chip label at once. The sentinel must become `null` or a sealed category first. |
| `" · "` | `NightfallScreen.kt:570`, `Screens.kt:283`, `NightfallScreen.kt:841` | Not a label: a separator used with `substringBefore` and `joinToString`. It stays a literal; the strings that contain it are the ones that get translated. |

`"major"`/`"minor"` (`Screens.kt:419`) and the twelve note names (`Screens.kt:574`,
`C`, `C♯`, `D`, `D♯`, `E`, `F`, `F♯`, `G`, `G♯`, `A`, `A♯`, `B`) are also id-and-label pairs.
The note names are universal in Western notation and stay untranslated; `studio.scale.major` and
`studio.scale.minor` are the display halves and do get extracted, while the `"major"`/`"minor"`
ids do not.

### 3.2 Developer diagnostics

| Text | Where | Reason |
| --- | --- | --- |
| `The mix to export is no longer available`, `Cannot open export file`, `Cannot open preset` | `NightfallScreen.kt:106, 110, 127` | `error(...)` messages folded into `Export failed: %s` / `Import failed: %s`. A diagnostic, not copy. The wrapper is translated; the detail is not. |
| `AudioTrack write failed: $written` | `AudioHost.kt:235` | `check()` message surfaced through `Audio stopped: %s`. Same class. |
| `Mix needs a name of 1–200 characters`, `Duplicate or invalid layer ID`, `Unknown sound: {}`, `Recording not bundled: {}`, `Non-finite audio setting` | `core/Models.kt:99-138` | Validation messages thrown as `IllegalArgumentException` from `core`, which has no Android dependency and therefore no `Resources`. They reach the user only as the `{detail}` of an import failure. |
| `A recording must run over three minutes before looping` | `core/Sources.kt:303` | Same: a `require` message. |

If import diagnostics are ever wanted in the user's language, the fix is a typed error code from
`core` and a message table in `app`, not string resources in `core`. That is a larger change than
localization and is not proposed here.

### 3.3 Identifiers, tags and filenames

`Nightfall:audio` (`AudioHost.kt:19`, wake-lock tag), `Nightfall PCM` (`AudioHost.kt:276`, thread
name), `nightfall` (`NightfallApp.kt:17` DataStore name, `PlaybackService.kt:41, 62` notification
channel id), `state_v1` (preference key), `nightfall-mix` (Media3 id, `PlaybackService.kt:138, 165`),
`AUDIO_LICENSES.md` (`NightfallScreen.kt:364`, asset filename), `application/json` and the two
fallback MIME types (`NightfallScreen.kt:101, 230-232`), `"play"` (`NightfallApp.kt:162`,
`PlaybackService.kt:90`, intent action), `"current"` (`Models.kt:55`, mix id), `nightfall-mix.json`
(`NightfallScreen.kt:263`, default export filename). None are read by a person.

One of these sits next to identifiers and does get extracted: `notification.channel.playback`
(`PlaybackService.kt:57`) is a channel *name* Android shows in Settings, so it is a resource even
though its id, `nightfall` (line 41), is not. `ic_moon` and the `pcm` asset names are file names and
stay as they are.

### 3.4 Tooling labels never rendered

`"Quiet waveform"` and `"Wave phase"` (`Design.kt:183, 188`) are animation labels for Compose
tooling; `"Nightfall dark"` / `"Nightfall light"` (`Design.kt:258, 276`) are `@Preview` names; the
`"Nightfall"` and `"Master volume"` text at `Design.kt:267-269, 286-287` appears only inside those
previews and never ships. Preview code is not built into the app, so extracting it would create
unused resources that lint cannot see are unused.

### 3.5 Catalogue-adjacent text that must not go through `strings.xml`

The audio credits body (§2.4) is a markdown asset, and the field-recording attribution in it names
tracks and licences. It stays English alongside `THIRD_PARTY_NOTICES.md`. So do the F-Droid and
Fastlane listings: `fastlane/metadata/android/en-US/` and `fdroid/metadata/` are store metadata that
the release workflow owns, not app resources.

### 3.6 Deliberately untranslatable technical statements

`48 kHz · stereo · floating-point PCM` (§2.16) is a specification. The unit symbols `dB`, `Hz`, `BPM`,
`%`, `min`, `s` are SI or already-universal; what gets translated is the pattern around them, never
the symbols. `A4` is a note designation in ISO notation and does not change with language.

## 4. Quantity, plural and format cases

These are the strings that cannot be `strings.xml` entries with substituted words; each one needs
either a `<plurals>` entry, an ICU message, or a locale-aware formatter. All are listed explicitly
because Android's `getQuantityString` and ICU's `plural` disagree on which languages need what, and
guessing wrong is silent.

### 4.1 Plural counts (need `<plurals>`)

| Key | Line | Form | Notes |
| --- | --- | --- | --- |
| `home.section.layer_summary` | 473 | `%1$d layers · hold the grip to reorder` | The instruction must not be separated from the count; one string. |
| `home.status.active_layers` | 587 | `%1$d layers` | Active (enabled, unmuted, solo-aware) count, not total. |
| `home.layer.options.label.adjusted` | 829 | `Options · %1$d adjusted` | |
| `home.layer.options.state.collapsed_adjusted` | 822 | `Collapsed, %1$d adjusted` | Accessibility; same quantity, different sentence. |
| `home.capacity_warning` | 661 | `%1$d%%` and `%2$d` underruns | Two quantities, one sentence: ICU plural on the underruns only. |
| `library.section.available` | 88 | `%1$d available` | |
| `library.count_in_mix` | 45 | `%1$d in your mix` | |
| `presets.section.user_count` | 183 | `%1$d saved` | |
| `presets.section.builtin_count` | 190 | `%1$d soundscapes` | |
| `presets.card.layer_count` | 218 | `%1$d layers` | |
| `notification.artist.count` | 142 | `%1$d layers` | Plus the timer fragment — see 4.3. |
| `home.fade_dialog.fade_duration.value` | 328 | `%1$d seconds` | "1 seconds" is wrong today; a plural fixes it. |
| `settings.timer_fade.value` | 711 | `%1$d seconds` | Same. |
| `home.timer_dialog.preset_minutes` | 1098 | `%1$d min` | |
| `common.unit.minutes` | 978, 988 | `%1$d min` | |

`library.section.available` counts only bundled sounds and can read `1 available`; `home.section.`
`layer_summary` can read `1 layers` today, which is the same bug the plural fixes.

### 4.2 Decimal and unit formatting

| Key | Line | Format | Why it is not a plural |
| --- | --- | --- | --- |
| `common.unit.decibels` | 879, 887, 895 | `%1$d dB` | Integer today, but the minus glyph and digit shaping are locale-controlled, so the value must be formatted, not concatenated. |
| `common.unit.hertz` | 919, 927 | `%1$d Hz` | |
| `common.unit.hertz_one_decimal` | 938 | `%1$.1f Hz` | `"%.1f".format()` at 909 and 938 already uses the default locale, so `3.5` renders `3,5` in German today — the one place the app is accidentally locale-correct. It must stay a `Locale`-aware format, not `String.format(Locale.ROOT, …)`. |
| `common.unit.ratio` | 909 | `%1$.1f` | No unit; spectral tilt, −1…1. |
| `common.unit.seconds_one_decimal` | 969 | `%1$.1f s` | |
| `common.unit.bpm` | 432 | `%1$d BPM` | |
| `home.layer.pan.value.side` | 863 | `%1$d%% left` / `%1$d%% right` | Two keys, not one string with the direction substituted, because the direction word's position and form vary. Also wrong in RTL if the sign keeps meaning "screen left". |
| `studio.tuning.option` | 396 | `A4 = %1$d Hz` | The `A4` stays; only the pattern is translated. |

Every `%.1f` in the app is at lines 909, 938, 969 and is reached through Kotlin's
`"…".format(value)`, which delegates to the default locale. Measured: `String.format("%.1f", 3.5f)`
returns `3,5` under `de_DE` and `3.5` under `en_US`, so the current behaviour is already correct and
an extraction must not route it through `Locale.ROOT`.

### 4.3 Time and duration formatting

| Site | Line | Current form | Required |
| --- | --- | --- | --- |
| `formatTime` | `NightfallScreen.kt:1064-1067` | `${s/60}:${s%60}` padded to two digits | `DateUtils.formatElapsedTime`, or ICU `MeasureFormat`. Two problems with the hand-rolled form: it always emits ASCII digits, and it has no hours field, so a 90-minute timer reads `90:00` instead of `1:30:00`. |
| `notification.artist.remaining` | `PlaybackService.kt:134` | `${ms/60000}:${(ms/1000)%60}` | Same, computed a second time in a second file — two implementations of one format that must not drift. |
| `home.timer.fading` / `notification.artist.fading` | 597 / 132 | `Fading` | Not a duration, but appended to the timer line; see below. |

`notification.artist.*` (132-135, 142) is the worst case in the app: `"$count layers$timer"` with
`timer` being one of `" · Fading out"`, `" · M:SS remaining"` or `" · All night"`. Four literals and a
separator combine into one line shown on a lock screen and a car head unit. It must become one ICU
message with a plural and a duration argument, or three complete strings selected in code — not
three fragments with their own leading separators.

### 4.4 Number formatting already correct, must not regress

`"%.1f".format()` (909, 938, 969) and `roundToInt()` before every integer interpolation are the only
locale-sensitive paths, and the integer rounding is deliberate: it is what avoids `0.30000004 dB`.
An extraction must keep the value formatting out of the resource and pass an already-formatted
string, or pass a typed argument and let `String.format` with the resource's locale do it. Passing
a `Float` to `%1$d` will truncate silently.

### 4.5 Composed text written into saved data

Four strings are not just displayed — they are composed and stored, so a translated version is
baked into the user's DataStore and never re-translates:

| Site | Line | Composed text | Where it ends up |
| --- | --- | --- | --- |
| `NightfallApp.kt:45` | 45 | `Your soundscape` | `Mix.name`, persisted as `state_v1` |
| `NightfallApp.kt:136` | 136 | `My night mix` | `Mix.name` when the save field is blank, persisted |
| `Screens.kt:252` | 252 | `{name} copy` | A new preset's `Mix.name`, persisted |
| `Screens.kt:549` | 549 | `Studio · {name}` | A new preset's `Mix.name`, persisted |

A person who saves a mix in English and then switches the app to German keeps an English name —
correct, because it is their data. The problem is the *default*: a mix saved in German on Monday
shows a German name for a preset the app considers built-in, and the fallback name for an unnamed
mix appears in whatever locale was active the first time. The consistent rule is that user data is
never translated and app-supplied defaults are marked as defaults (an empty or sentinel name
resolved to a resource at render time), which is a saved-state schema change and the riskiest item
in this document.

## 5. Staged extraction plan

**Status.** Stages 0–3 landed as one change against `1ffbee1`: `strings.xml`, `plurals.xml`, the
manifest label, and every chrome string through `stringResource`/`getString`. Stage 4's typed values
had already landed in `Text.kt` as `OutputRoute`, so the extraction used them rather than repeating
that work. Stages 5 and 6 are open. One deliberate difference from the plan below: stage 3 does not
adopt `DateUtils.formatElapsedTime` or ICU messages, because both change what is drawn for durations
over an hour, and the extraction was held to pixel-identical renders. The formatting is still the
same `elapsedLabel` as before, and moving to a locale-aware formatter is now its own piece of work.

The guard that keeps this from regressing is `:app:verifyNoHardcodedCopy`, wired to `lintDebug` and
`lintRelease` so the gate and the release workflow run it with no workflow change. Lint's own
`HardcodedText` is enabled as an error too, but it reads View XML and Java only — a planted
`Text("…")` in a composable does not trip it, which is why the Gradle task exists. It honours a
`hardcoded-copy-allowed` marker for the preview-only strings.

Each stage is independently shippable and independently reviewable. The gate for every stage is
`./gradlew :core:test :app:assembleDebug :app:lintDebug` with `abortOnError = true`. No stage is
verifiable on a device here — this machine has no `/dev/kvm`, so a stage that changes what TalkBack
says can only be checked by reading the semantics tree, not by listening to it.

Ownership note: `app/src/main/java/**` and `app/src/main/res/**` belong to the mixer-UI thread per
the crew's scope split. Every stage below needs that scope handed over before it starts.

| Stage | Scope | Files | Risk |
| --- | --- | --- | --- |
| 0 | Resource plumbing, one string | `app/src/main/res/values/strings.xml` (new), `app/src/main/AndroidManifest.xml` | Very low |
| 1 | Shell, navigation, dialogs, Settings, Library chrome | `NightfallScreen.kt`, `Screens.kt`, `strings.xml` | Low |
| 2 | Layer row and options panel | `NightfallScreen.kt`, `Design.kt`, `strings.xml` | Medium — the accessibility contract |
| 3 | Quantities and formatting | `NightfallScreen.kt`, `Screens.kt`, `PlaybackService.kt`, `AudioHost.kt`, `strings.xml`, `plurals.xml` (new) | Medium — behaviour, not text |
| 4 | Typed route and filter values | `AudioHost.kt`, `NightfallApp.kt`, `NightfallScreen.kt`, `Screens.kt` | Medium — touches `PlaybackStatus` |
| 5 | Catalogue content | `core/Library.kt`, `Screens.kt`, `NightfallScreen.kt`, `strings.xml` | High — ids, presets, 98 rows |
| 6 | Persisted defaults | `NightfallApp.kt`, `Screens.kt`, `core/Models.kt` | Highest — saved-state migration |

Stage 4 must precede stage 1, because stage 1 cannot translate the route labels that stage 4 turns
into a typed value. The order to actually run is **0, 4, 1, 2, 3, 5, 6**; the numbers below are
labels, not a sequence.

**Stage 0 — resource plumbing.** Create `res/values/strings.xml` with `app_name` only; point
`android:label` at it. No Kotlin changes. This is the stage that proves the resource pipeline works
in this build and gives the next stage a file to add to. Risk: very low; the only failure mode is a manifest merge error, caught by
`:app:assembleDebug`.

**Stage 1 — static chrome.** The ~140 literals with no interpolation and no data: §2.1, §2.2, §2.3,
§2.4, §2.5 minus `home.section.layer_summary`, §2.6 minus the timers, §2.7, §2.8 minus the warning,
§2.9 labels that carry no `{name}`, §2.13, §2.14 minus the counts, §2.15, §2.16, §2.17. Pure text
substitution, one screen per commit if preferred. Risk: low. The two ways it goes wrong are (a) a
`contentDescription` that was correct being replaced by the visible label where they differed —
several of these intentionally differ, e.g. `home.action.open_settings` vs the gear icon — and (b)
`CommonMark`-style apostrophes: the copy uses `’` (U+2019) in `each layer’s fade setting`, and a
careless `strings.xml` write turns it into `'`, which `aapt` then rejects or android escapes
incorrectly. Copy the characters, do not retype them.

**Stage 2 — the layer row and options panel.** ~70 literals (§2.9-§2.11), the densest block, all in
`NightfallScreen.kt`. Risk: **medium, and this is the one to review hardest.** These strings are the
accessibility contract: every slider's `stateDescription` is what TalkBack reads as the value, and
`spokenName` distinguishes 15 sliders that otherwise all announce as a percentage. The failure mode
is silent — a build where the slider still works but announces "Volume" fifteen times. It cannot be
caught here without a device. Mitigation: change one `LevelSlider` call at a time, keep
`spokenName` non-null everywhere, and diff the semantics strings against this document.

**Stage 3 — quantities and formats.** §4.1-§4.3. Adds `plurals.xml`, replaces `formatTime` with
`DateUtils.formatElapsedTime`, and rebuilds `notification.artist.*` as one ICU message. Risk: medium,
and unlike stages 1-2 the risk is behaviour. `DateUtils.formatElapsedTime` changes what is rendered
for durations over an hour (today `60:00`, after: `1:00:00`), which is a visible change nobody asked
for; `getQuantityString` needs the quantity *and* the formatted value, which is where the `%1$d` vs
`%1$s` mistake happens. This stage also fixes the real `1 seconds` / `1 layers` bugs today's English
copy has, so it is the first stage with a user-visible improvement.

**Stage 4 — typed route and filter values.** Replaces the `startsWith("Bluetooth")` test and the
`"All"` sentinel with an enum/sealed value carrying a label resource (§3.1). Touches `PlaybackStatus
.output` (a `String` today, `NightfallApp.kt:31`) and the Media3 notification's use of the same
value. Risk: medium — it is a type change through four files and a state class, and the icon choice
must still follow the real route. This stage is the prerequisite for translating `output.*` at all,
and is worth doing before stage 1 rather than after, because stage 1 cannot touch those four strings
without it.

Stage 3's `plurals.xml` is needed by `home.layer.stop_after` in stage 2, so if stage 2 lands before
stage 3, its unit suffixes have to stay literals and be picked up in stage 3.

**Stage 5 — catalogue content.** 106 names, 10 categories, 6 descriptions, 15 preset names. Two
changes must precede any translated name: (a) every `Library.slug(name)` id becomes an explicit
literal, so ids stop depending on English; (b) name lookup goes through a per-id resolution
(`library.sound.<id>.name`) with the built-in name as fallback for ids a translation does not cover,
because a translated build older than the catalogue would otherwise show raw ids. `library.disclaimer`
(§2.13) also needs the four colour names kept in step with the catalogue. Risk: high — 113 rows in a
file this thread does not own, a `Presets.p` that derives ids from names, and tests that assert on
`layer.name` (`core/src/test/.../MixerTest.kt:29`). The catalogue test that every library entry
produces sound would catch an id/reference mismatch, which is the one reassurance here.

**Stage 6 — persisted defaults.** §4.5. Replaces the four composed-and-stored strings with a
rendered-at-read-time default (an empty `Mix.name` resolved to `mix_name_untitled`, and a preset
carrying its catalogue id rather than its catalogue name). This is the only stage that has to read
existing user data and migrate it, so it needs the current `state_v1` payload to keep decoding, a
new key or a version bump, and a Robolectric test over a saved blob — which the crew does not have
yet (no `app/src/test`, no Robolectric dependency). Risk: highest. Do not start it before a
Robolectric test target exists.

## 6. Recommendation: not yet, and here is where the line is

**Do not extract the full app in service of a translation that does not exist.** Extraction with no
target locale buys nothing at runtime: the app renders the same English from a resource file instead
of a literal. What it costs is a diff of 250 UI strings across the six Kotlin files and the
manifest, 129 catalogue strings in `core`, and a saved-state migration, in a codebase whose
distinguishing claims are written in careful prose and whose only verification path is lint plus 18
core tests — no emulator, no device, and an accessibility contract (`stateDescription` per slider)
that a wrong extraction breaks silently. The UI files were also rewritten by the mixer-UI thread days ago; extracting on top of
that churn means two large diffs in the same three files in one week with no visual regression tool
to check either.

The strongest argument against translating now is not cost, it is honesty. `settings.data.body`
promises no account, no advertising, no analytics, no tracking. `settings.limits.body` promises no
software layer limit and states the exact warning threshold. `studio.tuning.disclaimer`,
`library.disclaimer` and `library.description.binaural` all exist to stop the app from overclaiming.
A translation of those five strings into a language nobody on this project can review is a
translation risk in exactly the place where the project's credibility lives. English-only is a
defensible, honest position; a machine-translated privacy claim is not.

**Do these now, because they are cheap and they are bugs independent of localization:**

1. Stage 0 (one resource, one manifest line). Ten minutes, removes the "no localization story at
   all" fact from the repo's description without touching behaviour.
2. Stage 4's typed route value. `output.startsWith("Bluetooth")` is a latent bug: a device whose
   route label ever changes casing picks the wrong icon. Same for the `"All"` sentinel, which is a
   string compared in three places.
3. `formatTime` and `PlaybackService.kt:134` as one shared duration formatter. Two hand-rolled
   `M:SS` implementations that disagree above 60 minutes is a bug today, not in translation.
4. Fix `Nightfall 1.0` in `settings.footer` to read the real version, since `versionName` is `1.1`
   and the string is about to be extractable.

**Extract the rest when one of these is true:** a translator or a target locale actually exists (then
the stages are already specified and can be done in order); or a screen is being substantially
rewritten anyway for another reason, in which case extracting that screen as part of that edit is
nearly free and avoids a second pass over the same file.

**What I would need before stage 5 or 6 is credible:** one reviewer per locale who can read the
privacy, tuning and capacity claims and confirm they stayed claims rather than promises. Without
that name, stages 5 and 6 should stay unstarted and this document should stay the plan.

**What is unverified in this document:** every line number and string was read from source at
`0df94ac`, but nothing here was observed on a device. The claim that TalkBack reads
`stateDescription` in display units is from the mixer-UI thread's code and its own report, not from a
listening test in this thread; no emulator exists on these machines to check it.

## Appendix A — the 106 sound names

Every name below is displayed by `Screens.kt:108` in the library list, `NightfallScreen.kt:755` in the
layer row and `Screens.kt:283` in a preset's layer line. The id in each key is the real `Sound.id`,
which is also the value stored in `Layer.soundId` and `SavedState`; for 86 of these it is
`Library.slug(name)` (`Library.kt:230`), and the twelve exceptions are listed in §2.22. Display name
and id are the same information today and must become two values. Line numbers are `Library.kt` at
`0df94ac`, except the eight entries added on 2026-11-19 (Rapids, Creeks, Waves on shingle, Lapping
water, Embers, Wood stove, Fire pit, Bonfire), which carry their line numbers at that change.

| Key | Name | Line |
| --- | --- | --- |
| `library.sound.white.name` | White noise | 23 |
| `library.sound.pink.name` | Pink noise | 24 |
| `library.sound.brown.name` | Brown noise | 25 |
| `library.sound.red.name` | Red noise | 26 |
| `library.sound.blue.name` | Blue noise | 27 |
| `library.sound.violet.name` | Violet noise | 28 |
| `library.sound.purple.name` | Purple noise | 29 |
| `library.sound.gray.name` | Gray noise | 30 |
| `library.sound.green.name` | Green noise | 31 |
| `library.sound.black.name` | Black noise | 32 |
| `library.sound.velvet.name` | Velvet noise | 33 |
| `library.sound.light-drizzle.name` | Light drizzle | 56 |
| `library.sound.moderate-rain.name` | Moderate rain | 57 |
| `library.sound.heavy-rain.name` | Heavy rain | 58 |
| `library.sound.rain-on-window.name` | Rain on window | 59 |
| `library.sound.rain-on-tent.name` | Rain on tent | 60 |
| `library.sound.rain-on-leaves.name` | Rain on leaves | 61 |
| `library.sound.rain-on-metal-roof.name` | Rain on metal roof | 62 |
| `library.sound.rain-on-car-roof.name` | Rain on car roof | 63 |
| `library.sound.rain-in-forest.name` | Rain in forest | 64 |
| `library.sound.rain-over-distant-ocean.name` | Rain over distant ocean | 65 |
| `library.sound.distant-thunder.name` | Distant thunder | 72 |
| `library.sound.rolling-thunder.name` | Rolling thunder | 73 |
| `library.sound.close-thunder.name` | Close thunder | 74 |
| `library.sound.thunderstorm.name` | Thunderstorm | 75 |
| `library.sound.occasional-lightning-associated-thunder.name` | Occasional lightning-associated thunder | 76 |
| `library.sound.tropical-storm.name` | Tropical storm | 77 |
| `library.sound.severe-rainstorm.name` | Severe rainstorm | 78 |
| `library.sound.windy-thunderstorm.name` | Windy thunderstorm | 79 |
| `library.sound.blizzard.name` | Blizzard | 86 |
| `library.sound.gentle-snowfall-atmosphere.name` | Gentle snowfall atmosphere | 87 |
| `library.sound.heavy-snowfall-with-wind.name` | Heavy snowfall with wind | 88 |
| `library.sound.hail.name` | Hail | 89 |
| `library.sound.sleet.name` | Sleet | 90 |
| `library.sound.ice-storm.name` | Ice storm | 91 |
| `library.sound.arctic-wind.name` | Arctic wind | 92 |
| `library.sound.snow-against-windows.name` | Snow against windows | 93 |
| `library.sound.gentle-breeze.name` | Gentle breeze | 100 |
| `library.sound.strong-wind.name` | Strong wind | 101 |
| `library.sound.wind-through-pine-trees.name` | Wind through pine trees | 102 |
| `library.sound.wind-through-deciduous-trees.name` | Wind through deciduous trees | 103 |
| `library.sound.mountain-wind.name` | Mountain wind | 104 |
| `library.sound.ocean-wind.name` | Ocean wind | 105 |
| `library.sound.wind-against-cabin-walls.name` | Wind against cabin walls | 106 |
| `library.sound.howling-wind.name` | Howling wind | 107 |
| `library.sound.ocean-waves.name` | Ocean waves | 113 |
| `library.sound.beaches.name` | Beaches | 113 |
| `library.sound.streams.name` | Streams | 113 |
| `library.sound.rivers.name` | Rivers | 113 |
| `library.sound.waterfalls.name` | Waterfalls | 113 |
| `library.sound.lakes.name` | Lakes | 113 |
| `library.sound.rapids.name` | Rapids | 166 |
| `library.sound.creeks.name` | Creeks | 167 |
| `library.sound.waves-on-shingle.name` | Waves on shingle | 168 |
| `library.sound.lapping-water.name` | Lapping water | 170 |
| `library.sound.wind-in-leaves.name` | Wind in leaves | 115 |
| `library.sound.caves.name` | Caves | 115 |
| `library.sound.mountain-ambience.name` | Mountain ambience | 115 |
| `library.sound.campfires.name` | Campfires | 116 |
| `library.sound.crackling-wood.name` | Crackling wood | 116 |
| `library.sound.embers.name` | Embers | 192 |
| `library.sound.wood-stove.name` | Wood stove | 192 |
| `library.sound.fire-pit.name` | Fire pit | 192 |
| `library.sound.bonfire.name` | Bonfire | 192 |
| `library.sound.forest-birds.name` | Forest birds | 118 |
| `library.sound.crickets.name` | Crickets | 118 |
| `library.sound.frogs.name` | Frogs | 118 |
| `library.sound.nighttime-forest.name` | Nighttime forest | 118 |
| `library.sound.fireplace.name` | Fireplace | 130 |
| `library.sound.box-fan.name` | Box fan | 135 |
| `library.sound.ceiling-fan.name` | Ceiling fan | 136 |
| `library.sound.air-conditioner.name` | Air conditioner | 137 |
| `library.sound.air-purifier.name` | Air purifier | 138 |
| `library.sound.heating-system.name` | Heating system | 139 |
| `library.sound.quiet-room.name` | Quiet room | 140 |
| `library.sound.distant-household-ambience.name` | Distant household ambience | 146 |
| `library.sound.ticking-clock.name` | Ticking clock | 152 |
| `library.sound.airplane-cabin.name` | Airplane cabin | 157 |
| `library.sound.train-interior.name` | Train interior | 158 |
| `library.sound.ship-cabin.name` | Ship cabin | 159 |
| `library.sound.distant-train.name` | Distant train | 160 |
| `library.sound.gentle-vehicle-ambience.name` | Gentle vehicle ambience | 161 |
| `library.sound.quiet-library.name` | Quiet library | 167 |
| `library.sound.distant-city-rain.name` | Distant city rain | 177 |
| `library.sound.soft-coffee-shop-ambience.name` | Soft coffee-shop ambience | 187 |
| `library.sound.space-themed-soundscape.name` | Space-themed soundscape | 194 |
| `library.sound.gentle-mechanical-hum.name` | Gentle mechanical hum | 194 |
| `library.sound.432-hz-ambient-music.name` | 432 Hz ambient music | 197 |
| `library.sound.440-hz-ambient-music.name` | 440 Hz ambient music | 198 |
| `library.sound.lo-fi-sleep-music.name` | Lo-fi sleep music | 199 |
| `library.sound.soft-piano.name` | Soft piano | 200 |
| `library.sound.minimalist-piano.name` | Minimalist piano | 201 |
| `library.sound.gentle-music-box.name` | Gentle music box | 202 |
| `library.sound.soft-synth-pads.name` | Soft synth pads | 203 |
| `library.sound.warm-analog-style-synth.name` | Warm analog-style synth | 204 |
| `library.sound.space-ambience.name` | Space ambience | 205 |
| `library.sound.dreamy-atmospheric-music.name` | Dreamy atmospheric music | 206 |
| `library.sound.soft-strings.name` | Soft strings | 207 |
| `library.sound.gentle-bells.name` | Gentle bells | 208 |
| `library.sound.meditation-drones.name` | Meditation drones | 209 |
| `library.sound.singing-bowl-style-synthesis.name` | Singing-bowl-style synthesis | 210 |
| `library.sound.generative-lullabies.name` | Generative lullabies | 211 |
| `library.sound.slow-percussion-free-lo-fi.name` | Slow percussion-free lo-fi | 212 |
| `library.sound.gentle-lo-fi.name` | Gentle lo-fi | 213 |
| `library.sound.evolving-cinematic-ambient-textures.name` | Evolving cinematic ambient textures | 214 |
| `library.sound.binaural.name` | Binaural tones | 220 |
