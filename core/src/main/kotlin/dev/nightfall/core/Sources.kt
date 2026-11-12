// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

class RandomStream(seed: Long) {
    private var state = if (seed == 0L) 0x12345678L else seed

    fun next(): Float {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return ((x ushr 40).toInt() / 8388608f) - 1f
    }

    fun unit() = (next() + 1f) * .5f

    fun index(n: Int) = (unit() * n).toInt().coerceIn(0, n - 1)
}

interface Source {
    var eventEnergy: Float

    fun render(left: FloatArray, right: FloatArray, frames: Int, layer: Layer, settings: Settings)
}

object Wave {
    private const val N = 16384
    private val sine = FloatArray(N + 1) { kotlin.math.sin(it * 2.0 * PI / N).toFloat() }

    fun sin(phase: Double): Float {
        val v = (phase - floor(phase)) * N
        val i = v.toInt()
        return sine[i] + (sine[i + 1] - sine[i]) * (v - i).toFloat()
    }
}

class Noise(private val rate: Int, seed: Long) {
    private val rng = RandomStream(seed)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f
    private var brown = 0f
    private var previous = 0f
    private var previousPink = 0f
    private var low = 0f
    private var lower = 0f

    fun next(color: Int, density: Float = .5f): Float {
        val w = rng.next()
        return when (color) {
            0 -> w * .38f
            1,
            4 -> {
                // Paul Kellet's seven-pole pinking filter, designed for 44.1–48 kHz.
                b0 = .99886f * b0 + w * .0555179f
                b1 = .99332f * b1 + w * .0750759f
                b2 = .969f * b2 + w * .153852f
                b3 = .8665f * b3 + w * .3104856f
                b4 = .55f * b4 + w * .5329522f
                b5 = -.7616f * b5 - w * .016898f
                val p = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * .5362f) * .065f
                b6 = w * .115926f
                if (color == 4) {
                    val y = (p - previousPink) * 1.6f
                    previousPink = p
                    y
                } else p
            }
            2,
            3 -> {
                brown = (brown + w * .017f) * .9991f
                brown * 2.8f
            }
            5,
            6 -> {
                val y = (w - previous) * .25f
                previous = w
                y
            }
            7 -> {
                low += .035f * (w - low)
                .2f * w + low * .9f
            } // Approximate perceptual preset, not calibrated equal loudness.
            8 -> {
                low += .11f * (w - low)
                lower += .025f * (low - lower)
                (low - lower) * 1.4f
            }
            9 -> {
                low += .002f * (w - low)
                low * .8f
            }
            else ->
                if (rng.unit() < (400f + density * 2000f) / rate) if (w > 0) .42f else -.42f else 0f
        }
    }
}

class NoiseSource(private val rate: Int, seed: Long, private val color: Int) : Source {
    private val a = Noise(rate, seed)
    private val b = Noise(rate, seed xor 0x6789L)
    private var frame = 0L
    override var eventEnergy = 0f

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        for (i in 0 until frames) {
            val variation =
                if (settings.slowVariation)
                    1f + layer.modulation * .1f * Wave.sin(frame.toDouble() / rate * .025)
                else 1f
            val intensity = (.65f + layer.intensity * .7f) * variation
            left[i] = a.next(color, layer.intensity) * intensity
            right[i] = b.next(color, layer.intensity) * intensity
            frame++
        }
    }
}

