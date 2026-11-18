// SPDX-License-Identifier: MIT
package dev.nightfall.core

/**
 * One catalogue entry.
 *
 * [intensity], [brightness] and [eventRate] are the layer defaults for the knobs the synthesis for
 * [kind] actually reads, so a name behaves the way it is named: a strong wind is brighter and louder
 * than a breeze, heavy rain has denser drops than drizzle, and ice storm lands harder than sleet.
 * [gain] is calibrated from measurement so every entry lands near the same level at the same slider
 * position (see `CatalogueQualityTest`).
 */
data class Sound(
    val id: String,
    val name: String,
    val category: String,
    val kind: String,
    val variant: Int = 0,
    val intensity: Float = .5f,
    val brightness: Float = .5f,
    val eventRate: Float = .4f,
    val gain: Float = .35f,
    val description: String = "Locally synthesized",
    val asset: String? = null,
) {
    val music
        get() = kind == "music"

    val available
        get() = kind != "unavailable"
}

object Library {
    private val synthesis = "Continuous spectral synthesis"

    /**
     * Default layer gain per entry, measured so a newly added sound lands at the same level as the
     * rest of the catalogue: 99th-percentile level of the 100 ms envelope is trimmed to −42 dBFS at
     * 48 kHz, measured over 120 s through `PcmMixer` at default settings. Black noise and gentle
     * snowfall are capped by gain 1.0 because their synthesis is intrinsically 10–14 dB quieter.
     */
    private val calibrated =
        mapOf(
            "white" to 0.20f,
            "pink" to 0.38f,
            "brown" to 0.10f,
            "red" to 0.09f,
            "blue" to 0.40f,
            "violet" to 0.22f,
            "purple" to 0.22f,
            "gray" to 0.28f,
            "green" to 0.22f,
            "black" to 1.00f,
            "velvet" to 0.57f,
            "light-drizzle" to 1.00f,
            "moderate-rain" to 0.56f,
            "heavy-rain" to 0.35f,
            "rain-on-window" to 0.57f,
            "rain-on-tent" to 0.55f,
            "rain-on-leaves" to 0.55f,
            "rain-on-metal-roof" to 0.54f,
            "rain-on-car-roof" to 0.55f,
            "rain-in-forest" to 0.57f,
            "rain-over-distant-ocean" to 0.57f,
            "distant-thunder" to 1.00f,
            "rolling-thunder" to 0.81f,
            "close-thunder" to 0.64f,
            "thunderstorm" to 0.55f,
            "occasional-lightning-associated-thunder" to 0.66f,
            "tropical-storm" to 0.43f,
            "severe-rainstorm" to 0.39f,
            "windy-thunderstorm" to 0.52f,
            "blizzard" to 0.87f,
            "gentle-snowfall-atmosphere" to 1.00f,
            "heavy-snowfall-with-wind" to 0.83f,
            "hail" to 0.72f,
            "sleet" to 0.79f,
            "ice-storm" to 0.67f,
            "arctic-wind" to 0.89f,
            "snow-against-windows" to 1.00f,
            "gentle-breeze" to 1.00f,
            "strong-wind" to 0.53f,
            "wind-through-pine-trees" to 0.67f,
            "wind-through-deciduous-trees" to 0.65f,
            "mountain-wind" to 0.61f,
            "ocean-wind" to 0.73f,
            "wind-against-cabin-walls" to 1.00f,
            "howling-wind" to 0.43f,
            "ocean-waves" to 0.69f,
            "beaches" to 0.69f,
            "streams" to 0.82f,
            "rivers" to 0.82f,
            "waterfalls" to 0.83f,
            "lakes" to 0.83f,
            "wind-in-leaves" to 0.74f,
            "caves" to 1.00f,
            "mountain-ambience" to 0.75f,
            "campfires" to 0.64f,
            "crackling-wood" to 0.56f,
            "forest-birds" to 0.72f,
            "crickets" to 0.81f,
            "frogs" to 1.00f,
            "nighttime-forest" to 0.49f,
            "fireplace" to 0.59f,
            "box-fan" to 0.57f,
            "ceiling-fan" to 0.59f,
            "air-conditioner" to 0.56f,
            "air-purifier" to 0.56f,
            "heating-system" to 0.55f,
            "quiet-room" to 0.55f,
            "ticking-clock" to 1.00f,
            "airplane-cabin" to 0.57f,
            "train-interior" to 0.59f,
            "ship-cabin" to 0.55f,
            "distant-train" to 0.56f,
            "gentle-vehicle-ambience" to 0.56f,
            "quiet-library" to 0.42f,
            "distant-city-rain" to 0.68f,
            "soft-coffee-shop-ambience" to 0.71f,
            "space-themed-soundscape" to 0.56f,
            "gentle-mechanical-hum" to 0.59f,
            "432-hz-ambient-music" to 0.71f,
            "440-hz-ambient-music" to 0.67f,
            "lo-fi-sleep-music" to 0.55f,
            "soft-piano" to 0.87f,
            "minimalist-piano" to 0.92f,
            "gentle-music-box" to 0.78f,
            "soft-synth-pads" to 0.60f,
            "warm-analog-style-synth" to 0.65f,
            "space-ambience" to 0.59f,
            "dreamy-atmospheric-music" to 0.72f,
            "soft-strings" to 0.62f,
            "gentle-bells" to 0.80f,
            "meditation-drones" to 0.71f,
            "singing-bowl-style-synthesis" to 0.90f,
            "generative-lullabies" to 0.79f,
            "slow-percussion-free-lo-fi" to 0.57f,
            "gentle-lo-fi" to 0.68f,
            "evolving-cinematic-ambient-textures" to 0.59f,
            "binaural" to 0.24f,
        )

