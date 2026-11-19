// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

/**
 * Output level calibration: the one place that decides how loud Nightfall is.
 *
 * A layer slider is independent digital gain, so the mixer needs a reference level for one layer at
 * full gain. Without one each source's private synthesis amplitude decided its loudness: measured
 * across the catalogue, gated integrated loudness at gain 1.0 spanned 30 dB, white noise at
 * -12.2 LUFS against rolling thunder at -38.4 LUFS, and the app's ceiling sat 19 dB below the sample
 * ceiling. Choosing a sound changed its level more than its character, and the quiet ones were
 * inaudible in a mix.
 *
 * Reference level: [REFERENCE_LUFS] gated integrated loudness (ITU-R BS.1770-4 K-weighting) for one
 * layer at gain 1.0, master 1.0, pan centre. Every catalogue entry is trimmed to it by the measured
 * table below, so a layer's slider means the same loudness for every sound and stacking raises level.
 *
 * The trims are loudness, not peak: they are measured below the limiter's knee and applied blind to
 * crest factor, exactly as a channel fader is set in a mix. A source whose peaks are 23 dB above its
 * average - a storm is mostly a quiet bed with loud events - therefore reaches the reference loudness
 * and leans on the limiter for its transients, which is what the limiter is for. What that costs is
 * measured, not assumed: see `LoudnessTargetTest`.
 *
 * The trims are measured, not chosen. `LevelCalibrationTest` renders every entry alone for 20–90 s
 * and prints this table; re-run it after changing a source's amplitude:
 *
 *     NIGHTFALL_CALIBRATE=1 ./gradlew :core:test --tests '*LevelCalibrationTest*'
 */
object Levels {
    /** Gated integrated loudness of one layer at gain 1.0, master 1.0, pan centre. */
    const val REFERENCE_LUFS = -18.0

    /** Measured dB trim per catalogue entry. Sources not listed play at their synthesis level. */
    private val trimsDb: Map<String, Float> =
        mapOf(
            "white" to -5.7f,
            "pink" to 2.9f,
            "brown" to -1.4f,
            "red" to -1.4f,
            "blue" to 0.4f,
            "violet" to -4.9f,
            "purple" to -4.9f,
            "gray" to -1.6f,
            "green" to -2.6f,
            "black" to 28.2f,
            "velvet" to 4.0f,
            "light-drizzle" to 10.9f,
            "moderate-rain" to 4.5f,
            "heavy-rain" to 0.4f,
            "rain-on-window" to 4.5f,
            "rain-on-tent" to 4.3f,
            "rain-on-leaves" to 4.3f,
            "rain-on-metal-roof" to 4.1f,
            "rain-on-car-roof" to 4.2f,
            "rain-in-forest" to 4.5f,
            "rain-over-distant-ocean" to 4.6f,
            "distant-thunder" to 21.3f,
            "rolling-thunder" to 23.9f,
            "close-thunder" to 20.7f,
            "thunderstorm" to 7.8f,
            "occasional-lightning-associated-thunder" to 9.8f,
            "tropical-storm" to 8.2f,
            "severe-rainstorm" to 7.0f,
            "windy-thunderstorm" to 8.2f,
            "blizzard" to 12.7f,
            "gentle-snowfall-atmosphere" to 20.0f,
            "heavy-snowfall-with-wind" to 8.2f,
            "hail" to 7.1f,
            "sleet" to 8.5f,
            "ice-storm" to 7.6f,
            "arctic-wind" to 9.9f,
            "snow-against-windows" to 10.5f,
            "gentle-breeze" to 18.2f,
            "strong-wind" to 5.0f,
            "wind-through-pine-trees" to 6.8f,
            "wind-through-deciduous-trees" to 9.2f,
            "mountain-wind" to 11.8f,
            "ocean-wind" to 11.0f,
            "wind-against-cabin-walls" to 23.1f,
            "howling-wind" to -0.3f,
            "ocean-waves" to 10.5f,
            "beaches" to 8.4f,
            "streams" to 9.9f,
            "rivers" to 11.0f,
            "waterfalls" to 6.1f,
            "lakes" to 21.5f,
            "rapids" to 3.8f,
            "creeks" to 10.7f,
            "waves-on-shingle" to 2.9f,
            "lapping-water" to 8.6f,
            "wind-in-leaves" to 8.5f,
            "caves" to 26.5f,
            "mountain-ambience" to 8.6f,
            "campfires" to 8.4f,
            "crackling-wood" to 7.2f,
            "embers" to 15.2f,
            "wood-stove" to 12.6f,
            "fire-pit" to 6.1f,
            "bonfire" to 6.7f,
            "forest-birds" to 15.5f,
            "crickets" to 17.2f,
            "frogs" to 17.1f,
            "nighttime-forest" to 19.4f,
            "fireplace" to 7.7f,
            "box-fan" to 8.9f,
            "ceiling-fan" to 8.6f,
            "air-conditioner" to 8.6f,
            "air-purifier" to 8.4f,
            "heating-system" to 8.4f,
            "quiet-room" to 8.3f,
            "ticking-clock" to 16.9f,
            "airplane-cabin" to 8.3f,
            "train-interior" to 10.7f,
            "ship-cabin" to 8.2f,
            "distant-train" to 8.1f,
            "gentle-vehicle-ambience" to 8.1f,
            "quiet-library" to 16.1f,
            "distant-city-rain" to 18.7f,
            "soft-coffee-shop-ambience" to 19.0f,
            "space-themed-soundscape" to 8.0f,
            "gentle-mechanical-hum" to 8.0f,
            "432-hz-ambient-music" to 11.4f,
            "440-hz-ambient-music" to 10.6f,
            "lo-fi-sleep-music" to 9.7f,
            "soft-piano" to 15.2f,
            "minimalist-piano" to 16.0f,
            "gentle-music-box" to 13.8f,
            "soft-synth-pads" to 10.3f,
            "warm-analog-style-synth" to 10.2f,
            "space-ambience" to 9.1f,
            "dreamy-atmospheric-music" to 11.1f,
            "soft-strings" to 10.8f,
            "gentle-bells" to 14.8f,
            "meditation-drones" to 11.3f,
            "singing-bowl-style-synthesis" to 16.8f,
            "generative-lullabies" to 13.8f,
            "slow-percussion-free-lo-fi" to 8.4f,
            "gentle-lo-fi" to 10.6f,
            "evolving-cinematic-ambient-textures" to 10.2f,
            "binaural" to 0.2f,
        )

    /** The measured trim itself, for the calibration test to correct the table in one pass. */
    fun trimDb(sound: Sound): Float = trimsDb[sound.id] ?: 0f

    fun trim(sound: Sound): Float = 10f.pow(trimDb(sound) / 20)
}
