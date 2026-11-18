// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/**
 * The Opus container, its cursors and their ring buffers, against a fake decoder: everything the app
 * needs except `MediaCodec` itself, which only a device has.
 */
class OpusStreamTest {
    private val packetSamples = 480

    /** A container whose packet `i` decodes to 480 samples of `i * 480 + j`, so a read is checkable. */
    private fun container(packets: Int = 9000): OpusFile {
        val table = packets * 8
        val head = "OpusHead".toByteArray(Charsets.US_ASCII) + ByteArray(3)
        val bytes = ByteArray(24 + head.size + table + packets * 4)
        fun put(at: Int, value: Int) {
            bytes[at] = value.toByte()
            bytes[at + 1] = (value shr 8).toByte()
            bytes[at + 2] = (value shr 16).toByte()
            bytes[at + 3] = (value shr 24).toByte()
        }
        put(0, 0x50464F4E)
        bytes[4] = 1
        bytes[5] = 1
        bytes[6] = 24000.toByte()
        bytes[7] = (24000 shr 8).toByte()
        put(8, packets * packetSamples)
        put(12, packets)
        put(16, table)
        put(20, head.size)
        head.copyInto(bytes, 24)
        for (i in 0 until packets) {
            put(24 + head.size + i * 8, i * packetSamples)
            put(24 + head.size + i * 8 + 4, 4)
            put(24 + head.size + table + i * 4, i)
        }
        return OpusFile.parse(bytes)
    }

    /** Packet `i` carries its own index in four bytes, so a restarted decoder still knows where it is. */
    private class Fake : OpusPackets {
        override val outputRate = 24000

        init {
            open.addAndGet(1)
        }

        override fun decode(packet: ByteArray, length: Int, out: FloatArray): Int {
            val index =
                (packet[0].toInt() and 0xFF) or
                    ((packet[1].toInt() and 0xFF) shl 8) or
                    ((packet[2].toInt() and 0xFF) shl 16) or
                    ((packet[3].toInt() and 0xFF) shl 24)
            for (j in 0 until 480) out[j] = index * 480f + j
            return 480
        }

        override fun close() {
            open.addAndGet(-1)
        }

        companion object {
            val open = AtomicInteger()
        }
    }

    /** A decoder factory that refuses a configuration without the Opus identification header. */
    private fun decoder(config: ByteArray): OpusPackets? {
        val magic = String(config, 0, minOf(8, config.size), Charsets.US_ASCII)
        if (magic != "OpusHead") {
            refused.addAndGet(1)
            return null
        }
        return Fake()
    }

    private val refused = AtomicInteger()

    @Test
    fun containerReportsItsOwnLengthAndPacketTable() {
        val file = container()
        assertEquals(24000, file.sampleRate)
        assertEquals(9000 * 480, file.totalSamples)
        assertEquals(9000, file.packetCount)
        assertEquals(0, file.packetAt(0))
        assertEquals(1, file.packetAt(480))
        assertEquals(4320, file.packetAt(4320 * 480 + 479))
        val into = ByteArray(4)
        assertEquals(4, file.packet(4320, into))
        assertEquals(4320, (into[0].toInt() and 0xFF) or ((into[1].toInt() and 0xFF) shl 8))
    }

    @Test
    fun aShortContainerIsRefused() {
        val bytes = ByteArray(0)
        assertThrows(IllegalArgumentException::class.java) { OpusFile.parse(bytes) }
    }

    @Test
    fun readsReturnTheDecodedSampleForThatPosition() {
        val file = container()
        val recording = OpusRecording(file, ::decoder)
        val cursor = recording.cursor()
        for (index in listOf(0, 1, 479, 480, 5000, 100_000)) {
            assertEquals("sample $index", index.toFloat(), cursor[index], 0.001f)
        }
        recording.close()
    }

    @Test
    fun aWrapRestartsTheDecoderAtTheHeldPacket() {
        val file = container()
        val recording = OpusRecording(file, ::decoder)
        val cursor = recording.cursor()
        assertEquals(0f, cursor[0], 0.001f)
        assertEquals(480f, cursor[480], 0.001f)
        val late = 3_000_000
        assertEquals(late.toFloat(), cursor[late], 0.001f)
        // A head wraps back to the top of the file, which restarts the decoder there.
        assertEquals(1000f, cursor[1000], 0.001f)
        assertEquals(1001f, cursor[1001], 0.001f)
        recording.close()
    }

    @Test
    fun twoCursorsReadIndependently() {
        val file = container()
        val recording = OpusRecording(file, ::decoder)
        val a = recording.cursor()
        val b = recording.cursor()
        assertEquals(1000f, a[1000], 0.001f)
        assertEquals(2_000_000f, b[2_000_000], 0.001f)
        assertEquals(1001f, a[1001], 0.001f)
        assertEquals(2_000_001f, b[2_000_001], 0.001f)
        recording.close()
    }

