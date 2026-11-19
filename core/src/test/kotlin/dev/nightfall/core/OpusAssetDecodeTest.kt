// SPDX-License-Identifier: MIT
package dev.nightfall.core

import io.github.jaredmdobson.concentus.OpusDecoder
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every bundled container, decoded by a conformant Opus decoder that is not ours.
 *
 * `OpusStreamTest` runs our container, cursors and ring buffers against a fake decoder, so it can
 * never catch a container whose bytes are not real Opus: both halves would agree with each other and
 * disagree with every phone. This test closes that gap from the other side. It reads the NFOP layout
 * straight from the file rather than through [OpusFile], so a parser bug cannot hide a payload bug,
 * and hands each packet to Concentus, a pure-Java port of the reference `libopus` decoder, in test
 * scope only so no APK carries it.
 *
 * What this proves: the container layout, the packet table, the identification header and the
 * payload all decode to the audio we think they hold, by a decoder written by someone else. What it
 * does not prove: that Android's `MediaCodec` binding feeds that header and those packets correctly
 * on a device. Only a device can say that, and no such run exists.
 */
class OpusAssetDecodeTest {
    /**
     * The container, read independently of [OpusFile]: magic, version, channel count, sample rate,
     * total samples, packet count, table length, codec configuration length, then the layout
     * `dev.nightfall.core.OpusFile` documents.
     */
    private class Raw(bytes: ByteArray) {
        val sampleRate: Int
        val totalSamples: Int
        val codecConfig: ByteArray
        val packets: List<ByteArray>

        init {
            require(bytes.size >= 24) { "not an NFOP file" }
            // The format is called NFOP; its four magic bytes read "NOFP" on disk, being the
            // little-endian integer the encoder writes and `OpusFile.parse` compares against.
            require(u32(bytes, 0) == 0x50464F4E) { "not an NFOP file" }
            require(bytes[4].toInt() == 1) { "NFOP version ${bytes[4]}" }
            require(bytes[5].toInt() == 1) { "NFOP channel count ${bytes[5]}" }
            sampleRate = u16(bytes, 6)
            totalSamples = u32(bytes, 8)
            val packetCount = u32(bytes, 12)
            val tableBytes = u32(bytes, 16)
            val configBytes = u32(bytes, 20)
            require(packetCount > 0) { "no packets" }
            require(24 + configBytes + tableBytes <= bytes.size) { "truncated NFOP header" }
            codecConfig = bytes.copyOfRange(24, 24 + configBytes)
            val table = 24 + configBytes
            var offset = table + tableBytes
            packets = ArrayList(packetCount)
            for (i in 0 until packetCount) {
                val length = u32(bytes, table + i * 8 + 4)
                require(offset + length <= bytes.size) { "truncated NFOP packets" }
                packets += bytes.copyOfRange(offset, offset + length)
                offset += length
            }
        }

        private fun u16(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xFF) or
                ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or
                ((b[at + 3].toInt() and 0xFF) shl 24)
    }

    private val folder = File("../app/src/opus/assets/audio")

    private fun containers() =
        folder.listFiles { f -> f.extension == "nfop" }?.sortedBy { it.name } ?: emptyList()

    @Test
    fun everyBundledContainerDecodesToTheAudioItsHeaderClaims() {
        val files = containers()
        assertEquals("the catalogue bundles seven field recordings", 7, files.size)
        for (file in files) {
            val raw = Raw(file.readBytes())
            assertEquals("${file.name} sample rate", 24000, raw.sampleRate)

            // The identification header is what the platform decoder needs as `csd-0`; a container
            // that dropped it would still look plausible in our own tests.
            assertEquals("${file.name} codec configuration", "OpusHead", String(raw.codecConfig, 0, 8, Charsets.US_ASCII))
            assertEquals("${file.name} OpusHead version", 1, raw.codecConfig[8].toInt())
            assertEquals("${file.name} OpusHead channels", 1, raw.codecConfig[9].toInt())
            assertEquals("${file.name} OpusHead input rate", 24000, u32le(raw.codecConfig, 12))

            val decoder = OpusDecoder(raw.sampleRate, 1)
            var decoded = 0
            var peak = 0
            var sumSquares = 0.0
            raw.packets.forEachIndexed { index, packet ->
                // 5760 is the longest frame Opus emits (120 ms at 48 kHz); the decoder needs room for
                // whatever the packet's own table of contents announces.
                val out = ShortArray(5760)
                val frames = decoder.decode(packet, 0, packet.size, out, 0, 5760, false)
                assertTrue("${file.name} packet $index decoded to $frames frames", frames > 0)
                for (i in 0 until frames) {
                    val value = out[i].toInt()
                    sumSquares += value.toDouble() * value
                    if (abs(value) > peak) peak = abs(value)
                }
                decoded += frames
            }

            // One 20 ms packet at the container's rate. Decoded totals match the header exactly
            // today; the tolerance is for a decoder that trims the last frame differently, not for a
            // container that disagrees with itself.
            val packet = raw.sampleRate / 50
            assertTrue(
                "${file.name} declares ${raw.totalSamples} samples but decodes to $decoded",
                abs(decoded - raw.totalSamples) <= packet,
            )
            assertTrue(
                "${file.name} cycles in ${decoded.toDouble() / raw.sampleRate} seconds",
                decoded / raw.sampleRate >= 180,
            )

            val rms = sqrt(sumSquares / decoded)
            val dbfs = 20 * log10(rms / 32768.0)
            println(
                "%s %d packets, %.2fs, rms %.1f (%.1f dBFS), peak %.3f".format(
                    file.name,
                    raw.packets.size,
                    decoded.toDouble() / raw.sampleRate,
                    rms,
                    dbfs,
                    peak / 32768.0,
                )
            )
            // Measured across the seven recordings: RMS -46.0 to -22.8 dBFS, peak 0.409 to 0.571.
            // These floors sit four decibels and four times below the quietest recording, so a
            // decoder returning silence, or noise at the wrong level, fails rather than passes.
            assertTrue("${file.name} is silent: rms $rms", dbfs >= -50.0)
            assertTrue("${file.name} peaks at ${peak / 32768.0}", peak >= 3277 && peak <= 32768)
        }
    }

    @Test
    fun ourParserAgreesWithTheContainersAnotherDecoderReads() {
        for (file in containers()) {
            val raw = Raw(file.readBytes())
            val parsed = OpusFile.parse(file.readBytes())
            assertEquals(file.name, raw.sampleRate, parsed.sampleRate)
            assertEquals(file.name, raw.totalSamples, parsed.totalSamples)
            assertEquals(file.name, raw.packets.size, parsed.packetCount)
            assertArrayEquals(file.name, raw.codecConfig, parsed.codecConfig)
            // The packet table gives every packet a start sample; a table that skipped or overlapped
            // would decode to audio the seek offsets cannot reach.
            var sample = 0
            val into = ByteArray(parsed.longestPacket)
            val out = ShortArray(5760)
            val decoder = OpusDecoder(raw.sampleRate, 1)
            raw.packets.forEachIndexed { index, packet ->
                assertEquals("${file.name} packet $index start", sample, parsed.packetStart(index))
                assertEquals("${file.name} packet $index length", packet.size, parsed.packet(index, into))
                // The frame count is what the decoder reports, not a hand-read of the table of
                // contents, so the table is checked against a real frame count.
                sample += decoder.decode(packet, 0, packet.size, out, 0, 5760, false)
            }
        }
    }

    private fun u32le(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun assertArrayEquals(name: String, expected: ByteArray, actual: ByteArray) {
        assertTrue("$name codec configuration differs", expected.contentEquals(actual))
    }
}
