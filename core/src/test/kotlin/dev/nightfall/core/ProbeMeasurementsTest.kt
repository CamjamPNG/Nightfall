// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.*
import org.junit.Test

/** Prints every quantity [RenderPathTest] asserts, so the thresholds can be set from measurement. */
class ProbeMeasurementsTest {

    private val loader = M.assetLoader()

    private fun report(name: String, body: String) {
        val dir = File("../artifacts").apply { mkdirs() }
        File(dir, "render-probe-$name.txt").writeText(body)
        print(body)
    }

    @Test
    fun spectralSlopesAndDc() {
        val out = StringBuilder()
        out.append("colour  slope(dB/oct, 60-12k)  dc\n")
        for (color in 0..10) {
            val sound = Library.all.first { it.kind == "noise" && it.variant == color }
            val s = M.render(Mix(layers = listOf(Library.layer(sound.id, 1f, "slope-$color"))), 12.0, loader)
            val slope = M.spectralSlope(M.welchSpectrum(s.mono()))
            out.append(
                String.format(
                    "%-8s %8.2f  %10.7f\n",
                    sound.name,
                    slope,
                    M.dcOffset(s.mono()),
                )
            )
        }
        report("spectral", out.toString())
    }

    @Test
    fun clickAtAddAndRemove() {
        val out = StringBuilder()
        val block = 1024
        val steady = Mix(layers = listOf(Library.layer("brown", .5f, "steady")))
        // Typical first difference of the running mix, used as the click reference.
        val ref = M.render(steady, 6.0, loader)
        val typical = M.maxStepDb(ref.left)
        val p999 = percentileStep(ref.left, .999)
        out.append(
            String.format(
                "steady maxStep %.1f dB, p99.9 step %.1f dB\n",
                typical,
                p999,
            )
        )
        for (gain in listOf(.35f, 1f)) {
            val mixer = PcmMixer(48000, block, loader)
            mixer.sync(steady)
            val buf = FloatArray(block * 2)
            val trace = FloatArray(48000 * 12)
            var n = 0
            repeat(48000 * 2 / block) {
                mixer.render(buf)
                for (i in 0 until block) trace[n + i] = buf[i * 2]
                n += block
            }
            val before = n
            mixer.sync(steady.copy(layers = steady.layers + Library.layer("moderate-rain", gain, "added")))
            repeat(48000 * 2 / block) {
                mixer.render(buf)
                for (i in 0 until block) if (n + i < trace.size) trace[n + i] = buf[i * 2]
                n += block
            }
            val afterAdd = maxStep(trace, before - block, min(n, before + block * 3))
            mixer.sync(steady)
            val removalStart = n
            repeat(48000 * 4 / block) {
                mixer.render(buf)
                for (i in 0 until block) if (n + i < trace.size) trace[n + i] = buf[i * 2]
                n += block
            }
            val afterRemove = maxStep(trace, removalStart - block, min(n, removalStart + block * 4))
            out.append(
                String.format(
                    "add/remove gain %.2f: step at add %.1f dB, at remove %.1f dB (steady max %.1f dB)\n",
                    gain,
                    afterAdd,
                    afterRemove,
                    typical,
                )
            )
        }
        report("clicks", out.toString())
    }

    private fun maxStep(a: FloatArray, from: Int, to: Int): Double {
        var p = 0f
        for (i in max(1, from) until min(a.size, to)) p = max(p, abs(a[i] - a[i - 1]))
        return 20 * log10(p.toDouble() + 1e-30)
    }

    private fun percentileStep(a: FloatArray, q: Double): Double {
        val steps = FloatArray(a.size - 1) { abs(a[it + 1] - a[it]) }
        steps.sort()
        return 20 * log10(steps[min(steps.size - 1, (q * steps.size).toInt())].toDouble() + 1e-30)
    }

    @Test
    fun loopSeamOnFieldRecordings() {
        val out = StringBuilder()
        for (sound in Library.all.filter { it.kind == "recording" }) {
            val data = loader(sound.asset!!)!!
            val source = RecordingSource(48000, data, 12345L)
            val layer = Library.layer(sound.id, 1f, "seam-${sound.id}")
            val frames = 1024
            val cycleFrames = ((data.size - RecordingSource.OVERLAP) / .5).toInt()
            val total = cycleFrames + 48000
            val out1 = FloatArray(total)
            val l = FloatArray(frames)
            val r = FloatArray(frames)
            var n = 0
            while (n < total) {
                val k = min(frames, total - n)
                source.render(l, r, k, layer, Settings())
                for (i in 0 until k) out1[n + i] = l[i]
                n += k
            }
            val seam = maxStep(out1, cycleFrames - 24000, cycleFrames + 24000)
            val body = percentileStep(out1, .9999)
            out.append(
                String.format(
                    "%-28s seam step %6.1f dB, body p99.99 step %6.1f dB\n",
                    sound.id,
                    seam,
                    body,
                )
            )
        }
        report("seams", out.toString())
    }

