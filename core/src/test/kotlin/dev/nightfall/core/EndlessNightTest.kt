// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

/**
 * Endless night: the night's schedule and the render path that follows it.
 *
 * The thresholds here are measured values plus stated margin; the measurements are printed by these
 * tests and quoted in the PR body. Nothing is tuned to pass — where a number is a bound on a
 * stochastic schedule it is stated as the bound and the spread is printed beside it.
 */
class EndlessNightTest {

    private val rate = M.RATE
    private val loader = M.assetLoader()

    /** One rain layer and one noise layer: two families, so a night earns two companions. */
    private val base =
        Mix(
            layers =
                listOf(
                    Library.layer("moderate-rain", .45f, "endless-rain"),
                    Library.layer("brown", .30f, "endless-brown"),
                )
        )

    private fun night(seed: Long = 1L, depth: Float = .65f) =
        base.copy(endless = EndlessConfig(enabled = true, seed = seed, depth = depth))

    /** The companion channels' envelope salts, derived exactly as the mixer derives them. */
    private fun salts(mix: Mix): IntArray =
        NightPlan.of(mix)!!.companions.map { it.id.hashCode() xor NightPlan.of(mix)!!.salt }.toIntArray()

    private fun envelope(mix: Mix, k: Int, seconds: Double): Float =
        Endless.envelope(salts(mix)[k], seconds, mix.endless.depth, Endless.ramp(seconds))

    /** Where a companion's gate opens (rising = true) or shuts, scanning a second at a time. */
    private fun crossings(mix: Mix, k: Int, upTo: Double): List<Pair<Double, Boolean>> {
        val out = ArrayList<Pair<Double, Boolean>>()
        var previous = 0f
        var t = 0.0
        while (t <= upTo) {
            val e = envelope(mix, k, t)
            if (previous <= 0f && e > 0f) out.add(t to true)
            if (previous > 0f && e <= 0f) out.add(t to false)
            previous = e
            t += 1.0
        }
        return out
    }

    private fun stepDb(a: FloatArray, from: Int, to: Int): Double {
        var p = 0f
        for (i in max(1, from) until minOf(a.size, to)) p = max(p, abs(a[i] - a[i - 1]))
        return 20 * kotlin.math.log10(p.toDouble() + 1e-30)
    }

    private fun lufs(s: M.Signal, fromSecond: Double, seconds: Double = 3.0): Double {
        val from = (fromSecond * rate).toInt()
        val to = from + (seconds * rate).toInt()
        return M.integratedLufs(M.Signal(s.left.copyOfRange(from, to), s.right.copyOfRange(from, to)))
    }

    @Test
    fun endlessOffIsTheMixExactlyAsSaved() {
        val a = M.render(base, 12.0, loader)
        val b = M.render(base.copy(endless = EndlessConfig(enabled = false, seed = 9L)), 12.0, loader)
        assertArrayEquals("left", a.left, b.left, 0f)
        assertArrayEquals("right", a.right, b.right, 0f)
        assertNull("an off mix plans no night", NightPlan.of(base))
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(base)
        assertEquals("an off mix has no companions", 2, mixer.channelCount)
    }

    @Test
    fun aMixSavedBeforeEndlessNightStillMeansWhatItMeant() {
        // A mix written by any build before this feature: no `endless` key at all.
        val old =
            """
            {"id":"old","name":"Old mix","master":0.5,
             "layers":[{"id":"a","soundId":"brown","name":"Brown","gain":0.3},
                       {"id":"b","soundId":"moderate-rain","name":"Moderate rain","gain":0.4}]}
            """
        val mix = PresetJson.decode(old)
        assertFalse("an old mix is not silently endless", mix.endless.enabled)
        assertNull(NightPlan.of(mix))
        assertEquals("an old mix keeps its layers", 2, mix.layers.size)
        val round = PresetJson.decode(PresetJson.encode(mix))
        assertEquals(mix.endless, round.endless)
        assertFalse(round.endless.enabled)
    }

    @Test
    fun endlessNightIsSavedAndRestoredWithItsSeed() {
        val saved = PresetJson.decode(PresetJson.encode(night(seed = 1234L, depth = .4f)))
        assertTrue(saved.endless.enabled)
        assertEquals(1234L, saved.endless.seed)
        assertEquals(.4f, saved.endless.depth, .001f)
        // Depth is clamped like every other audio setting rather than trusted.
        val wild = PresetJson.decode(PresetJson.encode(night(depth = 4f)))
        assertEquals(1f, wild.endless.depth, .001f)
    }

