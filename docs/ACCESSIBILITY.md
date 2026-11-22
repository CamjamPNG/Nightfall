# Accessibility audit

Nightfall is meant to be operated at two in the morning, half asleep, in a dark room, often with one
hand. That makes it an app where a screen reader, a large font and a steady 48dp target are not
politeness: they are the difference between a night that starts and one that never does. This file is
the record of the first audit of that, surface by surface, with the before-state written down — a
commit that only says "improved accessibility" cannot be reviewed for whether it fixed anything.

Two kinds of evidence appear below and they are kept apart on purpose:

- **measured** — a number a test in this repository computes on a JVM from the semantics tree Compose
  builds (the same tree TalkBack walks) or from the palette the theme is built from.
- **unverified** — everything a JVM cannot answer. **No screen reader has ever spoken on a device.**
  TalkBack, Android's accessibility scanner and a real finger have not touched any of this. A
  semantics tree is a contract, not a sentence in a dark room.

## How TalkBack was read

Compose publishes one merged semantics tree. What a control is announced with is, in order: its
`contentDescription`, its text, its `stateDescription`, and the role the component declares. A
control with none of the first two is an unlabeled button. The audit therefore reads four things per
control — name, state, role, actions — plus the two numbers a finger cares about: the box the
component draws and the touch bounds Android reports for it.

`app/src/test/java/dev/nightfall/AccessibilitySemanticsTest.kt` asserts the names, states, actions and
touch bounds on the Home, Library, Presets and Settings surfaces; `ContrastTest` computes every
contrast ratio the theme ships; `FontScaleTest` hosts the real composables at a doubled font scale.
Each test class is one scenario, because this repository runs one JVM per class.

## Findings, surface by surface

### 1. The transport on Home

| Control | Before | Now |
| --- | --- | --- |
| Play / pause | `"Play soundscape"` / `"Pause soundscape"`, role button, **no state**. Talking about a running mix and a paused one was identical. | Same name — a button whose name is the action it performs — plus `stateDescription` "Paused" or "Playing", measured by `TransportStateTest`. |
| Waveform | Its own node: `"Soundscape playing"`, a focus stop between the play button and the timer that repeated what the button already said. | Decorative and out of the tree (`clearAndSetSemantics`). `WaveformHiddenTest`. |
| Sleep timer | `"Sleep timer"` + a state description of the countdown (correct), but *setting* it changed nothing anyone was looking at: the dialog closes, focus goes nowhere. | The same name and state, and one polite announcement when it is set: "Sleep timer set, 30 minutes". The countdown itself is never announced — it ticks twice a second. `TimerAnnouncementTest` asserts the announcement exists **and** that three seconds of countdown add none. |
| Quick actions (mute all, stop, fade, save, reset) | Five labels in a `SpaceBetween` row with no width shared: at a doubled font they grow past the window. | Each takes an equal share and wraps inside it. `HomeBigFontTest` fails if any control crosses the window edge. |
| Layer volume / pan / width / EQ | Already named per layer (`"Volume, layer name"`) with the value as state, and already draggable *and* settable without dragging. | Unchanged — this was the one part of the app that was already right. `LayerSliderTest` now locks it, including the `SetProgress` action. |
| Mute / solo | `contentDescription` = "Mute <layer>", **no state**: pressed and unpressed sounded the same. | `stateDescription` "Muted"/"Not muted" and "Solo on"/"Solo off". `LayerMuteStateTest`. |
| Enable | Switch with the layer's name; the switch reports its own on/off. | Unchanged. |
| Reorder | Long-press-and-drag **and** "Move up"/"Move down" custom actions. Already both. | Unchanged. `LayerReorderTest` locks the custom actions and asserts the handle is not also a plain button. |
| Options (panel) | Named per layer with a state of expanded / collapsed-and-default / collapsed-with-N-adjusted. | Unchanged. |
| Stop-time, duplicate, delete | Stop time is a named slider; duplicate and delete are labelled buttons. | Unchanged. |

### 2. Library

| Finding | Before | Now |
| --- | --- | --- |
| Category chips | 33.5dp wide by 48dp tall. The height had been fixed; the width is whatever the word is. | `widthIn(min = 48.dp)` as well, so "All" and "Wind" are the same target. |
| Section headers | Named, `heading()`, `Role.Button`, expanded/collapsed state — correct — but their height was whatever the text happened to be. | `heightIn(min = 48.dp)`. |
| Search field | `contentDescription` "Search sounds"; the placeholder was the visible hint and is no longer the label. | Unchanged. |
| 106 rows | Each row is text followed by one labelled "Add <sound>" button. The row itself is not a control, so the focus order is one stop per sound rather than two. | Unchanged. |

