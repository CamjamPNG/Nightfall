// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.util.Locale

/** One layer a phrase asked for, with the gain the phrase's own intensity words chose. */
data class PhraseLayer(val soundId: String, val gain: Float)

/** A concept the catalogue does not hold, and the real entry the interpreter offers instead. */
data class PhraseSubstitution(val asked: String, val soundId: String)

/** Two words that cannot both be true of one mix. */
data class PhraseConflict(val first: String, val second: String)

/**
 * What a phrase was understood to mean. Nothing here is invented: every layer is a catalogue entry,
 * every gain comes from [PhraseVocabulary.GAIN_BY_LEVEL], and anything the interpreter could not
 * place is named in [unrecognized] rather than guessed at.
 */
data class PhrasePlan(
    val phrase: String,
    val layers: List<PhraseLayer>,
    val timerMinutes: Int? = null,
    val unrecognized: List<String> = emptyList(),
    val substitutions: List<PhraseSubstitution> = emptyList(),
    val conflict: PhraseConflict? = null,
) {
    /**
     * The mix these layers make, with [layers] defaulting to everything the phrase asked for. A
     * person removes a chip before playing, so the caller passes the layers that survived.
     *
     * The name is the phrase itself, and layer IDs are derived from the sound and its position, so
     * the same phrase always encodes to the same JSON: a mix someone liked can be reproduced by
     * typing it again.
     */
    fun toMix(layers: List<PhraseLayer> = this.layers): Mix {
        val name =
            phrase.trim()
                .replace(Regex("\\s+"), " ")
                .take(200)
                .ifEmpty { "Your soundscape" }
                .replaceFirstChar { it.toString().uppercase(Locale.ROOT) }
        return Mix(
            id = "current",
            name = name,
            layers =
                layers.mapIndexed { i, l -> Library.layer(l.soundId, l.gain, "phrase-$i-${l.soundId}") },
        )
    }
}

/**
 * The seam a phrase box talks to.
 *
 * TODO: a network-assisted interpreter was considered and declined on 2026-11-19 — it would need an
 * endpoint policy and a key an APK cannot keep, so there is exactly one implementation and nothing
 * behind this interface opens a socket.
 */
interface PhraseInterpreter {
    fun interpret(phrase: String): PhrasePlan
}

/**
 * Turns a phrase in words into a mix from the catalogue, on the device, with no network and no
 * randomness. English only for now; the words below are vocabulary data and every sentence the app
 * shows a person is a string resource, so a second language is a translation plus a second
 * vocabulary rather than a rewrite.
 *
 * ## How a phrase is read
 *
 * 1. Normalise: lower-case, every non-alphanumeric character becomes a space, articles drop out.
 * 2. Take a sleep timer if one is named ("for 45 minutes", "two hours", "an hour", "30m").
 * 3. Match catalogue names and aliases longest-first, left to right. "Rain on a tin roof" is one
 *    term for `rain-on-metal-roof`, not "rain" plus something; a term never overlaps another.
 * 4. Attach intensity words to the nearest term within four words, and negation ("no", "without")
 *    to the term that follows it within two.
 * 5. Refuse a phrase that contradicts itself, and name the words it did not recognise.
 *
 * ## The intensity scale
 *
 * A word that carries intensity sets one of five levels. The gain is digital gain before `Levels`
 * trims the source to −18 LUFS at full layer gain, so one step is about 3 LU for that layer:
 *
 * | level | words                                                   | gain | relative level |
 * |-------|---------------------------------------------------------|------|----------------|
 * | 1     | faint, barely, very quiet, whisper, background          | .25  | −12.0 dB       |
 * | 2     | quiet, soft, gentle, light, subtle, calm, distant, low  | .35  | −9.1 dB        |
 * | 3     | steady, moderate — and no word at all                   | .50  | −6.0 dB        |
 * | 4     | loud, heavy, strong, hard, full, dense, intense         | .71  | −3.0 dB        |
 * | 5     | deafening, maximum, very loud, as loud as possible      | 1.00 | 0 dB           |
 *
 * Intensity comes only from words the person typed. "City rain" is that sound at level 3, because
 * the "distant" in the entry's own name *Distant city rain* is our description of the recording and
 * not their instruction; "distant city rain" said in full is level 2, because then they said it.
 * The same rule reads a catalogue name the person typed ("heavy rain" is level 4) and an alias they
 * typed ("loud thunder" is level 4). Intensity never comes from the name of the entry a phrase
 * resolves to.
 *
 * The scale changes gain, never the sound: "gentle thunder" is rolling thunder at 35%, not another
 * entry. A chip that says something the person did not ask for is worse than a quiet version of
 * what they did — the one exception is `PhraseVocabulary.SUBSTITUTIONS`, which names its trade in
 * the result instead of doing it silently.
 */
