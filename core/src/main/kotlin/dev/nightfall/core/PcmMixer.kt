// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*

/**
 * One render owner. UI submits immutable snapshots; no locks, IO or allocation in sample loops.
 *
 * Level staging, in order: [Levels] trims each source to the same reference loudness, the layer
 * gain slider scales it, and the master gain follows. Summing is unnormalised, so adding a layer
 * adds level; [limit] is the only thing that ever removes it, and it exists to keep intersample
 * peaks under the ceiling rather than to set the app's loudness.
 */
class PcmMixer(
    val sampleRate: Int = 48000,
    private val blockSize: Int = 1024,
    private val recordings: (String) -> SampleData? = { null },
) {
    private class Channel(
        var layer: Layer,
        val source: Source,
        val trim: Float,
        val music: Boolean,
        /** An endless-night companion: not the person's layer, and not theirs to see or count. */
        val companion: Boolean,
    ) {
        /** Stable per-layer seed for the night's tone drift, from the id the mix gives the layer. */
        val driftSalt: Int = layer.id.hashCode()

        /** This block's endless level for the layer, 1 when endless night is off. */
        var endless = 1f

        /** This block's tone drift, decided once and used by both the compensation and the loop. */
        var sweep = 0f
        var spread = 0f
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

    private val channels = HashMap<String, Channel>()
    private val active = ArrayList<Channel>()
    private val scratchL = FloatArray(blockSize)
    private val scratchR = FloatArray(blockSize)
    private val accumL = FloatArray(blockSize)
    private val accumR = FloatArray(blockSize)
    private val smoothing = (1 - exp(-1.0 / (sampleRate * .025))).toFloat()
    var settings = Settings()

    /** The night this mix is living, or null when endless night is off. */
    private var plan: NightPlan? = null

    /** Frames rendered since the night began: the schedule's only clock, and not the wall clock. */
    private var nightFrames = 0L
    private var masterTarget = .5f
    private var master = .0f
    private var duck = 1f
    private var smoothedExternal = 1f
    private var duckFrames = -1
    private var duckStep = 0f
    var peak = 0f
        private set

    val channelCount
        get() = active.count { !it.removed && !it.companion }

    fun reset() {
        active.forEach { it.source.close() }
        channels.clear()
        active.clear()
        plan = null
        nightFrames = 0L
        master = 0f
        duck = 1f
        smoothedExternal = 1f
        peak = 0f
    }

    fun sync(mix: Mix) {
        // The mix's layer set is the person's layers plus, with endless night on, the companions its
        // own families earned. Companions keep fixed ids, so a night's roster is stable across syncs
        // and a changed seed retires the old companion through the ordinary crossfade.
        plan = NightPlan.of(mix)
        for (n in 0 until active.size) active[n].removed = true
        for (layer in mix.layers) attach(layer, false)
        val roster = plan?.companions
        if (roster != null) for (i in roster.indices) attach(roster[i], true)
        masterTarget = mix.master
    }

    private fun attach(layer: Layer, companion: Boolean) {
        val existing = channels[layer.id]
        if (existing != null) {
            existing.layer = layer
            existing.removed = false
            return
        }
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
                        requireNotNull(recordings(s.asset!!)) { "Recording missing: ${s.asset}" },
                        seed,
                    )
                else -> EnvironmentSource(sampleRate, seed, s.kind, s.variant)
            }
        val channel = Channel(layer, source, Levels.trim(s), s.music, companion)
        channels[layer.id] = channel
        active.add(channel)
    }

    fun activeIds() = active.filter { !it.removed && !it.companion }.map { it.layer.id }

    fun layerGain(id: String) = channels[id]?.gain ?: 0f

    fun render(output: FloatArray, frames: Int = output.size / 2, externalGain: Float = 1f) {
        require(frames <= blockSize && output.size >= frames * 2)
        accumL.fill(0f, 0, frames)
        accumR.fill(0f, 0, frames)
        val count = active.size
        var solo = false
        for (n in 0 until count) {
            val c = active[n].layer
            if (!active[n].removed && c.solo && c.enabled && !c.mute) solo = true
        }
        var thunder = 0f
        for (n in 0 until count) {
            val ch = active[n]
            val c = ch.layer
            if (c.enabled && !c.mute && (!solo || c.solo))
                thunder = max(thunder, ch.source.eventEnergy * c.gain)
        }
        val duckTarget = if (settings.ducking && thunder > .04f) .72f else 1f
        if (duckFrames != frames) {
            duckFrames = frames
            duckStep = (1 - exp(-frames.toDouble() / (sampleRate * .6))).toFloat()
        }
        duck += (duckTarget - duck) * duckStep
        // The night, computed once per block: each layer's endless level, and the one compensation
        // factor the whole mix shares. Costs a handful of integer hashes per layer, no allocation.
        val night = plan
        val seconds = if (night != null) nightFrames.toDouble() / sampleRate else 0.0
        var comp = 1f
        if (night != null) {
            val depth = night.config.depth
            val ramp = Endless.ramp(seconds)
            var user = 0f
            var total = 0f
            for (n in 0 until count) {
                val ch = active[n]
                val c = ch.layer
                val salt = ch.driftSalt xor night.salt
                // Decided here and reused by the sample loop, so the power below is the power the loop
                // actually renders rather than an approximation of it.
                ch.sweep = Endless.wobble(salt xor 0x51ED, seconds) * depth
                ch.spread = Endless.wobble(salt xor 0x2F79, seconds) * depth
                val tone =
                    tonePower(
                        c.bass,
                        c.mid,
                        c.treble,
                        (c.tilt + ch.sweep * .18f).coerceIn(-1f, 1f),
                        (c.brightness + ch.sweep * .10f).coerceIn(0f, 1f),
                    )
                // A layer on its way out is outside both sums, so removing one still lowers the mix
                // the way an unnormalised sum says it does; only the companions are compensated.
                if (ch.removed) {
                    ch.endless = if (ch.companion) 0f else 1f
                    continue
                }
                val audible = c.enabled && !c.mute && (!solo || c.solo)
                if (ch.companion) {
                    val e = if (audible) Endless.envelope(salt, seconds, depth, ramp) else 0f
                    ch.endless = e
                    total += c.gain * c.gain * tone * e * e
                } else {
                    ch.endless = 1f
                    if (audible) {
                        total += c.gain * c.gain * tone
                        user += c.gain * c.gain * tone
                    }
                }
            }
            // Levels holds every source at one reference loudness, so a companion that enters adds
            // its own power and nothing else: scaling the whole mix by sqrt(the person's power / the
            // total) keeps the night's loudness at the mix they set, coming or going. It is never
            // above 1, so the night cannot make a mix louder or push the limiter where the plain mix
            // did not. `tone` is what makes the two sums the power the loop renders: a layer pulled
            // 12 dB down by its own tone controls is quieter than its slider says, and a companion
            // entering must not read that as headroom to fill.
            comp = if (user > 1e-9f && total > 1e-9f) sqrt(user / total).coerceIn(.25f, 1f) else 1f
        }
        var index = 0
        // active.size, not the pre-scan count: retiring a channel shrinks the list underneath us.
        while (index < active.size) {
            val ch = active[index]
            val c = ch.layer
            if (ch.removed && ch.life <= 0f) {
                active.removeAt(index)
                channels.remove(c.id)
                ch.source.close()
                continue
            }
            index++
            val stopFrame = c.stopAfterMinutes * 60 * sampleRate
            if (stopFrame > 0 && ch.played >= stopFrame) {
                ch.expired = true
                ch.source.eventEnergy = 0f
                ch.played += frames
                if (ch.removed) {
                    active.removeAt(--index)
                    channels.remove(c.id)
                    ch.source.close()
                }
                continue
            }
            if (ch.expired) {
                ch.expired = false
                ch.gain = 0f
                ch.life = 0f
            }
            val audible = c.enabled && !c.mute && (!solo || c.solo)
            // A companion whose gate is shut renders nothing at all, the way an expired layer does:
            // two absent beds cost no synthesis for the hours they are away. The residual gain is
            // below -80 dBFS by the time this trips, so snapping it to zero cannot be heard.
            if (ch.companion && !ch.removed && ch.endless <= 0f && ch.gain <= 1e-4f) {
                ch.gain = 0f
                continue
            }
            ch.source.render(scratchL, scratchR, frames, c, settings)
            val target = if (audible) c.gain * ch.endless * comp else 0f
            var tilt = c.tilt
            var bright = c.brightness
            var width = c.width
            var pan = c.pan
            if (night != null) {
                // Tone drift, around the layer's own settings rather than instead of them, so the
                // slider still means what it says while the night moves. Tilt, brightness, width and
                // pan are all level-neutral by construction, which is why the drift cannot quietly
                // undo the compensation above.
                tilt = (c.tilt + ch.sweep * .18f).coerceIn(-1f, 1f)
                bright = (c.brightness + ch.sweep * .10f).coerceIn(0f, 1f)
                width = (c.width + ch.spread * .15f).coerceIn(0f, 1f)
                pan = (c.pan + ch.spread * .12f).coerceIn(-1f, 1f)
            }
            val hp = (1 - exp(-2 * PI * c.highPass / sampleRate)).toFloat()
            val lp = (1 - exp(-2 * PI * c.lowPass / sampleRate)).toFloat()
            val low = (1 - exp(-2 * PI * 250 / sampleRate)).toFloat()
            val high = (1 - exp(-2 * PI * 4000 / sampleRate)).toFloat()
            val bass = 10f.pow((c.bass + tilt * -4) / 20).toFloat()
            val mid = 10f.pow(c.mid / 20).toFloat()
            val treble = 10f.pow((c.treble + tilt * 4 + (bright - .5f) * 6) / 20).toFloat()
            val leftPan = sqrt((1 - pan) * .5f)
            val rightPan = sqrt((1 + pan) * .5f)
            val musicDuck = if (ch.music && settings.ducking) duck else 1f
            val lifecycleStep = 1f / (sampleRate * c.fadeSeconds.coerceAtLeast(.05f))
            val scale = ch.trim
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
                l = center + (l - center) * width
                r = center + (r - center) * width
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
                val gain = ch.gain * ch.life * fade * musicDuck * scale
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
        if (night != null) nightFrames += frames
    }

    companion object {
        /**
         * How far a layer's own tone controls attenuate it, as one relative power factor: 1.0 with the
         * controls flat, and the answer to "is this layer quieter than its slider says". The three
         * bands are weighted equally, which is exactly right at flat settings and a deliberate
         * approximation away from them — it is the layer's own settings, not a measured level table.
         */
        fun tonePower(bass: Float, mid: Float, treble: Float, tilt: Float, bright: Float): Float {
            val b = 10f.pow((bass + tilt * -4) / 20)
            val m = 10f.pow(mid / 20)
            val t = 10f.pow((treble + tilt * 4 + (bright - .5f) * 6) / 20)
            return (b * b + m * m + t * t) / 3f
        }

        /** Samples below this pass untouched (-6.9 dBFS): the limiter works on peaks, not the mix. */
        const val LIMIT_KNEE = .45f

        /**
         * -5.5 dBFS sample ceiling, which keeps 4x-oversampled true peaks under -1.0 dBTP.
         *
         * A memoryless waveshaper cannot see the peak a converter reconstructs between two samples.
         * Measured on the louder channel rather than the mono downmix, nine white layers at full gain
         * and full master - the worst case in the catalogue - reach 4.4 dB above the sample ceiling,
         * because white noise's true peak runs 2.4 dB over its own sample peak and the two channels
         * hold independent streams. The gap between the sample ceiling and the -1.0 dBTP limit is
         * that measurement plus margin, which is what a true-peak guarantee costs without an
         * oversampled lookahead limiter.
         */
        const val LIMIT_CEILING = .53f

        fun limit(x: Float): Float {
            if (!x.isFinite()) return 0f
            val a = abs(x)
            return if (a <= LIMIT_KNEE) x
            else
                sign(x) *
                    (LIMIT_KNEE +
                        (LIMIT_CEILING - LIMIT_KNEE) *
                            (1 - exp(-(a - LIMIT_KNEE) / (LIMIT_CEILING - LIMIT_KNEE))))
        }
    }
}
