// SPDX-License-Identifier: MIT
package dev.nightfall

import android.media.AudioTrack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioTrack

/**
 * Robolectric's stock AudioTrack shadow implements byte writes only. AudioHost writes 32-bit float
 * PCM, so under the stock shadow every write returns 0 and AudioHost correctly reports "AudioTrack
 * write failed: 0". This shadow accepts float writes and keeps what arrived, so the tests can look
 * at the samples the real mixer handed to the output with no audio hardware involved.
 *
 * It is a sink, not a speaker: nothing here proves a driver, a route or a speaker ever sounds.
 *
 * It also holds one synchroniser, because a teardown race cannot be tested by hoping for the
 * scheduling that produces it. The thread that writes the stream is this track's owner, and
 * [holdNextWrite] parks it inside a write, so the track stays published while the owner is provably
 * alive. A state call on that track from any other thread then lets the owner finish and waits until
 * the track really is released before running the platform's own method — the exception in that case
 * is `AudioTrack.pause()`'s, not this file's opinion, because the call is delegated to it unchanged.
 *
 * The real [AudioTrack] runs under Robolectric rather than being faked: it reports
 * `STATE_UNINITIALIZED` after `release()`, and `pause()` then throws exactly what a device throws.
 */
@Implements(AudioTrack::class)
class FloatSinkAudioTrack : ShadowAudioTrack() {
    @RealObject private lateinit var realTrack: AudioTrack

    @Implementation
    protected fun native_write_float(
        audioData: FloatArray,
        offsetInFloats: Int,
        sizeInFloats: Int,
        format: Int,
        isBlocking: Boolean,
    ): Int {
        var peak = 0f
        var finite = true
        for (i in offsetInFloats until offsetInFloats + sizeInFloats) {
            val v = audioData[i]
            if (v.isFinite()) peak = maxOf(peak, kotlin.math.abs(v)) else finite = false
        }
        floatsWritten.addAndGet(sizeInFloats.toLong())
        if (!finite) nonFiniteWrites.incrementAndGet()
        if (peak > 0f) nonSilentWrites.incrementAndGet()
        if (peak > peakWritten) peakWritten = peak
        owner.getOrSet(Thread.currentThread())
        if (holdArmed.compareAndSet(true, false)) {
            val arrived = CountDownLatch(1)
            val leave = CountDownLatch(1)
            parked = arrived
            resumed = leave
            arrived.countDown()
            // Bounded, so a teardown that never reaches the track cannot wedge the audio thread.
            leave.await(HOLD_MS, TimeUnit.MILLISECONDS)
        }
        return sizeInFloats
    }

    /**
     * The ordering that released a track under teardown's feet: the stream's owner is inside a write
     * and a second thread asks the track to change state. Let the owner run instead of freezing it
     * mid-write, wait for the release it performs, then make the platform's own call.
     */
    @Implementation
    protected fun pause() {
        val waiting = parked
        if (waiting != null && waiting.count == 0L && Thread.currentThread() !== owner.copyOrNull()) {
            pauseCallsFromNonOwner.incrementAndGet()
            resumed?.countDown()
            val deadline = System.nanoTime() + HOLD_MS * 1_000_000L
            while (realTrack.state == AudioTrack.STATE_INITIALIZED && System.nanoTime() < deadline)
                Thread.sleep(1)
            parked = null
            resumed = null
        }
        Shadow.directlyOn<Any, AudioTrack>(realTrack, AudioTrack::class.java, "pause")
    }

    companion object {
        /** Long enough for a release to finish, short enough that no test waits on it for a second. */
        private const val HOLD_MS = 400L

        val floatsWritten = AtomicLong()
        val nonSilentWrites = AtomicLong()
        val nonFiniteWrites = AtomicInteger()

        /** Times a thread other than the stream's owner asked the track to change state. */
        val pauseCallsFromNonOwner = AtomicInteger()

        private val holdArmed = AtomicBoolean()
        private val owner = ThreadSlot()
        @Volatile private var parked: CountDownLatch? = null
        @Volatile private var resumed: CountDownLatch? = null

        @Volatile
        var peakWritten = 0f
            private set

        /** Parks the next write, so the stream's owner is provably inside it when teardown starts. */
        fun holdNextWrite() {
            holdArmed.set(true)
        }

        /** Waits until the armed write is parked, so the ordering is set up rather than hoped for. */
        fun awaitWriteParked(): Boolean {
            val deadline = System.nanoTime() + HOLD_MS * 5 * 1_000_000L
            while (parked == null && System.nanoTime() < deadline) Thread.sleep(1)
            return parked != null
        }

        fun reset() {
            floatsWritten.set(0)
            nonSilentWrites.set(0)
            nonFiniteWrites.set(0)
            pauseCallsFromNonOwner.set(0)
            peakWritten = 0f
            holdArmed.set(false)
            owner.clear()
            parked = null
            resumed = null
        }
    }

    /** The first thread to write this track, remembered without a lock on the write path. */
    private class ThreadSlot {
        @Volatile private var thread: Thread? = null

        fun getOrSet(candidate: Thread): Thread {
            thread?.let { return it }
            thread = candidate
            return candidate
        }

        fun copyOrNull(): Thread? = thread

        fun clear() {
            thread = null
        }
    }
}
