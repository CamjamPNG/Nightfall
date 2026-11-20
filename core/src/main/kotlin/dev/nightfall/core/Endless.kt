// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.*
import kotlinx.serialization.Serializable

/**
 * Endless night: the mix's slow evolution, as a saved property of the mix.
 *
 * Off is the whole default and means exactly what a mix meant before this existed: every layer at the
 * setting the person left it at. A mix saved by an older build decodes with [enabled] false, so an old
 * JSON keeps its old meaning and nothing has to be migrated.
 *
 * On, the mixer adds up to two *companion* beds drawn from the catalogue families the person's own
 * layers belong to, brings each one in and out over tens of minutes, and drifts the tone of every
 * layer around its own setting. [seed] decides which companions and which offsets, so a night is
 * reproducible. There is deliberately no amount control: how far a night travels is [Endless.DEPTH],
 * one measured constant, because a sleep app should not ask a person to make a second decision at
 * bedtime with no way to hear the difference.
 *
 * Nothing here reads a clock: the schedule is a function of frames rendered, so pausing a night does
 * not spend it, and two runs of the same seed from the same start are identical sample for sample.
 */
@Serializable
data class EndlessConfig(
    val enabled: Boolean = false,
    val seed: Long = 1L,
    /**
     * A themed night's plan, in order, starting at minute zero. Empty is endless night's own schedule.
     *
     * Non-empty **replaces** the shape table below for this mix: [Endless.presence] is not consulted,
     * and no companions are added, because the plan already names every layer and how much of it
     * plays. The two could be multiplied — a phase share times a shape-table duty — and that product
     * would be a night neither the theme's author nor the depth constant described, so one of them
     * owns the arc and it is this one whenever it is present. A mix saved before this field existed
     * decodes with an empty list and keeps the behaviour it was saved with.
     */
    val phases: List<NightPhase> = emptyList(),
)

/**
 * One step of a themed night: from [startMinutes] on, these layers play at these shares of their own
 * gain, and any layer this phase does not name is absent until a later phase asks for it again.
 *
 * The shares are fractions of the layer's slider rather than gains of their own, so the slider keeps
 * meaning what it says after a theme is picked, and a phase moving one layer up and another down
 * changes the balance of the mix rather than its level.
 */
@Serializable
data class NightPhase(
    val startMinutes: Float = 0f,
    val layers: List<NightPhaseLayer> = emptyList(),
)

@Serializable
data class NightPhaseLayer(val layerId: String, val level: Float = 1f)

/**
 * The night's schedule, evaluated from elapsed frames alone.
 *
 * A companion is present or absent per *granule*, and the granule lengths are coprime primes, so the
 * pair of decisions never lines up again: the pattern does not repeat at any lag reachable in a night.
 * Two nested gates give the two time scales the night needs — 419 s granules set how often a source
 * comes back, 211 s granules break a long appearance into breaths — and both fade across the granule
 * boundary with a smoothstep, so a source arrives and leaves over about a minute instead of switching.
 *
 * The shape table is the night's own arc: the first hour is the busiest, the middle thins out, and the
 * last hour comes back most of the way. It is a function of elapsed time, not of a known end, so a
 * three-hour sleep still gets the busy part and an all-night one gets the whole arc.
 */
object Endless {
    /** Coarse granule: how often a companion may appear at all. Coprime with [FINE]. */
    private const val COARSE = 419.0

    /** Fade across a coarse boundary, in seconds. */
    private const val COARSE_FADE = 120.0

    /** Fine granule: geometry of one appearance. Coprime with [COARSE]. */
    private const val FINE = 211.0

    /** Fade across a fine boundary, in seconds. */
    private const val FINE_FADE = 80.0

    /** Fraction of fine granules a companion is present in, inside a present coarse granule. */
    private const val FINE_DUTY = .82f

    /** Seconds a companion takes to reach full after playback starts. */
    const val START_RAMP = 150.0