    private fun entry(
        name: String,
        category: String,
        kind: String,
        variant: Int = 0,
        intensity: Float = .5f,
        brightness: Float = .5f,
        eventRate: Float = .4f,
        description: String = synthesis,
        asset: String? = null,
    ) =
        Sound(
            slug(name),
            name,
            category,
            kind,
            variant,
            intensity,
            brightness,
            eventRate,
            calibrated[slug(name)] ?: .35f,
            description = description,
            asset = asset,
        )

    private fun recording(name: String, category: String) =
        entry(
            name,
            category,
            "recording",
            description = "Bundled field recording • see Audio credits",
            asset = slug(name),
        )

    val all: List<Sound> = buildList {
        listOf(
                "White",
                "Pink",
                "Brown",
                "Red",
                "Blue",
                "Violet",
                "Purple",
                "Gray",
                "Green",
                "Black",
                "Velvet",
            )
            .forEachIndexed { i, n ->
                add(
                    entry(
                        n,
                        "Noise",
                        "noise",
                        i,
                        description =
                            when (i) {
                                2 -> "1/f² spectrum. Red noise is another name for this spectrum"
                                3 -> "1/f² spectrum. Red noise is another name for brown noise"
                                5 -> "Differentiated white noise. Purple noise is the same spectrum"
                                6 -> "Differentiated white noise. Purple and violet name one spectrum"
                                in 7..10 ->
                                    "Creative configurable spectral preset; not a standardized noise color"
                                else -> synthesis
                            },
                    )
                )
            }
        listOf(
                "Light drizzle" to .12f,
                "Moderate rain" to .40f,
                "Heavy rain" to .85f,
                "Rain on window" to .30f,
                "Rain on tent" to .55f,
                "Rain on leaves" to .45f,
                "Rain on metal roof" to .90f,
                "Rain on car roof" to .70f,
                "Rain in forest" to .25f,
                "Rain over distant ocean" to .15f,
            )
            .forEachIndexed { i, (n, rate) ->
                add(entry(n, "Rain", "rain", i, eventRate = rate))
            }
        listOf(
                Triple("Distant thunder", .15f, .4f),
                Triple("Rolling thunder", .30f, .5f),
                Triple("Close thunder", .50f, .7f),
                Triple("Thunderstorm", .55f, .5f),
                Triple("Occasional lightning-associated thunder", .05f, .4f),
                Triple("Tropical storm", .70f, .5f),
                Triple("Severe rainstorm", .90f, .5f),
                Triple("Windy thunderstorm", .60f, .5f),
            )
            .forEachIndexed { i, (n, rate, intensity) ->
                add(entry(n, "Storms", "thunder", i, intensity = intensity, eventRate = rate))
            }
        listOf(
                Triple("Blizzard", .70f, .50f),
                Triple("Gentle snowfall atmosphere", .30f, .08f),
                Triple("Heavy snowfall with wind", .85f, .70f),
                Triple("Hail", .80f, .85f),
                Triple("Sleet", .60f, .45f),
                Triple("Ice storm", .75f, .95f),
                Triple("Arctic wind", .55f, .15f),
                Triple("Snow against windows", .40f, .30f),
            )
            .forEachIndexed { i, (n, bright, rate) ->
                add(entry(n, "Winter", "winter", i, brightness = bright, eventRate = rate))
            }
        listOf(
                Triple("Gentle breeze", .20f, .35f),
                Triple("Strong wind", .60f, .85f),
                Triple("Wind through pine trees", .45f, .75f),
                Triple("Wind through deciduous trees", .50f, .70f),
                Triple("Mountain wind", .35f, .80f),
                Triple("Ocean wind", .30f, .65f),
                Triple("Wind against cabin walls", .10f, .50f),
                Triple("Howling wind", .90f, .90f),
            )
            .forEachIndexed { i, (n, bright, intensity) ->
                add(entry(n, "Wind", "wind", i, intensity = intensity, brightness = bright))
            }
        val water = "Filtered-noise water bed; this variant does not model flowing or falling water"
        listOf("Ocean waves", "Beaches", "Streams", "Rivers", "Waterfalls", "Lakes")
            .forEachIndexed { i, n ->
                add(
                    entry(
                        n,
                        "Nature",
                        "water",
                        i,
                        description =
                            if (i <= 1) "Filtered-noise surf with a slow swell under the whole family"
                            else water,
                    )
                )
            }
        listOf(
                Triple("Wind in leaves", .70f, .45f),
                Triple("Caves", .02f, .40f),
                Triple("Mountain ambience", .50f, .60f),
            )
            .forEachIndexed { i, (n, bright, intensity) ->
                add(entry(n, "Nature", "wind", i, intensity = intensity, brightness = bright))
            }
        listOf("Campfires" to .25f, "Crackling wood" to .85f).forEachIndexed { i, (n, rate) ->
            add(entry(n, "Nature", "fire", i, eventRate = rate))
        }
        // Field recordings are explicitly distinct from physical-model textures.
        listOf("Forest birds", "Crickets", "Frogs", "Nighttime forest").forEach {
            add(recording(it, "Nature"))
        }
        add(entry("Fireplace", "Indoor", "fire", 0, eventRate = .50f))
        // The hum model derives its motor rumble from `variant`, so the index is unique per entry:
        // reusing one index across groups made nine of the thirteen hums the identical signal.
        listOf(
                "Box fan" to 0,
                "Ceiling fan" to 2,
                "Air conditioner" to 3,
                "Air purifier" to 4,
                "Heating system" to 5,
                "Quiet room" to 6,
            )
            .forEach { (n, v) -> add(entry(n, "Indoor", "hum", v)) }
        add(
            Sound(
                "distant-household-ambience",
                "Distant household ambience",
                "Indoor",
                "unavailable",
                description = "Recording not bundled; no substitute audio",
            )
        )
        add(entry("Ticking clock", "Indoor", "clock", 0))
        listOf(
                "Airplane cabin" to 7,
                "Train interior" to 1,
                "Ship cabin" to 8,
                "Distant train" to 9,
                "Gentle vehicle ambience" to 10,
            )
            .forEach { (n, v) -> add(entry(n, "Travel", "hum", v)) }
        add(recording("Quiet library", "Other"))
        add(recording("Distant city rain", "Other"))
        add(recording("Soft coffee-shop ambience", "Other"))
        listOf("Space-themed soundscape" to 11, "Gentle mechanical hum" to 12).forEach { (n, v) ->
            add(entry(n, "Other", "hum", v))
        }
        val names =
            listOf(
                "432 Hz ambient music",
                "440 Hz ambient music",
                "Lo-fi sleep music",
                "Soft piano",
                "Minimalist piano",
                "Gentle music box",
                "Soft synth pads",
                "Warm analog-style synth",
                "Space ambience",
                "Dreamy atmospheric music",
                "Soft strings",
                "Gentle bells",
                "Meditation drones",
                "Singing-bowl-style synthesis",
                "Generative lullabies",
                "Slow percussion-free lo-fi",
                "Gentle lo-fi",
                "Evolving cinematic ambient textures",
            )
        names.forEachIndexed { i, n -> add(entry(n, "Music", "music", i)) }
        add(
            Sound(
                "binaural",
                "Binaural tones",
                "Music",
                "binaural",
                description =
                    "200 Hz carrier; frequency difference adjustable 1–20 Hz. Stereo headphones required. Sleep-stage and healing benefits are not established.",
            )
        )
    }
    val byId = all.associateBy { it.id }

