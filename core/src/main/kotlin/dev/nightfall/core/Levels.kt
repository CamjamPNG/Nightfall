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
            "gray" to -1.7f,
            "green" to -2.6f,
            "black" to 28.2f,
            "velvet" to 4.0f,
            "light-drizzle" to 10.4f,
            "moderate-rain" to 4.5f,
            "heavy-rain" to 0.5f,
            "rain-on-window" to 4.4f,
            "rain-on-tent" to 4.4f,
            "rain-on-leaves" to 4.4f,
            "rain-on-metal-roof" to 4.4f,
            "rain-on-car-roof" to 4.4f,
            "rain-in-forest" to 4.4f,
            "rain-over-distant-ocean" to 4.4f,
            "distant-thunder" to 21.5f,
            "rolling-thunder" to 20.5f,
            "close-thunder" to 24.1f,
            "thunderstorm" to 8.2f,
            "occasional-lightning-associated-thunder" to 8.4f,
            "tropical-storm" to 8.3f,
            "severe-rainstorm" to 8.1f,
            "windy-thunderstorm" to 8.5f,
            "blizzard" to 12.9f,
            "gentle-snowfall-atmosphere" to 26.1f,
            "heavy-snowfall-with-wind" to 13.4f,
            "hail" to 11.7f,
            "sleet" to 11.9f,
            "ice-storm" to 12.0f,
            "arctic-wind" to 14.1f,
            "snow-against-windows" to 14.2f,
            "gentle-breeze" to 11.8f,
            "strong-wind" to 12.1f,
            "wind-through-pine-trees" to 12.3f,
            "wind-through-deciduous-trees" to 12.6f,
            "mountain-wind" to 12.9f,
            "ocean-wind" to 13.1f,
            "wind-against-cabin-walls" to 13.3f,
            "howling-wind" to 13.3f,
            "ocean-waves" to 10.4f,
            "beaches" to 10.4f,
            "streams" to 9.0f,
            "rivers" to 9.0f,
            "waterfalls" to 9.0f,
            "lakes" to 9.0f,
            "wind-in-leaves" to 11.9f,
            "caves" to 12.1f,
            "mountain-ambience" to 12.3f,
            "campfires" to 8.0f,
            "crackling-wood" to 8.0f,
            "forest-birds" to 15.2f,
            "crickets" to 15.7f,
            "frogs" to 16.7f,
            "nighttime-forest" to 16.0f,
            "fireplace" to 7.9f,
            "box-fan" to 8.9f,
            "ceiling-fan" to 10.7f,
            "air-conditioner" to 8.6f,
            "air-purifier" to 8.5f,
            "heating-system" to 8.5f,
            "quiet-room" to 8.3f,
            "ticking-clock" to 16.9f,
            "airplane-cabin" to 8.9f,
            "train-interior" to 10.7f,
            "ship-cabin" to 8.6f,
            "distant-train" to 8.5f,
            "gentle-vehicle-ambience" to 8.4f,
            "quiet-library" to 14.7f,
            "distant-city-rain" to 17.9f,
            "soft-coffee-shop-ambience" to 18.3f,
            "space-themed-soundscape" to 8.9f,
            "gentle-mechanical-hum" to 10.7f,
            "432-hz-ambient-music" to 10.5f,
            "440-hz-ambient-music" to 10.3f,
            "lo-fi-sleep-music" to 10.1f,
            "soft-piano" to 14.9f,
            "minimalist-piano" to 16.8f,
            "gentle-music-box" to 14.2f,
            "soft-synth-pads" to 10.5f,
            "warm-analog-style-synth" to 10.2f,
            "space-ambience" to 10.4f,
            "dreamy-atmospheric-music" to 10.5f,
            "soft-strings" to 10.1f,
            "gentle-bells" to 14.4f,
            "meditation-drones" to 10.2f,
            "singing-bowl-style-synthesis" to 15.8f,
            "generative-lullabies" to 14.5f,
            "slow-percussion-free-lo-fi" to 10.1f,
            "gentle-lo-fi" to 10.4f,
            "evolving-cinematic-ambient-textures" to 10.4f,
            "binaural" to 0.2f,
        )

    /** The measured trim itself, for the calibration test to correct the table in one pass. */
    fun trimDb(sound: Sound): Float = trimsDb[sound.id] ?: 0f

    fun trim(sound: Sound): Float = 10f.pow(trimDb(sound) / 20)
}
