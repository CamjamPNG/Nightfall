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

    /**
     * Called once when the mixer prunes this source. It must not wait: it runs on the mixing thread, so
     * anything a decoded source owns releases itself on that source's own worker.
     */
    fun close() {}
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
    private var gust = .65f
    private var gustTarget = .65f
    private var gustCountdown = 0
    private val gustSmoothing = (1 - exp(-1.0 / (rate * .8))).toFloat()
    override var eventEnergy = 0f

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        val baseCutoff =
            if (kind == "wind" || kind == "winter") .008f + layer.brightness * .025f else .06f
        // Each water name is its own bed rather than one shared texture with two slopes: swell rate
        // and depth, how much of the band is body against hiss, a flutter on top, and a low wash. The
        // constants are chosen once per block, not per sample, so the loop stays free of branches.
        val swellRate =
            when (variant) {
                0 -> .085
                1 -> .132
                2 -> .55
                3 -> .031
                4 -> .74
                5 -> .011
                else -> .09
            }
        val swellDepth =
            when (variant) {
                0 -> .62f
                1 -> .5f
                2 -> .18f
                3 -> .42f
                4 -> .3f
                5 -> .08f
                else -> .5f
            }
        val body =
            when (variant) {
                0 -> 1.25f
                1 -> .95f
                2 -> .55f
                3 -> .68f
                4 -> 1.5f
                5 -> .34f
                else -> 1.2f
            }
        val hiss =
            when (variant) {
                0 -> .18f
                1 -> .34f
                2 -> .42f
                3 -> .24f
                4 -> .52f
                5 -> .1f
                else -> .2f
            }
        val flutterRate =
            when (variant) {
                0 -> 3.1
                1 -> 5.7
                2 -> 2.3
                3 -> 1.4
                4 -> 7.9
                5 -> 1.1
                else -> 3.0
            }
        val flutterDepth =
            when (variant) {
                0 -> .06f
                1 -> .09f
                2 -> .16f
                3 -> .07f
                4 -> .2f
                5 -> .03f
                else -> .06f
            }
        // Wind and winter names differ by the band they rustle in, the sub they carry and whether they
        // whistle, on top of the gust that all of them share.
        val rustle =
            when (variant) {
                0 -> .03f
                1 -> .2f
                2 -> .34f
                3 -> .18f
                4 -> .08f
                5 -> .24f
                6 -> .04f
                7 -> .42f
                8 -> .3f
                9 -> .02f
                else -> .12f
            }
        val rustleRate =
            when (variant) {
                0 -> 1.7
                1 -> .8
                2 -> 3.3
                3 -> 2.4
                4 -> .6
                5 -> 1.1
                6 -> .45
                7 -> 4.1
                8 -> 2.9
                else -> 1.5
            }
        val whistle =
            when (variant) {
                0 -> .0f
                1 -> .0f
                2 -> .01f
                3 -> .004f
                4 -> .0f
                5 -> .008f
                6 -> .0f
                7 -> .05f
                8 -> .02f
                else -> 0f
            }
        val sub =
            when (variant) {
                4 -> .8f
                10 -> .95f
                6 -> .7f
                0 -> .05f
                9 -> .6f
                1 -> .35f
                else -> .22f
            }
        // The band a name lives in, on top of the layer's own brightness: a cabin is muffled, a
        // howling wind is bright, a cave is dark and long.
        val tilt =
            if (kind == "winter")
                when (variant) {
                    0 -> 1.15f
                    1 -> .55f
                    2 -> .95f
                    3 -> 1.45f
                    4 -> 1.05f
                    5 -> 1.3f
                    6 -> 1.25f
                    else -> .65f
                }
            else
                when (variant) {
                    0 -> .75f
                    1 -> 1.2f
                    2 -> 1.05f
                    3 -> .88f
                    4 -> .5f
                    5 -> .8f
                    6 -> .3f
                    7 -> 1.5f
                    8 -> 1.12f
                    9 -> .26f
                    else -> .62f
                }
        val cutoff = if (kind == "wind" || kind == "winter") baseCutoff * tilt else baseCutoff
        // Hail and ice crackle: only the winter names that carry ice, and only at their own rate.
        val hard = kind == "winter" && variant in 3..5
        val ringChance = (18 + layer.eventRate * 150) / rate
        // Gentle snowfall is meant to be barely there; everything else in these families sits at one level.
        val bedAmount = if (kind == "winter") (if (variant == 1) .18f else .8f) else 1f
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
            if (--gustCountdown <= 0) {
                gustTarget = .45f + rng.unit() * .40f
                gustCountdown = (rate * (1.5 + rng.unit() * 3.5)).toInt()
            }
            gust += (gustTarget - gust) * gustSmoothing
            val gustLevel = gust * (1f + .06f * Wave.sin(t * .071))
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
                "wind", "winter" -> {
                    if (hard && rng.unit() < ringChance) {
                        ring = .1f + rng.unit() * .3f
                        ringPhase = rng.unit().toDouble()
                    }
                    ring *= .975f
                    ringPhase += .09 + variant * .014
                    val hit =
                        ring * Wave.sin(ringPhase) * (if (settings.lowDistraction) .65f else 1f)
                    // The rustle's rate and phase ride the same random walk as the gust, so no sine in
                    // this branch can return on a fixed period across a night.
                    val rustleWave =
                        .5f +
                            .5f *
                                Wave.sin(
                                    t * rustleRate * (.75f + .5f * gustLevel) + gustLevel * 5.3f + variant
                                )
                    val bed = gustLevel * (1f + rustle * rustleWave) * bedAmount
                    l = (low * (1f + sub) * bed * 3.2f + (w - low) * rustle * rustleWave * .55f) + hit
                    r = (low2 * (1f + sub) * bed * 3.2f + (w2 - low2) * rustle * rustleWave * .55f) +
                        hit * .65f
                    if (whistle > 0f) {
                        l += Wave.sin(t * (120 + gustLevel * 35)) * whistle * gustLevel
                        r += Wave.sin(t * (121 + gustLevel * 35)) * whistle * gustLevel
                    }
                }
                "water" -> {
                    val swell = .4f + swellDepth * (.5f + .5f * Wave.sin(t * swellRate)).pow(2)
                    val flutter = 1f + flutterDepth * Wave.sin(t * flutterRate + variant)
                    l = (low * body + w * hiss) * swell * flutter
                    r = (low2 * body + w2 * hiss) * swell * flutter
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

/**
 * Sample data that is decoded rather than memory mapped, so it can serve one read head per cursor.
 * [unavailable] is set when the platform cannot decode the material: the layer reports it instead of
 * quietly contributing silence.
 */
interface DecodedSampleData : SampleData {
    /** An independent forward cursor with [seconds] of room ahead of its head. */
    fun cursor(seconds: Double = 4.0): SampleData

    val unavailable: String?
}

class Pcm16Data(buffer: java.nio.ByteBuffer) : SampleData {
    private val data = buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    override val size = data.limit()

    override fun get(index: Int) = data.get(index) / 32768f
}

/**
 * A bundled recording read by two heads at unrelated offsets.
 *
 * Mono 24 kHz, and every bundled cycle is longer than three minutes.
 *
 * The recording is longer than three minutes, which is what makes a single loop cycle tolerable, but a
 * single head still repeated the same three or four minutes at a fixed period: measured
 * self-correlation at the cycle lag was 1.000, so an eight-hour night replayed the file 120-160 times
 * in the same order. Two heads read the same samples from different offsets, each wraps at its own
 * randomised point, and the balance between them drifts between randomised aims on a randomised
 * schedule. Nothing repeats at a fixed period, and the overlap crossfades stay one second long so the
 * wraps stay inaudible.
 */
class RecordingSource(private val rate: Int, private val samples: SampleData, seed: Long) : Source {
    init {
        require(samples.size > 24000 * 181) {
            "A recording must run over three minutes before looping"
        }
    }

    private class Head {
        var position = 0.0
        var wrap = 0
        var reader: SampleData? = null

        /** The crossfade into the file's first second reads far behind [reader], so it gets its own. */
        var origin: SampleData? = null
    }

    /** Why this recording cannot be decoded, if it cannot; the host reports it to the user. */
    val unavailable: String?
        get() = (samples as? DecodedSampleData)?.unavailable

    /** The cursors this source opened, so a pruned layer releases its decoders with it. */
    private val opened = ArrayList<SampleData>()

    private val rng = RandomStream(seed or 1L)

    /**
     * How far a wrap point may move into the file. Each head still plays at least three minutes of
     * material between one wrap and the next — the shortest cycle the catalogue promises — so the
     * draw is bounded by the room a file leaves above that floor, and by a minute for the long files.
     */
    private val wrapJitter = wrapJitter(samples.size)

    private val heads = Array(2) { Head() }
    private var balance = 0.0
    private var balanceTarget = 0.0
    private var balanceCountdown = 0
    private val balanceSmoothing = (1 - exp(-1.0 / (rate * 6.0))).toFloat()
    override var eventEnergy = 0f

    init {
        // A decoded recording hands out one forward cursor per head; PCM serves both heads itself.
        if (samples is DecodedSampleData)
            heads.forEach {
                val reader = samples.cursor()
                val origin = samples.cursor(1.0)
                opened += reader
                opened += origin
                it.reader = reader
                it.origin = origin
            }
        val start = (seed.ushr(1) % max(1, samples.size / 3)).toInt()
        heads[0].position = start.toDouble()
        heads[1].position =
            ((start + samples.size / 2 + rng.index(max(1, samples.size / 8))) % (samples.size - 1))
                .toDouble()
        heads.forEach { it.wrap = samples.size - OVERLAP - rng.index(max(1, wrapJitter)) }
        balanceCountdown = rate * (6 + rng.index(18))
    }

    override fun close() {
        opened.forEach { (it as? CloseableSample)?.close() }
        opened.clear()
    }

    companion object {
        const val OVERLAP = 24000

        /** The catalogue promises three minutes of material before a recording repeats. */
        const val SHORTEST_CYCLE = 180 * 24000

        fun cycleSeconds(samples: Int) = (samples - OVERLAP) / 24000.0

        fun wrapJitter(samples: Int) =
            minOf(samples / 8, 60 * 24000, samples - OVERLAP - SHORTEST_CYCLE)

        /** The shortest cycle the randomised wrap points allow for a file of this length. */
        fun shortestCycleSeconds(samples: Int) =
            (samples - OVERLAP - wrapJitter(samples)) / 24000.0
    }

    private fun read(position: Double, reader: SampleData?): Float {
        val data = reader ?: samples
        val p = position.toInt().coerceIn(0, data.size - 1)
        val fraction = (position - p).toFloat()
        return data[p] * (1 - fraction) + data[min(p + 1, data.size - 1)] * fraction
    }

    /** One head's sample, crossfading into its own start over the last second before it wraps. */
    private fun sample(head: Head): Float {
        var v = read(head.position, head.reader)
        if (head.position >= head.wrap) {
            val x = ((head.position - head.wrap) / OVERLAP).toFloat().coerceAtMost(1f)
            v = v * (1 - x) + read(head.position - head.wrap, head.origin) * x
        }
        return v
    }

    private fun advance(head: Head) {
        head.position += 24000.0 / rate
        if (head.position >= head.wrap + OVERLAP) {
            head.position = OVERLAP + (head.position - head.wrap - OVERLAP)
            head.wrap = samples.size - OVERLAP - rng.index(max(1, wrapJitter))
        }
    }

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        for (i in 0 until frames) {
            if (--balanceCountdown <= 0) {
                balanceTarget = rng.unit().toDouble()
                balanceCountdown = rate * (12 + rng.index(40))
            }
            balance += (balanceTarget - balance) * balanceSmoothing
            val v = sample(heads[0]) * (1 - balance).toFloat() + sample(heads[1]) * balance.toFloat()
            left[i] = v * .4f
            right[i] = v * .4f
            advance(heads[0])
            advance(heads[1])
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