    @Test
    fun companionsComeFromTheFamiliesThePersonAlreadyChose() {
        val plan = NightPlan.of(night())!!
        assertEquals(2, plan.companions.size)
        val families =
            plan.companions.map { NightPlan.family(Library.byId.getValue(it.soundId)) }.toSet()
        assertEquals("one companion per chosen family", setOf("Rain/rain", "Noise/noise"), families)
        for (c in plan.companions) {
            assertNotEquals("a companion is never the layer itself", "moderate-rain", c.soundId)
            assertNotEquals("a companion is never the layer itself", "brown", c.soundId)
            assertTrue("a companion sits behind the mix", c.gain < .35f)
        }
        // Music is the one family left out: a second progression in another key is the one way this
        // could sound wrong.
        val music = Mix(layers = listOf(Library.layer("lo-fi-sleep-music", .4f, "just-music")))
        assertEquals(0, NightPlan.of(music.copy(endless = EndlessConfig(enabled = true, seed = 3L)))!!.companions.size)
        // A family too small to hold a sibling yields nothing rather than reaching outside it.
        val thin = Mix(layers = listOf(Library.layer("fireplace", .4f, "one-fire")))
        assertEquals(0, NightPlan.of(thin.copy(endless = EndlessConfig(enabled = true, seed = 3L)))!!.companions.size)
    }

    @Test
    fun companionsKeepTheirIdentityAcrossSyncsAndStayOutOfTheMixesLayerList() {
        val mixer = PcmMixer(rate, 1024, loader)
        val mix = night()
        mixer.sync(mix)
        val ids = mixer.activeIds()
        assertEquals("the person's own layers are still the whole layer list", mix.layers.map { it.id }, ids)
        assertEquals(2, mixer.channelCount)
        mixer.sync(mix.copy(master = .4f))
        assertEquals("a sync does not restart the night", ids, mixer.activeIds())
        assertEquals(2, mixer.channelCount)
        val coexisting = HashSet(mixer.activeIds() + NightPlan.of(mix)!!.companions.map { it.id })
        assertEquals("no id collision between the mix and its companions", 4, coexisting.size)
    }

    @Test
    fun theSameSeedIsIdenticalAndAnotherSeedIsNot() {
        val first = M.render(night(seed = 1L), 40.0, loader)
        val again = M.render(night(seed = 1L), 40.0, loader)
        assertArrayEquals("same seed, sample for sample", first.left, again.left, 0f)
        assertArrayEquals("same seed, sample for sample", first.right, again.right, 0f)
        val other = M.render(night(seed = 2L), 40.0, loader)
        var differ = 0
        for (i in first.left.indices) if (abs(first.left[i] - other.left[i]) > 1e-6f) differ++
        println("seed 1 vs seed 2: $differ of ${first.left.size} samples differ")
        assertTrue("a different seed must be a different night", differ > first.left.size / 10)
        // And the schedule itself differs, not only the sources.
        val a = crossings(night(seed = 1L), 0, 3 * 3600.0).take(4)
        val b = crossings(night(seed = 2L), 0, 3 * 3600.0).take(4)
        assertNotEquals("seeds share a schedule", a, b)
    }

    @Test
    fun theNightNeverRepeatsItsPatternAtAnyReachableLag() {
        // The crew's repetition standard, applied to the schedule itself: a lag that recurs would
        // reproduce the coarse decisions far more often than independent draws do. A night that
        // repeated would match every granule; chance for this duty is p² + (1-p)².
        val mix = night()
        val granule = 419.0
        val decisions = BooleanArray(6 * 3600 / granule.toInt()) { n ->
            var on = 0f
            var t = n * granule + granule * .25
            while (t < (n + 1) * granule - granule * .25) {
                on = max(on, envelope(mix, 0, t))
                t += 1.0
            }
            on > .5f
        }
        val duty = decisions.count { it }.toDouble() / decisions.size
        val chance = duty * duty + (1 - duty) * (1 - duty)
        fun match(lag: Int): Double {
            var same = 0
            var n = 0
            for (i in 0 until decisions.size - lag) {
                n++
                if (decisions[i] == decisions[i + lag]) same++
            }
            return same.toDouble() / n
        }
        val report = StringBuilder()
        var worstSpike = 0.0
        var at = 0
        for (lag in 3..decisions.size / 2) {
            val spike = match(lag) - (match(lag - 1) + match(lag + 1)) / 2
            if (spike > worstSpike) {
                worstSpike = spike
                at = lag
            }
        }
        report.append(
            "duty %.2f over %d granules, chance %.3f, worst spike +%.3f at %d granules"
                .format(duty, decisions.size, chance, worstSpike, at)
        )
        println(report)
        // A repeat is a lag that agrees far more often than the lags either side of it. Measured: no
        // lag rises more than a few points above its neighbours.
        assertTrue("the schedule repeats at a lag of $at granules: $report", worstSpike < .20)
    }

