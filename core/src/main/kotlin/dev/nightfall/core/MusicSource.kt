// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

class MusicSource(private val rate: Int, seed: Long, private val style: Int) : Source {
    private val random = RandomStream(seed)

    private class Voice {
        var phase = 0.0
        var step = 0.0
        var age = 0
        var length = 1
        var attack = 1
        var release = 1
        var level = 0f
        var pan = 0f
        var texture = 0
    }

    private val voices = Array(40) { Voice() }
    private var frame = 0L
    private var nextChord = 0L
    private var nextNote = 0L
    private var nextBeat = 0L
    private var progression = 0
    private val delayL = FloatArray((rate * .397).toInt())
    private val delayR = FloatArray((rate * .531).toInt())
    private val reverbs =
        Array(4) { FloatArray((rate * listOf(.067, .089, .113, .131)[it]).toInt()) }
    private val revPos = IntArray(4)
    private var posL = 0
    private var posR = 0
    private var filteredL = 0f
    private var filteredR = 0f
    override var eventEnergy = 0f

    companion object {
        fun frequency(midi: Int, tuning: Float): Double = tuning * 2.0.pow((midi - 69) / 12.0)
    }

    private fun note(
        midi: Int,
        seconds: Double,
        gain: Float,
        pan: Float,
        c: MusicConfig,
        texture: Int = c.texture,
    ) {
        val v =
            voices.firstOrNull { it.level == 0f } ?: voices.maxBy { it.age.toFloat() / it.length }
        v.phase = 0.0
        v.step = frequency(midi, c.tuning) / rate
        v.age = 0
        v.length = (seconds * rate).toInt().coerceAtLeast(2)
        v.attack = (rate * if (texture == 1 || texture == 3) .015 else 1.5).toInt()
        v.release = (rate * if (texture == 1) 1.8 else 3.0).toInt()
        v.level = gain
        v.pan = pan
        v.texture = texture
    }

    override fun render(
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        layer: Layer,
        settings: Settings,
    ) {
        val c = layer.music
        val beat = 60.0 / c.tempo
        val scale =
            if (c.minor) intArrayOf(0, 2, 3, 5, 7, 8, 10) else intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val progressionNotes =
            when ((c.seed.ushr(2) % 3).toInt()) {
                0 -> intArrayOf(0, 5, 3, 4)
                1 -> intArrayOf(0, 3, 5, 4)
                else -> intArrayOf(0, 4, 5, 3)
            }
        var degree = progressionNotes[(progression - 1).mod(4)]
        for (i in 0 until frames) {
            if (frame >= nextChord) {
                degree = progressionNotes[progression.mod(4)]
                progression++
                val duration = beat * 8
                for (j in 0 until (3 + (c.complexity * 3).toInt())) {
                    val d = degree + j * 2
                    val midi = 48 + c.key + scale[d % 7] + (d / 7) * 12
                    note(
                        midi,
                        duration + 3,
                        .038f * (if (j < 3) 1f else .6f),
                        (j - 2) * .22f,
                        c,
                        if (c.texture == 1 || c.texture == 3) c.texture else 0,
                    )
                }
                note(36 + c.key + scale[degree], duration + 2, .046f, 0f, c, 0)
                nextChord = frame + (duration * rate).toLong()
            }
            if (frame >= nextNote) {
                if (c.melody && random.unit() < c.density * .85f + .08f) {
                    val d =
                        degree +
                            listOf(0, 2, 4, 6, 8)[
                                random.index(if (random.unit() < c.randomness) 5 else 3)]
                    note(
                        60 + c.key + scale[d % 7] + (d / 7) * 12,
                        beat * (2 + random.index(4)),
                        .045f,
                        random.next() * .65f,
                        c,
                        if (c.texture == 0) 1 else c.texture,
                    )
                }
                nextNote = frame + (beat * rate * (if (c.density > .7f) 1 else 2)).toLong()
            }
            if (frame >= nextBeat) {
                if (c.percussion && (style == 2 || style == 16)) note(32, .4, .025f, 0f, c, 5)
                nextBeat = frame + (beat * rate).toLong()
            }
            var l = 0f
            var r = 0f
            for (v in voices) {
                if (v.level == 0f) continue
                val remaining = v.length - v.age
                if (remaining <= 0) {
                    v.level = 0f
                    continue
                }
                val attack = (v.age.toFloat() / v.attack).coerceAtMost(1f)
                val release = (remaining.toFloat() / v.release).coerceAtMost(1f)
                val decay =
                    when (v.texture) {
                        1 -> exp(-v.age.toDouble() / rate / 2.7).toFloat()
                        3 -> exp(-v.age.toDouble() / rate / 3.8).toFloat()
                        5 -> exp(-v.age.toDouble() / rate * 16).toFloat()
                        else -> 1f
                    }
                val fundamental = Wave.sin(v.phase)
                val tone =
                    when (v.texture) {
                        1 ->
                            fundamental +
                                .28f * Wave.sin(v.phase * 2) +
                                .1f * Wave.sin(v.phase * 3.002) +
                                .03f * Wave.sin(v.phase * 5)
                        2 ->
                            fundamental +
                                .18f * Wave.sin(v.phase * 2.001) +
                                .09f * Wave.sin(v.phase * 3)
                        3 ->
                            fundamental +
                                .35f * Wave.sin(v.phase * 2.756) +
                                .16f * Wave.sin(v.phase * 5.404)
                        4 ->
                            fundamental +
                                .24f * Wave.sin(v.phase * 2) +
                                .11f * Wave.sin(v.phase * 3.001)
                        else ->
                            fundamental +
                                .22f * Wave.sin(v.phase * 1.003) +
                                .06f * Wave.sin(v.phase * 2)
                    }
                val value = tone * attack * release * decay * v.level
                l += value * (1 - v.pan) * .707f
                r += value * (1 + v.pan) * .707f
                v.phase += v.step
                if (v.phase > 1000) v.phase -= 1000
                v.age++
            }
            val dl = delayL[posL]
            val dr = delayR[posR]
            delayL[posL] = l + dr * .32f
            delayR[posR] = r + dl * .32f
            posL = (posL + 1) % delayL.size
            posR = (posR + 1) % delayR.size
            var wetL = 0f
            var wetR = 0f
            for (j in 0..3) {
                val buf = reverbs[j]
                val p = revPos[j]
                val echo = buf[p]
                buf[p] = (l + r) * .25f + echo * .72f
                if (j % 2 == 0) wetL += echo else wetR += echo
                revPos[j] = (p + 1) % buf.size
            }
            filteredL += .16f * (l + dl * c.delay + wetL * c.reverb * .5f - filteredL)
            filteredR += .16f * (r + dr * c.delay + wetR * c.reverb * .5f - filteredR)
            left[i] = filteredL
            right[i] = filteredR
            frame++
        }
    }
}
