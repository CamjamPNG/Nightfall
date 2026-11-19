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
import java.util.concurrent.CopyOnWriteArrayList

/*
 * The only platform-specific half of the Opus path: packets in, mono floats out, through the decoder
 * the device already ships. Container parsing, the ring buffers and the cursors against them live in
 * `core` where a host test covers them; the decoder's three calls are faked under Robolectric here,
 * and nothing in this file has run against a platform decoder.
 */

/**
 * Which step of the decode path refused, as the closed set the opt-in journal may hold.
 *
 * Every value is a branch this file can tell apart, which is the only reason to add one:
 * `createDecoderByType`, `configure` and `start` are three separate calls, the header being refused
 * while the decoder works without it is the fallback's own branch, and a started decoder that
 * produced nothing is the bounded first-output wait. None of them is a message: a platform's words
 * can name a codec, a path and a device, and the step is what decides the next fix.
 */
enum class OpusDecodeFailure(val token: String) {
    /** This device has no Opus decoder: `createDecoderByType` itself refused. */
    NO_DECODER("no_decoder"),

    /** A decoder was built and refused the format. */
    CONFIGURE("configure"),

    /** A decoder was configured and refused to start. */
    START("start"),

    /** The identification header was refused and the decoder was built without it. */
    HEADER_REJECTED("header_rejected"),

    /** A decoder that started produced no output for its first packet: it refused it or emitted none. */
    FIRST_OUTPUT_TIMEOUT("first_output_timeout"),

    /** A refusal that came from none of those calls: never guessed into a finer token. */
    OTHER("other"),
}

/**
 * Where the decode path says which step refused it — a listener list, not a log.
 *
 * The only listener is the opt-in diagnostics journal, which lives in the `github` channel alone, so
 * this file learns nothing about diagnostics and the `fdroid` build carries these tokens with nobody
 * listening. Listeners run on the thread that hit the failure, and one that throws is ignored:
 * diagnostics may never become a failure of the path it watches.
 */
object OpusFailures {
    private val listeners = CopyOnWriteArrayList<(OpusDecodeFailure) -> Unit>()

    fun report(failure: OpusDecodeFailure) {
        listeners.forEach { listener -> runCatching { listener(failure) } }
    }

    /** Adds a listener and hands back the way to remove it. */
    fun listen(listener: (OpusDecodeFailure) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}

/** A decoder that refused at one named step, so a report can say which rather than guess. */
private class OpusRefused(val step: OpusDecodeFailure, cause: Exception) :
    Exception(cause.message, cause)

/**
 * Builds a decoder out of the three calls that can refuse one, naming the step that did.
 *
 * A device that fails only when the codec starts is the class this exists for: `csd-0` is submitted
 * at [start], so "the decoder failed" could not tell a refused header from there being no decoder at
 * all. A codec left half-built by a refusal is released here, because the fallback needs a fresh one.
 */
internal fun <C> openCodec(
    create: () -> C,
    configure: (C) -> Unit,
    start: (C) -> Unit,
    release: (C) -> Unit,
): C {
    val codec =
        try {
            create()
        } catch (e: Exception) {
            throw OpusRefused(OpusDecodeFailure.NO_DECODER, e)
        }
    try {
        try {
            configure(codec)
        } catch (e: Exception) {
            throw OpusRefused(OpusDecodeFailure.CONFIGURE, e)
        }
        try {
            start(codec)
        } catch (e: Exception) {
            throw OpusRefused(OpusDecodeFailure.START, e)
        }
        return codec
    } catch (e: Exception) {
        runCatching { release(codec) }
        throw e
    }
}

/**
 * A decoder that may or may not accept the Opus identification header as its codec-specific data.
 *
 * The platform submits `csd-0` when the codec **starts**, not when it configures, so one attempt
 * covers both calls: a decoder that refuses the header can fail at either, and a fallback that only
 * wrapped configuration would never run for the very device class it exists for. On any failure a
 * half-built decoder is released by [build] and a fresh one is built without the header.
 *
 * Both outcomes are named for the journal: the fallback working is [OpusDecodeFailure.HEADER_REJECTED]
 * and both failing is the step the second attempt refused at.
 */
internal fun <T> openOpusDecoder(header: ByteArray, build: (header: ByteArray?) -> T): T =
    try {
        build(header)
    } catch (_: Exception) {
        try {
            build(null).also { OpusFailures.report(OpusDecodeFailure.HEADER_REJECTED) }
        } catch (e: Exception) {
            OpusFailures.report((e as? OpusRefused)?.step ?: OpusDecodeFailure.OTHER)
            throw e
        }
    }

internal class MediaCodecOpus(private val config: ByteArray) : OpusPackets {
    override var outputRate = 48000
        private set

    private val codec = openOpusDecoder(config) { header -> startedDecoder(header) }
    private val info = MediaCodec.BufferInfo()
    private var encoding = 0
    private var pending = -1
    /** The first output of a codec that has just started is the slowest one it will ever produce. */
    private var starting = true

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

        /** A decoder configured — with the identification header or without it — and started. */
        fun startedDecoder(header: ByteArray?): MediaCodec =
            openCodec(
                create = { MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS) },
                configure = { codec ->
                    codec.configure(
                        MediaFormat.createAudioFormat(
                                MediaFormat.MIMETYPE_AUDIO_OPUS,
                                SOURCE_RATE,
                                1,
                            )
                            .apply {
                                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_PACKET)
                                header?.let { setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
                            },
                        null,
                        null,
                        0,
                    )
                },
                start = { codec -> codec.start() },
                release = { codec -> codec.release() },
            )
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
        // The first packet after a start is the slowest one, and the one worth naming if it fails.
        val first = starting
        val budget = if (first) START_POLLS else MAX_POLLS
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
                if (wrote <= 0) break
            }
        }
        val frames = if (pending < 0) 0 else read(pending, out)
        if (frames > 0) {
            starting = false
        } else if (first) {
            // A decoder that started and gave nothing for its first packet is a failure of its own,
            // and the one a platform failure would otherwise hide behind a layer of silence: it either
            // refused the packet, took it and emitted nothing inside the budget, or handed back an
            // empty buffer. All three leave the same hole in the release, so all three are the one
            // token. A stall later in a night is the cursor's to count, not a decoder's refusal.
            OpusFailures.report(OpusDecodeFailure.FIRST_OUTPUT_TIMEOUT)
        }
        return frames
    }

    /** The frames one output buffer holds, converted to mono floats. Zero when it holds none. */
    private fun read(index: Int, out: FloatArray): Int {
        val buffer: ByteBuffer = codec.getOutputBuffer(index) ?: return 0
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        return if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
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
