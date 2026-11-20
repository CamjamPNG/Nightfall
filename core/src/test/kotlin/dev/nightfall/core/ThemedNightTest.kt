// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

/**
 * Themed nights: a preset that is a plan over the night rather than a snapshot of layers.
 *
 * Every threshold here is a measured value with margin, printed by the test that asserts it. The one
 * thing none of this can establish is how a themed night sounds — nobody has listened to one — so
 * these tests measure continuity, level and distinctness, and the PR says plainly that they are not a
 * listening test.
 */
class ThemedNightTest {

    private val rate = M.RATE
    private val loader = M.assetLoader()

    private val themes = Presets.all.filter { it.endless.phases.isNotEmpty() }

    private fun theme(id: String) = Presets.all.first { it.id == id }

    private val storm = theme("storm-that-passes")

    /**
     * Only the two phase tables on either side of a boundary decide what happens at it, and the
     * schedule is a function of elapsed time alone, so replaying those two from the start of a fresh
     * render measures the boundary with three minutes of audio instead of the whole night.
     */
    private fun boundary(mix: Mix, index: Int): Mix {
        val phases = mix.endless.phases
        return mix.copy(
            endless =
                mix.endless.copy(
                    phases =
                        listOf(
                            phases[index - 1].copy(startMinutes = 0f),
                            phases[index].copy(startMinutes = .25f),
                        )
                )
        )
    }

    /** The themed mix reduced to one phase's layer set, so that phase can be measured on its own. */
    private fun phaseAlone(mix: Mix, index: Int): Mix {
        val phase = mix.endless.phases[index]
        val ids = phase.layers.map { it.layerId }.toSet()
        return mix.copy(
            layers = mix.layers.filter { it.id in ids },
            endless = mix.endless.copy(phases = listOf(phase.copy(startMinutes = 0f))),
        )
    }

    /** RMS level of [from]..[to] in dBFS, from one render that ends at [to]. */
    private fun levelDb(mix: Mix, from: Double, to: Double): Double {
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(mix)
        val buffer = FloatArray(2048)
        val first = (from * rate).toLong()
        val last = (to * rate).toLong()
        var frame = 0L
        var power = 0.0
        var count = 0L
        val total = last / 1024 * 1024
        while (frame < total) {
            mixer.render(buffer, 1024)
            for (i in 0 until 1024) {
                val at = frame + i
                if (at >= first && at < last) {
                    val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                    power += v.toDouble() * v
                    count++
                }
            }
            frame += 1024
        }
        return 10 * kotlin.math.log10(power / count + 1e-30)
    }

    /** The largest sample-to-sample step the render makes inside [from]..[to], in seconds. */
    private fun largestStep(mix: Mix, from: Double, to: Double): Float {
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(mix)
        val buffer = FloatArray(2048)
        val first = (from * rate).toLong()
        val last = (to * rate).toLong()
        var previous = 0f
        var frame = 0L
        var step = 0f
        val total = (to * rate).toLong() / 1024 * 1024
        while (frame < total) {
            mixer.render(buffer, 1024)
            for (i in 0 until 1024) {
                val v = buffer[i * 2]
                if (frame + i >= first && frame + i > 0) step = max(step, abs(v - previous))
                previous = v
            }
            frame += 1024
        }
        return step
    }

    private fun lufs(mix: Mix, seconds: Double = 20.0): Double = M.integratedLufs(M.render(mix, seconds, loader))

    @Test
    fun aThemedNightIsOneScheduleWithNoCompanions() {
        // Endless night's two other mechanisms would each be a second author of the night: a companion
        // is a bed the plan did not name, and the shape table is an arc the plan did not write. A themed
        // mix's plan replaces both, so neither is present — and the schedule's answer is exactly the
        // phase's share, not a share multiplied by a presence duty.
        for (mix in themes) {
            val plan = NightPlan.of(mix)!!
            assertNotNull("${mix.id} plans no phases", plan.phases)
            assertTrue("${mix.id} gained companions", plan.companions.isEmpty())
            assertTrue("${mix.id} has companion parents", plan.parents.isEmpty())
            assertEquals(
                "${mix.id} has layers no phase names",
                mix.layers.size,
                plan.phases!!.layerCount,
            )
        }
        val plan = NightPlan.of(storm)!!
        val first = storm.layers.first { it.soundId == "heavy-rain" }
        assertEquals(1f, plan.amount(first.id, 0, 0.0), 0f)
        assertEquals(1f, plan.amount(first.id, 0, 10 * 60.0), 0f)
        assertEquals(0f, plan.amount(first.id, 0, 300 * 60.0), 0f)
        // A different seed moves the tone drift and nothing else, so the composition is the same night.
        assertEquals(
            plan.amount(first.id, 1, 100 * 60.0),
            plan.amount(first.id, 987_654, 100 * 60.0),
            0f,
        )
    }

