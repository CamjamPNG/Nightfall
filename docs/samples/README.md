# Hearing Nightfall

Eight short clips of what the app sounds like, rendered offline by the same synthesis code the phone
runs and then encoded. Every one is 48 kHz stereo, faded in and out over two seconds at each end,
and together they are 1,306,777 bytes (1.25 MiB) of Opus.

They are not recordings of a phone. Nothing here was captured from a speaker, no device decoder was
involved, and the hardware path — Android's `MediaCodec`, an `AudioTrack`, a DAC, a pair of
headphones — is not exercised by any of them. What they are is the engine outputting samples, which
is the part this repository can prove on a build machine.

## The clips

| Clip | What it is | Length | Peak | Loudness | Size |
|---|---|---|---|---|---|
| [nine-layer-dream.ogg](nine-layer-dream.ogg) | the Nine-Layer Dream preset | 30.0 s | −11.1 dBFS | −24.5 LUFS | 207,887 B |
| [storm-that-passes.ogg](storm-that-passes.ogg) | Storm That Passes, its opening phase | 24.0 s | −15.3 dBFS | −24.8 LUFS | 164,510 B |
| [heavy-rain-metal-roof-fire.ogg](heavy-rain-metal-roof-fire.ogg) | the phrase "heavy rain on a metal roof with a fire" | 24.0 s | −14.3 dBFS | −23.9 LUFS | 170,653 B |
| [lo-fi-sleep-music.ogg](lo-fi-sleep-music.ogg) | the phrase "lo-fi sleep music" | 24.0 s | −20.6 dBFS | −30.3 LUFS | 207,654 B |
| [rain-on-tent.ogg](rain-on-tent.ogg) | the phrase "rain on a tent" | 20.0 s | −23.9 dBFS | −30.1 LUFS | 140,774 B |
| [ocean-waves.ogg](ocean-waves.ogg) | the phrase "ocean waves" | 20.0 s | −18.7 dBFS | −29.9 LUFS | 137,588 B |
| [campfires.ogg](campfires.ogg) | the phrase "campfires" | 20.0 s | −17.0 dBFS | −30.0 LUFS | 139,192 B |
| [brown-noise.ogg](brown-noise.ogg) | the phrase "brown noise" | 20.0 s | −15.1 dBFS | −30.2 LUFS | 138,519 B |

Peak and loudness are measured on the render, before encoding. Opus moves the integrated loudness by
at most 0.6 LU and does not move the sample peak; a true-peak meter on the encoded file reads up to
3 dB higher than the sample peak above, which is what a true peak is for.

**Levels are as the app ships them, not matched between clips.** A preset sums several layers and
carries a master trim chosen to land at −24 LUFS, so a nine-layer mix is about six decibels louder
than a phrase that asked for one sound: the phrase interpreter gives it 50% gain and the default
master, and the mixer does not normalise a sum. If you play the whole set back to back, expect that
jump. It is the product's behaviour, not an encoding artefact, and changing it is not something a
sample set gets to do.

## Exact settings

Each clip is the mixer at its shipped 48 kHz rate and 1024-frame block size, driven by the bundled
recordings in `app/src/pcm/assets/audio/` exactly as the app loads them. A per-layer gain of 0.50 is
the interpreter's level 3, its default when a phrase carries no intensity word.

- **nine-layer-dream** — the `Nine-Layer Dream` preset, unmodified: lo-fi-sleep-music 0.45, brown
  0.30, moderate-rain 0.50, blizzard 0.34, hail 0.30, rolling-thunder 0.45, wind-through-pine-trees
  0.40, fireplace 0.45, 440-hz-ambient-music 0.30; master 0.40.
- **storm-that-passes** — the `Storm That Passes` themed night, rendered from second zero, which is
  its opening phase: heavy-rain 0.52, thunderstorm 0.45, howling-wind 0.38, moderate-rain 0.48,
  rain-on-metal-roof 0.45, rolling-thunder 0.38, strong-wind 0.34, light-drizzle 0.50,
  rain-over-distant-ocean 0.62, quiet-room 0.32; master 0.34; endless night on, so its companions and
  tone drift are live during the clip as they are on a phone.
- **heavy-rain-metal-roof-fire** — heavy-rain 0.71 ("heavy" is the interpreter's level 4),
  rain-on-metal-roof 0.50, fireplace 0.50; master 0.50.
- **lo-fi-sleep-music** — lo-fi-sleep-music 0.50; master 0.50.
- **rain-on-tent** — rain-on-tent 0.50; master 0.50.
- **ocean-waves** — ocean-waves 0.50; master 0.50.
- **campfires** — campfires 0.50; master 0.50.
- **brown-noise** — brown 0.50; master 0.50.

Every phrase above is read by `LocalPhraseInterpreter` with no unrecognised word and no substitution;
`Samples.kt` refuses one that would not be.

## The format

Ogg Opus at 64 kbps, variable bit rate, stereo, 48 kHz — the mixer's own rate and channel count, so
the encoder is the only thing between the render and the file. MP3 was measured as an alternative and
rejected: at 96 kbps it moved `rain-on-tent` from −30.1 to −32.2 LUFS, and at 128 kbps to −31.5,
where Opus at 64 kbps holds it at −30.7 for 141 kB against MP3's 241 and 321. A sample that
misstates the level by two decibels cannot honestly demonstrate an engine that trims every source to
one loudness.

Opus in Ogg plays in current Chrome, Edge, Firefox and Safari. GitHub's own file view is the surface
these links open on, and whether it draws a player or offers only a download was not verifiable from
the machine that produced the files — either way the bytes are the bytes, and any browser can open
the raw file.

## Regenerating

```sh
bash tools/render_samples.sh
```

That renders every clip to `core/build/samples/*.wav` through the Gradle task `:core:renderSamples`,
prints each clip's peak and loudness, and encodes the WAVs into this directory with `ffmpeg`. It
needs a JDK and an `ffmpeg` with `libopus`; it is not part of the build gate, so CI never encodes
anything. `NIGHTFALL_SAMPLE_BITRATE=96k` changes the bitrate if a future codec or a different
tradeoff warrants it.

Run from a pristine tree of this commit — `git archive`, no build directory, the clips deleted
first — it reproduced all eight files at the same byte lengths (1,306,777 in total) and the same
measurements. The bytes themselves are not bit-identical from run to run: the Opus encoder is not
reproducible across processes, so the encoder's own output differs while the render under it does not.

`./gradlew :core:test` runs `SampleClipTest`, which reads every file in this directory and asserts it
is a real Ogg Opus stream at 48 kHz stereo, that its length is the length its recipe asks for, and
that a fresh render of that recipe peaks within 0.5 dB of the figure published above. A synth change
that moves a level therefore fails the suite instead of leaving these files quietly wrong, and the
failure message says to regenerate.

## What was never checked

Nobody has listened to these with an ear. Nothing here has been played through a phone's audio path,
through Bluetooth, through a sleep mask or through a speaker, and no listening test of any kind was
run — not for quality, not for loudness at the listener, not for whether a clip sounds like the thing
it is named after. The claims above are about bytes and measurements: a render, an encode, a decoder
reading the header back, and a number from a meter. Whether Nightfall sounds like a night is still
something only a person can say.
