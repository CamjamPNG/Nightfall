// SPDX-License-Identifier: MIT
package dev.nightfall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phrase interpreter as a corpus: the exact layers and levels each phrase must become, plus the
 * cases a parser gets wrong quietly — a sound the catalogue does not have, two words that contradict
 * each other, nothing recognisable, and negation.
 *
 * Gains are the [LocalPhraseInterpreter] scale (.25 / .35 / .5 / .71 / 1.0); a level that is not one
 * of those five fails the last test, so the corpus cannot drift from the documented table.
 */
class PhraseInterpreterTest {

    private data class Expect(
        val phrase: String,
        val layers: List<Pair<String, Float>> = emptyList(),
        val timer: Int? = null,
        val conflict: Pair<String, String>? = null,
        val unrecognized: List<String> = emptyList(),
        val substitutions: List<Pair<String, String>> = emptyList(),
    )

    /** Every phrase in the brief, every phrasing that was argued with, and the awkward cases. */
    private val corpus =
        listOf(
            // The brief's own examples.
            Expect(
                "heavy rain and a fan",
                listOf("heavy-rain" to .71f, "box-fan" to .5f),
            ),
            Expect(
                "forest at night with gentle thunder",
                listOf("nighttime-forest" to .5f, "rolling-thunder" to .35f),
            ),
            Expect(
                "rain on a tin roof with a distant rumble",
                listOf("rain-on-metal-roof" to .5f, "distant-thunder" to .35f),
            ),
            Expect("quiet ocean, no music", listOf("ocean-waves" to .35f)),
            // The intensity words, each on its own.
            Expect("deafening rain", listOf("moderate-rain" to 1f)),
            Expect("loud fan", listOf("box-fan" to .71f)),
            Expect("gentle rain", listOf("moderate-rain" to .35f)),
            Expect("faint rain", listOf("moderate-rain" to .25f)),
            Expect("quiet rain and loud wind", listOf("moderate-rain" to .35f, "strong-wind" to .71f)),
            Expect("very light breeze", listOf("gentle-breeze" to .35f)),
            Expect("distant thunder", listOf("distant-thunder" to .35f)),
            Expect("heavy rain, quiet thunder", listOf("heavy-rain" to .71f, "rolling-thunder" to .35f)),
            // Names that carry their own intensity, with no modifier at all.
            Expect("rain", listOf("moderate-rain" to .5f)),
            Expect("heavy rain", listOf("heavy-rain" to .71f)),
            Expect("light drizzle", listOf("light-drizzle" to .35f)),
            Expect("thunder", listOf("rolling-thunder" to .5f)),
            Expect("loud thunder", listOf("close-thunder" to .71f)),
            Expect("blizzard with howling wind", listOf("blizzard" to .5f, "howling-wind" to .71f)),
            // Surfaces are one sound, not "rain" plus a roof.
            Expect("rain on window", listOf("rain-on-window" to .5f)),
            Expect("rain on a car roof", listOf("rain-on-car-roof" to .5f)),
            Expect("rain in the forest", listOf("rain-in-forest" to .5f)),
            // Catalogue names that would otherwise be caught by a shorter alias.
            Expect("ocean wind", listOf("ocean-wind" to .5f)),
            Expect("mountain wind", listOf("mountain-wind" to .5f)),
            Expect("fire pit", listOf("fire-pit" to .5f)),
            Expect("quiet room", listOf("quiet-room" to .35f)),
            Expect("space ambience", listOf("space-ambience" to .5f)),
            Expect("crackling campfire", listOf("campfires" to .5f)),
            // Whole mixes of the kinds people actually type.
            Expect("storm", listOf("thunderstorm" to .5f)),
            Expect("thunderstorm and hail", listOf("thunderstorm" to .5f, "hail" to .5f)),
            Expect("brown noise with fan on low", listOf("brown" to .5f, "box-fan" to .35f)),
            Expect("soft piano and rain on window", listOf("soft-piano" to .5f, "rain-on-window" to .5f)),
            Expect("quiet piano", listOf("soft-piano" to .35f)),
            Expect("fireplace and crickets", listOf("fireplace" to .5f, "crickets" to .5f)),
            Expect("coffee shop", listOf("soft-coffee-shop-ambience" to .5f)),
            Expect(
                "city rain with a distant train",
                listOf("distant-city-rain" to .35f, "distant-train" to .35f),
            ),
            Expect(
                "crickets and frogs at night",
                listOf("crickets" to .5f, "frogs" to .5f),
            ),
            Expect("lofi", listOf("lo-fi-sleep-music" to .5f)),
            Expect("binaural beats", listOf("binaural" to .5f)),
            Expect("music", listOf("440-hz-ambient-music" to .5f)),
            Expect("wind through pine trees", listOf("wind-through-pine-trees" to .5f)),
            Expect("airplane cabin", listOf("airplane-cabin" to .5f)),
            // A sound the catalogue does hold, named the singular way round.
            Expect("waterfall", listOf("waterfalls" to .5f)),
            // A sound it does not hold: the closest entry, and the trade is named.
            Expect(
                "washing machine",
                listOf("gentle-mechanical-hum" to .5f),
                substitutions = listOf("washing machine" to "gentle-mechanical-hum"),
            ),
            Expect(
                "helicopter",
                listOf("airplane-cabin" to .5f),
                substitutions = listOf("helicopter" to "airplane-cabin"),
            ),
            // Negation.
            Expect("rain without thunder", listOf("moderate-rain" to .5f)),
            Expect("no thunder, just rain", listOf("moderate-rain" to .5f)),
            Expect("no music"),
            Expect("rain but no thunder", listOf("moderate-rain" to .5f)),
            // Timers.
            Expect("rain for 45 minutes", listOf("moderate-rain" to .5f), timer = 45),
            Expect("two hours of ocean waves", listOf("ocean-waves" to .5f), timer = 120),
            Expect("an hour of rain", listOf("moderate-rain" to .5f), timer = 60),
            Expect("half an hour of fan", listOf("box-fan" to .5f), timer = 30),
            Expect("rain for 30m", listOf("moderate-rain" to .5f), timer = 30),
            Expect("eight hours of brown noise", listOf("brown" to .5f), timer = 480),
            // Contradictions are refused, not resolved.
            Expect(
                "loud rain, silent",
                listOf("moderate-rain" to .71f),
                conflict = "loud" to "silent",
            ),
            Expect(
                "rain, quiet and loud at once",
                listOf("moderate-rain" to .35f),
                conflict = "loud" to "quiet",
            ),
            Expect(
                "silent rain",
                listOf("moderate-rain" to .5f),
                conflict = "silent" to "Moderate rain",
            ),
            // Nothing to build from.
            Expect("zzzz qqqq", unrecognized = listOf("zzzz", "qqqq")),
            Expect("something to help me sleep"),
            Expect("silence"),
            Expect(""),
        )