    /**
     * How far a night travels, as one measured constant rather than a second control: the duty of the
     * gate that brings companions in, and the size of the tone drift.
     *
     * Measured by `EndlessNightTest.theChosenDepthIsWhereTheNightBecomesAudible`, which prints the
     * whole table on every run:
     *
     * | depth | a companion is present this much of a night | the drift alone, against the plain mix |
     * | --- | --- | --- |
     * | 0.2 | 20% | -44.3 dB, a tint nobody hears |
     * | 0.65 | 39% | -34.1 dB, movement without a change of identity |
     * | 1.0 | 52% | -30.4 dB, and the bed starts to sound like a different one |
     *
     * Presence is the mean over eight seeds across eight hours, evaluated from the schedule and printed
     * for all three depths by that test. The drift figure is the waveform difference from the plain mix
     * at a moment no companion is in: the test measures and asserts it at 0.65, and the 0.2 and 1.0
     * cells are the same test run with this constant set to those values (-9.4 dB and -8.9 dB with a
     * companion in, for the record).
     *
     * 0.65 is the value where a source arrives and leaves often enough to be the point of the feature
     * without the night becoming a different mix. It is a constant because a second slider on a sleep
     * app is a decision the person has to make at bedtime, and nobody can hear the difference between
     * 0.5 and 0.65 without a test harness.
     */
    const val DEPTH = .65f

    /** The night's arc, in minutes and presence 0..1. Linear between points, held after the last. */
    private val shape =
        floatArrayOf(
            0f,
            .25f,
            15f,
            .50f,
            45f,
            1f,
            90f,
            .90f,
            150f,
            .55f,
            240f,
            .28f,
            330f,
            .15f,
            400f,
            .40f,
            465f,
            .75f,
            480f,
            .62f,
            720f,
            .62f,
        )

    /**
     * How much of the catalogue the night is willing to bring in at [minutes], 0..1.
     *
     * Only endless night reads this. A themed night's plan replaces the shape table outright, so this
     * function is never called for a mix that carries phases — see [NightPlan.amount] for the single
     * place the render path asks what the night is doing.
     */
    fun presence(minutes: Double): Float {
        if (minutes <= shape[0]) return shape[1]
        var i = 2
        while (i < shape.size) {
            if (minutes <= shape[i]) {
                val m0 = shape[i - 2].toDouble()
                val p0 = shape[i - 1]
                val m1 = shape[i].toDouble()
                val p1 = shape[i + 1]
                val t = ((minutes - m0) / (m1 - m0)).toFloat()
                return p0 + (p1 - p0) * t
            }
            i += 2
        }
        return shape[shape.size - 1]
    }

    /** The start ramp: a companion is never at full level on the first block of a night. */
    fun ramp(seconds: Double): Float =
        if (seconds >= START_RAMP) 1f else smooth(seconds / START_RAMP)

    /**
     * A companion's level at [seconds] into a night, 0..1. Allocation-free: two gate evaluations, each
     * at most three lattice hashes.
     */
    fun envelope(salt: Int, seconds: Double, depth: Float, ramp: Float): Float =
        coarse(salt, seconds, depth) * fine(salt + 7717, seconds) * ramp

    /** A slow, aperiodic value in [-1,1] for drifting one layer's tone, at granule scales of minutes. */
    fun wobble(salt: Int, seconds: Double): Float =
        .55f * lattice(salt, seconds, 660.0) +
            .30f * lattice(salt + 9991, seconds, 1740.0) +
            .15f * lattice(salt + 21943, seconds, 4260.0)

    private fun coarse(salt: Int, seconds: Double, depth: Float): Float {
        val n = floor(seconds / COARSE).toInt()
        val phase = seconds - n * COARSE
        val half = COARSE_FADE * .5
        return when {
            phase < half -> {
                val a = coarseOn(n - 1, salt, depth)
                a + (coarseOn(n, salt, depth) - a) * smooth(.5 + phase / COARSE_FADE)
            }
            phase > COARSE - half -> {
                val a = coarseOn(n, salt, depth)
                a +
                    (coarseOn(n + 1, salt, depth) - a) *
                        smooth((phase - (COARSE - half)) / COARSE_FADE)
            }
            else -> coarseOn(n, salt, depth)
        }
    }

