// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Generates the trim table in [Levels]. Every catalogue entry is rendered alone at layer gain 1.0 and
 * master 0.25 - twelve dB below full scale, where no source reaches the limiter's knee - and its gated
 * integrated loudness is measured. The trim that would bring it to [Levels.REFERENCE_LUFS] at master
 * 1.0 is printed as a Kotlin map entry.
 *
 * Long renders on purpose: thunder events are minutes apart and music level wanders, so a short probe
 * would calibrate against a lucky stretch.
 *
 *     NIGHTFALL_CALIBRATE=1 ./gradlew :core:test --tests '*LevelCalibrationTest*'
 */
class LevelCalibrationTest {

    private fun secondsFor(s: Sound) =
        when (s.kind) {
            "thunder" -> 90.0
            "music" -> 45.0
            "recording" -> 30.0
            "clock" -> 45.0
            else -> 20.0
        }

    @Test
    fun printTrims() {
        assumeTrue(System.getenv("NIGHTFALL_CALIBRATE") == "1")
        val loader = M.assetLoader()
        val rows = StringBuilder()
        rows.append(
            String.format("%-40s %7s %7s %8s %8s %8s %s%n", "id", "LUFS", "dBTP", "loudLvl", "peakCap", "trim", "note")
        )
        val trims = LinkedHashMap<String, Float>()
        for (s in Library.all.filter { it.available }) {
            val layer = Library.layer(s.id, 1f, "cal-${s.id}")
            val signal = M.render(Mix(layers = listOf(layer), master = .25f), secondsFor(s), loader)
            // 12 dB below full master, where nothing reaches the limiter: a storm's transients would
            // otherwise enter it and their attenuation would be folded into the correction.
            val lufs = M.integratedLufs(signal) + 12.0
            val peak = M.truePeakDb(signal)
            // The render already applied the current table; loudness tracks the trim 1:1 below the
            // limiter, so one pass converges instead of compounding.
            val trim = Levels.trimDb(s) + (Levels.REFERENCE_LUFS - lufs)
            val note =
                if (peak + 12 + trim > 20 * log10(PcmMixer.LIMIT_KNEE.toDouble()))
                    "crest %.1f dB, peaks into the limiter".format(peak + 12 - lufs)
                else ""
            trims[s.id] = trim.toFloat()
            rows.append(
                String.format(
                    "%-40s %7.1f %7.1f %8.1f %8.1f %8.1f %s%n",
                    s.id,
                    lufs,
                    peak,
                    Levels.REFERENCE_LUFS - lufs,
                    peak + 12,
                    trim,
                    note,
                )
            )
        }
        val table = StringBuilder("\n    private val trimsDb: Map<String, Float> =\n        mapOf(\n")
        for ((id, db) in trims) table.append(String.format("            \"%s\" to %.1ff,%n", id, db))
        table.append("        )\n")
        val dir = File("../artifacts").apply { mkdirs() }
        File(dir, "level-trims.txt").writeText(rows.toString() + table.toString())
        print(rows)
        print(table)
    }
}