object LocalPhraseInterpreter : PhraseInterpreter {

    override fun interpret(phrase: String): PhrasePlan {
        val raw = PhraseVocabulary.normalize(phrase).split(' ').filter { it.isNotEmpty() }
        val timer = PhraseVocabulary.timer(raw)
        val words =
            raw.filterIndexed { i, w -> !timer.consumed[i] && w !in PhraseVocabulary.ARTICLES }
        val consumed = BooleanArray(words.size)
        val heard = PhraseVocabulary.terms(words, consumed)
        // "very quiet room" asks for the Quiet room entry at level 1: the term keeps its sound, and a
        // two-word intensity phrase that begins before the term sets the level, even though its last
        // word belongs to the name.
        val longModifiers = PhraseVocabulary.modifiers(words, consumed, minWords = 2, mayOverlap = true)
        val silence = PhraseVocabulary.silence(words, consumed)
        val negated = PhraseVocabulary.negated(words, heard)
        val modifiers = longModifiers + PhraseVocabulary.modifiers(words, consumed, minWords = 1)
        val assigned = PhraseVocabulary.assign(modifiers, heard)
        val kept = heard.indices.filter { !negated[it] }

        val layers =
            kept.mapNotNull { index ->
                val id = heard[index].term.soundId
                val level = assigned[index]?.level ?: heard[index].term.level ?: 3
                PhraseLayer(id, PhraseVocabulary.GAIN_BY_LEVEL[level] ?: .5f)
            }.distinctBy { it.soundId }

        return PhrasePlan(
            phrase = phrase,
            layers = layers,
            timerMinutes = timer.minutes,
            unrecognized = PhraseVocabulary.unrecognized(words, consumed),
            substitutions =
                kept.mapNotNull { index ->
                    val term = heard[index].term
                    if (term.substitution) PhraseSubstitution(term.asked, term.soundId) else null
                },
            conflict = PhraseVocabulary.conflict(heard, assigned, silence, kept),
        )
    }
}

internal object PhraseVocabulary {

    val ARTICLES = setOf("a", "an", "the")

    /**
     * Digital gain per intensity level. One step is about 3 LU for the layer: .25 = −12 dB,
     * .35 = −9.1 dB, .5 = −6 dB, .71 = −3 dB, 1.0 = 0 dB.
     */
    val GAIN_BY_LEVEL = mapOf(1 to .25f, 2 to .35f, 3 to .5f, 4 to .71f, 5 to 1f)

    /** Intensity words, longest phrase first so "very quiet" is not read as "quiet" plus noise. */
    private val INTENSITY: List<Pair<String, Int>> =
        listOf(
                "as quiet as possible" to 1,
                "very quiet" to 1,
                "very soft" to 1,
                "barely audible" to 1,
                "hint of" to 1,
                "as loud as possible" to 5,
                "very loud" to 5,
                "very heavy" to 5,
                "ear splitting" to 5,
                "faint" to 1,
                "barely" to 1,
                "whisper" to 1,
                "whispering" to 1,
                "background" to 1,
                "quiet" to 2,
                "quietly" to 2,
                "soft" to 2,
                "softly" to 2,
                "gentle" to 2,
                "gently" to 2,
                "light" to 2,
                "lightly" to 2,
                "subtle" to 2,
                "subtly" to 2,
                "calm" to 2,
                "distant" to 2,
                "far away" to 2,
                "faraway" to 2,
                "in distance" to 2,
                "hushed" to 2,
                "low" to 2,
                "mild" to 2,
                "steady" to 3,
                "moderate" to 3,
                "normal" to 3,
                "medium" to 3,
                "deafening" to 5,
                "maximum" to 5,
                "overwhelming" to 5,
                "max" to 5,
                "loud" to 4,
                "loudly" to 4,
                "heavy" to 4,
                "heavily" to 4,
                "howling" to 4,
                "strong" to 4,
                "strongly" to 4,
                "hard" to 4,
                "big" to 4,
                "full" to 4,
                "intense" to 4,
                "thick" to 4,
                "dense" to 4,
                "prominent" to 4,
                "pouring" to 4,
            )
            .map { (text, level) -> normalize(text) to level }

    /** A term that means "stop everything", not a layer. */
    private val SILENCE =
        listOf("silence", "silent", "nothing", "hush", "muted", "no sound", "no sounds")
            .map(::normalize)

