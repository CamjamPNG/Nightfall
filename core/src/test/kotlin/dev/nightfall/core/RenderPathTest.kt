// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/**
 * The render path's measured contract. Every threshold below is a measured value plus stated margin;
 * the measurements are in `ProbeMeasurementsTest` and in the PR body. Nothing here is tuned to pass.
 */
class RenderPathTest {

    private val loader = M.assetLoader()

    private fun stepDb(a: FloatArray, from: Int, to: Int): Double {
        var p = 0f
        for (i in max(1, from) until min(a.size, to)) p = max(p, abs(a[i] - a[i - 1]))
        return 20 * log10(p.toDouble() + 1e-30)
    }

    @Test
    fun renderAllocatesNothingAfterWarmup() {
        val bean =
            java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val buf = FloatArray(1024 * 2)
        val mixes =
            listOf(
                Mix(layers = listOf(Library.layer("brown", .5f, "alloc-brown"))),
                Mix(layers = listOf(Library.layer("lo-fi-sleep-music", .5f, "alloc-music"))),
                Mix(layers = listOf(Library.layer("forest-birds", .5f, "alloc-rec"))),
                Presets.all.last(),
            )
        for (mix in mixes) {
            val mixer = PcmMixer(48000, 1024, loader)
            mixer.sync(mix)
            repeat(400) { mixer.render(buf) }
            val before = bean.currentThreadAllocatedBytes
            repeat(2000) { mixer.render(buf) }
            val perBlock = (bean.currentThreadAllocatedBytes - before) / 2000.0
            // Measured 0 B/block. The threshold allows one 8-byte long per block, so a regression
            // that puts a single small object in the loop fails instead of hiding in noise.
            assertTrue("${mix.name} allocated $perBlock B/block", perBlock < 8.0)
        }
    }

    @Test
    fun noiseColoursHaveTheirStatedSpectralSlope() {
        // Measured slopes through the shipped layer filters, 60 Hz–12 kHz, 12 s renders.
        val expected = mapOf("white" to 0.0, "pink" to -3.0, "brown" to -6.0, "red" to -6.0,
            "blue" to 3.0, "violet" to 6.0, "purple" to 6.0)
        for ((id, slope) in expected) {
            val s =
                M.render(
                    Mix(layers = listOf(Library.layer(id, 1f, "slope-$id"))),
                    12.0,
                    loader,
                )
            val measured = M.spectralSlope(M.welchSpectrum(s.mono()))
            println(String.format("slope %-8s measured %.2f dB/oct, expected %.1f", id, measured, slope))
            assertEquals("$id slope", slope, measured, .6)
        }
    }

    @Test
    fun outputHasNoDcOffset() {
        val nine = M.render(Presets.all.last(), 8.0, loader)
        // Measured worst across the catalogue and the nine-layer default: 9.6e-5 (brown noise).
        assertTrue("mix DC ${M.dcOffset(nine.mono())}", abs(M.dcOffset(nine.mono())) < 1e-3)
        for (id in listOf("brown", "red", "black", "pink")) {
            val s = M.render(Mix(layers = listOf(Library.layer(id, .5f, "dc-$id"))), 8.0, loader)
            assertTrue("$id DC ${M.dcOffset(s.mono())}", abs(M.dcOffset(s.mono())) < 1e-3)
        }
    }

    @Test
    fun addingAndRemovingALayerIsAsSmoothAsTheMaterial() {
        val block = 1024
        val steady = Mix(layers = listOf(Library.layer("brown", .5f, "steady")))
        for (gain in listOf(.35f, 1f)) {
            val mixer = PcmMixer(48000, block, loader)
            mixer.sync(steady)
            val buf = FloatArray(block * 2)
            val trace = FloatArray(48000 * 6)
            var n = 0
            fun run(blocks: Int) {
                repeat(blocks) {
                    mixer.render(buf)
                    for (i in 0 until block) trace[n + i] = buf[i * 2]
                    n += block
                }
            }
            run(48000 * 2 / block)
            mixer.sync(steady.copy(layers = steady.layers + Library.layer("moderate-rain", gain, "add")))
            val addAt = n
            run(48000 * 2 / block)
            mixer.sync(steady)
            val removeAt = n
            run(48000 * 2 / block)
            for ((label, at) in listOf("add" to addAt, "remove" to removeAt)) {
                val boundary = stepDb(trace, at - 64, at + 64)
                val material =
                    max(stepDb(trace, at - 48000, at - 64), stepDb(trace, at + 64, at + 48000))
                println(String.format("gain %.2f %-6s boundary %.1f dB, material %.1f dB", gain, label, boundary, material))
                // Measured: the boundary matches the surrounding material. 3 dB of margin covers a
                // sample landing at a different point in the waveform.
                assertTrue(
                    "click at $label (gain $gain): $boundary dB vs material $material dB",
                    boundary <= material + 3.0,
                )
            }
        }
    }

