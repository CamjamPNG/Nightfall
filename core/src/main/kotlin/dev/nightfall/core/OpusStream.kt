// SPDX-License-Identifier: MIT
package dev.nightfall.core

import kotlin.math.max
import kotlin.math.min

/**
 * Opus packets in a packed container, decoded on demand.
 *
 * A bundled recording is ~77 MB as 16-bit PCM and ~1 MB as 48 kbit/s Opus, so the catalogue cannot
 * ship as PCM forever. The capture is the same: [RecordingSource] reads a long file from two heads at
 * unrelated offsets, so this file supplies container parsing, a decoded ring buffer and the forward
 * cursors the source needs, while [OpusPackets] stays the only platform-specific piece — the app binds
 * it to `MediaCodec`, a host test binds it to a fake.
 *
 * Container: `NFOP` magic, version 1, one byte of channel count, then the sample rate, the total sample
 * count at that rate, the packet count, the table length, the codec configuration length, the Opus
 * identification header the decoder needs as `csd-0`, a packet table of `(start sample, byte length)`
 * pairs, and the packets themselves. Every sample count in the header and table is in the file's own rate, which is
 * the rate [RecordingSource] expects (24 kHz).
 */
class OpusFile private constructor(
    private val bytes: ByteArray,
    private val packetStarts: IntArray,
    private val packetOffsets: IntArray,
    private val packetLengths: IntArray,
    val sampleRate: Int,
    val totalSamples: Int,
    /** The Opus identification header, which a decoder wants as codec-specific data before any packet. */
    val codecConfig: ByteArray,
) {
    val packetCount = packetStarts.size

    val longestPacket: Int = packetLengths.maxOrNull() ?: 0

    /** Index of the packet that contains [sample]. */
    fun packetAt(sample: Int): Int {
        var low = 0
        var high = packetCount - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (packetStarts[middle] <= sample) low = middle else high = middle - 1
        }
        return low
    }

    fun packetStart(index: Int) = packetStarts[index]

    /** Copies packet [index] into [into] and returns its length. */
    fun packet(index: Int, into: ByteArray): Int {
        val length = packetLengths[index]
        require(length <= into.size) { "packet $index is $length bytes" }
        System.arraycopy(bytes, packetOffsets[index], into, 0, length)
        return length
    }

    companion object {
        private const val MAGIC = 0x50464F4E // "NFOP" little-endian
        private const val VERSION = 1
        private const val HEADER = 24
        const val MIN_SAMPLES = 180 * 24000

        fun parse(bytes: ByteArray): OpusFile {
            require(bytes.size >= HEADER) { "not an NFOP file" }
            require(readInt(bytes, 0) == MAGIC) { "not an NFOP file" }
            require(bytes[4].toInt() == VERSION) { "NFOP version ${bytes[4]}" }
            require(bytes[5].toInt() == 1) { "NFOP channel count ${bytes[5]}" }
            val sampleRate = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
            val totalSamples = readInt(bytes, 8)
            val packetCount = readInt(bytes, 12)
            val tableBytes = readInt(bytes, 16)
            val configBytes = readInt(bytes, 20)
            require(packetCount > 0 && sampleRate > 0) { "empty NFOP file" }
            require(HEADER + configBytes + tableBytes <= bytes.size) { "truncated NFOP header" }
            val config = bytes.copyOfRange(HEADER, HEADER + configBytes)
            require(config.size >= 8 && String(config, 0, 8, Charsets.US_ASCII) == "OpusHead") {
                "NFOP has no Opus identification header"
            }
            val table = HEADER + configBytes
            val starts = IntArray(packetCount)
            val offsets = IntArray(packetCount)
            val lengths = IntArray(packetCount)
            var offset = table + tableBytes
            for (i in 0 until packetCount) {
                starts[i] = readInt(bytes, table + i * 8)
                lengths[i] = readInt(bytes, table + i * 8 + 4)
                offsets[i] = offset
                offset += lengths[i]
            }
            require(offset <= bytes.size) { "truncated NFOP packets" }
            require(totalSamples >= MIN_SAMPLES) { "NFOP file is only $totalSamples samples" }
            return OpusFile(bytes, starts, offsets, lengths, sampleRate, totalSamples, config)
        }

        private fun readInt(b: ByteArray, at: Int): Int =
            (b[at].toInt() and 0xFF) or
                ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or
                ((b[at + 3].toInt() and 0xFF) shl 24)
    }
}