    @Test
    fun theCorpusParsesToTheDocumentedMix() {
        val failures = ArrayList<String>()
        for (case in corpus) {
            val plan = LocalPhraseInterpreter.interpret(case.phrase)
            val actual =
                plan.layers
                    .map { it.soundId to it.gain }
                    .sortedBy { it.first }
                    .joinToString { "${it.first}@${it.second}" }
            val expected = case.layers.sortedBy { it.first }.joinToString { "${it.first}@${it.second}" }
            println(
                "PARSE “${case.phrase}” -> " +
                    plan.layers.joinToString(prefix = "[", postfix = "]") { "${it.soundId}@${it.gain}" } +
                    (plan.timerMinutes?.let { " timer=${it}min" } ?: "") +
                    (plan.conflict?.let { " conflict=${it.first}/${it.second}" } ?: "") +
                    (plan.unrecognized.takeIf { it.isNotEmpty() }?.let { " unknown=$it" } ?: "") +
                    (plan.substitutions.takeIf { it.isNotEmpty() }
                        ?.let { " substituted=$it" } ?: "")
            )
            if (actual != expected)
                failures += "“${case.phrase}”: expected [$expected], parsed [$actual]"
            if (plan.timerMinutes != case.timer)
                failures += "“${case.phrase}”: expected timer ${case.timer}, parsed ${plan.timerMinutes}"
            val conflict = plan.conflict?.let { it.first to it.second }
            if (conflict != case.conflict)
                failures += "“${case.phrase}”: expected conflict ${case.conflict}, parsed $conflict"
            if (plan.unrecognized != case.unrecognized)
                failures +=
                    "“${case.phrase}”: expected unrecognized ${case.unrecognized}, " +
                        "parsed ${plan.unrecognized}"
            val subs = plan.substitutions.map { it.asked to it.soundId }
            if (subs != case.substitutions)
                failures += "“${case.phrase}”: expected substitutions ${case.substitutions}, parsed $subs"
        }
        assertTrue(failures.joinToString("\n", prefix = "${failures.size} phrase(s) parsed wrong:\n"), failures.isEmpty())
    }