    fun slug(n: String) = n.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * Every music profile gets its own key, mode, tempo, density and texture. Measured before this
     * table existed: five pairs or triples of profiles produced byte-identical output (Space
     * ambience / Dreamy atmospheric / Evolving cinematic, Lo-fi / Warm analog, Music box / Bells),
     * and every profile shared key 0, major mode and the same delay settings.
     */
    private fun music(config: MusicConfig, variant: Int) =
        when (variant) {
            0 -> config.copy(key = 2, tempo = 44f, density = .35f, complexity = .5f, reverb = .5f, delay = .25f)
            1 -> config.copy(key = 9, tempo = 52f, density = .30f, complexity = .4f, reverb = .45f, delay = .20f)
            2 -> config.copy(key = 0, tempo = 42f, density = .45f, delay = .35f, reverb = .3f)
            3 -> config.copy(key = 5, tempo = 50f, density = .35f, reverb = .4f, delay = .25f)
            4 -> config.copy(key = 10, minor = true, tempo = 30f, density = .18f, reverb = .45f)
            5 -> config.copy(key = 7, tempo = 58f, density = .40f, reverb = .3f)
            6 -> config.copy(key = 4, tempo = 40f, density = .30f, reverb = .6f, delay = .3f, melody = false)
            7 -> config.copy(key = 8, tempo = 36f, density = .50f, delay = .3f)
            8 ->
                config.copy(
                    key = 11,
                    minor = true,
                    tempo = 34f,
                    density = .30f,
                    reverb = .7f,
                    delay = .4f,
                    melody = false,
                )
            9 -> config.copy(key = 3, tempo = 62f, density = .30f, reverb = .6f)
            10 -> config.copy(key = 6, tempo = 48f, density = .40f, reverb = .5f)
            11 -> config.copy(key = 1, tempo = 66f, density = .25f, reverb = .35f)
            12 ->
                config.copy(
                    key = 0,
                    minor = true,
                    tempo = 28f,
                    density = .15f,
                    reverb = .55f,
                    melody = false,
                )
            13 ->
                config.copy(key = 9, tempo = 26f, density = .20f, complexity = .3f, reverb = .5f)
            14 ->
                config.copy(
                    key = 2,
                    tempo = 54f,
                    density = .65f,
                    complexity = .6f,
                    randomness = .7f,
                )
            15 -> config.copy(key = 7, tempo = 32f, density = .30f, delay = .35f, reverb = .4f)
            16 -> config.copy(key = 5, tempo = 56f, density = .60f, percussion = true, reverb = .3f)
            else ->
                config.copy(
                    key = 10,
                    tempo = 38f,
                    density = .55f,
                    complexity = .65f,
                    reverb = .75f,
                    delay = .45f,
                )
        }