    private val NEGATORS =
        setOf("no", "not", "without", "dont", "do not", "doesnt", "exclude", "except", "minus",
              "skip", "avoid")

    private val FILLER =
        setOf(
            "and", "or", "with", "plus", "also", "but", "some", "something", "anything",
            "everything", "please", "i", "im", "want", "wanted", "need", "would", "like", "love",
            "try", "make", "create", "build", "give", "play", "mix", "mixes", "sound", "sounds",
            "soundscape", "sleep", "sleeping", "sleepy", "night", "nights", "nighttime",
            "tonight", "bed", "bedtime", "rest", "relax", "relaxing", "help", "me", "my", "its",
            "fall", "asleep", "just", "really", "bit", "little", "very", "too", "more", "now",
            "go", "going", "to", "for", "of", "on", "in", "at", "by", "from", "over", "under",
            "near", "around", "up", "down", "that", "this", "it", "is", "be", "am", "get", "feel",
            "feeling", "vibe", "vibes", "once", "minute", "minutes", "min", "mins", "hour",
            "hours", "hr",
            "hrs", "h", "m", "timer",
        )

    /**
     * One thing a person can name. [asked] is the words they used, empty when the term is a
     * catalogue name; [substitution] marks a concept the catalogue lacks.
     */
    class Term(
        val tokens: List<String>,
        val soundId: String,
        val asked: String,
        val substitution: Boolean = false,
        /**
         * The level this term's own words ask for. It is read from the matched text — what the
         * person actually typed — and never from the entry's name in the catalogue, which is why
         * "city rain" is neutral even though the entry's name says "Distant city rain". A
         * substitution carries no level: it is not the sound the person named.
         */
        val level: Int? = null,
    ) {
        val size get() = tokens.size
    }

    class Heard(val start: Int, val end: Int, val term: Term)

    class Intensity(val start: Int, val end: Int, val level: Int, val word: String)

    class Assigned(val level: Int, val words: List<String>)

    /**
     * Concepts the catalogue does not hold, and the real entry each one goes to. Every one is
     * reported in `PhrasePlan.substitutions`, so the person is told what they got instead.
     */
    private val SUBSTITUTIONS =
        listOf(
            "washing machine" to "gentle-mechanical-hum",
            "tumble dryer" to "gentle-mechanical-hum",
            "dryer" to "gentle-mechanical-hum",
            "dishwasher" to "gentle-mechanical-hum",
            "refrigerator" to "gentle-mechanical-hum",
            "fridge" to "gentle-mechanical-hum",
            "freezer" to "gentle-mechanical-hum",
            "vacuum cleaner" to "gentle-mechanical-hum",
            "vacuum" to "gentle-mechanical-hum",
            "factory" to "gentle-mechanical-hum",
            "helicopter" to "airplane-cabin",
            "propeller plane" to "airplane-cabin",
            "crowd" to "soft-coffee-shop-ambience",
            "people talking" to "soft-coffee-shop-ambience",
            "chatter" to "soft-coffee-shop-ambience",
            "restaurant" to "soft-coffee-shop-ambience",
            "fountain" to "waterfalls",
            "owl" to "nighttime-forest",
            "owls" to "nighttime-forest",
            "seagulls" to "beaches",
            "gulls" to "beaches",
            "wind chimes" to "gentle-bells",
            "chimes" to "gentle-bells",
        )