    @Test
    fun theEnvelopeIsNotAChainOfFixedSinusoids() {
        // The fault this rules out is the one the crew already paid for in wind: two fixed sinusoids
        // whose pattern returns exactly. Measured as the envelope's correlation with itself at lags
        // across the night, with the neighbouring lags beside it, so only a spike counts as a cycle.
        val mix = night()
        val step = 5
        val e = FloatArray(4 * 3600 / step) { envelope(mix, 0, (it * step).toDouble()) }
        fun corr(lagSeconds: Int): Double {
            val lag = lagSeconds / step
            var s = 0.0
            var x = 0.0
            var y = 0.0
            for (i in 0 until e.size - lag) {
                s += e[i] * e[i + lag]
                x += e[i] * e[i]
                y += e[i + lag] * e[i + lag]
            }
            return s / kotlin.math.sqrt(x * y + 1e-30)
        }
        val report = StringBuilder()
        var worstSpike = 0.0
        for (minutes in listOf(12, 21, 34, 55, 89)) {
            val at = corr(minutes * 60)
            val beside = (corr(minutes * 60 - 120) + corr(minutes * 60 + 120)) / 2
            report.append("%d min %.3f (beside %.3f)  ".format(minutes, at, beside))
            worstSpike = max(worstSpike, at - beside)
        }
        println("envelope self-correlation: $report")
        // Measured: no lag rises more than a few points above its neighbours, which is the shape of an
        // aperiodic process; a fixed cycle reads as a peak with dips either side.
        assertTrue("the envelope peaks at a period: $report", worstSpike < .2)
    }

    @Test
    fun aFadeMovesAtAFixedSlewAndNeverSteps() {
        val mix = night(depth = 1f)
        var quickest = 0.0
        var steps = 0
        for (k in 0 until 2) {
            var previous = 0f
            var t = 0.0
            while (t < 8 * 3600.0) {
                val e = envelope(mix, k, t)
                val delta = abs(e - previous)
                if (t > 0) {
                    if (delta > quickest) quickest = delta.toDouble()
                    if (delta > .05) steps++
                }
                previous = e
                t += 1.0
            }
        }
        println("quickest envelope change %.4f per second, %d apparent steps in 16 h".format(quickest, steps))
        assertEquals("a fade must never step", 0, steps)
        // A 120 s and an 80 s smoothstep give at most about .031 a second; .05 is the step threshold.
        assertTrue("fades move at %.4f a second".format(quickest), quickest < .05)
    }

    /**
     * Renders [mix] block by block and keeps only what the caller asks for, so a 40-minute transition
     * costs a window rather than a gigabyte.
     */
    private fun stream(
        mix: Mix,
        seconds: Double,
        marks: List<Double> = emptyList(),
        windows: List<Pair<Double, Double>> = emptyList(),
    ): Transcript {
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(mix)
        val buffer = FloatArray(2048)
        val transcript = Transcript(marks.size, windows.size)
        val near = LongArray(marks.size) { (marks[it] * rate).toLong() }
        val from = IntArray(windows.size) { (windows[it].first * rate).toInt() }
        val to = IntArray(windows.size) { (windows[it].second * rate).toInt() }
        var previous = 0f
        var frame = 0L
        val total = (seconds * rate).toLong() / 1024 * 1024
        while (frame < total) {
            mixer.render(buffer, 1024)
            for (i in 0 until 1024) {
                val v = buffer[i * 2]
                if (frame + i > 0) {
                    val step = abs(v - previous)
                    if (step > transcript.step) transcript.step = step
                    for (m in near.indices)
                        if (abs(frame + i - near[m]) <= 64 && step > transcript.near[m]) {
                            transcript.near[m] = step
                        }
                }
                for (w in from.indices) {
                    val at = (frame + i).toInt()
                    if (at >= from[w] && at < to[w]) transcript.windows[w].add(v)
                }
                previous = v
            }
            frame += 1024
        }
        return transcript
    }