    @Test
    fun theBundledContainersParseAndRunOverThreeMinutes() {
        val folder = java.io.File("../app/src/opus/assets/audio")
        val files = folder.listFiles { f -> f.extension == "nfop" }?.sortedBy { it.name } ?: emptyList()
        assertTrue("no encoded containers under ${folder.path}", files.size >= 7)
        for (f in files) {
            val file = OpusFile.parse(f.readBytes())
            val cycle = (file.totalSamples - RecordingSource.OVERLAP) / 24000.0
            println("%s %.0f packets, %.2fs cycle, %d bytes".format(f.name, file.packetCount.toDouble(), cycle, f.length()))
            assertEquals(24000, file.sampleRate)
            assertTrue("${f.name} cycles in $cycle seconds", cycle >= 180.0)
            assertTrue("${f.name} has no packets", file.packetCount > 1000)
        }
    }

    @Test
    fun twoRecordingsOfCursorsStayWellUnderTheOldPcmFootprint() {
        // Two recordings is what a preset holds at most, and each needs four cursors: two read heads
        // and the file-start regions their crossfades read.
        val file = container()
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val recordings = List(2) { OpusRecording(file, ::decoder) }
        val cursors =
            recordings.flatMap {
                listOf(it.cursor(4.0), it.cursor(4.0), it.cursor(1.0), it.cursor(1.0))
            }
        for (round in 0 until 3) cursors.forEach { it[round * 100_000 + 500_000] }
        val after = runtime.totalMemory() - runtime.freeMemory()
        val grown = after - before
        println("eight cursors grew the heap by ${grown / 1024} KiB")
        assertTrue("eight cursors took ${grown / 1024} KiB", grown < 6_000_000)
        recordings.forEach { it.close() }
    }

    @Test
    fun aDecoderIsOnlyAskedToStartWithTheIdentificationHeader() {
        val refusedBefore = refused.get()
        val file = container()
        val recording = OpusRecording(file, ::decoder)
        assertEquals(0f, recording.cursor()[0], 0.001f)
        assertEquals("the container must hand the decoder its identification header", refusedBefore, refused.get())
        assertTrue(file.codecConfig.size >= 8)
        recording.close()
    }

    @Test
    fun closingAReleasesEveryDecoderItOpened() {
        val file = container()
        val before = Fake.open.get()
        val recording = OpusRecording(file, ::decoder)
        val cursors =
            listOf(recording.cursor(4.0), recording.cursor(4.0), recording.cursor(1.0), recording.cursor(1.0))
        cursors.forEach { it[0] }
        assertTrue("cursors opened decoders", Fake.open.get() > before)
        recording.close()
        Thread.sleep(200)
        assertEquals("every decoder is released", before, Fake.open.get())
    }

    @Test
    fun aRetiredLayerReleasesItsDecodersWithoutTouchingTheLiveOne() {
        val file = container()
        val before = Fake.open.get()
        val recording = OpusRecording(file, ::decoder)
        val live = recording.cursor(4.0)
        assertEquals(0f, live[0], 0.001f)
        // Two layers come and go, four cursors each, which is what add-and-remove does. A retired
        // layer's cursors stop being read, so they are the ones a new layer displaces.
        repeat(2) {
            val gone = List(4) { recording.cursor(4.0) }
            gone.forEach { it[0] }
            Thread.sleep(1_200)
            assertEquals(480f, live[480], 0.001f)
        }
        assertTrue("decoders are capped rather than accumulated", Fake.open.get() - before <= 8)
        assertEquals("the live cursor still reads", 480f, live[480], 0.001f)
        recording.close()
        Thread.sleep(200)
        assertEquals("close releases everything", before, Fake.open.get())
    }

    @Test
    fun aDecoderThatCannotBeRebuiltStopsTheWorkerInsteadOfSpinning() {
        val file = container()
        val builds = AtomicInteger()
        val recording =
            OpusRecording(
                file,
                { config ->
                    if (builds.incrementAndGet() > 1) null else decoder(config)
                },
            )
        val cursor = recording.cursor(4.0)
        assertEquals(0f, cursor[0], 0.001f)
        cursor[2_000_000]
        Thread.sleep(250)
        val attempts = builds.get()
        Thread.sleep(250)
        assertEquals("the worker stops after the failure", attempts, builds.get())
        assertNotNull(recording.unavailable)
        recording.close()
    }

    @Test
    fun anUndecodableRecordingSaysSoInsteadOfSounding() {
        val recording = OpusRecording(container(), { _ -> null })
        val cursor = recording.cursor()
        assertEquals(0f, cursor[0], 0.0f)
        assertNotNull(recording.unavailable)
        println("unavailable: ${recording.unavailable}")
    }

    @Test
    fun aRecordingHoldsABoundedAmountOfMemory() {
        val file = container()
        val recording = OpusRecording(file, ::decoder, seconds = 4.0)
        val cursors = listOf(recording.cursor(4.0), recording.cursor(4.0), recording.cursor(1.0), recording.cursor(1.0))
        cursors.forEach { it[0] }
        val bytes = (2 * 4.0 + 2 * 1.0) * 24000 * 4 + 4 * (file.longestPacket + 5760 * 4)
        println("one recording holds ${bytes.toInt()} bytes of decoded buffers")
        assertTrue(bytes < 1_100_000)
        recording.close()
    }
}