    private fun musicConfig(s: Sound, seed: Long): MusicConfig =
        music(
            MusicConfig(
                tuning = if (s.variant == 0) 432f else 440f,
                seed = seed,
                texture =
                    when (s.variant) {
                        2,
                        7,
                        15,
                        16 -> 2
                        3,
                        4,
                        14 -> 1
                        5,
                        11,
                        13 -> 3
                        10,
                        17 -> 4
                        else -> 0
                    },
            ),
            s.variant,
        )

    fun layer(id: String, gain: Float? = null, unique: String = java.util.UUID.randomUUID().toString()): Layer {
        val s = byId.getValue(id)
        val seed = unique.hashCode().toLong()
        return Layer(
            unique,
            id,
            s.name,
            gain ?: s.gain,
            width = if (s.kind == "binaural") 1f else .7f,
            intensity = s.intensity,
            brightness = s.brightness,
            eventRate = s.eventRate,
            music = if (s.music) musicConfig(s, seed) else MusicConfig(seed = seed),
        )
    }
}

object Presets {
    private fun Mix.at(master: Float) = copy(master = master)

    private fun p(name: String, vararg specs: Pair<String, Float>): Mix =
        Mix(
            Library.slug(name),
            name,
            specs.mapIndexed { i, (id, g) ->
                Library.layer(id, g, "${Library.slug(name)}-$i").let {
                    if (id == "432-hz-ambient-music" || id == "440-hz-ambient-music")
                        it.copy(music = it.music.copy(seed = 42))
                    else it
                }
            },
        )