### 3. Presets

| Finding | Before | Now |
| --- | --- | --- |
| Favourite | `"Favorite <preset>"` in both states. The glyph fills in; a screen reader heard nothing change. | `stateDescription` "Favorited"/"Not favorited". `PresetCardTest`. |
| Play | Text "Play". With twenty cards on screen, twenty identical "Play, button" stops. | `contentDescription` "Play <preset name>". |
| Overflow menu | `"Options for <preset>"`, items labelled. | Unchanged. |
| Duplicate / rename / delete | Menu items with their own text. | Unchanged. |
| Themed nights | Each carries a description of what the night does, drawn as copy under the name. | Unchanged. |
| Import / favourites filter | 40dp-tall buttons and a 32dp-tall chip. | Both `heightIn(min = 48.dp)`. |

### 4. The phrase box

| Finding | Before | Now |
| --- | --- | --- |
| Field | `contentDescription` "Describe the mix you want". | Unchanged. |
| Result chips | The chip's action removes its layer, and the label said `"Heavy rain · 71%"` — what the layer is, not what pressing it does. The word "Remove" lived on a 14dp close glyph inside the chip. | `contentDescription` "Remove Heavy rain from the mix, 71 percent". `PhraseAnnouncementTest`. |
| The plan appearing | Nothing was announced: the result card grows under a field nobody is looking at any more. | One polite line — "Understood: 2 sounds ready to play". |
| "Play this mix" | Fixed 46dp box around a 13sp label: under the minimum, and at 200% the label is taller than the box. | `heightIn(min = 48.dp)`. |
| "Browse the library" | 44dp. | 48dp. |
| "Make mix" | Fixed 56dp box. | Minimum 56dp. |

### 5. Settings

| Finding | Before | Now |
| --- | --- | --- |
| Every switch | Draws 52×32. The label beside it was 64dp tall; the control itself was a 32dp target. | Centred in a 48dp-tall box, which looks identical. |
| Version row, five taps | **Gesture-only.** A screen reader user could not reach the diagnostics surface at all; the row has no hint that it is anything but the app's name and version. | The same gesture, plus a `customActions` entry "Show diagnostics". Invisible to anyone not opening accessibility actions, so the surface is exactly as undiscoverable as it was. github channel only — the F-Droid build answers `VersionTap` with what it was given and has nothing to reveal. `DiagnosticsRevealActionTest`. |
| Rows, readouts, notes | Text; the diagnostic readout is one long string with its own line height. | Unchanged. |

### 6. First-run card, widget configure, tile

| Finding | Before | Now |
| --- | --- | --- |
| First-run dismiss | `IconButton` at an explicit **32×32dp** — the smallest control in the app. | 48dp box, same 18dp glyph. |
| "Not now" | 44dp. | 48dp. |
| The three rows | Clickable rows with merged text, no role. | `Role.Button` and a 48dp minimum. |
| Widget configure rows | **Two nodes in one place**: a bare `clickable` row announcing its text and, beside it, a `RadioButton` announcing "not selected". Neither was the control. | One node: `selectable(role = Role.RadioButton)` on the row, the inner radio with no click of its own. |
| Quick Settings tile | Already sets `contentDescription` to "<action>, <state>" and `subtitle` to the state. | **Confirmed, not rewritten.** |

## What was already right, and is now locked

An audit that only lists what was broken is a bad map of the app. Four things already worked, and
each now has a test that fails if it stops working: layer sliders are named per layer with their value
as state and are already settable without dragging; the reorder handle is already a long press *and*
"Move up"/"Move down"; the layer's options button already says whether its panel is expanded and how
many of its settings were adjusted; and the Quick Settings tile already gives the shade a
`contentDescription` of "<action>, <state>" plus a subtitle. The tile was read and confirmed rather
than rewritten.

One correction to that list came from measuring it. Material's icon button draws a 40dp box and
reports a **48dp touch bound**, which is what Android's accessibility service hits, so the corner
icons and the per-row add/delete buttons were never finger problems; enlarging them would have been
visible churn for no gain, and those changes were reverted. The controls that genuinely drew smaller
than a finger are the ones fixed above: the first-run close glyph at 32dp, the library category chips
at 33.5dp wide, and Settings' 32dp-tall switches.

## Contrast, computed

WCAG 2.2 asks 4.5:1 for body text and 3:1 for the parts that identify a control. Every pair below is
computed by `ContrastTest` from the palette constants, so an edit that softens a colour fails the
build instead of shipping. Each pair must clear its threshold by 0.4 as well, so nothing passes by a
hundredth.