/** Stochastic physical-model textures. These are labeled synthesis, never field recordings. */
class EnvironmentSource(
    private val rate: Int,
    seed: Long,
    private val kind: String,
    private val variant: Int,
) : Source {
    private val rng = RandomStream(seed)
    private val n = Noise(rate, seed)
    private val n2 = Noise(rate, seed + 7919)
    private var frame = 0L
    private var low = 0f
    private var low2 = 0f
    private var event = 0f
    private var eventPan = 0f
    private var untilEvent = (rate * .5).toInt()
    private var phase = 0.0
    private var ring = 0f
    private var ringPhase = 0.0
    private var eventDecay = .9999f
    override var eventEnergy = 0f

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        val cutoff =
            if (kind == "wind" || kind == "winter") .008f + layer.brightness * .025f else .06f
        val storm = kind == "thunder"
        val thunderInterval = rate * (6f + (1 - layer.eventRate) * 65f)
        val transition =
            if (layer.weatherToRainMinutes > 0)
                (frame.toDouble() / (rate * 60.0 * layer.weatherToRainMinutes))
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            else 0f
        for (i in 0 until frames) {
            val t = frame.toDouble() / rate
            frame++
            val w = n.next(0)
            val w2 = n2.next(0)
            low += cutoff * (w - low)
            low2 += cutoff * (w2 - low2)
            val slow = if (settings.dynamicWeather) .8f + .2f * Wave.sin(t * .008 + variant) else 1f
            val gust =
                .65f + .22f * Wave.sin(t * (.025 + variant * .003)) + .12f * Wave.sin(t * .071)
            val intensity = .4f + layer.intensity * .9f
            var l: Float
            var r: Float
            when (kind) {
                "thunder" -> {
                    if (--untilEvent <= 0) {
                        event = .65f + rng.unit() * .35f
                        eventPan = rng.next() * .65f
                        eventDecay = exp(-1.0 / (rate * (1.4 + rng.unit() * 3.0))).toFloat()
                        untilEvent = (thunderInterval * (.45f + rng.unit())).toInt()
                    }
                    event *= eventDecay
                    val roll = .65f + .35f * Wave.sin(t * (2.1 + variant * .13))
                    val th =
                        low *
                            event *
                            roll *
                            6f *
                            (if (settings.lowDistraction) .5f else 1f) *
                            (1 - transition)
                    val rain = if (variant >= 3) (w * .17f + low * .65f) else w * .025f
                    l = th * (1 - eventPan) + rain + transition * w * .3f
                    r = th * (1 + eventPan) + rain + transition * w2 * .3f
                    eventEnergy = event * (1 - transition)
                }
                "fire" -> {
                    if (rng.unit() < (5 + layer.eventRate * 30) / rate) {
                        ring = .1f + rng.unit() * .35f
                        ringPhase = rng.unit().toDouble()
                    }
                    ring *= .997f
                    val crack =
                        ring * Wave.sin(ringPhase) * (if (settings.lowDistraction) .65f else 1f)
                    ringPhase += .08
                    l = low * 1.5f + crack + w * .05f
                    r = low2 * 1.5f + crack * .6f + w2 * .05f
                }
                "rain" -> {
                    if (rng.unit() < (70 + layer.eventRate * 500) / rate)
                        ring = .1f + rng.unit() * .24f
                    ring *= .985f
                    ringPhase += (.025 + variant * .008)
                    val drop = ring * Wave.sin(ringPhase)
                    val weight =
                        when (variant) {
                            0 -> .3f
                            2 -> 1.05f
                            else -> .65f
                        }
                    l = (w * .55f + low * .7f) * weight + drop * .4f
                    r = (w2 * .55f + low2 * .7f) * weight + drop * .3f
                }
                "winter" -> {
                    val hard = variant in 3..5
                    if (hard && rng.unit() < (18 + layer.eventRate * 150) / rate) {
                        ring = .1f + rng.unit() * .3f
                        ringPhase = rng.unit().toDouble()
                    }
                    ring *= .975f
                    ringPhase += .09 + variant * .014
                    val hit =
                        ring * Wave.sin(ringPhase) * (if (settings.lowDistraction) .65f else 1f)
                    val amount = if (variant == 1) .18f else .8f
                    l = (low * gust * 3f + w * .07f) * amount + hit
                    r = (low2 * gust * 3f + w2 * .07f) * amount + hit * .65f
                }
                "wind" -> {
                    l = low * gust * 3.2f
                    r = low2 * gust * 3.2f
                    if (variant == 7) {
                        l += Wave.sin(t * (120 + gust * 35)) * .018f * gust
                        r += Wave.sin(t * (121 + gust * 35)) * .018f * gust
                    }
                }
                "water" -> {
                    val swell =
                        if (variant <= 1) .4f + .6f * (.5f + .5f * Wave.sin(t * .09)).pow(2)
                        else .8f
                    l = (low * 1.2f + w * .2f) * swell
                    r = (low2 * 1.2f + w2 * .2f) * swell
                }
                "clock" -> {
                    val tick = (t % 1.0)
                    val e = exp(-tick * 95).toFloat()
                    l = Wave.sin(t * 1700) * e * .25f
                    r = l * .8f
                }
                else -> {
                    phase += (48.0 + variant * 5.0) / rate
                    val rumble = Wave.sin(phase) * .065f + Wave.sin(phase * 2.005) * .02f
                    val travel = if (variant == 1) .7f + .3f * Wave.sin(t * 1.4) else 1f
                    l = low * 1.6f * travel + rumble
                    r = low2 * 1.6f * travel + rumble
                }
            }
            val modulation =
                if (settings.slowVariation) 1f + layer.modulation * .15f * Wave.sin(t * .017)
                else 1f
            left[i] = l * slow * intensity * modulation
            right[i] = r * slow * intensity * modulation
        }
    }
}

/** Shared read-only sample storage; Android memory maps uncompressed APK assets. */
interface SampleData {
    val size: Int

    operator fun get(index: Int): Float
}

class Pcm16Data(buffer: java.nio.ByteBuffer) : SampleData {
    private val data = buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    override val size = data.limit()

    override fun get(index: Int) = data.get(index) / 32768f
}

/** Mono 24 kHz recording. Every bundled cycle is longer than three minutes. */
class RecordingSource(private val rate: Int, private val samples: SampleData, seed: Long) : Source {
    init {
        require(samples.size > 24000 * 181) {
            "A recording must run over three minutes before looping"
        }
    }

    private var position = (seed.ushr(1) % max(1, samples.size / 3)).toDouble()
    override var eventEnergy = 0f

    companion object {
        const val OVERLAP = 24000

        fun cycleSeconds(samples: Int) = (samples - OVERLAP) / 24000.0
    }

    private fun read(position: Double): Float {
        val p = position.toInt().coerceIn(0, samples.size - 1)
        val fraction = (position - p).toFloat()
        return samples[p] * (1 - fraction) + samples[min(p + 1, samples.size - 1)] * fraction
    }

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        val end = samples.size - OVERLAP
        for (i in 0 until frames) {
            var v = read(position)
            if (position >= end) {
                val x = (position - end).toFloat() / OVERLAP
                v = v * (1 - x) + read(position - end) * x
            }
            left[i] = v * .4f
            right[i] = v * .4f
            position += 24000.0 / rate
            if (position >= samples.size) position = OVERLAP + (position - samples.size)
        }
    }
}

class BinauralSource(private val rate: Int) : Source {
    private var l = 0.0
    private var r = 0.0
    override var eventEnergy = 0f

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        val difference = 1.0 + layer.intensity * 19
        for (i in 0 until frames) {
            left[i] = Wave.sin(l) * .2f
            right[i] = Wave.sin(r) * .2f
            l += 200.0 / rate
            r += (200 + difference) / rate
        }
    }
}
