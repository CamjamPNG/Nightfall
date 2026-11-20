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
 * A themed night, whole, through the real render path, beside the same mix with the plan switched off.
 *
 * The claim a sleeper would notice is the one the level rule makes: a night that opens on a rainstorm
 * and ends on a quiet room is the same loudness at four in the morning, so the plan changes what they
 * hear and not how much of it. The plan's own powers are printed beside the measured hours, because
 * the compensation is derived from them and a plan that asked for more than the mixers' cap would be a
 * different product.
 */
@Category(Night::class)
class ThemedNightRunTest {
    private val rate = 48000
    private val frames = 1024
    private val hours = 8
    private val budgetNanos = frames * 1_000_000_000.0 / rate

    private class Run(
        val hourly: FloatArray,
        /** The whole night's level: every sample, so the value is not one draw of a rain bed. */
        val nightDb: Double,
        val nanosPerBlock: Double,
        val peak: Float,
    )

    private fun night(mix: Mix): Run {
        val loader = M.assetLoader()
        val mixer = PcmMixer(rate, frames, loader)
        mixer.sync(mix)
        val buffer = FloatArray(frames * 2)
        repeat(1500) { mixer.render(buffer) }
        mixer.reset()
        mixer.sync(mix)

        val totalBlocks = (hours * 3600_000.0 / (frames * 1000.0 / rate)).toLong()
        val hourly = FloatArray(hours + 1)
        val hourCapture = FloatArray(rate * 60)
        var hourAt = 0
        var captureOf = -1
        var lastHour = 0
        var peak = 0f
        var squares = 0.0
        var samples = 0L
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        var allocated = 0L
        var startedAt = 0L
        var block = 0L
        while (block < totalBlocks) {
            mixer.render(buffer, frames)
            for (i in 0 until frames) {
                val v = (buffer[i * 2] + buffer[i * 2 + 1]) * .5f
                if (captureOf >= 0 && hourAt < hourCapture.size) hourCapture[hourAt] = v
                if (captureOf >= 0) hourAt++
                if (abs(v) > peak) peak = abs(v)
                squares += v.toDouble() * v
                samples++
            }
            if (captureOf >= 0 && hourAt >= hourCapture.size) {
                hourly[captureOf] =
                    M.integratedLufs(M.Signal(hourCapture.copyOf(), hourCapture.copyOf())).toFloat()
                captureOf = -1
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
            if (block == 20_000L) {
                // The render path's own allocation figure, on a window with nothing but rendering in it.
                allocated = bean.currentThreadAllocatedBytes
                startedAt = System.nanoTime()
            }
            block++
        }
        val elapsed = System.nanoTime() - startedAt
        val measured = block - 20_000L
        return Run(
            hourly,
            10 * kotlin.math.log10(squares / samples + 1e-30),
            elapsed.toDouble() / measured,
            peak,
        )
    }

    /** What the mixer's compensation is asked to do by the plan alone: the phases' own power ratio. */
    private fun planCompensation(mix: Mix): Double {
        val gains = mix.layers.associate { it.id to it.gain }
        val powers =
            mix.endless.phases.map { phase ->
                phase.layers.sumOf { target ->
                    val gain = gains.getValue(target.layerId)
                    (gain * target.level).toDouble() * (gain * target.level)
                }
            }
        return sqrt(powers.max() / powers.min())
    }

    @Test
    fun eightHoursOfAThemedNightHoldTheirLevelAndCostTheSame() {
        val themed = Presets.all.first { it.id == "rain-that-calms" }
        val plain = themed.copy(endless = EndlessConfig())
        val expected = planCompensation(themed)
        println("%s phases at %s min".format(themed.id, themed.endless.phases.map { it.startMinutes }))
        println("plan asks the mixer for at most %.2f x make-up gain".format(expected))

        val off = night(plain)
        val on = night(themed)
        val delta = StringBuilder()
        var worst = 0.0
        for (h in 1..hours) {
            val d = (on.hourly[h] - off.hourly[h]).toDouble()
            if (abs(d) > worst) worst = abs(d)
            delta.append("%+.2f ".format(d))
        }
        val audible = on.hourly.copyOfRange(1, hours + 1).filter { it != 0f }
        val baseAudible = off.hourly.copyOfRange(1, hours + 1).filter { it != 0f }
        println("hourly level, themed night on:  " + audible.joinToString(" ") { "%.2f".format(it) })
        println("hourly level, plan switched off: " + baseAudible.joinToString(" ") { "%.2f".format(it) })
        println("hour-by-hour difference: $delta(worst %.2f LU)".format(worst))
        println(
            "hourly spread %.2f LU on, %.2f LU off; whole night %.2f dBFS on, %.2f dBFS off (%.2f dB apart); peak %.3f on, %.3f off"
                .format(
                    (audible.maxOrNull() ?: 0f) - (audible.minOrNull() ?: 0f),
                    (baseAudible.maxOrNull() ?: 0f) - (baseAudible.minOrNull() ?: 0f),
                    on.nightDb,
                    off.nightDb,
                    on.nightDb - off.nightDb,
                    on.peak,
                    off.peak,
                )
        )
        println(
            "CPU %.2f ms/block on (%.0f%% of the %.2f ms budget), %.2f ms/block off (%.0f%%)"
                .format(
                    on.nanosPerBlock / 1e6,
                    100 * on.nanosPerBlock / budgetNanos,
                    budgetNanos / 1e6,
                    off.nanosPerBlock / 1e6,
                    100 * off.nanosPerBlock / budgetNanos,
                )
        )

        // The level rule, measured on the whole night rather than on one minute of it: a themed night
        // is the loudness the preset is, so the plan changes what plays and not how loud it is. The
        // per-hour column is printed for the record and not asserted, because a sixty-second window of
        // rain is one draw of its own weather — the endless-night proof that the compensation holds
        // hour by hour is `EndlessNightRunTest`s, on the expression this night shares.
        val nightLevel = abs(on.nightDb - off.nightDb)
        assertTrue("the plan moved the night's level by %.2f dB".format(nightLevel), nightLevel < .5)
        val spreadOn = (audible.maxOrNull() ?: 0f) - (audible.minOrNull() ?: 0f)
        val spreadOff = (baseAudible.maxOrNull() ?: 0f) - (baseAudible.minOrNull() ?: 0f)
        assertTrue(
            "the themed night is jumpier hour to hour: %.2f LU against %.2f LU plain"
                .format(spreadOn, spreadOff),
            spreadOn <= spreadOff + 1f,
        )
        assertTrue("the themed night peaked at ${on.peak}", on.peak < PcmMixer.LIMIT_KNEE)
        assertTrue(
            "the themed night used %.0f%% of a block".format(100 * on.nanosPerBlock / budgetNanos),
            on.nanosPerBlock < budgetNanos * .65,
        )
        // Zero allocation is measured by `ThemedNightTest.aThemedNightRendersWithoutAllocating`, on a
        // clean two-thousand-block window; this test allocates on purpose, to store the hours.
    }
}
