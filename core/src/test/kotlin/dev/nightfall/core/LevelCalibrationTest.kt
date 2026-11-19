// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The trim table in [Levels] and the measurement that produces it, in one place.
 *
 * Every catalogue entry is rendered alone at layer gain 1.0 and master 0.25 - twelve dB below full
 * scale, where no source reaches the limiter's knee - and its gated integrated loudness is measured.
 * At full master the entry must measure [Levels.REFERENCE_LUFS]; how far it misses that is the drift
 * of the trim the table stores for it.
 *
 * The same measurement is both the generator and its check. [printTrims] prints the table when asked
 * for it; [everyStoredTrimMatchesAFreshMeasurement] runs by default and fails when the table has
 * drifted from a fresh measurement, when a catalogue entry has no trim, or when a trim names an entry
 * the catalogue no longer has. A source's level moves with its constants, so a trim measured once and
 * never re-checked is a promise nothing keeps.
 *
 * Long renders on purpose: thunder events are minutes apart and music level wanders, so a short probe
 * would calibrate against a lucky stretch. The renders are deterministic - the layer id seeds every
 * source - so a re-measurement of the shipped table reproduces it rather than sampling it.
 *
 *     NIGHTFALL_CALIBRATE=1 ./gradlew :core:test --tests '*LevelCalibrationTest*'
 */
class LevelCalibrationTest {

    /** One catalogue entry measured at the trim the table currently stores for it. */
    private class Measured(val sound: Sound, val lufs: Double, val peak: Double) {
        /** How far the stored trim leaves this entry from the reference, in dB. */
        val drift: Double get() = Levels.REFERENCE_LUFS - lufs

        /** The trim this measurement asks for, for the printed table. */
        val corrected: Float get() = (Levels.trimDb(sound) + drift).toFloat()

        val note: String
            get() =
                if (peak + 12 + corrected > 20 * log10(PcmMixer.LIMIT_KNEE.toDouble()))
                    "crest %.1f dB, peaks into the limiter".format(peak + 12 - lufs)
                else ""
    }

    private val loader = M.assetLoader()

    private fun secondsFor(s: Sound) =
        when (s.kind) {
            "thunder" -> 90.0
            "music" -> 45.0
            "recording" -> 30.0
            "clock" -> 45.0
            else -> 20.0
        }

    private fun measure(s: Sound): Measured {
        val layer = Library.layer(s.id, 1f, "cal-${s.id}")
        val signal = M.render(Mix(layers = listOf(layer), master = .25f), secondsFor(s), loader)
        // 12 dB below full master, where nothing reaches the limiter: a storm's transients would
        // otherwise enter it and their attenuation would be folded into the correction.
        return Measured(s, M.integratedLufs(signal) + 12.0, M.truePeakDb(signal))
    }

    @Test
    fun printTrims() {
        assumeTrue(System.getenv("NIGHTFALL_CALIBRATE") == "1")
        val rows = StringBuilder()
        rows.append(
            String.format("%-40s %7s %7s %8s %8s %8s %s%n", "id", "LUFS", "dBTP", "loudLvl", "peakCap", "trim", "note")
        )
        val trims = LinkedHashMap<String, Float>()
        for (s in Library.all.filter { it.available }) {
            val m = measure(s)
            // The render already applied the current table; loudness tracks the trim 1:1 below the
            // limiter, so one pass converges instead of compounding.
            trims[s.id] = m.corrected
            rows.append(
                String.format(
                    "%-40s %7.1f %7.1f %8.1f %8.1f %8.1f %s%n",
                    s.id,
                    m.lufs,
                    m.peak,
                    m.drift,
                    m.peak + 12,
                    m.corrected,
                    m.note,
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

    /**
     * The guard. Re-measures the whole catalogue and fails on any trim the sources have moved away
     * from, anywhere the table and the catalogue disagree, and reports the corrected table in the
     * message so a drift is a paste rather than an investigation.
     */
    @Test
    fun everyStoredTrimMatchesAFreshMeasurement() {
        val entries = Library.all.filter { it.available }
        val started = System.nanoTime()
        val measured = entries.map { measure(it) }
        val seconds = (System.nanoTime() - started) / 1e9

        val stale = measured.filter { abs(it.drift) > TRIM_TOLERANCE_DB }
        val missing = entries.map { it.id }.filter { it !in Levels.tableIds() }
        val phantom = Levels.tableIds().filter { id -> entries.none { it.id == id } }.sorted()
        val worst = measured.maxByOrNull { abs(it.drift) }!!

        println(
            String.format(
                "level trims: %d entries re-measured in %.1f s, worst drift %+.2f dB (%s), tolerance %.3f dB",
                measured.size,
                seconds,
                worst.drift,
                worst.sound.id,
                TRIM_TOLERANCE_DB,
            )
        )

        val problems = StringBuilder()
        if (stale.isNotEmpty()) {
            problems.append("stored trims a fresh measurement disagrees with:\n")
            for (m in stale.sortedBy { -abs(it.drift) })
                problems.append(
                    "  %-40s stored %6.1f, measures %+.2f dB off the reference, ask for %6.1f\n".format(
                        m.sound.id,
                        Levels.trimDb(m.sound),
                        m.drift,
                        m.corrected,
                    )
                )
            problems.append("re-run with NIGHTFALL_CALIBRATE=1 to print the table that matches.\n")
        }
        if (missing.isNotEmpty()) problems.append("catalogue entries with no trimmed level: $missing\n")
        if (phantom.isNotEmpty()) problems.append("trims for entries the catalogue no longer has: $phantom\n")
        assertEquals(problems.toString(), "", problems.toString())
    }

    companion object {
        /**
         * How far a stored trim may sit from a fresh measurement, in dB.
         *
         * Measured, not guessed. The table stores one decimal, so a correctly regenerated row can sit
         * up to 0.050 dB from the reference; re-measuring the shipped table puts the worst of the 105
         * at 0.0497 dB, and the same measurement has reproduced to 0.000 dB across runs. 0.055 is 10%
         * above that rounding floor, which is room for a re-measurement that has shown no spread at
         * all, not room for a source to move.
         *
         * The other side is the nine wind and winter rows this catalogue shipped with, which had
         * drifted 0.053 to 0.39 dB. Eight of them sit above 0.06, so this fails them with 0.0626 dB
         * the closest to the line. The ninth, wind-through-pine-trees at 0.053 dB, is inside the
         * table's own quantization: no threshold that all 105 honest rows pass can see a drift that
         * small, which is a limit of storing one decimal rather than of this check.
         */
        private const val TRIM_TOLERANCE_DB = 0.055
    }
}