    private val ALIASES =
        listOf(
            // Noise.
            "white noise" to "white",
            "white" to "white",
            "pink noise" to "pink",
            "pink" to "pink",
            "brown noise" to "brown",
            "brownian noise" to "brown",
            "brown" to "brown",
            "red noise" to "red",
            "red" to "red",
            "blue noise" to "blue",
            "blue" to "blue",
            "violet noise" to "violet",
            "violet" to "violet",
            "purple noise" to "purple",
            "purple" to "purple",
            "grey noise" to "gray",
            "gray noise" to "gray",
            "grey" to "gray",
            "gray" to "gray",
            "green noise" to "green",
            "green" to "green",
            "black noise" to "black",
            "black" to "black",
            "velvet noise" to "velvet",
            "velvet" to "velvet",
            "noise" to "brown",
            // Rain.
            "rain" to "moderate-rain",
            "rainy" to "moderate-rain",
            "steady rain" to "moderate-rain",
            "drizzle" to "light-drizzle",
            "light rain" to "light-drizzle",
            "misty rain" to "light-drizzle",
            "spitting rain" to "light-drizzle",
            "heavy rain" to "heavy-rain",
            "downpour" to "heavy-rain",
            "pouring rain" to "heavy-rain",
            "rainstorm" to "heavy-rain",
            "rain on tin roof" to "rain-on-metal-roof",
            "tin roof" to "rain-on-metal-roof",
            "metal roof" to "rain-on-metal-roof",
            "rain on roof" to "rain-on-metal-roof",
            "rain on window" to "rain-on-window",
            "rain on windows" to "rain-on-window",
            "rain against window" to "rain-on-window",
            "rain on glass" to "rain-on-window",
            "rain on tent" to "rain-on-tent",
            "tent" to "rain-on-tent",
            "rain on leaves" to "rain-on-leaves",
            "rain on car roof" to "rain-on-car-roof",
            "car roof" to "rain-on-car-roof",
            "rain on car" to "rain-on-car-roof",
            "rain in forest" to "rain-in-forest",
            "forest rain" to "rain-in-forest",
            "rainforest" to "rain-in-forest",
            "rain over ocean" to "rain-over-distant-ocean",
            "rain at sea" to "rain-over-distant-ocean",
            // Storms.
            "rolling thunder" to "rolling-thunder",
            "thunder" to "rolling-thunder",
            "rumble" to "rolling-thunder",
            "rumbling" to "rolling-thunder",
            "distant thunder" to "distant-thunder",
            "far thunder" to "distant-thunder",
            "thunder in distance" to "distant-thunder",
            "distant rumble" to "distant-thunder",
            "distant rumbling" to "distant-thunder",
            "close thunder" to "close-thunder",
            "loud thunder" to "close-thunder",
            "thunderstorm" to "thunderstorm",
            "storm" to "thunderstorm",
            "lightning storm" to "thunderstorm",
            "lightning" to "occasional-lightning-associated-thunder",
            "tropical storm" to "tropical-storm",
            "severe storm" to "severe-rainstorm",
            "severe rainstorm" to "severe-rainstorm",
            "windy storm" to "windy-thunderstorm",
            // Winter.
            "blizzard" to "blizzard",
            "snowstorm" to "blizzard",
            "snow" to "gentle-snowfall-atmosphere",
            "snowfall" to "gentle-snowfall-atmosphere",
            "snow falling" to "gentle-snowfall-atmosphere",
            "gentle snow" to "gentle-snowfall-atmosphere",
            "light snow" to "gentle-snowfall-atmosphere",
            "heavy snow" to "heavy-snowfall-with-wind",
            "snow with wind" to "heavy-snowfall-with-wind",
            "hail" to "hail",
            "hailstorm" to "hail",
            "sleet" to "sleet",
            "freezing rain" to "sleet",
            "ice storm" to "ice-storm",
            "arctic wind" to "arctic-wind",
            "polar wind" to "arctic-wind",
            "snow against windows" to "snow-against-windows",
            "snow on window" to "snow-against-windows",
            // Wind.
            "wind" to "strong-wind",
            "breeze" to "gentle-breeze",
            "gentle wind" to "gentle-breeze",
            "light wind" to "gentle-breeze",
            "soft wind" to "gentle-breeze",
            "strong wind" to "strong-wind",
            "gusty wind" to "strong-wind",
            "gusts" to "strong-wind",
            "gust" to "strong-wind",
            "pine trees" to "wind-through-pine-trees",
            "pines" to "wind-through-pine-trees",
            "pine forest" to "wind-through-pine-trees",
            "wind through pines" to "wind-through-pine-trees",
            "wind in pines" to "wind-through-pine-trees",
            "deciduous trees" to "wind-through-deciduous-trees",
            "trees" to "wind-through-deciduous-trees",
            "wind through trees" to "wind-through-deciduous-trees",
            "mountain wind" to "mountain-wind",
            "ocean wind" to "ocean-wind",
            "sea wind" to "ocean-wind",
            "coastal wind" to "ocean-wind",
            "wind against cabin" to "wind-against-cabin-walls",
            "cabin wind" to "wind-against-cabin-walls",
            "howling wind" to "howling-wind",
            "howling" to "howling-wind",
            // Water.
            "ocean" to "ocean-waves",
            "sea" to "ocean-waves",
            "waves" to "ocean-waves",
            "surf" to "ocean-waves",
            "ocean waves" to "ocean-waves",
            "beach" to "beaches",
            "beaches" to "beaches",
            "shore" to "beaches",
            "seashore" to "beaches",
            "stream" to "streams",
            "streams" to "streams",
            "brook" to "streams",
            "river" to "rivers",
            "rivers" to "rivers",
            "waterfall" to "waterfalls",
            "waterfalls" to "waterfalls",
            "lake" to "lakes",
            "lakes" to "lakes",
            "pond" to "lakes",
            "rapids" to "rapids",
            "creek" to "creeks",
            "creeks" to "creeks",
            "shingle" to "waves-on-shingle",
            "pebble beach" to "waves-on-shingle",
            "lapping water" to "lapping-water",
            "lapping" to "lapping-water",
            "water" to "streams",
            // Nature.
            "wind in leaves" to "wind-in-leaves",
            "leaves" to "wind-in-leaves",
            "rustling leaves" to "wind-in-leaves",
            "cave" to "caves",
            "caves" to "caves",
            "cavern" to "caves",
            "mountain" to "mountain-ambience",
            "mountains" to "mountain-ambience",
            "mountain ambience" to "mountain-ambience",
            "alpine" to "mountain-ambience",
            "campfire" to "campfires",
            "campfires" to "campfires",
            "crackling wood" to "crackling-wood",
            "crackling fire" to "crackling-wood",
            "crackling" to "crackling-wood",
            "crackle" to "crackling-wood",
            "embers" to "embers",
            "wood stove" to "wood-stove",
            "stove" to "wood-stove",
            "log burner" to "wood-stove",
            "fire pit" to "fire-pit",
            "firepit" to "fire-pit",
            "crackling campfire" to "campfires",
            "campfire crackling" to "campfires",
            "bonfire" to "bonfire",
            "bon fire" to "bonfire",
            "fire" to "fireplace",
            "birds" to "forest-birds",
            "bird" to "forest-birds",
            "birdsong" to "forest-birds",
            "bird song" to "forest-birds",
            "crickets" to "crickets",
            "cricket" to "crickets",
            "frogs" to "frogs",
            "frog" to "frogs",
            "forest at night" to "nighttime-forest",
            "night forest" to "nighttime-forest",
            "nighttime forest" to "nighttime-forest",
            "forest" to "nighttime-forest",
            "woods" to "nighttime-forest",
            "woodland" to "nighttime-forest",
            // Indoor.
            "fireplace" to "fireplace",
            "hearth" to "fireplace",
            "box fan" to "box-fan",
            "fan" to "box-fan",
            "pedestal fan" to "box-fan",
            "standing fan" to "box-fan",
            "ceiling fan" to "ceiling-fan",
            "air conditioner" to "air-conditioner",
            "air conditioning" to "air-conditioner",
            "air con" to "air-conditioner",
            "aircon" to "air-conditioner",
            "ac" to "air-conditioner",
            "air purifier" to "air-purifier",
            "purifier" to "air-purifier",
            "hepa" to "air-purifier",
            "heating system" to "heating-system",
            "heater" to "heating-system",
            "radiator" to "heating-system",
            "furnace" to "heating-system",
            "quiet room" to "quiet-room",
            "room tone" to "quiet-room",
            "room" to "quiet-room",
            "ticking clock" to "ticking-clock",
            "clock" to "ticking-clock",
            "ticking" to "ticking-clock",
            "tick tock" to "ticking-clock",
            // Travel.
            "airplane cabin" to "airplane-cabin",
            "airplane" to "airplane-cabin",
            "aeroplane" to "airplane-cabin",
            "plane cabin" to "airplane-cabin",
            "plane" to "airplane-cabin",
            "flight" to "airplane-cabin",
            "in flight" to "airplane-cabin",
            "jet" to "airplane-cabin",
            "train interior" to "train-interior",
            "train cabin" to "train-interior",
            "train carriage" to "train-interior",
            "ship cabin" to "ship-cabin",
            "ship" to "ship-cabin",
            "boat cabin" to "ship-cabin",
            "cruise" to "ship-cabin",
            "boat" to "ship-cabin",
            "distant train" to "distant-train",
            "train in distance" to "distant-train",
            "train" to "distant-train",
            "railway" to "distant-train",
            "car ride" to "gentle-vehicle-ambience",
            "driving" to "gentle-vehicle-ambience",
            "vehicle" to "gentle-vehicle-ambience",
            "road trip" to "gentle-vehicle-ambience",
            "car" to "gentle-vehicle-ambience",
            // Other.
            "quiet library" to "quiet-library",
            "library" to "quiet-library",
            "city rain" to "distant-city-rain",
            "distant city rain" to "distant-city-rain",
            "urban rain" to "distant-city-rain",
            "coffee shop" to "soft-coffee-shop-ambience",
            "coffeeshop" to "soft-coffee-shop-ambience",
            "cafe" to "soft-coffee-shop-ambience",
            "cafeteria" to "soft-coffee-shop-ambience",
            "space" to "space-themed-soundscape",
            "spaceship" to "space-themed-soundscape",
            "space station" to "space-themed-soundscape",
            "spacecraft" to "space-themed-soundscape",
            "outer space" to "space-themed-soundscape",
            "cosmos" to "space-themed-soundscape",
            "mechanical hum" to "gentle-mechanical-hum",
            "machine hum" to "gentle-mechanical-hum",
            "engine hum" to "gentle-mechanical-hum",
            "machinery" to "gentle-mechanical-hum",
            "hum" to "gentle-mechanical-hum",
            // Music.
            "432 hz" to "432-hz-ambient-music",
            "432hz" to "432-hz-ambient-music",
            "440 hz" to "440-hz-ambient-music",
            "440hz" to "440-hz-ambient-music",
            "lofi" to "lo-fi-sleep-music",
            "lo fi" to "lo-fi-sleep-music",
            "lofi hip hop" to "lo-fi-sleep-music",
            "chillhop" to "lo-fi-sleep-music",
            "piano" to "soft-piano",
            "soft piano" to "soft-piano",
            "piano music" to "soft-piano",
            "minimalist piano" to "minimalist-piano",
            "minimal piano" to "minimalist-piano",
            "minimalism" to "minimalist-piano",
            "music box" to "gentle-music-box",
            "lullaby box" to "gentle-music-box",
            "musicbox" to "gentle-music-box",
            "synth pads" to "soft-synth-pads",
            "pads" to "soft-synth-pads",
            "synth pad" to "soft-synth-pads",
            "warm synth" to "warm-analog-style-synth",
            "analog synth" to "warm-analog-style-synth",
            "analogue synth" to "warm-analog-style-synth",
            "warm analog" to "warm-analog-style-synth",
            "dreamy" to "dreamy-atmospheric-music",
            "atmospheric" to "dreamy-atmospheric-music",
            "ambient" to "dreamy-atmospheric-music",
            "ambience" to "dreamy-atmospheric-music",
            "strings" to "soft-strings",
            "string section" to "soft-strings",
            "bells" to "gentle-bells",
            "bell" to "gentle-bells",
            "meditation" to "meditation-drones",
            "meditation drone" to "meditation-drones",
            "drone" to "meditation-drones",
            "drones" to "meditation-drones",
            "om" to "meditation-drones",
            "singing bowl" to "singing-bowl-style-synthesis",
            "singing bowls" to "singing-bowl-style-synthesis",
            "tibetan bowls" to "singing-bowl-style-synthesis",
            "bowls" to "singing-bowl-style-synthesis",
            "lullaby" to "generative-lullabies",
            "lullabies" to "generative-lullabies",
            "slow lofi" to "slow-percussion-free-lo-fi",
            "gentle lofi" to "gentle-lo-fi",
            "cinematic" to "evolving-cinematic-ambient-textures",
            "evolving" to "evolving-cinematic-ambient-textures",
            "binaural" to "binaural",
            "binaural beats" to "binaural",
            "beats" to "binaural",
            "music" to "440-hz-ambient-music",
        )