    private class Transcript(marks: Int, windows: Int) {
        var step = 0f
        val near = FloatArray(marks)
        val windows = List(windows) { FloatArray(0).toMutableList() }
        val captured: List<FloatArray>
            get() = windows.map { it.toFloatArray() }
    }

    private fun db(x: Float) = 20 * kotlin.math.log10(x.toDouble() + 1e-30)

    private fun peakDb(a: FloatArray): Double {
        var p = 0f
        for (v in a) if (abs(v) > p) p = abs(v)
        return db(p)
    }

    @Test
    fun noSeamWhereACompanionArrivesOrLeaves() {
        val mix = night()
        val marks = crossings(mix, 0, 40 * 60.0)
        assertTrue("the night must bring a companion in within 40 minutes: $marks", marks.any { it.second })
        val last = marks.firstOrNull { !it.second }?.first ?: (marks.last().first + 120.0)
        val seconds = last + 120.0
        val at = marks.map { it.first }
        println("arrivals and departures in %.0f s: $marks".format(seconds))
        val endless = stream(mix, seconds, at)
        val plain = stream(base, seconds)
        val material = db(max(endless.step, plain.step))
        for (i in at.indices) {
            println(
                "  %s at %.0f s: boundary %.1f dB, material %.1f dB"
                    .format(if (marks[i].second) "arrival" else "departure", at[i], db(endless.near[i]), material)
            )
            // Measured: a transition is as smooth as the material around it, because its gain travels
            // through the same 25 ms smoother every other gain change in the mixer uses.
            assertTrue(
                "click where a source ${if (marks[i].second) "arrives" else "leaves"} at %.0f s: %.1f dB vs %.1f dB"
                    .format(at[i], db(endless.near[i]), material),
                db(endless.near[i]) <= material + 3.0,
            )
        }
    }

    @Test
    fun aSourceEnteringDoesNotChangeTheMixsLevel() {
        val mix = night()
        val marks = crossings(mix, 0, 60 * 60.0)
        val arrival = marks.first { it.second }.first
        val leave = marks.first { !it.second && it.first > arrival }.first
        val inside = arrival + 90.0
        val outside = leave - 130.0
        val windows = listOf(outside to outside + 10.0, inside to inside + 10.0)
        val seconds = maxOf(inside, outside) + 60.0
        val endless = stream(mix, seconds, windows = windows).captured
        val plain = stream(base, seconds, windows = windows).captured
        fun lufs(x: FloatArray) = M.integratedLufs(M.Signal(x, x))
        val out = lufs(endless[0])
        val inTheNight = lufs(endless[1])
        val reference = lufs(plain[1])
        val control = lufs(plain[0])
        println(
            "companion out %.2f LUFS against %.2f, in %.2f LUFS against %.2f (the plain mix, same windows)"
                .format(out, control, inTheNight, reference)
        )
        // Measured 0.37 LU entering and 0.45 LU leaving on ten-second windows; the bound here is the
        // 1 LU the preset suite already holds between presets, which is the contract this must meet.
        // Ten seconds of a rain swell is itself one draw, so this test cannot hold a tighter number
        // than the eight-hour proof does against a night of the same mix.
        assertTrue("a source entering moved the level to $inTheNight", abs(inTheNight - reference) < 1.0)
        assertTrue("the night before the source arrived was $out", abs(out - control) < 1.0)
    }

    @Test
    fun aCompanionIsALevelCalibratedSourceAndNotASecondGainTable() {
        // A companion is a real catalogue sibling and it goes through `Levels` like everything else: at
        // equal gain it is as loud as a layer of the same kind. That is what lets the compensation
        // reason about power at all, and it is the line the crew already paid to hold.
        val mix = night()
        val companion = NightPlan.of(mix)!!.companions[0]
        fun loudness(layer: Layer) =
            M.integratedLufs(M.render(Mix(layers = listOf(layer)), 60.0, loader))
        val gain = .3f
        val guest = loudness(companion.copy(gain = gain))
        val parent = loudness(Library.layer("moderate-rain", gain, "cal-parent"))
        println(
            "companion %s at %.2f gain %.2f LUFS, a catalogue layer at the same gain %.2f LUFS"
                .format(companion.soundId, gain, guest, parent)
        )
        assertEquals("a companion is trimmed by Levels like any other source", parent, guest, 1.5)
        assertEquals("a companion is not its parent", false, companion.soundId == "moderate-rain")
    }