    @Test
    fun eachPhaseAsksForItsOwnLayerSet() {
        val plan = NightPlan.of(storm)!!
        fun id(sound: String) = storm.layers.first { it.soundId == sound }.id
        val cases =
            listOf(
                Triple(30.0, "heavy-rain", 1f),
                Triple(30.0, "thunderstorm", 1f),
                Triple(30.0, "quiet-room", 0f),
                Triple(200.0, "moderate-rain", 1f),
                Triple(200.0, "rain-on-metal-roof", .55f),
                Triple(200.0, "howling-wind", 0f),
                Triple(300.0, "light-drizzle", 1f),
                Triple(300.0, "quiet-room", 0f),
                Triple(430.0, "rain-over-distant-ocean", 1f),
                Triple(430.0, "light-drizzle", .35f),
                Triple(430.0, "quiet-room", .25f),
            )
        for ((minute, sound, want) in cases)
            assertEquals(
                "$sound at $minute min",
                want.toDouble(),
                plan.amount(id(sound), 0, minute * 60).toDouble(),
                .001,
            )
    }

    @Test
    fun aPhaseTakesOverOverThreeMinutesAndNeverSteps() {
        // The schedule itself, dense in time across every real boundary of every theme: the shape of
        // the change, before the render path sees it. A smoothstep over 180 s moves at most .0083 of a
        // share a second, and a step would be an infinite slew.
        var quickest = 0.0
        var steps = 0
        for (mix in themes) {
            val plan = NightPlan.of(mix)!!
            for (phase in mix.endless.phases.drop(1)) {
                for (layer in mix.layers) {
                    val from = ((phase.startMinutes - 10) * 60).toDouble()
                    val to = ((phase.startMinutes + 10) * 60).toDouble()
                    val salt = layer.id.hashCode() xor plan.salt
                    var previous = plan.amount(layer.id, salt, from)
                    var t = from + 1.0
                    while (t <= to) {
                        val now = plan.amount(layer.id, salt, t)
                        val slew = abs(now - previous) / 1.0
                        if (slew > quickest) quickest = slew
                        if (now != previous && slew == 0.0) steps++
                        previous = now
                        t += 1.0
                    }
                }
            }
        }
        println("quickest phase change %.4f of a share a second, %d steps".format(quickest, steps))
        assertEquals("a phase must never step", 0, steps)
        assertTrue("phases move at %.4f a second".format(quickest), quickest < .02)
    }

    @Test
    fun noSeamAtAnyPhaseBoundary() {
        // A boundary is a level change that has to be inaudible. Two claims, both measured:
        //
        //   1. the schedule, sample-dense in time, never steps (`aPhaseTakesOver...` above), and
        //   2. the rendered level across the half second either side of a boundary moves no more than
        //      the material's own level moves between two other adjacent half seconds in the same
        //      render. A switch would move the whole difference between the two phases — decibels — in
        //      one instant; this compares the boundary against the same night's own breathing, which is
        //      the only honest baseline for beds with birds and rain in them.
        val report = StringBuilder()
        var worst = 0.0
        for (mix in themes) {
            for (i in 1 until mix.endless.phases.size) {
                val at = boundary(mix, i)
                val hop = .5
                val marks = DoubleArray(36) { it * hop }
                val levels = levelsIn(at, marks, 17.5)
                val adjacent = DoubleArray(levels.size - 1) { abs(levels[it + 1] - levels[it]) }
                // The boundary is the window that starts at 15 s; its own change is the pair across it.
                val seam = adjacent[30]
                var elsewhere = 0.0
                for (k in adjacent.indices) if (k != 30) elsewhere = max(elsewhere, adjacent[k])
                if (seam > worst) worst = seam
                report.append(
                    "%s phase %d: %.3f dB across the boundary, %.3f dB between the widest other two half seconds\n"
                        .format(mix.id, i, seam, elsewhere)
                )
                assertTrue(
                    "${mix.id} phase $i moves %.3f dB at its boundary against %.3f elsewhere".format(seam, elsewhere),
                    seam <= elsewhere,
                )
            }
        }
        print(report)
        println("worst boundary step %.3f dB".format(worst))
    }

