// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Catalogue-level quality assertions: level calibration, entry identity, preset loudness balance and
 * preset headroom. Every threshold carries the measurement that motivated it, and each test prints
 * the numbers it measured so a regression names itself instead of just failing
 */
class CatalogueQualityTest {
    private val rate = CatalogueMetrics.RATE

    /**
     * Entries that intentionally run the same synthesis and say so in their description: synonyms
     * for one noise spectrum, and one water bed shared by six names.
     */
    private val documentedAliases =
        listOf(
            setOf("brown", "red"),
            setOf("violet", "purple"),
            setOf("ocean-waves", "beaches"),
            setOf("streams", "rivers", "waterfalls", "lakes"),
        )

    private fun documented(a: String, b: String) =
        documentedAliases.any { a in it && b in it }

    private fun solo(id: String, gain: Float = .35f): PcmMixer {
        val layer = Library.layer(id, gain, "catalogue-probe").copy(id = "catalogue-probe")
        return CatalogueMetrics.mixer(rate).apply { sync(Mix(layers = listOf(layer))) }
    }

    @Test
    fun noEntryRepeatsAnotherUnlessTheCatalogueSaysSo() {
        val pairs = ArrayList<String>()
        var closest = Double.MAX_VALUE
        var closestPair = ""
        for ((kind, sounds) in Library.all.filter { it.available }.groupBy { it.kind }) {
            for (i in sounds.indices) {
                for (j in i + 1 until sounds.size) {
                    val a = sounds[i].id
                    val b = sounds[j].id
                    val rel = CatalogueMetrics.relativeDifference(solo(a), solo(b), 4.0, rate)
                    if (rel < closest) {
                        closest = rel
                        closestPair = "$a~$b"
                    }
                    if (rel <= .01) pairs += "$kind:$a~$b"
                }
            }
        }
        println("closest pair $closestPair measured ${CatalogueMetrics.fmt(closest, 4)}")
        val undocumented = pairs.filterNot { documented(it.substringAfter(':').substringBefore('~'), it.substringAfter('~')) }
        assertTrue("Undocumented identical entries: $undocumented", undocumented.isEmpty())
        assertEquals(
            "The catalogue changed shape; update documentedAliases deliberately: $pairs",
            9,
            pairs.size,
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
                    val rel =
                        CatalogueMetrics.relativeDifference(
                            solo(sounds[i].id),
                            solo(sounds[j].id),
                            4.0,
                            rate,
                        )
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
    fun everyEntryLandsAtTheCalibratedLevel() {
        val levels = LinkedHashMap<String, Double>()
        for (s in Library.all.filter { it.available }) {
            levels[s.id] = CatalogueMetrics.analyseSound(s.id, 40.0, rate).p99Db
        }
        val target = -42.0
        val within = levels.values.count { abs(it - target) <= 4.0 }
        val loudest = levels.maxBy { it.value }
        println(
            "catalogue levels: ${within}/${levels.size} within 4 dB of $target; " +
                "loudest ${loudest.key} at ${CatalogueMetrics.fmt(loudest.value)} dB"
        )
        assertTrue(
            "Only $within of ${levels.size} entries sit within 4 dB of the calibrated level",
            within >= levels.size * 9 / 10,
        )
        assertTrue(
            "No entry may exceed the calibrated level: ${loudest.key} at ${loudest.value}",
            loudest.value <= target + 4.0,
        )
    }

    @Test
    fun presetsShareOneOutputLevel() {
        val levels = LinkedHashMap<String, Double>()
        for (p in Presets.all) {
            val mixer = CatalogueMetrics.mixer(rate).apply { sync(p) }
            levels[p.id] = CatalogueMetrics.analyse(mixer, 40.0, rate).rmsDb
        }
        val min = levels.minBy { it.value }
        val max = levels.maxBy { it.value }
        println(
            "preset levels span ${CatalogueMetrics.fmt(max.value - min.value)} dB: " +
                "${min.key} ${CatalogueMetrics.fmt(min.value)} to ${max.key} ${CatalogueMetrics.fmt(max.value)}"
        )
        assertTrue(
            "Presets differ by ${CatalogueMetrics.fmt(max.value - min.value)} dB; " +
                "a sleeper switching presets hears a jump",
            max.value - min.value <= 3.0,
        )
    }

    @Test
    fun noPresetLayerIsBuried() {
        var worst = 0.0
        var worstName = ""
        for (p in Presets.all) {
            val perLayer = LinkedHashMap<String, Double>()
            for (layer in p.layers) {
                val mixer = CatalogueMetrics.mixer(rate).apply { sync(p.copy(layers = listOf(layer))) }
                perLayer[layer.soundId] = CatalogueMetrics.analyse(mixer, 40.0, rate).p99Db
            }
            val spread = perLayer.values.max() - perLayer.values.min()
            if (spread > worst) {
                worst = spread
                worstName = "${p.id} $perLayer"
            }
        }
        println("worst in-preset layer spread ${CatalogueMetrics.fmt(worst)} dB: $worstName")
        assertTrue("A preset layer sits ${CatalogueMetrics.fmt(worst)} dB under its winner", worst <= 15.0)
    }

    @Test
    fun presetsLeaveHeadroomForTheSoftKnee() {
        var peak = -Double.MAX_VALUE
        var name = "none"
        for (p in Presets.all) {
            val mixer = CatalogueMetrics.mixer(rate).apply { sync(p) }
            val measured = CatalogueMetrics.analyse(mixer, 40.0, rate).peakDb
            if (measured > peak) {
                peak = measured
                name = p.id
            }
        }
        println("loudest preset peak ${CatalogueMetrics.fmt(peak)} dBFS ($name)")
        assertTrue("$name peaks at ${CatalogueMetrics.fmt(peak)} dBFS", peak <= -6.0)
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