    @Test
    fun theNightChangesTheWaveformByAboutTheCompanionsOwnLevel() {
        // The instrument that can actually see a source arrive. A band profile is normalised to its
        // own peak, and a companion is drawn from the same family, so it moves almost every band by
        // the same decibels and the normalised profile barely notices. The difference between the
        // evolving render and the plain one cannot be fooled that way.
        val mix = night()
        val marks = crossings(mix, 0, 60 * 60.0)
        val arrival = marks.first { it.second }.first
        val inside = arrival + 150.0
        // A moment when neither companion is in, so the difference is the tone drift alone.
        var quiet = -1.0
        var t = 180.0
        while (quiet < 0 && t < 4 * 3600.0) {
            var clear = true
            for (k in 0 until 2) {
                for (d in 0 until 10) if (envelope(mix, k, t + d) > 0f) clear = false
            }
            if (clear) quiet = t
            t += 10.0
        }
        assertTrue("the night never leaves both companions out", quiet > 0)
        println("a companion is in from %.0f s, and neither is in at %.0f s".format(inside, quiet))
        val windows = listOf(quiet to quiet + 10.0, inside to inside + 10.0)
        val seconds = maxOf(inside, quiet) + 60.0
        val evolving = stream(mix, seconds, windows = windows).captured
        val plain = stream(base, seconds, windows = windows).captured
        fun rms(a: FloatArray) = kotlin.math.sqrt(a.fold(0.0) { acc, v -> acc + v * v } / a.size)
        fun difference(k: Int): Double {
            val d = FloatArray(evolving[k].size) { evolving[k][it] - plain[k][it] }
            return 20 * kotlin.math.log10(rms(d) / rms(plain[k]) + 1e-30)
        }
        val alone = difference(0)
        val present = difference(1)
        println(
            "the night differs from the plain mix by %.1f dB with a companion in, %.1f dB with the drift alone"
                .format(present, alone)
        )
        // Measured: a companion that is in moves the waveform by about its own level; the tone drift on
        // its own is far quieter. The margin is the point — the difference is a source, not a rounding
        // error, and the drift is a tint rather than an event.
        assertTrue("the companion is too quiet to be a source ($present dB)", present > -14.0)
        assertTrue("the drift alone is not quiet ($alone dB)", alone < -26.0)
        assertTrue("a companion arriving is not distinguishable ($present vs $alone)", present > alone + 10.0)
    }

    @Test
    fun aCompanionFollowsEverythingThePersonCanDoToItsParent() {
        // A companion the person cannot see must still obey the controls of the layer it belongs to.
        // Each case renders the same mix with the night on and asks whether anything is left.
        fun peakOf(mix: Mix, seconds: Double = 20.0) = M.peakDb(M.render(mix, seconds, loader))
        val muted = night().let { n -> n.copy(layers = n.layers.map { it.copy(mute = true) }) }
        val disabled = night().let { n -> n.copy(layers = n.layers.map { it.copy(enabled = false) }) }
        val silent = night().let { n -> n.copy(layers = n.layers.map { it.copy(gain = 0f) }) }
        for ((label, mix) in listOf("muted" to muted, "disabled" to disabled, "at zero gain" to silent)) {
            // The plain mix is silence in all three cases, so any sound here is a companion that
            // outlived the layer it came from.
            val plain = M.peakDb(M.render(base.copy(layers = mix.layers), 20.0, loader))
            val withNight = peakOf(mix)
            println("%s: %.1f dBFS with the night, %.1f dBFS without".format(label, withNight, plain))
            assertEquals("a companion kept playing after its layer was $label", plain, withNight, .5)
        }
        // A stop schedule that ends the visible layer has to end the companion too.
        val stopping = night().let { n ->
            n.copy(layers = n.layers.map { it.copy(stopAfterMinutes = 1f) })
        }
        val transcript = stream(stopping, 200.0)
        val tail = stream(stopping, 200.0, windows = listOf(150.0 to 180.0)).captured[0]
        println("after its layer's stop schedule: %.1f dBFS in minutes two to three".format(peakDb(tail)))
        assertTrue("a companion outlived its layer's stop schedule", peakDb(tail) < -60.0)
        assertTrue("nothing was rendered at all", transcript.step > 0f)
    }