    @Test
    fun tuningInCents() {
        val out = StringBuilder()
        for (tuning in listOf(432f, 440f)) {
            for (key in 0..11) {
                val config =
                    MusicConfig(
                        tuning = tuning,
                        key = key,
                        minor = false,
                        tempo = 40f,
                        density = 0f,
                        complexity = 0f,
                        melody = false,
                        percussion = false,
                        texture = 0,
                        seed = 7,
                    )
                val layer = Library.layer("440-hz-ambient-music", 1f, "tune-$tuning-$key").copy(music = config)
                val source = MusicSource(48000, config.seed, 1)
                val l = FloatArray(1024)
                val r = FloatArray(1024)
                val trace = FloatArray(48000 * 6)
                var n = 0
                while (n + 1024 <= trace.size) {
                    source.render(l, r, 1024, layer, Settings())
                    for (i in 0 until 1024) trace[n + i] = l[i]
                    n += 1024
                }
                val midi = 36 + key
                val expected = MusicSource.frequency(midi, tuning)
                val measured = M.fundamental(trace, expected * .9, expected * 1.1)
                out.append(
                    String.format(
                        "tuning %.0f midi %2d expected %7.2f Hz measured %7.2f Hz -> %+6.1f cents\n",
                        tuning,
                        midi,
                        expected,
                        measured,
                        M.cents(measured, expected),
                    )
                )
            }
        }
        report("tuning", out.toString())
    }

    @Test
    fun renderAllocations() {
        val bean =
            java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val out = StringBuilder()
        for (id in listOf("brown", "moderate-rain", "lo-fi-sleep-music", "forest-birds")) {
            val mixer = PcmMixer(48000, 1024, loader)
            mixer.sync(
                Mix(
                    layers =
                        listOf(Library.layer(id, .5f, "alloc-$id")),
                )
            )
            val buf = FloatArray(1024 * 2)
            repeat(300) { mixer.render(buf) }
            val before = bean.currentThreadAllocatedBytes
            repeat(2000) { mixer.render(buf) }
            val bytes = bean.currentThreadAllocatedBytes - before
            out.append(String.format("%-24s %6d bytes over 2000 blocks (%.1f B/block)\n", id, bytes, bytes / 2000.0))
        }
        // Steady-state mix with nine layers plus ducking source.
        val mixer = PcmMixer(48000, 1024, loader)
        mixer.sync(Presets.all.last())
        val buf = FloatArray(1024 * 2)
        repeat(300) { mixer.render(buf) }
        val before = bean.currentThreadAllocatedBytes
        repeat(2000) { mixer.render(buf) }
        val bytes = bean.currentThreadAllocatedBytes - before
        out.append(String.format("nine-layer preset       %6d bytes over 2000 blocks (%.1f B/block)\n", bytes, bytes / 2000.0))
        // sync cost, which runs on the audio thread when a layer is added.
        val times = ArrayList<Long>()
        repeat(20) { k ->
            val m = PcmMixer(48000, 1024, loader)
            val t = System.nanoTime()
            m.sync(Mix(layers = listOf(Library.layer("lo-fi-sleep-music", .5f, "sync-$k"))))
            times.add(System.nanoTime() - t)
        }
        out.append(
            String.format(
                "sync(first music layer) median %d us, worst %d us\n",
                times.sorted()[times.size / 2] / 1000,
                times.max() / 1000,
            )
        )
        report("alloc", out.toString())
    }
}

/** Pitch probe cross-check: autocorrelation on a chord vs an isolated single voice. */
class PitchProbeTest {
    @Test
    fun compareMethods() {
        val out = StringBuilder()
        val sb = StringBuilder()
        for (tuning in listOf(432f, 440f)) for (key in 0..11) {
            val expected = MusicSource.frequency(36 + key, tuning)
            val chord = render(key, tuning, 0f)
            val solo = render(key, tuning, -1f)
            out.append(
                String.format(
                    "%.0f midi %2d exp %7.2f | chord ac %7.2f (%+.1f c) | solo ac %7.2f (%+.1f c)\n",
                    tuning,
                    key + 36,
                    expected,
                    M.fundamental(chord, expected * .9, expected * 1.1),
                    M.cents(M.fundamental(chord, expected * .9, expected * 1.1), expected),
                    M.fundamental(solo, expected * .9, expected * 1.1),
                    M.cents(M.fundamental(solo, expected * .9, expected * 1.1), expected),
                )
            )
        }
        sb.append(out)
        java.io.File("../artifacts/render-probe-pitch.txt").writeText(sb.toString())
        print(sb)
    }

    private fun render(key: Int, tuning: Float, complexity: Float): FloatArray {
        val config =
            MusicConfig(
                tuning = tuning,
                key = key,
                tempo = 40f,
                density = 0f,
                complexity = complexity,
                melody = false,
                percussion = false,
                texture = 0,
                seed = 7,
            )
        val layer = Library.layer("440-hz-ambient-music", 1f, "tune-$tuning-$key").copy(music = config)
        val source = MusicSource(48000, config.seed, 1)
        val l = FloatArray(1024)
        val r = FloatArray(1024)
        val trace = FloatArray(48000 * 8)
        var n = 0
        while (n + 1024 <= trace.size) {
            source.render(l, r, 1024, layer, Settings())
            for (i in 0 until 1024) trace[n + i] = l[i]
            n += 1024
        }
        return trace
    }
}