/** A platform Opus decoder that turns one packet into mono floats at its own output rate. */
interface OpusPackets {
    val outputRate: Int

    /** Decodes [length] bytes of [packet] into [out], returning how many samples it wrote. */
    fun decode(packet: ByteArray, length: Int, out: FloatArray): Int

    fun close() {}
}

/**
 * One recording decoded on demand, handing out an independent forward cursor per read head.
 *
 * [RecordingSource] reads two heads at unrelated offsets and each head also reads the file's first
 * second for its wrap crossfade, so it takes four cursors. Each owns a decoder and a ring buffer of
 * [seconds] of audio, decodes ahead of its head on a worker thread, and restarts its decoder at the
 * packet holding the head when the head wraps. Decoders are released when a layer goes away, not on a
 * timer: a timer cannot tell a retired cursor from an origin cursor, which is only read during the
 * one-second crossfade before a wrap. `RecordingSource` closes its cursors when the mixer prunes it.
 *
 * [onUnavailable] is called from the worker the moment a decoder cannot be created or a packet decodes
 * to nothing, so the host can report it immediately rather than at the next mix change.
 */
class OpusRecording(
    private val file: OpusFile,
    private val decoder: (ByteArray) -> OpusPackets?,
    private val seconds: Double = 4.0,
    private val onUnavailable: ((String) -> Unit)? = null,
) : DecodedSampleData {
    private val cursors = ArrayList<OpusCursor>()

    @Volatile override var unavailable: String? = null

    override val size = file.totalSamples

    /** Reads that ran dry, summed over cursors. Counted, never hidden. */
    val stalls: Int
        get() {
            var total = 0
            synchronized(cursors) { cursors.forEach { total += it.stalls } }
            return total
        }

    override fun get(index: Int): Float = cursor()[index]

    override fun cursor(seconds: Double): SampleData {
        val cursor = OpusCursor(file, decoder, if (seconds > 0) seconds else this.seconds, this)
        synchronized(cursors) { cursors += cursor }
        unavailable?.let { cursor.fail(it) }
        return cursor
    }

    internal fun forget(cursor: OpusCursor) {
        synchronized(cursors) {
            cursors.remove(cursor)
        }
    }

    internal fun report(reason: String) {
        if (unavailable == null) unavailable = reason
        onUnavailable?.invoke(reason)
    }

    fun close() {
        val open = synchronized(cursors) { cursors.toList() }
        open.forEach { it.close() }
        synchronized(cursors) { cursors.clear() }
    }
}

/** One read head's view of an [OpusRecording]: random reads, one decoder, one ring. */
/** A decoded cursor the mixer can release without waiting for its worker. */
interface CloseableSample : SampleData {
    fun close()
}