    /** RMS level of each consecutive pair of [marks] in dBFS, from one render that ends at [to]. */
    private fun levelsIn(mix: Mix, marks: DoubleArray, to: Double): DoubleArray {
        val mixer = PcmMixer(rate, 1024, loader)
        mixer.sync(mix)
        val buffer = FloatArray(2048)
        val power = DoubleArray(marks.size - 1)
        val count = LongArray(marks.size - 1)
        var frame = 0L
        val total = (to * rate).toLong() / 1024 * 1024
        while (frame < total) {
            mixer.render(buffer, 1024)
            for (i in 0 until 1024) {
                val second = (frame + i).toDouble() / rate
                for (w in power.indices) {
                    if (second < marks[w] || second >= marks[w + 1]) continue
                    val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                    power[w] += v.toDouble() * v
                    count[w]++
                }
            }
            frame += 1024
        }
        return DoubleArray(power.size) { 10 * kotlin.math.log10(power[it] / count[it] + 1e-30) }
    }

    @Test
    fun aThemedNightIsSavedAndRestoredWithItsPlan() {
        val saved = PresetJson.decode(PresetJson.encode(storm))
        assertEquals(storm.endless, saved.endless)
        assertEquals(storm.layers.map { it.id }, saved.layers.map { it.id })
        assertTrue("the plan survives a save", saved.endless.phases.size == 4)
        // A saved and restored themed night is the same night, sample for sample.
        assertArrayEquals(
            "left",
            M.render(storm, 4.0, loader).left,
            M.render(saved, 4.0, loader).left,
            0f,
        )
    }

    @Test
    fun aMixSavedBeforeThemesStillMeansWhatItMeant() {
        val old =
            """{"id":"saved","name":"Old mix","master":0.5,"layers":[{"id":"a","soundId":"pink",""" +
                """"name":"Pink","gain":0.3}],"endless":{"enabled":true,"seed":7}}"""
        val mix = PresetJson.decode(old)
        assertTrue("an old endless mix is still endless", mix.endless.enabled)
        assertEquals(7L, mix.endless.seed)
        assertEquals("an old mix has no plan", 0, mix.endless.phases.size)
        assertNull("an old mix keeps endless night's own schedule", NightPlan.of(mix)!!.phases)
        val plain = PresetJson.decode("""{"id":"p","name":"Plain","layers":[]}""")
        assertFalse(plain.endless.enabled)
        assertNull(NightPlan.of(plain))
    }

    @Test
    fun aPhaseCannotReachALayerTheMixNoLongerHas() {
        // Editing is the point: a themed preset is an ordinary mix, so a person can remove a layer or
        // add one. A removed layer's phase targets are ignored, and a layer no phase names plays at its
        // own gain all night rather than falling silent.
        val without = storm.copy(layers = storm.layers.filter { it.soundId != "thunderstorm" })
        val plan = NightPlan.of(without)!!
        assertEquals(without.layers.size, plan.phases!!.layerCount)
        val guitar = Library.layer("pink", .2f, "added-by-hand")
        val extended = without.copy(layers = without.layers + guitar)
        val edited = NightPlan.of(extended)!!
        assertEquals(1f, edited.amount(guitar.id, 0, 0.0), 0f)
        assertEquals(1f, edited.amount(guitar.id, 0, 30 * 60.0), 0f)
        // And the phase text that names the removed layer does not survive sanitising as a live target.
        val decoded =
            PresetJson.decode(PresetJson.encode(storm.copy(layers = without.layers)))
        assertEquals(without.layers.map { it.id }, decoded.layers.map { it.id })
        assertEquals(storm.endless.phases, decoded.endless.phases)
    }