    /**
     * One coarse granule's decision. The duty is read at the granule's *centre*, never at the instant
     * asked about: a duty that moved with the clock would flip a granule's own decision half way
     * through it, which is a step in the middle of a fade.
     */
    private fun coarseOn(index: Int, salt: Int, depth: Float): Float =
        if (unit(index, salt) < .10f + .90f * presence((index + .5) * COARSE / 60) * depth) 1f else 0f

    private fun fine(salt: Int, seconds: Double): Float {
        val n = floor(seconds / FINE).toInt()
        val phase = seconds - n * FINE
        val half = FINE_FADE * .5
        return when {
            phase < half -> {
                val a = fineOn(n - 1, salt)
                a + (fineOn(n, salt) - a) * smooth(.5 + phase / FINE_FADE)
            }
            phase > FINE - half -> {
                val a = fineOn(n, salt)
                a + (fineOn(n + 1, salt) - a) * smooth((phase - (FINE - half)) / FINE_FADE)
            }
            else -> fineOn(n, salt)
        }
    }

    private fun fineOn(index: Int, salt: Int): Float =
        if (unit(index, salt) < FINE_DUTY) 1f else 0f

    private fun smooth(x: Double): Float {
        val c = x.coerceIn(0.0, 1.0)
        return (c * c * (3 - 2 * c)).toFloat()
    }

    private fun lattice(salt: Int, seconds: Double, period: Double): Float {
        val p = seconds / period
        val i = floor(p)
        val f = (p - i).toFloat()
        val s = f * f * (3 - 2 * f)
        val a = unit(i.toInt(), salt) * 2 - 1
        val b = unit(i.toInt() + 1, salt) * 2 - 1
        return a + (b - a) * s
    }

    /** Deterministic per-index noise in [0,1): an integer mix, never a wall clock. */
    private fun unit(index: Int, salt: Int): Float {
        var h = index * 0x9E3779B1.toInt() + salt * 0x27D4EB2D
        h = h xor (h ushr 15)
        h *= 0x2545F491
        h = h xor (h ushr 13)
        h *= 0x9E3779B1.toInt()
        h = h xor (h ushr 16)
        return ((h ushr 8) / 8388608f) * .5f
    }
}

/**
 * A themed night's plan, evaluated from elapsed frames alone: the phases a preset names, resolved to
 * one answer per layer per instant.
 *
 * A phase begins at its start minute and takes over across [FADE_MINUTES] with a smoothstep, so a
 * boundary has no step in it and no corner either — the share arrives with zero slope at both ends,
 * and the level travels through the same 25 ms smoother every other gain change uses. A layer no phase
 * names plays throughout at its own gain, which is what makes a sound added to a themed mix behave the
 * way a sound added to any other mix does, and a layer a phase leaves out is absent rather than faint.
 *
 * Built on the thread that submits a mix, like the channels themselves; [level] allocates nothing.
 */
