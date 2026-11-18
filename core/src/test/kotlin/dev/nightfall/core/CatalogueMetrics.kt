// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * Measurement helpers for catalogue-level audio analysis: level, spectral balance, DC, periodicity
 * and similarity. Everything is processed one block at a time, so a long render never has to be held
 * in memory. Test-only; nothing here runs on the render path.
 */
object CatalogueMetrics {
    const val RATE = 48000
    private const val FFT = 16384
    private const val FLOOR_DB = 120.0
    private const val DB_STEP = .1

    /** 1/3-octave centres, 25 Hz to 16 kHz. */
    val thirdOctave =
        doubleArrayOf(
            25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0,
            500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0,
            6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
        )

    class Analysis(
        val rmsDb: Double,
        val peakDb: Double,
        val dc: Double,
        val crest: Double,
        val p50Db: Double,
        val p90Db: Double,
        val p99Db: Double,
        val centroid: Double,
        val band: DoubleArray,
    )

    private class Histogram {
        private val counts = IntArray((FLOOR_DB / DB_STEP).toInt() + 1)
        private var total = 0

        fun add(db: Double) {
            val i = (-db / DB_STEP).roundToInt().coerceIn(0, counts.size - 1)
            counts[i]++
            total++
        }

        /** Level at which [fraction] of frames are quieter; .99 is the loud-end p99. */
        fun percent(fraction: Double): Double {
            if (total == 0) return -FLOOR_DB
            val want = fraction * total
            var seen = 0.0
            for (i in counts.indices.reversed()) {
                if (counts[i] == 0) continue
                seen += counts[i]
                if (seen >= want) return -i * DB_STEP
            }
            return 0.0
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]
                re[i] = re[j]
                re[j] = t
                t = im[i]
                im[i] = im[j]
                im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Streaming level, envelope and spectrum accumulator over interleaved stereo blocks. */
    class Accumulator(private val rate: Int = RATE) {
        private var sumSquares = 0.0
        private var sumMono = 0.0
        private var frames = 0L
        private var peak = 0f
        private val envelope = Histogram()
        private var hopEnergy = 0.0
        private var hopCount = 0
        private val hop = max(1, rate / 10)
        private val window = DoubleArray(FFT) { .5 - .5 * cos(2.0 * PI * it / FFT) }
        private val block = DoubleArray(FFT)
        private var blockFill = 0
        private val re = DoubleArray(FFT)
        private val im = DoubleArray(FFT)
        private val power = DoubleArray(FFT / 2 + 1)
        private var windows = 0

        fun add(buffer: FloatArray, count: Int) {
            for (i in 0 until count) {
                val l = buffer[i * 2]
                val r = buffer[i * 2 + 1]
                sumSquares += l.toDouble() * l + r.toDouble() * r
                sumMono += (l + r) * .5
                peak = max(peak, max(abs(l), abs(r)))
                hopEnergy += l.toDouble() * l + r.toDouble() * r
                hopCount += 2
                if (hopCount >= hop * 2) {
                    envelope.add(db(sqrt(hopEnergy / hopCount)))
                    hopEnergy = 0.0
                    hopCount = 0
                }
                block[blockFill++] = (l + r) * .5
                if (blockFill == FFT) {
                    if (windows < 16) accumulate()
                    blockFill = 0
                }
                frames++
            }
        }

        private fun accumulate() {
            for (i in 0 until FFT) re[i] = block[i] * window[i]
            im.fill(0.0)
            fft(re, im)
            for (k in power.indices) power[k] += re[k] * re[k] + im[k] * im[k]
            windows++
        }

        fun finish(): Analysis {
            val rms = if (frames == 0L) 0.0 else sqrt(sumSquares / (frames * 2))
            return Analysis(
                rmsDb = db(rms),
                peakDb = if (peak <= 0f) -FLOOR_DB else 20 * log10(peak.toDouble()),
                dc = if (frames == 0L) 0.0 else sumMono / frames,
                crest = if (rms <= 0.0) 0.0 else peak / rms,
                p50Db = envelope.percent(.5),
                p90Db = envelope.percent(.9),
                p99Db = envelope.percent(.99),
                centroid = centroid(power, rate),
                band = bandProfile(power, rate),
            )
        }
    }

    /** Energy in each 1/3-octave band, in dB relative to the loudest band. */
    fun bandProfile(spectrum: DoubleArray, rate: Int): DoubleArray {
        val binHz = rate.toDouble() / ((spectrum.size - 1) * 2)
        val raw =
            DoubleArray(thirdOctave.size) { b ->
                val centre = thirdOctave[b]
                var sum = 0.0
                var k = ceil(centre / 2.0.pow(1.0 / 6) / binHz).toInt()
                val last =
                    floor(centre * 2.0.pow(1.0 / 6) / binHz).toInt().coerceAtMost(spectrum.size - 1)
                while (k <= last) {
                    sum += spectrum[k]
                    k++
                }
                sum
            }
        val peak = raw.max()
        return DoubleArray(raw.size) {
            if (raw[it] <= 0.0 || peak <= 0.0) -90.0 else 10 * log10(raw[it] / peak)
        }
    }