    @Test
    fun everyPhaseHasItsOwnTexture() {
        // A theme nobody can tell apart from another is a menu that lies, and so is a phase that is the
        // previous phase with a different name. Each phase is rendered on its own from its own table and
        // compared by band profile, both with its neighbours in the same night and with the presets
        // that already exist.
        val presets = Presets.all.filter { it.endless.phases.isEmpty() }
        val profiles = presets.associate { it.id to CatalogueMetrics.bandProfile(M.render(it, 20.0, loader)) }
        val report = StringBuilder()
        var closestNeighbour = Double.MAX_VALUE
        var closestCatalogue = Double.MAX_VALUE
        for (mix in themes) {
            val phases = mix.endless.phases.indices.map { CatalogueMetrics.bandProfile(M.render(phaseAlone(mix, it), 20.0, loader)) }
            for (i in phases.indices) {
                val nearest =
                    phases.indices.filter { it != i }.minOf { CatalogueMetrics.bandDistance(phases[i], phases[it]) }
                val catalogue =
                    profiles.minBy { CatalogueMetrics.bandDistance(phases[i], it.value) }
                val distance = CatalogueMetrics.bandDistance(phases[i], catalogue.value)
                closestNeighbour = minOf(closestNeighbour, nearest)
                closestCatalogue = minOf(closestCatalogue, distance)
                report.append(
                    "%s phase %d (+%.0f min): %.2f dB from its nearest phase, %.2f dB from the nearest preset (%s)\n"
                        .format(mix.id, i, mix.endless.phases[i].startMinutes, nearest, distance, catalogue.key)
                )
            }
        }
        print(report)
        println("closest phase pair %.2f dB, closest preset %.2f dB".format(closestNeighbour, closestCatalogue))
        assertTrue("two phases are only $closestNeighbour dB apart", closestNeighbour >= .5)
        assertTrue("a phase is only $closestCatalogue dB from an existing preset", closestCatalogue >= .5)
    }

    @Test
    fun everyPhaseIsAtThePresetsOwnLevel() {
        // Level is `Levels`' authority and the mixer's compensation holds it across a phase change; this
        // measures each phase on its own to show what the plans ask of that compensation. The eight-hour
        // night is measured by `ThemedNightRunTest`.
        val report = StringBuilder()
        var worst = 0.0
        for (mix in themes) {
            val levels = ArrayList<Double>()
            for (i in mix.endless.phases.indices) levels.add(lufs(phaseAlone(mix, i)))
            val spread = levels.maxOrNull()!! - levels.minOrNull()!!
            worst = max(worst, spread)
            report.append(
                "%s phases: %s LUFS, spread %.2f LU\n"
                    .format(mix.id, levels.joinToString(" ") { "%.2f".format(it) }, spread)
            )
        }
        print(report)
        // Four seeds would be the preset test's standard; a phase's own level is here as the size of
        // what the compensation takes on, which is why the bound is 3 LU rather than the presets' 1.
        assertTrue("a theme's phases differ by %.2f LU".format(worst), worst <= 3.5)
    }

    @Test
    fun renamingLayersCarriesThePlanWithThem() {
        // This is the model half of the Presets-tab defect: `usePreset` renames the layers so a whole
        // mix crossfades in, and a plan written in the old ids would then name nothing. The rename and
        // the plan move together or the night silently becomes a plain mix.
        val renamed = storm.reidentify { "fresh-${it.soundId}" }
        assertEquals("every layer got a new id", storm.layers.size, renamed.layers.size)
        assertTrue(renamed.layers.none { l -> storm.layers.any { it.id == l.id } })
        val oldIds = storm.layers.map { it.id }.toSet()
        assertTrue(
            "a phase target still names a layer that no longer exists",
            renamed.endless.phases.flatMap { it.layers }.none { it.layerId in oldIds },
        )
        assertEquals(
            "every share survived the rename",
            storm.endless.phases.map { phase -> phase.layers.map { it.level } },
            renamed.endless.phases.map { phase -> phase.layers.map { it.level } },
        )
        val plan = NightPlan.of(renamed)!!
        val before = NightPlan.of(storm)!!
        for (sound in listOf("heavy-rain", "rain-on-metal-roof", "quiet-room", "light-drizzle")) {
            val old = storm.layers.first { it.soundId == sound }.id
            val fresh = renamed.layers.first { it.soundId == sound }.id
            assertEquals(
                "$sound answers the same at every hour",
                before.amount(old, 0, 300 * 60.0),
                plan.amount(fresh, 0, 300 * 60.0),
                0f,
            )
            assertEquals(
                "$sound answers the same at the start",
                before.amount(old, 0, 0.0),
                plan.amount(fresh, 0, 0.0),
                0f,
            )
        }
    }