    /**
     * Every preset is trimmed to the same measured output level (see `CatalogueQualityTest`), so
     * changing presets no longer jumps output by up to 18 dB, and no advertised layer sits more than
     * 12 dB under its neighbours.
     */
    val all =
        listOf(
            p("Quiet Skies", "432-hz-ambient-music" to .30f, "light-drizzle" to .45f).at(0.90f),
            p("Cozy Cabin", "fireplace" to .45f, "blizzard" to .25f, "gentle-breeze" to .25f).at(0.68f),
            p("Rainy Lo-fi", "lo-fi-sleep-music" to .50f, "moderate-rain" to .50f, "brown" to .15f)
                .at(0.27f),
            p("Deep Sleep Noise", "brown" to .14f, "pink" to .25f).at(0.35f),
            p("Ocean Night", "ocean-waves" to .52f, "gentle-breeze" to .23f).at(0.89f),
            p(
                    "Storm Watch",
                    "moderate-rain" to .45f,
                    "rolling-thunder" to .25f,
                    "strong-wind" to .20f,
                )
                .at(0.57f),
            p("Winter Storm", "hail" to .25f, "blizzard" to .35f, "fireplace" to .40f).at(0.66f),
            p(
                    "Forest Cabin",
                    "wind-through-pine-trees" to .30f,
                    "fireplace" to .40f,
                    "crickets" to .25f,
                )
                .at(0.69f),
            p("Space Drift", "440-hz-ambient-music" to .35f, "soft-synth-pads" to .30f).at(0.82f),
            p("Airplane Sleep", "airplane-cabin" to .40f, "brown" to .12f).at(0.38f),
            p("Mountain Stream", "rivers" to .40f, "wind-through-pine-trees" to .30f).at(0.81f),
            p("Piano Rain", "soft-piano" to .35f, "rain-on-window" to .40f).at(0.64f),
            p("Night Library", "quiet-library" to .30f, "moderate-rain" to .40f).at(0.64f),
            p("Midnight Fire", "fireplace" to .45f, "minimalist-piano" to .30f).at(0.70f),
            // Bundled recordings and the binaural tone that no earlier preset reached.
            p("Summer Night", "crickets" to .63f, "frogs" to .52f, "nighttime-forest" to .34f).at(1.00f),
            p("City Rain", "distant-city-rain" to .60f, "moderate-rain" to .45f, "distant-train" to .35f)
                .at(0.44f),
            p("Coffee Shop", "soft-coffee-shop-ambience" to .50f, "quiet-library" to .35f, "distant-city-rain" to .25f)
                .at(1.00f),
            p("Dawn Birds", "forest-birds" to .60f, "wind-in-leaves" to .45f).at(0.65f),
            p("Binaural Drift", "binaural" to .35f, "432-hz-ambient-music" to .40f).at(0.30f),
            p("Piano Only", "soft-piano" to .57f).at(1.00f),
            p(
                "Nine-Layer Dream",
                "lo-fi-sleep-music" to .45f,
                "brown" to .07f,
                "moderate-rain" to .50f,
                "blizzard" to .30f,
                "hail" to .25f,
                "rolling-thunder" to .25f,
                "wind-through-pine-trees" to .40f,
                "fireplace" to .45f,
                "440-hz-ambient-music" to .30f,
            ).at(0.30f),
        )
}
