// SPDX-License-Identifier: MIT
package dev.nightfall

import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

/**
 * Backgrounding and the sleep timer.
 *
 * Robolectric's main clock and SystemClock are virtual, so the timer is advanced explicitly instead
 * of waited for. That makes expiry deterministic, but it also means this proves AudioHost keeps
 * rendering and then stops when the timer expires, not that a phone keeps its CPU awake for an hour
 * with the screen off, and not that Android refrains from killing the process. Background survival
 * here means: nothing in the activity layer stops playback when the activity is not resumed.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackTest : PlaybackHarness() {
    private val wakeLock: PowerManager.WakeLock
        get() = checkNotNull(ShadowPowerManager.getLatestWakeLock()) { "no wake lock was created" }

    @Test
    fun playbackKeepsRenderingInBackgroundAndTheTimerStopsIt() {
        clickTransport("Play soundscape")
        val playback = deliverStartCommand()
        await { runtime.status.value.playing && runtime.status.value.peak > 0f }
        assertTrue(wakeLock.isHeld)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val writtenInBackground = FloatSinkAudioTrack.floatsWritten.get()
        await { FloatSinkAudioTrack.floatsWritten.get() > writtenInBackground }
        assertTrue("playback stopped when the activity left the foreground", runtime.status.value.playing)
        assertTrue(wakeLock.isHeld)
        assertTrue(
            "the foreground notification was dropped in the background",
            shadowOf(playback).isLastForegroundNotificationAttached,
        )
        assertFalse(shadowOf(playback).isStoppedBySelf)

        compose.runOnIdle { runtime.timer.start(SystemClock.elapsedRealtime(), 1_500, 500) }
        advanceClock(1_600)
        await { runtime.status.value.timerFading }
        assertTrue("playback ended before the fade completed", runtime.status.value.playing)

        advanceClock(600)
        await { !runtime.status.value.playing }
        assertNull(runtime.status.value.error)
        assertFalse(wakeLock.isHeld)
        val settled = FloatSinkAudioTrack.floatsWritten.get()
        advanceClock(200)
        assertTrue(
            "the audio thread kept writing after the timer expired",
            FloatSinkAudioTrack.floatsWritten.get() - settled < 1_000_000,
        )

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitTransport("Play soundscape")
    }
}
