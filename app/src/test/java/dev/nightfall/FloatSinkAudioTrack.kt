// SPDX-License-Identifier: MIT
package dev.nightfall

import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack

/**
 * Robolectric's stock AudioTrack shadow implements byte writes only. AudioHost writes 32-bit float
 * PCM, so under the stock shadow every write returns 0 and AudioHost correctly reports "AudioTrack
 * write failed: 0". This shadow accepts float writes and keeps what arrived, so the tests can look
 * at the samples the real mixer handed to the output with no audio hardware involved.
 *
 * It is a sink, not a speaker: nothing here proves a driver, a route or a speaker ever sounds.
 */
@Implements(AudioTrack::class)
class FloatSinkAudioTrack : ShadowAudioTrack() {
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
        return sizeInFloats
    }

    companion object {
        val floatsWritten = AtomicLong()
        val nonSilentWrites = AtomicLong()
        val nonFiniteWrites = AtomicInteger()

        @Volatile
        var peakWritten = 0f
            private set

        fun reset() {
            floatsWritten.set(0)
            nonSilentWrites.set(0)
            nonFiniteWrites.set(0)
            peakWritten = 0f
        }
    }
}
