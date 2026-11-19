// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import dev.nightfall.core.OpusFile
import dev.nightfall.core.OpusPackets
import dev.nightfall.core.OpusRecording
import dev.nightfall.core.SampleData
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The only platform-specific half of the Opus path: packets in, mono floats out, through the decoder
 * the device already ships. Container parsing, the ring buffers and the cursors against them live in
 * `core` where a host test covers them; nothing here is exercised by a unit test, because only a
 * device has `MediaCodec`.
 */
private class MediaCodecOpus(private val config: ByteArray) : OpusPackets {
    override var outputRate = 48000
        private set

    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private var encoding = 0
    private var pending = -1
    /** The first output of a codec that has just started is the slowest one it will ever produce. */
    private var starting = true

    init {
        // Two platform behaviours have to work, and neither can be checked from here. Most decoders
        // take the Opus identification header as the codec-specific data `csd-0` and reject input
        // without it; some reject it instead. So the header is supplied first and configuration is
        // retried once without it, which leaves a decoder on both classes of device rather than a
        // recording that cannot sound on all of them.
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SOURCE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET)
            }
        val withHeader =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SOURCE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET)
                setByteBuffer("csd-0", ByteBuffer.wrap(config))
            }
        try {
            codec.configure(withHeader, null, null, 0)
        } catch (_: Exception) {
            // A codec left in its error state only accepts a reset before it will configure again.
            runCatching { codec.reset() }
            codec.configure(format, null, null, 0)
        }
        codec.start()
    }

    override fun decode(packet: ByteArray, length: Int, out: FloatArray): Int {
        if (pending >= 0) {
            codec.releaseOutputBuffer(pending, false)
            pending = -1
        }
        var wrote = 0
        // Bounded, because a decoder that accepts this packet and then produces nothing — a missing
        // initialization buffer, a packet it discards — used to leave this loop polling forever while
        // the cursor's worker waited behind it. Returning 0 hands that to the cursor, which reports a
        // recording it cannot decode instead of hanging the audio thread's helper.
        var polls = 0
        val budget = if (starting) START_POLLS else MAX_POLLS
        while (pending < 0 && polls < budget) {
            polls++
            if (wrote == 0) wrote = feed(packet, length)
            pending = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (pending == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val output = codec.outputFormat
                outputRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                encoding =
                    if (output.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else
                        android.media.AudioFormat.ENCODING_PCM_16BIT
            } else if (pending == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (wrote <= 0) return 0
            }
        }
        if (pending < 0) return 0
        starting = false
        val buffer: ByteBuffer = codec.getOutputBuffer(pending) ?: return 0
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val frames =
            if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                val count = minOf(floats.remaining(), out.size)
                floats.get(out, 0, count)
                count
            } else {
                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                val count = minOf(shorts.remaining(), out.size)
                for (i in 0 until count) out[i] = shorts.get(i) / 32768f
                count
            }
        return frames
    }

    private fun feed(packet: ByteArray, length: Int): Int {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return 0
        val input = codec.getInputBuffer(index) ?: return 0
        input.clear()
        input.put(packet, 0, length)
        codec.queueInputBuffer(index, 0, length, 0, 0)
        return length
    }

    override fun close() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private companion object {
        const val SOURCE_RATE = 24000
        const val TIMEOUT_US = 4_000L

        /** Room for any single Opus packet at this bitrate, comfortably. */
        const val MAX_PACKET = 1500

        /** About 200 ms, well inside the 500 ms a cursor's read will wait before counting a stall. */
        const val MAX_POLLS = 50

        /**
         * About 500 ms, spent only on the first output after a codec starts: every read head builds a
         * fresh decoder on its first read and on each seek, and killing it for being slow to start
         * would mark the whole recording unavailable for the session.
         */
        const val START_POLLS = 125
    }
}

object OpusAssets {
    /**
     * Builds one decoder from a bundled container's identification header and releases it, so a
     * platform that refuses Opus is known when the app starts rather than when a layer first reads.
     * Returns the platform's reason, or null when the format was accepted.
     *
     * Off the main thread: codec creation is not free, and this only needs to answer once.
     */
    fun probe(context: Context): String? {
        val name =
            runCatching { context.assets.list("audio")?.firstOrNull { it.endsWith(".nfop") } }
                .getOrNull() ?: return null
        val config =
            runCatching {
                context.assets.open("audio/$name").use { OpusFile.parse(it.readBytes()).codecConfig }
            }
                .getOrNull() ?: return null
        return runCatching {
                MediaCodecOpus(config).close()
                null
            }
            .getOrElse { it.message ?: it.javaClass.simpleName }
    }

    /**
     * A bundled recording decoded on demand, or null when the asset is missing. A device without an
     * Opus decoder still gets a recording object: its cursor reports why it cannot sound, so the app
     * can say the layer is unavailable instead of playing silence.
     */
    fun open(
        context: Context,
        asset: String,
        onUnavailable: (String) -> Unit = {},
    ): SampleData? {
        val bytes =
            runCatching { context.assets.open("audio/$asset.nfop").use { it.readBytes() } }
                .getOrNull() ?: return null
        val file = runCatching { OpusFile.parse(bytes) }.getOrNull() ?: return null
        val recording =
            OpusRecording(
                file,
                { config -> runCatching { MediaCodecOpus(config) as OpusPackets }.getOrNull() },
                onUnavailable = onUnavailable,
            )
        return recording
    }
}
