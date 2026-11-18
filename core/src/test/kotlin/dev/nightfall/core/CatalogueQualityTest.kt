// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Catalogue and preset quality: entry identity, in-preset layer balance, preset loudness parity and
 * preset headroom. Level calibration itself belongs to `Levels` and is covered by
 * `LoudnessTargetTest`; nothing here re-tests it.
 */
class CatalogueQualityTest {
    /** Seed draws averaged per preset: the stochastic layers make one draw a lottery, not a level. */
    private val presetDraws = 4

    /** Independent layer seeds per music profile: one seed can repeat two phrases by chance. */
    private val MUSIC_DRAWS = 5

    private val loader = M.assetLoader()

    /**
     * Entries that intentionally run the same synthesis, and say so in their description: two pairs of
     * synonyms for one noise spectrum, and one water bed shared by six names.
     */
    private val documentedAliases =
        listOf(
            setOf("brown", "red"),
            setOf("violet", "purple"),
            setOf("ocean-waves", "beaches"),
            setOf("streams", "rivers", "waterfalls", "lakes"),
        )

    private fun documented(a: String, b: String) = documentedAliases.any { a in it && b in it }

    private fun pairDifference(a: String, b: String, seconds: Double = 3.0): Double {
        val sa = CatalogueMetrics.solo(a, seconds)
        val sb = CatalogueMetrics.solo(b, seconds)
        return CatalogueMetrics.relativeDifference(sa, sb)
    }

    @Test
    fun noEntryRepeatsAnotherUnlessTheCatalogueSaysSo() {
        val identical = ArrayList<String>()
        var closest = Double.MAX_VALUE
        var closestPair = ""
        for ((kind, sounds) in Library.all.filter { it.available }.groupBy { it.kind }) {
            for (i in sounds.indices) {
                for (j in i + 1 until sounds.size) {
                    val a = sounds[i].id
                    val b = sounds[j].id
                    val rel = pairDifference(a, b)
                    if (rel < closest) {
                        closest = rel
                        closestPair = "$a~$b"
                    }
                    if (rel <= .01) identical += "$kind:$a~$b"
                }
            }
        }
        println("closest pair $closestPair measured ${CatalogueMetrics.fmt(closest, 4)}")
        val undocumented =
            identical.filterNot { documented(it.substringAfter(':').substringBefore('~'), it.substringAfter('~')) }
        assertTrue("Undocumented identical entries: $undocumented", undocumented.isEmpty())
        assertEquals(
            "The catalogue changed shape; the identical set must stay documented: $identical",
            9,
            identical.size,
        )
    }

    @Test
    fun familiesWithADensityOrRumbleKnobAreDistinct() {
        val measurable = setOf("rain", "thunder", "fire", "hum", "music")
        var worst = Double.MAX_VALUE
        var worstPair = ""
        for ((kind, sounds) in Library.all.filter { it.available }.groupBy { it.kind }) {
            if (kind !in measurable) continue
            for (i in sounds.indices) {
                for (j in i + 1 until sounds.size) {
                    val rel = pairDifference(sounds[i].id, sounds[j].id)
                    if (rel < worst) {
                        worst = rel
                        worstPair = "${sounds[i].id}~${sounds[j].id}"
                    }
                }
            }
        }
        println("least distinct measurable pair $worstPair measured ${CatalogueMetrics.fmt(worst, 4)}")
        assertTrue("$worstPair is too close: ${CatalogueMetrics.fmt(worst, 4)}", worst >= .1)
    }

    @Test
    fun everyNameThatVariesABehaviouralKnobVariesTheSound() {
        // Named pairs whose difference is a synthesis parameter the catalogue sets per entry, measured
        // as spectral distance rather than level. These are the families that used to be identical.
        val pairs =
            listOf(
                "light-drizzle" to "heavy-rain",
                "rain-on-leaves" to "rain-on-metal-roof",
                "campfires" to "crackling-wood",
                "caves" to "howling-wind",
                "gentle-breeze" to "strong-wind",
            )
        for ((a, b) in pairs) {
            val distance =
                CatalogueMetrics.bandDistance(
                    CatalogueMetrics.bandProfile(CatalogueMetrics.solo(a, 20.0)),
                    CatalogueMetrics.bandProfile(CatalogueMetrics.solo(b, 20.0)),
                )
            println("$a vs $b: ${CatalogueMetrics.fmt(distance, 2)} dB mean band distance")
            assertTrue("$a and $b differ by only ${CatalogueMetrics.fmt(distance, 2)} dB", distance >= .5)
        }
    }