class PhaseSchedule private constructor(
    private val starts: DoubleArray,
    private val ids: Array<String>,
    private val levels: FloatArray,
) {
    private val index = HashMap<String, Int>(ids.size * 2)

    init {
        ids.forEachIndexed { i, id -> index[id] = i }
    }

    /** How many layers the phases can name. */
    val layerCount
        get() = ids.size

    fun level(layerId: String, seconds: Double): Float {
        val i = index[layerId] ?: return 1f
        val minutes = seconds / 60.0
        var phase = 0
        while (phase + 1 < starts.size && starts[phase + 1] <= minutes) phase++
        val target = levels[phase * ids.size + i]
        if (phase == 0) return target
        val since = minutes - starts[phase]
        if (since >= FADE_MINUTES) return target
        val from = levels[(phase - 1) * ids.size + i]
        val t = (since / FADE_MINUTES).toFloat()
        return from + (target - from) * (t * t * (3 - 2 * t))
    }

    companion object {
        /**
         * How long a phase takes to take over. Three minutes is the shortest that is unambiguously a
         * change of scene rather than a fade on a layer, and it is long enough that the largest
         * sample-to-sample step it can cause is far below the beds' own.
         */
        const val FADE_MINUTES = 3.0

        /** Null when the mix carries no phases, which is every mix that is not a themed night. */
        fun of(mix: Mix): PhaseSchedule? {
            val phases = mix.endless.phases
            if (phases.isEmpty()) return null
            // The mix's own layer order, so a plan and a re-decoded plan answer identically.
            val ids = Array(mix.layers.size) { mix.layers[it].id }
            val index = HashMap<String, Int>(ids.size * 2)
            ids.forEachIndexed { i, id -> index[id] = i }
            val ordered = phases.sortedBy { it.startMinutes }
            val starts = DoubleArray(ordered.size)
            val levels = FloatArray(ordered.size * ids.size)
            val named = BooleanArray(ids.size)
            ordered.forEachIndexed { h, phase ->
                starts[h] = phase.startMinutes.toDouble()
                for (target in phase.layers) {
                    val i = index[target.layerId] ?: continue
                    named[i] = true
                    levels[h * ids.size + i] = target.level.coerceIn(0f, 1f)
                }
            }
            // A layer no phase names is not part of the plan, so it is not the plan's to silence:
            // it plays at its own gain all night. That is what makes a sound added to a themed mix
            // behave the way a sound added to any other mix does.
            for (i in ids.indices) {
                if (named[i]) continue
                for (h in ordered.indices) levels[h * ids.size + i] = 1f
            }
            return PhaseSchedule(starts, ids, levels)
        }
    }
}

/**
 * What endless night adds to one mix, decided once per [Mix] rather than per block.
 *
 * Companions are drawn from the catalogue *families* the person's own layers belong to — another rain,
 * another wind, another hum — so the mix keeps the character they chose. Music is the one family left
 * out: two independent progressions in different keys is the single way this could sound wrong, so a
 * music layer keeps its own evolution and gets the tone drift and nothing else.
 *
 * Built on the thread that submits a mix, like the channels themselves; the render path only reads it.
 */
