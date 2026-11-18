// SPDX-License-Identifier: MIT
package dev.nightfall

import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowPowerManager

/**
 * Teardown: when the service goes away the PCM thread really ends, instead of surviving as a
 * spinning orphan that keeps a wake lock and its AudioTrack.
 */
@RunWith(AndroidJUnit4::class)
class TeardownTest : PlaybackHarness() {
    @Test
    fun destroyingTheServiceStopsTheAudioThread() {
        clickTransport("Play soundscape")
        deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }
        val host = checkNotNull(runtime.host) { "playback ran without an AudioHost" }
        assertTrue(host.isAlive)
        assertTrue(pcmThreads().isNotEmpty())

        service?.destroy()
        service = null

        await { !host.isAlive }
        assertTrue("the PCM thread survived service teardown", pcmThreads().isEmpty())
        assertFalse(runtime.status.value.playing)
        assertFalse(
            "teardown left the wake lock held",
            checkNotNull(ShadowPowerManager.getLatestWakeLock() as PowerManager.WakeLock?).isHeld,
        )

        val settled = FloatSinkAudioTrack.floatsWritten.get()
        advanceClock(200)
        assertTrue(
            "the audio thread wrote ${FloatSinkAudioTrack.floatsWritten.get() - settled} floats " +
                "after teardown",
            FloatSinkAudioTrack.floatsWritten.get() - settled < 1_000_000,
        )
    }

    private fun pcmThreads() =
        Thread.getAllStackTraces().keys.filter { it.name == "Nightfall PCM" && it.isAlive }
}
