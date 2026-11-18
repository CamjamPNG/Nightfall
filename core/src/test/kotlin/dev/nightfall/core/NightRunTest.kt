// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

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
@Category(Night::class)
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
        val tid = Thread.currentThread().threadId()
        var sliceStart = 0L
        var sliceBlocks = 0L
        var sliceDirty = false
        var dirtySlices = 0
        var worstSlice = 0L
        var allocatedPerBlock = 0.0

        val hourlyLufs = FloatArray(hours + 1)
        // Five seconds of the mixer that has been running all night, captured live: a fresh mixer's
        // first seconds would measure the same thing at hour seven as at hour one, which is the one
        // question this test exists to answer.
        val capture = FloatArray(rate * CAPTURE_SECONDS)
        var captureAt = 0
        var captureHour = -1
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

            if (captureHour >= 0) {
                for (i in 0 until frames) {
                    val at = captureAt + i
                    if (at < capture.size) capture[at] = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                }
                captureAt += frames
                if (captureAt >= capture.size) {
                    hourlyLufs[captureHour] = M.integratedLufs(M.Signal(capture.copyOf(), capture.copyOf())).toFloat()
                    captureHour = -1
                    System.gc()
                    hourHeap[lastHour] = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                }
            }

            val hour = (now / 3600_000).toInt()
            if (hour != lastHour && hour <= hours) {
                lastHour = hour
                // Hours that end in the timer's fade or after it measure silence by design, so only a
                // window that starts while the mix is still audible is captured.
                if (gain > 0f && hour >= 1) {
                    captureHour = hour
                    captureAt = 0
                }
            }
            if (captureHour >= 0) sliceDirty = true

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
        System.gc()
        hourHeap[hours] = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val elapsed = (System.nanoTime() - startedAt) / 1e9

        val dc = sum / sums
        // Hours that ended in the timer's fade captured nothing, so the trend uses the audible ones.
        val audible = hourlyLufs.copyOfRange(1, hours + 1).filter { it != 0f }
        val creepSpread = (audible.maxOrNull() ?: 0f) - (audible.minOrNull() ?: 0f)
        // Creep is a trend, not the variation a rain mix has between any two minutes: compare the
        // mean of the first third of the night with the mean of the last third.
        val third = audible.size / 3
        val early = if (third > 0) audible.take(third).average() else 0.0
        val late = if (third > 0) audible.takeLast(third).average() else 0.0
        val creepTrend = late - early
        val growth = hourHeap[hours] - hourHeap[2]

        println(
            "eight hours: ${"%.0f".format(elapsed)} s of wall time, " +
                "$dirtySlices allocating slices (worst ${worstSlice} bytes) after warm-up, " +
                "peak ${"%.3f".format(peak)}, DC ${"%.6f".format(dc)}, " +
                "level spread ${"%.2f".format(creepSpread)} LU, trend ${"%.2f".format(creepTrend)} LU, " +
                "heap ${hourHeap[2] / 1_048_576} MB at hour 2, ${hourHeap[hours] / 1_048_576} MB at hour 8, " +
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
        assertTrue(
            "the level drifted ${"%.2f".format(creepTrend)} LU from the first third of the night to the last",
            abs(creepTrend) < .5,
        )
        // Measured after a collection at each end, so this bounds growth rather than asserting a
        // number that GC timing decides.
        assertTrue(
            "the heap moved ${growth / 1_048_576} MB between hour 2 and hour 8",
            abs(growth) < 24L * 1_048_576,
        )
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

    private companion object {
        /** Blocks before the allocation measurement starts: JIT and first-use work is not drift. */
        const val WARMUP_BLOCKS = 20_000L

        /** Allocation is checked in slices of this many blocks once the warm-up is done. */
        const val SLICE_BLOCKS = 1_000L

        /** Seconds of the running mixer kept for the hourly level, taken from its own output. */
        /** Seconds of the running mixer kept for the hourly level: short windows of rain vary by LU. */
        const val CAPTURE_SECONDS = 60
    }
}
