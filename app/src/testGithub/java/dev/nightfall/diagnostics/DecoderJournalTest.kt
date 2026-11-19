// SPDX-License-Identifier: MIT
package dev.nightfall.diagnostics

import dev.nightfall.OpusDecodeFailure
import dev.nightfall.OpusFailures
import dev.nightfall.openCodec
import dev.nightfall.openOpusDecoder
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
 * Decoder failures in the journal, measured on the file.
 *
 * The entries here are produced the way production produces them — a fake platform refusing one of
 * the decoder's three calls, reported through [OpusFailures] into [DecoderLog] — rather than by
 * constructing `JournalEvent`s by hand, so the payload under test is the payload a phone would write.
 */
class DecoderJournalTest {
    private lateinit var folder: File
    private lateinit var file: File
    private val day = LocalDate.parse("2026-11-19")
    private val header = ByteArray(19) { 1 }
    private val tokens = OpusDecodeFailure.entries.map { it.token }.toSet()

    @Before
    fun freshFolder() {
        folder = Files.createTempDirectory("nightfall-decoders").toFile()
        file = File(folder, "diagnostics/journal.json")
    }

    @After
    fun removeFolder() {
        folder.deleteRecursively()
    }

    private fun journal(enabled: () -> Boolean) =
        DiagnosticsJournal(
            store = JournalStore(file) { day },
            enabled = enabled,
            runOnIo = { work -> work.run() },
        )

    /** One attempt whose fake platform refuses at [step], with [message] as the platform's words. */
    private fun failAt(step: OpusDecodeFailure, message: String) {
        runCatching {
            openOpusDecoder(header) { _ ->
                openCodec(
                    create = { if (step == OpusDecodeFailure.NO_DECODER) error(message) else Unit },
                    configure = { if (step == OpusDecodeFailure.CONFIGURE) error(message) },
                    start = { if (step == OpusDecodeFailure.START) error(message) },
                    release = {},
                )
            }
        }
    }

    private fun refuseHeader(message: String) {
        runCatching {
            openOpusDecoder(header) { offered ->
                if (offered != null) error(message)
                "without-header"
            }
        }
    }

    private fun details(journal: DiagnosticsJournal) = journal.entries().map { it.detail }

    @Test
    fun everyRefusedStepIsWrittenWhileCollectionIsOn() {
        val journal = journal { true }
        val log = DecoderLog(journal)
        OpusFailures.listen { log.failure(it, true) }.use {
            failAt(OpusDecodeFailure.NO_DECODER, "no codec")
            failAt(OpusDecodeFailure.CONFIGURE, "csd-0 refused")
            failAt(OpusDecodeFailure.START, "start refused")
            refuseHeader("csd-0 refused at start")
        }

        assertEquals(
            "a refused step left no entry, or the entries do not name the steps",
            listOf("no_decoder", "configure", "start", "header_rejected"),
            details(journal),
        )
        assertTrue(
            "a decoder entry was written under another kind",
            journal.entries().all { it.kind == Journal.DECODER },
        )
        assertTrue("the journal was not written to disk", file.exists())
    }

    @Test
    fun aFailureWhileCollectionIsOffIsNotWrittenAndNotBackFilled() {
        var on = false
        val journal = journal { on }
        val log = DecoderLog(journal)
        OpusFailures.listen { log.failure(it, on) }.use {
            failAt(OpusDecodeFailure.CONFIGURE, "csd-0 refused")

            assertFalse("an entry was written while collection was off", file.exists())
            assertEquals("an entry was held while collection was off", 0, journal.entries().size)

            on = true

            assertEquals(
                "a failure from before the switch was on was back-filled",
                emptyList<String>(),
                details(journal),
            )

            failAt(OpusDecodeFailure.CONFIGURE, "csd-0 refused")

            assertEquals(
                "the same step refused again while collection was on was not written",
                listOf("configure"),
                details(journal),
            )
        }
    }

    @Test
    fun oneEntryPerStepHoweverOftenTheDecoderRestarts() {
        val journal = journal { true }
        val log = DecoderLog(journal)
        OpusFailures.listen { log.failure(it, true) }.use {
            repeat(5) { failAt(OpusDecodeFailure.START, "start refused") }
        }

        assertEquals(
            "a read head rebuilding its decoder filled the journal with copies of one entry",
            listOf("start"),
            details(journal),
        )
    }

    @Test
    fun thePayloadIsAClosedTokenAndThePlatformsWordsAreNowhere() {
        val journal = journal { true }
        val log = DecoderLog(journal)
        OpusFailures.listen { log.failure(it, true) }.use {
            failAt(
                OpusDecodeFailure.CONFIGURE,
                "csd-0 refused for /data/user/0/io.github.camjampng.nightfall/files/rain_soft.nfop",
            )
        }

        val written = file.readText()
        assertFalse("the platform's message reached the journal", written.contains("refused"))
        assertFalse("a file path reached the journal", written.contains(".nfop"))
        assertFalse("an exception reached the journal", written.contains("IllegalState"))

        for (event in journal.entries()) {
            assertTrue(
                "a decoder entry holds a detail that is not one of the closed tokens",
                event.detail in tokens,
            )
            assertTrue(
                "a decoder entry holds a value outside the entry's own fields",
                event.bucket == null,
            )
            assertEquals(
                "a decoder entry is dated with something other than the local date",
                day.toString(),
                event.date,
            )
        }

        val report = journal.report(AppVersion("2.1.2", 36, 35))
        assertFalse("the report a person shares carries the platform's words", report.contains("csd-0"))
        assertTrue("the report does not name the decoder kind", report.contains("decoder"))
    }
}