    fun centroid(spectrum: DoubleArray, rate: Int): Double {
        val binHz = rate.toDouble() / ((spectrum.size - 1) * 2)
        var num = 0.0
        var den = 0.0
        for (k in 1 until spectrum.size) {
            num += k * binHz * spectrum[k]
            den += spectrum[k]
        }
        return if (den <= 0.0) 0.0 else num / den
    }

    fun db(x: Double) = if (x <= 0.0) -FLOOR_DB else 20 * log10(x)

    fun fmt(x: Double, digits: Int = 1) = String.format("%.${digits}f", x)

    /** Mean absolute difference of two band profiles, in dB. */
    fun bandDistance(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size
    }

    private val recordings = HashMap<String, SampleData>()

    val recordingLoader: (String) -> SampleData? = { id ->
        recordings.getOrPut(id) {
            val file = File("../app/src/main/assets/audio/$id.pcm")
            require(file.exists()) { "Missing bundled recording $id" }
            Pcm16Data(ByteBuffer.wrap(file.readBytes()))
        }
    }

    fun mixer(rate: Int = RATE) = PcmMixer(rate, recordings = recordingLoader)

    /** Render [seconds] of a single-layer mix and analyse it. */
    fun analyse(
        mixer: PcmMixer,
        seconds: Double,
        rate: Int = RATE,
        block: Int = 1024,
    ): Analysis {
        val accumulator = Accumulator(rate)
        val buffer = FloatArray(block * 2)
        var remaining = (seconds * rate).toLong()
        while (remaining > 0) {
            val n = min(remaining, block.toLong()).toInt()
            mixer.render(buffer, n)
            accumulator.add(buffer, n)
            remaining -= n
        }
        return accumulator.finish()
    }

    fun analyseSound(
        soundId: String,
        seconds: Double,
        rate: Int = RATE,
        layer: Layer = Library.layer(soundId, unique = "probe"),
        settings: Settings = Settings(),
    ): Analysis {
        val mixer = mixer(rate)
        mixer.settings = settings
        mixer.sync(Mix(layers = listOf(layer.copy(id = "probe"))))
        return analyse(mixer, seconds, rate)
    }

    /**
     * Relative difference between two same-seed renders, as the RMS of their difference over the RMS
     * of the first. Two entries that run the same algorithm against the same seed give exactly 0.
     */
    fun relativeDifference(a: PcmMixer, b: PcmMixer, seconds: Double, rate: Int = RATE): Double {
        val block = 1024
        val x = FloatArray(block * 2)
        val y = FloatArray(block * 2)
        var diff = 0.0
        var energy = 0.0
        var remaining = (seconds * rate).toLong()
        while (remaining > 0) {
            val n = min(remaining, block.toLong()).toInt()
            a.render(x, n)
            b.render(y, n)
            for (i in 0 until n * 2) {
                diff += (x[i] - y[i]).toDouble().pow(2)
                energy += x[i].toDouble().pow(2)
            }
            remaining -= n
        }
        return sqrt(diff / max(energy, 1e-20))
    }

    /**
     * Correlation between the render and itself [lag] seconds earlier, averaged over
     * [window] seconds. Uses a ring buffer of exactly one lag, so memory stays proportional to the
     * lag rather than the render.
     */
    fun selfCorrelation(
        mixer: PcmMixer,
        lagSeconds: Double,
        windowSeconds: Double,
        rate: Int = RATE,
    ): Double {
        val block = 1024
        val lag = (lagSeconds * rate).toLong()
        val window = (windowSeconds * rate).toLong()
        require(lag > 0 && window > 0)
        val ring = FloatArray(lag.toInt())
        val buffer = FloatArray(block * 2)
        var position = 0L
        var sum = 0.0
        var sx = 0.0
        var sy = 0.0
        var counted = 0L
        val total = lag + window
        var remaining = total
        while (remaining > 0) {
            val n = min(remaining, block.toLong()).toInt()
            mixer.render(buffer, n)
            for (i in 0 until n) {
                val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                val index = (position % lag).toInt()
                if (position >= lag) {
                    val w = ring[index]
                    sum += v.toDouble() * w
                    sx += v.toDouble() * v
                    sy += w.toDouble() * w
                    counted++
                }
                ring[index] = v
                position++
            }
            remaining -= n
        }
        return if (sx <= 0.0 || sy <= 0.0 || counted == 0L) 0.0 else sum / sqrt(sx * sy)
    }
}