    @Test
    fun stormFrequencyFollowsTheName() {
        // Thunder names swap event rate and keep the rain bed, so the difference is temporal, not
        // spectral. Measured as the share of 100 ms frames louder than the median by 8 dB.
        val names = listOf("occasional-lightning-associated-thunder", "severe-rainstorm")
        val density = names.associateWith { CatalogueMetrics.eventDensity(CatalogueMetrics.solo(it, 90.0)) }
        println("event density: ${density.mapValues { CatalogueMetrics.fmt(it.value * 100, 1) + "%" }}")
        assertTrue(
            "Occasional lightning fires as often as a severe rainstorm: $density",
            density.getValue(names[0]) < density.getValue(names[1]) * .6,
        )
    }

    @Test
    fun noPresetLayerIsBuried() {
        // Loudness, not peak: after `Levels` a layer's contribution is its gain, so this measures what
        // the sleeper hears of each advertised layer rather than how spiky its transients are.
        var worst = 0.0
        var worstName = ""
        for (p in Presets.all) {
            val perLayer = LinkedHashMap<String, Double>()
            for (layer in p.layers) {
                val signal = M.render(p.copy(layers = listOf(layer)), 20.0, loader)
                perLayer[layer.soundId] = M.shortTermLufs(signal, 3.0)
            }
            val spread = perLayer.values.max() - perLayer.values.min()
            if (spread > worst) {
                worst = spread
                worstName = "${p.id} $perLayer"
            }
        }
        println("worst in-preset layer spread ${CatalogueMetrics.fmt(worst)} LU: $worstName")
        assertTrue("A preset layer sits ${CatalogueMetrics.fmt(worst)} LU under its neighbour", worst <= 12.0)
    }

    @Test
    fun presetsShareOneOutputLevel() {
        val levels = LinkedHashMap<String, Double>()
        for (p in Presets.all) {
            // Wind, snow and the event sources run on randomised schedules, so one window of one
            // seed is a single draw of that schedule: measured spread between 20 s draws reaches
            // 3 LU on the weather-heavy presets. A minute per draw, averaged over four seeds, is
            // the preset's expectation rather than one roll of its weather.
            val taken = DoubleArray(presetDraws) { n ->
                M.integratedLufs(
                    M.render(
                        if (n == 0) p
                        else
                            p.copy(
                                layers =
                                    p.layers.mapIndexed { i, l ->
                                        l.copy(
                                            id = "${l.id}-$n",
                                            music = l.music.copy(seed = l.music.seed + 101 * i * n),
                                        )
                                    }
                            ),
                        60.0,
                        loader,
                    )
                )
            }
            println(
                "  %-22s %5.1f LUFS, draws %5.1f..%5.1f"
                    .format(p.id, taken.average(), taken.min(), taken.max())
            )
            levels[p.id] = taken.average()
        }
        val min = levels.minBy { it.value }
        val max = levels.maxBy { it.value }
        println(
            "preset loudness spans ${CatalogueMetrics.fmt(max.value - min.value)} LU: " +
                "${min.key} ${CatalogueMetrics.fmt(min.value)} to ${max.key} ${CatalogueMetrics.fmt(max.value)}"
        )
        assertTrue(
            "Presets differ by ${CatalogueMetrics.fmt(max.value - min.value)} LU; switching presets jumps",
            max.value - min.value <= 1.0,
        )
    }

    @Test
    fun presetsLeaveHeadroomForTheSoftKnee() {
        var peak = -Double.MAX_VALUE
        var name = "none"
        for (p in Presets.all) {
            val measured = M.truePeakDb(M.render(p, 20.0, loader))
            if (measured > peak) {
                peak = measured
                name = p.id
            }
        }
        println("loudest preset peak ${CatalogueMetrics.fmt(peak)} dBTP ($name)")
        assertTrue("$name peaks at ${CatalogueMetrics.fmt(peak)} dBTP", peak <= -6.0)
    }

