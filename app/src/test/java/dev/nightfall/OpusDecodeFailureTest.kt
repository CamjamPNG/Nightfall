// SPDX-License-Identifier: MIT
package dev.nightfall

import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowMediaCodec

/**
 * Every token the journal can hold for the Opus decoder, proven reachable by a decoder that fails at
 * that exact point.
 *
 * `MediaCodecOpus` has never run against a platform decoder — the binding is the unverified half of
 * 2.0 — so this file cannot show that a real device fails at any of these steps. What it can show is
 * that each token names a branch the code actually takes: a fake platform that refuses `configure`
 * produces `configure` and not `start`, a started decoder that emits nothing produces
 * `first_output_timeout`, and a device with no codec at all produces `no_decoder`.
 */
@RunWith(AndroidJUnit4::class)
class OpusDecodeFailureTest {
    private val header = ByteArray(19) { 1 }
    private val reported = ArrayList<OpusDecodeFailure>()
    private lateinit var listener: AutoCloseable

    @Before
    fun listen() {
        reported.clear()
        listener = OpusFailures.listen { failure -> reported += failure }
    }

    @After
    fun stop() {
        listener.close()
    }

    /** Runs one decoder attempt whose fake platform fails at [step], and returns what was reported. */
    private fun refusalAt(step: OpusDecodeFailure): List<OpusDecodeFailure> {
        reported.clear()
        runCatching {
            openOpusDecoder(header) { _ ->
                openCodec(
                    create = {
                        if (step == OpusDecodeFailure.NO_DECODER) error("no codec for audio/opus")
                        "codec"
                    },
                    configure = { if (step == OpusDecodeFailure.CONFIGURE) error("csd-0 refused") },
                    start = { if (step == OpusDecodeFailure.START) error("start refused") },
                    release = {},
                )
            }
        }
        return reported.toList()
    }

    @Test
    fun aDeviceWithNoOpusDecoderIsItsOwnToken() {
        assertEquals(listOf(OpusDecodeFailure.NO_DECODER), refusalAt(OpusDecodeFailure.NO_DECODER))
    }

    @Test
    fun aRefusedFormatIsItsOwnToken() {
        assertEquals(listOf(OpusDecodeFailure.CONFIGURE), refusalAt(OpusDecodeFailure.CONFIGURE))
    }

    @Test
    fun aRefusedStartIsItsOwnToken() {
        assertEquals(listOf(OpusDecodeFailure.START), refusalAt(OpusDecodeFailure.START))
    }

    @Test
    fun aHeaderTheFallbackCoversIsReportedAsANamedStep() {
        reported.clear()

        val result =
            openOpusDecoder(header) { offered ->
                if (offered != null) error("csd-0 refused")
                "without-header"
            }

        assertEquals("without-header", result)
        assertEquals(
            "the header being refused is a distinguishable branch and was not reported",
            listOf(OpusDecodeFailure.HEADER_REJECTED),
            reported.toList(),
        )
    }

    @Test
    fun aRefusalFromNoneOfThoseCallsIsNotGuessed() {
        reported.clear()

        runCatching { openOpusDecoder(header) { error("something else entirely") } }

        assertEquals(
            "a refusal outside the three calls was reported as a step it was not",
            listOf(OpusDecodeFailure.OTHER),
            reported.toList(),
        )
    }

    /** The real `MediaCodecOpus`, with only the platform beneath it replaced. */
    @Test
    fun aStartedDecoderThatProducesNothingIsItsOwnToken() {
        ShadowMediaCodec.addDecoder(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            ShadowMediaCodec.CodecConfig(
                1500,
                0x15,
                object : ShadowMediaCodec.CodecConfig.Codec {
                    override fun process(input: ByteBuffer, output: ByteBuffer) {}
                },
            ),
        )
        reported.clear()

        val codec = MediaCodecOpus(header)
        try {
            val frames = codec.decode(ByteArray(40) { 1 }, 40, FloatArray(5760))
            assertEquals("a silent decoder reported frames it did not produce", 0, frames)
        } finally {
            codec.close()
        }

        assertEquals(
            "the first output timing out was not reported",
            listOf(OpusDecodeFailure.FIRST_OUTPUT_TIMEOUT),
            reported.toList(),
        )
    }

    @Test
    fun aWorkingDecoderReportsNothing() {
        ShadowMediaCodec.addDecoder(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            ShadowMediaCodec.CodecConfig(
                1500,
                0x15,
                object : ShadowMediaCodec.CodecConfig.Codec {
                    override fun process(input: ByteBuffer, output: ByteBuffer) {
                        output.putShort(0)
                    }
                },
            ),
        )
        reported.clear()

        MediaCodecOpus(header).close()

        assertNull("a decoder that built and closed cleanly was reported as a failure", reported.firstOrNull())
    }
}
