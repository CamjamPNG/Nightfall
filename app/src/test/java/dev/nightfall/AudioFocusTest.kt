// SPDX-License-Identifier: MIT
package dev.nightfall

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

/**
 * Focus handling, driven through the listener AudioHost registers with Android.
 *
 * Robolectric records the focus request but has no other app competing for it, so these tests prove
 * what Nightfall does when Android reports a focus change, plus the request it made on the way in.
 * That a real second app can take focus from Nightfall is only provable on a phone.
 *
 * The assertions are on the audio thread's output rather than on the transport control's label: in
 * Robolectric the Compose tree does not reliably recompose for a state flip that arrives from the
 * focus handler, and a label is a weaker fact than "samples stopped arriving".
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusTest : PlaybackHarness() {
    private lateinit var manager: AudioManager

    private val wakeLock: PowerManager.WakeLock
        get() = checkNotNull(ShadowPowerManager.getLatestWakeLock()) { "no wake lock was created" }

    @Before
    fun startPlaying() {
        manager = app.getSystemService(AudioManager::class.java)
        clickTransport("Play soundscape")
        deliverStartCommand()
        await { runtime.status.value.playing }
        awaitSamples()
    }

    private fun focusListener(): AudioManager.OnAudioFocusChangeListener {
        val request = shadowOf(manager).lastAudioFocusRequest
        assertNotNull("AudioHost never requested audio focus", request)
        return checkNotNull(request.listener)
    }

    /** Lets the block already in flight land, then measures a window in which nothing may be written. */
    private fun assertSilent(window: Long = 250) {
        Thread.sleep(100)
        assertEquals(
            "the audio thread kept writing $window ms after playback stopped",
            0L,
            samplesDuring(window),
        )
    }

    @Test
    fun playbackAsksForMediaFocusAndHoldsTheCpu() {
        val request = checkNotNull(shadowOf(manager).lastAudioFocusRequest).audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.focusGain)
        assertEquals(AudioAttributes.USAGE_MEDIA, request.audioAttributes.usage)
        assertTrue(wakeLock.isHeld)
    }

    @Test
    fun transientLossStopsAudioAndGainResumesIt() {
        val listener = focusListener()
        compose.runOnIdle { listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) }
        await { !runtime.status.value.playing }
        assertFalse(wakeLock.isHeld)
        assertNull(
            "a transient loss must keep the focus request for the resume",
            shadowOf(manager).lastAbandonedAudioFocusRequest,
        )
        assertSilent()

        compose.runOnIdle { listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN) }
        await { runtime.status.value.playing }
        assertTrue(wakeLock.isHeld)
        awaitSamples()
        await { runtime.status.value.peak > 0f }
    }

    @Test
    fun permanentLossStopsAudioAndGainDoesNotResumeIt() {
        val listener = focusListener()
        compose.runOnIdle { listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS) }
        await { !runtime.status.value.playing }
        assertSilent()

        compose.runOnIdle { listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN) }
        compose.waitForIdle()
        assertFalse("playback restarted after a permanent focus loss", runtime.status.value.playing)
        assertSilent()
    }

    @Test
    fun userPauseReleasesFocusAndTheWakeLock() {
        compose.runOnIdle { runtime.pause() }
        await { !runtime.status.value.playing }
        assertNotNull(
            "a user pause must hand focus back",
            shadowOf(manager).lastAbandonedAudioFocusRequest,
        )
        assertFalse(wakeLock.isHeld)
        assertSilent()
    }
}
