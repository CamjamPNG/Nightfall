// SPDX-License-Identifier: MIT
package dev.nightfall.core

data class Sound(
    val id: String,
    val name: String,
    val category: String,
    val kind: String,
    val variant: Int = 0,
    val description: String = "Locally synthesized",
    val asset: String? = null,
) {
    val music
        get() = kind == "music"

    val available
        get() = kind != "unavailable"
}

object Library {
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
                    Sound(
                        n.lowercase(),
                        "$n noise",
                        "Noise",
                        "noise",
                        i,
                        if (i in 7..10)
                            "Creative configurable spectral preset; not a standardized noise color"
                        else "Continuous spectral synthesis",
                    )
                )
            }
        fun group(category: String, kind: String, names: List<String>) {
            names.forEachIndexed { i, n -> add(Sound(slug(n), n, category, kind, i)) }
        }
        group(
            "Rain",
            "rain",
            listOf(
                "Light drizzle",
                "Moderate rain",
                "Heavy rain",
                "Rain on window",
                "Rain on tent",
                "Rain on leaves",
                "Rain on metal roof",
                "Rain on car roof",
                "Rain in forest",
                "Rain over distant ocean",
            ),
        )
        group(
            "Storms",
            "thunder",
            listOf(
                "Distant thunder",
                "Rolling thunder",
                "Close thunder",
                "Thunderstorm",
                "Occasional lightning-associated thunder",
                "Tropical storm",
                "Severe rainstorm",
                "Windy thunderstorm",
            ),
        )
        group(
            "Winter",
            "winter",
            listOf(
                "Blizzard",
                "Gentle snowfall atmosphere",
                "Heavy snowfall with wind",
                "Hail",
                "Sleet",
                "Ice storm",
                "Arctic wind",
                "Snow against windows",
            ),
        )
        group(
            "Wind",
            "wind",
            listOf(
                "Gentle breeze",
                "Strong wind",
                "Wind through pine trees",
                "Wind through deciduous trees",
                "Mountain wind",
                "Ocean wind",
                "Wind against cabin walls",
                "Howling wind",
            ),
        )
        group(
            "Nature",
            "water",
            listOf("Ocean waves", "Beaches", "Streams", "Rivers", "Waterfalls", "Lakes"),
        )
        group("Nature", "wind", listOf("Wind in leaves", "Caves", "Mountain ambience"))
        group("Nature", "fire", listOf("Campfires", "Crackling wood"))
        // Field recordings are explicitly distinct from physical-model textures.
        listOf("Forest birds", "Crickets", "Frogs", "Nighttime forest").forEach { n ->
            add(
                Sound(
                    slug(n),
                    n,
                    "Nature",
                    "recording",
                    description = "Bundled field recording • see Audio credits",
                    asset = slug(n),
                )
            )
        }
        group("Indoor", "fire", listOf("Fireplace"))
        group(
            "Indoor",
            "hum",
            listOf(
                "Box fan",
                "Ceiling fan",
                "Air conditioner",
                "Air purifier",
                "Heating system",
                "Quiet room",
            ),
        )
        add(
            Sound(
                "distant-household-ambience",
                "Distant household ambience",
                "Indoor",
                "unavailable",
                description = "Recording not bundled; no substitute audio",
            )
        )
        group("Indoor", "clock", listOf("Ticking clock"))
        group(
            "Travel",
            "hum",
            listOf(
                "Airplane cabin",
                "Train interior",
                "Ship cabin",
                "Distant train",
                "Gentle vehicle ambience",
            ),
        )
        add(
            Sound(
                "quiet-library",
                "Quiet library",
                "Other",
                "recording",
                description = "Bundled field recording • see Audio credits",
                asset = "quiet-library",
            )
        )
        add(
            Sound(
                "distant-city-rain",
                "Distant city rain",
                "Other",
                "recording",
                description = "Bundled field recording • see Audio credits",
                asset = "distant-city-rain",
            )
        )
        add(
            Sound(
                "soft-coffee-shop-ambience",
                "Soft coffee-shop ambience",
                "Other",
                "recording",
                description = "Bundled field recording • see Audio credits",
                asset = "soft-coffee-shop-ambience",
            )
        )
        group("Other", "hum", listOf("Space-themed soundscape", "Gentle mechanical hum"))
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
        group("Music", "music", names)
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

    fun layer(
        id: String,
        gain: Float = .35f,
        unique: String = java.util.UUID.randomUUID().toString(),
    ): Layer {
        val s = byId.getValue(id)
        return Layer(
            unique,
            id,
            s.name,
            gain,
            width = if (s.kind == "binaural") 1f else .7f,
            music =
                MusicConfig(
                    tuning = if (s.variant == 0) 432f else 440f,
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
                            10 -> 4
                            else -> 0
                        },
                    tempo =
                        when (s.variant) {
                            4,
                            12,
                            13 -> 30f
                            15 -> 36f
                            14,
                            16 -> 54f
                            else -> 46f
                        },
                    density =
                        when (s.variant) {
                            4,
                            12,
                            13 -> .18f
                            14 -> .65f
                            else -> .4f
                        },
                    melody = s.variant !in listOf(6, 12),
                    reverb = if (s.variant in listOf(8, 9, 17)) .65f else .35f,
                    percussion = s.variant == 16,
                    seed = unique.hashCode().toLong(),
                ),
        )
    }
}

object Presets {
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

    val all =
        listOf(
            p("Quiet Skies", "432-hz-ambient-music" to .3f, "light-drizzle" to .45f),
            p("Cozy Cabin", "fireplace" to .45f, "blizzard" to .25f, "gentle-breeze" to .25f),
            p("Rainy Lo-fi", "lo-fi-sleep-music" to .5f, "moderate-rain" to .5f, "brown" to .3f),
            p("Deep Sleep Noise", "brown" to .45f, "pink" to .25f),
            p("Ocean Night", "ocean-waves" to .45f, "gentle-breeze" to .2f),
            p(
                "Storm Watch",
                "moderate-rain" to .45f,
                "rolling-thunder" to .25f,
                "strong-wind" to .2f,
            ),
            p("Winter Storm", "hail" to .2f, "blizzard" to .3f, "fireplace" to .4f),
            p(
                "Forest Cabin",
                "wind-through-pine-trees" to .3f,
                "fireplace" to .4f,
                "crickets" to .2f,
            ),
            p("Space Drift", "440-hz-ambient-music" to .35f, "soft-synth-pads" to .3f),
            p("Airplane Sleep", "airplane-cabin" to .4f, "brown" to .3f),
            p("Mountain Stream", "rivers" to .4f, "wind-through-pine-trees" to .25f),
            p("Piano Rain", "soft-piano" to .35f, "rain-on-window" to .4f),
            p("Night Library", "quiet-library" to .3f, "moderate-rain" to .4f),
            p("Midnight Fire", "fireplace" to .45f, "minimalist-piano" to .3f),
            p(
                "Nine-Layer Dream",
                "lo-fi-sleep-music" to .55f,
                "brown" to .35f,
                "moderate-rain" to .5f,
                "blizzard" to .2f,
                "hail" to .15f,
                "rolling-thunder" to .2f,
                "wind-through-pine-trees" to .3f,
                "fireplace" to .45f,
                "440-hz-ambient-music" to .2f,
            ),
        )
}