    @Test
    fun bundledRecordingsDoNotReplayOnAFixedPeriod() {
        val report = StringBuilder()
        for (s in Library.all.filter { it.kind == "recording" }) {
            val samples = (java.io.File("../app/src/pcm/assets/audio/${s.asset}.pcm").length() / 2).toInt()
            val cycle = RecordingSource.cycleSeconds(samples)
            val shortest = RecordingSource.shortestCycleSeconds(samples)
            println("%s shortest cycle %.1fs of %.1fs".format(s.id, shortest, cycle))
            assertTrue("${s.id} can repeat after only %.1fs".format(shortest), shortest >= 180.0)
            val mix = Mix(layers = listOf(Library.layer(s.id, .35f, "loop-probe")))
            val atCycle = CatalogueMetrics.selfCorrelation(mix, cycle, 12.0, 24000)
            val control = CatalogueMetrics.selfCorrelation(mix, cycle / 2, 12.0, 24000)
            report.append(
                "%s cycle %.1fs corr %.3f control %.3f\n"
                    .format(s.id, cycle, atCycle, control)
            )
            assertTrue("${s.id} still repeats its cycle (correlation $atCycle)", atCycle < .8)
            assertTrue("${s.id} control lag correlates $control", abs(control) < .2)
        }
        println(report)
    }

    @Test
    fun windDoesNotBreatheOnAFixedCycle() {
        // Wind's level used two fixed sinusoids (251 s and 88.5 s), so the gust pattern returned
        // exactly. Measured as the largest envelope self-similarity anywhere in 60-400 s.
        for (id in listOf("gentle-breeze", "strong-wind")) {
            val (lag, correlation) =
                CatalogueMetrics.envelopeCorrelationMax(
                    Mix(layers = listOf(Library.layer(id, 1f, "gust-$id"))),
                    600.0,
                )
            println("%s peak envelope correlation %.3f at %.0fs".format(id, correlation, lag))
            assertTrue("$id breathes on a %.0fs cycle (%.3f)".format(lag, correlation), abs(correlation) < .6)
        }
    }

    @Test
    fun musicDoesNotReturnToTheSameProgressionOnAFixedPeriod() {
        // Every music profile changed chord on a fixed eight-beat grid through one four-chord
        // progression, so a whole phrase returned every 32 beats. Measured as the correlation of one
        // full phrase against the next, over five independent layer seeds, medians below.
        val report = StringBuilder()
        for (s in Library.all.filter { it.kind == "music" }) {
            val atCycle = DoubleArray(MUSIC_DRAWS)
            val control = DoubleArray(MUSIC_DRAWS)
            for (draw in 0 until MUSIC_DRAWS) {
                val layer = Library.layer(s.id, .35f, "music-loop-$draw")
                val beat = 60.0 / layer.music.tempo
                val mix = Mix(layers = listOf(layer))
                atCycle[draw] = CatalogueMetrics.selfCorrelation(mix, beat * 32, beat * 32, 12000)
                control[draw] =
                    CatalogueMetrics.selfCorrelation(mix, beat * 34, beat * 32, 12000)
            }
            atCycle.sort()
            control.sort()
            report.append(
                "%-34s phrase corr %+.3f control %+.3f (all %s)\n"
                    .format(
                        s.id,
                        atCycle[MUSIC_DRAWS / 2],
                        abs(control[MUSIC_DRAWS / 2]),
                        atCycle.joinToString(" ") { "%+.2f".format(it) },
                    )
            )
            assertTrue(
                "${s.id} returns to the same phrase (correlation ${atCycle[MUSIC_DRAWS / 2]})",
                atCycle[MUSIC_DRAWS / 2] < .5,
            )
            assertTrue(
                "${s.id} control lag correlates ${control[MUSIC_DRAWS / 2]}",
                abs(control[MUSIC_DRAWS / 2]) < .25,
            )
        }
        println(report)
    }

    @Test
    fun everyBundledRecordingAndTheBinauralToneIsReachableFromAPreset() {
        val used = Presets.all.flatMap { p -> p.layers.map { it.soundId } }.toSet()
        val missing =
            Library.all.filter { it.kind == "recording" || it.kind == "binaural" }.map { it.id }
                .filterNot { it in used }
        assertTrue("Bundled material with no preset: $missing", missing.isEmpty())
    }
}
