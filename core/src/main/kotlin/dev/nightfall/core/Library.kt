// SPDX-License-Identifier: MIT
package dev.nightfall.core

/**
 * One catalogue entry.
 *
 * [intensity], [brightness] and [eventRate] are the layer defaults for the knobs the synthesis for
 * [kind] actually reads, so a name behaves the way it is named: a strong wind is brighter than a
 * breeze, heavy rain has denser drops than drizzle, ice storm lands harder than sleet, and thunder's
 * interval follows how frequent the name says it is. Output level is not decided here: `Levels` owns
 * that, so changing a source's character does not change how loud its slider is (re-run
 * `LevelCalibrationTest` when these defaults change).
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

    fun layer(id: String, gain: Float = .35f, unique: String = java.util.UUID.randomUUID().toString()): Layer {
        val s = byId.getValue(id)
        val seed = unique.hashCode().toLong()
        return Layer(
            unique,
            id,
            s.name,
            gain,
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
     * Every preset is trimmed to the same measured loudness — −24 LUFS short-term at 48 kHz — so
     * switching presets no longer jumps, and every advertised layer sits within 12 LU of the loudest
     * layer it is mixed with, which is also the widest gain ratio the presets use. The layer gains set balance; `master` is the per-preset level trim, and the one
     * preset that cannot reach the target through gains alone (ocean-waves would exceed 1.0) carries
     * the remainder there. Measured by `CatalogueQualityTest`.
     */
    val all =
        listOf(
            p("Quiet Skies", "432-hz-ambient-music" to .56f, "light-drizzle" to .85f).at(.50f),
            p("Cozy Cabin", "fireplace" to .80f, "blizzard" to .45f, "gentle-breeze" to .45f)
                .at(.50f),
            p("Rainy Lo-fi", "lo-fi-sleep-music" to .68f, "moderate-rain" to .68f, "brown" to .38f)
                .at(.50f),
            p("Deep Sleep Noise", "brown" to .60f, "pink" to .25f).at(.74f),
            p("Ocean Night", "ocean-waves" to 1f, "gentle-breeze" to .44f).at(.62f),
            p(
                    "Storm Watch",
                    "moderate-rain" to .87f,
                    "rolling-thunder" to .48f,
                    "strong-wind" to .39f,
                )
                .at(.50f),
            p("Winter Storm", "hail" to .44f, "blizzard" to .62f, "fireplace" to .70f).at(.50f),
            p(
                    "Forest Cabin",
                    "wind-through-pine-trees" to .50f,
                    "fireplace" to .67f,
                    "crickets" to .42f,
                )
                .at(.50f),
            p("Space Drift", "440-hz-ambient-music" to .79f, "soft-synth-pads" to .68f).at(.50f),
            p("Airplane Sleep", "airplane-cabin" to .94f, "brown" to .28f).at(.50f),
            p("Mountain Stream", "rivers" to .80f, "wind-through-pine-trees" to .60f).at(.50f),
            p("Piano Rain", "soft-piano" to .64f, "rain-on-window" to .74f).at(.50f),
            p("Night Library", "quiet-library" to .59f, "moderate-rain" to .79f).at(.50f),
            p("Midnight Fire", "fireplace" to .79f, "minimalist-piano" to .53f).at(.50f),
            // Bundled recordings and the binaural tone that no earlier preset reached.
            p("Summer Night", "crickets" to .80f, "frogs" to .66f, "nighttime-forest" to .55f)
                .at(.50f),
            p("City Rain", "distant-city-rain" to .71f, "moderate-rain" to .54f, "distant-train" to .42f)
                .at(.50f),
            p("Coffee Shop", "soft-coffee-shop-ambience" to .73f, "quiet-library" to .51f, "distant-city-rain" to .37f)
                .at(.50f),
            p("Dawn Birds", "forest-birds" to .90f, "wind-in-leaves" to .68f).at(.50f),
            p("Binaural Drift", "binaural" to .69f, "432-hz-ambient-music" to .79f).at(.50f),
            p("Piano Only", "soft-piano" to .90f).at(.50f),
            p(
                "Nine-Layer Dream",
                "lo-fi-sleep-music" to .45f,
                "brown" to .30f,
                "moderate-rain" to .50f,
                "blizzard" to .34f,
                "hail" to .30f,
                "rolling-thunder" to .45f,
                "wind-through-pine-trees" to .40f,
                "fireplace" to .45f,
                "440-hz-ambient-music" to .30f,
            ).at(.45f),
        )
}