class OpusCursor(
    private val file: OpusFile,
    private val factory: (ByteArray) -> OpusPackets?,
    seconds: Double,
    private val owner: OpusRecording?,
) : CloseableSample {
    override val size = file.totalSamples

    private val window = max(1, (seconds * file.sampleRate).toInt())
    private val packet = ByteArray(max(1, file.longestPacket))
    private val decoded = FloatArray(5760)
    private val lock = Object()
    private var ring: FloatArray? = null
    private var decoder: OpusPackets? = null
    private var start = 0
    private var next = 0
    private var nextPacket = 0
    private var restart = -1
    private var seeking = -1
    private var running = true
    private var closing = false
    private var lastRead = System.currentTimeMillis()
    private var started = false

    @Volatile var stalls = 0
        private set

    @Volatile private var failure: String? = null

    private val worker = Thread({ loop() }, "Nightfall opus").apply { isDaemon = true }

    @Synchronized private fun begin() {
        if (started || closing) return
        started = true
        worker.start()
    }

    fun fail(reason: String) {
        if (failure != null) return
        synchronized(lock) {
            failure = reason
            lock.notifyAll()
        }
        owner?.report(reason)
    }

    override fun get(index: Int): Float {
        begin()
        val target = index.coerceIn(0, size - 1)
        synchronized(lock) {
            // A closed cursor says nothing and waits for nothing: the layer that read it is gone.
            if (closing) return 0f
            lastRead = System.currentTimeMillis()
            val buffer = ring ?: FloatArray(window).also { ring = it }
            val deadline = System.nanoTime() + WAIT_NANOS
            while (true) {
                if (failure != null) return 0f
                if (target >= next - buffer.size && target < next) {
                    start = max(start, target - buffer.size / 2)
                    return buffer[target % buffer.size]
                }
                // A miss restarts the decoder at the packet holding the head — a table lookup — unless
                // that exact restart is already in flight, which keeps a waiting read from queueing the
                // same seek again while a slow decoder is being built.
                if (restart != target && seeking != target) {
                    restart = target
                    start = target
                }
                val left = (deadline - System.nanoTime()) / 1_000_000L
                if (left <= 0) {
                    stalls++
                    return 0f
                }
                lock.wait(min(left, 20L))
            }
        }
    }

    /**
     * Stops this cursor. The worker releases the decoder and the ring on its way out, so this is only a
     * flag and a wake — a mixer pruning a layer on the audio thread does not wait for a codec teardown.
     * A read after this returns silence rather than rebuilding anything.
     */
    override fun close() {
        synchronized(lock) {
            if (closing) return
            closing = true
            running = false
            lock.notifyAll()
        }
        owner?.forget(this)
    }

    private fun full() = next - start >= (ring?.size ?: 1)

    private fun open(): OpusPackets? {
        synchronized(lock) {
            decoder?.let { return it }
            if (failure != null) return null
        }
        val fresh = factory(file.codecConfig)
        if (fresh == null) {
            fail("this device has no Opus decoder")
            return null
        }
        synchronized(lock) {
            decoder = fresh
            return fresh
        }
    }

    private fun loop() {
        if (open() == null) return
        try {
        while (true) {
            var seek = -1
            synchronized(lock) {
                // A failure is terminal: the worker exits rather than rebuilding a decoder that cannot
                // be built, which would spin the core and hammer codec allocation on every iteration.
                if (failure != null) return
                while (running && restart < 0 && full()) lock.wait(20)
                if (!running || failure != null) return
                if (restart >= 0) {
                    seek = restart
                    restart = -1
                    seeking = seek
                }
            }
            if (seek >= 0) reseek(seek) else step()
        }
        } finally {
            // Closing here rather than in close() keeps a codec teardown off the mixing thread.
            synchronized(lock) {
                decoder?.close()
                decoder = null
                ring = null
            }
        }
    }

    private fun reseek(target: Int) {
        val fresh = factory(file.codecConfig)
        if (fresh == null) {
            fail("this device has no Opus decoder")
            return
        }
        synchronized(lock) {
            decoder?.close()
            decoder = fresh
            nextPacket = file.packetAt(target)
            next = file.packetStart(nextPacket)
            start = target
            seeking = -1
            lastRead = System.currentTimeMillis()
            lock.notifyAll()
        }
    }

    private fun step() {
        val source = open() ?: return
        var index: Int
        var length: Int
        synchronized(lock) {
            if (nextPacket >= file.packetCount) {
                next = size
                lock.notifyAll()
                return
            }
            index = nextPacket
            length = file.packet(index, packet)
        }
        val produced = source.decode(packet, length, decoded)
        if (produced <= 0) {
            fail("an Opus packet decoded to nothing")
            return
        }
        val step = source.outputRate / file.sampleRate
        val out = if (step <= 1) produced else produced / step
        synchronized(lock) {
            val buffer = ring ?: FloatArray(window).also { ring = it }
            for (i in 0 until out) {
                var value = decoded[i * step]
                if (step > 1) {
                    var sum = 0f
                    for (j in 0 until step) sum += decoded[i * step + j]
                    value = sum / step
                }
                if (next < size) buffer[next % buffer.size] = value
                next++
            }
            nextPacket = index + 1
            lock.notifyAll()
        }
    }

    private companion object {
        const val WAIT_NANOS = 500_000_000L
    }
}