    /** Every matched term, longest first at each position. */
    private val TERMS: Map<String, List<Term>> by lazy {
        val all = LinkedHashMap<String, Term>()
        // The catalogue's own names first, so an alias can never shadow one.
        for (sound in Library.all) {
            if (!sound.available) continue
            val tokens = tokensOf(sound.name)
            all.putIfAbsent(
                tokens.joinToString(" "),
                Term(tokens, sound.id, asked = "", level = levelOfText(tokens)),
            )
        }
        for ((text, id) in ALIASES) {
            if (Library.byId[id]?.available != true) continue
            val tokens = tokensOf(text)
            all.putIfAbsent(
                text,
                Term(tokens, id, asked = text, level = levelOfText(tokens)),
            )
        }
        for ((text, id) in SUBSTITUTIONS) {
            if (Library.byId[id]?.available != true) continue
            all.putIfAbsent(
                text,
                Term(tokensOf(text), id, asked = text, substitution = true),
            )
        }
        all.values
            .groupBy { it.tokens.first() }
            .mapValues { (_, terms) ->
                terms.sortedWith(
                    compareByDescending<Term> { it.size }
                        .thenByDescending { it.tokens.joinToString(" ").length }
                        .thenBy { it.tokens.joinToString(" ") }
                )
            }
    }

