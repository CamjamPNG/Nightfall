// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * A whole night of the evolving mix, through the real render path, beside the same mix with endless
 * night off.
 *
 * The claim this exists to hold is the one a sleeper would notice: the bed is not the same bed an hour
 * later, while the level they set is the level they get, at no CPU worth naming. Both nights render
 * from the same layer ids, so the two hourly levels differ only by what the night itself did — the
 * compensation, not the weather's own dice.
 *
 * Evolution is measured the only way that survives a stochastic bed: a spectrum snapshot every 30
 * seconds, a ten-minute moving average over those snapshots, and the spread of that average across the
 * night. A steady mix holds its ten-minute average; a mix that keeps arriving at new textures does not.
 */
@Category(Night::class)
class EndlessNightRunTest {
    private val rate = 48000
    private val frames = 1024
    private val hours = 8
    private val budgetNanos = frames * 1_000_000_000.0 / rate

    /** Seconds of audio in one spectrum snapshot, and the spacing between snapshots. */
    private val snapshotSeconds = 30

    /** Snapshots averaged into one point of the slow trend: twenty of them are ten minutes. */
    private val trendWindow = 20

    private class Night(
        val hourly: FloatArray,
        val profiles: List<DoubleArray>,
        val nanosPerBlock: Double,
        val peak: Float,
        val dirtySlices: Int,
        val worstSlice: Long,
    )

    private fun night(mix: Mix): Night {
        val loader = M.assetLoader()
        val mixer = PcmMixer(rate, frames, loader)
        mixer.sync(mix)
        val buffer = FloatArray(frames * 2)

        // A warm-up of the same shape, so the CPU figure below is not the first minute of a cold JIT.
        repeat(1500) { mixer.render(buffer) }
        mixer.reset()
        mixer.sync(mix)

        val totalBlocks = (hours * 3600_000.0 / (frames * 1000.0 / rate)).toLong()
        val hourly = FloatArray(hours + 1)
        val profiles = ArrayList<DoubleArray>(hours * 3600 / snapshotSeconds)
        val hourCapture = FloatArray(rate * 60)
        val snapshot = FloatArray(rate * snapshotSeconds)
        var hourAt = 0
        var snapshotAt = 0
        var captureOf = -1
        var peak = 0f
        var dirtySlices = 0
        var worstSlice = 0L
        var sliceBlocks = 0L
        var sliceDirty = false
        val allocations = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        @Suppress("DEPRECATION") val tid = Thread.currentThread().id
        var sliceStart = allocations.getThreadAllocatedBytes(tid)
        val startedAt = System.nanoTime()
        var block = 0L
        var lastHour = 0
        while (block < totalBlocks) {
            mixer.render(buffer, frames)
            for (i in 0 until frames) {
                val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                if (captureOf >= 0 && hourAt < hourCapture.size) hourCapture[hourAt] = v
                if (snapshotAt < snapshot.size) snapshot[snapshotAt] = v
                hourAt++
                snapshotAt++
                if (abs(v) > peak) peak = abs(v)
            }
            if (captureOf >= 0 && hourAt >= hourCapture.size) {
                hourly[captureOf] =
                    M.integratedLufs(M.Signal(hourCapture.copyOf(), hourCapture.copyOf())).toFloat()
                captureOf = -1
                hourAt = 0
            }
            if (snapshotAt >= snapshot.size) {
                profiles.add(CatalogueMetrics.bandProfile(M.Signal(snapshot.copyOf(), snapshot)))
                snapshotAt = 0
                sliceDirty = true
            }
            val second = block * (frames.toDouble() / rate)
            if (captureOf < 0) {
                val hour = (second / 3600).toInt()
                if (hour != lastHour && hour in 1..hours) {
                    lastHour = hour
                    captureOf = hour
                    hourAt = 0
                }
            }
            sliceBlocks++
            if (sliceBlocks == 1000L) {
                val now = allocations.getThreadAllocatedBytes(tid)
                if (!sliceDirty && block > 20_000L && now - sliceStart > 0) {
                    dirtySlices++
                    if (now - sliceStart > worstSlice) worstSlice = now - sliceStart
                }
                sliceStart = now
                sliceBlocks = 0
                sliceDirty = false
            }
            block++
        }
        val elapsed = System.nanoTime() - startedAt
        return Night(
            hourly,
            profiles,
            elapsed.toDouble() / totalBlocks,
            peak,
            dirtySlices,
            worstSlice,
        )
    }

    /**
     * How far the mix's ten-minute average spectrum travels across the night: the mean per-band
     * deviation of that average, and the widest single band's swing.
     */
    private fun slowTrend(profiles: List<DoubleArray>): Pair<Double, Double> {
        require(profiles.size > trendWindow * 2)
        var deviation = 0.0
        var widest = 0.0
        for (band in profiles[0].indices) {
            val slow =
                DoubleArray(profiles.size - trendWindow + 1) { i ->
                    var sum = 0.0
                    for (k in 0 until trendWindow) sum += profiles[i + k][band]
                    sum / trendWindow
                }
            val mean = slow.average()
            var variance = 0.0
            for (v in slow) variance += (v - mean) * (v - mean)
            deviation += sqrt(variance / slow.size)
            widest = max(widest, slow.max() - slow.min())
        }
        return (deviation / profiles[0].size) to widest
    }

