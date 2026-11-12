// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

/** One render owner. UI submits immutable snapshots; no locks, IO or allocation in sample loops. */
class PcmMixer(
    val sampleRate: Int = 48000,
    private val blockSize: Int = 1024,
    private val recordings: (String) -> SampleData? = { null },
) {
    private class Channel(var layer: Layer, val source: Source) {
        var removed = false
        var gain = 0f
        var life = 0f
        var played = 0L
        var expired = false
        var lpL = 0f
        var lpR = 0f
        var hpL = 0f
        var hpR = 0f
        var bassL = 0f
        var bassR = 0f
        var trebleL = 0f
        var trebleR = 0f
    }

    private val channels = linkedMapOf<String, Channel>()
    private val scratchL = FloatArray(blockSize)
    private val scratchR = FloatArray(blockSize)
    private val accumL = FloatArray(blockSize)
    private val accumR = FloatArray(blockSize)
    var settings = Settings()
    private var masterTarget = .5f
    private var master = .0f
    private var duck = 1f
    private var smoothedExternal = 1f
    var peak = 0f
        private set

    val channelCount
        get() = channels.values.count { !it.removed }

    fun reset() {
        channels.clear()
        master = 0f
        duck = 1f
        smoothedExternal = 1f
        peak = 0f
    }

    fun sync(mix: Mix) {
        val ids = mix.layers.mapTo(HashSet()) { it.id }
        channels.values.forEach { it.removed = it.layer.id !in ids }
        for (layer in mix.layers) {
            val existing = channels[layer.id]
            if (existing != null) {
                existing.layer = layer
                existing.removed = false
            } else {
                val s = Library.byId.getValue(layer.soundId)
                require(s.available) { "This recording is not bundled" }
                // Keep both seed and identity; XORing two identical default hashes cancels to zero.
                val seed = layer.music.seed xor (layer.id.hashCode().toLong() shl 32)
                val source: Source =
                    when (s.kind) {
                        "noise" -> NoiseSource(sampleRate, seed, s.variant)
                        "music" -> MusicSource(sampleRate, layer.music.seed, s.variant)
                        "binaural" -> BinauralSource(sampleRate)
                        "recording" ->
                            RecordingSource(
                                sampleRate,
                                requireNotNull(recordings(s.asset!!)) {
                                    "Recording missing: ${s.asset}"
                                },
                                seed,
                            )
                        else -> EnvironmentSource(sampleRate, seed, s.kind, s.variant)
                    }
                channels[layer.id] = Channel(layer, source)
            }
        }
        masterTarget = mix.master
    }

    fun activeIds() = channels.values.filter { !it.removed }.map { it.layer.id }

    fun layerGain(id: String) = channels[id]?.gain ?: 0f

    fun render(output: FloatArray, frames: Int = output.size / 2, externalGain: Float = 1f) {
        require(frames <= blockSize && output.size >= frames * 2)
        accumL.fill(0f, 0, frames)
        accumR.fill(0f, 0, frames)
        val solo =
            channels.values.any {
                !it.removed && it.layer.solo && it.layer.enabled && !it.layer.mute
            }
        val smoothing = (1 - exp(-1.0 / (sampleRate * .025))).toFloat()
        val thunder =
            channels.values.maxOfOrNull {
                if (it.layer.enabled && !it.layer.mute && (!solo || it.layer.solo))
                    it.source.eventEnergy * it.layer.gain
                else 0f
            } ?: 0f
        val duckTarget = if (settings.ducking && thunder > .04f) .72f else 1f
        duck += (duckTarget - duck) * (1 - exp(-frames.toDouble() / (sampleRate * .6))).toFloat()
        val iterator = channels.values.iterator()
        while (iterator.hasNext()) {
            val ch = iterator.next()
            val c = ch.layer
            if (ch.removed && ch.life <= 0f) {
                iterator.remove()
                continue
            }
            val stopFrame = c.stopAfterMinutes * 60 * sampleRate
            if (stopFrame > 0 && ch.played >= stopFrame) {
                ch.expired = true
                ch.source.eventEnergy = 0f
                ch.played += frames
                if (ch.removed) iterator.remove()
                continue
            }
            if (ch.expired) {
                ch.expired = false
                ch.gain = 0f
                ch.life = 0f
            }
            ch.source.render(scratchL, scratchR, frames, c, settings)
            val audible = c.enabled && !c.mute && (!solo || c.solo)
            val target = if (audible) c.gain else 0f
            val hp = (1 - exp(-2 * PI * c.highPass / sampleRate)).toFloat()
            val lp = (1 - exp(-2 * PI * c.lowPass / sampleRate)).toFloat()
            val low = (1 - exp(-2 * PI * 250 / sampleRate)).toFloat()
            val high = (1 - exp(-2 * PI * 4000 / sampleRate)).toFloat()
            val bass = 10f.pow((c.bass + c.tilt * -4) / 20).toFloat()
            val mid = 10f.pow(c.mid / 20).toFloat()
            val treble = 10f.pow((c.treble + c.tilt * 4 + (c.brightness - .5f) * 6) / 20).toFloat()
            val leftPan = sqrt((1 - c.pan) * .5f)
            val rightPan = sqrt((1 + c.pan) * .5f)
            val isMusic = Library.byId[c.soundId]?.music == true
            val musicDuck = if (isMusic) duck else 1f
            val lifecycleStep = 1f / (sampleRate * c.fadeSeconds.coerceAtLeast(.05f))
            for (i in 0 until frames) {
                ch.gain += (target - ch.gain) * smoothing
                ch.life =
                    (ch.life + if (ch.removed) -lifecycleStep else lifecycleStep).coerceIn(0f, 1f)
                val fade =
                    if (stopFrame > 0)
                        ((stopFrame - ch.played) / (sampleRate * c.fadeSeconds)).coerceIn(0f, 1f)
                    else 1f
                var l = scratchL[i]
                var r = scratchR[i]
                val center = (l + r) * .5f
                l = center + (l - center) * c.width
                r = center + (r - center) * c.width
                ch.hpL += hp * (l - ch.hpL)
                ch.hpR += hp * (r - ch.hpR)
                l -= ch.hpL
                r -= ch.hpR
                ch.lpL += lp * (l - ch.lpL)
                ch.lpR += lp * (r - ch.lpR)
                ch.bassL += low * (ch.lpL - ch.bassL)
                ch.bassR += low * (ch.lpR - ch.bassR)
                ch.trebleL += high * (ch.lpL - ch.trebleL)
                ch.trebleR += high * (ch.lpR - ch.trebleR)
                l = ch.bassL * bass + (ch.trebleL - ch.bassL) * mid + (ch.lpL - ch.trebleL) * treble
                r = ch.bassR * bass + (ch.trebleR - ch.bassR) * mid + (ch.lpR - ch.trebleR) * treble
                val gain = ch.gain * ch.life * fade * musicDuck * .65f
                accumL[i] += l * gain * leftPan
                accumR[i] += r * gain * rightPan
                ch.played++
            }
        }
        peak = 0f
        for (i in 0 until frames) {
            master += (masterTarget - master) * smoothing
            smoothedExternal += (externalGain - smoothedExternal) * smoothing
            val l = limit(accumL[i] * master * smoothedExternal)
            val r = limit(accumR[i] * master * smoothedExternal)
            output[i * 2] = l
            output[i * 2 + 1] = r
            peak = max(peak, max(abs(l), abs(r)))
        }
    }

    companion object {
        fun limit(x: Float): Float {
            if (!x.isFinite()) return 0f
            val a = abs(x)
            return if (a <= .75f) x else sign(x) * (.75f + .23f * (1 - exp(-(a - .75f) / .23f)))
        }
    }
}
