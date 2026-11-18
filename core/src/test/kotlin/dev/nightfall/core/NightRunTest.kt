// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test

/**
 * A whole night through the real render path: eight hours of a preset that mixes a bundled recording
 * with synthesized layers, rendered in the 1024-frame blocks the app uses, with a sleep timer running
 * against a simulated clock.
 *
 * Everything else in the suite measures seconds to minutes. A sleeper notices the properties this test
 * holds for the whole eight hours: the audio never drifts from the clock it is meant to follow, nothing
 * grows, the limiter never engages, no DC accumulates, the level does not creep, and the timer fades out
 * where it was configured to. It also measures what it costs to prove — wall time and heap — because a
 * claim nobody can afford to re-check is not evidence.
 */
class NightRunTest {
    private val rate = 48000
    private val frames = 1024
    private val hours = 8

    /** The block rate the mixer is driven at, in milliseconds per block. */
    private val blockMs = frames * 1000.0 / rate

    @Test
    fun eightHoursOfRenderingDoNotDriftGrowOrCreep() {
        val loader = M.assetLoader()
        // A bundled recording (which wraps about 160 times in a night) plus a synthesized layer, at a
        // shipped level with headroom, so the limiter can be asserted silent rather than merely quiet.
        val mix = Presets.all.first { it.id == "city-rain" }
        val mixer = PcmMixer(rate, frames, loader)
        mixer.sync(mix)
        val buffer = FloatArray(frames * 2)

        val totalBlocks = (hours * 3600_000 / blockMs).toLong()
        val sleepAfterMs = 7 * 3600_000 + 45 * 60_000
        val fadeMs = 20_000L
        val timer = SleepTimer()
        timer.start(0, sleepAfterMs.toLong(), fadeMs)

        val allocations = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        var sliceStart = 0L
        var sliceBlocks = 0L
        var sliceDirty = false
        var dirtySlices = 0
        var worstSlice = 0L
        var allocatedPerBlock = 0.0

        val hourlyLufs = FloatArray(hours + 1)
        var peak = 0f
        var sum = 0.0
        var sums = 0L
        var nonFinite = 0L
        var timerBlocks = -1
        var fadedBlocks = 0
        var silentFrom = -1L
        var previousGain = 1f
        val hourHeap = LongArray(hours + 1)

        val startedAt = System.nanoTime()
        var block = 0L
        var lastHour = -1
        while (block < totalBlocks) {
            val now = (block * blockMs).toLong()
            val gain = timer.gain(now)
            mixer.render(buffer, frames, gain)

            for (i in 0 until frames * 2) {
                val v = buffer[i]
                if (!v.isFinite()) {
                    nonFinite++
                } else {
                    sum += v
                    sums++
                    peak = max(peak, abs(v))
                }
            }

            if (gain < 1f && timerBlocks < 0) timerBlocks = block.toInt()
            if (gain > 0f && gain < 1f) fadedBlocks++
            if (silentFrom < 0 && gain <= 0f) silentFrom = block
            if (gain > previousGain) fail("the timer fade went back up at block $block")
            previousGain = gain

            val hour = (now / 3600_000).toInt()
            var measuredHour = false
            if (hour != lastHour && hour <= hours) {
                lastHour = hour
                if (gain > 0f) hourlyLufs[hour] = level(mixer, loader, mix, rate)
                System.gc()
                hourHeap[hour] = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                measuredHour = true
            }
            if (measuredHour) sliceDirty = true

            // Allocation is checked in slices of a thousand blocks, so a slice that merely averages
            // zero cannot hide a real allocation: every slice without hourly bookkeeping must be zero.
            sliceBlocks++
            if (sliceBlocks == SLICE_BLOCKS) {
                val nowAllocated = allocations.getThreadAllocatedBytes(tid)
                val spent = nowAllocated - sliceStart
                if (!sliceDirty && block > WARMUP_BLOCKS) {
                    if (spent > 0) {
                        dirtySlices++
                        worstSlice = max(worstSlice, spent)
                    }
                    allocatedPerBlock =
                        max(allocatedPerBlock, spent.toDouble() / SLICE_BLOCKS)
                }
                sliceStart = nowAllocated
                sliceBlocks = 0
                sliceDirty = false
            }

            block++
        }
        val elapsed = (System.nanoTime() - startedAt) / 1e9

        val dc = sum / sums
        val creep = hourlyLufs.copyOfRange(1, hours + 1).filter { it > -70f && it != 0f }
        val creepSpread = (creep.maxOrNull() ?: 0f) - (creep.minOrNull() ?: 0f)
        val growth = hourHeap[hours] - hourHeap[2]

        println(
            "eight hours: ${"%.0f".format(elapsed)} s of wall time, " +
                "$dirtySlices allocating slices (worst ${worstSlice} bytes) after warm-up, " +
                "peak ${"%.3f".format(peak)}, DC ${"%.6f".format(dc)}, " +
                "level creep ${"%.2f".format(creepSpread)} LU, " +
                "heap ${growth / 1_048_576} MB from hour 2 to hour 8, " +
                "timer first faded at block $timerBlocks, silent from block $silentFrom, " +
                "$fadedBlocks blocks in fade"
        )
        println(
            "hourly level: " +
                hourlyLufs
                    .copyOfRange(1, hours + 1)
                    .joinToString(" ") { "${"%.2f".format(it)}" }
        )

        assertEquals("not one non-finite sample in eight hours", 0L, nonFinite)
        assertTrue(
            "the limiter must never engage: peak $peak over the ${PcmMixer.LIMIT_KNEE} knee",
            peak < PcmMixer.LIMIT_KNEE,
        )
        assertEquals("the sample ceiling must never be reached", peak, PcmMixer.limit(peak), 0f)
        assertTrue("DC offset accumulated: $dc", abs(dc) < .001)
        assertTrue("the level crept ${"%.2f".format(creepSpread)} LU across the night", creepSpread < 1.0)
        assertTrue("the heap grew ${growth / 1_048_576} MB across six hours", growth < 16L * 1_048_576)
        assertEquals(
            "render allocated in $dirtySlices slices, worst $worstSlice bytes",
            0,
            dirtySlices,
        )

        // The timer must fade exactly where it was told to, not earlier and not forever.
        val expectedFirst = (sleepAfterMs / blockMs).toInt()
        assertTrue(
            "the timer first faded at block $timerBlocks, not $expectedFirst",
            abs(timerBlocks - expectedFirst) <= 1,
        )
        val expectedFadeBlocks = (fadeMs / blockMs).toInt()
        assertTrue(
            "the fade lasted $fadedBlocks blocks, not about $expectedFadeBlocks",
            abs(fadedBlocks - expectedFadeBlocks) <= 2,
        )
        assertTrue("the night never went silent", silentFrom > 0)
    }

    /** Integrated loudness of a fresh render of the same mix, used to look for creep between hours. */
    private fun level(
        mixer: PcmMixer,
        loader: (String) -> SampleData?,
        mix: Mix,
        rate: Int,
    ): Float {
        val scratch = PcmMixer(rate, frames, loader)
        scratch.sync(mix)
        val buffer = FloatArray(frames * 2)
        val seconds = 5
        val samples = FloatArray(rate * seconds)
        var at = 0
        while (at < samples.size) {
            scratch.render(buffer, frames)
            for (i in 0 until frames * 2) {
                // The mix is a stereo pair; both sides carry the same signal, so scale each one to
                // keep the channel sum equal to the mono level rather than 3 dB above it.
                if (at + i / 2 < samples.size) samples[at + i / 2] = buffer[i] * .7071f
            }
            at += frames
        }
        return M.integratedLufs(M.Signal(samples, samples.copyOf())).toFloat()
    }

    private companion object {
        /** Blocks before the allocation measurement starts: JIT and first-use work is not drift. */
        const val WARMUP_BLOCKS = 20_000L

        /** Allocation is checked in slices of this many blocks once the warm-up is done. */
        const val SLICE_BLOCKS = 1_000L
    }
}
