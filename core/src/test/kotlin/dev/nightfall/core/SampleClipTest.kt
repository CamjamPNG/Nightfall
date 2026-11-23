// SPDX-License-Identifier: MIT
package dev.nightfall.core

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The committed listening samples still describe the engine that renders them.
 *
 * `docs/samples/` is prose about audio, and prose drifts: a synth change moves a level, an encoder
 * run is forgotten, and the README keeps promising what the files no longer hold. So this reads each
 * published file and each recipe beside it, and asserts three things a stale pair cannot satisfy —
 * the file is a real Ogg Opus stream at the mixer's own rate and channel count, its length is the
 * length the recipe asks for, and a fresh render of that recipe peaks where the published figure says
 * it does.
 *
 * The tolerance on the peak is for float ordering, not for engine changes: 0.5 dB is more than a
 * re-render of unchanged code moves and less than anything a person would notice as a different
 * sound. What this cannot check is what the clips sound like — nothing here has been listened to, and
 * decoding them is not a listening test.
 */
class SampleClipTest {
    private val folder = File("../docs/samples")

    @Test
    fun everyPublishedClipIsTheAudioItsRecipeRendersToday() {
        for (clip in Samples.all) {
            val file = File(folder, "${clip.id}.ogg")
            assertTrue("docs/samples/${clip.id}.ogg is missing", file.isFile)

            val stream = Ogg(file.readBytes())
            assertEquals("${file.name} channels", 2, stream.channels)
            assertEquals("${file.name} input rate", 48000, stream.inputRate)
            assertEquals(
                "${file.name} is ${stream.seconds} s, the recipe asks for ${clip.seconds}",
                clip.seconds,
                stream.seconds,
                0.05,
            )

            val signal = Samples.render(clip)
            val peak = Samples.peakDb(signal)
            assertEquals(
                "${file.name} was published at ${clip.peakDb} dBFS and renders at $peak: " +
                    "regenerate docs/samples with tools/render_samples.sh",
                clip.peakDb,
                peak,
                0.5,
            )

            // The two-second fade is the reason a clip neither starts mid-crackle nor stops at full
            // level, so it is asserted rather than trusted: a render that lost it is a click.
            val head = (0 until Samples.RATE / 100).maxOf { maxOf(abs(signal.left[it]), abs(signal.right[it])) }
            val tail =
                (signal.frames - Samples.RATE / 100 until signal.frames)
                    .maxOf { maxOf(abs(signal.left[it]), abs(signal.right[it])) }
            assertTrue("${file.name} does not fade in: $head at the start", head < 1e-4f)
            assertTrue("${file.name} does not fade out: $tail at the end", tail < 1e-4f)
        }
    }

    /**
     * An Ogg page walk, just enough of one: the identification packet's channel count and rate, and
     * the last page's granule position, which is the length of the stream in samples at 48 kHz.
     * Written here rather than borrowed from [OpusStream] because that reader is the app's NFOP
     * container and this file is Ogg — a bug in one cannot hide in the other.
     */
    private class Ogg(private val bytes: ByteArray) {
        var channels = 0
            private set
        var inputRate = 0
            private set

        /** Seconds of audio: the last granule position, less the decoder's pre-skip. */
        var seconds = 0.0
            private set

        init {
            require(bytes.size > 27) { "not an Ogg file" }
            require(String(bytes, 0, 4, Charsets.US_ASCII) == "OggS") { "no Ogg page" }
            // The identification packet is the first page's payload, after its header and its
            // segment table.
            val head = 27 + (bytes[26].toInt() and 0xFF)
            require(String(bytes, head, 8, Charsets.US_ASCII) == "OpusHead") {
                "first packet is not an Opus identification header"
            }
            channels = bytes[head + 9].toInt() and 0xFF
            val preSkip = u16(bytes, head + 10)
            inputRate = u32(bytes, head + 12)

            var offset = 0
            var granule = 0L
            var pages = 0
            while (offset + 27 <= bytes.size) {
                require(String(bytes, offset, 4, Charsets.US_ASCII) == "OggS") {
                    "page at $offset is not an Ogg page"
                }
                granule = maxOf(granule, i64(bytes, offset + 6))
                val segments = bytes[offset + 26].toInt() and 0xFF
                var payload = 0
                for (i in 0 until segments) payload += bytes[offset + 27 + i].toInt() and 0xFF
                offset += 27 + segments + payload
                pages++
            }
            require(pages > 1) { "a single page is not a stream" }
            seconds = (granule - preSkip).toDouble() / 48000.0
        }

        private fun u16(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, at: Int) =
            (b[at].toInt() and 0xFF) or
                ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or
                ((b[at + 3].toInt() and 0xFF) shl 24)

        private fun i64(b: ByteArray, at: Int): Long {
            var value = 0L
            for (i in 7 downTo 0) value = (value shl 8) or (b[at + i].toLong() and 0xFF)
            return value
        }
    }
}
