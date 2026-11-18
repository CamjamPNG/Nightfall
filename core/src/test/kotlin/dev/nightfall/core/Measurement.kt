// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * Host-JVM measurement of rendered mixer output. Everything here is an analysis tool for tests and
 * for the loudness report; none of it ships in `core` production code.
 */
object M {

    const val RATE = 48000

    fun assetLoader(root: File = File("../app/src/pcm/assets/audio")): (String) -> SampleData? = { id ->
        val file = File(root, "$id.pcm")
        if (!file.exists()) null else Pcm16Data(ByteBuffer.wrap(file.readBytes()))
    }

    class Signal(val left: FloatArray, val right: FloatArray) {
        val frames get() = left.size

        fun mono() = FloatArray(left.size) { (left[it] + right[it]) * .5f }
    }

    /** Renders [mix] for [seconds] in 1024-frame blocks, exactly as the app does. */
    fun render(
        mix: Mix,
        seconds: Double,
        recordings: (String) -> SampleData? = assetLoader(),
        block: Int = 1024,
    ): Signal {
        val mixer = PcmMixer(RATE, block, recordings)
        mixer.sync(mix)
        val frames = (seconds * RATE).toInt() / block * block
        val l = FloatArray(frames)
        val r = FloatArray(frames)
        val buf = FloatArray(block * 2)
        var n = 0
        while (n < frames) {
            mixer.render(buf, block)
            for (i in 0 until block) {
                l[n + i] = buf[i * 2]
                r[n + i] = buf[i * 2 + 1]
            }
            n += block
        }
        return Signal(l, r)
    }

    fun rmsDb(a: FloatArray): Double {
        var s = 0.0
        for (v in a) s += v.toDouble() * v
        return 10 * log10(s / a.size.coerceAtLeast(1) + 1e-30)
    }

    fun peakDb(a: FloatArray): Double {
        var p = 0f
        for (v in a) p = max(p, abs(v))
        return 20 * log10(p.toDouble() + 1e-30)
    }