    @Test
    fun aMixAttenuatedByItsOwnToneControlsKeepsItsLevel() {
        // A layer pulled down by its own EQ is quieter than its slider says. Compensation that counted
        // the slider alone would treat that quiet as headroom and let a companion raise the mix.
        val attenuated =
            night().let { n ->
                n.copy(
                    layers =
                        n.layers.map {
                            it.copy(bass = -12f, mid = -12f, treble = -12f)
                        }
                )
            }
        val plain =
            base.let { b ->
                b.copy(layers = b.layers.map { it.copy(bass = -12f, mid = -12f, treble = -12f) })
            }
        val arrival = crossings(attenuated, 0, 60 * 60.0).first { it.second }.first
        val at = arrival + 150.0
        val windows = listOf(at to at + 30.0)
        val seconds = at + 60.0
        val evolving = stream(attenuated, seconds, windows = windows).captured[0]
        val reference = stream(plain, seconds, windows = windows).captured[0]
        fun lufs(x: FloatArray) = M.integratedLufs(M.Signal(x, x))
        println(
            "EQ-attenuated mix with a companion in %.2f LUFS, the same mix plain %.2f LUFS"
                .format(lufs(evolving), lufs(reference))
        )
        // Measured 1.1 LU on a ten-second window and less on thirty. The bound is the 1.5 LU
        // `LevelCalibrationTest` already allows a catalogue source against its reference, and the
        // residual is the one thing power compensation cannot fix: it equalises power, not loudness,
        // so a companion whose spectrum differs from its parent's shifts the K-weighted sum slightly.
        assertTrue(
            "a companion raised an attenuated mix's level (${lufs(evolving)} vs ${lufs(reference)})",
            abs(lufs(evolving) - lufs(reference)) < 1.5,
        )
        // And the tone factor is exactly 1 at flat controls, so nothing about this moved the measured
        // level of a plain mix.
        assertEquals(1f, PcmMixer.tonePower(0f, 0f, 0f, 0f, .5f), .001f)
        assertEquals(.0625f, PcmMixer.tonePower(-12f, -12f, -12f, 0f, .5f), .005f)
    }

    @Test
    fun endlessRendersWithoutAllocating() {
        val bean =
            java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val buf = FloatArray(1024 * 2)
        for (mix in listOf(night(), night(seed = 5L, depth = 1f))) {
            val mixer = PcmMixer(rate, 1024, loader)
            mixer.sync(mix)
            repeat(400) { mixer.render(buf) }
            val before = bean.currentThreadAllocatedBytes
            repeat(2000) { mixer.render(buf) }
            val perBlock = (bean.currentThreadAllocatedBytes - before) / 2000.0
            println("endless seed ${mix.endless.seed}: $perBlock B/block")
            // The same bound the render-path test holds the plain mixer to.
            assertTrue("endless night allocated $perBlock B/block", perBlock < 8.0)
        }
    }

    @Test
    fun theLayerControlsStillBindWhileTheNightDrifts() {
        // A slider is still a loudness control with the night running: doubling it must raise the
        // level, and the drift must not wander the mix into the limiter.
        fun level(gain: Float) =
            M.integratedLufs(
                M.render(
                    night().copy(
                        layers = base.layers.map { if (it.soundId == "moderate-rain") it.copy(gain = gain) else it }
                    ),
                    60.0,
                    loader,
                )
            )
        val quiet = level(.2f)
        val loud = level(.4f)
        println("rain at 20%% %.2f LUFS, at 40%% %.2f LUFS".format(quiet, loud))
        assertTrue("a layer slider stopped meaning loudness ($quiet vs $loud)", loud - quiet > 2.0)
    }

    @Test
    fun theNightStaysOutOfTheLimiter() {
        val mix = night(depth = 1f)
        val s = M.render(mix.copy(master = .5f), 120.0, loader)
        val peak = M.peakDb(s)
        val plain = M.peakDb(M.render(base.copy(master = .5f), 120.0, loader))
        val kneeDb = 20 * kotlin.math.log10(PcmMixer.LIMIT_KNEE.toDouble())
        println("peak %.3f dBFS with the night, %.3f dBFS without, knee %.3f dBFS".format(peak, plain, kneeDb))
        // `peakDb` is dBFS and the knee is a linear amplitude; comparing them directly would pass at
        // any output at all, which is what this test used to do.
        assertTrue("the night pushed the peak to $peak dBFS", peak < kneeDb)
    }
}