    @Test
    fun eightHoursOfAnEvolvingMixHoldTheirLevelAndCostTheSame() {
        val plain = Presets.all.first { it.id == "city-rain" }
        val evolving = plain.copy(endless = EndlessConfig(enabled = true, seed = 1L))
        val plan = NightPlan.of(evolving)!!
        println(
            "night companions: " +
                plan.companions.joinToString(", ") {
                    "${it.soundId} (${NightPlan.family(Library.byId.getValue(it.soundId))} at ${"%.2f".format(it.gain)})"
                }
        )
        val base = night(plain)
        val growth = night(evolving)

        val delta = StringBuilder()
        var worst = 0.0
        for (h in 1..hours) {
            val d = (growth.hourly[h] - base.hourly[h]).toDouble()
            if (abs(d) > worst) worst = abs(d)
            delta.append("%+.2f ".format(d))
        }
        val audible = growth.hourly.copyOfRange(1, hours + 1).filter { it != 0f }
        val baseAudible = base.hourly.copyOfRange(1, hours + 1).filter { it != 0f }
        println("hourly level, night on:  " + audible.joinToString(" ") { "%.2f".format(it) })
        println("hourly level, night off: " + baseAudible.joinToString(" ") { "%.2f".format(it) })
        println("hour-by-hour difference: $delta(worst ${"%.2f".format(worst)} LU)")
        println(
            "spread %.2f LU on, %.2f LU off; peak %.3f on, %.3f off"
                .format(
                    (audible.maxOrNull() ?: 0f) - (audible.minOrNull() ?: 0f),
                    (baseAudible.maxOrNull() ?: 0f) - (baseAudible.minOrNull() ?: 0f),
                    growth.peak,
                    base.peak,
                )
        )
        println(
            "CPU %.2f ms/block on (%.0f%% of the %.2f ms budget), %.2f ms/block off (%.0f%%)"
                .format(
                    growth.nanosPerBlock / 1e6,
                    100 * growth.nanosPerBlock / budgetNanos,
                    budgetNanos / 1e6,
                    base.nanosPerBlock / 1e6,
                    100 * base.nanosPerBlock / budgetNanos,
                )
        )
        val (drift, widest) = slowTrend(growth.profiles)
        val (baseline, baseWidest) = slowTrend(base.profiles)

        // Composition, which is what the night actually changes: at the same instant of the same
        // mix, how far is the spectrum from the night that did not evolve? This is the one comparison
        // the material's own variety cannot fake, because both nights share every layer's seed.
        val distances =
            DoubleArray(growth.profiles.size) {
                CatalogueMetrics.bandDistance(growth.profiles[it], base.profiles[it])
            }
        val meanDistance = distances.average()
        val moved = distances.count { it > .5 } * 100.0 / distances.size

        println(
            "ten-minute spectrum drift: %.2f dB on (widest band %.2f dB), %.2f dB off (%.2f dB)"
                .format(drift, widest, baseline, baseWidest)
        )
        println(
            "spectrum against the plain mix at the same instant: mean %.2f dB, %.0f%% of the night's"
                .format(meanDistance, moved) +
                " thirty-second snapshots more than 0.5 dB away"
        )

        // The level the person set is the level they get, all night: hour by hour, the difference is
        // the compensation's own error and nothing else, because both nights share every layer's seed.
        assertTrue("the night moved the hourly level by ${"%.2f".format(worst)} LU: $delta", worst < .5)
        assertTrue("the evolving night peaked at ${growth.peak}", growth.peak < PcmMixer.LIMIT_KNEE)
        // No assertion on the two numbers above: a band profile is normalised to its own peak and a
        // companion comes from the same family, so it moves nearly every band by the same decibels.
        // They are here as the honest size of the effect and because a large change there would be a
        // different mix, not this one. What a source arriving does to the waveform is measured by
        // `EndlessNightTest.theNightChangesTheWaveformByAboutTheCompanionsOwnLevel`.
        // The render path's own budget: the app warns at 65% of a block.
        assertTrue(
            "the evolving night used ${"%.0f".format(100 * growth.nanosPerBlock / budgetNanos)}% of a block",
            growth.nanosPerBlock < budgetNanos * .65,
        )
        // Zero allocation is measured properly by `EndlessNightTest.endlessRendersWithoutAllocating`,
        // on a clean two-thousand-block window; this test allocates on purpose, to store the spectra.
    }

    @Test
    fun theRecordingCycleStillDoesNotCorrelate() {
        // The crew's repetition standard, applied to the evolving mix where it can apply: a bundled
        // recording's loop material is the same material with the night on, so this number is expected
        // to hold rather than fall. It is here so the claim is measured rather than assumed.
        val plain = Presets.all.first { it.id == "city-rain" }
        val evolving = plain.copy(endless = EndlessConfig(enabled = true, seed = 1L))
        val audio = java.io.File("../app/src/pcm/assets/audio/distant-city-rain.pcm")
        val cycle = RecordingSource.cycleSeconds((audio.length() / 2).toInt())
        fun measure(mix: Mix, lag: Double) = CatalogueMetrics.selfCorrelation(mix, lag, 12.0, 24000)
        for ((label, mix) in listOf("off" to plain, "on" to evolving)) {
            val atCycle = measure(mix, cycle)
            val control = measure(mix, cycle / 2)
            println("%-3s cycle %.1f s: correlation %.3f, control lag %.3f".format(label, cycle, atCycle, control))
            assertTrue("$label still repeats its cycle ($atCycle)", atCycle < .8)
            assertTrue("$label control lag correlates $control", abs(control) < .2)
        }
    }
}