    /**
     * Every available catalogue entry can be asked for by its own name and comes back as itself. This
     * is what catches an alias that shadows a name, and a name whose own words would otherwise be
     * read as two sounds.
     */
    @Test
    fun everyCatalogueEntryIsReachableByItsOwnName() {
        for (sound in Library.all.filter { it.available }) {
            val plan = LocalPhraseInterpreter.interpret(sound.name)
            assertEquals(
                "“${sound.name}” did not resolve to its own entry",
                listOf(sound.id),
                plan.layers.map { it.soundId },
            )
            assertNull("“${sound.name}” invented a conflict", plan.conflict)
            assertTrue("“${sound.name}” left words unrecognised", plan.unrecognized.isEmpty())
        }
    }

    /**
     * The same phrase twice is the same mix, byte for byte, including the music seeds: a night
     * someone liked can be typed again and heard again.
     */
    @Test
    fun theSamePhraseAlwaysProducesTheSameMix() {
        for (case in corpus.filter { it.layers.isNotEmpty() && it.conflict == null }) {
            val first = PresetJson.json.encodeToString(LocalPhraseInterpreter.interpret(case.phrase).toMix())
            val second = PresetJson.json.encodeToString(LocalPhraseInterpreter.interpret(case.phrase).toMix())
            assertEquals("“${case.phrase}” is not reproducible", first, second)
        }
    }

    /** Every gain a plan can hold is on the documented scale, and inside the layer's legal range. */
    @Test
    fun everyGainIsOnTheDocumentedScale() {
        val scale = PhraseVocabulary.GAIN_BY_LEVEL.values.toSet()
        for (case in corpus) {
            for (layer in LocalPhraseInterpreter.interpret(case.phrase).layers) {
                assertTrue("${layer.soundId} at ${layer.gain} is off the scale", layer.gain in scale)
                assertTrue("${layer.soundId} is not in the catalogue", Library.byId.containsKey(layer.soundId))
                assertTrue("${layer.soundId} is unavailable", Library.byId.getValue(layer.soundId).available)
            }
        }
    }

    /** A phrase that names no layer produces an empty mix, never a default one. */
    @Test
    fun anEmptyPlanHasNoLayer() {
        for (phrase in listOf("", "   ", "something cozy", "zzzz qqqq", "no music")) {
            val plan = LocalPhraseInterpreter.interpret(phrase)
            assertTrue("“$phrase” produced ${plan.layers.size} layer(s)", plan.layers.isEmpty())
        }
        assertFalse(LocalPhraseInterpreter.interpret("rain").layers.isEmpty())
    }

    /** Removing a chip removes exactly that layer and nothing else, and the mix is still buildable. */
    @Test
    fun aRemovedLayerLeavesTheRest() {
        val plan = LocalPhraseInterpreter.interpret("heavy rain and a fan")
        val kept = plan.layers.filterNot { it.soundId == "box-fan" }
        val mix = plan.toMix(kept)
        assertEquals(listOf("heavy-rain"), mix.layers.map { it.soundId })
        assertEquals("Heavy rain and a fan", mix.name)
    }
}