    fun tokensOf(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            // Dropped rather than split: "don't" must become the negation word "dont", not "don t".
            .replace("'", "")
            .replace("\u2019", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /** The first intensity phrase in a term's own text, longest phrase first. */
    private fun levelOfText(tokens: List<String>): Int? {
        var i = 0
        while (i < tokens.size) {
            val match =
                INTENSITY.filter { (text, _) ->
                        val parts = text.split(' ')
                        i + parts.size <= tokens.size &&
                            parts.indices.all { tokens[i + it] == parts[it] }
                    }
                    .maxByOrNull { it.first.split(' ').size }
            if (match != null) return match.second
            i++
        }
        return null
    }

    class TimerResult(val minutes: Int?, val consumed: BooleanArray)

    private val GLUED = Regex("^(\\d+)(minute|minutes|mins|min|m|hours|hour|hrs|hr|h)$")

    private val UNITS =
        mapOf(
            "minute" to 1, "minutes" to 1, "min" to 1, "mins" to 1, "m" to 1,
            "hour" to 60, "hours" to 60, "hr" to 60, "hrs" to 60, "h" to 60,
        )

    private val NUMBER_WORDS =
        mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
            "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
            "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
            "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
            "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
            "eighty" to 80, "ninety" to 90,
        )

    /**
     * Minutes for a count and a unit, clamped before multiplying so no input can overflow into a
     * negative or absurd timer. The sleep timer's own range is 1 minute to 24 hours.
     */
    private fun minutes(value: Long, unit: Long): Int =
        if (value >= 1440L) 1440 else (value * unit).coerceIn(1L, 1440L).toInt()

    /** A number at [index], and how many words it took. */
    private fun number(words: List<String>, index: Int): Pair<Int, Int>? {
        val token = words.getOrNull(index) ?: return null
        token.toIntOrNull()?.let { return it to 1 }
        val value = NUMBER_WORDS[token] ?: return null
        val next = words.getOrNull(index + 1)?.let { NUMBER_WORDS[it] }
        return if (value >= 20 && next != null && next < 10) value + next to 2 else value to 1
    }

    /** "for 45 minutes", "two hours", "an hour", "half an hour", "30m". Keeps the first match. */
    fun timer(words: List<String>): TimerResult {
        val consumed = BooleanArray(words.size)
        for (i in words.indices) {
            GLUED.matchEntire(words[i])?.let { match ->
                val unit = UNITS.getValue(match.groupValues[2]).toLong()
                val value = match.groupValues[1].toLongOrNull() ?: return@let
                consumed[i] = true
                return TimerResult(minutes(value, unit), consumed)
            }
            if (words[i] == "half" && words.getOrNull(i + 1) in setOf("hour", "an", "a")) {
                val unitIndex = if (words.getOrNull(i + 1) == "hour") i + 1 else i + 2
                if (words.getOrNull(unitIndex) in setOf("hour", "hours")) {
                    for (k in i..unitIndex) consumed[k] = true
                    return TimerResult(30, consumed)
                }
            }
            val (value, length) =
                if (words[i] in setOf("a", "an") && words.getOrNull(i + 1) == "hour") 1 to 1
                else number(words, i) ?: continue
            val unitIndex = i + length
            val perUnit = UNITS[words.getOrNull(unitIndex)] ?: continue
            for (k in i..unitIndex) consumed[k] = true
            return TimerResult(minutes(value.toLong(), perUnit.toLong()), consumed)
        }
        return TimerResult(null, consumed)
    }

    /** Catalogue names and aliases, longest-first, left to right, never overlapping. */
    fun terms(words: List<String>, consumed: BooleanArray): List<Heard> {
        val out = ArrayList<Heard>()
        var i = 0
        while (i < words.size) {
            if (consumed[i]) {
                i++
                continue
            }
            var found: Term? = null
            for (term in TERMS[words[i]].orEmpty()) {
                if (i + term.size > words.size) continue
                var ok = true
                for (k in 0 until term.size) {
                    if (consumed[i + k] || words[i + k] != term.tokens[k]) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    found = term
                    break
                }
            }
            if (found == null) {
                i++
                continue
            }
            for (k in 0 until found.size) consumed[i + k] = true
            out += Heard(i, i + found.size, found)
            i += found.size
        }
        return out
    }

    /** Silence words, longest phrase first. Returns the words the person used. */
    fun silence(words: List<String>, consumed: BooleanArray): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            if (consumed[i]) {
                i++
                continue
            }
            val match =
                SILENCE.filter { phrase ->
                        val tokens = phrase.split(' ')
                        i + tokens.size <= words.size &&
                            tokens.indices.all { !consumed[i + it] && words[i + it] == tokens[it] }
                    }
                    .maxByOrNull { it.split(' ').size }
            if (match == null) {
                i++
                continue
            }
            val size = match.split(' ').size
            for (k in 0 until size) consumed[i + k] = true
            out += match
            i += size
        }
        return out
    }

    /** A negation word suppresses the one term that follows it within two words. */
    fun negated(words: List<String>, heard: List<Heard>): BooleanArray {
        val out = BooleanArray(heard.size)
        for (i in words.indices) {
            if (words[i] !in NEGATORS) continue
            val target =
                heard.indices.filter { heard[it].start > i && heard[it].start <= i + 2 }
                    .minByOrNull { heard[it].start }
            if (target != null) out[target] = true
        }
        return out
    }

    /**
     * Intensity words anywhere a term did not already claim them, longest phrase first. [minWords]
     * lets the caller read every multiword phrase before the catalogue names run, and the single
     * words after them.
     */
    fun modifiers(
        words: List<String>,
        consumed: BooleanArray,
        minWords: Int,
        mayOverlap: Boolean = false,
    ): List<Intensity> {
        val out = ArrayList<Intensity>()
        var i = 0
        while (i < words.size) {
            if (consumed[i]) {
                i++
                continue
            }
            val match =
                INTENSITY.filter { (text, _) ->
                        val tokens = text.split(' ')
                        val size = tokens.size
                        val wanted = if (minWords == 1) size == 1 else size >= minWords
                        wanted &&
                            i + size <= words.size &&
                            tokens.indices.all {
                                (mayOverlap || !consumed[i + it]) && words[i + it] == tokens[it]
                            }
                    }
                    .maxByOrNull { it.first.split(' ').size }
            if (match == null) {
                i++
                continue
            }
            val size = match.first.split(' ').size
            for (k in 0 until size) consumed[i + k] = true
            out += Intensity(i, i + size, match.second, match.first)
            i += size
        }
        return out
    }

    /**
     * Each intensity word belongs to the nearest term within four words; on a tie it belongs to the
     * term after it, because English puts the modifier first ("heavy rain").
     */
    fun assign(modifiers: List<Intensity>, heard: List<Heard>): Map<Int, Assigned> {
        class Hit(val index: Int, val gap: Int, val intensity: Intensity)

        val hits = HashMap<Int, MutableList<Hit>>()
        for (m in modifiers) {
            var best: Hit? = null
            for (index in heard.indices) {
                val h = heard[index]
                val gap =
                    when {
                        m.end <= h.start -> h.start - m.end
                        h.end <= m.start -> m.start - h.end
                        else -> 0
                    }
                if (gap > 4) continue
                if (best == null ||
                    gap < best.gap ||
                    (gap == best.gap && h.start > heard[best.index].start)
                )
                    best = Hit(index, gap, m)
            }
            if (best != null) hits.getOrPut(best.index) { ArrayList() } += best
        }
        return hits.mapValues { (_, list) ->
            val closest = list.minWith(compareBy<Hit> { it.gap }.thenByDescending { it.intensity.start })
            Assigned(closest.intensity.level, list.sortedBy { it.intensity.start }.map { it.intensity.word })
        }
    }

    /**
     * Two ways a phrase can contradict itself: opposite intensity words on one layer, or a silence
     * word beside a requested layer. It is reported, never resolved by dropping a word quietly.
     */
    fun conflict(
        heard: List<Heard>,
        assigned: Map<Int, Assigned>,
        silence: List<String>,
        kept: List<Int>,
    ): PhraseConflict? {
        for (index in kept) {
            val words = assigned[index]?.words ?: continue
            val quiet = words.firstOrNull { levelOf(it) in 1..2 }
            val loud = words.firstOrNull { levelOf(it) in 4..5 }
            if (quiet != null && loud != null) return PhraseConflict(loud, quiet)
        }
        if (silence.isNotEmpty() && kept.isNotEmpty()) {
            val silenceWord = silence.first()
            val loud =
                kept.firstNotNullOfOrNull { index ->
                    assigned[index]?.words?.firstOrNull { levelOf(it) in 4..5 }
                }
            return if (loud != null) PhraseConflict(loud, silenceWord)
            else
                PhraseConflict(
                    silenceWord,
                    Library.byId[heard[kept.first()].term.soundId]?.name ?: silenceWord,
                )
        }
        return null
    }

    private fun levelOf(word: String): Int =
        INTENSITY.firstOrNull { it.first == word }?.second ?: 3

    /** Words that are neither a name, an intensity, a negation nor filler. */
    fun unrecognized(words: List<String>, consumed: BooleanArray): List<String> {
        val out = LinkedHashSet<String>()
        for (i in words.indices) {
            val word = words[i]
            if (consumed[i] || word in FILLER || word in NEGATORS) continue
            if (word.toIntOrNull() != null || word.length < 2) continue
            out += word
        }
        return out.toList()
    }
}
