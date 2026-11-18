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
 * count at that rate, the packet count, a packet table of `(start sample, byte length)` pairs, and the
 * packets themselves. Every sample count in the header and table is in the file's own rate, which is
 * the rate [RecordingSource] expects (24 kHz).
 */
class OpusFile private constructor(
    private val bytes: ByteArray,
    private val packetStarts: IntArray,
    private val packetOffsets: IntArray,
    private val packetLengths: IntArray,
    val sampleRate: Int,
    val totalSamples: Int,
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
        private const val HEADER = 20
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
            require(packetCount > 0 && sampleRate > 0) { "empty NFOP file" }
            require(HEADER + tableBytes <= bytes.size) { "truncated NFOP table" }
            val starts = IntArray(packetCount)
            val offsets = IntArray(packetCount)
            val lengths = IntArray(packetCount)
            var offset = HEADER + tableBytes
            for (i in 0 until packetCount) {
                starts[i] = readInt(bytes, HEADER + i * 8)
                lengths[i] = readInt(bytes, HEADER + i * 8 + 4)
                offsets[i] = offset
                offset += lengths[i]
            }
            require(offset <= bytes.size) { "truncated NFOP packets" }
            require(totalSamples >= MIN_SAMPLES) { "NFOP file is only $totalSamples samples" }
            return OpusFile(bytes, starts, offsets, lengths, sampleRate, totalSamples)
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
 * [RecordingSource] reads two heads at unrelated offsets, so one sequential decode cannot serve it:
 * each cursor owns a decoder and a ring buffer covering [seconds] of audio, decodes ahead of its head
 * on a worker thread, and restarts its decoder at the packet holding the head when the head wraps. The
 * audio thread only waits when its ring has run dry, which is at most one packet after a wrap; it is
 * never handed a buffer it has to allocate.
 */
class OpusRecording(
    private val file: OpusFile,
    private val decoder: () -> OpusPackets?,
    private val seconds: Double = 4.0,
) : DecodedSampleData {
    private val cursors = ArrayList<OpusCursor>()

    @Volatile override var unavailable: String? = null

    override val size = file.totalSamples

    /** Samples the decoder could not deliver in time, summed over cursors. Reported, never hidden. */
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

    fun close() {
        synchronized(cursors) { cursors.forEach { it.close() } }
    }
}

/** One read head's view of an [OpusRecording]: random reads, one decoder, one ring. */
class OpusCursor(
    private val file: OpusFile,
    private val factory: () -> OpusPackets?,
    seconds: Double,
    private val owner: OpusRecording?,
) : SampleData {
    override val size = file.totalSamples

    private val ring = FloatArray(max(1, (seconds * file.sampleRate).toInt()))
    private val packet = ByteArray(max(1, file.longestPacket))
    private val decoded = FloatArray(5760)
    private val lock = Object()
    private var decoder: OpusPackets? = null
    private var start = 0
    private var next = 0
    private var nextPacket = 0
    private var restart = -1
    private var running = true

    @Volatile
    var stalls = 0
        private set

    @Volatile private var failure: String? = null

    private val worker = Thread({ loop() }, "Nightfall opus").apply { isDaemon = true }
    private var started = false

    @Synchronized private fun begin() {
        if (started) return
        started = true
        worker.start()
    }

    fun fail(reason: String) {
        synchronized(lock) {
            failure = reason
            owner?.let { if (it.unavailable == null) it.unavailable = reason }
            lock.notifyAll()
        }
    }

    override fun get(index: Int): Float {
        begin()
        val target = index.coerceIn(0, size - 1)
        synchronized(lock) {
            val deadline = System.nanoTime() + 500_000_000L
            while (true) {
                failure?.let { return 0f }
                if (target >= next - ring.size && target < next) {
                    start = max(start, target - ring.size / 2)
                    return ring[target % ring.size]
                }
                // A miss — the head wrapped backwards, or it is further ahead than the ring reaches —
                // restarts the decoder at the packet holding it, which is a table lookup.
                if (restart != target) {
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

    fun close() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        worker.join(500)
        decoder?.close()
        decoder = null
    }

    private fun loop() {
        val first = factory()
        if (first == null) {
            fail("this device has no Opus decoder")
            return
        }
        synchronized(lock) { decoder = first }
        while (true) {
            var seek = -1
            synchronized(lock) {
                while (running && restart < 0 && next - start >= ring.size) lock.wait(50)
                if (!running) return
                if (restart >= 0) {
                    seek = restart
                    restart = -1
                }
            }
            if (seek >= 0) reseek(seek) else step()
        }
    }

    private fun reseek(target: Int) {
        val fresh = factory()
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
            lock.notifyAll()
        }
    }

    private fun step() {
        var index = 0
        var length = 0
        var source: OpusPackets? = null
        synchronized(lock) {
            if (nextPacket >= file.packetCount) {
                next = size
                lock.notifyAll()
                return
            }
            index = nextPacket
            length = file.packet(index, packet)
            source = decoder
        }
        if (source == null) return
        val produced = source.decode(packet, length, decoded)
        if (produced <= 0) {
            fail("an Opus packet decoded to nothing")
            return
        }
        val step = source.outputRate / file.sampleRate
        val out = if (step <= 1) produced else produced / step
        synchronized(lock) {
            for (i in 0 until out) {
                var value = decoded[i * step]
                if (step > 1) {
                    var sum = 0f
                    for (j in 0 until step) sum += decoded[i * step + j]
                    value = sum / step
                }
                if (next < size) ring[next % ring.size] = value
                next++
            }
            nextPacket = index + 1
            lock.notifyAll()
        }
    }
}
