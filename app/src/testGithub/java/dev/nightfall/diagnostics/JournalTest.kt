// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The journal's three promises, measured on the file itself: nothing is held before the switch is on,
 * the caps drop the oldest entries first, and erasing leaves nothing behind.
 *
 * These run against a real directory and a real file, with the write handed back as a direct call, so
 * what they assert is what a phone's private storage would hold rather than the state of a fake.
 */
class JournalTest {
    private lateinit var folder: File
    private lateinit var file: File
    private val day = LocalDate.parse("2026-11-19")

    @Before
    fun freshFolder() {
        folder = Files.createTempDirectory("nightfall-journal").toFile()
        file = File(folder, "diagnostics/journal.json")
    }

    @After
    fun removeFolder() {
        folder.deleteRecursively()
    }

    private fun journal(
        limits: JournalLimits = JournalLimits(),
        enabled: () -> Boolean = { true },
    ) =
        DiagnosticsJournal(
            store = JournalStore(file, limits) { day },
            enabled = enabled,
            runOnIo = { work -> work.run() },
        )

    @Test
    fun nothingIsRecordedUntilTheSwitchIsOn() {
        var on = false
        val journal = journal(enabled = { on })

        journal.record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "1to2s"))

        assertFalse("an entry was written while collection was off", file.exists())
        assertEquals("an entry was held while collection was off", 0, journal.entries().size)

        on = true
        journal.record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "1to2s"))

        assertTrue("nothing was written after collection was turned on", file.exists())
        assertEquals(1, journal.entries().size)
    }

    @Test
    fun theEntryCapDropsTheOldestFirst() {
        val journal = journal(JournalLimits(entries = 3, bytes = 64L * 1024, days = 30))
        for (hour in 1..5)
            journal.record(JournalEvent(Journal.UPDATE, day.toString(), detail = "check$hour"))

        assertEquals(
            "the cap kept the wrong entries",
            listOf("check3", "check4", "check5"),
            journal.entries().map { it.detail },
        )
        assertEquals("the file holds more than the cap allows", 3, journal.entries().size)
    }

    @Test
    fun theByteCapDropsTheOldestFirst() {
        val limits = JournalLimits(entries = 500, bytes = 400, days = 30)
        val journal = journal(limits)
        repeat(60) { index ->
            journal.record(JournalEvent(Journal.PLAYBACK, day.toString(), bucket = "2to8h$index"))
        }

        val held = journal.entries()
        assertTrue("the byte cap did not drop anything", held.size < 60)
        assertTrue(
            "the journal holds more bytes than the cap allows",
            JournalStore(file, limits) { day }.encodedBytes(held) <= limits.bytes,
        )
        assertEquals("the newest entry was dropped", "2to8h59", held.last().bucket)
        assertEquals(
            "the entries kept are not the newest ones, in order",
            (60 - held.size until 60).map { "2to8h$it" },
            held.map { it.bucket },
        )
    }

    @Test
    fun theAgeCapDropsWholeDays() {
        val journal = journal(JournalLimits(entries = 200, bytes = 64L * 1024, days = 30))
        journal.record(JournalEvent(Journal.EXIT, day.minusDays(40).toString(), detail = "crash"))
        journal.record(JournalEvent(Journal.EXIT, day.toString(), detail = "anr"))

        assertEquals(
            "an entry older than the age cap survived",
            listOf("anr"),
            journal.entries().map { it.detail },
        )
    }

    @Test
    fun erasingRemovesTheTemporaryFileAWriteGoesThrough() {
        val journal = journal()
        journal.record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "lt1s"))
        // What an interrupted write leaves behind: the completed journal, under the write's own name.
        val interrupted = File(file.parentFile, file.name + ".writing")
        interrupted.writeText("{ \"schema\": 1, \"events\": [] }")

        assertTrue("erasing reported a failure on a writable folder", journal.erase())

        assertFalse("the journal file survived an erase", file.exists())
        assertFalse("an interrupted write left the journal on disk after an erase", interrupted.exists())
    }

    @Test
    fun aFailedEraseIsReportedRatherThanCalledSuccess() {
        val journal = journal()
        journal.record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "lt1s"))
        // A directory where the journal file is: delete fails, which is the case the surface must not
        // report as "nothing is left on this device".
        file.delete()
        file.mkdirs()
        File(file, "undeletable").writeText("x")

        assertFalse("a failed erase was reported as a success", journal.erase())
    }

    @Test
    fun entriesSurviveANewProcess() {
        journal().record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "lt1s"))

        val reopened = journal()

        assertEquals(listOf("lt1s"), reopened.entries().map { it.bucket })
    }

    @Test
    fun erasingLeavesNothing() {
        val journal = journal()
        repeat(4) { journal.record(JournalEvent(Journal.FAILURE, day.toString(), detail = "playback")) }
        assertTrue(file.exists())

        journal.erase()

        assertFalse("the journal file survived an erase", file.exists())
        assertEquals("an erased journal still held entries", 0, journal.entries().size)
        val report = journal.report(AppVersion("2.0", 40, 36))
        assertTrue("the report still lists events: $report", report.contains("\"entries\": 0"))
        assertFalse("the report still lists an event: $report", report.contains("\"kind\""))
    }

    @Test
    fun theReportCarriesTheCapsAndTheCounts() {
        val journal = journal(JournalLimits(entries = 200, bytes = 64L * 1024, days = 30))
        journal.record(JournalEvent(Journal.STARTUP, day.toString(), bucket = "lt1s"))
        journal.record(JournalEvent(Journal.PLAYBACK, day.toString(), bucket = "2to8h"))
        journal.record(JournalEvent(Journal.PLAYBACK, day.toString(), bucket = "lt5m"))

        val report = journal.report(AppVersion("2.0", 40, 36))

        assertTrue(report.contains("\"versionName\": \"2.0\""))
        assertTrue(report.contains("\"androidSdk\": 36"))
        assertTrue(report.contains("\"playback\": 2"))
        assertTrue(report.contains("\"days\": 30"))
        assertTrue(report.contains("\"first\": \"2026-11-19\""))
    }
}