    @Test
    fun everyFieldRecordingLoopsWithoutASeam() {
        for (sound in Library.all.filter { it.kind == "recording" }) {
            val data = loader(sound.asset!!)!!
            val source = RecordingSource(48000, data, 12345L)
            val layer = Library.layer(sound.id, 1f, "seam-${sound.id}")
            val cycleFrames = ((data.size - RecordingSource.OVERLAP) / .5).toInt()
            val total = cycleFrames + 48000
            val trace = FloatArray(total)
            val l = FloatArray(1024)
            val r = FloatArray(1024)
            var n = 0
            while (n < total) {
                val k = min(1024, total - n)
                source.render(l, r, k, layer, Settings())
                for (i in 0 until k) trace[n + i] = l[i]
                n += k
            }
            val seam = stepDb(trace, cycleFrames - 24000, cycleFrames + 24000)
            val steps = FloatArray(trace.size - 1) { abs(trace[it + 1] - trace[it]) }
            steps.sort()
            val material = 20 * log10(steps[(steps.size * .9999).toInt()].toDouble() + 1e-30)
            println(String.format("seam %-28s %.1f dB, material p99.99 %.1f dB", sound.id, seam, material))
            // Measured: every seam is below the recording's own loudest steps. 3 dB of margin.
            assertTrue("${sound.id} seam $seam dB vs material $material dB", seam <= material + 3.0)
        }
    }

    @Test
    fun musicVoicesAreInTuneToTheCent() {
        // One voice isolated, because a chord's other voices bias any fundamental estimate: the same
        // renderer measured -4 to -54 cents that way. A single sustained note is 0.4 cents sharp or
        // flatter, so the threshold is 2 cents.
        for (tuning in listOf(432f, 440f)) {
            for (key in 0..11) {
                val config =
                    MusicConfig(
                        tuning = tuning,
                        key = key,
                        tempo = 40f,
                        density = 0f,
                        complexity = -1f,
                        melody = false,
                        percussion = false,
                        texture = 0,
                        seed = 7,
                    )
                val layer =
                    Library.layer("440-hz-ambient-music", 1f, "tune-$tuning-$key")
                        .copy(music = config)
                val source = MusicSource(48000, config.seed, 1)
                val trace = FloatArray(48000 * 6)
                val l = FloatArray(1024)
                val r = FloatArray(1024)
                var n = 0
                while (n + 1024 <= trace.size) {
                    source.render(l, r, 1024, layer, Settings())
                    for (i in 0 until 1024) trace[n + i] = l[i]
                    n += 1024
                }
                val expected = MusicSource.frequency(36 + key, tuning)
                val cents = M.cents(M.fundamental(trace, expected * .9, expected * 1.1), expected)
                println(String.format("tuning %.0f midi %2d -> %+.2f cents", tuning, key + 36, cents))
                assertEquals("midi ${36 + key} at $tuning", 0.0, cents, 2.0)
            }
        }
    }

    @Test
    fun outputNeverLeavesTheLimiterCeiling() {
        assertTrue(abs(PcmMixer.limit(-100f)) <= PcmMixer.LIMIT_CEILING)
        assertTrue(abs(PcmMixer.limit(100f)) <= PcmMixer.LIMIT_CEILING)
        val m = PcmMixer(48000, 1024, loader)
        m.sync(Mix(master = 1f, layers = List(100) { Library.layer("white", 1f, "hot-$it") }))
        val buf = FloatArray(1024 * 2)
        repeat(200) {
            m.render(buf)
            for (v in buf) assertTrue("$v", v.isFinite() && abs(v) <= PcmMixer.LIMIT_CEILING + 1e-6f)
        }
    }
}
