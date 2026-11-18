// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

/**
 * Catalogue-level analysis on top of [M]: identity between two renders, envelope percentiles, spectral
 * balance, and repetition at a fixed lag. [M] owns rendering and loudness; this adds the measurements
 * those tests do not need. Test-only.
 */
object CatalogueMetrics {
    private const val FFT = 16384
    private const val FLOOR_DB = 120.0
    private const val DB_STEP = .1

    /** 1/3-octave centres, 25 Hz to 16 kHz. */
    private val thirdOctave =
        doubleArrayOf(
            25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0,
            500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0,
            6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
        )

    private val recordings = HashMap<String, SampleData>()

    private val loader: (String) -> SampleData? = { id ->
        recordings.getOrPut(id) { M.assetLoader().invoke(id) ?: error("missing recording $id") }
    }

    fun render(mix: Mix, seconds: Double, rate: Int = M.RATE): M.Signal =
        M.render(mix, seconds, loader).let {
            if (rate == M.RATE) it else throw IllegalArgumentException("only 48 kHz is supported here")
        }

    /** One layer of [id] alone, at a fixed gain so two entries are comparable. */
    fun solo(id: String, seconds: Double, gain: Float = .35f): M.Signal =
        render(Mix(layers = listOf(Library.layer(id, gain, "catalogue-probe"))), seconds)

    /**
     * Relative difference between two renders of the same seed, as the RMS of their difference over
     * the RMS of the first. Two entries running one algorithm against one seed give exactly 0.
     */
    fun relativeDifference(a: M.Signal, b: M.Signal): Double {
        var diff = 0.0
        var energy = 0.0
        for (i in a.left.indices) {
            diff += (a.left[i] - b.left[i]).toDouble().pow(2)
            diff += (a.right[i] - b.right[i]).toDouble().pow(2)
            energy += a.left[i].toDouble().pow(2) + a.right[i].toDouble().pow(2)
        }
        return sqrt(diff / max(energy, 1e-20))
    }

    private class Histogram {
        private val counts = IntArray((FLOOR_DB / DB_STEP).toInt() + 1)
        private var total = 0

        fun add(db: Double) {
            counts[(-db / DB_STEP).roundToInt().coerceIn(0, counts.size - 1)]++
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

    private fun envelope(signal: M.Signal, hopMs: Int = 100): Histogram {
        val hop = M.RATE * hopMs / 1000
        val hist = Histogram()
        val mono = signal.mono()
        var start = 0
        while (start + hop <= mono.size) {
            var sum = 0.0
            for (i in start until start + hop) sum += mono[i].toDouble() * mono[i]
            hist.add(if (sum <= 0.0) -FLOOR_DB else 10 * log10(sum / hop))
            start += hop
        }
        return hist
    }

    /** 99th-percentile level of the 100 ms envelope: how loud a source sits when it is doing something. */
    fun p99Db(signal: M.Signal): Double = envelope(signal).percent(.99)

    fun percentileDb(signal: M.Signal, fraction: Double): Double = envelope(signal).percent(fraction)

    /** Share of 100 ms frames louder than the median by more than [aboveDb]: how often events land. */
    fun eventDensity(signal: M.Signal, aboveDb: Double = 8.0): Double {
        val hop = M.RATE / 10
        val mono = signal.mono()
        val levels = ArrayList<Double>(mono.size / hop + 1)
        var start = 0
        while (start + hop <= mono.size) {
            var sum = 0.0
            for (i in start until start + hop) sum += mono[i].toDouble() * mono[i]
            levels += if (sum <= 0.0) -FLOOR_DB else 10 * log10(sum / hop)
            start += hop
        }
        if (levels.isEmpty()) return 0.0
        val sorted = levels.sorted()
        val gate = sorted[sorted.size / 2] + aboveDb
        return levels.count { it > gate }.toDouble() / levels.size
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

    /** Energy per 1/3-octave band in dB relative to the loudest, Hann-windowed, up to 16 windows. */
    fun bandProfile(signal: M.Signal): DoubleArray {
        val mono = signal.mono()
        val window = DoubleArray(FFT) { .5 - .5 * cos(2.0 * PI * it / FFT) }
        val power = DoubleArray(FFT / 2 + 1)
        val re = DoubleArray(FFT)
        val im = DoubleArray(FFT)
        var windows = 0
        var start = 0
        while (start + FFT <= mono.size && windows < 16) {
            for (i in 0 until FFT) re[i] = mono[start + i] * window[i]
            im.fill(0.0)
            fft(re, im)
            for (k in power.indices) power[k] += re[k] * re[k] + im[k] * im[k]
            windows++
            start += FFT
        }
        val binHz = M.RATE.toDouble() / FFT
        val raw =
            DoubleArray(thirdOctave.size) { b ->
                val centre = thirdOctave[b]
                var sum = 0.0
                var k = ceil(centre / 2.0.pow(1.0 / 6) / binHz).toInt()
                val last = floor(centre * 2.0.pow(1.0 / 6) / binHz).toInt().coerceAtMost(power.size - 1)
                while (k <= last) {
                    sum += power[k]
                    k++
                }
                sum
            }
        val peak = raw.max()
        return DoubleArray(raw.size) {
            if (raw[it] <= 0.0 || peak <= 0.0) -90.0 else 10 * log10(raw[it] / peak)
        }
    }

    fun bandDistance(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size
    }

    /**
     * Correlation between the render and itself [lagSeconds] earlier, over [windowSeconds]. Streams a
     * ring buffer of one lag, so a four-minute lag costs 4 minutes of samples, not the whole render.
     */
    fun selfCorrelation(mix: Mix, lagSeconds: Double, windowSeconds: Double, rate: Int = M.RATE): Double {
        val lag = (lagSeconds * rate).toLong()
        val window = (windowSeconds * rate).toLong()
        require(lag > 0 && window > 0)
        val ring = FloatArray(lag.toInt())
        val buffer = FloatArray(1024 * 2)
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(mix)
        var position = 0L
        var sum = 0.0
        var sx = 0.0
        var sy = 0.0
        var remaining = lag + window
        while (remaining > 0) {
            val n = min(remaining, 1024L).toInt()
            mixer.render(buffer, n)
            for (i in 0 until n) {
                val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                val index = (position % lag).toInt()
                if (position >= lag) {
                    val w = ring[index]
                    sum += v.toDouble() * w
                    sx += v.toDouble() * v
                    sy += w.toDouble() * w
                }
                ring[index] = v
                position++
            }
            remaining -= n
        }
        return if (sx <= 0.0 || sy <= 0.0) 0.0 else sum / sqrt(sx * sy)
    }

    fun fmt(x: Double, digits: Int = 1) = String.format("%.${digits}f", x)
}