| Pair | Ratio | Needs |
| --- | --- | --- |
| Night: White on background `#0B101C` | 17.61:1 | 4.5 |
| Night: White on card `#171F32` | 15.22:1 | 4.5 |
| Night: Muted `#A7B0C4` on background | 8.73:1 | 4.5 |
| Night: Muted on card | 7.55:1 | 4.5 |
| Night: Lavender `#B8A9F4` on background | 9.07:1 | 4.5 |
| Day: ink `#202437` on `#F4F2F9` | 13.82:1 | 4.5 |
| Day: ink on card `#EAE6F2` | 12.49:1 | 4.5 |
| Day: secondary `#555D73` on background | 5.91:1 | 4.5 |
| Day: secondary on card | 5.34:1 | 4.5 |
| Day: primary `#67519E` on background | 5.82:1 | 4.5 |
| **Night: slider groove `#6A7690` on background** | **4.17:1** (was 1.62:1) | 3.0 |
| **Night: slider groove on card** | **3.60:1** (was 1.40:1) | 3.0 |
| **Day: slider groove `#747A8C` on background** | **3.86:1** (was 1.41:1) | 3.0 |
| **Day: slider groove on card** | **3.49:1** (was 1.37:1) | 3.0 |

The two failures were both the *inactive* half of a slider's track: a groove nobody could see is a
slider nobody can find, and in a dark room it was the only thing showing where the range starts. The
replacement keeps the theme: the brightest thing in a layer row is still the lavender thumb and filled
half at 9.07:1, and the groove is a grey that reads as a groove rather than as a lit line.

**One deliberate failure, with the number.** The night theme's `outline` `#354058` — the hairline
border on the navigation bar and the rules between sections — measures **1.83:1** against the
background. It is left as it is: it draws a decorative rule, not a control boundary, and every
control it sits beside identifies itself by its own icon and label (8.7:1 and better). Raising it to
3:1 would put a bright line across the bottom of every screen in the app, which is the opposite of
what the theme is for. That is a decision with the number beside it, not an oversight.

The widget's own palette — declared as resources because a launcher inflates it in its own process —
passes the same lines: 15.22:1 for its dark label, 9.07:1 for the dark glyph on its button, 13.82:1
and 6.47:1 for the light ones.

## Text at 200%

`FontScaleTest` hosts the real `NightfallScreen`, `SettingsScreen` and `FirstRunCard` with a density
whose font scale is doubled and whose pixel density is not, at a 411×914 dp window.

What is asserted: no control is laid out past the window's edge, on any of the three; the controls
that used to be pinned to a fixed height are now minimums and are still at least their original
height; the three first-run rows are taller than their single line, because at 200% they wrap; and
"Not now" is taller than its 48dp minimum. What a JVM cannot see is a glyph actually cut in half —
these are sizes and placements on the semantics tree, and the rest is the device gap below.

## Transient changes, announced once

Two things change while nobody is looking at them: the sleep timer is set from a dialog that then
closes, and a typed phrase is replaced by a mix under the field that was typed in. Both now produce
one **polite** live region — queued behind whatever is being read, never interrupting. Neither is
given anything that changes on a meter tick: the countdown is not announced, and the capacity warning
(which reads a load figure twice a second) deliberately has no live region at all.

## What is unverified

**No screen reader has spoken on a device.** Nothing in this file is a report of what TalkBack said:
the semantics tree is a contract, and whether a sentence arrives comprehensibly in a dark room at 2am
is not something a JVM can answer. Specifically unverified:

- Whether TalkBack announces the two polite live regions at all, and whether the invisible announcement
  node is read in the order intended. The tests assert the node carries `LiveRegion.Polite` and the
  text; they cannot assert that a voice speaks it.
- Whether the custom actions ("Move up", "Move down", "Show diagnostics") appear in TalkBack's actions
  menu in the order and wording expected, and whether the five-tap gesture still feels natural to a
  sighted user who has not been told about it.
- Whether a doubled font clips a *glyph* anywhere. The tests measure box sizes and placement; a real
  device at 200% is the check.
- The widget and the tile in a real launcher and a real shade. Their semantics were confirmed by
  reading the code, not hosted: a `RemoteViews` content description and a tile's `contentDescription`
  and `subtitle` are strings the system reads, and no launcher has read them.
- Colour on an actual panel. Contrast is computed from sRGB values as specified; a dim screen at 2am,
  an OLED gamma curve and Android's own dimming overlay are all outside the arithmetic.