    /**
     * BS.1770-4 K-weighting prefilter at 48 kHz: a high shelf and a high pass, run in double
     * precision. Coefficients are the published 48 kHz ones; other rates would need their own.
     */
    fun kWeight(x: FloatArray): DoubleArray {
        val y = DoubleArray(x.size)
        val sh = doubleArrayOf(1.53512485958697, -2.69169618940638, 1.19839281085285)
        val sha = doubleArrayOf(1.0, -1.69065929318241, 0.73248077421585)
        val hp = doubleArrayOf(1.0, -2.0, 1.0)
        val hpa = doubleArrayOf(1.0, -1.99004745483398, 0.99007225036621)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val v = x[i].toDouble()
            val o = sh[0] * v + sh[1] * x1 + sh[2] * x2 - sha[1] * y1 - sha[2] * y2
            x2 = x1
            x1 = v
            y2 = y1
            y1 = o
            y[i] = o
        }
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
        for (i in y.indices) {
            val v = y[i]
            val o = hp[0] * v + hp[1] * x1 + hp[2] * x2 - hpa[1] * y1 - hpa[2] * y2
            x2 = x1
            x1 = v
            y2 = y1
            y1 = o
            y[i] = o
        }
        return y
    }

    private fun loudnessOf(meanSquare: Double) = -0.691 + 10 * log10(meanSquare + 1e-30)

    /** Ungated 3 s short-term loudness (LUFS) over a window centred in the signal. */
    fun shortTermLufs(s: Signal, windowSeconds: Double = 3.0): Double {
        val wl = kWeight(s.left)
        val wr = kWeight(s.right)
        val n = min(wl.size, (windowSeconds * RATE).toInt())
        if (n <= 0) return -70.0
        val start = (wl.size - n) / 2
        var sum = 0.0
        for (i in start until start + n) sum += wl[i] * wl[i] + wr[i] * wr[i]
        return loudnessOf(sum / n)
    }

    /** Gated integrated loudness (LUFS) per BS.1770-4: 400 ms blocks, 75% overlap. */
    fun integratedLufs(s: Signal): Double {
        val wl = kWeight(s.left)
        val wr = kWeight(s.right)
        val step = (RATE * .1).toInt()
        val len = (RATE * .4).toInt()
        if (wl.size < len) return shortTermLufs(s, wl.size.toDouble() / RATE)
        val blocks = ArrayList<Double>()
        var start = 0
        while (start + len <= wl.size) {
            var sum = 0.0
            for (i in start until start + len) sum += wl[i] * wl[i] + wr[i] * wr[i]
            blocks += sum / len
            start += step
        }
        val abs_ = blocks.filter { loudnessOf(it) > -70.0 }
        if (abs_.isEmpty()) return -70.0
        val ungated = abs_.average()
        val relative = loudnessOf(ungated) - 10.0
        val gated = abs_.filter { loudnessOf(it) > relative }
        return loudnessOf(gated.average())
    }

    /** 4x oversampled true peak, windowed-sinc polyphase interpolator. */
    fun truePeakDb(a: FloatArray): Double {
        val phases = 4
        val taps = 16
        val table = Array(phases) { p ->
            val h = DoubleArray(taps)
            for (k in 0 until taps) {
                val t = (k - taps / 2 + 1) + p.toDouble() / phases
                val sinc = if (abs(t) < 1e-9) 1.0 else sin(PI * t) / (PI * t)
                val window = .5 - .5 * cos(2 * PI * (k + .5) / taps)
                h[k] = sinc * window
            }
            val sum = h.sum()
            FloatArray(taps) { (h[it] / sum).toFloat() }
        }
        var peak = 0.0
        val hist = FloatArray(taps)
        for (n in a.indices) {
            System.arraycopy(hist, 1, hist, 0, taps - 1)
            hist[taps - 1] = a[n]
            peak = max(peak, abs(a[n].toDouble()))
            if (n < taps) continue
            for (p in 1 until phases) {
                var acc = 0f
                val h = table[p]
                for (k in 0 until taps) acc += h[k] * hist[k]
                peak = max(peak, abs(acc.toDouble()))
            }
        }
        return 20 * log10(peak + 1e-30)
    }

    /** Peak of the louder channel. Stereo noise has independent streams, so averaging cancels it. */
    fun peakDb(s: Signal) = max(peakDb(s.left), peakDb(s.right))

    /** 4x true peak of the louder channel: the figure a converter and a limiter ceiling care about. */
    fun truePeakDb(s: Signal) = max(truePeakDb(s.left), truePeakDb(s.right))

    fun dcOffset(s: Signal) = max(abs(dcOffset(s.left)), abs(dcOffset(s.right)))

    fun dcOffset(a: FloatArray): Double {
        var s = 0.0
        for (v in a) s += v
        return s / a.size.coerceAtLeast(1)
    }

    /** Largest absolute first difference, in dBFS. Clicks show up here. */
    fun maxStepDb(a: FloatArray): Double {
        var p = 0f
        for (i in 1 until a.size) p = max(p, abs(a[i] - a[i - 1]))
        return 20 * log10(p.toDouble() + 1e-30)
    }

    /** In-place radix-2 FFT magnitude spectrum, Hann windowed, averaged over 50% overlapping frames. */
    fun welchSpectrum(a: FloatArray, fft: Int = 4096): DoubleArray {
        val acc = DoubleArray(fft / 2)
        val re = DoubleArray(fft)
        val im = DoubleArray(fft)
        var frames = 0
        var start = 0
        val hop = fft / 2
        while (start + fft <= a.size) {
            for (i in 0 until fft) {
                val w = .5 - .5 * cos(2 * PI * i / (fft - 1))
                re[i] = a[start + i] * w
                im[i] = 0.0
            }
            fftInPlace(re, im)
            for (i in 0 until fft / 2) acc[i] += (re[i] * re[i] + im[i] * im[i]) / (fft * fft.toDouble())
            frames++
            start += hop
        }
        if (frames > 0) for (i in acc.indices) acc[i] /= frames
        return acc
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
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
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val c = cos(ang * k)
                    val s = sin(ang * k)
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * c - im[i + k + len / 2] * s
                    val vi = re[i + k + len / 2] * s + im[i + k + len / 2] * c
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
    }

    /** Least-squares log-log spectral slope in dB per octave over [fLo, fHi] Hz. */
    fun spectralSlope(spectrum: DoubleArray, fft: Int = 4096, fLo: Double = 60.0, fHi: Double = 12000.0): Double {
        var n = 0.0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (i in 1 until spectrum.size) {
            val f = i.toDouble() * RATE / fft
            if (f < fLo || f > fHi) continue
            val y = 10 * log10(spectrum[i] + 1e-30)
            val x = log2(f)
            n++
            sx += x
            sy += y
            sxx += x * x
            sxy += x * y
        }
        return (n * sxy - sx * sy) / (n * sxx - sx * sx)
    }

    /** Fundamental by autocorrelation over [fLo, fHi] Hz, in Hz. */
    fun fundamental(a: FloatArray, fLo: Double = 40.0, fHi: Double = 200.0): Double {
        val minLag = (RATE / fHi).toInt()
        val maxLag = (RATE / fLo).toInt()
        var mean = 0.0
        for (v in a) mean += v
        mean /= a.size
        var bestLag = minLag
        var best = -1.0
        for (lag in minLag..maxLag) {
            var c = 0.0
            var e1 = 0.0
            var e2 = 0.0
            var i = 0
            while (i + lag < a.size) {
                val x = a[i] - mean
                val y = a[i + lag] - mean
                c += x * y
                e1 += x * x
                e2 += y * y
                i++
            }
            val r = c / sqrt(e1 * e2 + 1e-30)
            if (r > best) {
                best = r
                bestLag = lag
            }
        }
        val y0 = bestLag.toDouble()
        var refined = y0
        if (bestLag > minLag && bestLag < maxLag) {
            fun corr(lag: Int): Double {
                var c = 0.0
                var e1 = 0.0
                var e2 = 0.0
                var i = 0
                while (i + lag < a.size) {
                    val x = a[i] - mean
                    val y = a[i + lag] - mean
                    c += x * y
                    e1 += x * x
                    e2 += y * y
                    i++
                }
                return c / sqrt(e1 * e2 + 1e-30)
            }
            val ym = corr(bestLag - 1)
            val yp = corr(bestLag + 1)
            val denom = 2 * (2 * best - ym - yp)
            if (abs(denom) > 1e-12) refined = y0 + (yp - ym) / denom
        }
        return RATE / refined
    }

    fun cents(measured: Double, reference: Double) = 1200.0 * log2(measured / reference)
}