    @Test
    fun mutingOneLayerDoesNotMakeAnotherLouder() {
        // Compensation answers for what the night did, never for a control the person moved: a muted
        // layer leaves the reference as it leaves the output, so the layers still playing keep the
        // level they had.
        val themed = theme("rain-that-calms")
        val target = themed.layers.first { it.soundId == "rain-on-leaves" }
        val muted = themed.copy(layers = themed.layers.map { if (it.id == target.id) it.copy(mute = true) else it })
        val removed = themed.copy(layers = themed.layers.filter { it.id != target.id })
        val a = lufs(muted)
        val b = lufs(removed)
        println("themed: one layer muted %.2f LUFS, the same layer gone %.2f LUFS".format(a, b))
        assertTrue("muting a layer moved the rest by %.2f LU".format(abs(a - b)), abs(a - b) < .5)

        // And the same for endless night, which shares the expression.
        val two =
            Mix(
                layers =
                    listOf(
                        Library.layer("moderate-rain", .45f, "mute-rain"),
                        Library.layer("brown", .45f, "mute-brown"),
                    ),
                endless = EndlessConfig(enabled = true, seed = 3L),
            )
        val heldBack = two.copy(layers = two.layers.map { if (it.soundId == "brown") it.copy(mute = true) else it })
        val absent = two.copy(layers = two.layers.filter { it.soundId != "brown" })
        val c = lufs(heldBack)
        val d = lufs(absent)
        println("endless: one layer muted %.2f LUFS, the same layer gone %.2f LUFS".format(c, d))
        assertTrue("muting a layer moved the night by %.2f LU".format(abs(c - d)), abs(c - d) < .5)
    }

    @Test
    fun selectingAnotherNightStartsItAtZero() {
        // A night's identity is the config, not the layer set: editing a gain continues the night,
        // another night begins one.
        val first = theme("storm-that-passes")
        val second = theme("rain-that-calms")
        val mixer = PcmMixer(rate, 1024, loader)
        val buffer = FloatArray(2048)
        mixer.sync(first)
        repeat(2000) { mixer.render(buffer) }
        val played = mixer.nightSeconds
        assertTrue("the first night did not advance", played > 40.0)

        mixer.sync(first.copy(layers = first.layers.map { it.copy(gain = it.gain * .5f) }))
        repeat(100) { mixer.render(buffer) }
        assertTrue(
            "editing a gain restarted the night (${mixer.nightSeconds} s)",
            mixer.nightSeconds > played,
        )

        mixer.sync(second)
        assertEquals("another night started already elapsed", 0.0, mixer.nightSeconds, 0.0)
    }

    @Test
    fun closelySpacedPhasesStartFromWhatIsPlaying() {
        // Phases closer together than the three-minute transition are legal, and they must not step:
        // the second transition begins from where the first one had actually got to, not from a target
        // it never reached.
        val mix =
            Mix(
                layers = listOf(Library.layer("brown", .4f, "phased")),
                endless =
                    EndlessConfig(
                        enabled = true,
                        phases =
                            listOf(
                                NightPhase(0f, listOf(NightPhaseLayer("phased", 1f))),
                                NightPhase(.5f, listOf(NightPhaseLayer("phased", 0f))),
                                NightPhase(1f, listOf(NightPhaseLayer("phased", 1f))),
                            ),
                    ),
            )
        val plan = NightPlan.of(mix)!!
        val justBefore = plan.amount("phased", 0, 59.9)
        val justAfter = plan.amount("phased", 0, 60.1)
        println("across a 30 s phase: %.4f then %.4f".format(justBefore, justAfter))
        assertTrue(
            "a closely spaced phase steps from ${justBefore} to ${justAfter}",
            abs(justAfter - justBefore) < .05,
        )
        // The whole plan, sample-dense, still moves at the documented rate and never steps.
        var quickest = 0.0
        var previous = plan.amount("phased", 0, 0.0)
        var at = 0.001
        while (at <= 200.0) {
            val now = plan.amount("phased", 0, at)
            quickest = max(quickest, abs(now - previous) / 0.001)
            previous = now
            at += 0.001
        }
        println("quickest change %.4f a second".format(quickest))
        assertTrue("closely spaced phases move at %.4f a second".format(quickest), quickest < .02)
    }

    @Test
    fun aThemedNightRendersWithoutAllocating() {
        val bean =
            ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val buffer = FloatArray(1024 * 2)
        for (mix in listOf(storm, theme("forest-dusk"))) {
            val mixer = PcmMixer(rate, 1024, loader)
            mixer.sync(mix)
            repeat(400) { mixer.render(buffer) }
            val before = bean.currentThreadAllocatedBytes
            repeat(2000) { mixer.render(buffer) }
            val perBlock = (bean.currentThreadAllocatedBytes - before) / 2000.0
            println("${mix.id}: $perBlock B/block")
            assertTrue("${mix.id} allocated $perBlock B/block", perBlock < 8.0)
        }
    }
}