class NightPlan(
    val config: EndlessConfig,
    val companions: List<Layer>,
    /** Each companion's id to the id of the layer it belongs to, so the render path can read the parent. */
    val parents: Map<String, String>,
    /**
     * A themed night's plan, when the mix carries one. Then [companions] is empty and the shape table
     * is not consulted: this is the whole schedule.
     */
    val phases: PhaseSchedule? = null,
) {
    /** The seed as an integer salt, so two nights of the same mix differ in schedule and in drift. */
    val salt: Int = (config.seed xor (config.seed ushr 32)).toInt()

    /**
     * How much of [layerId] plays at [seconds] into the night, 0..1: the one question the render path
     * asks, and the one place the two kinds of night differ. The person's own layers answer 1 all
     * night in endless mode, a companion answers its gate, and a themed night answers its phase.
     */
    fun amount(layerId: String, salt: Int, seconds: Double): Float =
        phases?.level(layerId, seconds)
            ?: if (parents.containsKey(layerId))
                Endless.envelope(salt, seconds, Endless.DEPTH, Endless.ramp(seconds))
            else 1f

    companion object {
        /** How many companion beds a mix may gain. Two is enough to be heard; each costs CPU. */
        const val MAX_COMPANIONS = 2

        /** Prefix that keeps a companion's id out of the range a saved mix's layer ids live in. */
        private const val GUEST = "~endless:"

        /** A family needs this many members before another one is a variation rather than a change. */
        private const val MIN_FAMILY = 4

        /** The catalogue grouped by family, built once: `Library.all` never changes at runtime. */
        private val families: Map<String, List<Sound>> by lazy {
            val map = HashMap<String, ArrayList<Sound>>(16)
            for (s in Library.all) {
                val f = family(s) ?: continue
                map.getOrPut(f) { ArrayList(MIN_FAMILY) }.add(s)
            }
            map
        }

        /** Null when a sound may not take part in the night: not bundled, or music. */
        fun family(sound: Sound): String? =
            when {
                !sound.available -> null
                sound.kind == "music" || sound.kind == "binaural" -> null
                else -> "${sound.category}/${sound.kind}"
            }

        fun of(mix: Mix): NightPlan? {
            if (!mix.endless.enabled) return null
            // A themed mix's phase plan is the whole schedule: no shape table, no companions. Two arcs
            // multiplying would be a night neither the theme's author nor `Endless.DEPTH` described.
            val planned = PhaseSchedule.of(mix)
            if (planned != null) return NightPlan(mix.endless, emptyList(), emptyMap(), planned)
            val rng = RandomStream(mix.endless.seed * 0x2545F491L + 17L)
            val taken = HashSet<String>(MAX_COMPANIONS)
            val companions = ArrayList<Layer>(MAX_COMPANIONS)
            val parents = HashMap<String, String>(MAX_COMPANIONS)
            for (layer in mix.layers) {
                if (companions.size == MAX_COMPANIONS) break
                val sound = Library.byId[layer.soundId] ?: continue
                val f = family(sound) ?: continue
                if (!taken.add(f)) continue
                val siblings = families[f]?.filter { it.id != sound.id } ?: continue
                if (siblings.size < MIN_FAMILY - 1) continue
                val bed = guest(siblings[rng.index(siblings.size)], layer, companions.size, mix.endless.seed)
                companions.add(bed)
                parents[bed.id] = layer.id
            }
            return NightPlan(mix.endless, companions, parents)
        }

        /**
         * A companion bed: a real catalogue sibling, so it sounds like what it is named, sitting behind
         * the mix — narrower band, opposite half of the image, at a fraction of its parent's level.
         */
        private fun guest(sound: Sound, parent: Layer, index: Int, seed: Long): Layer {
            val rng = RandomStream(seed * 7919L + index)
            return Library
                .layer(
                    sound.id,
                    // Sixty per cent of its parent, with no floor: a companion of a silent layer is
                    // silent, and a companion can never be louder than the layer it came from.
                    (parent.gain * .60f).coerceIn(0f, .40f),
                    "$GUEST$index:${sound.id}",
                )
                .copy(
                    pan = (parent.pan * -.5f + (rng.unit() - .5f) * .7f).coerceIn(-1f, 1f),
                    width = (.85f + rng.unit() * .15f).coerceIn(0f, 1f),
                    // The parent's tone, kept, and only ever narrowed: a companion of a layer the
                    // person pulled down is pulled down with it, so their equalisation cannot become
                    // headroom for a source they never set. Its own band is narrower than its
                    // parent's, which is what keeps it behind the mix.
                    bass = parent.bass,
                    mid = parent.mid,
                    treble = parent.treble,
                    tilt = parent.tilt,
                    lowPass = minOf(parent.lowPass, 9000f + rng.unit() * 7000f),
                    highPass = maxOf(parent.highPass, 50f + rng.unit() * 40f),
                    // Long enough that turning endless night off mid-night rests the bed rather
                    // than cutting it.
                    fadeSeconds = 8f,
                    // A companion belongs to its parent, so everything the person can do to that layer
                    // it does too: muted, disabled and solo follow it, and so does a stop schedule, so
                    // a night that ends on the visible layers ends on the invisible ones with them.
                    // Anything else would be sound the person cannot see a control for.
                    enabled = parent.enabled,
                    mute = parent.mute,
                    solo = parent.solo,
                    stopAfterMinutes = parent.stopAfterMinutes,
                )
        }
    }
}
