// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.*
import org.junit.Test

/**
 * Writes the loudness ledger the PR body quotes: per source family and for realistic mixes, in the
 * shipped gain staging. Not an assertion test; [LoudnessTargetTest] holds the thresholds.
 */
class LoudnessReportTest {

    private val profiles =
        listOf(
            "brown",
            "pink",
            "white",
            "moderate-rain",
            "rolling-thunder",
            "strong-wind",
            "fireplace",
            "blizzard",
            "ocean-waves",
            "ticking-clock",
            "box-fan",
            "crickets",
            "lo-fi-sleep-music",
            "binaural",
        )

    private val loader = M.assetLoader()

    private fun row(label: String, s: M.Signal, seconds: Double): String {
        val rms = M.rmsDb(s.left) - 3.0 // single channel RMS -> per-channel level
        val peak = M.peakDb(s.mono())
        val tp = M.truePeakDb(s.mono())
        val crest = peak - rms
        val st = M.shortTermLufs(s, min(3.0, seconds))
        val il = M.integratedLufs(s)
        return String.format(
            "%-26s %8.1f %8.1f %8.1f %7.1f %8.1f %8.1f %9.5f%n",
            label,
            rms,
            peak,
            tp,
            crest,
            st,
            il,
            M.dcOffset(s.mono()),
        )
    }

    @Test
    fun writeLoudnessLedger() {
        val seconds = 20.0
        val out = StringBuilder()
        val head =
            String.format(
                "%-26s %8s %8s %8s %7s %8s %8s %9s%n",
                "profile",
                "RMSdBFS",
                "peakdB",
                "truePk",
                "crest",
                "STlufs",
                "ILufs",
                "DC",
            )
        out.append("=== per layer, Library default gain 0.35, master 0.5 (shipped default) ===\n")
        out.append(head)
        for (id in profiles) {
            val mix = Mix(layers = listOf(Library.layer(id, .35f, "p-$id")), master = .5f)
            out.append(row(id, M.render(mix, seconds, loader), seconds))
        }
        out.append("\n=== per layer at gain 1.0, master 1.0 (source + staging ceiling) ===\n")
        out.append(head)
        for (id in profiles) {
            val mix = Mix(layers = listOf(Library.layer(id, 1f, "f-$id")), master = 1f)
            out.append(row(id, M.render(mix, seconds, loader), seconds))
        }
        out.append("\n=== mixes ===\n")
        out.append(head)
        val four =
            Mix(
                layers =
                    listOf(
                        Library.layer("moderate-rain", .5f, "m-rain"),
                        Library.layer("brown", .35f, "m-brown"),
                        Library.layer("lo-fi-sleep-music", .55f, "m-music"),
                        Library.layer("wind-through-pine-trees", .3f, "m-wind"),
                    ),
                master = .5f,
            )
        out.append(row("four-layer", M.render(four, seconds, loader), seconds))
        out.append(row("default preset (9)", M.render(Presets.all.last(), seconds, loader), seconds))
        out.append(
            row(
                "four-layer master 1.0",
                M.render(four.copy(master = 1f), seconds, loader),
                seconds,
            )
        )

        out.append("\n=== stacking: nine-layer preset truncated to N layers ===\n")
        out.append(head)
        val nine = Presets.all.last()
        for (n in 1..nine.layers.size) {
            val mix = nine.copy(layers = nine.layers.take(n))
            out.append(row("default preset N=$n", M.render(mix, seconds, loader), seconds))
        }

        out.append("\n=== stacking the same source nine times (brown 0.35) ===\n")
        out.append(head)
        for (n in 1..9) {
            val mix =
                Mix(layers = List(n) { Library.layer("brown", .35f, "stack-$it") }, master = .5f)
            out.append(row("brown x$n", M.render(mix, seconds, loader), seconds))
        }

        val dir = File("../artifacts").apply { mkdirs() }
        File(dir, "loudness-report.txt").writeText(out.toString())
        print(out)
    }

    /** Extreme user settings: the limiter's job, and how much of the material it touches. */
    @Test
    fun writeLimiterReport() {
        val seconds = 20.0
        val out = StringBuilder()
        out.append("\n=== maximum user settings (limiter ceiling work) ===\n")
        out.append(
            String.format(
                "%-34s %8s %8s %8s %8s %8s %9s%n",
                "case",
                "RMSdBFS",
                "peakdB",
                "truePk",
                "STlufs",
                "ILufs",
                "atCeiling",
            )
        )
        val nine = Presets.all.last()
        val cases =
            linkedMapOf(
                "default preset, master 1.0" to nine.copy(master = 1f),
                "default preset, all gains 1.0, master 1.0" to
                    nine.copy(master = 1f, layers = nine.layers.map { it.copy(gain = 1f) }),
                "9x brown gain 1.0 master 1.0" to
                    Mix(master = 1f, layers = List(9) { Library.layer("brown", 1f, "mx-$it") }),
                "9x white gain 1.0 master 1.0" to
                    Mix(master = 1f, layers = List(9) { Library.layer("white", 1f, "mw-$it") }),
                "9x recording gain 1.0 master 1.0" to
                    Mix(
                        master = 1f,
                        layers =
                            List(9) {
                                Library.layer(
                                    listOf(
                                            "forest-birds",
                                            "crickets",
                                            "frogs",
                                            "nighttime-forest",
                                            "quiet-library",
                                            "distant-city-rain",
                                            "soft-coffee-shop-ambience",
                                            "forest-birds",
                                            "crickets",
                                        )
                                        [it],
                                    1f,
                                    "mr-$it",
                                )
                            },
                    ),
            )
        for ((label, mix) in cases) {
            val s = M.render(mix, seconds, loader)
            val mono = s.mono()
            val atCeiling = mono.count { abs(it) >= PcmMixer.LIMIT_CEILING * .995f } * 100.0 / mono.size
            out.append(
                String.format(
                    "%-34s %8.1f %8.1f %8.1f %8.1f %8.1f %8.2f%%%n",
                    label,
                    M.rmsDb(s.left) - 3,
                    M.peakDb(mono),
                    M.truePeakDb(mono),
                    M.shortTermLufs(s, 3.0),
                    M.integratedLufs(s),
                    atCeiling,
                )
            )
        }
        val dir = File("../artifacts").apply { mkdirs() }
        File(dir, "limiter-report.txt").writeText(out.toString())
        print(out)
    }
}
