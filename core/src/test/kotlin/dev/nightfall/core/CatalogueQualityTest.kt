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
        assertTrue("A preset layer sits ${CatalogueMetrics.fmt(worst)} LU under its neighbour", worst <= 15.0)
    }

    @Test
    fun presetsShareOneOutputLevel() {
        val levels = LinkedHashMap<String, Double>()
        for (p in Presets.all) {
            levels[p.id] = M.shortTermLufs(M.render(p, 20.0, loader), 3.0)
        }
        levels.forEach { (id, lufs) -> println(String.format("  %-22s %.1f LUFS", id, lufs)) }
        val min = levels.minBy { it.value }
        val max = levels.maxBy { it.value }
        println(
            "preset loudness spans ${CatalogueMetrics.fmt(max.value - min.value)} LU: " +
                "${min.key} ${CatalogueMetrics.fmt(min.value)} to ${max.key} ${CatalogueMetrics.fmt(max.value)}"
        )
        assertTrue(
            "Presets differ by ${CatalogueMetrics.fmt(max.value - min.value)} LU; switching presets jumps",
            max.value - min.value <= 3.0,
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
    fun everyBundledRecordingAndTheBinauralToneIsReachableFromAPreset() {
        val used = Presets.all.flatMap { p -> p.layers.map { it.soundId } }.toSet()
        val missing =
            Library.all.filter { it.kind == "recording" || it.kind == "binaural" }.map { it.id }
                .filterNot { it in used }
        assertTrue("Bundled material with no preset: $missing", missing.isEmpty())
    }
}
